package com.petchat.app.data

import com.petchat.app.model.AuthResponse
import com.petchat.app.model.CreatePetRequest
import com.petchat.app.model.CreatePetResponse
import com.petchat.app.model.Conversation
import com.petchat.app.model.ConversationsResponse
import com.petchat.app.model.FriendsResponse
import com.petchat.app.model.HistoryItem
import com.petchat.app.model.HistoryResponse
import com.petchat.app.model.PetProfile
import com.petchat.app.model.PetsResponse
import com.petchat.app.model.SendCodeResponse
import com.petchat.app.model.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * 轻量 REST 客户端（OkHttp + kotlinx.serialization）。
 * 仅覆盖悬浮层启动所需：登录、拉取我的宠物。
 */
class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun login(username: String, password: String): AuthResponse {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(mediaType)
        return call("/api/auth/login", body).decode<AuthResponse>()
    }

    fun register(username: String, displayName: String, password: String): AuthResponse {
        val body = buildJsonObject {
            put("username", username)
            put("displayName", displayName)
            put("password", password)
        }.toString().toRequestBody(mediaType)
        return call("/api/auth/register", body).decode<AuthResponse>()
    }

    /** 发送短信验证码：POST /api/auth/send-code */
    fun sendCode(phone: String): SendCodeResponse {
        val body = buildJsonObject { put("phone", phone) }
            .toString().toRequestBody(mediaType)
        return call("/api/auth/send-code", body).decode<SendCodeResponse>()
    }

    /** 手机号 + 验证码 注册：POST /api/auth/register-phone（username/displayName 留空则由服务端默认） */
    fun registerPhone(
        phone: String,
        code: String,
        password: String,
        username: String = "",
        displayName: String = ""
    ): AuthResponse {
        val body = buildJsonObject {
            put("phone", phone)
            put("code", code)
            put("password", password)
            if (username.isNotBlank()) put("username", username)
            if (displayName.isNotBlank()) put("displayName", displayName)
        }.toString().toRequestBody(mediaType)
        return call("/api/auth/register-phone", body).decode<AuthResponse>()
    }

    /** 手机号 + 密码 登录：POST /api/auth/login-phone */
    fun loginPhone(phone: String, password: String): AuthResponse {
        val body = buildJsonObject {
            put("phone", phone)
            put("password", password)
        }.toString().toRequestBody(mediaType)
        return call("/api/auth/login-phone", body).decode<AuthResponse>()
    }

    fun myPets(token: String): List<PetProfile> {
        val req = Request.Builder()
            .url("$baseUrl/api/pets/mine")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw RuntimeException(resp.body?.string() ?: "拉取宠物失败(${resp.code})")
        }
        return json.decodeFromString(PetsResponse.serializer(), resp.body!!.string()).pets
    }

    /** 创建宠物：POST /api/pets，成功后服务端生成外观图并返回完整档案 */
    fun createPet(token: String, req: CreatePetRequest): PetProfile {
        val body = json.encodeToString(CreatePetRequest.serializer(), req)
            .toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/pets")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        val resp = client.newCall(request).execute()
        if (!resp.isSuccessful) {
            throw RuntimeException(resp.body?.string() ?: "创建宠物失败(${resp.code})")
        }
        return json.decodeFromString(CreatePetResponse.serializer(), resp.body!!.string()).pet
    }

    /** 修改宠物：PATCH /api/pets/:id（仅名字/性格/特质/重新生成外观） */
    fun patchPet(
        token: String,
        petId: String,
        name: String? = null,
        personality: String? = null,
        trait: String? = null,
        regenerateImage: Boolean = false
    ): PetProfile {
        val body = buildJsonObject {
            if (name != null) put("name", name)
            if (personality != null) put("personality", personality)
            if (trait != null) put("trait", trait)
            if (regenerateImage) put("regenerateImage", true)
        }.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("$baseUrl/api/pets/$petId")
            .header("Authorization", "Bearer $token")
            .patch(body)
            .build()
        val resp = client.newCall(request).execute()
        if (!resp.isSuccessful) {
            throw RuntimeException(resp.body?.string() ?: "更新宠物失败(${resp.code})")
        }
        return json.decodeFromString(CreatePetResponse.serializer(), resp.body!!.string()).pet
    }

    // 按用户名查找用户（聊天加好友用）
    fun findUser(token: String, username: String): User? {
        val enc = URLEncoder.encode(username, "UTF-8")
        val req = Request.Builder()
            .url("$baseUrl/api/auth/users/find?username=$enc")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) return null
        return runCatching {
            json.decodeFromString(User.serializer(), resp.body!!.string())
        }.getOrNull()
    }

    fun listFriends(token: String): List<User> {
        val req = Request.Builder()
            .url("$baseUrl/api/auth/friends")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw RuntimeException(resp.body?.string() ?: "拉取好友失败(${resp.code})")
        }
        return json.decodeFromString(FriendsResponse.serializer(), resp.body!!.string()).friends
    }

    fun addFriend(token: String, friendId: String): Boolean {
        val body = buildJsonObject { put("friendId", friendId) }
            .toString().toRequestBody(mediaType)
        val req = Request.Builder()
            .url("$baseUrl/api/auth/friends")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        return resp.isSuccessful
    }

    fun conversations(token: String): List<Conversation> {
        val req = Request.Builder()
            .url("$baseUrl/api/messages/conversations")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw RuntimeException(resp.body?.string() ?: "拉取会话失败(${resp.code})")
        }
        return json.decodeFromString(ConversationsResponse.serializer(), resp.body!!.string()).conversations
    }

    fun history(token: String, withId: String): List<HistoryItem> {
        val req = Request.Builder()
            .url("$baseUrl/api/messages?with=$withId")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw RuntimeException(resp.body?.string() ?: "拉取历史失败(${resp.code})")
        }
        return json.decodeFromString(HistoryResponse.serializer(), resp.body!!.string()).messages
    }

    private fun call(path: String, body: okhttp3.RequestBody): String {
        val req = Request.Builder().url("$baseUrl$path").post(body).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            throw RuntimeException(resp.body?.string() ?: "请求失败(${resp.code})")
        }
        return resp.body!!.string()
    }

    private inline fun <reified T> String.decode(): T =
        json.decodeFromString(serializer(), this)
}
