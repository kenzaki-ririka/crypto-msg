package com.encryptchat.model

/**
 * 联系人数据模型
 * @param id 唯一标识
 * @param name 联系人名称（显示用）
 * @param password 与该联系人共享的加密密码
 * @param createdAt 创建时间戳
 */
data class Contact(
    val id: String,
    val name: String,
    val password: String,
    val createdAt: Long = System.currentTimeMillis()
)
