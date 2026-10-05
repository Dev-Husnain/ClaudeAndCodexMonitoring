package com.claude.codex.ai.monitoring.desktop.system

import java.net.URI

/**
 * The address phones use to reach this computer from anywhere: the owner's Cloudflare tunnel hostname,
 * e.g. `agent.example.com`. Only HTTPS is accepted, because the tunnel ends in TLS and the phone refuses
 * plain HTTP to anything but the USB route.
 */
object PublicAddress {

    sealed interface Parsed {
        /** A valid address, as `https://host[:port]`. */
        data class Valid(val url: String) : Parsed
        /** Empty input: no address (pair over USB only). */
        data object Cleared : Parsed
        data class Invalid(val reason: String) : Parsed
    }

    fun parse(input: String): Parsed {
        val text = input.trim().trimEnd('/')
        if (text.isEmpty()) return Parsed.Cleared
        if (text.startsWith("http://", ignoreCase = true)) {
            return Parsed.Invalid("Use https:// (the Cloudflare tunnel address), not http://")
        }
        val withScheme = if (text.contains("://")) text else "https://$text"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return Parsed.Invalid("That is not a web address")
        val host = uri.host?.lowercase()
        return when {
            !uri.scheme.equals("https", ignoreCase = true) -> Parsed.Invalid("Use an https:// address")
            host == null || !HOST.matches(host) -> Parsed.Invalid("Enter a hostname such as agent.example.com")
            host == "localhost" || host.startsWith("127.") -> Parsed.Invalid("Use the tunnel's public hostname; USB pairing needs no address")
            !uri.rawPath.isNullOrEmpty() || uri.rawQuery != null -> Parsed.Invalid("Enter only the hostname, without a path")
            uri.userInfo != null -> Parsed.Invalid("Enter only the hostname")
            else -> Parsed.Valid("https://$host" + if (uri.port > 0 && uri.port != HTTPS_PORT) ":${uri.port}" else "")
        }
    }

    private const val HTTPS_PORT = 443
    private val HOST = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
}
