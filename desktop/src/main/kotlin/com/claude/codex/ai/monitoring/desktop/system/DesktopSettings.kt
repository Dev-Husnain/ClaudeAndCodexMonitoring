package com.claude.codex.ai.monitoring.desktop.system

import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists

/** Small per-computer preferences of the agent, in `settings.properties` next to its database. */
class DesktopSettings(private val file: Path) {

    private val _keepAwake = MutableStateFlow(read().getProperty(KEEP_AWAKE)?.toBooleanStrictOrNull() ?: true)

    /** Keep the computer from sleeping while Claude works or Away mode is on (default on). */
    val keepAwake: StateFlow<Boolean> = _keepAwake.asStateFlow()

    fun setKeepAwake(enabled: Boolean) {
        _keepAwake.value = enabled
        write(KEEP_AWAKE, enabled.toString())
    }

    private val _publicUrl = MutableStateFlow(read().getProperty(PUBLIC_URL)?.ifBlank { null })

    /** The tunnel address put in pairing codes (`https://host`), or null to pair over USB only. */
    val publicUrl: StateFlow<String?> = _publicUrl.asStateFlow()

    fun setPublicUrl(url: String?) {
        _publicUrl.value = url
        write(PUBLIC_URL, url)
    }

    private fun read(): Properties = Properties().apply {
        if (file.exists()) runCatching { Files.newBufferedReader(file).use(::load) }
    }

    @Synchronized
    private fun write(key: String, value: String?) {
        val properties = read().apply { if (value == null) remove(key) else setProperty(key, value) }
        Files.newBufferedWriter(file).use { properties.store(it, "AgentMon desktop settings") }
        AppStorage.restrictToOwner(file, directory = false)
    }

    private companion object {
        const val KEEP_AWAKE = "keepAwake"
        const val PUBLIC_URL = "publicUrl"
    }
}
