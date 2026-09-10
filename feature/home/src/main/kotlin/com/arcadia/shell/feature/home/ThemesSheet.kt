package com.arcadia.shell.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.datastore.CustomTheme
import com.arcadia.shell.datastore.DEFAULT_BOOT_ANIMATION_ID
import com.arcadia.shell.datastore.GAME_ART_ALIGN_STEP
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.ShellTheme
import com.arcadia.shell.designsystem.ShellThemeCatalog
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.component.ArtworkImage
import java.util.Locale

/** Narrow left-nav sections of the Customize window. */
enum class CustomizeSection { PresetThemes, CustomThemes, BootAnimations }

private const val GRID_COLUMNS = 3

/**
 * Customize window: preset theme packs, saved custom (wallpaper + BGM) themes, boot animation.
 *
 * Narrow left nav + wide right content, same split for every section — each renders its options
 * as a grid of preview + name cards. Hosted only on the primary Activity window (same rule as
 * Start settings). Wallpaper / BGM pickers are requested by the parent; Activity Result launchers
 * live in the Activity-rooted shell.
 */
@Composable
fun ThemesSheet(
    activeThemeId: String,
    customThemes: List<CustomTheme>,
    hasCustomWallpaper: Boolean,
    customWallpaperLabel: String,
    hasCustomBgm: Boolean,
    bootAnimationId: String,
    onDismiss: () -> Unit,
    onSelectTheme: (String) -> Unit,
    onRequestWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRequestBgm: () -> Unit,
    onClearBgm: () -> Unit,
    onSaveCustomTheme: (String) -> Unit,
    onApplyCustomTheme: (String) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    onSelectBootAnimation: (String) -> Unit,
    wallpaperAlignX: Float = 0f,
    wallpaperAlignY: Float = 0f,
    onNudgeWallpaper: (Float, Float) -> Unit = { _, _ -> },
    onResetWallpaper: () -> Unit = {},
    initialSection: CustomizeSection = CustomizeSection.PresetThemes,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    var creatingCustomTheme by remember { mutableStateOf(false) }
    BackHandler(onBack = { if (creatingCustomTheme) creatingCustomTheme = false else onDismiss() })

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
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.88f)
                .heightIn(max = 580.dp)
                .clip(ArcadiaGlass.CardShape)
                .background(glass.tintStrong)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(vertical = 18.dp),
        ) {
            CustomizeNav(
                selected = section,
                content = glass.content,
                onSelect = {
                    section = it
                    creatingCustomTheme = false
                },
                onDismiss = onDismiss,
                modifier = Modifier
                    .width(176.dp)
                    .fillMaxHeight()
                    .padding(start = 18.dp, end = 12.dp),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.10f)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 18.dp, end = 20.dp),
            ) {
                when (section) {
                    CustomizeSection.PresetThemes -> PresetThemesGrid(
                        activeThemeId = activeThemeId,
                        content = glass.content,
                        onSelectTheme = onSelectTheme,
                    )
                    CustomizeSection.CustomThemes -> if (creatingCustomTheme) {
                        CreateCustomThemeContent(
                            hasCustomWallpaper = hasCustomWallpaper,
                            customWallpaperLabel = customWallpaperLabel,
                            hasCustomBgm = hasCustomBgm,
                            content = glass.content,
                            onRequestWallpaper = onRequestWallpaper,
                            onClearWallpaper = onClearWallpaper,
                            onRequestBgm = onRequestBgm,
                            onClearBgm = onClearBgm,
                            wallpaperAlignX = wallpaperAlignX,
                            wallpaperAlignY = wallpaperAlignY,
                            onNudgeWallpaper = onNudgeWallpaper,
                            onResetWallpaper = onResetWallpaper,
                            onCancel = { creatingCustomTheme = false },
                            onSave = { name ->
                                onSaveCustomTheme(name)
                                creatingCustomTheme = false
                            },
                        )
                    } else {
                        CustomThemesGrid(
                            themes = customThemes,
                            content = glass.content,
                            onNewTheme = { creatingCustomTheme = true },
                            onApply = onApplyCustomTheme,
                            onDelete = onDeleteCustomTheme,
                        )
                    }
                    CustomizeSection.BootAnimations -> BootAnimationsGrid(
                        selectedId = bootAnimationId,
                        content = glass.content,
                        onSelect = onSelectBootAnimation,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomizeNav(
    selected: CustomizeSection,
    content: Color,
    onSelect: (CustomizeSection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Customize",
            color = content,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 14.dp),
        )
        CustomizeNavRow("Preset Themes", CustomizeSection.PresetThemes == selected, content) {
            onSelect(CustomizeSection.PresetThemes)
        }
        CustomizeNavRow("Custom Themes", CustomizeSection.CustomThemes == selected, content) {
            onSelect(CustomizeSection.CustomThemes)
        }
        CustomizeNavRow("Boot Animations", CustomizeSection.BootAnimations == selected, content) {
            onSelect(CustomizeSection.BootAnimations)
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) {
            Text(text = "Done", color = content)
        }
    }
}

@Composable
private fun CustomizeNavRow(
    label: String,
    selected: Boolean,
    content: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = label,
        color = if (selected) content else content.copy(alpha = 0.6f),
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) content.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun PresetThemesGrid(
    activeThemeId: String,
    content: Color,
    onSelectTheme: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = ShellThemeCatalog.all,
            key = { _, theme -> theme.id.id },
        ) { _, theme ->
            CustomizeGridCard(
                name = theme.id.displayName,
                selected = theme.id.id.equals(activeThemeId, ignoreCase = true),
                content = content,
                onClick = { onSelectTheme(theme.id.id) },
            ) {
                ThemeSwatchPreview(theme)
            }
        }
    }
}

