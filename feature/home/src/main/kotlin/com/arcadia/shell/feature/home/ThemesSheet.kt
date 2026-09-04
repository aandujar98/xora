package com.arcadia.shell.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadia.shell.datastore.GAME_ART_ALIGN_STEP
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.ShellTheme
import com.arcadia.shell.designsystem.ShellThemeCatalog
import com.arcadia.shell.designsystem.rememberGlassTokens
import java.util.Locale

enum class ThemesSheetTab { Presets, Customize }

/**
 * Themes editor: preset packs, shop scaffold, custom wallpaper/BGM, shortcuts.
 *
 * Hosted only on the primary Activity window (same rule as Start settings). Wallpaper / BGM
 * pickers are requested by the parent; Activity Result launchers live in the Activity-rooted shell.
 */
@Composable
fun ThemesSheet(
    activeThemeId: String,
    shopThemeIds: List<String>,
    hasCustomWallpaper: Boolean,
    customWallpaperLabel: String,
    hasCustomBgm: Boolean,
    shortcutCount: Int,
    onDismiss: () -> Unit,
    onSelectTheme: (String) -> Unit,
    onShopComingSoon: () -> Unit,
    onUploadComingSoon: () -> Unit,
    onRequestWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRequestBgm: () -> Unit,
    onClearBgm: () -> Unit,
    onManageShortcuts: () -> Unit,
    wallpaperAlignX: Float = 0f,
    wallpaperAlignY: Float = 0f,
    onNudgeWallpaper: (Float, Float) -> Unit = { _, _ -> },
    onResetWallpaper: () -> Unit = {},
    initialTab: ThemesSheetTab = ThemesSheetTab.Customize,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    BackHandler(onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.78f)
                .heightIn(max = 520.dp)
                .clip(ArcadiaGlass.CardShape)
                .background(glass.tintStrong)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Themes",
                color = glass.content,
                fontWeight = FontWeight.SemiBold,
            )
            ThemesTabRow(
                selected = tab,
                content = glass.content,
                onSelect = { tab = it },
            )
            if (tab == ThemesSheetTab.Customize) {
                Text(
                    text = "Change your wallpaper or background music",
                    color = Color.White.copy(alpha = 0.55f),
                )
            }

            when (tab) {
                ThemesSheetTab.Presets -> PresetsContent(
                    activeThemeId = activeThemeId,
                    shopThemeIds = shopThemeIds,
                    content = glass.content,
                    onSelectTheme = onSelectTheme,
                    onShopComingSoon = onShopComingSoon,
                    onUploadComingSoon = onUploadComingSoon,
                )
                ThemesSheetTab.Customize -> CustomizeContent(
                    hasCustomWallpaper = hasCustomWallpaper,
                    customWallpaperLabel = customWallpaperLabel,
                    hasCustomBgm = hasCustomBgm,
                    shortcutCount = shortcutCount,
                    content = glass.content,
                    onRequestWallpaper = onRequestWallpaper,
                    onClearWallpaper = onClearWallpaper,
                    onRequestBgm = onRequestBgm,
                    onClearBgm = onClearBgm,
                    onManageShortcuts = onManageShortcuts,
                    wallpaperAlignX = wallpaperAlignX,
                    wallpaperAlignY = wallpaperAlignY,
                    onNudgeWallpaper = onNudgeWallpaper,
                    onResetWallpaper = onResetWallpaper,
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = "Done", color = glass.content)
            }
        }
    }
}

