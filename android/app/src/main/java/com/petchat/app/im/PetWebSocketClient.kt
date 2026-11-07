package com.petchat.app.im

import android.os.Handler
import android.os.Looper
import com.petchat.app.floating.PetOverlayState
import com.petchat.app.model.ServerMessage
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * 实时 IM（WebSocket）客户端 —— 架构中的"聊天层网络"。
 *
 * 连接后端 /ws?token=...，监听来自好友的消息帧；当帧中带有 reaction 时，
 * 切换到主线程驱动悬浮宠物做出动作 + 气泡（核心玩法：消息触发桌面宠物反应）。
 *
 * 断线后指数退避自动重连，保证宠物在后台也能持续响应。
 */
class PetWebSocketClient(
    private val state: PetOverlayState,
    private val onStatus: (String) -> Unit
) {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS) // 心跳，保持长连接
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var ws: WebSocket? = null
    private var baseWs: String = ""
    private var token: String = ""
    private var closedByUser = false
    private var retry = 0

    fun connect(baseWs: String, token: String) {
        this.baseWs = baseWs
        this.token = token
        this.closedByUser = false
        open()
    }

    private fun open() {
        if (closedByUser) return
        val url = "$baseWs/ws?token=$token"
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                retry = 0
                onStatus("connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val msg = json.decodeFromString<ServerMessage>(text)
                    if (msg.type == "message") {
                        // 跨线程：切主线程更新 Compose 状态
                        mainHandler.post {
                            if (state.inVisit.value) {
                                // 访客态中的新消息：刷新形象/文案并续时
                                state.showFriendVisit(
                                    friendId = msg.from,
                                    senderName = msg.fromName,
                                    senderPetName = msg.senderPetName,
                                    senderPetImage = msg.senderPetImage,
                                    senderPetSpecies = msg.senderPetSpecies,
                                    messageText = msg.text,
                                    presetResBySpecies =
                                        com.petchat.app.data.PetPresets.resBySpecies(msg.senderPetSpecies)
                                )
                            } else {
                                // 正常态：对方宠物临时"来访"变身 + 消息气泡
                                if (msg.reaction != null) {
                                    state.react(msg.reaction.action, msg.reaction.text)
                                }
                                state.showFriendVisit(
                                    friendId = msg.from,
                                    senderName = msg.fromName,
                                    senderPetName = msg.senderPetName,
                                    senderPetImage = msg.senderPetImage,
                                    senderPetSpecies = msg.senderPetSpecies,
                                    messageText = msg.text,
                                    presetResBySpecies =
                                        com.petchat.app.data.PetPresets.resBySpecies(msg.senderPetSpecies)
                                )
                            }
                        }
                    }
                }.onFailure { /* 忽略解析异常，避免单个坏帧中断连接 */ }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatus("disconnected: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closedByUser) scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (closedByUser) return
        val delayMs = (3000L * (1 shl retry.coerceAtMost(4))).coerceAtMost(30000L)
        retry++
        mainHandler.postDelayed({ open() }, delayMs)
    }

    fun close() {
        closedByUser = true
        runCatching { ws?.close(1000, "user logout") }
        client.dispatcher.executorService.shutdown()
    }
}
