package com.encryptchat.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.encryptchat.model.Contact
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 联系人密钥管理器
 * 使用 EncryptedSharedPreferences 安全存储联系人及其密码
 */
class ContactKeyManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "encrypted_contacts",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * 添加或更新联系人
     */
    fun saveContact(contact: Contact) {
        val contacts = getAllContacts().toMutableList()
        contacts.removeAll { it.id == contact.id }
        contacts.add(contact)
        saveAllContacts(contacts)
    }

    /**
     * 创建新联系人
     */
    fun createContact(name: String, password: String): Contact {
        val contact = Contact(
            id = UUID.randomUUID().toString(),
            name = name,
            password = password
        )
        saveContact(contact)
        return contact
    }

    /**
     * 删除联系人
     */
    fun deleteContact(id: String) {
        val contacts = getAllContacts().toMutableList()
        contacts.removeAll { it.id == id }
        saveAllContacts(contacts)
    }

    /**
     * 获取指定联系人
     */
    fun getContact(id: String): Contact? {
        return getAllContacts().find { it.id == id }
    }

    /**
     * 获取所有联系人
     */
    fun getAllContacts(): List<Contact> {
        val json = prefs.getString("contacts", "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<Contact>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                Contact(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    password = obj.getString("password"),
                    createdAt = obj.optLong("createdAt", 0)
                )
            )
        }
        return list
    }

    private fun saveAllContacts(contacts: List<Contact>) {
        val arr = JSONArray()
        contacts.forEach { contact ->
            arr.put(JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("password", contact.password)
                put("createdAt", contact.createdAt)
            })
        }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }

    /**
     * 获取当前选中的联系人ID
     */
    fun getSelectedContactId(): String? = prefs.getString("selected_contact", null)

    /**
     * 设置当前选中的联系人ID
     */
    fun setSelectedContactId(id: String?) {
        prefs.edit().putString("selected_contact", id ?: "").apply()
    }
}
