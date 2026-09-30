package com.claude.codex.ai.monitoring.presentation.sessiondetail

import com.claude.codex.ai.monitoring.core.theme.TerminalPalette
import com.claude.codex.ai.monitoring.data.mapper.toModel
import com.claude.codex.ai.monitoring.domain.models.TerminalColor
import com.claude.codex.ai.monitoring.domain.models.TerminalLineModel
import com.claude.codex.ai.monitoring.domain.models.TerminalSpanModel
import com.claude.codex.ai.monitoring.protocol.Message
import com.claude.codex.ai.monitoring.protocol.TerminalLineDto
import com.claude.codex.ai.monitoring.protocol.TerminalSpanDto
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalTextMapperTest {

    @Test
    fun `wire colours become palette and rgb colours`() {
        val screen = Message.TerminalScreen(
            "s1",
            columns = 80,
            lines = listOf(TerminalLineDto(listOf(TerminalSpanDto("a", fg = 2), TerminalSpanDto("b", fg = TerminalSpanDto.RGB_FLAG or 0x7C5CFF, bg = 196)))),
        ).toModel()
        val spans = screen.lines.single().spans
        assertEquals(TerminalColor.Palette(2), spans[0].foreground)
        assertEquals(TerminalColor.Rgb(0x7C5CFF), spans[1].foreground)
        assertEquals(TerminalColor.Palette(196), spans[1].background)
    }

    @Test
    fun `inverse swaps colours, using the terminal defaults when unset`() {
        val text = TerminalLineModel(listOf(TerminalSpanModel("█", inverse = true))).toAnnotatedString()
        val style = text.spanStyles.single().item
        assertEquals(TerminalPalette.Background, style.color)
        assertEquals(TerminalPalette.Foreground, style.background)
        assertEquals("█", text.text)
    }
}
