package com.claude.codex.ai.monitoring.cli

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** Decodes a UTF-8 byte stream read in arbitrary chunks, keeping a character split across chunks for the next one. */
class Utf8Decoder {
    private val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var leftover = ByteArray(0)

    fun decode(bytes: ByteArray, length: Int = bytes.size): String {
        val input = ByteBuffer.allocate(leftover.size + length).put(leftover).put(bytes, 0, length).flip()
        val output = CharBuffer.allocate(input.remaining() + 1)
        decoder.decode(input, output, false)
        leftover = ByteArray(input.remaining()).also { input.get(it) }
        return output.flip().toString()
    }
}
