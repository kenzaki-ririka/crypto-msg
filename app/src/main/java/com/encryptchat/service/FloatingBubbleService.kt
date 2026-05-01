package com.encryptchat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.encryptchat.MainActivity
import com.encryptchat.R
import com.encryptchat.crypto.CryptoEngine
import com.encryptchat.storage.ContactKeyManager
import com.encryptchat.util.ClipboardHelper

/**
 * 悬浮气泡服务
 * 提供始终置顶的悬浮气泡，点击展开加解密面板
 */
class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var contactKeyManager: ContactKeyManager

    private var bubbleView: View? = null
    private var panelView: View? = null
    private var isPanelShowing = false

    companion object {
        const val CHANNEL_ID = "encrypt_chat_bubble"
        const val NOTIFICATION_ID = 1001
        private var isRunning = false

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }

        fun isRunning(): Boolean = isRunning
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        contactKeyManager = ContactKeyManager(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        showBubble()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeBubble()
        hidePanel()
    }

    // ─── 通知 ──────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "加密聊天悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持加密聊天悬浮气泡运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("加密聊天")
            .setContentText("悬浮窗运行中，点击打开设置")
            .setSmallIcon(R.drawable.ic_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ─── 悬浮气泡 ──────────────────────────────────────

    private fun showBubble() {
        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.layout_floating_bubble, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        setupBubbleTouchListener(params)
        windowManager.addView(bubbleView, params)
    }

    private fun removeBubble() {
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
    }

    private fun setupBubbleTouchListener(params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx * dx + dy * dy > 100) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try {
                        windowManager.updateViewLayout(bubbleView, params)
                    } catch (_: Exception) {}
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        togglePanel()
                    }
                    // 吸附到屏幕边缘
                    val displayMetrics = resources.displayMetrics
                    val screenWidth = displayMetrics.widthPixels
                    params.x = if (params.x < screenWidth / 2) 0
                    else screenWidth - (bubbleView?.width ?: 0)
                    try {
                        windowManager.updateViewLayout(bubbleView, params)
                    } catch (_: Exception) {}
                    true
                }

                else -> false
            }
        }
    }

    // ─── 加解密面板 ─────────────────────────────────────

    private fun togglePanel() {
        if (isPanelShowing) hidePanel() else showPanel()
    }

    private fun showPanel() {
        if (isPanelShowing) return

        val inflater = LayoutInflater.from(this)
        panelView = inflater.inflate(R.layout.layout_floating_panel, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            windowAnimations = android.R.style.Animation_InputMethod
        }

        panelView?.let { panel ->
            setupPanelActions(panel)
            windowManager.addView(panel, params)
            isPanelShowing = true

            // 自动检测剪贴板
            autoDetectClipboard(panel)
        }
    }

    private fun hidePanel() {
        if (isPanelShowing) {
            panelView?.let {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
            panelView = null
            isPanelShowing = false
        }
    }

    private fun setupPanelActions(panel: View) {
        val etInput = panel.findViewById<EditText>(R.id.et_input)
        val tvResult = panel.findViewById<TextView>(R.id.tv_result)
        val btnEncrypt = panel.findViewById<Button>(R.id.btn_encrypt)
        val btnDecrypt = panel.findViewById<Button>(R.id.btn_decrypt)
        val btnCopy = panel.findViewById<Button>(R.id.btn_copy)
        val btnAutoFill = panel.findViewById<Button>(R.id.btn_autofill)
        val btnClose = panel.findViewById<ImageButton>(R.id.btn_close)
        val spinnerContact = panel.findViewById<Spinner>(R.id.spinner_contact)

        // 加载联系人列表
        val contacts = contactKeyManager.getAllContacts()
        loadContactsSpinner(spinnerContact, contacts)

        // 加密按钮
        btnEncrypt.setOnClickListener {
            val text = etInput.text.toString()
            if (text.isBlank()) {
                showToast("请输入要加密的文本")
                return@setOnClickListener
            }
            val password = getSelectedPassword(spinnerContact, contacts)
            if (password == null) {
                showToast("请先添加联系人")
                return@setOnClickListener
            }
            try {
                val encrypted = CryptoEngine.encrypt(text, password)
                tvResult.text = encrypted
                ClipboardHelper.copyToClipboard(this, encrypted)
                // 尝试自动填入聊天输入框
                CryptoAccessibilityService.requestFillText(encrypted)
                showToast("✅ 已加密并复制，正在自动填入")
                etInput.text.clear()
            } catch (e: Exception) {
                showToast("加密失败: ${e.message}")
            }
        }

        // 解密按钮
        btnDecrypt.setOnClickListener {
            // 优先使用输入框内容，其次剪贴板
            val inputText = etInput.text.toString()
            val clipText = ClipboardHelper.getFromClipboard(this)
            val textToDecrypt = when {
                inputText.isNotBlank() && CryptoEngine.isEncryptedText(inputText) -> inputText
                inputText.isNotBlank() -> {
                    CryptoEngine.extractEncryptedText(inputText) ?: run {
                        showToast("输入的文本不是有效密文")
                        return@setOnClickListener
                    }
                }
                clipText != null && CryptoEngine.isEncryptedText(clipText) -> {
                    etInput.setText(clipText)
                    clipText
                }
                clipText != null -> {
                    CryptoEngine.extractEncryptedText(clipText) ?: run {
                        showToast("未找到有效密文")
                        return@setOnClickListener
                    }
                }
                else -> {
                    showToast("请输入密文或复制密文到剪贴板")
                    return@setOnClickListener
                }
            }

            val password = getSelectedPassword(spinnerContact, contacts)
            if (password == null) {
                showToast("请先添加联系人")
                return@setOnClickListener
            }

            try {
                val extracted = CryptoEngine.extractEncryptedText(textToDecrypt) ?: textToDecrypt
                val decrypted = CryptoEngine.decrypt(extracted, password)
                tvResult.text = decrypted
                showToast("✅ 解密成功")
            } catch (e: Exception) {
                showToast("❌ 解密失败，请检查密钥是否正确")
            }
        }

        // 复制结果
        btnCopy.setOnClickListener {
            val result = tvResult.text.toString()
            if (result.isNotBlank()) {
                ClipboardHelper.copyToClipboard(this, result)
                showToast("已复制到剪贴板")
            }
        }

        // 自动填入结果到聊天输入框
        btnAutoFill.setOnClickListener {
            val result = tvResult.text.toString()
            if (result.isNotBlank()) {
                CryptoAccessibilityService.requestFillText(result)
                showToast("已自动填入")
                hidePanel()
            }
        }

        // 关闭面板
        btnClose.setOnClickListener { hidePanel() }

        // 点击面板外部关闭
        panel.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hidePanel()
                true
            } else false
        }
    }

    /**
     * 面板打开时自动检测剪贴板中的密文
     */
    private fun autoDetectClipboard(panel: View) {
        val etInput = panel.findViewById<EditText>(R.id.et_input)
        val clipText = ClipboardHelper.getFromClipboard(this)
        if (clipText != null && CryptoEngine.isEncryptedText(clipText)) {
            etInput.setText(clipText)
            showToast("🔒 检测到剪贴板中的密文")
        }
    }

    private fun loadContactsSpinner(spinner: Spinner, contacts: List<com.encryptchat.model.Contact>) {
        val names = if (contacts.isEmpty()) listOf("请先添加联系人")
        else contacts.map { it.name }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.adapter = adapter

        // 恢复上次选择
        val selectedId = contactKeyManager.getSelectedContactId()
        val selectedIndex = contacts.indexOfFirst { it.id == selectedId }
        if (selectedIndex >= 0) spinner.setSelection(selectedIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos < contacts.size) {
                    contactKeyManager.setSelectedContactId(contacts[pos].id)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun getSelectedPassword(spinner: Spinner, contacts: List<com.encryptchat.model.Contact>): String? {
        if (contacts.isEmpty()) return null
        val pos = spinner.selectedItemPosition
        return if (pos in contacts.indices) contacts[pos].password else null
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
