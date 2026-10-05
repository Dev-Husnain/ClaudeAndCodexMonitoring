package com.claude.codex.ai.monitoring.desktop.history

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TranscriptStoreTest {

    private val config = Files.createTempDirectory("agentmon-claude-config")
    private val project = Files.createTempDirectory("agentmon-project").resolve("app").createDirectories()
    private val store = TranscriptStore(configDir = config)

    private val id1 = "11111111-1111-1111-1111-111111111111"
    private val id2 = "22222222-2222-2222-2222-222222222222"
    private val id3 = "33333333-3333-3333-3333-333333333333"
    private val id4 = "44444444-4444-4444-4444-444444444444"

    private fun quoted(path: Path) = "\"" + path.toString().replace("\\", "\\\\") + "\""

    private fun transcript(folder: Path, id: String, ageMinutes: Long, vararg lines: String) {
        val file = config.resolve("projects").resolve(TranscriptStore.folderName(folder)).createDirectories().resolve("$id.jsonl")
        file.writeText(lines.joinToString("\n") + "\n")
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - ageMinutes * 60_000))
    }

    private fun user(text: String, cwd: Path = project) =
        """{"type":"user","cwd":${quoted(cwd)},"message":{"role":"user","content":"$text"}}"""

    @Test
    fun `titles prefer a name, then Claude's title, then the first typed prompt, newest first`() {
        transcript(project, id1, ageMinutes = 30, user("<command-name>/clear</command-name>"), user("Fix the login bug"))
        transcript(project, id2, ageMinutes = 5, user("refactor"), """{"type":"ai-title","aiTitle":"Refactor the repository layer"}""")
        transcript(project, id3, ageMinutes = 10, user("x"), """{"type":"agent-name","agentName":"release-prep"}""")

        val sessions = store.list(project)
        assertEquals(listOf(id2, id3, id1), sessions.map { it.claudeSessionId })
        assertEquals(listOf("Refactor the repository layer", "release-prep", "Fix the login bug"), sessions.map { it.title })
    }

    @Test
    fun `sessions from subfolders count, a sibling project with a similar name does not`() {
        val sub = project.resolve("feature").createDirectories()
        transcript(sub, id1, ageMinutes = 1, user("in a subfolder", cwd = sub))
        val sibling = project.resolveSibling("app-old").createDirectories()
        transcript(sibling, id2, ageMinutes = 1, user("other project", cwd = sibling))

        assertEquals(listOf(id1), store.list(project).map { it.claudeSessionId })
        assertEquals(sub, store.workingDirectory(store.find(project, id1)!!))
        assertNull(store.find(project, id2))
    }

    @Test
    fun `a live conversation's title is read only from Claude's own projects folder`() {
        transcript(project, id1, ageMinutes = 1, user("Fix the login bug"), """{"type":"ai-title","aiTitle":"Login crash fix"}""")
        val file = config.resolve("projects").resolve(TranscriptStore.folderName(project)).resolve("$id1.jsonl")
        assertEquals("Login crash fix", store.titleOf(file.toString()))

        val outside = Files.createTempFile("agentmon-elsewhere", ".jsonl")
        outside.writeText(user("secret"))
        assertNull(store.titleOf(outside.toString()))
        assertNull(store.titleOf(file.resolveSibling("missing.jsonl").toString()))
    }

    @Test
    fun `unreadable or unknown files are skipped`() {
        transcript(project, id4, ageMinutes = 1, "not json", """{"type":"user","message":{"content":[{"type":"tool_result"}]}}""")
        val dir = config.resolve("projects").resolve(TranscriptStore.folderName(project))
        dir.resolve("notes.jsonl").writeText(user("not a session id"))
        assertEquals(emptyList(), store.list(project))
    }

    @Test
    fun `a project path stored in another letter case still finds its conversations`() {
        transcript(project, id1, ageMinutes = 1, user("hello"))
        val differentCase = Path.of(project.toString().uppercase())
        assertEquals(listOf(id1), store.list(differentCase).map { it.claudeSessionId })
    }
}
