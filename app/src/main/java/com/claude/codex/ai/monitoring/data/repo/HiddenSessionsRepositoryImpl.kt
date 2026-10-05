package com.claude.codex.ai.monitoring.data.repo

import com.claude.codex.ai.monitoring.data.local.HiddenSessionsDataSource
import com.claude.codex.ai.monitoring.domain.repo.HiddenSessionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

class HiddenSessionsRepositoryImpl(
    private val dataSource: HiddenSessionsDataSource,
) : HiddenSessionsRepository {
    override val hidden: Flow<Map<String, Long>> = dataSource.hidden.distinctUntilChanged()

    override suspend fun hide(ids: Collection<String>, atMs: Long) = dataSource.hide(ids, atMs)
}
