package com.claude.codex.ai.monitoring.domain.models

sealed class ServerUrlError : Exception() {
    /** Not a ws:// or wss:// URL with a host and the /ws path. */
    class Invalid : ServerUrlError()

    /** Plain ws:// to a non-loopback host would expose traffic; wss:// is required. */
    class Insecure : ServerUrlError()
}
