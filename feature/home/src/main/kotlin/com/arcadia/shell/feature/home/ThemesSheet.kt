package com.arcadia.shell.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.CustomTheme
import com.arcadia.shell.datastore.CUSTOM_BOOT_ANIMATION_ID
import com.arcadia.shell.datastore.DEFAULT_BOOT_ANIMATION_ID
import com.arcadia.shell.datastore.GAME_ART_ALIGN_STEP
import com.arcadia.shell.designsystem.ShellTheme
import com.arcadia.shell.designsystem.ShellThemeCatalog
import com.arcadia.shell.designsystem.XoraSheetScrim
import com.arcadia.shell.designsystem.XoraSettingsPanelHeader
import com.arcadia.shell.designsystem.xoraFocusHighlight
import com.arcadia.shell.designsystem.xoraSettingsPanelSurface
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.input.NavAction
import java.util.Locale
import kotlinx.coroutines.flow.Flow

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
private val FocusRingColor = Color(0xFF8ED6FF)

/** Which half of the window has the stick. */
private enum class CustomizePane { Nav, Content }

/**
 * One selectable card in a section grid. Building these up front means the grid render and the
 * controller focus model read from the same list, so they can never disagree about what is at
 * index N.
 */
private class CustomizeEntry(
    val key: String,
    val name: String,
    val selected: Boolean,
    val onActivate: () -> Unit,
    /** Select / long-press: the card's secondary action, currently only delete. */
    val onSecondary: (() -> Unit)? = null,
    val preview: @Composable () -> Unit,
)

