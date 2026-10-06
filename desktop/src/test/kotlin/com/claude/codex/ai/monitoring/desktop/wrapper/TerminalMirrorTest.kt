package com.claude.codex.ai.monitoring.desktop.wrapper

import com.claude.codex.ai.monitoring.protocol.TerminalLineDto
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TerminalMirrorTest {

    private val mirror = TerminalMirror(columns = 40, rows = 10)

    @AfterTest
    fun tearDown() = mirror.close()

    private fun List<TerminalLineDto>.texts() = map { line -> line.spans.joinToString("") { it.text } }

    /** The emulator runs on its own thread: wait until the screen shows what the test expects. */
    private fun awaitLines(expected: List<String>): List<TerminalLineDto> {
        repeat(200) {
            val lines = mirror.snapshot()
            if (lines.texts() == expected) return lines
            Thread.sleep(10)
        }
        assertEquals(expected, mirror.snapshot().texts())
        error("unreachable")
    }

    @Test
    fun `plain output becomes lines with colours kept`() {
        mirror.feed("hello\r\n\u001B[31mred\u001B[0m and \u001B[1mbold\u001B[0m")
        val lines = awaitLines(listOf("hello", "red and bold"))
        val spans = lines[1].spans
        assertEquals(1, spans.first { it.text == "red" }.fg)
        assertTrue(spans.first { it.text == "bold" }.bold)
    }

    @Test
    fun `redraws with cursor movement show the final screen, not the history of edits`() {
        mirror.feed("step 1\r\nloading...")
        mirror.feed("\r\u001B[2Kdone\r\n")
        mirror.feed("\u001B[2A\u001B[2Kstep 2")
        awaitLines(listOf("step 2", "done"))
    }

    @Test
    fun `sequences split across chunks are still understood`() {
        mirror.feed("\u001B[3")
        mirror.feed("2mgreen\u001B[")
        mirror.feed("0m")
        val lines = awaitLines(listOf("green"))
        assertEquals(2, lines.single().spans.single().fg)
    }

    @Test
    fun `true colour is sent as rgb and bracketed paste is noticed`() {
        mirror.feed("\u001B[?2004h\u001B[38;2;124;92;255mbrand\u001B[0m")
        val span = awaitLines(listOf("brand")).single().spans.single()
        assertEquals(0x1000000 or 0x7C5CFF, span.fg)
        assertTrue(mirror.bracketedPaste)
    }

    /** A full-screen repaint the way Windows' ConPTY sends it: home, then every row rewritten in place. */
    private fun repaint(rows: List<String>) =
        "\u001B[H" + rows.mapIndexed { i, row -> "\u001B[${i + 1};1H$row\u001B[K" }.joinToString("")

    @Test
    fun `lines a repaint pushes off the top are kept as scrollback`() {
        val first = (1..8).map { "line $it" } + listOf("", "> prompt")
        mirror.feed(repaint(first))
        awaitLines(first)
        // Claude printed two more lines: the content moved up by two, the prompt stayed at the bottom.
        val second = (3..10).map { "line $it" } + listOf("", "> prompt")
        mirror.feed(repaint(second))
        awaitLines((1..10).map { "line $it" } + listOf("", "> prompt"))
    }

    @Test
    fun `a redraw that only changes the bottom is not taken for scrolling`() {
        val before = (1..6).map { "msg $it" } + listOf("", "", "", "working.")
        mirror.feed(repaint(before))
        awaitLines(before)
        val after = (1..6).map { "msg $it" } + listOf("", "", "", "working...")
        mirror.feed(repaint(after))
        awaitLines(after)
    }

    @Test
    fun `scrolled-off lines are found from the shift, and a mostly blank screen never counts`() {
        fun lines(vararg texts: String) = texts.map { TerminalLineDto(if (it.isEmpty()) emptyList() else listOf(com.claude.codex.ai.monitoring.protocol.TerminalSpanDto(it))) }
        val before = lines("a", "b", "c", "d", "e", "", "> ")
        val after = lines("c", "d", "e", "f", "g", "", "> ")
        assertEquals(listOf("a", "b"), TerminalMirror.scrolledOff(before, after).texts())
        assertEquals(emptyList(), TerminalMirror.scrolledOff(before, before).texts())
        assertEquals(emptyList(), TerminalMirror.scrolledOff(lines("x", "", "", ""), lines("", "", "", "y")).texts())
        assertEquals(emptyList(), TerminalMirror.scrolledOff(before, lines("new", "screen", "entirely", "", "")).texts())
    }
}
