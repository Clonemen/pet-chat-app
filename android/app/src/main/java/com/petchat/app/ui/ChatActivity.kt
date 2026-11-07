package com.petchat.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.petchat.app.data.ApiClient
import com.petchat.app.data.SessionStore
import com.petchat.app.im.ChatMessage
import com.petchat.app.im.ChatWebSocketClient
import com.petchat.app.ui.PetChatTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 单聊界面：复用聊天层的 [ChatWebSocketClient] 收发消息，并用 REST 拉取历史。
 * 好友发来的消息会同时驱动（另一路）悬浮宠物做出反应——桌面层与聊天层是两个独立消费者。
 */
class ChatActivity : ComponentActivity() {

    private lateinit var session: SessionStore
    private var wsClient: ChatWebSocketClient? = null
    private var friendId: String = ""
    private var friendName: String = ""
    private val messages = mutableStateListOf<ChatMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionStore(this)
        friendId = intent.getStringExtra(EXTRA_FRIEND_ID) ?: ""
        friendName = intent.getStringExtra(EXTRA_FRIEND_NAME) ?: "好友"
        val server = session.serverBase
        val token = session.token
        if (server.isNullOrBlank() || token.isNullOrBlank() || friendId.isBlank()) {
            finish()
            return
        }

        // 连接聊天 WS；收到的消息归一到 messages（切主线程写入快照状态）
        wsClient = ChatWebSocketClient(
            onMessage = { msg -> runOnUiThread { messages.add(msg) } },
            onStatus = { /* 连接状态，可在此显示 */ }
        )
        wsClient?.connect(server.replaceFirst("^http".toRegex(), "ws"), token)

        setContent {
            PetChatTheme {
                ChatScreen(
                    friendName = friendName,
                    friendId = friendId,
                    myId = session.userId ?: "",
                    serverBase = server,
                    token = token,
                    messages = messages,
                    onBack = { finish() },
                    onSend = { text -> wsClient?.send(friendId, text) }
                )
            }
        }
    }

    override fun onDestroy() {
        wsClient?.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_FRIEND_ID = "friendId"
        const val EXTRA_FRIEND_NAME = "friendName"
    }
}

@Composable
private fun ChatScreen(
    friendName: String,
    friendId: String,
    myId: String,
    serverBase: String,
    token: String,
    messages: List<ChatMessage>,
    onBack: () -> Unit,
    onSend: (String) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // 拉取与该好友的历史消息
    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) {
                ApiClient(serverBase).history(token, friendId)
            }
        }.onSuccess { list ->
            withContext(Dispatchers.Main) {
                val snap = messages as androidx.compose.runtime.snapshots.SnapshotStateList<ChatMessage>
                snap.clear()
                snap.addAll(list.map {
                    ChatMessage(it.id, it.from, it.text, it.createdAt, fromMe = it.from == myId)
                })
            }
        }
    }

    // 新消息到达自动滚到底部
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    WaterBackground {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            // 顶栏：返回 · 头像 · 名称 + 在线状态
            ChatTopBar(name = friendName, onBack = onBack)

            // 消息列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(messages) { index, msg ->
                    val prev = messages.getOrNull(index - 1)
                    val showTime = prev == null ||
                        (msg.createdAt > 0 && prev.createdAt > 0 &&
                                msg.createdAt - prev.createdAt > 5 * 60 * 1000)
                    if (showTime) TimeStamp(msg.createdAt)
                    MessageRow(msg = msg, friendName = friendName, scheme = scheme)
                }
            }

            // 输入栏：胶囊输入框 + 圆形发送按钮
            ChatInputBar(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = {
                    val text = draft.trim()
                    if (text.isNotBlank()) {
                        onSend(text)
                        draft = ""
                    }
                }
            )
        }
    }
}

/* ----------------------------- 顶栏 ----------------------------- */

@Composable
private fun ChatTopBar(name: String, onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(onClick = onBack, size = 38.dp) {
            Text("‹", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Avatar(letter = name.firstOrNull()?.toString() ?: "?", size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground
            )
            Spacer(Modifier.width(0.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CD964))
                )
                Spacer(Modifier.width(5.dp))
                Text("在线", fontSize = 12.sp, color = scheme.secondary)
            }
        }
    }
}

/* ----------------------------- 消息行 / 气泡 ----------------------------- */

@Composable
private fun MessageRow(msg: ChatMessage, friendName: String, scheme: ColorScheme) {
    val mine = msg.fromMe
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            Avatar(
                letter = friendName.firstOrNull()?.toString() ?: "?",
                size = 30.dp
            )
            Spacer(Modifier.width(6.dp))
        }
        ChatBubble(text = msg.text, mine = mine, scheme = scheme)
    }
}

@Composable
private fun ChatBubble(text: String, mine: Boolean, scheme: ColorScheme) {
    // 不对称圆角模拟「气泡尾巴」：己方尾巴在右下，对方尾巴在左下
    val shape = if (mine) {
        RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 5.dp)
    }
    val background = if (mine) {
        Brush.verticalGradient(
            listOf(
                scheme.primary.lighten(0.26f),
                scheme.primary,
                scheme.primary.darken(0.12f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.72f),
                Color.White.copy(alpha = 0.50f)
            )
        )
    }
    val textColor = if (mine) Color.White else Color(0xFF0E2E3F)
    Box(
        modifier = Modifier
            .widthIn(max = 248.dp)
            .clip(shape)
            .background(background)
            .then(
                if (!mine) {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.65f), shape)
                } else {
                    Modifier
                }
            )
            .padding(11.dp, 8.dp)
    ) {
        Text(text, color = textColor, fontSize = 14.5.sp, lineHeight = 20.sp)
    }
}

/* ----------------------------- 时间戳 ----------------------------- */

@Composable
private fun TimeStamp(ts: Long) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            formatTime(ts),
            fontSize = 11.sp,
            color = scheme.secondary.copy(alpha = 0.85f)
        )
    }
}

/* ----------------------------- 头像 ----------------------------- */

@Composable
private fun Avatar(letter: String, size: Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.primary.lighten(0.22f),
                        scheme.primary.darken(0.10f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/* ----------------------------- 输入栏 ----------------------------- */

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("发条消息…") },
            singleLine = true
        )
        Spacer(Modifier.width(8.dp))
        GlassIconButton(
            onClick = onSend,
            size = 46.dp,
            containerColor = scheme.primary,
            contentColor = Color.White
        ) {
            Text("➤", fontSize = 17.sp)
        }
    }
}

/* ----------------------------- 工具 ----------------------------- */

private fun formatTime(ts: Long): String {
    if (ts <= 0) return "刚刚"
    return try {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ts))
    } catch (_: Exception) {
        ""
    }
}

/** 颜色提亮（与 Glass3D 同款算法，本地副本以便复用） */
private fun Color.lighten(f: Float): Color = Color(
    red = red + (1f - red) * f,
    green = green + (1f - green) * f,
    blue = blue + (1f - blue) * f,
    alpha = alpha
)

/** 颜色压暗 */
private fun Color.darken(f: Float): Color = Color(
    red = red * (1f - f),
    green = green * (1f - f),
    blue = blue * (1f - f),
    alpha = alpha
)
