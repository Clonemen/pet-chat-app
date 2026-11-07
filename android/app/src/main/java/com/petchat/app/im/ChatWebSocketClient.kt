package com.petchat.app.im

import com.petchat.app.model.ServerMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * 聊天专用 WebSocket 客户端（架构"聊天层网络"的另一消费者）。
 *
 * 与悬浮宠物用的 [PetWebSocketClient] 解耦：这里只负责"聊天线程"的收发——
 * 发送 {type:"message",to,text}，接收服务端的 "message"（对方发来）与
 * "message_ack"（自己发出的回执），统一归一化为 [ChatMessage] 上抛。
 *
 * 断线指数退避重连，保证聊天页在后台切回时仍能恢复。
 */
data class ChatMessage(
    val id: String?,
    val from: String?,
    val text: String,
    val createdAt: Long,
    val fromMe: Boolean
)

class ChatWebSocketClient(
    private val onMessage: (ChatMessage) -> Unit,
    private val onStatus: (String) -> Unit
) {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

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
                    when (msg.type) {
                        "message" -> onMessage(
                            ChatMessage(msg.id, msg.from, msg.text ?: "", msg.createdAt ?: 0, fromMe = false)
                        )
                        "message_ack" -> onMessage(
                            ChatMessage(msg.id, msg.from, msg.text ?: "", msg.createdAt ?: 0, fromMe = true)
                        )
                    }
                }.onFailure { /* 忽略坏帧 */ }
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

    /** 发送一条消息给 friendId；连接未建立时返回 false */
    fun send(to: String, text: String): Boolean {
        val payload = buildJsonObject {
            put("type", "message")
            put("to", to)
            put("text", text)
        }.toString()
        return ws?.send(payload) ?: false
    }

    private fun scheduleReconnect() {
        if (closedByUser) return
        val delayMs = (3000L * (1 shl retry.coerceAtMost(4))).coerceAtMost(30000L)
        retry++
        Thread {
            Thread.sleep(delayMs)
            if (!closedByUser) open()
        }.start()
    }

    fun close() {
        closedByUser = true
        runCatching { ws?.close(1000, "chat closed") }
    }
}
