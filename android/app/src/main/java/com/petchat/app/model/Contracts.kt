package com.petchat.app.model

import kotlinx.serialization.Serializable

/**
 * 宠物动作类型：与后端 contracts.js / 客户端 PetEngine 一一对应。
 * 客户端据此切换预制动画，仅做"标签驱动"，不实时生成视频。
 */
@Serializable
enum class ActionType {
    IDLE, HAPPY, SURPRISED, THINKING, SAD, GREETING
}

/** 宠物对消息的反应：动作标签 + 气泡文案（后端 LLM 只产出此结构） */
@Serializable
data class Reaction(
    val action: ActionType,
    val text: String? = null
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val phone: String? = null
)

/** 宠物档案：与后端 PetProfile 字段对齐 */
@Serializable
data class PetProfile(
    val id: String,
    val ownerId: String,
    val name: String,
    val species: String = "",
    val personality: String = "",
    val trait: String = "",
    val style: String = "",
    val appearancePrompt: String = "",
    val imageUrl: String = "",
    val createdAt: Long = 0L
)

/** REST 响应模型 */
@Serializable
data class AuthResponse(val token: String, val user: User)

/** 发送短信验证码响应（devMode=true 时回传 devCode 便于本地联调） */
@Serializable
data class SendCodeResponse(
    val ok: Boolean,
    val devMode: Boolean = false,
    val devCode: String? = null
)

@Serializable
data class PetsResponse(val pets: List<PetProfile>)

/** 创建宠物请求体（与后端 validatePetCreate 对应） */
@Serializable
data class CreatePetRequest(
    val name: String,
    val species: String = "cat",
    val personality: String = "",
    val trait: String = "sweet",
    val style: String = "chibi",
    val appearancePrompt: String = ""
)

@Serializable
data class CreatePetResponse(val pet: PetProfile)

/**
 * WebSocket 入站消息（服务端 -> 客户端）。
 * 好友发来消息时，服务端会附带接收方宠物的 reaction，
 * 以及发送方的宠物信息（senderPet*，用于悬浮宠物"变身"为对方宠物展示）。
 */
@Serializable
data class ServerMessage(
    val type: String,
    val id: String? = null,
    val from: String? = null,
    val fromName: String? = null,
    val text: String? = null,
    val createdAt: Long? = null,
    val reaction: Reaction? = null,
    val senderPetName: String? = null,
    val senderPetImage: String? = null,
    val senderPetSpecies: String? = null
)

/** 最近会话（GET /api/messages/conversations） */
@Serializable
data class Conversation(
    val friendId: String,
    val friendName: String,
    val lastMessage: String = "",
    val lastAt: Long = 0L
)

/** 单条历史消息（GET /api/messages?with=） */
@Serializable
data class HistoryItem(
    val id: String = "",
    val from: String? = null,
    val text: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class FriendsResponse(val friends: List<User>)

@Serializable
data class ConversationsResponse(val conversations: List<Conversation>)

@Serializable
data class HistoryResponse(val messages: List<HistoryItem>)
