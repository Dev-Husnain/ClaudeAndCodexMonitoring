package com.claude.codex.ai.monitoring.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownTest {

    @Test
    fun `blocks Claude writes are recognised`() {
        val blocks = parseMarkdown(
            """
            ## Summary
            I fixed the crash
            in two places.

            - First fix
              continued here
            - Second fix
               - nested
            1. Run the tests
            > Note: restart the app

            ```kotlin
            val x = 1
            ```
            | File | Change |
            |---|:---:|
            | A.kt | fix |
            ---
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MarkdownBlock.Heading(2, "Summary"),
                MarkdownBlock.Paragraph("I fixed the crash in two places."),
                MarkdownBlock.ListItem("•", "First fix continued here", 0),
                MarkdownBlock.ListItem("•", "Second fix", 0),
                MarkdownBlock.ListItem("•", "nested", 1),
                MarkdownBlock.ListItem("1.", "Run the tests", 0),
                MarkdownBlock.Quote("Note: restart the app"),
                MarkdownBlock.Code("kotlin", "val x = 1"),
                MarkdownBlock.Table(listOf("File", "Change"), listOf(listOf("A.kt", "fix"))),
                MarkdownBlock.Rule,
            ),
            blocks,
        )
    }

    @Test
    fun `an unclosed code fence keeps the rest as code`() {
        assertEquals(listOf(MarkdownBlock.Code(null, "a\nb")), parseMarkdown("```\na\nb"))
    }

    @Test
    fun `inline styles, links and text that only looks like Markdown`() {
        assertEquals(
            listOf(
                InlineSpan("Use "),
                InlineSpan("bold", bold = true),
                InlineSpan(" and "),
                InlineSpan("it", italic = true),
                InlineSpan(" in "),
                InlineSpan("my_file.kt", code = true),
                InlineSpan(", see "),
                InlineSpan("docs", url = "https://example.com"),
            ),
            parseInline("Use **bold** and *it* in `my_file.kt`, see [docs](https://example.com)"),
        )
        assertEquals(listOf(InlineSpan("snake_case_name and 2 * 3 = 6")), parseInline("snake_case_name and 2 * 3 = 6"))
        assertEquals(listOf(InlineSpan("a **b")), parseInline("a **b"))
        assertEquals(listOf(InlineSpan("[x](javascript:alert)")), parseInline("[x](javascript:alert)"))
    }

    @Test
    fun `previews are plain one-line text`() {
        assertEquals("Done Fixed the crash in AuthRepository.kt 1. Run tests", "## Done\n\nFixed the **crash** in `AuthRepository.kt`\n1. Run tests".stripMarkdown())
    }
}
