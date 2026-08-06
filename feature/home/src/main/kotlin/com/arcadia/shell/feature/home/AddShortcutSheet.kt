package com.arcadia.shell.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.launcher.InstalledAppSync
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.ShortcutSpan

/**
 * Chooser for pinning a Home hub shortcut.
 *
 * Intentionally **not** a Compose [androidx.compose.ui.window.Dialog] / Material [AlertDialog]:
 * those create a nested Window. On dual-screen the hub lives inside a [android.app.Presentation]
 * marked FLAG_NOT_FOCUSABLE, and nested dialog windows crash (BadToken / missing owners).
 * This overlay is plain layout inside the existing Compose tree so the type menu opens with no
 * Activity Result registration and no new window token.
 *
 * Flow: type → tile size → target list / media picker.
 * Picture / GIF picks are requested via callbacks; the Activity-rooted shell owns the launchers.
 * Library game / Android app picks open an in-sheet controller list (U/D, A confirm, B back).
 */
@Composable
fun AddShortcutSheet(
    picker: ShortcutTargetPickerUiState?,
    pendingKind: PendingShortcutKind?,
    pendingSpan: ShortcutSpan,
    onDismiss: () -> Unit,
    onPinRecentGame: () -> Unit,
    onPinAndroidApp: () -> Unit,
    onPinPicture: () -> Unit,
    onPinGif: () -> Unit,
    onSelectSpan: (ShortcutSpan) -> Unit,
    onConfirmSpan: () -> Unit,
    onCancelSpan: () -> Unit,
    onSelectTarget: (Int) -> Unit,
    onConfirmTarget: () -> Unit,
    onCancelTargetPicker: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    BackHandler(onBack = {
        when {
            picker != null -> onCancelTargetPicker()
            pendingKind != null -> onCancelSpan()
            else -> onDismiss()
        }
    })

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        when {
                            picker != null -> onCancelTargetPicker()
                            pendingKind != null -> onCancelSpan()
                            else -> onDismiss()
                        }
                    },
                ),
        )
        when {
            picker != null -> ShortcutTargetPickerPanel(
                picker = picker,
                glassContent = glass.content,
                glassMuted = Color.White.copy(alpha = 0.55f),
                panelTint = glass.tintStrong,
                onSelectTarget = onSelectTarget,
                onConfirmTarget = onConfirmTarget,
                onCancelTargetPicker = onCancelTargetPicker,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.82f),
            )
            pendingKind != null -> ShortcutSizePickerPanel(
                kind = pendingKind,
                selected = pendingSpan,
                glassContent = glass.content,
                glassMuted = Color.White.copy(alpha = 0.55f),
                panelTint = glass.tintStrong,
                onSelectSpan = onSelectSpan,
                onConfirmSpan = onConfirmSpan,
                onCancelSpan = onCancelSpan,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f),
            )
            else -> Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f)
                    .clip(ArcadiaGlass.CardShape)
                    .background(glass.tintStrong)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "Add shortcut",
                    color = glass.content,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Button(
                    onClick = { runCatching { onPinRecentGame() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Pin library game")
                }
                Button(
                    onClick = { runCatching { onPinAndroidApp() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Pin an Android app")
                }
                OutlinedButton(
                    onClick = { runCatching { onPinPicture() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Pin a picture")
                }
                OutlinedButton(
                    onClick = { runCatching { onPinGif() } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Pin a GIF")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = "Cancel", color = glass.content)
                }
            }
        }
    }
}

@Composable
private fun ShortcutSizePickerPanel(
    kind: PendingShortcutKind,
    selected: ShortcutSpan,
    glassContent: Color,
    glassMuted: Color,
    panelTint: Color,
    onSelectSpan: (ShortcutSpan) -> Unit,
    onConfirmSpan: () -> Unit,
    onCancelSpan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kindLabel = when (kind) {
        PendingShortcutKind.LibraryGame -> "library game"
        PendingShortcutKind.AndroidApp -> "Android app"
        PendingShortcutKind.Picture -> "picture"
        PendingShortcutKind.Gif -> "GIF"
    }
    Column(
        modifier = modifier
            .clip(ArcadiaGlass.CardShape)
            .background(panelTint)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Tile size",
            color = glassContent,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Choose size for this $kindLabel · L/R cycle · A continue · B back",
            style = MaterialTheme.typography.labelMedium,
            color = glassMuted,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ShortcutSpan.entries.forEach { span ->
                val isSelected = span == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(12.dp),
                                    )
                            } else {
                                Modifier.background(Color.White.copy(alpha = 0.06f))
                            },
                        )
                        .clickable {
                            onSelectSpan(span)
                            onConfirmSpan()
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    SpanPreview(span = span, selected = isSelected)
                    Text(
                        text = span.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) glassContent else glassMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancelSpan) {
                Text(text = "Back", color = glassContent)
            }
            Button(onClick = onConfirmSpan) {
                Text(text = "Continue")
            }
        }
    }
}

