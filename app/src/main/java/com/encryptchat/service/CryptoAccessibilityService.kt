package com.encryptchat.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.encryptchat.crypto.CryptoEngine

/**
 * 无障碍服务
 * 核心功能：
 * 1. 自动扫描屏幕上的密文
 * 2. 监听剪贴板变化，自动检测密文
 * 3. 自动填入加密/解密后的文本到聊天输入框
 */
class CryptoAccessibilityService : AccessibilityService() {

    private var clipboardManager: ClipboardManager? = null
    private var lastClipText: String? = null
    private var lastScannedEncryptedText: String? = null

    // 防止重复扫描的节流
    private var lastScanTime = 0L
    private val scanIntervalMs = 1500L

    companion object {
        private var instance: CryptoAccessibilityService? = null
        private var pendingFillText: String? = null

        /**
         * 请求自动填入文本到当前焦点输入框
         */
        fun requestFillText(text: String) {
            val service = instance
            if (service != null) {
                service.performFillText(text)
            } else {
                pendingFillText = text
            }
        }

        /**
         * 检查无障碍服务是否运行中
         */
        fun isRunning(): Boolean = instance != null

        /**
         * 获取最近在屏幕上发现的密文
         */
        fun getLastDetectedEncryptedText(): String? = instance?.lastScannedEncryptedText
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 300
        }

        // 监听剪贴板变化
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener {
            onClipboardChanged()
        }

        // 处理待填入的文本
        pendingFillText?.let {
            performFillText(it)
            pendingFillText = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 节流：避免高频扫描
                val now = System.currentTimeMillis()
                if (now - lastScanTime > scanIntervalMs) {
                    lastScanTime = now
                    try {
                        scanForEncryptedText(rootInActiveWindow)
                    } catch (_: Exception) {}
                }
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // 有输入框获得焦点时，检查是否有待填入的文本
                pendingFillText?.let {
                    performFillText(it)
                    pendingFillText = null
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ─── 剪贴板监听 ──────────────────────────────────────

    private fun onClipboardChanged() {
        val text = try {
            clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()
        } catch (_: Exception) {
            null
        }

        if (text != null && text != lastClipText && CryptoEngine.isEncryptedText(text)) {
            lastClipText = text
            Toast.makeText(this, "🔒 检测到密文，点击气泡解密", Toast.LENGTH_LONG).show()
        }
    }

    // ─── 屏幕密文扫描 ────────────────────────────────────

    /**
     * 递归扫描当前窗口中的所有文本节点，寻找密文
     */
    private fun scanForEncryptedText(nodeInfo: AccessibilityNodeInfo?) {
        nodeInfo ?: return

        try {
            val text = nodeInfo.text?.toString()
            if (text != null && text.length > 10) {
                val encrypted = CryptoEngine.extractEncryptedText(text)
                if (encrypted != null && encrypted != lastScannedEncryptedText) {
                    lastScannedEncryptedText = encrypted
                    notifyEncryptedTextFound(encrypted)
                }
            }

            // 递归扫描子节点（限制深度避免性能问题）
            val childCount = nodeInfo.childCount
            for (i in 0 until minOf(childCount, 50)) {
                try {
                    val child = nodeInfo.getChild(i)
                    if (child != null) {
                        scanForEncryptedText(child)
                        child.recycle()
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * 发现屏幕上的密文后的处理
     */
    private fun notifyEncryptedTextFound(encryptedText: String) {
        // 将密文复制到剪贴板，便于用户在面板中粘贴解密
        Toast.makeText(this, "🔒 检测到屏幕上的密文，点击气泡解密", Toast.LENGTH_SHORT).show()
    }

    // ─── 自动填入 ─────────────────────────────────────

    /**
     * 将文本自动填入当前窗口中的可编辑输入框
     */
    private fun performFillText(text: String) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // 方法1: 查找当前焦点的可编辑控件
            val focusedNode = findFocusedEditText(rootNode)
            if (focusedNode != null) {
                fillNode(focusedNode, text)
                focusedNode.recycle()
                return
            }

            // 方法2: 查找任意可编辑控件
            val editableNode = findEditableNode(rootNode)
            if (editableNode != null) {
                fillNode(editableNode, text)
                editableNode.recycle()
                return
            }

            // 方法3: 回退到剪贴板
            com.encryptchat.util.ClipboardHelper.copyToClipboard(this, text)
            Toast.makeText(this, "未找到输入框，已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            com.encryptchat.util.ClipboardHelper.copyToClipboard(this, text)
            Toast.makeText(this, "自动填入失败，已复制到剪贴板", Toast.LENGTH_SHORT).show()
        } finally {
            try { rootNode.recycle() } catch (_: Exception) {}
        }
    }

    private fun fillNode(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * 查找当前获得焦点的可编辑输入框
     */
    private fun findFocusedEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable) return AccessibilityNodeInfo.obtain(node)

        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                val result = findFocusedEditText(child)
                child.recycle()
                if (result != null) return result
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 查找窗口中第一个可编辑输入框
     */
    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return AccessibilityNodeInfo.obtain(node)

        for (i in 0 until node.childCount) {
            try {
                val child = node.getChild(i) ?: continue
                val result = findEditableNode(child)
                child.recycle()
                if (result != null) return result
            } catch (_: Exception) {}
        }
        return null
    }
}
