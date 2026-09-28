package com.claude.codex.ai.monitoring.protocol

object ProtocolConstants {
    const val PROTOCOL_VERSION = 1
    const val DEFAULT_PORT = 8787
    const val LOOPBACK_HOST = "127.0.0.1"
    const val PUBLIC_HOST = "agent.appsdev.qzz.io"
    const val PATH_WS = "/ws"
    const val PATH_HEALTH = "/health"
    const val PATH_PAIR = "/pair"
    const val PATH_HOOK = "/hook"

    /** Heartbeat interval. Cloudflare drops idle WebSockets, so stay well under its timeout. */
    const val HEARTBEAT_INTERVAL_MS = 25_000L

    /** A connection with no inbound frame for this long is considered dead. */
    const val CONNECTION_TIMEOUT_MS = 60_000L

    /** Maximum timeline events kept per session. */
    const val TIMELINE_CAPACITY = 200

    /** Maximum characters of free text (snippets, event details) sent to a phone. */
    const val MAX_TEXT_CHARS = 2_000
}