@Composable
private fun ThemeSwatchPreview(theme: ShellTheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(theme.colors.background, theme.colors.primary, theme.colors.accent),
                ),
            ),
    )
}

@Composable
private fun CustomThemesGrid(
    themes: List<CustomTheme>,
    content: Color,
    onNewTheme: () -> Unit,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "__new_custom_theme") {
            NewCustomThemeCard(content = content, onClick = onNewTheme)
        }
        itemsIndexed(
            items = themes,
            key = { _, theme -> theme.id },
        ) { _, theme ->
            var confirmingDelete by remember(theme.id) { mutableStateOf(false) }
            CustomizeGridCard(
                name = theme.name,
                selected = false,
                content = content,
                onClick = { if (confirmingDelete) confirmingDelete = false else onApply(theme.id) },
                onLongClick = { confirmingDelete = true },
            ) {
                if (confirmingDelete) {
                    DeleteConfirmOverlay(
                        content = content,
                        onConfirm = { onDelete(theme.id) },
                        onCancel = { confirmingDelete = false },
                    )
                } else {
                    ArtworkImage(
                        path = theme.wallpaperPath,
                        contentDescription = theme.name,
                        fallbackText = theme.name.take(1).uppercase(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmOverlay(content: Color, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Delete?", color = content, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onConfirm) { Text("Yes", color = content) }
            TextButton(onClick = onCancel) { Text("No", color = content) }
        }
    }
}

@Composable
private fun NewCustomThemeCard(content: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(shape)
                .border(1.dp, content.copy(alpha = 0.35f), shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "+ New", color = content, fontWeight = FontWeight.SemiBold)
        }
        Text(
            text = "Save current as…",
            color = content.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BootAnimationsGrid(
    selectedId: String,
    content: Color,
    onSelect: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = DEFAULT_BOOT_ANIMATION_ID) {
            CustomizeGridCard(
                name = "Default",
                selected = selectedId.equals(DEFAULT_BOOT_ANIMATION_ID, ignoreCase = true) ||
                    selectedId.isBlank(),
                content = content,
                onClick = { onSelect(DEFAULT_BOOT_ANIMATION_ID) },
            ) {
                ArtworkImage(
                    path = null,
                    contentDescription = "Default boot animation",
                    fallbackText = "B",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CustomizeGridCard(
    name: String,
    selected: Boolean,
    content: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    preview: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    } else {
                        Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                    },
                )
                .combinedClickable(onLongClick = onLongClick, onClick = onClick),
        ) {
            preview()
        }
        Text(
            text = name + if (selected) " · Active" else "",
            color = if (selected) content else content.copy(alpha = 0.75f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CreateCustomThemeContent(
    hasCustomWallpaper: Boolean,
    customWallpaperLabel: String,
    hasCustomBgm: Boolean,
    content: Color,
    onRequestWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRequestBgm: () -> Unit,
    onClearBgm: () -> Unit,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    onNudgeWallpaper: (Float, Float) -> Unit,
    onResetWallpaper: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel("New custom theme", content)

        SectionLabel("Wallpaper", content, topPad = true)
        Text(
            text = if (hasCustomWallpaper) {
                "$customWallpaperLabel (still, GIF, or MP4)"
            } else {
                "Theme backdrop (image / GIF / MP4)"
            },
            color = Color.White.copy(alpha = 0.55f),
        )
        Button(onClick = { runCatching { onRequestWallpaper() } }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Choose wallpaper")
        }
        if (hasCustomWallpaper) {
            OutlinedButton(onClick = onClearWallpaper, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Restore theme wallpaper")
            }
        }
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
                text = "Offset ${"%.2f".format(Locale.US, wallpaperAlignX)}, " +
                    "${"%.2f".format(Locale.US, wallpaperAlignY)}",
                color = Color.White.copy(alpha = 0.45f),
            )
        }

        SectionLabel("Background music", content, topPad = true)
        Text(
            text = if (hasCustomBgm) "Custom track (MP3 / WAV)" else "Theme or default soundtrack",
            color = Color.White.copy(alpha = 0.55f),
        )
        Button(onClick = { runCatching { onRequestBgm() } }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Choose BGM")
        }
        if (hasCustomBgm) {
            OutlinedButton(onClick = onClearBgm, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Restore theme / default BGM")
            }
        }

        SectionLabel("Name this theme", content, topPad = true)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            singleLine = true,
            placeholder = { Text("My theme") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel", color = content)
            }
            Button(
                onClick = { onSave(name.trim().ifBlank { "My theme" }) },
                enabled = hasCustomWallpaper || hasCustomBgm,
            ) {
                Text("Save custom theme")
            }
        }
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
