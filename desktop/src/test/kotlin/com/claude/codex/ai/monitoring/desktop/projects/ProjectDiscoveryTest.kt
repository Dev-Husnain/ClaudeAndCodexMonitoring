package com.claude.codex.ai.monitoring.desktop.projects

import com.claude.codex.ai.monitoring.desktop.history.TranscriptStore
import com.claude.codex.ai.monitoring.desktop.storage.AppStorage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectDiscoveryTest {

    private val config = Files.createTempDirectory("agentmon-claude-config")
    private val home: Path = Files.createTempDirectory("agentmon-home")
    private val work = home.resolve("Work").createDirectories()
    private val store = ProjectStore(AppStorage.openDatabase(null))
    private val discovery = ProjectDiscovery(TranscriptStore(configDir = config), store, canMonitor = { ProjectStore.canAutoMonitor(it, home) })
    private var counter = 0

    private fun quoted(path: Path) = "\"" + path.toString().replace("\\", "\\\\") + "\""

    /** A saved conversation Claude had in [cwd], [ageMinutes] old. */
    private fun conversation(cwd: Path, ageMinutes: Long) {
        val id = "%08d-0000-0000-0000-000000000000".format(++counter)
        val file = config.resolve("projects").resolve(TranscriptStore.folderName(cwd)).createDirectories().resolve("$id.jsonl")
        file.writeText("""{"type":"user","cwd":${quoted(cwd)},"message":{"content":"hi"}}""" + "\n")
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - ageMinutes * 60_000))
    }

    @Test
    fun `projects come from Claude's conversations, newest first, with their real names`() {
        val shop = work.resolve("shop-kart").createDirectories()
        val notes = work.resolve("NotesApp").createDirectories()
        conversation(shop, ageMinutes = 30)
        conversation(shop, ageMinutes = 40)
        conversation(notes, ageMinutes = 5)

        val found = discovery.discover().map { it.dto }
        assertEquals(listOf("NotesApp", "Shop Kart"), found.map { it.name })
        assertEquals(2, found.last().conversations)
        assertEquals("Work\\shop-kart", found.last().pathHint)
        assertTrue(found.none { it.monitored })
    }

    @Test
    fun `a subfolder counts for its monitored project, and home, missing folders are never offered`() {
        val app = work.resolve("app").createDirectories()
        store.add(app)
        conversation(app.resolve("feature").createDirectories(), ageMinutes = 1)
        conversation(home, ageMinutes = 2)
        conversation(work.resolve("deleted"), ageMinutes = 3)

        val found = discovery.discover().map { it.dto }
        assertEquals(1, found.size)
        assertTrue(found.single().monitored)
        assertEquals(store.projects.value.single().projectId, found.single().projectId)
    }

    @Test
    fun `a project is found again by its id, which is what the phone sends`() {
        val shop = work.resolve("shop").createDirectories()
        conversation(shop, ageMinutes = 1)
        val id = discovery.discover().single().dto.projectId
        val again = assertNotNull(discovery.find(id))
        assertEquals(shop.toAbsolutePath().normalize(), again.path)
        assertFalse(again.dto.monitored)
        assertNull(discovery.find("not-an-id"))
    }
}
