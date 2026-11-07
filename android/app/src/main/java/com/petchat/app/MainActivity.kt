package com.petchat.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.petchat.app.data.ApiClient
import com.petchat.app.data.SessionStore
import com.petchat.app.model.PetProfile
import com.petchat.app.model.User
import com.petchat.app.ui.PetChatTheme
import com.petchat.app.ui.ChatListActivity
import com.petchat.app.ui.GlassButton
import com.petchat.app.ui.GlassTextButton
import com.petchat.app.ui.GlassTextField
import com.petchat.app.ui.WaterBackground
import com.petchat.app.ui.WaterDropMascot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 入口 Activity：登录 / 注册 -> 拉取宠物 -> 申请悬浮窗权限 -> 启动桌面宠物服务。
 *
 * 登录界面（主流 App 风格）：
 * - 登录 / 注册 分段 Tab 切换，单页完成两种模式
 * - 服务器地址隐藏（默认取本地保存值或 http://localhost:3000，修改入口在「设置」页，
 *   登录页底部保留折叠的「高级选项」供调试展开）
 * - 密码可见性切换、用户名一键清空、注册模式带格式提示
 */
class MainActivity : ComponentActivity() {

    private lateinit var session: SessionStore
    private var loading by mutableStateOf(false)
    private var autoLoginPending by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionStore(this)
        // 已登录过：标记自动登录，启动后静默校验并直接进主页
        autoLoginPending = session.token != null

        setContent {
            PetChatTheme {
                LoginScreen(
                    initialServer = session.serverBase ?: "http://localhost:3000",
                    initialUsername = session.username ?: "",
                    initialPassword = session.password ?: "",
                    autoLoginPending = autoLoginPending,
                    loading = loading,
                    onSubmit = { server, username, password, displayName, isRegister, usePhone, phone, code ->
                        authenticate(server, username, password, displayName, isRegister, usePhone, phone, code)
                    },
                    onSendCode = { phone -> sendCode(phone) }
                )
            }
        }

