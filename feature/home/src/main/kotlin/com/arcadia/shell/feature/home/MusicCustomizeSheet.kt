package com.arcadia.shell.feature.home

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.input.NavAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** One D-pad / stick step of travel through a sheet that is taller than the viewport. */
private val SCROLL_STEP = 96.dp

/**
 * Select on a focused album or track: custom cover art and a wallpaper (still or looping video).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicCustomizeSheet(
    title: String,
    coverPath: String?,
    wallpaperPath: String?,
    navActions: Flow<NavAction>,
    onDismiss: () -> Unit,
    onPickCover: () -> Unit,
    onPickWallpaper: () -> Unit,
    onClearCover: () -> Unit,
    onClearWallpaper: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val glass = rememberGlassTokens(GlassTone.Surface)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    fun dismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    LaunchedEffect(navActions) {
        val step = with(density) { SCROLL_STEP.toPx() }
        navActions.collect { action ->
            when (action) {
                NavAction.Up -> scrollState.animateScrollBy(-step)
                NavAction.Down -> scrollState.animateScrollBy(step)
                NavAction.Cancel, NavAction.ScrapeMenu, NavAction.Options -> dismiss()
                else -> Unit
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = glass.content,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = ArcadiaGlass.SheetShape,
                    tone = GlassTone.Surface,
                    intensity = GlassIntensity.Strong,
                )
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Music options",
                style = MaterialTheme.typography.titleLarge,
                color = glass.content,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = glass.contentMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = "Customize",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            MusicMediaRow(
                title = "Cover art",
                status = musicPathStatus(coverPath),
                onChange = onPickCover,
                onClear = onClearCover.takeIf { !coverPath.isNullOrBlank() },
            )
            MusicMediaRow(
                title = "Wallpaper",
                status = musicPathStatus(wallpaperPath),
                onChange = onPickWallpaper,
                onClear = onClearWallpaper.takeIf { !wallpaperPath.isNullOrBlank() },
            )
            Text(
                text = "Wallpapers can be a still or a looping video.",
                style = MaterialTheme.typography.bodySmall,
                color = glass.contentMuted,
            )

            TextButton(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Text(
                text = "U/D · Scroll   B / Select · Close",
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )
        }
    }
}

@Composable
private fun MusicMediaRow(
    title: String,
    status: String,
    onChange: () -> Unit,
    onClear: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onChange) { Text("Change") }
            if (onClear != null) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

private fun musicPathStatus(path: String?): String =
    if (path.isNullOrBlank()) "Not set" else path.substringAfterLast('/')
