package com.claude.codex.ai.monitoring.fakes

import com.claude.codex.ai.monitoring.domain.models.SessionControl
import com.claude.codex.ai.monitoring.domain.models.SessionModel
import com.claude.codex.ai.monitoring.domain.models.SessionStatus

fun sessionModel(
    id: String,
    projectId: String = "p1",
    status: SessionStatus = SessionStatus.RUNNING,
    lastEventAtMs: Long = 0L,
) = SessionModel(
    sessionId = id,
    projectId = projectId,
    status = status,
    control = SessionControl.MONITOR_ONLY,
    startedAtMs = 0L,
    lastEventAtMs = lastEventAtMs,
    lastMessageSnippet = null,
    lastTool = null,
    errorInfo = null,
)
