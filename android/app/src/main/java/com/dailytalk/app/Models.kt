package com.dailytalk.app

data class Profile(val uid: String, val displayName: String, val phone: String? = null)

data class Conversation(
    val id: String,
    val type: String,               // "direct" | "group"
    val members: List<String>,
    val admins: List<String>,
    val name: String?,
    val lastText: String?,
    val lastSender: String?,
    val updatedAt: Long,
    val unread: Map<String, Long>,
) {
    val isGroup get() = type == "group"
}

enum class SendState { SENT, SENDING, FAILED }

data class Message(
    val id: String,
    val senderId: String,
    val senderName: String,
    val kind: String,               // text | gif | image | video
    val text: String?,
    val url: String?,
    val createdAt: Long,
    val readBy: List<String>,
    val state: SendState = SendState.SENT,
    val progress: Float = 0f,       // 0..1 while a photo/video is uploading
)

data class Contact(val number: String, val name: String)

data class Status(
    val id: String,
    val authorId: String,
    val authorName: String,
    val kind: String,
    val text: String?,
    val createdAt: Long,
    val expiresAt: Long,
    val viewers: List<String>,
)

data class CallDoc(
    val id: String,
    val callerId: String,
    val participants: List<String>,
    val conversationId: String,
    val type: String,
    val state: String,
    val offerType: String?,
    val offerSdp: String?,
    val createdAt: Long,
)
