package com.claude.codex.ai.monitoring.desktop.system

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.name

/** Why auto-start could not be changed, in words for the desktop window. */
class AutoStartException(message: String) : Exception(message)

/**
 * Starts the agent when the user signs in to Windows, in the tray (`--background`), through the per-user
 * `Run` key. Only the installed copy (`gradlew :desktop:installAgent`) can be registered: running from
 * Gradle's build folders would break on the next build.
 */
class AutoStart(
    private val runReg: (List<String>) -> Pair<Int, String> = ::reg,
    private val currentLaunch: () -> InstalledLaunch? = InstalledLaunch::current,
    private val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
) {
    val supported: Boolean get() = isWindows

    fun isEnabled(): Boolean = isWindows && runReg(listOf("query", RUN_KEY, "/v", VALUE)).first == 0

    fun enable() {
        if (!isWindows) throw AutoStartException("Starting with the computer is only available on Windows")
        val launch = currentLaunch()
            ?: throw AutoStartException("Install the agent first: run scripts\\install-agent.cmd, then start it from there")
        val (code, output) = runReg(listOf("add", RUN_KEY, "/v", VALUE, "/t", "REG_SZ", "/d", launch.commandLine(), "/f"))
        if (code != 0) throw AutoStartException("Windows refused the change: ${output.trim().take(200)}")
    }

    fun disable() {
        if (!isWindows || !isEnabled()) return
        val (code, output) = runReg(listOf("delete", RUN_KEY, "/v", VALUE, "/f"))
        if (code != 0) throw AutoStartException("Windows refused the change: ${output.trim().take(200)}")
    }

    /** The installed agent: the jars in its `lib` folder, started with this Java. */
    data class InstalledLaunch(val javaw: Path, val libDir: Path) {
        fun commandLine(): String = "\"$javaw\" -cp \"$libDir\\*\" $MAIN_CLASS --background"

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
        const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
        const val VALUE = "AgentMon"
        const val LAUNCHER = "AgentMon.cmd"
        const val MAIN_CLASS = "com.claude.codex.ai.monitoring.desktop.MainKt"

        private fun reg(args: List<String>): Pair<Int, String> {
            val process = ProcessBuilder(listOf("reg.exe") + args).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(REG_TIMEOUT_S, TimeUnit.SECONDS)) process.destroy()
            return process.exitValue() to output
        }

        private const val REG_TIMEOUT_S = 10L
    }
}