/**
 * Customize window: two panels — a narrow section list on the left, the selected section's
 * contents on the right. Every section lays its options out the same way, as a grid of framed
 * preview + name cards.
 *
 * Fully controller-driven: the window owns the stick while it is up (see
 * [HomeViewModel.customizeNavActionFlow]) rather than relying on Compose focus traversal, which
 * cannot see across the two panels or into the create-theme form.
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
    hasTrayBgm: Boolean,
    bootAnimationId: String,
    bootAnimationPath: String?,
    onDismiss: () -> Unit,
    onSelectTheme: (String) -> Unit,
    onRequestWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRequestBgm: () -> Unit,
    onClearBgm: () -> Unit,
    onRequestTrayBgm: () -> Unit,
    onClearTrayBgm: () -> Unit,
    onSaveCustomTheme: (String) -> Unit,
    onUpdateCustomTheme: (String, String) -> Unit,
    onApplyCustomTheme: (String) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    onSelectBootAnimation: (String) -> Unit,
    onRequestBootAnimation: () -> Unit,
    onClearBootAnimation: () -> Unit,
    navActions: Flow<NavAction>,
    wallpaperAlignX: Float = 0f,
    wallpaperAlignY: Float = 0f,
    onNudgeWallpaper: (Float, Float) -> Unit = { _, _ -> },
    onResetWallpaper: () -> Unit = {},
    initialSection: CustomizeSection = CustomizeSection.PresetThemes,
) {
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    var creatingCustomTheme by remember { mutableStateOf(false) }
    var pane by remember { mutableStateOf(CustomizePane.Nav) }
    var itemIndex by remember { mutableIntStateOf(0) }
    // Select opens a small menu on the focused theme: edit it, or remove it.
    var themeMenuId by remember { mutableStateOf<String?>(null) }
    var themeMenuIndex by remember { mutableIntStateOf(0) }
    var editingThemeId by remember { mutableStateOf<String?>(null) }
    var formRowIndex by remember { mutableIntStateOf(0) }
    var themeName by remember { mutableStateOf("") }
    val gridState = rememberLazyGridState()
    val nameFocus = remember { FocusRequester() }

    fun leaveForm() {
        creatingCustomTheme = false
        editingThemeId = null
        formRowIndex = 0
        pane = CustomizePane.Content
    }

    fun beginEditTheme(id: String, name: String) {
        // Load it first: editing means changing the wallpaper / BGM that theme holds, so the
        // pickers have to be acting on that theme's media, not whatever was last applied.
        onApplyCustomTheme(id)
        editingThemeId = id
        themeName = name
        themeMenuId = null
        creatingCustomTheme = true
        formRowIndex = 0
    }

    val entries = customizeEntries(
        section = section,
        activeThemeId = activeThemeId,
        customThemes = customThemes,
        bootAnimationId = bootAnimationId,
        bootAnimationPath = bootAnimationPath,
        onRequestBootAnimation = onRequestBootAnimation,
        onClearBootAnimation = onClearBootAnimation,
        menuThemeId = themeMenuId,
        menuIndex = themeMenuIndex,
        onSelectTheme = onSelectTheme,
        onNewTheme = {
            creatingCustomTheme = true
            formRowIndex = 0
        },
        onApplyCustomTheme = onApplyCustomTheme,
        onDeleteCustomTheme = {
            onDeleteCustomTheme(it)
            themeMenuId = null
        },
        onEditCustomTheme = { id, name -> beginEditTheme(id, name) },
        onOpenThemeMenu = {
            themeMenuId = it
            themeMenuIndex = 0
        },
        onCloseThemeMenu = { themeMenuId = null },
        onSelectBootAnimation = onSelectBootAnimation,
    )

    val formRows = createFormRows(
        hasCustomWallpaper = hasCustomWallpaper,
        hasCustomBgm = hasCustomBgm,
        hasTrayBgm = hasTrayBgm,
    )

    // Keep focus inside the list as sections change size under it (a theme was just deleted).
    val safeItemIndex = itemIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val safeFormIndex = formRowIndex.coerceIn(0, (formRows.size - 1).coerceAtLeast(0))

    // Exit has to finish before the parent drops the sheet, so dismissal is deferred until the
    // transition settles rather than flipping the flag straight away.
    val transition = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (!transition.targetState && !transition.currentState) onDismiss()
    }
    val requestDismiss = { transition.targetState = false }
    BackHandler(onBack = { if (creatingCustomTheme) leaveForm() else requestDismiss() })

    // Collected once, so a held direction is never dropped while the tree recomposes around it.
    val onNav by rememberUpdatedState<(NavAction) -> Unit> { action ->
        when {
            creatingCustomTheme -> when (action) {
                NavAction.Up -> formRowIndex =
                    (safeFormIndex - 1).coerceAtLeast(0)
                NavAction.Down -> formRowIndex =
                    (safeFormIndex + 1).coerceAtMost(formRows.size - 1)
                NavAction.Left -> formRows.getOrNull(safeFormIndex)?.onLeft?.invoke()
                NavAction.Right -> formRows.getOrNull(safeFormIndex)?.onRight?.invoke()
                NavAction.Confirm -> when (val row = formRows.getOrNull(safeFormIndex)?.kind) {
                    CreateFormKind.Wallpaper -> onRequestWallpaper()
                    CreateFormKind.ClearWallpaper -> onClearWallpaper()
                    CreateFormKind.Bgm -> onRequestBgm()
                    CreateFormKind.ClearBgm -> onClearBgm()
                    CreateFormKind.TrayBgm -> onRequestTrayBgm()
                    CreateFormKind.ClearTrayBgm -> onClearTrayBgm()
                    CreateFormKind.Name -> runCatching { nameFocus.requestFocus() }
                    CreateFormKind.Save -> {
                        val name = themeName.trim().ifBlank { "My theme" }
                        editingThemeId?.let { onUpdateCustomTheme(it, name) }
                            ?: onSaveCustomTheme(name)
                        themeName = ""
                        leaveForm()
                    }
                    CreateFormKind.Align, null -> Unit
                }
                NavAction.Cancel -> leaveForm()
                else -> Unit
            }

            themeMenuId != null -> when (action) {
                NavAction.Up -> themeMenuIndex = 0
                NavAction.Down -> themeMenuIndex = 1
                NavAction.Confirm -> {
                    val id = themeMenuId
                    val theme = customThemes.firstOrNull { it.id == id }
                    when {
                        id == null -> Unit
                        themeMenuIndex == 0 -> beginEditTheme(id, theme?.name.orEmpty())
                        else -> {
                            onDeleteCustomTheme(id)
                            themeMenuId = null
                        }
                    }
                }
                NavAction.Cancel, NavAction.ScrapeMenu -> themeMenuId = null
                else -> Unit
            }

            pane == CustomizePane.Nav -> when (action) {
                NavAction.Up -> {
                    val next = (section.ordinal - 1).coerceAtLeast(0)
                    section = CustomizeSection.entries[next]
                    itemIndex = 0
                }
                NavAction.Down -> {
                    val next = (section.ordinal + 1)
                        .coerceAtMost(CustomizeSection.entries.size - 1)
                    section = CustomizeSection.entries[next]
                    itemIndex = 0
                }
                NavAction.Right, NavAction.Confirm -> if (entries.isNotEmpty()) {
                    pane = CustomizePane.Content
                    itemIndex = 0
                }
                NavAction.Cancel -> requestDismiss()
                else -> Unit
            }

            else -> when (action) {
                NavAction.Left -> if (safeItemIndex % GRID_COLUMNS == 0) {
                    pane = CustomizePane.Nav
                } else {
                    itemIndex = safeItemIndex - 1
                }
                NavAction.Right -> itemIndex =
                    (safeItemIndex + 1).coerceAtMost(entries.size - 1)
                NavAction.Up -> {
                    val next = safeItemIndex - GRID_COLUMNS
                    if (next < 0) pane = CustomizePane.Nav else itemIndex = next
                }
                NavAction.Down -> itemIndex =
                    (safeItemIndex + GRID_COLUMNS).coerceAtMost(entries.size - 1)
                NavAction.Confirm -> entries.getOrNull(safeItemIndex)?.onActivate?.invoke()
                NavAction.ScrapeMenu -> entries.getOrNull(safeItemIndex)?.onSecondary?.invoke()
                NavAction.Cancel -> pane = CustomizePane.Nav
                else -> Unit
            }
        }
    }
    LaunchedEffect(Unit) { navActions.collect { onNav(it) } }

    // Follow the focused card without snapping the grid to the top on every sideways step.
    LaunchedEffect(safeItemIndex, pane, section) {
        if (pane == CustomizePane.Content) gridState.scrollItemIntoView(safeItemIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        XoraSheetScrim(visible = transition.targetState, onClick = requestDismiss)
        AnimatedVisibility(
            visibleState = transition,
            enter = fadeIn(tween(SHEET_ENTER_MS)) +
                scaleIn(tween(SHEET_ENTER_MS, easing = FastOutSlowInEasing), initialScale = 0.92f),
            exit = fadeOut(tween(SHEET_EXIT_MS)) +
                scaleOut(tween(SHEET_EXIT_MS), targetScale = 0.94f),
            modifier = Modifier.align(Alignment.Center),
        ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
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
                            focused = entry == section && pane == CustomizePane.Nav,
                            onClick = {
                                section = entry
                                creatingCustomTheme = false
                                itemIndex = 0
                                pane = CustomizePane.Nav
                            },
                        )
                    }
                }
            }

            CustomizePanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
                PanelHeader(section.label)
                Box(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
                    if (section == CustomizeSection.CustomThemes && creatingCustomTheme) {
                        CreateCustomThemeContent(
                            rows = formRows,
                            focusedIndex = safeFormIndex,
                            name = themeName,
                            onNameChange = { themeName = it },
                            nameFocus = nameFocus,
                            hasCustomWallpaper = hasCustomWallpaper,
                            customWallpaperLabel = customWallpaperLabel,
                            hasCustomBgm = hasCustomBgm,
                            hasTrayBgm = hasTrayBgm,
                            onRequestWallpaper = onRequestWallpaper,
                            onClearWallpaper = onClearWallpaper,
                            onRequestBgm = onRequestBgm,
                            onClearBgm = onClearBgm,
                            onRequestTrayBgm = onRequestTrayBgm,
                            onClearTrayBgm = onClearTrayBgm,
                            wallpaperAlignX = wallpaperAlignX,
                            wallpaperAlignY = wallpaperAlignY,
                            onNudgeWallpaper = onNudgeWallpaper,
                            onResetWallpaper = onResetWallpaper,
                            onFocusRow = { formRowIndex = it },
                            onCancel = { leaveForm() },
                            editing = editingThemeId != null,
                            onSave = { name ->
                                editingThemeId?.let { onUpdateCustomTheme(it, name) }
                                    ?: onSaveCustomTheme(name)
                                themeName = ""
                                leaveForm()
                            },
                        )
                    } else if (entries.isEmpty()) {
                        SectionPlaceholder("Coming soon")
                    } else {
                        CustomizeGrid(
                            state = gridState,
                            entries = entries,
                            focusedIndex = safeItemIndex.takeIf { pane == CustomizePane.Content },
                            onFocus = {
                                pane = CustomizePane.Content
                                itemIndex = it
                            },
                        )
                    }
                }
            }
        }
        }
    }
}

/** How the Customize and pin-picker sheets arrive and leave. */
internal val SHEET_BACKDROP_BLUR = 16.dp
internal const val SHEET_ENTER_MS = 220
internal const val SHEET_EXIT_MS = 160
internal val SheetScrimColor = Color.Black.copy(alpha = 0.42f)

