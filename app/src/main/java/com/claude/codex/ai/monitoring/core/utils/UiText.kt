package com.claude.codex.ai.monitoring.core.utils

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** Text chosen by a ViewModel without resolving it through a Context (guidelines 5.12). */
sealed interface UiText {
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText

    /** Text that comes from the computer (project names, tool names) and is shown as-is. */
    data class Raw(val value: String) : UiText
}
