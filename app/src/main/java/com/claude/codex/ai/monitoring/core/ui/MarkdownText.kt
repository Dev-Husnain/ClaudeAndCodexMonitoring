package com.claude.codex.ai.monitoring.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.JetBrainsMono
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle
import com.claude.codex.ai.monitoring.core.utils.InlineSpan
import com.claude.codex.ai.monitoring.core.utils.MarkdownBlock
import com.claude.codex.ai.monitoring.core.utils.parseInline
import com.claude.codex.ai.monitoring.core.utils.parseMarkdown

private val CodeShape = RoundedCornerShape(12.dp)
private val TableCellMinWidth = 96.dp
private val TableCellMaxWidth = 220.dp
private val QuoteBarWidth = 3.dp
private val ListIndent = 16.dp

/**
 * Claude's Markdown as styled, selectable text: headings, paragraphs, lists, quotes, code blocks,
 * tables, links and inline bold / italic / code. Wide code and tables scroll sideways.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = AppTheme.colors.textPrimary,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSm)) {
            blocks.forEach { block -> MarkdownBlockView(block, style, color) }
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, style: TextStyle, color: Color) {
    val colors = AppTheme.colors
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = inline(block.text),
            style = when (block.level) {
                1 -> MaterialTheme.typography.titleLarge
                2 -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            },
            color = color,
            modifier = Modifier.padding(top = Dimens.SpaceXs),
        )
        is MarkdownBlock.Paragraph -> Text(text = inline(block.text), style = style, color = color)
        is MarkdownBlock.ListItem -> Row(modifier = Modifier.padding(start = ListIndent * block.depth)) {
            Text(
                text = block.marker,
                style = style,
                color = colors.textSecondary,
                modifier = Modifier.widthIn(min = ListIndent).padding(end = Dimens.SpaceXs),
            )
            Text(text = inline(block.text), style = style, color = color, modifier = Modifier.weight(1f))
        }
        is MarkdownBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(QuoteBarWidth)
                    .fillMaxHeight()
                    .background(colors.outline, RoundedCornerShape(2.dp)),
            )
            Text(
                text = inline(block.text),
                style = style.copy(fontStyle = FontStyle.Italic),
                color = colors.textSecondary,
                modifier = Modifier.padding(start = Dimens.SpaceSm),
            )
        }
        is MarkdownBlock.Code -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surfaceElevated, CodeShape)
                .border(Dimens.BorderThin, colors.outline, CodeShape)
                .horizontalScroll(rememberScrollState())
                .padding(Dimens.SpaceMd),
        ) {
            Text(text = block.code, style = MonoTextStyle, color = color, softWrap = false)
        }
        is MarkdownBlock.Table -> Column(
            modifier = Modifier
                .border(Dimens.BorderThin, colors.outline, CodeShape)
                .horizontalScroll(rememberScrollState()),
        ) {
            TableRow(block.header, style.copy(fontWeight = FontWeight.SemiBold), color, colors.surfaceElevated)
            block.rows.forEach { row -> TableRow(row, style, color, Color.Transparent) }
        }
        MarkdownBlock.Rule -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.BorderThin)
                .background(colors.outline),
        )
    }
}

@Composable
private fun TableRow(cells: List<String>, style: TextStyle, color: Color, background: Color) {
    Row(modifier = Modifier.background(background)) {
        cells.forEach { cell ->
            Text(
                text = inline(cell),
                style = style,
                color = color,
                modifier = Modifier
                    .widthIn(min = TableCellMinWidth, max = TableCellMaxWidth)
                    .padding(horizontal = Dimens.SpaceSm, vertical = Dimens.SpaceXs),
            )
        }
    }
}

@Composable
private fun inline(text: String): AnnotatedString {
    val colors = AppTheme.colors
    val codeStyle = SpanStyle(fontFamily = JetBrainsMono, background = colors.surfaceElevated)
    val linkStyles = TextLinkStyles(SpanStyle(color = colors.brandStart, textDecoration = TextDecoration.Underline))
    return remember(text, colors) { buildInline(parseInline(text), codeStyle, linkStyles) }
}

private fun buildInline(spans: List<InlineSpan>, codeStyle: SpanStyle, linkStyles: TextLinkStyles) = buildAnnotatedString {
    spans.forEach { span ->
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.SemiBold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            textDecoration = if (span.strike) TextDecoration.LineThrough else null,
        ).let { if (span.code) it.merge(codeStyle) else it }
        if (span.url != null) {
            withLink(LinkAnnotation.Url(span.url, linkStyles)) { withStyle(style) { append(span.text) } }
        } else {
            withStyle(style) { append(span.text) }
        }
    }
}

@Preview
@Composable
private fun MarkdownTextPreview() {
    AppTheme(darkTheme = true) {
        MarkdownText(
            markdown = """
                ## Done
                I fixed the **login crash** in `AuthRepository.kt`:
                - Null check on the *token*
                - Added a test, see [the docs](https://example.com)

                ```kotlin
                val token = prefs.token ?: return
                ```
                | File | Change |
                |---|---|
                | AuthRepository.kt | null check |
            """.trimIndent(),
            modifier = Modifier.padding(Dimens.SpaceLg),
        )
    }
}
