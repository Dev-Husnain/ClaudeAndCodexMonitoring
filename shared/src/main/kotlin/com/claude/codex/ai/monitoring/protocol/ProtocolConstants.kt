package com.claude.codex.ai.monitoring.protocol

object ProtocolConstants {
    const val PROTOCOL_VERSION = 1
    const val DEFAULT_PORT = 8787
    const val LOOPBACK_HOST = "127.0.0.1"
    const val PATH_WS = "/ws"
    const val PATH_HEALTH = "/health"
    const val PATH_PAIR = "/pair"
    const val PATH_HOOK = "/hook"
    const val PATH_WRAPPER = "/wrapper"

    /** Environment variable `agentmon claude` sets for Claude; hooks send it back in [HEADER_WRAPPER]. */
    const val ENV_WRAPPER_ID = "AGENTMON_WRAPPER_ID"
    const val HEADER_WRAPPER = "X-Agentmon-Wrapper"
    const val HEADER_SECRET = "X-Agentmon-Secret"

    /** Heartbeat interval. Cloudflare drops idle WebSockets, so stay well under its timeout. */
    const val HEARTBEAT_INTERVAL_MS = 25_000L

    /** A connection with no inbound frame for this long is considered dead. */
    const val CONNECTION_TIMEOUT_MS = 60_000L

    /** Maximum timeline events kept per session. */
    const val TIMELINE_CAPACITY = 200

    /** Maximum characters of free text (snippets, event details) sent to a phone. */
    const val MAX_TEXT_CHARS = 2_000

    /** Newest terminal lines (scrollback + screen) sent to an attached phone. */
    const val TERMINAL_MAX_LINES = 500
}
