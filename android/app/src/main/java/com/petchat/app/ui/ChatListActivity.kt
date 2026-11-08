package com.petchat.app.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import coil.compose.AsyncImage
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.petchat.app.MainActivity
import com.petchat.app.R
import com.petchat.app.data.ApiClient
import com.petchat.app.data.PetPresets
import com.petchat.app.data.SessionStore
import com.petchat.app.floating.FloatingPetService
import com.petchat.app.model.Conversation
import com.petchat.app.model.PetProfile
import com.petchat.app.ui.PetChatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 聊天主页（首屏）：底部导航两个 Tab ——「我的好友」与「宠物管理」。
 *
 * - 首屏（我的好友 Tab，默认）：仅展示【用户自己的宠物卡片】+【对方的会话列表】；
 *   顶部右上角为「添加好友」入口（弹窗输入），左上角为设置齿轮。不展示其它冗余入口。
 * - 宠物管理 Tab：嵌入 PetProfileScreen（创建 / 管理宠物形象，点选 3D 形象库即时生效）。
 *
 * 桌面宠物的「开启 / 关闭」已迁移到设置页（SettingsActivity）的总开关，并由
 * [SessionStore.petEnabled] 持久化；本页在创建时若开关开启且已授予悬浮窗权限则自动恢复。
 */
class ChatListActivity : ComponentActivity() {

    private lateinit var session: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionStore(this)
        val server = session.serverBase
        val token = session.token
        if (server.isNullOrBlank() || token.isNullOrBlank()) {
            goLogin()
            return
        }

        // 若用户在设置中开启了桌面宠物，且已授予悬浮窗权限，则自动启动
        // （让设置里的总开关在 App 重启后依然生效）
        if (session.petEnabled &&
            !FloatingPetService.isRunning &&
            Settings.canDrawOverlays(this)
        ) {
            val pet = session.getPet()
            FloatingPetService.start(
                context = this,
                serverBase = server,
                token = token,
                petImageUrl = pet?.imageUrl,
                petName = pet?.name ?: "宠物",
                petPresetRes = PetPresets.resOf(session.presetId)
            )
        }

        setContent {
            PetChatTheme {
                ChatListScreen(
                    activity = this,
                    session = session,
                    serverBase = server,
                    token = token,
                    lifecycle = lifecycle,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onOpenChat = { fid, fname -> openChat(fid, fname) }
                )
            }
        }
    }

    private fun goLogin() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun openChat(friendId: String, friendName: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_FRIEND_ID, friendId)
            putExtra(ChatActivity.EXTRA_FRIEND_NAME, friendName)
        }
        startActivity(intent)
    }
}

@Composable
private fun ChatListScreen(
    activity: ComponentActivity,
    session: SessionStore,
    serverBase: String,
    token: String,
    lifecycle: Lifecycle,
    onOpenSettings: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = 我的好友（首屏），1 = 宠物管理

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // 悬浮胶囊液态玻璃导航栏：整条栏是一块可折射的玻璃镜片，
            // 半透明底色让背后流动的光斑透出来，选中项呈内嵌镜片感。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    elevation = 22.dp
                ) {
                    NavigationBar(
                        modifier = Modifier.height(64.dp),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Image(
                                    painter = painterResource(R.drawable.ic_friends),
                                    contentDescription = "我的好友",
                                    modifier = Modifier.size(if (selectedTab == 0) 30.dp else 26.dp)
                                )
                            },
                            label = { Text("我的好友") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.White.copy(alpha = 0.42f),
                                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                unselectedTextColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                Image(
                                    painter = painterResource(R.drawable.ic_pet_mascot),
                                    contentDescription = "我的宠物",
                                    modifier = Modifier.size(if (selectedTab == 1) 30.dp else 26.dp)
                                )
                            },
                            label = { Text("我的宠物") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.White.copy(alpha = 0.42f),
                                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                unselectedTextColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Image(
                                    painter = painterResource(R.drawable.ic_manage),
                                    contentDescription = "宠物管理",
                                    modifier = Modifier.size(if (selectedTab == 2) 30.dp else 26.dp)
                                )
                            },
                            label = { Text("宠物管理") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.White.copy(alpha = 0.42f),
                                unselectedIconColor = MaterialTheme.colorScheme.secondary,
                                unselectedTextColor = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> FriendsTab(
                    activity = activity,
                    serverBase = serverBase,
                    token = token,
                    lifecycle = lifecycle,
                    onOpenSettings = onOpenSettings,
                    onOpenChat = onOpenChat
                )
                1 -> MyPetTab(
                    session = session,
                    onGoManage = { selectedTab = 2 }
                )
                2 -> PetManageTab(
                    session = session,
                    serverBase = serverBase,
                    token = token,
                    activity = activity
                )
            }
        }
    }
}

