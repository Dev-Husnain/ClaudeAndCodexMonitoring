package com.claude.codex.ai.monitoring.cli

import kotlin.test.Test
import kotlin.test.assertEquals

class Utf8DecoderTest {

    @Test
    fun `a character split across reads is decoded once it is complete`() {
        val bytes = "ok ✓ 😀".toByteArray(Charsets.UTF_8)
        val decoder = Utf8Decoder()
        // Feed one byte at a time: every multi-byte character is split.
        val text = buildString { bytes.forEach { append(decoder.decode(byteArrayOf(it))) } }
        assertEquals("ok ✓ 😀", text)
    }

    @Test
    fun `only the given length of the buffer is used`() {
        val buffer = "abcdef".toByteArray()
        assertEquals("abc", Utf8Decoder().decode(buffer, 3))
    }
}
