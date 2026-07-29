package com.whoami22888.remoteagent.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Keeps a paired status channel open. Tasks use REST so results also work after a transient socket loss. */
class GatewaySocket {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null

    fun connect(url: String, onStatus: (String) -> Unit) {
        close()
        onStatus("Connecting")
        socket = client.newWebSocket(
            Request.Builder().url(url).header("Sec-WebSocket-Protocol", "agent-gateway.v1").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onStatus("Connected")
                    webSocket.send("{\"type\":\"ping\"}")
                }

                override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                    onStatus("Reconnecting unavailable")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onStatus("Disconnected")
                }
            },
        )
    }

    fun close() {
        socket?.close(1000, "Client closed")
        socket = null
    }
}
