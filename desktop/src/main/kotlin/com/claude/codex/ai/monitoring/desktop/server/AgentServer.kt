package com.claude.codex.ai.monitoring.desktop.server

import com.claude.codex.ai.monitoring.desktop.hooks.HookInstaller
import com.claude.codex.ai.monitoring.desktop.hooks.HookReceiver
import com.claude.codex.ai.monitoring.desktop.hooks.HookResult
import com.claude.codex.ai.monitoring.desktop.pairing.PairingManager
import com.claude.codex.ai.monitoring.desktop.security.RateLimiter
import com.claude.codex.ai.monitoring.protocol.PairRequestDto
import com.claude.codex.ai.monitoring.protocol.PairResponseDto
import com.claude.codex.ai.monitoring.protocol.PairStatus
import com.claude.codex.ai.monitoring.protocol.PairingCode
import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket

/** Installs `/health`, `/pair` and `/ws`. Kept separate from [AgentServer.start] so tests can use `testApplication`. */
fun Application.agentModule(
    handler: ClientHandler,
    pairing: PairingManager,
    pairingLimiter: RateLimiter,
    version: String,
    hooks: HookReceiver? = null,
) {
    install(WebSockets) {
        pingPeriodMillis = PING_PERIOD_MS
        timeoutMillis = TIMEOUT_MS
        maxFrameSize = MAX_FRAME_BYTES
    }
    routing {
        get(ProtocolConstants.PATH_HEALTH) {
            call.respondText("""{"status":"ok","version":"$version"}""", ContentType.Application.Json)
        }
        post(ProtocolConstants.PATH_PAIR) {
            val remote = call.remoteAddress()
            val key = "pair:$remote"
            val response = if (pairingLimiter.isLimited(key)) {
                PairResponseDto(PairStatus.RATE_LIMITED)
            } else {
                val request = runCatching {
                    PairingCode.jsonFormat.decodeFromString(PairRequestDto.serializer(), call.receiveText().take(MAX_PAIR_BODY))
                }.getOrNull()
                val result = request?.let { pairing.handle(it, remote) } ?: PairResponseDto(PairStatus.INVALID)
                if (result.status == PairStatus.INVALID || result.status == PairStatus.EXPIRED) pairingLimiter.recordFailure(key)
                result
            }
            call.respondText(
                PairingCode.jsonFormat.encodeToString(PairResponseDto.serializer(), response),
                ContentType.Application.Json,
                response.status.httpStatus(),
            )
        }
        if (hooks != null) {
            post(ProtocolConstants.PATH_HOOK) {
                val viaTunnel = call.request.headers["CF-Connecting-IP"] != null || call.request.headers["Cf-Ray"] != null
                val response = hooks.receive(
                    secretHeader = call.request.headers[HookInstaller.SECRET_HEADER],
                    viaTunnel = viaTunnel,
                    body = call.receiveText().take(MAX_HOOK_BODY),
                )
                // A 2xx with an empty body means "no decision" to Claude Code; a JSON body carries one.
                val decision = response.body
                if (decision != null) {
                    call.respondText(decision, ContentType.Application.Json)
                } else {
                    call.respond(
                        when (response.result) {
                            HookResult.ACCEPTED, HookResult.IGNORED -> HttpStatusCode.NoContent
                            HookResult.FORBIDDEN -> HttpStatusCode.Forbidden
                            HookResult.BAD_REQUEST -> HttpStatusCode.BadRequest
                        },
                    )
                }
            }
        }
        webSocket(ProtocolConstants.PATH_WS) { handler.handle(this, call.remoteAddress()) }
    }
}

/**
 * The client address. Through the Cloudflare tunnel every request comes from 127.0.0.1, so the
 * real address is taken from `CF-Connecting-IP`. The server is loopback-only, so only local
 * processes could forge it, and they are also limited per device.
 */
private fun ApplicationCall.remoteAddress(): String =
    request.headers["CF-Connecting-IP"] ?: request.origin.remoteHost

private fun PairStatus.httpStatus(): HttpStatusCode = when (this) {
    PairStatus.APPROVED -> HttpStatusCode.OK
    PairStatus.REJECTED -> HttpStatusCode.Forbidden
    PairStatus.EXPIRED, PairStatus.INVALID -> HttpStatusCode.Gone
    PairStatus.TIMEOUT -> HttpStatusCode.RequestTimeout
    PairStatus.RATE_LIMITED -> HttpStatusCode.TooManyRequests
}

/**
 * Starts the agent server. It binds to the loopback interface only (spec 6.4): the Cloudflare
 * tunnel and `adb reverse` both reach it through 127.0.0.1, and nothing on the LAN can.
 */
class AgentServer(
    private val handler: ClientHandler,
    private val pairing: PairingManager,
    private val pairingLimiter: RateLimiter,
    private val version: String,
    private val hooks: HookReceiver? = null,
    private val port: Int = ProtocolConstants.DEFAULT_PORT,
) {
    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        server = embeddedServer(CIO, host = ProtocolConstants.LOOPBACK_HOST, port = port) {
            agentModule(handler, pairing, pairingLimiter, version, hooks)
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
private const val MAX_PAIR_BODY = 8 * 1024
private const val MAX_HOOK_BODY = 2 * 1024 * 1024
