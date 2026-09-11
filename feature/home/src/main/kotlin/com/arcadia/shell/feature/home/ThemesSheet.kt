package com.arcadia.shell.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.CustomTheme
import com.arcadia.shell.datastore.DEFAULT_BOOT_ANIMATION_ID
import com.arcadia.shell.datastore.GAME_ART_ALIGN_STEP
import com.arcadia.shell.designsystem.ShellTheme
import com.arcadia.shell.designsystem.ShellThemeCatalog
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.feature.home.component.ArtworkImage
import java.util.Locale

/** Narrow left-nav sections of the Customize window. */
enum class CustomizeSection(val label: String) {
    PresetThemes("Preset Themes"),
    CustomThemes("Custom Themes"),
    CustomIcons("Custom Icons"),
    BootAnimation("Boot Animation"),
}

private const val GRID_COLUMNS = 3
private val PanelShape = RoundedCornerShape(20.dp)
private val ThumbShape = RoundedCornerShape(8.dp)

/**
 * Customize window: two panels — a narrow section list on the left, the selected section's
 * contents on the right. Every section lays its options out the same way, as a grid of framed
 * preview + name cards.
 *
 * Hosted only on the primary Activity window (same rule as Start settings). Wallpaper / BGM
 * pickers are requested by the parent; Activity Result launchers live in the Activity-rooted shell.
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
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            CustomizePanel(modifier = Modifier.width(208.dp).fillMaxHeight()) {
                PanelHeader("Customize")
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    CustomizeSection.entries.forEach { entry ->
                        CustomizeNavRow(
                            label = entry.label,
                            selected = entry == section,
                            onClick = {
                                section = entry
                                creatingCustomTheme = false
                            },
                        )
                    }
                }
            }

            CustomizePanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PanelHeader(section.label)
                Box(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
                    when (section) {
                        CustomizeSection.PresetThemes -> PresetThemesGrid(
                            activeThemeId = activeThemeId,
                            onSelectTheme = onSelectTheme,
                        )
                        CustomizeSection.CustomThemes -> if (creatingCustomTheme) {
                            CreateCustomThemeContent(
                                hasCustomWallpaper = hasCustomWallpaper,
                                customWallpaperLabel = customWallpaperLabel,
                                hasCustomBgm = hasCustomBgm,
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
                                onNewTheme = { creatingCustomTheme = true },
                                onApply = onApplyCustomTheme,
                                onDelete = onDeleteCustomTheme,
                            )
                        }
                        CustomizeSection.CustomIcons -> SectionPlaceholder("Coming soon")
                        CustomizeSection.BootAnimation -> BootAnimationGrid(
                            selectedId = bootAnimationId,
                            onSelect = onSelectBootAnimation,
                        )
                    }
                }
            }
        }
    }
}

/** Dark glass plate with the thin light edge both panels share. */
@Composable
private fun CustomizePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B0D12),
                        Color(0xFF10141C),
                        Color(0xFF151B26),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.35f), PanelShape)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

/** XOIREQE caps with the hairline rule under it, as in the Customize mock. */
@Composable
private fun PanelHeader(text: String) {
    XoraTitleText(
        text = text.uppercase(Locale.US),
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.55f)),
    )
}

