package com.claude.codex.ai.monitoring.desktop.system

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name

/** Why auto-start could not be changed, in words for the desktop window. */
class AutoStartException(message: String) : Exception(message)

/** The per-user `Run` key, behind an interface so tests never touch the real registry. */
interface RunKey {
    fun get(): String?
    fun set(command: String)
    fun remove()
}

/**
 * Starts the agent when the user signs in to Windows, in the tray (`--background`), through the per-user
 * `Run` key. The value is written with the Windows API, not `reg.exe`: its quoted command line does not
 * survive a second round of command-line quoting. Only the installed copy (`gradlew :desktop:installAgent`)
 * can be registered: running from Gradle's build folders would break on the next build.
 */
class AutoStart(
    private val runKey: RunKey = WindowsRunKey(),
    private val currentLaunch: () -> Launch? = { PackagedLaunch.current() ?: InstalledLaunch.current() },
    private val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
) {
    val supported: Boolean get() = isWindows

    fun isEnabled(): Boolean = isWindows && runCatching { runKey.get() != null }.getOrDefault(false)

    fun enable() {
        if (!isWindows) throw AutoStartException("Starting with the computer is only available on Windows")
        val launch = currentLaunch()
            ?: throw AutoStartException("Start AgentMon from AgentMon.exe, or install it first (scripts\\install-agent.cmd), not from Gradle")
        runCatching { runKey.set(launch.commandLine()) }
            .onFailure { throw AutoStartException("Windows refused the change: ${it.message.orEmpty().take(200)}") }
    }

    fun disable() {
        if (!isEnabled()) return
        runCatching { runKey.remove() }
            .onFailure { throw AutoStartException("Windows refused the change: ${it.message.orEmpty().take(200)}") }
    }

    /** How Windows should start this copy of the agent at sign-in. */
    interface Launch {
        fun commandLine(): String
    }

    /** The packaged app (`AgentMon.exe` from a release), which carries its own Java. */
    data class PackagedLaunch(val exe: Path) : Launch {
        override fun commandLine(): String = "\"$exe\" --background"

        companion object {
            /** Set by the jpackage launcher to the running `.exe`. */
            fun current(): PackagedLaunch? = System.getProperty("jpackage.app-path")
                ?.let { runCatching { Path.of(it) }.getOrNull() }
                ?.takeIf { it.exists() }
                ?.let(::PackagedLaunch)
        }
    }

    /** The installed agent: the jars in its `lib` folder, started with this Java. */
    data class InstalledLaunch(val javaw: Path, val libDir: Path) : Launch {
        override fun commandLine(): String = "\"$javaw\" --enable-native-access=ALL-UNNAMED -cp \"$libDir\\*\" $MAIN_CLASS --background"

        companion object {
            /** Set when this process runs from an installed copy (the jar holding this class sits in `lib`). */
            fun current(): InstalledLaunch? {
                val jar = runCatching { Path.of(AutoStart::class.java.protectionDomain.codeSource.location.toURI()) }.getOrNull() ?: return null
                val lib = jar.parent ?: return null
                if (lib.name != "lib" || !lib.resolveSibling(LAUNCHER).exists()) return null
                val java = ProcessHandle.current().info().command().orElse(null)?.let(Path::of) ?: return null
                val javaw = java.resolveSibling("javaw.exe").takeIf { Files.exists(it) } ?: java
                return InstalledLaunch(javaw, lib)
            }
        }
    }

    companion object {
        const val RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val VALUE = "AgentMon"
        const val LAUNCHER = "AgentMon.cmd"
        const val MAIN_CLASS = "com.claude.codex.ai.monitoring.desktop.MainKt"
    }
}

private class WindowsRunKey : RunKey {
    override fun get(): String? =
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, AutoStart.RUN_KEY, AutoStart.VALUE)) {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, AutoStart.RUN_KEY, AutoStart.VALUE)
        } else {
            null
        }

    override fun set(command: String) =
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, AutoStart.RUN_KEY, AutoStart.VALUE, command)

    override fun remove() = Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, AutoStart.RUN_KEY, AutoStart.VALUE)
}
