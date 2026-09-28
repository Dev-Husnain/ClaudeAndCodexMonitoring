package com.claude.codex.ai.monitoring.desktop.projects

import com.claude.codex.ai.monitoring.desktop.db.AgentMonDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.absolute
import kotlin.io.path.name

data class MonitoredProject(
    val projectId: String,
    val name: String,
    val path: String,
    val addedAtMs: Long,
)

/** Persistent list of monitored projects, plus mapping a hook's `cwd` to its project. */
class ProjectStore(
    database: AgentMonDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val queries = database.projectQueries
    private val _projects = MutableStateFlow(load())
    val projects: StateFlow<List<MonitoredProject>> = _projects.asStateFlow()

    fun add(path: Path): MonitoredProject {
        val normalized = normalize(path.absolute().normalize().toString())
        val project = MonitoredProject(idFor(normalized), path.absolute().normalize().name.ifBlank { normalized }, normalized, clock())
        synchronized(this) {
            queries.upsert(project.projectId, project.name, project.path, project.addedAtMs)
            _projects.value = load()
        }
        return project
    }

    fun remove(projectId: String) {
        synchronized(this) {
            queries.deleteById(projectId)
            _projects.value = load()
        }
    }

    /**
     * The project that contains [cwd]. Claude may `cd` into a subfolder, so the longest monitored
     * path that is a prefix of `cwd` wins. Paths compare case-insensitively on Windows.
     */
    fun projectFor(cwd: String): MonitoredProject? {
        val dir = normalize(cwd)
        return _projects.value
            .filter { dir == it.path || dir.startsWith(it.path + "/") }
            .maxByOrNull { it.path.length }
    }

    private fun load(): List<MonitoredProject> =
        queries.selectAll().executeAsList().map { MonitoredProject(it.project_id, it.name, it.path, it.added_at) }

    companion object {
        private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows")

        /** Forward slashes, no trailing slash, lower case on Windows (case-insensitive file system). */
        fun normalize(path: String): String {
            val slashes = path.replace('\\', '/').trimEnd('/')
            return if (windows) slashes.lowercase() else slashes
        }

        /** Stable id from the path, so re-adding a project keeps its id. */
        fun idFor(normalizedPath: String): String =
            MessageDigest.getInstance("SHA-256").digest(normalizedPath.encodeToByteArray())
                .take(8).joinToString("") { "%02x".format(it) }
    }
}
