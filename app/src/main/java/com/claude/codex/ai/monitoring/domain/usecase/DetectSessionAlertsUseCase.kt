package com.claude.codex.ai.monitoring.domain.usecase

import com.claude.codex.ai.monitoring.domain.models.AgentSnapshotModel
import com.claude.codex.ai.monitoring.domain.models.AwaitingKind
import com.claude.codex.ai.monitoring.domain.models.SessionAlertKind
import com.claude.codex.ai.monitoring.domain.models.SessionAlertModel
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus

/** What changed between two snapshots, for background notifications. */
data class SessionAlertChanges(
    /** Sessions that just started needing the user (or changed why). */
    val raised: List<SessionAlertModel>,
    /** Sessions whose alert is over: answered, resumed, ended or gone. */
    val cleared: Set<String>,
)

/**
 * Turns snapshot changes into alerts. A session alerts once when it starts needing the user and again
 * only if the reason changes; its alert is cleared when it no longer needs the user. Nothing is raised
 * for the first snapshot, so turning alerts on does not replay everything that is already waiting.
 */
class DetectSessionAlertsUseCase {

    operator fun invoke(previous: AgentSnapshotModel?, current: AgentSnapshotModel): SessionAlertChanges {
        if (!current.hasSnapshot) return SessionAlertChanges(emptyList(), emptySet())
        val before = previous?.takeIf { it.hasSnapshot }?.alertKinds()
        val now = current.alertKinds()
        val names = current.projects.associate { it.projectId to it.name }
        val raised = if (before == null) {
            emptyList()
        } else {
            now.filter { (id, kind) -> before[id] != kind }.map { (id, kind) ->
                val session = current.sessions.first { it.sessionId == id }
                SessionAlertModel(id, names[session.projectId] ?: session.projectId, kind)
            }
        }
        val cleared = before.orEmpty().keys - now.keys
        return SessionAlertChanges(raised, cleared)
    }

    private fun AgentSnapshotModel.alertKinds(): Map<String, SessionAlertKind> =
        sessions.mapNotNull { session -> session.alertKind()?.let { session.sessionId to it } }.toMap()

    private fun SessionModel.alertKind(): SessionAlertKind? = when {
        awaiting?.kind == AwaitingKind.PERMISSION -> SessionAlertKind.PERMISSION
        awaiting?.kind == AwaitingKind.REPLY -> SessionAlertKind.REPLY
        status == SessionStatus.WAITING_INPUT -> SessionAlertKind.INPUT
        status == SessionStatus.ERROR -> SessionAlertKind.ERROR
        else -> null
    }
}