/** Dark glass plate with the thin light edge both panels share. */
@Composable
private fun CustomizePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.xoraSettingsPanelSurface(), content = content)
}

@Composable
private fun PanelHeader(text: String) {
    XoraSettingsPanelHeader(text)
}

@Composable
private fun CustomizeNavRow(
    label: String,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .xoraFocusHighlight(focused)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        XoraSecondaryText(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fillColor = when {
                focused -> Color.White
                selected -> Color.White
                else -> Color.White.copy(alpha = 0.62f)
            },
        )
    }
}

@Composable
private fun SectionPlaceholder(text: String) {
    XoraSecondaryText(
        text = text,
        fontSize = 14.sp,
        fillColor = Color.White.copy(alpha = 0.55f),
    )
}

/** The cards for one section, in the order the grid lays them out. */
@Composable
private fun customizeEntries(
    section: CustomizeSection,
    activeThemeId: String,
    customThemes: List<CustomTheme>,
    bootAnimationId: String,
    bootAnimationPath: String?,
    onRequestBootAnimation: () -> Unit,
    onClearBootAnimation: () -> Unit,
    menuThemeId: String?,
    menuIndex: Int,
    onSelectTheme: (String) -> Unit,
    onNewTheme: () -> Unit,
    onApplyCustomTheme: (String) -> Unit,
    onDeleteCustomTheme: (String) -> Unit,
    onEditCustomTheme: (String, String) -> Unit,
    onOpenThemeMenu: (String) -> Unit,
    onCloseThemeMenu: () -> Unit,
    onSelectBootAnimation: (String) -> Unit,
): List<CustomizeEntry> = when (section) {
    CustomizeSection.PresetThemes -> ShellThemeCatalog.all.map { theme ->
        CustomizeEntry(
            key = theme.id.id,
            name = theme.id.displayName,
            selected = theme.id.id.equals(activeThemeId, ignoreCase = true),
            onActivate = { onSelectTheme(theme.id.id) },
        ) { ThemeSwatchPreview(theme) }
    }

    CustomizeSection.CustomThemes -> buildList {
        add(
            CustomizeEntry(
                key = "__new_custom_theme",
                name = "Save current as…",
                selected = false,
                onActivate = onNewTheme,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    XoraSecondaryText(text = "+ New", fontSize = 15.sp)
                }
            },
        )
        customThemes.forEach { theme ->
            val menuOpen = menuThemeId == theme.id
            add(
                CustomizeEntry(
                    key = theme.id,
                    name = theme.name,
                    selected = false,
                    onActivate = {
                        if (menuOpen) onCloseThemeMenu() else onApplyCustomTheme(theme.id)
                    },
                    onSecondary = { onOpenThemeMenu(theme.id) },
                ) {
                    if (menuOpen) {
                        ThemeMenuOverlay(
                            highlighted = menuIndex,
                            onEdit = { onEditCustomTheme(theme.id, theme.name) },
                            onRemove = { onDeleteCustomTheme(theme.id) },
                        )
                    } else {
                        ArtworkImage(
                            path = theme.wallpaperPath,
                            contentDescription = theme.name,
                            fallbackText = theme.name.take(1).uppercase(Locale.US),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                },
            )
        }
    }

    CustomizeSection.CustomIcons -> emptyList()

    CustomizeSection.BootAnimation -> buildList {
        val usingCustom = bootAnimationPath != null &&
            bootAnimationId.equals(CUSTOM_BOOT_ANIMATION_ID, ignoreCase = true)
        add(
            CustomizeEntry(
                key = DEFAULT_BOOT_ANIMATION_ID,
                name = "Default",
                selected = !usingCustom,
                onActivate = { onSelectBootAnimation(DEFAULT_BOOT_ANIMATION_ID) },
            ) {
                ArtworkImage(
                    path = null,
                    contentDescription = "Default boot animation",
                    fallbackText = "B",
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
        if (bootAnimationPath != null) {
            add(
                CustomizeEntry(
                    key = CUSTOM_BOOT_ANIMATION_ID,
                    name = bootAnimationPath.substringAfterLast('/'),
                    selected = usingCustom,
                    onActivate = { onSelectBootAnimation(CUSTOM_BOOT_ANIMATION_ID) },
                    // Select / long-press removes it, the same gesture custom themes use.
                    onSecondary = onClearBootAnimation,
                ) {
                    ArtworkImage(
                        path = bootAnimationPath,
                        contentDescription = "Your boot animation",
                        fallbackText = "\u25B6",
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        }
        add(
            CustomizeEntry(
                key = "__add_boot_animation",
                name = if (bootAnimationPath == null) "Add your own" else "Replace",
                selected = false,
                onActivate = onRequestBootAnimation,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    XoraSecondaryText(text = "+ Video", fontSize = 15.sp)
                }
            },
        )
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

/** Select on a saved theme: edit it, or remove it. Drawn on the card so the target is obvious. */
@Composable
private fun ThemeMenuOverlay(
    highlighted: Int,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.80f)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThemeMenuRow(label = "Edit", highlighted = highlighted == 0, onClick = onEdit)
        ThemeMenuRow(label = "Remove", highlighted = highlighted == 1, onClick = onRemove)
    }
}

@Composable
private fun ThemeMenuRow(label: String, highlighted: Boolean, onClick: () -> Unit) {
    XoraSecondaryText(
        text = label,
        fontSize = 14.sp,
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
        fillColor = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .xoraFocusHighlight(highlighted, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
    )
}

@Composable
private fun CustomizeGrid(
    state: LazyGridState,
    entries: List<CustomizeEntry>,
    focusedIndex: Int?,
    onFocus: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = state,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            itemsIndexedKeyed(entries) { index, entry ->
                CustomizeGridCard(
                    name = entry.name,
                    selected = entry.selected,
                    focused = index == focusedIndex,
                    onClick = {
                        onFocus(index)
                        entry.onActivate()
                    },
                    onLongClick = entry.onSecondary,
                    preview = entry.preview,
                )
            }
        }
        CustomizeScrollbar(
            state = state,
            modifier = Modifier.padding(start = 10.dp).fillMaxHeight(),
        )
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.itemsIndexedKeyed(
    entries: List<CustomizeEntry>,
    content: @Composable (Int, CustomizeEntry) -> Unit,
) {
    items(count = entries.size, key = { entries[it].key }) { index ->
        content(index, entries[index])
    }
}

/**
 * Square thumbnail in a light frame with a centred caption — the one card shape every
 * Customize section uses.
 */
@Composable
private fun CustomizeGridCard(
    name: String,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
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
                    if (selected) FocusRingColor else Color.White.copy(alpha = 0.92f),
                    ThumbShape,
                )
                .then(
                    if (focused) {
                        Modifier.border(3.dp, FocusRingColor, ThumbShape)
                    } else {
                        Modifier
                    },
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
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            fillColor = if (focused) FocusRingColor else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

/**
 * Proportional bar down the right edge of the content pane. Hidden when everything already fits,
 * so short sections do not grow a decorative stub.
 */
@Composable
private fun CustomizeScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total == 0 || visible.isEmpty()) return@derivedStateOf null
            val totalRows = ceilDiv(total, GRID_COLUMNS)
            val visibleRows = ceilDiv(visible.size, GRID_COLUMNS)
            if (visibleRows >= totalRows) return@derivedStateOf null
            val firstRow = state.firstVisibleItemIndex / GRID_COLUMNS
            val scrollable = (totalRows - visibleRows).toFloat()
            ScrollbarMetrics(
                thumbFraction = (visibleRows.toFloat() / totalRows).coerceIn(0.08f, 1f),
                offsetFraction = (firstRow / scrollable).coerceIn(0f, 1f),
            )
        }
    }
    val bar = metrics ?: return
    Box(
        modifier = modifier
            .width(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(bar.thumbFraction)
                .fillMaxWidth()
                .align(BiasAlignment(bar.offsetFraction))
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.55f)),
        )
    }
}

private data class ScrollbarMetrics(val thumbFraction: Float, val offsetFraction: Float)

/** 0 = pinned to the top of the track, 1 = pinned to the bottom. */
private fun BiasAlignment(fraction: Float): Alignment =
    androidx.compose.ui.BiasAlignment(
        horizontalBias = 0f,
        verticalBias = (fraction * 2f - 1f).coerceIn(-1f, 1f),
    )

private fun ceilDiv(value: Int, by: Int): Int = (value + by - 1) / by

/** Scroll only far enough to reveal the card, so sideways steps do not jump the grid. */
private suspend fun LazyGridState.scrollItemIntoView(index: Int) {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        animateScrollToItem(index)
        return
    }
    val top = item.offset.y
    val bottom = top + item.size.height
    val viewportTop = layoutInfo.viewportStartOffset
    val viewportBottom = layoutInfo.viewportEndOffset
    when {
        top < viewportTop -> animateScrollBy((top - viewportTop).toFloat())
        bottom > viewportBottom -> animateScrollBy((bottom - viewportBottom).toFloat())
    }
}

/** The controls in the create-theme form, in focus order. */
private enum class CreateFormKind {
    Wallpaper,
    ClearWallpaper,
    Align,
    Bgm,
    ClearBgm,
    TrayBgm,
    ClearTrayBgm,
    Name,
    Save,
}

private class CreateFormRow(
    val kind: CreateFormKind,
    val onLeft: (() -> Unit)? = null,
    val onRight: (() -> Unit)? = null,
)

private fun createFormRows(
    hasCustomWallpaper: Boolean,
    hasCustomBgm: Boolean,
    hasTrayBgm: Boolean,
): List<CreateFormRow> = buildList {
    add(CreateFormRow(CreateFormKind.Wallpaper))
    if (hasCustomWallpaper) add(CreateFormRow(CreateFormKind.ClearWallpaper))
    add(CreateFormRow(CreateFormKind.Align))
    add(CreateFormRow(CreateFormKind.Bgm))
    if (hasCustomBgm) add(CreateFormRow(CreateFormKind.ClearBgm))
    add(CreateFormRow(CreateFormKind.TrayBgm))
    if (hasTrayBgm) add(CreateFormRow(CreateFormKind.ClearTrayBgm))
    add(CreateFormRow(CreateFormKind.Name))
    add(CreateFormRow(CreateFormKind.Save))
}

@Composable
private fun CreateCustomThemeContent(
    rows: List<CreateFormRow>,
    focusedIndex: Int,
    name: String,
    onNameChange: (String) -> Unit,
    nameFocus: FocusRequester,
    hasCustomWallpaper: Boolean,
    customWallpaperLabel: String,
    hasCustomBgm: Boolean,
    hasTrayBgm: Boolean,
    onRequestWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onRequestBgm: () -> Unit,
    onClearBgm: () -> Unit,
    onRequestTrayBgm: () -> Unit,
    onClearTrayBgm: () -> Unit,
    wallpaperAlignX: Float,
    wallpaperAlignY: Float,
    onNudgeWallpaper: (Float, Float) -> Unit,
    onResetWallpaper: () -> Unit,
    onFocusRow: (Int) -> Unit,
    editing: Boolean = false,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    val focusedKind = rows.getOrNull(focusedIndex)?.kind
    fun rowIndexOf(kind: CreateFormKind) = rows.indexOfFirst { it.kind == kind }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scroll),
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
            FormButton(
                text = "Choose wallpaper",
                focused = focusedKind == CreateFormKind.Wallpaper,
                onClick = {
                    onFocusRow(rowIndexOf(CreateFormKind.Wallpaper))
                    runCatching { onRequestWallpaper() }
                },
            )
            if (hasCustomWallpaper) {
                FormOutlinedButton(
                    text = "Restore theme wallpaper",
                    focused = focusedKind == CreateFormKind.ClearWallpaper,
                    onClick = {
                        onFocusRow(rowIndexOf(CreateFormKind.ClearWallpaper))
                        onClearWallpaper()
                    },
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.focusRing(focusedKind == CreateFormKind.Align),
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
            if (focusedKind == CreateFormKind.Align) {
                XoraSecondaryText(
                    text = "Nudge with the stick · offset " +
                        "${"%.2f".format(Locale.US, wallpaperAlignX)}, " +
                        "${"%.2f".format(Locale.US, wallpaperAlignY)}",
                    fontSize = 12.sp,
                    fillColor = Color.White.copy(alpha = 0.45f),
                )
            } else if (wallpaperAlignX != 0f || wallpaperAlignY != 0f) {
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
            FormButton(
                text = "Choose BGM",
                focused = focusedKind == CreateFormKind.Bgm,
                onClick = {
                    onFocusRow(rowIndexOf(CreateFormKind.Bgm))
                    runCatching { onRequestBgm() }
                },
            )
            if (hasCustomBgm) {
                FormOutlinedButton(
                    text = "Restore theme / default BGM",
                    focused = focusedKind == CreateFormKind.ClearBgm,
                    onClick = {
                        onFocusRow(rowIndexOf(CreateFormKind.ClearBgm))
                        onClearBgm()
                    },
                )
            }

            XoraSecondaryText(
                text = "Shortcut menu music",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            XoraSecondaryText(
                text = if (hasTrayBgm) {
                    "Fades in when the shortcut menu opens"
                } else {
                    "Optional — the main BGM keeps playing"
                },
                fontSize = 13.sp,
                fillColor = Color.White.copy(alpha = 0.55f),
            )
            FormButton(
                text = "Choose shortcut menu BGM",
                focused = focusedKind == CreateFormKind.TrayBgm,
                onClick = {
                    onFocusRow(rowIndexOf(CreateFormKind.TrayBgm))
                    runCatching { onRequestTrayBgm() }
                },
            )
            if (hasTrayBgm) {
                FormOutlinedButton(
                    text = "Remove shortcut menu BGM",
                    focused = focusedKind == CreateFormKind.ClearTrayBgm,
                    onClick = {
                        onFocusRow(rowIndexOf(CreateFormKind.ClearTrayBgm))
                        onClearTrayBgm()
                    },
                )
            }

            XoraSecondaryText(
                text = "Name this theme",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                placeholder = { Text("My theme") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(nameFocus)
                    .focusRing(focusedKind == CreateFormKind.Name),
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
                    enabled = hasCustomWallpaper || hasCustomBgm || hasTrayBgm,
                    modifier = Modifier.focusRing(focusedKind == CreateFormKind.Save),
                ) {
                    Text(if (editing) "Save changes" else "Save custom theme")
                }
            }
        }
        CustomizeFormScrollbar(
            scroll = scroll,
            modifier = Modifier.padding(start = 10.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun FormButton(text: String, focused: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().focusRing(focused)) {
        Text(text = text)
    }
}

@Composable
private fun FormOutlinedButton(text: String, focused: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().focusRing(focused)) {
        Text(text = text)
    }
}

private fun Modifier.focusRing(focused: Boolean): Modifier =
    if (focused) border(2.dp, FocusRingColor, RoundedCornerShape(10.dp)) else this

/** Same bar as the grid's, driven by a plain scroll offset instead of item rows. */
@Composable
private fun CustomizeFormScrollbar(
    scroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    val metrics by remember(scroll) {
        derivedStateOf {
            val max = scroll.maxValue
            if (max <= 0 || max == Int.MAX_VALUE) return@derivedStateOf null
            val viewport = scroll.viewportSize
            if (viewport <= 0) return@derivedStateOf null
            val content = viewport + max
            ScrollbarMetrics(
                thumbFraction = (viewport.toFloat() / content).coerceIn(0.08f, 1f),
                offsetFraction = (scroll.value.toFloat() / max).coerceIn(0f, 1f),
            )
        }
    }
    val bar = metrics ?: return
    Box(
        modifier = modifier
            .width(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight(bar.thumbFraction)
                .fillMaxWidth()
                .align(BiasAlignment(bar.offsetFraction))
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.55f)),
        )
    }
}
