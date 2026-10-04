package com.claude.codex.ai.monitoring.platform

import java.io.File

/**
 * Finds the `claude` command the way the shell would (PATH, and PATHEXT on Windows) and builds the
 * command line. npm installs `claude.cmd` on Windows, which has to be started through `cmd.exe`.
 */
object ClaudeCommand {

    fun resolve(
        args: List<String>,
        env: Map<String, String> = System.getenv(),
        isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
        exists: (File) -> Boolean = File::isFile,
    ): List<String>? {
        val override = env["AGENTMON_CLAUDE"]?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf(exists)
        val executable = override ?: find(env, isWindows, exists) ?: return null
        val script = isWindows && executable.extension.lowercase() in setOf("cmd", "bat")
        if (!script) return listOf(executable.path) + args
        // cmd.exe splits a quoted script path that contains a space (C:\Users\A B\...\claude.cmd). A script
        // found on PATH is started by its bare name, which cmd resolves exactly as when typing `claude`.
        val name = when {
            override == null -> executable.nameWithoutExtension
            ' ' !in executable.path -> executable.path
            else -> return null
        }
        return listOf(env["ComSpec"] ?: "cmd.exe", "/d", "/c", name) + args
    }

    private fun find(env: Map<String, String>, isWindows: Boolean, exists: (File) -> Boolean): File? {
        val path = env.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value.orEmpty()
        val extensions = if (isWindows) {
            (env.entries.firstOrNull { it.key.equals("PATHEXT", ignoreCase = true) }?.value ?: ".COM;.EXE;.BAT;.CMD")
                .split(';').filter { it.isNotBlank() }.map { it.lowercase() }
        } else {
            listOf("")
        }
        return path.split(File.pathSeparatorChar).asSequence()
            .filter { it.isNotBlank() }
            .flatMap { dir -> extensions.asSequence().map { ext -> File(dir.trim('"'), "claude$ext") } }
            .firstOrNull(exists)
    }
}
