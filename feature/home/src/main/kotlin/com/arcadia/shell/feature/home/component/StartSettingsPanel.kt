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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.motionMillis
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.StartSettingsCategory
import com.arcadia.shell.feature.home.StartSettingsRow
import com.arcadia.shell.feature.home.StartSettingsTrailingIcon
import com.arcadia.shell.feature.home.StartSettingsUiState
import com.arcadia.shell.feature.home.XmbIcon
import com.arcadia.shell.feature.home.vectorDrawableRes

private val ListShape = RoundedCornerShape(22.dp)
private val TabShape = RoundedCornerShape(12.dp)
private val RowFocusShape = RoundedCornerShape(14.dp)

/**
 * Start-button app config: dual floating glass panels (list + category rail).
 *
 * Overlay (not Dialog) so Dual Mode [android.app.Presentation] panes can host it without a
 * nested window. Enter/exit uses scale+fade with a light spring overshoot.
 */
@Composable
fun StartSettingsPanel(
    state: StartSettingsUiState,
    onSelectCategory: (StartSettingsCategory) -> Unit,
    onSelectRow: (Int) -> Unit,
    onActivate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val enterMs = motionMillis(ArcadiaMotion.Slow)
    val enterSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    BackHandler(enabled = state.open, onBack = onDismiss)

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
                    .liquidGlass(
                        shape = ListShape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            ) {
                Text(
                    text = categoryTitle(state.category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.content,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StartSettingsCategory.entries.forEach { category ->
                        CategoryTab(
                            category = category,
                            selected = category == state.category,
                            tint = glass.content,
                            onClick = { onSelectCategory(category) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(1.dp)
                        .background(glass.border.copy(alpha = 0.35f)),
                )
                val categoryFadeIn = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
                val categoryFadeOut = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
                AnimatedContent(
                    targetState = state.category,
                    transitionSpec = {
                        categoryFadeIn togetherWith categoryFadeOut
                    },
                    label = "startSettingsCategory",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { category ->
                    val rows = if (category == state.category) state.rows else emptyList()
                    val listState = rememberLazyListState()
                    LaunchedEffect(state.selectedRowIndex, category, rows.size) {
                        if (category != state.category || rows.isEmpty()) return@LaunchedEffect
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
    val theme = LocalShellTheme.current.colors
    val focusStart = theme.focusStart
    val focusEnd = theme.focusEnd
    val highlightAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "startSettingsFocus",
    )
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
            .clip(RowFocusShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        focusStart.copy(alpha = 0.42f * highlightAlpha),
                        focusEnd.copy(alpha = 0.38f * highlightAlpha),
                    ),
                ),
            )
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

/** Compact header tab: glyph over an accent underline when focused. */
@Composable
private fun CategoryTab(
    category: StartSettingsCategory,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalShellTheme.current.colors
    val highlight by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "categoryTabFocus",
    )
    Column(
        modifier = modifier
            .clip(TabShape)
            .background(theme.focusEnd.copy(alpha = 0.22f * highlight))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        CategoryGlyph(
            category = category,
            tint = if (selected) tint else tint.copy(alpha = 0.5f),
        )
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(theme.focusEnd.copy(alpha = highlight)),
        )
    }
}

@Composable
private fun CategoryGlyph(
    category: StartSettingsCategory,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val icon = category.toXmbIcon()
    val resId = icon.vectorDrawableRes() ?: return
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(24.dp),
    )
}

private fun StartSettingsCategory.toXmbIcon(): XmbIcon = when (this) {
    StartSettingsCategory.General -> XmbIcon.General
    StartSettingsCategory.Display -> XmbIcon.Display
    StartSettingsCategory.Themes -> XmbIcon.Themes
    StartSettingsCategory.Sound -> XmbIcon.Sound
    StartSettingsCategory.Scrape -> XmbIcon.Scrape
    StartSettingsCategory.Social -> XmbIcon.Social
    StartSettingsCategory.Notifications -> XmbIcon.Notifications
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

private fun categoryTitle(category: StartSettingsCategory): String = when (category) {
    StartSettingsCategory.Display -> "Display"
    StartSettingsCategory.Themes -> "Themes"
    StartSettingsCategory.Sound -> "Sound"
    StartSettingsCategory.Scrape -> "Scrape"
    StartSettingsCategory.Social -> "Social"
    StartSettingsCategory.Notifications -> "Notifications"
    StartSettingsCategory.General -> "General"
}
