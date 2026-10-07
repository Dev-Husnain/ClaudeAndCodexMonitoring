package com.claude.codex.ai.monitoring.presentation.computerprojects

sealed interface ComputerProjectsEvent {
    data object OnRetryClick : ComputerProjectsEvent

    /** A watched project opens its History; any other starts being watched first. */
    data class OnProjectClick(val projectId: String) : ComputerProjectsEvent
}

sealed interface ComputerProjectsEffect {
    data class OpenHistory(val projectId: String, val projectName: String) : ComputerProjectsEffect
}
