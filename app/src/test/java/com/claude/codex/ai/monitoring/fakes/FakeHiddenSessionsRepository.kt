package com.claude.codex.ai.monitoring.fakes

import com.claude.codex.ai.monitoring.domain.repo.HiddenSessionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeHiddenSessionsRepository : HiddenSessionsRepository {
    override val hidden = MutableStateFlow<Map<String, Long>>(emptyMap())

    override suspend fun hide(ids: Collection<String>, atMs: Long) = hidden.update { it + ids.associateWith { atMs } }
}
