package com.encryptchat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.encryptchat.model.Contact
import com.encryptchat.service.CryptoAccessibilityService
import com.encryptchat.service.FloatingBubbleService
import com.encryptchat.storage.ContactKeyManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var contactKeyManager: ContactKeyManager
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var switchBubble: SwitchMaterial
    private lateinit var tvAccessibilityStatus: TextView

    companion object {
        private const val REQUEST_OVERLAY = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        contactKeyManager = ContactKeyManager(this)
        initViews()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun initViews() {
        // 悬浮窗开关
        switchBubble = findViewById(R.id.switch_bubble)
        switchBubble.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startBubbleService()
            } else {
                FloatingBubbleService.stop(this)
            }
        }

        // 无障碍服务状态
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status)
        val btnAccessibility = findViewById<Button>(R.id.btn_open_accessibility)
        btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // 联系人列表
        val recyclerView = findViewById<RecyclerView>(R.id.rv_contacts)
        contactAdapter = ContactAdapter(
            onEdit = { contact -> showEditContactDialog(contact) },
            onDelete = { contact -> deleteContact(contact) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = contactAdapter

        // 添加联系人 FAB
        val fabAdd = findViewById<FloatingActionButton>(R.id.fab_add_contact)
        fabAdd.setOnClickListener {
            showAddContactDialog()
        }
    }

    private fun updateServiceStatus() {
        switchBubble.isChecked = FloatingBubbleService.isRunning()

        val isAccessibilityOn = CryptoAccessibilityService.isRunning()
        tvAccessibilityStatus.text = if (isAccessibilityOn) "✅ 已开启" else "❌ 未开启"
        tvAccessibilityStatus.setTextColor(
            if (isAccessibilityOn) getColor(R.color.green_accent)
            else getColor(R.color.red_accent)
        )
    }

    // ─── 悬浮窗服务 ──────────────────────────────────────

    private fun startBubbleService() {
        if (!Settings.canDrawOverlays(this)) {
            // 请求悬浮窗权限
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
            switchBubble.isChecked = false
            return
        }
        FloatingBubbleService.start(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                FloatingBubbleService.start(this)
                switchBubble.isChecked = true
            } else {
                Toast.makeText(this, "需要悬浮窗权限才能使用", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "请找到「加密聊天」并开启", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── 联系人管理 ──────────────────────────────────────

    private fun loadContacts() {
        contactAdapter.submitList(contactKeyManager.getAllContacts())
    }

    private fun showAddContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_contact_name)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_contact_password)

        AlertDialog.Builder(this, R.style.Theme_EncryptChat_Dialog)
            .setTitle("添加联系人")
            .setView(dialogView)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (name.isBlank() || password.isBlank()) {
                    Toast.makeText(this, "名称和密码不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                contactKeyManager.createContact(name, password)
                loadContacts()
                Toast.makeText(this, "已添加 $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditContactDialog(contact: Contact) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_contact, null)
        val etName = dialogView.findViewById<EditText>(R.id.et_contact_name)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_contact_password)

        etName.setText(contact.name)
        etPassword.setText(contact.password)

        AlertDialog.Builder(this, R.style.Theme_EncryptChat_Dialog)
            .setTitle("编辑联系人")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val password = etPassword.text.toString().trim()
                if (name.isBlank() || password.isBlank()) {
                    Toast.makeText(this, "名称和密码不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                contactKeyManager.saveContact(contact.copy(name = name, password = password))
                loadContacts()
                Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteContact(contact: Contact) {
        AlertDialog.Builder(this, R.style.Theme_EncryptChat_Dialog)
            .setTitle("删除联系人")
            .setMessage("确定要删除「${contact.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                contactKeyManager.deleteContact(contact.id)
                loadContacts()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ─── 联系人列表适配器 ─────────────────────────────────

    inner class ContactAdapter(
        private val onEdit: (Contact) -> Unit,
        private val onDelete: (Contact) -> Unit
    ) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

        private var contacts: List<Contact> = emptyList()

        fun submitList(list: List<Contact>) {
            contacts = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_contact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(contacts[position])
        }

        override fun getItemCount() = contacts.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tv_contact_name)
            private val tvPasswordHint: TextView = itemView.findViewById(R.id.tv_password_hint)
            private val btnEdit: ImageButton = itemView.findViewById(R.id.btn_edit_contact)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_contact)

            fun bind(contact: Contact) {
                tvName.text = contact.name
                // 密码只显示前2位和长度提示
                val hint = if (contact.password.length > 2) {
                    "${contact.password.take(2)}${"•".repeat(minOf(contact.password.length - 2, 8))}"
                } else "••••"
                tvPasswordHint.text = "密钥: $hint"

                btnEdit.setOnClickListener { onEdit(contact) }
                btnDelete.setOnClickListener { onDelete(contact) }
            }
        }
    }
}
