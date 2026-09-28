package com.claude.codex.ai.monitoring.desktop.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.claude.codex.ai.monitoring.desktop.db.AgentMonDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories

/**
 * Where the agent keeps its key and database: `%APPDATA%\AgentMon` on Windows (already private
 * to the Windows user), `~/.agentmon` elsewhere, restricted to the owner on POSIX systems.
 */
object AppStorage {

    fun dataDirectory(override: String? = null): Path {
        val dir = when {
            override != null -> Path(override)
            System.getenv("APPDATA") != null -> Path(System.getenv("APPDATA"), "AgentMon")
            else -> Path(System.getProperty("user.home"), ".agentmon")
        }
        dir.createDirectories()
        restrictToOwner(dir, directory = true)
        return dir
    }

    /** Opens (and creates or migrates) the SQLite database. Pass `null` for an in-memory database in tests. */
    fun openDatabase(file: Path?): AgentMonDatabase {
        val url = if (file == null) JdbcSqliteDriver.IN_MEMORY else "jdbc:sqlite:${file.absolutePathString()}"
        val driver = JdbcSqliteDriver(url, Properties(), AgentMonDatabase.Schema)
        file?.let { restrictToOwner(it, directory = false) }
        return AgentMonDatabase(driver)
    }

    fun restrictToOwner(path: Path, directory: Boolean) {
        if (!Files.exists(path)) return
        runCatching {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(if (directory) "rwx------" else "rw-------"))
        }
        // Not a POSIX file system (Windows): the per-user %APPDATA% ACL already applies.
    }
}
