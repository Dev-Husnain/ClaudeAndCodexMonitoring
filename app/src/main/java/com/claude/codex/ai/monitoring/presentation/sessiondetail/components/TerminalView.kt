package com.claude.codex.ai.monitoring.presentation.sessiondetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import com.claude.codex.ai.monitoring.R
import com.claude.codex.ai.monitoring.core.theme.AppTheme
import com.claude.codex.ai.monitoring.core.theme.Dimens
import com.claude.codex.ai.monitoring.core.theme.MonoTextStyle
import com.claude.codex.ai.monitoring.core.theme.TerminalPalette
import com.claude.codex.ai.monitoring.core.ui.QuickActionChip
import com.claude.codex.ai.monitoring.domain.models.TerminalColor
import com.claude.codex.ai.monitoring.domain.models.TerminalLineModel
import com.claude.codex.ai.monitoring.domain.models.TerminalScreenModel
import com.claude.codex.ai.monitoring.domain.models.TerminalSpanModel
import com.claude.codex.ai.monitoring.presentation.sessiondetail.toAnnotatedString
import android.content.ClipData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The mirrored terminal: follows new output while scrolled to the bottom, offers "Jump to latest"
 * otherwise, scrolls sideways for wide lines, and zooms with two fingers. Text can be selected with a long
 * press, and "Copy" copies everything the terminal holds.
 */
@Composable
fun TerminalView(
    screen: TerminalScreenModel?,
    modifier: Modifier = Modifier,
) {
    var scale by rememberSaveable { mutableFloatStateOf(1f) }
    val style = MonoTextStyle.copy(
        color = TerminalPalette.Foreground,
        fontSize = MonoTextStyle.fontSize * scale,
        lineHeight = MonoTextStyle.lineHeight * scale,
    )
    val measurer = rememberTextMeasurer()
    val charWidthPx = remember(style) { measurer.measure("M", style).size.width }
    val lines = screen?.lines.orEmpty()
    val texts = remember(lines) { lines.map { it.toAnnotatedString() } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var follow by remember { mutableStateOf(true) }
    val clipboard = LocalClipboard.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_LABEL_MS)
            copied = false
        }
    }

    LaunchedEffect(listState) {
        // Dragging up stops following; reaching the bottom again resumes it.
        snapshotFlow { listState.isScrollInProgress to listState.canScrollForward }.collect { (scrolling, canScrollDown) ->
            if (!canScrollDown) follow = true else if (scrolling) follow = false
        }
    }
    LaunchedEffect(texts, follow) {
        if (follow && texts.isNotEmpty()) listState.scrollToItem(texts.lastIndex)
    }

    BoxWithConstraints(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(TerminalPalette.Background)
            .pinchToZoom { zoom -> scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE) },
    ) {
        if (screen == null) {
            Text(
                text = stringResource(R.string.terminal_connecting),
                style = MaterialTheme.typography.bodyMedium,
                color = TerminalPalette.Foreground.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center).padding(Dimens.SpaceLg),
            )
            return@BoxWithConstraints
        }
        val contentWidth = with(LocalDensity.current) { (charWidthPx * screen.columns).toDp() + Dimens.SpaceMd * 2 }
        SelectionContainer {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(Dimens.SpaceMd),
                modifier = Modifier
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
                    .width(maxOf(maxWidth, contentWidth)),
            ) {
                itemsIndexed(texts) { _, text ->
                    Text(text = text, style = style, softWrap = false, maxLines = 1)
                }
            }
        }
        if (texts.isNotEmpty()) {
            val copyLabel = stringResource(if (copied) R.string.terminal_copied else R.string.terminal_copy)
            QuickActionChip(
                text = copyLabel,
                onClick = {
                    val all = texts.joinToString("\n") { it.text.trimEnd() }.trimEnd()
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Terminal", all))) }
                    copied = true
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(Dimens.SpaceSm),
            )
        }
        if (!follow) {
            QuickActionChip(
                text = stringResource(R.string.terminal_jump_latest),
                onClick = {
                    follow = true
                    scope.launch { if (texts.isNotEmpty()) listState.scrollToItem(texts.lastIndex) }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.SpaceMd),
            )
        }
    }
}

/** Two-finger zoom that leaves one-finger scrolling to the list. */
private fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) onZoom(zoom)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

private const val COPIED_LABEL_MS = 1_500L
private const val MIN_SCALE = 0.6f
private const val MAX_SCALE = 2.5f

@Preview
@Composable
private fun TerminalViewPreview() {
    AppTheme(darkTheme = true) {
        TerminalView(
            screen = TerminalScreenModel(
                columns = 60,
                lines = listOf(
                    TerminalLineModel(listOf(TerminalSpanModel("✻ Welcome to Claude Code!", foreground = TerminalColor.Rgb(0xD77757), bold = true))),
                    TerminalLineModel(listOf(TerminalSpanModel("> ", foreground = TerminalColor.Palette(8)), TerminalSpanModel("run the tests"))),
                    TerminalLineModel(listOf(TerminalSpanModel("● ", foreground = TerminalColor.Palette(2)), TerminalSpanModel("Bash(./gradlew test)"))),
                ),
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