/**
 * Tab 1 · 我的好友（首屏）：自己的宠物卡片 + 对方会话列表。
 * 顶部右上角为「添加好友」入口（弹窗），左上角为设置齿轮。
 */
@Composable
private fun FriendsTab(
    activity: ComponentActivity,
    serverBase: String,
    token: String,
    lifecycle: Lifecycle,
    onOpenSettings: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    fun load() {
        scope.launch(Dispatchers.IO) {
            runCatching { ApiClient(serverBase).conversations(token) }
                .onSuccess { withContext(Dispatchers.Main) { conversations = it } }
        }
    }

    // 首次进入 + 每次 onResume（从聊天页返回）刷新会话列表
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) load()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    WaterBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
        // 顶部栏：左=设置齿轮，中=标题，右=添加好友入口
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(onClick = onOpenSettings) {
                Text("⚙", fontSize = 19.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("消息", fontSize = 22.sp, color = scheme.onBackground)
            Spacer(Modifier.weight(1f))
            GlassIconButton(
                onClick = {
                    activity.startActivity(Intent(activity, QrFriendActivity::class.java))
                }
            ) {
                Text("📷", fontSize = 18.sp)
            }
            Spacer(Modifier.width(8.dp))
            GlassTextButton(onClick = { showAddDialog = true }) {
                Text("＋ 添加好友", fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        Text("会话", fontSize = 14.sp, color = scheme.secondary)
        Spacer(Modifier.height(8.dp))

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无会话，点右上角添加好友吧～", color = scheme.secondary, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversations) { conv ->
                    ConversationRow(conv) { onOpenChat(conv.friendId, conv.friendName) }
                }
            }
        }
        } // 关闭 Column
    } // 关闭 WaterBackground

    if (showAddDialog) {
        AddFriendDialog(
            serverBase = serverBase,
            token = token,
            onDismiss = { showAddDialog = false },
            onAdded = {
                showAddDialog = false
                load()
            }
        )
    }
}

/**
 * Tab 2 · 宠物管理：直接嵌入已有的 PetProfileScreen（创建 / 管理宠物）。
 * showBack=false：作为 Tab 页时不显示返回键（底部导航负责切换）。
 * activity 为非空时用于「实时刷新桌面宠物形象」。
 */
@Composable
private fun PetManageTab(
    session: SessionStore,
    serverBase: String,
    token: String,
    activity: ComponentActivity?
) {
    PetProfileScreen(
        serverBase = serverBase,
        token = token,
        existingPet = session.getPet(),
        currentPresetId = session.presetId,
        showBack = false,
        onSaved = { session.savePet(it) },
        onPickPreset = { id ->
            session.presetId = id
            val pet = session.getPet()
            if (FloatingPetService.isRunning) {
                FloatingPetService.start(
                    context = activity ?: return@PetProfileScreen,
                    serverBase = serverBase,
                    token = token,
                    petImageUrl = pet?.imageUrl,
                    petName = pet?.name ?: "宠物",
                    petPresetRes = PetPresets.resOf(id)
                )
            }
        }
    )
}