@Composable
private fun CustomizeNavRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    XoraSecondaryText(
        text = label,
        fontSize = 15.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        fillColor = if (selected) Color.White else Color.White.copy(alpha = 0.62f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun SectionPlaceholder(text: String) {
    XoraSecondaryText(
        text = text,
        fontSize = 14.sp,
        fillColor = Color.White.copy(alpha = 0.55f),
    )
}

@Composable
private fun PresetThemesGrid(
    activeThemeId: String,
    onSelectTheme: (String) -> Unit,
) {
    CustomizeGrid {
        itemsIndexed(
            items = ShellThemeCatalog.all,
            key = { _, theme -> theme.id.id },
        ) { _, theme ->
            CustomizeGridCard(
                name = theme.id.displayName,
                selected = theme.id.id.equals(activeThemeId, ignoreCase = true),
                onClick = { onSelectTheme(theme.id.id) },
            ) {
                ThemeSwatchPreview(theme)
            }
        }
    }
}

/** Wallpaper still when the theme ships one; a palette swatch for the procedural backdrops. */
@Composable
private fun ThemeSwatchPreview(theme: ShellTheme) {
    val preview = theme.previewRes
    if (preview != null) {
        Image(
            painter = painterResource(preview),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
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
    onNewTheme: () -> Unit,
    onApply: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    CustomizeGrid {
        item(key = "__new_custom_theme") {
            CustomizeGridCard(
                name = "Save current as…",
                selected = false,
                onClick = onNewTheme,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    XoraSecondaryText(text = "+ New", fontSize = 15.sp)
                }
            }
        }
        itemsIndexed(
            items = themes,
            key = { _, theme -> theme.id },
        ) { _, theme ->
            var confirmingDelete by remember(theme.id) { mutableStateOf(false) }
            CustomizeGridCard(
                name = theme.name,
                selected = false,
                onClick = { if (confirmingDelete) confirmingDelete = false else onApply(theme.id) },
                onLongClick = { confirmingDelete = true },
            ) {
                if (confirmingDelete) {
                    DeleteConfirmOverlay(
                        onConfirm = { onDelete(theme.id) },
                        onCancel = { confirmingDelete = false },
                    )
                } else {
                    ArtworkImage(
                        path = theme.wallpaperPath,
                        contentDescription = theme.name,
                        fallbackText = theme.name.take(1).uppercase(Locale.US),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmOverlay(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.74f)).padding(6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        XoraSecondaryText(text = "Delete?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = onConfirm) { Text("Yes", color = Color.White) }
            TextButton(onClick = onCancel) { Text("No", color = Color.White) }
        }
    }
}

@Composable
private fun BootAnimationGrid(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    CustomizeGrid {
        item(key = DEFAULT_BOOT_ANIMATION_ID) {
            CustomizeGridCard(
                name = "Default",
                selected = selectedId.isBlank() ||
                    selectedId.equals(DEFAULT_BOOT_ANIMATION_ID, ignoreCase = true),
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
private fun CustomizeGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier.fillMaxSize(),
        content = content,
    )
}

/**
 * Square thumbnail in a light frame with a centred caption — the one card shape every
 * Customize section uses.
 */
@Composable
private fun CustomizeGridCard(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    preview: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(ThumbShape)
                .background(
                    if (selected) Color(0xFF8ED6FF) else Color.White.copy(alpha = 0.92f),
                    ThumbShape,
                )
                .padding(3.dp)
                .combinedClickable(onLongClick = onLongClick, onClick = onClick),
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
                preview()
            }
        }
        XoraSecondaryText(
            text = name,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun CreateCustomThemeContent(
    hasCustomWallpaper: Boolean,
    customWallpaperLabel: String,
    hasCustomBgm: Boolean,
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
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        XoraSecondaryText(text = "Wallpaper", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        XoraSecondaryText(
            text = if (hasCustomWallpaper) {
                "$customWallpaperLabel (still, GIF, or MP4)"
            } else {
                "Theme backdrop (image / GIF / MP4)"
            },
            fontSize = 13.sp,
            fillColor = Color.White.copy(alpha = 0.55f),
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
                Text("Left", color = Color.White)
            }
            TextButton(onClick = { onNudgeWallpaper(0f, -GAME_ART_ALIGN_STEP) }) {
                Text("Up", color = Color.White)
            }
            TextButton(onClick = { onNudgeWallpaper(0f, GAME_ART_ALIGN_STEP) }) {
                Text("Down", color = Color.White)
            }
            TextButton(onClick = { onNudgeWallpaper(GAME_ART_ALIGN_STEP, 0f) }) {
                Text("Right", color = Color.White)
            }
            TextButton(onClick = onResetWallpaper) {
                Text("Reset", color = Color.White)
            }
        }
        if (wallpaperAlignX != 0f || wallpaperAlignY != 0f) {
            XoraSecondaryText(
                text = "Offset ${"%.2f".format(Locale.US, wallpaperAlignX)}, " +
                    "${"%.2f".format(Locale.US, wallpaperAlignY)}",
                fontSize = 12.sp,
                fillColor = Color.White.copy(alpha = 0.45f),
            )
        }

        XoraSecondaryText(
            text = "Background music",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        XoraSecondaryText(
            text = if (hasCustomBgm) "Custom track (MP3 / WAV)" else "Theme or default soundtrack",
            fontSize = 13.sp,
            fillColor = Color.White.copy(alpha = 0.55f),
        )
        Button(onClick = { runCatching { onRequestBgm() } }, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Choose BGM")
        }
        if (hasCustomBgm) {
            OutlinedButton(onClick = onClearBgm, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Restore theme / default BGM")
            }
        }

        XoraSecondaryText(
            text = "Name this theme",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
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
                Text("Cancel", color = Color.White)
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
