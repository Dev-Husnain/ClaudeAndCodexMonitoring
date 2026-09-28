package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.ServerUrlError
import com.claude.codex.ai.monitoring.domain.repo.SettingsRepository
import java.net.URI

/**
 * Validates and stores the desktop agent address. Plain `ws://` is accepted only for loopback
 * (used with `adb reverse`); anything remote must be `wss://` so traffic is always encrypted.
 */
class UpdateServerUrlUseCase(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(rawUrl: String): Result<String> {
        val url = rawUrl.trim()
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host
        return when {
            uri == null || host.isNullOrBlank() || uri.path != WS_PATH -> Result.failure(ServerUrlError.Invalid())
            uri.scheme == "wss" -> save(url)
            uri.scheme == "ws" && host in LOOPBACK_HOSTS -> save(url)
            uri.scheme == "ws" -> Result.failure(ServerUrlError.Insecure())
            else -> Result.failure(ServerUrlError.Invalid())
        }
    }

    private suspend fun save(url: String): Result<String> {
        settingsRepository.setServerUrl(url)
        return Result.success(url)
    }

    private companion object {
        const val WS_PATH = "/ws"
        val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost")
    }
}