/** 添加好友弹窗：输入对方用户名 -> 查找 -> 加好友 */
@Composable
private fun AddFriendDialog(
    serverBase: String,
    token: String,
    onDismiss: () -> Unit,
    onAdded: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    GlassAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        confirmButton = {
            GlassButton(
                onClick = {
                    val target = name.trim()
                    if (target.isBlank() || busy) return@GlassButton
                    busy = true
                    error = null
                    scope.launch(Dispatchers.IO) {
                        try {
                            val api = ApiClient(serverBase)
                            val user = api.findUser(token, target)
                            if (user == null) {
                                withContext(Dispatchers.Main) { error = "找不到该用户" }
                            } else if (!api.addFriend(token, user.id)) {
                                withContext(Dispatchers.Main) { error = "添加失败（可能已是好友）" }
                            } else {
                                withContext(Dispatchers.Main) { onAdded() }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { error = e.message }
                        } finally {
                            withContext(Dispatchers.Main) { busy = false }
                        }
                    }
                },
                enabled = !busy,
                horizontalPadding = 26.dp,
                verticalPadding = 11.dp
            ) {
                if (busy) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White
                ) else Text("添加")
            }
        },
        dismissButton = {
            GlassTextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
        },
        title = { Text("添加好友", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground) },
        text = {
            Column {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("对方用户名") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(error ?: "", color = Color(0xFFFF7A7A), fontSize = 12.sp)
                }
            }
        }
    )
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(conv.friendName.take(1), color = scheme.primary, fontSize = 18.sp)
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(conv.friendName, color = scheme.onBackground, fontSize = 15.sp)
                Text(
                    conv.lastMessage.ifBlank { "（暂无消息）" },
                    color = scheme.secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 我的宠物：展示宠物完整形象（全貌）+ 性格特点介绍文字 */
@Composable
private fun MyPetTab(
    session: SessionStore,
    onGoManage: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val pet = session.getPet()
    WaterBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(6.dp))
            Text("我的宠物", fontSize = 22.sp, color = scheme.onBackground)
            Spacer(Modifier.height(18.dp))

            if (pet == null) {
                Spacer(Modifier.height(48.dp))
                Text("还没有宠物哦~", fontSize = 16.sp, color = scheme.secondary)
                Spacer(Modifier.height(12.dp))
                GlassButton(onClick = onGoManage) { Text("去创建宠物") }
            } else {
                GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左侧：宠物完整形象（全貌，不裁剪成圆形）
                        Box(
                            modifier = Modifier
                                .weight(0.42f)
                                .height(200.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.30f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PetFullFigure(pet = pet, presetId = session.presetId)
                        }
                        Spacer(Modifier.width(14.dp))
                        // 右侧：性格特点介绍文字
                        Column(modifier = Modifier.weight(0.58f)) {
                            Text(
                                pet.name.ifBlank { "我的宠物" },
                                fontSize = 20.sp,
                                color = scheme.onBackground
                            )
                            Spacer(Modifier.height(10.dp))
                            val traitLabel = TRAIT_LABELS[pet.trait] ?: pet.trait.ifBlank { "软萌" }
                            InfoLine("性格特质", traitLabel)
                            if (pet.species.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                InfoLine("物种", speciesLabel(pet.species))
                            }
                            Spacer(Modifier.height(10.dp))
                            val desc = pet.personality.ifBlank {
                                com.petchat.app.data.PetPresets.personaOf(session.presetId).greeting
                            }
                            Text(
                                desc,
                                fontSize = 13.sp,
                                color = scheme.secondary,
                                lineHeight = 19.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                GlassTextButton(onClick = onGoManage) { Text("编辑宠物形象 ›") }
            }
        }
    }
}

/** 宠物完整形象（全貌）：远程图优先，否则取本地预设 / 默认吉祥物，按原比例铺满 */
@Composable
private fun PetFullFigure(pet: PetProfile, presetId: String?) {
    val resId = if (pet.imageUrl.isBlank()) com.petchat.app.data.PetPresets.resOf(presetId) else null
    when {
        pet.imageUrl.isNotBlank() -> AsyncImage(
            model = pet.imageUrl,
            contentDescription = "宠物完整形象",
            modifier = Modifier.fillMaxSize().padding(10.dp),
            contentScale = ContentScale.Fit
        )
        resId != null -> Image(
            painter = painterResource(resId),
            contentDescription = "宠物完整形象",
            modifier = Modifier.fillMaxSize().padding(10.dp),
            contentScale = ContentScale.Fit
        )
        else -> Image(
            painter = painterResource(R.drawable.ic_mascot),
            contentDescription = "宠物完整形象",
            modifier = Modifier.fillMaxSize().padding(10.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = scheme.secondary)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 14.sp, color = scheme.primary)
    }
}

private val TRAIT_LABELS = mapOf(
    "sweet" to "软萌", "tsundere" to "傲娇", "genki" to "元气",
    "calm" to "沉稳", "tsukkomi" to "毒舌"
)

private fun speciesLabel(s: String): String = when (s) {
    "cat" -> "猫"; "dog" -> "狗"; "rabbit" -> "兔"; "fox" -> "狐";
    "bear" -> "熊"; "bird" -> "鸟"; "dragon" -> "龙"; "red_panda" -> "小熊猫";
    else -> s
}
