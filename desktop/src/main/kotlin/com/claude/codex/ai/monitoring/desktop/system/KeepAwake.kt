package com.claude.codex.ai.monitoring.desktop.system

import com.claude.codex.ai.monitoring.protocol.SessionDto
import com.claude.codex.ai.monitoring.protocol.SessionState
import com.sun.jna.Library
import com.sun.jna.Native
import java.util.concurrent.Executors

/** Asks the OS to stay awake (the display may still turn off). */
fun interface SleepBlocker {
    fun setAwake(awake: Boolean)

    companion object {
        fun platformDefault(): SleepBlocker =
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) WindowsSleepBlocker() else SleepBlocker { }
    }
}

/**
 * Keeps the computer awake while it matters for the phone: a session is working or waiting for an
 * answer, or Away mode is on (the owner relies on reaching Claude). Sleep would drop the tunnel and stop
 * Claude mid-task. Closing a laptop lid still sleeps, as set in Windows' power options.
 */
class KeepAwake(private val blocker: SleepBlocker = SleepBlocker.platformDefault()) {
    private var awake = false

    @Synchronized
    fun update(enabled: Boolean, awayMode: Boolean, sessions: List<SessionDto>, phoneRunActive: Boolean = false) {
        val wanted = enabled && (awayMode || phoneRunActive || sessions.any { it.needsComputer() })
        if (wanted == awake) return
        awake = wanted
        blocker.setAwake(wanted)
    }

    private fun SessionDto.needsComputer() =
        state == SessionState.RUNNING || state == SessionState.WAITING_INPUT || awaiting != null
}

/**
 * `SetThreadExecutionState` with ES_SYSTEM_REQUIRED. The request belongs to the calling thread, so every call
 * runs on one dedicated thread that lives as long as the agent.
 */
private class WindowsSleepBlocker : SleepBlocker {
    private interface Kernel32 : Library {
        @Suppress("FunctionName")
        fun SetThreadExecutionState(flags: Int): Int
    }

    private val kernel32: Kernel32? = runCatching { Native.load("kernel32", Kernel32::class.java) }.getOrNull()
    private val thread = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "agentmon-keep-awake").apply { isDaemon = true } }

    override fun setAwake(awake: Boolean) {
        val api = kernel32 ?: return
        thread.execute { api.SetThreadExecutionState(if (awake) ES_CONTINUOUS or ES_SYSTEM_REQUIRED else ES_CONTINUOUS) }
    }

    private companion object {
        const val ES_CONTINUOUS = 0x80000000.toInt()
        const val ES_SYSTEM_REQUIRED = 0x00000001
    }
}
