package com.claude.codex.ai.monitoring.desktop.projects

import com.claude.codex.ai.monitoring.desktop.history.TranscriptStore
import com.claude.codex.ai.monitoring.protocol.AvailableProjectDto
import java.nio.file.Files
import java.nio.file.Path

/**
 * The projects Claude Code has worked in on this computer, found from its saved conversations, for the phone's
 * "Projects on this computer" list. A conversation in a subfolder of a monitored project counts for that project;
 * home folders, folders above them and drive roots are left out (they are never monitored, see
 * [ProjectStore.canAutoMonitor]).
 */
class ProjectDiscovery(
    private val transcripts: TranscriptStore,
    private val projects: ProjectStore,
    private val canMonitor: (Path) -> Boolean = { ProjectStore.canAutoMonitor(it) },
) {
    /** A project found on this computer; [path] stays on the computer. */
    data class Found(val path: Path, val dto: AvailableProjectDto)

    fun discover(limit: Int = MAX_PROJECTS): List<Found> {
        val byId = LinkedHashMap<String, Found>()
        for (folder in transcripts.projectFolders()) {
            val cwd = folder.workingDirectory ?: continue
            val monitored = projects.projectFor(cwd.toString())
            val found = if (monitored != null) {
                Found(Path.of(monitored.path), dto(monitored.projectId, monitored.name, Path.of(monitored.path), folder, monitored = true))
            } else {
                if (!Files.isDirectory(cwd) || !canMonitor(cwd)) continue
                val id = ProjectStore.idFor(ProjectStore.normalize(cwd.toString()))
                Found(cwd, dto(id, ProjectNamer.nameFor(cwd), cwd, folder, monitored = false))
            }
            byId.merge(found.dto.projectId, found) { a, b ->
                a.copy(
                    dto = a.dto.copy(
                        conversations = a.dto.conversations + b.dto.conversations,
                        lastActiveAt = maxOf(a.dto.lastActiveAt, b.dto.lastActiveAt),
                    ),
                )
            }
        }
        // Monitored projects without saved conversations are listed too, so the list is complete.
        projects.projects.value.filter { it.projectId !in byId }.forEach { project ->
            val path = Path.of(project.path)
            byId[project.projectId] = Found(path, AvailableProjectDto(project.projectId, project.name, pathHint(path), 0, project.addedAtMs, monitored = true))
        }
        return byId.values.sortedByDescending { it.dto.lastActiveAt }.take(limit)
    }

    /** A project from [discover] by its id; null when it is not (or no longer) offered. */
    fun find(projectId: String): Found? = discover(Int.MAX_VALUE).firstOrNull { it.dto.projectId == projectId }

    private fun dto(id: String, name: String, path: Path, folder: TranscriptStore.ProjectFolder, monitored: Boolean) =
        AvailableProjectDto(id, name, pathHint(path), folder.conversations, folder.lastActiveAt, monitored)

    companion object {
        const val MAX_PROJECTS = 50

        /** The last two parts of a path, e.g. `OtherProjects\ShopKart`: enough to tell projects apart. */
        fun pathHint(path: Path): String {
            val parts = (0 until path.nameCount).map { path.getName(it).toString() }
            return parts.takeLast(2).joinToString("\\")
        }
    }
}
