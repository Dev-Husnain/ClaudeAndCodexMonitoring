package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.ui.AwayModeToggle
import com.claude.codex.ai.monitoring.core.ui.InfoBanner
import com.claude.codex.ai.monitoring.core.ui.SectionHeader
import com.claude.codex.ai.monitoring.core.ui.StateMessage
import com.claude.codex.ai.monitoring.core.ui.StatusTone
import com.claude.codex.ai.monitoring.core.utils.UiText
import com.claude.codex.ai.monitoring.presentation.sessiondetail.SessionControlsUiModel
import com.claude.codex.ai.monitoring.presentation.sessiondetail.SessionHeaderUiModel
import com.claude.codex.ai.monitoring.presentation.sessiondetail.TimelineItemUiModel

/** Status card, offline notice and the activity timeline in one scrolling list. */
@Composable
fun SessionActivityList(
    header: SessionHeaderUiModel,
    timeline: List<TimelineItemUiModel>?,
    isOffline: Boolean,
    modifier: Modifier = Modifier,
    controls: SessionControlsUiModel? = null,
    onApprove: () -> Unit = {},
    onDeny: () -> Unit = {},
    onStop: () -> Unit = {},
    onContinue: () -> Unit = {},
    onAwayModeToggle: (Boolean) -> Unit = {},
) {
    val listState = rememberLazyListState()
    // The card is inserted above the header, where the list's scroll anchor would keep it off-screen.
    // Claude waiting for an answer is the most important thing here, so bring it into view.
    val awaiting = controls?.awaiting
    LaunchedEffect(awaiting) {
        if (awaiting != null) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, bottom = Dimens.SpaceXxl),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMd),
    ) {
        if (isOffline) {
            item(key = "offline", contentType = "banner") {
                InfoBanner(
                    icon = R.drawable.ic_offline,
                    text = stringResource(R.string.detail_offline_banner),
                    tone = StatusTone.ERROR,
                )
            }
        }
        controls?.awaiting?.let { awaiting ->
            item(key = "awaiting", contentType = "awaiting") {
                AwaitingCard(
                    awaiting = awaiting,
                    enabled = !controls.busy,
                    onApprove = onApprove,
                    onDeny = onDeny,
                    onStop = onStop,
                    onContinue = onContinue,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        item(key = "header", contentType = "header") {
            SessionStatusHeader(header = header)
        }
        if (controls != null) {
            if (controls.canControl) {
                item(key = "away", contentType = "away") {
                    AwayModeToggle(enabled = controls.awayMode, busy = controls.awayBusy, onToggle = onAwayModeToggle)
                }
            } else if (controls.showReadOnlyNote) {
                item(key = "readonly", contentType = "banner") {
                    InfoBanner(icon = R.drawable.ic_shield, text = stringResource(R.string.detail_read_only_note), tone = StatusTone.STALE)
                }
            }
        }
        item(key = "activity-title", contentType = "section") {
            SectionHeader(
                title = stringResource(R.string.detail_section_activity),
                count = timeline?.size,
                modifier = Modifier.padding(top = Dimens.SpaceMd),
            )
        }
        when {
            timeline == null -> item(key = "loading", contentType = "loading") { TimelineLoading() }
            timeline.isEmpty() -> item(key = "empty", contentType = "empty") {
                StateMessage(
                    icon = R.drawable.ic_activity,
                    title = stringResource(R.string.detail_no_activity_title),
                    message = stringResource(R.string.detail_no_activity_message),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> itemsIndexed(timeline, key = { _, item -> item.eventId }, contentType = { _, _ -> "event" }) { index, item ->
                TimelineItem(
                    item = item,
                    showRail = index != timeline.lastIndex,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SessionActivityListPreview() {
    AppTheme(darkTheme = true) {
        SessionActivityList(
            header = SessionHeaderUiModel(
                tone = StatusTone.RUNNING,
                statusLabel = UiText.Raw("Running"),
                started = UiText.Raw("Started 4 min ago"),
                lastTool = "Edit",
                controlLabel = UiText.Raw("Controllable"),
                controlTone = StatusTone.BRAND,
                message = "Updating the repository layer",
                errorInfo = null,
            ),
            timeline = listOf(
                TimelineItemUiModel("e2", R.drawable.ic_tool, StatusTone.RUNNING, "Edit", "AgentRepositoryImpl.kt", UiText.Raw("just now")),
                TimelineItemUiModel("e1", R.drawable.ic_user, StatusTone.BRAND, "Prompt", "Add reconnect back-off", UiText.Raw("4 min ago")),
            ),
            isOffline = false,
        )
    }
}
