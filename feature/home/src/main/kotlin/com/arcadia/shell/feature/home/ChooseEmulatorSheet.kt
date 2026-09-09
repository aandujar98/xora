package com.arcadia.shell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraModalGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.input.NavAction
import com.arcadia.shell.launcher.DetectedEmulator
import com.arcadia.shell.model.GamePlatform
import kotlinx.coroutines.flow.Flow

private val WindowFill = Color(0xE6101218)
private val RowFill = Color(0xCC161A22)

/**
 * Controller-friendly list of installed emulators / RetroArch cores for one system.
 * Dark modal chrome matches the social card, not a light Material sheet.
 */
@Composable
fun ChooseEmulatorSheet(
    platform: GamePlatform,
    options: List<DetectedEmulator>,
    selectedPlayerId: String?,
    emptyMessage: String,
    navActions: Flow<NavAction>,
    onSelect: (DetectedEmulator) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val initialIndex = remember(options, selectedPlayerId) {
        options.indexOfFirst { it.playerId == selectedPlayerId }.coerceAtLeast(0)
    }
    var focusedIndex by remember(options, selectedPlayerId) { mutableIntStateOf(initialIndex) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(focusedIndex) {
        if (options.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, options.lastIndex))
        }
    }

    // Controller input is consumed during Activity key dispatch, so the sheet is driven from the
    // shell's NavActions rather than from focus. The key handler below still serves keyboards.
    LaunchedEffect(navActions, options) {
        navActions.collect { action ->
            when (action) {
                NavAction.Up, NavAction.Left -> if (options.isNotEmpty()) {
                    focusedIndex = (focusedIndex - 1 + options.size) % options.size
                }
                NavAction.Down, NavAction.Right -> if (options.isNotEmpty()) {
                    focusedIndex = (focusedIndex + 1) % options.size
                }
                NavAction.Confirm -> options.getOrNull(focusedIndex)?.let(onSelect)
                NavAction.Cancel -> onDismiss()
                else -> Unit
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 420.dp, max = 560.dp)
                .fillMaxWidth(0.56f)
                .xoraModalGlass()
                .background(WindowFill, XoraModalGlass.Shape)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp, Key.DirectionLeft -> {
                            if (options.isNotEmpty()) {
                                focusedIndex =
                                    (focusedIndex - 1 + options.size) % options.size
                            }
                            true
                        }
                        Key.DirectionDown, Key.DirectionRight -> {
                            if (options.isNotEmpty()) {
                                focusedIndex = (focusedIndex + 1) % options.size
                            }
                            true
                        }
                        Key.Enter, Key.DirectionCenter, Key.ButtonA -> {
                            options.getOrNull(focusedIndex)?.let(onSelect)
                            true
                        }
                        Key.Back, Key.Escape, Key.ButtonB -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                }
                .padding(horizontal = 22.dp)
                .padding(top = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Choose Emulator",
                style = MaterialTheme.typography.titleLarge,
                color = glass.content,
            )
            Text(
                text = "${platform.displayName} · Select to set the default for this system",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.contentMuted,
            )

            if (options.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = glass.contentMuted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(options, key = { _, item -> item.playerId }) { index, item ->
                        val selected = item.playerId == selectedPlayerId
                        val focused = index == focusedIndex
                        EmulatorOptionRow(
                            emulator = item,
                            selected = selected,
                            focused = focused,
                            onClick = {
                                focusedIndex = index
                                onSelect(item)
                            },
                        )
                    }
                }
            }

            if (selectedPlayerId != null) {
                TextButton(
                    onClick = {
                        onClear()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Use automatic (first installed)", color = glass.content)
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close", color = glass.contentMuted)
            }
            Text(
                text = "A · Choose   B · Close   U/D · Move",
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )
        }
    }
}

@Composable
private fun EmulatorOptionRow(
    emulator: DetectedEmulator,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val shape = RoundedCornerShape(16.dp)
    val borderColor = when {
        focused -> glass.content
        selected -> glass.content.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.12f)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RowFill, shape)
            .border(
                width = if (focused || selected) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = buildString {
                append(emulator.displayName)
                if (selected) append("  ✓")
            },
            style = MaterialTheme.typography.titleSmall,
            color = glass.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = emulator.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = glass.contentMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
