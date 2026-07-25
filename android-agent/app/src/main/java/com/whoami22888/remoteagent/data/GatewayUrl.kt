package com.whoami22888.remoteagent.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object GatewayUrl {
    fun normalize(raw: String): String {
        val value = raw.trim().trimEnd('/')
        val parsed = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("Use a full gateway URL, such as https://agent.example.com")
        require(parsed.scheme == "https") {
            "HTTPS is required. Use a TLS reverse proxy or private HTTPS mesh endpoint for the gateway."
        }
        return parsed.newBuilder().encodedPath("").build().toString().trimEnd('/')
    }

    fun websocketUrl(gatewayUrl: String, token: String): String {
        val base = gatewayUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid saved gateway URL")
        val socketScheme = if (base.scheme == "https") "wss" else "ws"
        return base.newBuilder()
            .scheme(socketScheme)
            .encodedPath("/v1/ws")
            .query(null)
            .addQueryParameter("token", token)
            .build()
            .toString()
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host == "::1" || host.startsWith("127.")) return true
        if (host.startsWith("10.") || host.startsWith("192.168.")) return true
        if (host.startsWith("fd") || host.startsWith("fc")) return true
        val parts = host.split('.')
        if (parts.size == 4) {
            val first = parts[0].toIntOrNull()
            val second = parts[1].toIntOrNull()
            if (first == 172 && second in 16..31) return true
            if (first == 100 && second in 64..127) return true
        }
        return false
    }
}
