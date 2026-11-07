package com.petchat.app.data

import android.content.Context
import android.content.SharedPreferences
import com.petchat.app.model.PetProfile
import kotlinx.serialization.json.Json

/**
 * 会话本地存储（轻量 SharedPreferences）。
 * 保存 token / 服务器地址 / 当前用户宠物，供悬浮服务在后台读取。
 */
class SessionStore(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("petchat_session", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    var token: String?
        get() = sp.getString(KEY_TOKEN, null)
        set(v) = sp.edit().putString(KEY_TOKEN, v).apply()

    var serverBase: String?
        get() = sp.getString(KEY_SERVER, null)
        set(v) = sp.edit().putString(KEY_SERVER, v).apply()

    var userId: String?
        get() = sp.getString(KEY_USER_ID, null)
        set(v) = sp.edit().putString(KEY_USER_ID, v).apply()

    // 记住登录信息：用于重启 App 后自动登录（明文存储，仅本地调试用途）
    var username: String?
        get() = sp.getString(KEY_USERNAME, null)
        set(v) = sp.edit().putString(KEY_USERNAME, v).apply()

    /** 登录用户的展示名（用于好友二维码与添加确认展示） */
    var displayName: String?
        get() = sp.getString(KEY_DISPLAY_NAME, null)
        set(v) = sp.edit().putString(KEY_DISPLAY_NAME, v).apply()

    /** 登录用户的手机号（手机号注册/登录时保存） */
    var phone: String?
        get() = sp.getString(KEY_PHONE, null)
        set(v) = sp.edit().putString(KEY_PHONE, v).apply()

    var password: String?
        get() = sp.getString(KEY_PASSWORD, null)
        set(v) = sp.edit().putString(KEY_PASSWORD, v).apply()

    /** 选中的 3D 预设宠物形象 id（null = 使用 AI 生成形象） */
    var presetId: String?
        get() = sp.getString(KEY_PRESET, null)
        set(v) = sp.edit().putString(KEY_PRESET, v).apply()

    /** 桌面宠物总开关（用户在设置页控制；App 重启后依此自动恢复） */
    var petEnabled: Boolean
        get() = sp.getBoolean(KEY_PET_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_PET_ENABLED, v).apply()

    private var petJson: String?
        get() = sp.getString(KEY_PET, null)
        set(v) = sp.edit().putString(KEY_PET, v).apply()

    fun savePet(pet: PetProfile) {
        petJson = json.encodeToString(PetProfile.serializer(), pet)
    }

    fun getPet(): PetProfile? =
        petJson?.let { runCatching { json.decodeFromString(PetProfile.serializer(), it) }.getOrNull() }

    fun clear() = sp.edit().clear().apply()

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_SERVER = "server_base"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PHONE = "phone"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PET = "pet"
        private const val KEY_PRESET = "pet_preset_id"
        private const val KEY_PET_ENABLED = "pet_enabled"
    }
}