@Composable
private fun SpanPreview(
    span: ShortcutSpan,
    selected: Boolean,
) {
    val maxCols = 3
    val maxRows = 2
    val cell = 10.dp
    val gap = 2.dp
    Box(
        modifier = Modifier
            .width(cell * maxCols + gap * (maxCols - 1))
            .height(cell * maxRows + gap * (maxRows - 1)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(cell * span.colSpan + gap * (span.colSpan - 1).coerceAtLeast(0))
                .height(cell * span.rowSpan + gap * (span.rowSpan - 1).coerceAtLeast(0))
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.White.copy(alpha = 0.35f),
                ),
        )
    }
}

@Composable
private fun ShortcutTargetPickerPanel(
    picker: ShortcutTargetPickerUiState,
    glassContent: Color,
    glassMuted: Color,
    panelTint: Color,
    onSelectTarget: (Int) -> Unit,
    onConfirmTarget: () -> Unit,
    onCancelTargetPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val title = when (picker.kind) {
        ShortcutPinTargetKind.LibraryGame -> "Choose a game"
        ShortcutPinTargetKind.AndroidApp -> "Choose an app"
    }

    LaunchedEffect(picker.selectedIndex, picker.candidates.size) {
        if (picker.candidates.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(
            picker.selectedIndex.coerceIn(0, picker.candidates.lastIndex),
        )
    }

    Column(
        modifier = modifier
            .clip(ArcadiaGlass.CardShape)
            .background(panelTint)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = title,
            color = glassContent,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "U/D move · A pin · B back",
            style = MaterialTheme.typography.labelMedium,
            color = glassMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(
                items = picker.candidates,
                key = { _, game -> game.id },
            ) { index, game ->
                ShortcutTargetRow(
                    game = game,
                    kind = picker.kind,
                    selected = index == picker.selectedIndex,
                    onClick = {
                        onSelectTarget(index)
                        onConfirmTarget()
                    },
                )
            }
        }
        TextButton(
            onClick = onCancelTargetPicker,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(text = "Back", color = glassContent)
        }
    }
}

@Composable
private fun ShortcutTargetRow(
    game: Game,
    kind: ShortcutPinTargetKind,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val artPath = when (kind) {
        ShortcutPinTargetKind.LibraryGame -> game.gridArt
        ShortcutPinTargetKind.AndroidApp -> InstalledAppSync.iconPathFor(game.fileName)
    }
    val subtitle = when (kind) {
        ShortcutPinTargetKind.LibraryGame -> game.platform.displayName
        ShortcutPinTargetKind.AndroidApp -> game.fileName
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                        .border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                        )
                } else {
                    Modifier.background(glass.tintSubtle.copy(alpha = 0.35f))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArtworkImage(
            path = artPath,
            contentDescription = null,
            fallbackText = game.title.take(1),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(40.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyLarge,
                color = glass.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = glass.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Text(
                text = "A",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@XoraPreview
@Composable
private fun AddShortcutSheetPreview() {
    XoraPreviewTheme {
        AddShortcutSheet(
            picker = null,
            pendingKind = null,
            pendingSpan = ShortcutSpan.OneByOne,
            onDismiss = {},
            onPinRecentGame = {},
            onPinAndroidApp = {},
            onPinPicture = {},
            onPinGif = {},
            onSelectSpan = {},
            onConfirmSpan = {},
            onCancelSpan = {},
            onSelectTarget = {},
            onConfirmTarget = {},
            onCancelTargetPicker = {},
        )
    }
}
