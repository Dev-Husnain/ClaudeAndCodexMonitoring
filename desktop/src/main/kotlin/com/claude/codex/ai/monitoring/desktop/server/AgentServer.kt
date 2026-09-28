package com.claude.codex.ai.monitoring.desktop.server

import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket

/** Installs `/health` and `/ws`. Kept separate from [start] so tests can use `testApplication`. */
fun Application.agentModule(handler: ClientHandler, version: String) {
    install(WebSockets) {
        pingPeriodMillis = PING_PERIOD_MS
        timeoutMillis = TIMEOUT_MS
        maxFrameSize = MAX_FRAME_BYTES
    }
    routing {
        get(ProtocolConstants.PATH_HEALTH) {
            call.respondText("""{"status":"ok","version":"$version"}""", ContentType.Application.Json)
        }
        webSocket(ProtocolConstants.PATH_WS) { handler.handle(this) }
    }
}

/**
 * Starts the agent server. It binds to the loopback interface only (spec 6.4): the Cloudflare
 * tunnel and `adb reverse` both reach it through 127.0.0.1, and nothing on the LAN can.
 */
class AgentServer(
    private val handler: ClientHandler,
    private val version: String,
    private val port: Int = ProtocolConstants.DEFAULT_PORT,
) {
    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        server = embeddedServer(CIO, host = ProtocolConstants.LOOPBACK_HOST, port = port) {
            agentModule(handler, version)
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
    }
}

private const val MAX_FRAME_BYTES = 1L * 1024 * 1024
private const val PING_PERIOD_MS = 20_000L
private const val TIMEOUT_MS = 60_000L