@Composable
private fun ThemesTabRow(
    selected: ThemesSheetTab,
    content: Color,
    onSelect: (ThemesSheetTab) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ThemesSheetTab.entries.forEach { tab ->
            val active = tab == selected
            Text(
                text = tab.name,
                color = if (active) content else content.copy(alpha = 0.55f),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) content.copy(alpha = 0.14f) else Color.Transparent,
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun PresetsContent(
    activeThemeId: String,
    shopThemeIds: List<String>,
    content: Color,
    onSelectTheme: (String) -> Unit,
    onShopComingSoon: () -> Unit,
    onUploadComingSoon: () -> Unit,
) {
    SectionLabel("Presets", content)
    ShellThemeCatalog.all.forEach { theme ->
        ThemePresetRow(
            theme = theme,
            selected = theme.id.id.equals(activeThemeId, ignoreCase = true),
            content = content,
            onClick = { onSelectTheme(theme.id.id) },
        )
    }

    SectionLabel("From XOrA Store", content, topPad = true)
    if (shopThemeIds.isEmpty()) {
        Text(
            text = "Coming from XOrA Store — downloadable packs will list here.",
            color = Color.White.copy(alpha = 0.55f),
        )
        OutlinedButton(
            onClick = onShopComingSoon,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "XOrA Store (coming soon)")
        }
    } else {
        shopThemeIds.forEach { id ->
            OutlinedButton(
                onClick = { onSelectTheme(id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = id)
            }
        }
    }

    OutlinedButton(
        onClick = onUploadComingSoon,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Upload theme to XOrA Store")
    }
    Text(
        text = "Coming soon — stub stays safe until the shop lands.",
        color = Color.White.copy(alpha = 0.45f),
    )
}

@Composable
private fun CustomizeContent(
    hasCustomWallpaper: Boolean,
    customWallpaperLabel: String,
    hasCustomBgm: Boolean,
    shortcutCount: Int,
    content: Color,
    onRequestWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRequestBgm: () -> Unit,
    onClearBgm: () -> Unit,
    onManageShortcuts: () -> Unit,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    onNudgeWallpaper: (Float, Float) -> Unit,
    onResetWallpaper: () -> Unit,
) {
    SectionLabel("Wallpaper", content)
    Text(
        text = if (hasCustomWallpaper) {
            "$customWallpaperLabel (still, GIF, or MP4)"
        } else {
            "Theme backdrop (image / GIF / MP4)"
        },
        color = Color.White.copy(alpha = 0.55f),
    )
    Button(
        onClick = { runCatching { onRequestWallpaper() } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Choose wallpaper")
    }
    if (hasCustomWallpaper) {
        OutlinedButton(
            onClick = onClearWallpaper,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Restore theme wallpaper")
        }
    }
    Text(
        text = "Pan the wallpaper. Same control as cover art inside a Game Icon.",
        color = Color.White.copy(alpha = 0.55f),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onNudgeWallpaper(-GAME_ART_ALIGN_STEP, 0f) }) {
            Text("Left", color = content)
        }
        TextButton(onClick = { onNudgeWallpaper(0f, -GAME_ART_ALIGN_STEP) }) {
            Text("Up", color = content)
        }
        TextButton(onClick = { onNudgeWallpaper(0f, GAME_ART_ALIGN_STEP) }) {
            Text("Down", color = content)
        }
        TextButton(onClick = { onNudgeWallpaper(GAME_ART_ALIGN_STEP, 0f) }) {
            Text("Right", color = content)
        }
        TextButton(onClick = onResetWallpaper) {
            Text("Reset", color = content)
        }
    }
    if (wallpaperAlignX != 0f || wallpaperAlignY != 0f) {
        Text(
            text = "Offset ${"%.2f".format(Locale.US, wallpaperAlignX)}, ${"%.2f".format(Locale.US, wallpaperAlignY)}",
            color = Color.White.copy(alpha = 0.45f),
        )
    }

    SectionLabel("Background music", content, topPad = true)
    Text(
        text = if (hasCustomBgm) "Custom track (MP3 / WAV)" else "Theme or default soundtrack",
        color = Color.White.copy(alpha = 0.55f),
    )
    Button(
        onClick = { runCatching { onRequestBgm() } },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Choose BGM")
    }
    if (hasCustomBgm) {
        OutlinedButton(
            onClick = onClearBgm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Restore theme / default BGM")
        }
    }

    SectionLabel("Shortcuts", content, topPad = true)
    Text(
        text = "$shortcutCount pinned",
        color = Color.White.copy(alpha = 0.55f),
    )
    OutlinedButton(
        onClick = onManageShortcuts,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = "Edit shortcut grid")
    }
}

@Composable
private fun SectionLabel(text: String, content: Color, topPad: Boolean = false) {
    Text(
        text = text,
        color = content,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = if (topPad) 8.dp else 0.dp),
    )
}

@Composable
private fun ThemePresetRow(
    theme: ShellTheme,
    selected: Boolean,
    content: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) content.copy(alpha = 0.12f) else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(1.dp, content.copy(alpha = 0.35f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = theme.id.displayName + if (selected) " · Active" else "",
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
        Text(
            text = buildString {
                append(theme.description)
                theme.bgm?.let {
                    append(" · BGM: ")
                    append(it.displayHint)
                }
            },
            color = Color.White.copy(alpha = 0.55f),
        )
    }
}
