package com.claude.codex.ai.monitoring.desktop.wrapper

import com.claude.codex.ai.monitoring.protocol.ProtocolConstants
import com.claude.codex.ai.monitoring.protocol.TerminalLineDto
import com.claude.codex.ai.monitoring.protocol.TerminalSpanDto
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.RequestOrigin
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.TtyBasedArrayDataStream
import com.jediterm.terminal.TtyConnector
import com.jediterm.terminal.emulator.JediEmulator
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalLine
import com.jediterm.terminal.model.TerminalSelection
import com.jediterm.terminal.model.TerminalTextBuffer
import com.jediterm.terminal.util.CharUtils
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * A headless terminal emulator (JediTerm) fed with a wrapper's output, so the phone can be shown what
 * the terminal shows. Claude Code redraws its screen with cursor movement; only an emulator turns
 * that into lines. Everything stays in memory and is never logged.
 */
class TerminalMirror(columns: Int, rows: Int, historyLines: Int = HISTORY_LINES) {

    private val styleState = StyleState()
    private val buffer = TerminalTextBuffer(columns.coerceIn(MIN_SIZE, MAX_COLUMNS), rows.coerceIn(MIN_SIZE, MAX_ROWS), styleState, historyLines)
    private val display = HeadlessDisplay()
    private val terminal = JediTerminal(display, buffer, styleState)
    private val connector = QueueConnector()
    private val version = AtomicLong()

    /** Increases whenever the screen changed; lets the sender skip unchanged snapshots. */
    val changeCount: Long get() = version.get()

    /** True once the program asked for bracketed paste, so multi-line text can be pasted as one message. */
    val bracketedPaste: Boolean get() = display.bracketedPaste

    val columns: Int get() = buffer.width

    private val thread = Thread({
        val emulator = JediEmulator(TtyBasedArrayDataStream(connector), terminal)
        while (!connector.closed && emulator.hasNext()) {
            // A sequence the emulator cannot parse must not stop the mirror.
            runCatching { emulator.next() }
        }
    }, "agentmon-terminal-mirror").apply { isDaemon = true }

    init {
        buffer.addModelListener { version.incrementAndGet() }
        thread.start()
    }

    fun feed(text: String) {
        if (text.isNotEmpty()) connector.offer(text)
    }

    fun resize(columns: Int, rows: Int) {
        terminal.resize(TermSize(columns.coerceIn(MIN_SIZE, MAX_COLUMNS), rows.coerceIn(MIN_SIZE, MAX_ROWS)), RequestOrigin.Remote)
        version.incrementAndGet()
    }

    fun close() = connector.close()

    /** The newest [maxLines] lines (scrollback, then screen), without the empty rows below the content. */
    fun snapshot(maxLines: Int = ProtocolConstants.TERMINAL_MAX_LINES): List<TerminalLineDto> {
        buffer.lock()
        try {
            val history = buffer.historyLinesStorage
            val screen = buffer.screenLinesStorage
            var lastScreenLine = terminal.cursorY - 1 // cursorY is 1-based
            for (i in screen.size - 1 downTo 0) {
                if (!screen.get(i).isNulOrEmpty) {
                    lastScreenLine = maxOf(lastScreenLine, i)
                    break
                }
            }
            val lines = ArrayList<TerminalLine>(history.size + lastScreenLine + 1)
            for (i in 0 until history.size) lines += history.get(i)
            for (i in 0..minOf(lastScreenLine, screen.size - 1)) lines += screen.get(i)
            return lines.takeLast(maxLines).map { it.toDto() }
        } finally {
            buffer.unlock()
        }
    }

    private fun TerminalLine.toDto(): TerminalLineDto {
        val spans = ArrayList<TerminalSpanDto>()
        forEachEntry { entry ->
            val text = if (entry.isNul) " ".repeat(entry.length) else entry.text.toString().filterNot { it == CharUtils.DWC }
            if (text.isEmpty()) return@forEachEntry
            val span = entry.style.toSpan(text)
            val previous = spans.lastOrNull()
            if (previous != null && previous.copy(text = "") == span.copy(text = "")) {
                spans[spans.size - 1] = previous.copy(text = previous.text + text)
            } else {
                spans += span
            }
        }
        // Trailing blanks carry no information; drop them to keep frames small.
        val last = spans.lastOrNull()
        if (last != null && last.bg == null && !last.inverse) {
            val trimmed = last.text.trimEnd()
            if (trimmed.isEmpty()) spans.removeAt(spans.size - 1) else spans[spans.size - 1] = last.copy(text = trimmed)
        }
        return TerminalLineDto(spans)
    }

    private fun TextStyle.toSpan(text: String) = TerminalSpanDto(
        text = text,
        fg = foreground.toWire(),
        bg = background.toWire(),
        bold = hasOption(TextStyle.Option.BOLD),
        italic = hasOption(TextStyle.Option.ITALIC),
        underline = hasOption(TextStyle.Option.UNDERLINED),
        dim = hasOption(TextStyle.Option.DIM),
        inverse = hasOption(TextStyle.Option.INVERSE),
    )

    private fun TerminalColor?.toWire(): Int? = when {
        this == null -> null
        isIndexed -> colorIndex
        else -> toColor().let { TerminalSpanDto.RGB_FLAG or (it.red shl 16) or (it.green shl 8) or it.blue }
    }

    /** Output queued for the emulator thread. The emulator's own replies (e.g. cursor reports) are dropped. */
    private class QueueConnector : TtyConnector {
        private val queue = LinkedBlockingQueue<String>()
        private var pending = ""
        private var offset = 0

        @Volatile
        var closed = false
            private set

        fun offer(text: String) {
            queue.put(text)
        }

        override fun read(buf: CharArray, off: Int, len: Int): Int {
            while (offset >= pending.length) {
                val next = queue.take()
                if (next === CLOSED) return -1
                pending = next
                offset = 0
            }
            val count = minOf(len, pending.length - offset)
            pending.toCharArray(buf, off, offset, offset + count)
            offset += count
            return count
        }

        override fun write(bytes: ByteArray) = Unit
        override fun write(string: String) = Unit
        override fun isConnected() = !closed
        override fun waitFor() = 0
        override fun ready() = offset < pending.length || queue.isNotEmpty()
        override fun getName() = "agentmon"
        override fun close() {
            closed = true
            queue.put(CLOSED)
        }

        private companion object {
            val CLOSED = String(charArrayOf('\u0000'))
        }
    }

    private class HeadlessDisplay : TerminalDisplay {
        @Volatile
        var bracketedPaste = false

        override fun setCursor(x: Int, y: Int) = Unit
        override fun setCursorShape(shape: CursorShape?) = Unit
        override fun beep() = Unit
        override fun scrollArea(scrollRegionTop: Int, scrollRegionSize: Int, dy: Int) = Unit
        override fun setCursorVisible(visible: Boolean) = Unit
        override fun useAlternateScreenBuffer(useAlternateScreenBuffer: Boolean) = Unit
        override fun getWindowTitle(): String = ""
        override fun setWindowTitle(title: String) = Unit
        override fun getSelection(): TerminalSelection? = null
        override fun terminalMouseModeSet(mode: MouseMode) = Unit
        override fun setMouseFormat(mouseFormat: MouseFormat) = Unit
        override fun ambiguousCharsAreDoubleWidth() = false
        override fun setBracketedPasteMode(enabled: Boolean) {
            bracketedPaste = enabled
        }
    }

    private companion object {
        const val HISTORY_LINES = 1_000
        const val MIN_SIZE = 2
        const val MAX_COLUMNS = 500
        const val MAX_ROWS = 300
    }
}
