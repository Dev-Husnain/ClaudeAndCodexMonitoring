package com.claude.codex.ai.monitoring.core.utils

/** A block of the Markdown Claude writes: what [parseMarkdown] returns and the UI draws. */
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    /** One list line; [marker] is "•" or the number ("3."), [depth] counts nesting from 0. */
    data class ListItem(val marker: String, val text: String, val depth: Int) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Rule : MarkdownBlock
}

/** A run of inline text with its styling, from [parseInline]. */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val url: String? = null,
)

private val HEADING = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val NUMBERED = Regex("^(\\s*)(\\d{1,4})[.)]\\s+(.*)$")
private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
private val FENCE = Regex("^\\s*(```|~~~)\\s*([\\w+#.-]*).*$")
private val TABLE_DIVIDER = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")
private const val INDENT_PER_LEVEL = 2
private const val MAX_DEPTH = 3

/** Splits Claude's Markdown into blocks. Anything unusual falls back to a paragraph, never an error. */
fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").replace('\t', ' ').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ") { it.trim() })
        paragraph.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val fence = FENCE.matchEntire(line)
        when {
            fence != null -> {
                flush()
                val marker = fence.groupValues[1]
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) code += lines[i++]
                blocks += MarkdownBlock.Code(fence.groupValues[2].ifBlank { null }, code.joinToString("\n").trimEnd())
            }
            line.isBlank() -> flush()
            RULE.matches(line) -> {
                flush()
                blocks += MarkdownBlock.Rule
            }
            HEADING.matches(line) -> {
                flush()
                val match = HEADING.matchEntire(line)!!
                blocks += MarkdownBlock.Heading(match.groupValues[1].length, match.groupValues[2])
            }
            line.trimStart().startsWith("|") && i + 1 < lines.size && TABLE_DIVIDER.matches(lines[i + 1]) -> {
                flush()
                val header = cells(line)
                val rows = mutableListOf<List<String>>()
                i += 2
                while (i < lines.size && lines[i].trimStart().startsWith("|")) rows += cells(lines[i++])
                blocks += MarkdownBlock.Table(header, rows.map { row -> List(header.size) { row.getOrElse(it) { "" } } })
                continue
            }
            BULLET.matches(line) -> {
                flush()
                val match = BULLET.matchEntire(line)!!
                blocks += MarkdownBlock.ListItem("•", match.groupValues[2], depth(match.groupValues[1]))
            }
            NUMBERED.matches(line) -> {
                flush()
                val match = NUMBERED.matchEntire(line)!!
                blocks += MarkdownBlock.ListItem("${match.groupValues[2]}.", match.groupValues[3], depth(match.groupValues[1]))
            }
            line.trimStart().startsWith(">") -> {
                flush()
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) quote += lines[i++].trimStart().removePrefix(">").trim()
                blocks += MarkdownBlock.Quote(quote.joinToString(" "))
                continue
            }
            // A line under a list item that is indented continues that item.
            line.startsWith("  ") && paragraph.isEmpty() && blocks.lastOrNull() is MarkdownBlock.ListItem -> {
                val item = blocks.removeAt(blocks.lastIndex) as MarkdownBlock.ListItem
                blocks += item.copy(text = item.text + " " + line.trim())
            }
            else -> paragraph += line
        }
        i++
    }
    flush()
    return blocks
}

private fun depth(indent: String) = (indent.length / INDENT_PER_LEVEL).coerceAtMost(MAX_DEPTH)

private fun cells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

/**
 * Inline styling: `code`, **bold**, *italic* / _italic_, ~~strike~~ and [links](https://…). Underscores
 * inside words (snake_case) stay text. Unclosed markers are kept as typed.
 */
fun parseInline(text: String): List<InlineSpan> {
    val spans = mutableListOf<InlineSpan>()
    val plain = StringBuilder()
    var bold = false
    var italic = false
    var strike = false
    fun emit(span: InlineSpan) {
        if (plain.isNotEmpty()) {
            spans += InlineSpan(plain.toString(), bold = bold, italic = italic, strike = strike)
            plain.clear()
        }
        if (span.text.isNotEmpty()) spans += span
    }
    fun toggle(apply: () -> Unit) {
        emit(InlineSpan(""))
        apply()
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val rest = text.length - i
        when {
            c == '\\' && rest > 1 && text[i + 1] in ESCAPABLE -> {
                plain.append(text[i + 1])
                i += 2
                continue
            }
            c == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    emit(InlineSpan(text.substring(i + 1, end), bold = bold, italic = italic, code = true))
                    i = end + 1
                    continue
                }
            }
            c == '[' -> {
                val close = text.indexOf("](", i + 1)
                val end = if (close > i) text.indexOf(')', close + 2) else -1
                val url = if (end > 0) text.substring(close + 2, end).trim() else ""
                if (end > 0 && (url.startsWith("http://") || url.startsWith("https://"))) {
                    emit(InlineSpan(text.substring(i + 1, close), bold = bold, italic = italic, url = url))
                    i = end + 1
                    continue
                }
            }
            (c == '*' || c == '_') && rest > 1 && text[i + 1] == c && closes(text, i + 2, "$c$c", bold) -> {
                toggle { bold = !bold }
                i += 2
                continue
            }
            c == '~' && rest > 1 && text[i + 1] == '~' && closes(text, i + 2, "~~", strike) -> {
                toggle { strike = !strike }
                i += 2
                continue
            }
            c == '*' && closes(text, i + 1, "*", italic) -> {
                toggle { italic = !italic }
                i++
                continue
            }
            c == '_' && underscoreItalic(text, i, italic) -> {
                toggle { italic = !italic }
                i++
                continue
            }
        }
        plain.append(c)
        i++
    }
    emit(InlineSpan(""))
    return spans
}

/** An opening marker counts only when it is closed later on the line (or it is the closing one). */
private fun closes(text: String, from: Int, marker: String, open: Boolean): Boolean =
    open || (from < text.length && !text[from].isWhitespace() && text.indexOf(marker, from) > from)

private fun underscoreItalic(text: String, i: Int, open: Boolean): Boolean {
    val before = text.getOrNull(i - 1)
    val after = text.getOrNull(i + 1)
    return if (open) {
        after == null || !after.isLetterOrDigit()
    } else {
        (before == null || !before.isLetterOrDigit()) && closes(text, i + 1, "_", false)
    }
}

private const val ESCAPABLE = "\\`*_~[]()#>|-!"

/** Plain text for one-line previews: no Markdown markers, everything on one line. */
fun String.stripMarkdown(): String =
    parseMarkdown(this).joinToString(" ") { block ->
        when (block) {
            is MarkdownBlock.Heading -> block.text.inlineText()
            is MarkdownBlock.Paragraph -> block.text.inlineText()
            is MarkdownBlock.ListItem -> (if (block.marker == "•") "" else "${block.marker} ") + block.text.inlineText()
            is MarkdownBlock.Quote -> block.text.inlineText()
            is MarkdownBlock.Code -> block.code.lineSequence().joinToString(" ") { it.trim() }
            is MarkdownBlock.Table -> (listOf(block.header) + block.rows).joinToString(" ") { row -> row.joinToString(" ") { it.inlineText() } }
            MarkdownBlock.Rule -> ""
        }
    }.replace(Regex("\\s+"), " ").trim()

private fun String.inlineText(): String = parseInline(this).joinToString("") { it.text }
