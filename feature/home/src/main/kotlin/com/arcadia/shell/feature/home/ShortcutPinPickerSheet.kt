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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.arcadiaBackdropBlur
import com.arcadia.shell.designsystem.XoraSettingsPanelHeader
import com.arcadia.shell.designsystem.XoraSettingsPanelRule
import com.arcadia.shell.designsystem.xoraFocusHighlight
import com.arcadia.shell.designsystem.xoraSettingsPanelSurface
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.feature.home.component.ArtworkImage
import java.util.Locale

private val PanelShape = RoundedCornerShape(20.dp)
private val ThumbShape = RoundedCornerShape(8.dp)
private val FocusRingColor = Color(0xFF8ED6FF)

/**
 * Pin picker for an empty Vita bubble: platforms down the left, that platform's ROMs / apps as
 * cards on the right, search over the grid.
 *
 * Focus lives in [ShortcutPickerUiState] rather than here, because the pad is routed through the
 * ViewModel while this is up — the same split the Customize window uses, inverted: there the
 * sheet owns focus because it owns the item list; here the ViewModel already owns the filtered
 * results, so it owns the index into them too.
 *
 * Deliberately plain layout rather than a Dialog: on dual-screen the hub can live inside a
 * FLAG_NOT_FOCUSABLE Presentation, where nested dialog windows crash.
 */
@Composable
fun ShortcutPinPickerSheet(
    picker: ShortcutPickerUiState,
    /** Drives the exit animation; the parent keeps the sheet composed until it finishes. */
    visible: Boolean,
    onDismiss: () -> Unit,
    onSelectPlatform: (Int) -> Unit,
    onSelectItem: (Int) -> Unit,
    onConfirm: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFocusPane: (ShortcutPickerPane) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val searchFocus = remember { FocusRequester() }
    val transition = remember { MutableTransitionState(false) }
    transition.targetState = visible
    val backdropBlur by animateDpAsState(
        targetValue = if (visible) SHEET_BACKDROP_BLUR else 0.dp,
        animationSpec = tween(SHEET_ENTER_MS, easing = FastOutSlowInEasing),
        label = "pinPickerBackdropBlur",
    )

    BackHandler(onBack = onDismiss)

    // Keep the keyboard on the field only while the search pane actually holds focus.
    LaunchedEffect(picker.pane) {
        if (picker.pane == ShortcutPickerPane.Search) {
            runCatching { searchFocus.requestFocus() }
        }
    }
    LaunchedEffect(picker.itemIndex, picker.pane) {
        if (picker.pane == ShortcutPickerPane.Content) {
            gridState.scrollPinItemIntoView(picker.itemIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visibleState = transition,
            enter = fadeIn(tween(SHEET_ENTER_MS)),
            exit = fadeOut(tween(SHEET_EXIT_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .arcadiaBackdropBlur(backdropBlur, SheetScrimColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
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
            PinPickerPanel(modifier = Modifier.width(208.dp).fillMaxHeight()) {
                PinPanelHeader("Platforms")
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    picker.platforms.forEachIndexed { index, summary ->
                        PlatformNavRow(
                            label = summary.platform.displayName,
                            selected = index == picker.platformIndex,
                            focused = index == picker.platformIndex &&
                                picker.pane == ShortcutPickerPane.Platforms,
                            onClick = {
                                onSelectPlatform(index)
                                onFocusPane(ShortcutPickerPane.Platforms)
                            },
                        )
                    }
                }
            }

            PinPickerPanel(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XoraTitleText(
                        text = (picker.platform?.platform?.displayName ?: "Library")
                            .uppercase(Locale.US),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    SearchField(
                        query = picker.query,
                        focused = picker.pane == ShortcutPickerPane.Search,
                        focusRequester = searchFocus,
                        onQueryChange = onQueryChange,
                        onClick = { onFocusPane(ShortcutPickerPane.Search) },
                    )
                }
                XoraSettingsPanelRule(modifier = Modifier.padding(top = 8.dp))

                Box(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
                    if (picker.results.isEmpty()) {
                        XoraSecondaryText(
                            text = if (picker.query.isBlank()) {
                                "Nothing here yet"
                            } else {
                                "No matches for \"${picker.query}\""
                            },
                            fontSize = 14.sp,
                            fillColor = Color.White.copy(alpha = 0.55f),
                        )
                    } else {
                        Row(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(PIN_PICKER_COLUMNS),
                                state = gridState,
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            ) {
                                items(
                                    count = picker.results.size,
                                    key = { picker.results[it].id },
                                ) { index ->
                                    val game = picker.results[index]
                                    PinCard(
                                        title = game.title,
                                        artPath = game.boxArtPath
                                            ?: game.shortcutIcon
                                            ?: game.heroImagePath,
                                        focused = index == picker.itemIndex &&
                                            picker.pane == ShortcutPickerPane.Content,
                                        onClick = {
                                            if (index == picker.itemIndex &&
                                                picker.pane == ShortcutPickerPane.Content
                                            ) {
                                                onConfirm()
                                            } else {
                                                onSelectItem(index)
                                            }
                                        },
                                    )
                                }
                            }
                            PinPickerScrollbar(
                                state = gridState,
                                modifier = Modifier.padding(start = 10.dp).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PinPickerPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.xoraSettingsPanelSurface(), content = content)
}

@Composable
private fun PinPanelHeader(text: String) {
    XoraSettingsPanelHeader(text)
}

@Composable
private fun PlatformNavRow(
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
            fillColor = if (selected || focused) Color.White else Color.White.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    focused: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(32.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f), shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) FocusRingColor else Color.White.copy(alpha = 0.30f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        if (query.isEmpty()) {
            Text(
                text = "Search",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun PinCard(
    title: String,
    artPath: String?,
    focused: Boolean,
    onClick: () -> Unit,
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
                .background(Color.White.copy(alpha = 0.92f), ThumbShape)
                .then(
                    if (focused) Modifier.border(3.dp, FocusRingColor, ThumbShape) else Modifier,
                )
                .padding(3.dp)
                .clickable(onClick = onClick),
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))) {
                ArtworkImage(
                    path = artPath,
                    contentDescription = title,
                    fallbackText = title.take(1).uppercase(Locale.US),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        XoraSecondaryText(
            text = title,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            fillColor = if (focused) FocusRingColor else Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun PinPickerScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total == 0 || visible.isEmpty()) return@derivedStateOf null
            val totalRows = (total + PIN_PICKER_COLUMNS - 1) / PIN_PICKER_COLUMNS
            val visibleRows = (visible.size + PIN_PICKER_COLUMNS - 1) / PIN_PICKER_COLUMNS
            if (visibleRows >= totalRows) return@derivedStateOf null
            val firstRow = state.firstVisibleItemIndex / PIN_PICKER_COLUMNS
            val scrollable = (totalRows - visibleRows).toFloat()
            (visibleRows.toFloat() / totalRows).coerceIn(0.08f, 1f) to
                (firstRow / scrollable).coerceIn(0f, 1f)
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
                .fillMaxHeight(bar.first)
                .fillMaxWidth()
                .align(
                    androidx.compose.ui.BiasAlignment(
                        horizontalBias = 0f,
                        verticalBias = (bar.second * 2f - 1f).coerceIn(-1f, 1f),
                    ),
                )
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.55f)),
        )
    }
}

/** Reveal the focused card without snapping the grid to the top on every sideways step. */
private suspend fun LazyGridState.scrollPinItemIntoView(index: Int) {
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
