package com.claude.codex.ai.monitoring.desktop.control

import com.claude.codex.ai.monitoring.protocol.TerminalKey

/**
 * Keys that answer Claude Code's own terminal prompts (spec 7.4 `PromptProfile`). Used for wrapper
 * sessions when no hook is held, e.g. with Away mode off.
 * - The permission dialog highlights "Yes" first, so Enter approves.
 * - Esc picks "No, and tell Claude what to do differently", and interrupts a running turn.
 * If Claude Code changes its dialogs, only this object needs updating.
 */
object ClaudeCodePromptProfile {
    val APPROVE = TerminalKey.ENTER
    val DENY = TerminalKey.ESCAPE
    val INTERRUPT = TerminalKey.ESCAPE
}