        if (autoLoginPending) {
            autoLogin()
        }
    }

    private fun authenticate(
        server: String,
        username: String,
        password: String,
        displayName: String,
        isRegister: Boolean,
        usePhone: Boolean,
        phone: String,
        code: String
    ) {
        loading = true
        val api = ApiClient(server)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val auth = when {
                    usePhone && isRegister ->
                        api.registerPhone(phone, code, password,
                            username = if (username.isBlank()) phone else username,
                            displayName = displayName)
                    usePhone ->
                        api.loginPhone(phone, password)
                    isRegister ->
                        api.register(username, displayName.ifBlank { username }, password)
                    else ->
                        api.login(username, password)
                }
                val pet = runCatching { api.myPets(auth.token).firstOrNull() }.getOrNull()
                withContext(Dispatchers.Main) {
                    loading = false
                    onAuthenticated(server, auth.token, auth.user, pet, username, password, auth.user.phone)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loading = false
                    Toast.makeText(this@MainActivity, "操作失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** 发送短信验证码（手机号注册用），dev 模式回传验证码并以 Toast 提示便于联调 */
    private fun sendCode(phone: String) {
        val server = (session.serverBase ?: "http://localhost:3000").trim().removeSuffix("/")
        val api = ApiClient(server)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val r = api.sendCode(phone)
                withContext(Dispatchers.Main) {
                    if (r.devMode && !r.devCode.isNullOrBlank()) {
                        Toast.makeText(this@MainActivity,
                            "开发模式验证码：${r.devCode}（生产环境由短信下发）", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "验证码已发送，请查收短信", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "发送失败：${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 自动登录：用已保存的 token 试探有效性；失效则尝试用记住的账号密码静默重登；
     * 都不行才退回登录页（并预填账号密码）。
     */
    private fun autoLogin() {
        val server = session.serverBase ?: "http://localhost:3000"
        val token = session.token
        if (token.isNullOrBlank()) {
            autoLoginPending = false
            return
        }
        val api = ApiClient(server)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val pet = runCatching { api.myPets(token).firstOrNull() }.getOrNull()
                pet?.let { session.savePet(it) }
                withContext(Dispatchers.Main) { enterHome() }
            } catch (e: Exception) {
                // token 失效：用记住的账号密码静默重登
                val u = session.username
                val p = session.password
                if (!u.isNullOrBlank() && !p.isNullOrBlank()) {
                    try {
                        val auth = api.login(u, p)
                        val pet = runCatching { api.myPets(auth.token).firstOrNull() }.getOrNull()
                        withContext(Dispatchers.Main) {
                            onAuthenticated(server, auth.token, auth.user, pet, u, p)
                        }
                    } catch (e2: Exception) {
                        withContext(Dispatchers.Main) { autoLoginPending = false }
                    }
                } else {
                    withContext(Dispatchers.Main) { autoLoginPending = false }
                }
            }
        }
    }

    private fun onAuthenticated(
        server: String,
        token: String,
        user: User,
        pet: PetProfile?,
        username: String,
        password: String,
        phone: String? = null
    ) {
        session.serverBase = server
        session.token = token
        session.userId = user.id
        session.username = user.username
        session.displayName = user.displayName
        session.phone = phone ?: user.phone
        session.password = password
        pet?.let { session.savePet(it) }
        enterHome()
    }

    private fun enterHome() {
        // 登录成功后进入聊天主页；桌面宠物由主页按需开启
        startActivity(Intent(this, ChatListActivity::class.java))
        finish()
    }
}

@Composable
private fun LoginScreen(
    initialServer: String,
    initialUsername: String,
    initialPassword: String,
    autoLoginPending: Boolean,
    loading: Boolean,
    onSubmit: (server: String, username: String, password: String, displayName: String, isRegister: Boolean, usePhone: Boolean, phone: String, code: String) -> Unit,
    onSendCode: (phone: String) -> Unit
) {
    var server by remember { mutableStateOf(initialServer) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var displayName by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var usePhone by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeCooldown by remember { mutableStateOf(0) }

    // 验证码倒计时（60s 后可重发）
    LaunchedEffect(codeCooldown) {
        if (codeCooldown > 0) {
            delay(1000)
            codeCooldown--
        }
    }

    val scheme = MaterialTheme.colorScheme

    WaterBackground(Modifier.fillMaxSize().padding(horizontal = 28.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 浮动宠物吉祥物（主视觉）
            PetMascot()
            Spacer(Modifier.height(14.dp))

            Text(
                text = "宠物聊天",
                fontSize = 26.sp,
                color = scheme.onBackground
            )
            Text(
                text = if (isRegister) "注册账号，领养你的专属桌面宠物" else "欢迎回来，它在桌面上等你哦",
                fontSize = 13.sp,
                color = scheme.secondary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(24.dp))

            // 自动登录中：仅显示进度，不展示表单
            if (autoLoginPending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 3.dp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "正在自动登录…",
                    fontSize = 14.sp,
                    color = scheme.primary
                )
            } else {
                // 登录 / 注册 分段 Tab（主流 App 交互）
                AuthTabs(
                    isRegister = isRegister,
                    onSwitch = { isRegister = it }
                )
                Spacer(Modifier.height(12.dp))

                // 账号 / 手机号 切换
                GlassTextButton(onClick = { usePhone = !usePhone }) {
                    Text(
                        if (usePhone) "使用账号密码" else "使用手机号",
                        fontSize = 12.sp,
                        color = scheme.primary
                    )
                }
                Spacer(Modifier.height(16.dp))

                if (usePhone) {
                    // 手机号（带字数限制）
                    GlassTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 11) phone = it.filter { c -> c.isDigit() } },
                        label = { Text("手机号") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    // 注册模式：验证码 + 获取按钮
                    if (isRegister) {
                        GlassTextField(
                            value = code,
                            onValueChange = { if (it.length <= 6) code = it.filter { c -> c.isDigit() } },
                            label = { Text("验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Text(
                                    if (codeCooldown > 0) "重新获取($codeCooldown)" else "获取验证码",
                                    fontSize = 12.sp,
                                    color = if (codeCooldown > 0) scheme.secondary else scheme.primary,
                                    modifier = Modifier
                                        .then(if (codeCooldown > 0) Modifier else Modifier.clickable {
                                            if (phone.length == 11) {
                                                onSendCode(phone)
                                                codeCooldown = 60
                                            }
                                        })
                                        .padding(horizontal = 10.dp)
                                )
                            }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    // 用户名（带一键清空）
                    GlassTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (username.isNotEmpty()) {
                                Text(
                                    "✕",
                                    fontSize = 14.sp,
                                    color = scheme.secondary,
                                    modifier = Modifier
                                        .clickable { username = "" }
                                        .padding(horizontal = 10.dp)
                                )
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                }

                // 密码（带可见性切换）
                GlassTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        autoCorrect = false,
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Text(
                            if (passwordVisible) "隐藏" else "显示",
                            fontSize = 12.sp,
                            color = scheme.primary,
                            modifier = Modifier
                                .clickable { passwordVisible = !passwordVisible }
                                .padding(horizontal = 10.dp)
                        )
                    }
                )

                // 注册模式：昵称 + 格式提示
                AnimatedVisibility(visible = isRegister) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        if (!usePhone) {
                            GlassTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("昵称（可选）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            text = if (usePhone) "需先获取并填写短信验证码；密码至少 6 位"
                                   else "用户名至少 3 个字符，密码至少 6 位",
                            fontSize = 11.sp,
                            color = scheme.secondary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                // 主按钮（大圆角，宠物化文案）
                val canSubmit = when {
                    loading -> false
                    usePhone -> phone.length == 11 && password.isNotBlank() && (!isRegister || code.length == 6)
                    else -> username.isNotBlank() && password.isNotBlank()
                }
                GlassButton(
                    onClick = {
                        val srv = server.trim().removeSuffix("/")
                        if (!canSubmit) return@GlassButton
                        onSubmit(srv, username.trim(), password, displayName.trim(), isRegister, usePhone, phone.trim(), code.trim())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = canSubmit,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("请稍候…", fontSize = 16.sp)
                    } else {
                        Text(
                            if (isRegister) "创建账号，领养宠物 🐾" else "进入宠物世界 🐾",
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 高级选项（默认折叠，隐藏服务器设置）
                GlassTextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(
                        if (showAdvanced) "收起高级选项" else "高级选项",
                        fontSize = 12.sp,
                        color = scheme.secondary
                    )
                }
                AnimatedVisibility(visible = showAdvanced) {
                    Column {
                        GlassTextField(
                            value = server,
                            onValueChange = { server = it },
                            label = { Text("服务器地址（调试用）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "日常使用无需修改；可在「设置」页切换服务器",
                            fontSize = 11.sp,
                            color = scheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 登录 / 注册 分段切换 Tab（3D 玻璃胶囊样式） */
@Composable
private fun AuthTabs(isRegister: Boolean, onSwitch: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth(0.62f)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(999.dp),
                ambientColor = scheme.primary.copy(alpha = 0.14f),
                spotColor = scheme.primary.copy(alpha = 0.22f)
            )
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.50f),
                        Color.White.copy(alpha = 0.22f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(999.dp))
            .padding(4.dp)
    ) {
        Row {
            AuthTab("登录", selected = !isRegister, modifier = Modifier.weight(1f)) { onSwitch(false) }
            AuthTab("注册", selected = isRegister, modifier = Modifier.weight(1f)) { onSwitch(true) }
        }
    }
}

@Composable
private fun AuthTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .then(
                if (selected) {
                    Modifier
                        .shadow(
                            elevation = 7.dp,
                            shape = shape,
                            ambientColor = scheme.primary.copy(alpha = 0.30f),
                            spotColor = scheme.primary.copy(alpha = 0.45f)
                        )
                } else Modifier
            )
            .clip(shape)
            .background(
                if (selected) {
                    Brush.verticalGradient(
                        listOf(
                            scheme.primary.copy(alpha = 0.98f),
                            scheme.primary,
                            scheme.primary.copy(alpha = 0.86f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Transparent)
                    )
                }
            )
            .then(
                if (selected) {
                    Modifier.border(1.2.dp, Color.White.copy(alpha = 0.6f), shape)
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (selected) Color.White else scheme.secondary
        )
    }
}

@Composable
private fun PetMascot() {
    // 升级为与闪屏一致的 3D 水滴动画（浮动 / 呼吸 / 阴影 / 高光 / 眨眼）
    WaterDropMascot(size = 130.dp, showShadow = true)
}
