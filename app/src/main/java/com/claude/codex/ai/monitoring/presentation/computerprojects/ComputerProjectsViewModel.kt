package com.claude.codex.ai.monitoring.presentation.computerprojects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.utils.Clock
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.core.utils.toRelativeTime
import com.claude.codex.ai.monitoring.domain.models.AvailableProjectModel
import com.claude.codex.ai.monitoring.domain.models.DeliveryStatus
import com.claude.codex.ai.monitoring.domain.repo.AgentRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Projects on this computer": every folder Claude Code worked in, so the phone can start watching one and reach
 * its History without the computer at hand.
 */
class ComputerProjectsViewModel(
    private val agentRepository: AgentRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _computerProjectsUiState = MutableStateFlow(ComputerProjectsUiState())
    val computerProjectsUiState: StateFlow<ComputerProjectsUiState> = _computerProjectsUiState.asStateFlow()

    private val _effects = Channel<ComputerProjectsEffect>(Channel.BUFFERED)
    val effects: Flow<ComputerProjectsEffect> = _effects.receiveAsFlow()

    private var projects: List<AvailableProjectModel> = emptyList()

    init {
        load()
        viewModelScope.launch {
            agentRepository.snapshot.collect { snapshot -> _computerProjectsUiState.update { it.copy(canAdd = snapshot.canSendInput) } }
        }
    }

    fun onEvent(event: ComputerProjectsEvent) {
        when (event) {
            ComputerProjectsEvent.OnRetryClick -> load()
            is ComputerProjectsEvent.OnProjectClick -> open(event.projectId)
        }
    }

    private fun load() {
        _computerProjectsUiState.update { it.copy(isLoading = true, isUnavailable = false, error = null) }
        viewModelScope.launch {
            val answer = agentRepository.availableProjects()
            projects = answer?.projects.orEmpty()
            _computerProjectsUiState.update {
                it.copy(
                    isLoading = false,
                    isUnavailable = answer == null,
                    notAllowed = answer?.allowed == false,
                    projects = projects.map { project -> project.toItem() },
                )
            }
        }
    }

    private fun open(projectId: String) {
        val project = projects.firstOrNull { it.projectId == projectId } ?: return
        if (project.monitored) {
            viewModelScope.launch { _effects.send(ComputerProjectsEffect.OpenHistory(project.projectId, project.name)) }
            return
        }
        val state = _computerProjectsUiState.value
        if (!state.canAdd || state.addingId != null) return
        _computerProjectsUiState.update { it.copy(addingId = projectId, error = null) }
        viewModelScope.launch {
            val status = agentRepository.addProject(projectId)
            if (status is DeliveryStatus.Failed) {
                val message = status.detail?.let { UiText.Raw(it) } ?: UiText.Res(R.string.delivery_rejected)
                _computerProjectsUiState.update { it.copy(addingId = null, error = message) }
                return@launch
            }
            projects = projects.map { if (it.projectId == projectId) it.copy(monitored = true) else it }
            _computerProjectsUiState.update { it.copy(addingId = null, projects = projects.map { p -> p.toItem() }) }
            _effects.send(ComputerProjectsEffect.OpenHistory(project.projectId, project.name))
        }
    }

    private fun AvailableProjectModel.toItem() = ComputerProjectItemUiModel(
        projectId = projectId,
        name = name,
        pathHint = pathHint,
        detail = UiText.Res(
            R.string.computer_projects_detail,
            listOf(UiText.Plural(R.plurals.computer_projects_conversations, conversations), lastActiveAtMs.toRelativeTime(clock.nowMs())),
        ),
        monitored = monitored,
    )
}
