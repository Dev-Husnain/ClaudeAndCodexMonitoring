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
 *
 * Scrollback is kept here, not only in the emulator: Windows' pseudo-terminal (ConPTY) repaints the visible
 * screen instead of scrolling it, so lines leaving the top never reach the emulator's own history. Each change
 * is compared with the previous screen; when its content moved up, the lines that left the top are saved.
 */
class TerminalMirror(columns: Int, rows: Int, private val historyLines: Int = HISTORY_LINES) {

    private val styleState = StyleState()
    private val buffer = TerminalTextBuffer(columns.coerceIn(MIN_SIZE, MAX_COLUMNS), rows.coerceIn(MIN_SIZE, MAX_ROWS), styleState, historyLines)
    private val display = HeadlessDisplay()
    private val terminal = JediTerminal(display, buffer, styleState)
    private val connector = QueueConnector()
    private val version = AtomicLong()

    /** Lines that scrolled off the top, oldest first (guarded by itself). */
    private val history = ArrayDeque<TerminalLineDto>()
    private var lastScreen: List<TerminalLineDto> = emptyList()
    private var lastEmulatorHistory = 0
    private var trackedVersion = -1L

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

    private val tracker = Thread({
        while (!connector.closed) {
            runCatching { track() }
            Thread.sleep(TRACK_INTERVAL_MS)
        }
    }, "agentmon-terminal-history").apply { isDaemon = true }

    init {
        buffer.addModelListener { version.incrementAndGet() }
        thread.start()
        tracker.start()
    }

    fun feed(text: String) {
        if (text.isNotEmpty()) connector.offer(text)
    }

    fun resize(columns: Int, rows: Int) {
        terminal.resize(TermSize(columns.coerceIn(MIN_SIZE, MAX_COLUMNS), rows.coerceIn(MIN_SIZE, MAX_ROWS)), RequestOrigin.Remote)
        // Lines reflow on a resize; comparing across it would look like scrolling.
        synchronized(history) { lastScreen = emptyList() }
        version.incrementAndGet()
    }

    fun close() = connector.close()

    /** The newest [maxLines] lines (scrollback, then screen), without the empty rows below the content. */
    fun snapshot(maxLines: Int = ProtocolConstants.TERMINAL_MAX_LINES): List<TerminalLineDto> {
        track()
        val screen = readScreen()
        var last = screen.lastIndex
        while (last > cursorRow && screen[last].spans.isEmpty()) last--
        val visible = screen.subList(0, last + 1)
        val saved = synchronized(history) { history.toList() }
        return (saved + visible).takeLast(maxLines)
    }

    /** Saves what scrolled off since the last call: the emulator's own new history, or lines a repaint pushed out. */
    private fun track() {
        val current = version.get()
        buffer.lock()
        val screen: List<TerminalLineDto>
        val emulatorHistory: List<TerminalLineDto>
        try {
            if (current == trackedVersion) return
            trackedVersion = current
            screen = screenLines()
            val stored = buffer.historyLinesStorage
            // The emulator scrolled for real (e.g. on macOS and Linux): take its new lines as they are.
            val added = (stored.size - lastEmulatorHistory).coerceAtLeast(0)
            emulatorHistory = (stored.size - added until stored.size).map { stored.get(it).toDto() }
            lastEmulatorHistory = stored.size
        } finally {
            buffer.unlock()
        }
        synchronized(history) {
            val scrolledOff = if (emulatorHistory.isNotEmpty()) emulatorHistory else scrolledOff(lastScreen, screen)
            scrolledOff.forEach { history.addLast(it) }
            while (history.size > historyLines) history.removeFirst()
            lastScreen = screen
        }
    }

    private val cursorRow: Int get() = terminal.cursorY - 1 // cursorY is 1-based

    private fun readScreen(): List<TerminalLineDto> {
        buffer.lock()
        try {
            return screenLines()
        } finally {
            buffer.unlock()
        }
    }

    private fun screenLines(): List<TerminalLineDto> {
        val screen = buffer.screenLinesStorage
        return (0 until screen.size).map { screen.get(it).toDto() }
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

    companion object {
        /**
         * The lines that left the top between [before] and [after]: the largest shift `k` for which the top of
         * [after] continues [before] from line `k` on, with at least [MIN_OVERLAP] non-empty matching lines (so a
         * mostly blank screen or a full redraw is not mistaken for scrolling). Lines are compared by their text.
         */
        internal fun scrolledOff(before: List<TerminalLineDto>, after: List<TerminalLineDto>): List<TerminalLineDto> {
            if (before.isEmpty() || after.isEmpty()) return emptyList()
            val old = before.map { it.plainText() }
            val new = after.map { it.plainText() }
            if (overlap(old, new, 0) >= MIN_OVERLAP) return emptyList() // Same top: nothing scrolled.
            for (shift in 1 until old.size) {
                if (overlap(old, new, shift) >= MIN_OVERLAP) return before.subList(0, shift)
            }
            return emptyList()
        }

        /** Non-empty lines of `new` matching `old` from [shift] on, counted until the first difference. */
        private fun overlap(old: List<String>, new: List<String>, shift: Int): Int {
            var matched = 0
            var i = 0
            while (shift + i < old.size && i < new.size && old[shift + i] == new[i]) {
                if (new[i].isNotBlank()) matched++
                i++
            }
            return matched
        }

        private fun TerminalLineDto.plainText(): String = spans.joinToString("") { it.text }.trimEnd()

        private const val MIN_OVERLAP = 3
        private const val TRACK_INTERVAL_MS = 60L
        const val HISTORY_LINES = 1_000
        const val MIN_SIZE = 2
        const val MAX_COLUMNS = 500
        const val MAX_ROWS = 300
    }
}
