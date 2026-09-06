package com.arcadia.shell.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.input.NavAction
import com.arcadia.shell.model.HomeShortcut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Edit a Vita bubble icon: pick a file, scrape SteamGrid icons, reset, or remove the pin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitaShortcutIconSheet(
    shortcut: HomeShortcut,
    navActions: Flow<NavAction>,
    onDismiss: () -> Unit,
    onPickIcon: () -> Unit,
    onScrapeSteamGridIcon: () -> Unit,
    onResetIcon: () -> Unit,
    onRemove: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val glass = rememberGlassTokens(GlassTone.Surface)

    fun dismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    LaunchedEffect(navActions) {
        navActions.collect { action ->
            when (action) {
                NavAction.Cancel, NavAction.ScrapeMenu -> dismiss()
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
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Shortcut icon",
                style = MaterialTheme.typography.titleLarge,
                color = glass.content,
            )
            Text(
                text = shortcut.title,
                style = MaterialTheme.typography.bodyMedium,
                color = glass.contentMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onPickIcon, modifier = Modifier.fillMaxWidth()) {
                Text("Choose image or GIF")
            }
            TextButton(onClick = onScrapeSteamGridIcon, modifier = Modifier.fillMaxWidth()) {
                Text("Scrape SteamGrid icon")
            }
            TextButton(onClick = onResetIcon, modifier = Modifier.fillMaxWidth()) {
                Text("Reset to default")
            }
            TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                Text("Remove shortcut")
            }
            TextButton(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Text(
                text = "SteamGrid uses icon art only — not grid or hero images.",
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )
        }
    }
}
