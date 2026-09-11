package com.arcadia.shell.feature.home.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.XoraSettingsPanelHeader
import com.arcadia.shell.designsystem.xoraFocusHighlight
import com.arcadia.shell.designsystem.xoraSettingsPanelSurface
import com.arcadia.shell.designsystem.motionMillis
import com.arcadia.shell.datastore.VisualPerformanceChoices
import com.arcadia.shell.datastore.VisualPerformanceMode
import com.arcadia.shell.datastore.visualPerformanceModeLabel
import com.arcadia.shell.datastore.visualPerformanceModeSubtitle
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.StartSettingsAction
import com.arcadia.shell.feature.home.StartSettingsRow
import com.arcadia.shell.feature.home.StartSettingsTrailingIcon
import com.arcadia.shell.feature.home.StartSettingsUiState

private val ListShape = RoundedCornerShape(22.dp)
private val RowFocusShape = RoundedCornerShape(14.dp)

/**
 * Start-button app config: one floating glass list. Root is the category list;
 * Confirm drills in, Back closes the overlay rather than returning to Settings.
 *
 * Overlay (not Dialog) so Dual Mode [android.app.Presentation] panes can host it without a
 * nested window. Enter/exit uses scale+fade with a light spring overshoot.
 */
@Composable
fun StartSettingsPanel(
    state: StartSettingsUiState,
    onSelectRow: (Int) -> Unit,
    onActivate: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    onSelectPerformanceMode: (VisualPerformanceMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val enterMs = motionMillis(ArcadiaMotion.Slow)
    val enterSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    BackHandler(enabled = state.open, onBack = onBack)

    AnimatedVisibility(
        visible = state.open,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + scaleIn(
            animationSpec = if (enterMs == 0) arcadiaTween(0) else enterSpring,
            initialScale = 0.88f,
        ),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + scaleOut(
            animationSpec = arcadiaTween(ArcadiaMotion.Fast),
            targetScale = 0.94f,
        ),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
            // One panel: categories ride a compact strip in the header rather than a
            // full-height capsule down the side.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.74f)
                    .xoraSettingsPanelSurface(),
            ) {
                XoraSettingsPanelHeader(state.title)
                val categoryFadeIn = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
                val categoryFadeOut = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
                val pageKey = if (state.inCategory) "cat:${state.category.name}" else "root"
                AnimatedContent(
                    targetState = pageKey,
                    transitionSpec = {
                        categoryFadeIn togetherWith categoryFadeOut
                    },
                    label = "startSettingsPage",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    val rows = state.rows.takeIf { page == pageKey }.orEmpty()
                    val listState = rememberLazyListState()
                    LaunchedEffect(state.selectedRowIndex, page, rows.size) {
                        if (rows.isEmpty()) return@LaunchedEffect
                        listState.animateScrollToItem(
                            state.selectedRowIndex.coerceIn(0, rows.lastIndex),
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 6.dp, bottom = 4.dp),
                    ) {
                        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                            StartSettingsListRow(
                                row = row,
                                selected = index == state.selectedRowIndex,
                                content = glass.content,
                                muted = glass.contentMuted,
                                onClick = {
                                    if (row is StartSettingsRow.Header) return@StartSettingsListRow
                                    onSelectRow(index)
                                    onActivate()
                                },
                            )
                        }
                    }
                }
            }
            if (state.performancePickerOpen) {
                VisualPerformancePickerOverlay(
                    selectedMode = state.settings.visualPerformanceMode,
                    focusedIndex = state.performancePickerIndex,
                    content = glass.content,
                    muted = glass.contentMuted,
                    onSelect = onSelectPerformanceMode,
                    onDismiss = onBack,
                )
            }
        }
    }
}

@Composable
private fun VisualPerformancePickerOverlay(
    selectedMode: VisualPerformanceMode,
    focusedIndex: Int,
    content: Color,
    muted: Color,
    onSelect: (VisualPerformanceMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.72f)
                .liquidGlass(
                    shape = ListShape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Strong,
                    shimmer = true,
                )
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Performance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(1.dp)
                    .background(glass.border.copy(alpha = 0.35f)),
            )
            VisualPerformanceChoices.forEachIndexed { index, mode ->
                val focused = index == focusedIndex
                StartSettingsListRow(
                    row = StartSettingsRow.Action(
                        id = "perf_${mode.name}",
                        title = visualPerformanceModeLabel(mode),
                        subtitle = buildString {
                            append(visualPerformanceModeSubtitle(mode))
                            if (mode == selectedMode) append(" · Active")
                        },
                        action = StartSettingsAction.SelectVisualPerformance(mode),
                    ),
                    selected = focused,
                    content = content,
                    muted = muted,
                    onClick = { onSelect(mode) },
                )
            }
        }
    }
}

@Composable
private fun StartSettingsListRow(
    row: StartSettingsRow,
    selected: Boolean,
    content: Color,
    muted: Color,
    onClick: () -> Unit,
) {
    if (row is StartSettingsRow.Header) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = content.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!row.subtitle.isNullOrBlank()) {
                Text(
                    text = row.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .xoraFocusHighlight(selected, shape = RowFocusShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = when (row) {
                is StartSettingsRow.Toggle ->
                    row.subtitle ?: if (row.checked) "On" else "Off"
                is StartSettingsRow.Action -> row.subtitle
                is StartSettingsRow.Header -> row.subtitle
            }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = muted.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        when {
            row is StartSettingsRow.Toggle -> {
                ToggleGlyph(checked = row.checked, tint = content)
            }
            row.trailingIcon == StartSettingsTrailingIcon.Edit && selected -> {
                PencilGlyph(tint = content.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun PencilGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(18.dp)) {
        val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.72f)
            lineTo(size.width * 0.68f, size.height * 0.26f)
            lineTo(size.width * 0.78f, size.height * 0.36f)
            lineTo(size.width * 0.32f, size.height * 0.82f)
            close()
        }
        drawPath(path, color = tint, style = stroke)
        drawLine(
            color = tint,
            start = Offset(size.width * 0.18f, size.height * 0.86f),
            end = Offset(size.width * 0.38f, size.height * 0.86f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ToggleGlyph(checked: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val focusEnd = LocalShellTheme.current.colors.focusEnd
    val track = if (checked) focusEnd.copy(alpha = 0.55f) else tint.copy(alpha = 0.22f)
    Box(
        modifier = modifier
            .width(36.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(track)
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(percent = 50))
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White.copy(alpha = 0.92f)),
        )
    }
}
