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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewStartSettings

private val ListShape = RoundedCornerShape(22.dp)
private val RailShape = RoundedCornerShape(percent = 50)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.72f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: wider glass list
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .liquidGlass(
                            shape = ListShape,
                            tone = GlassTone.OverMedia,
                            intensity = GlassIntensity.Strong,
                            shimmer = true,
                        )
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = categoryTitle(state.category),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = glass.content,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            rows.forEachIndexed { index, row ->
                                if (index > 0 && row !is StartSettingsRow.Header) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .height(1.dp)
                                            .background(glass.border.copy(alpha = 0.35f)),
                                    )
                                }
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

                // Right: narrow pill category rail
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .fillMaxHeight(0.92f)
                        .liquidGlass(
                            shape = RailShape,
                            tone = GlassTone.OverMedia,
                            intensity = GlassIntensity.Strong,
                        )
                        .padding(vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StartSettingsCategory.entries.forEach { category ->
                        CategoryRailIcon(
                            category = category,
                            selected = category == state.category,
                            tint = glass.content,
                            onClick = { onSelectCategory(category) },
                        )
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
            .padding(horizontal = 14.dp, vertical = 14.dp),
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
private fun CategoryRailIcon(
    category: StartSettingsCategory,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    val theme = LocalShellTheme.current.colors
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "railIconScale",
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                theme.focusStart.copy(alpha = 0.35f),
                                theme.focusEnd.copy(alpha = 0.28f),
                            ),
                        ),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CategoryGlyph(
            category = category,
            tint = if (selected) tint else tint.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun CategoryGlyph(
    category: StartSettingsCategory,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round)
        when (category) {
            StartSettingsCategory.Display -> {
                // Film strip
                val left = size.width * 0.18f
                val top = size.height * 0.12f
                val w = size.width * 0.64f
                val h = size.height * 0.76f
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                val perforations = 4
                for (i in 0 until perforations) {
                    val y = top + h * ((i + 0.5f) / perforations)
                    drawCircle(
                        color = tint,
                        radius = size.minDimension * 0.04f,
                        center = Offset(left + w * 0.14f, y),
                        style = stroke,
                    )
                    drawCircle(
                        color = tint,
                        radius = size.minDimension * 0.04f,
                        center = Offset(left + w * 0.86f, y),
                        style = stroke,
                    )
                }
            }
            StartSettingsCategory.Themes -> {
                // Palette swatches
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.16f,
                    center = Offset(size.width * 0.34f, size.height * 0.42f),
                    style = stroke,
                )
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.16f,
                    center = Offset(size.width * 0.58f, size.height * 0.42f),
                    style = stroke,
                )
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.12f,
                    center = Offset(size.width * 0.46f, size.height * 0.64f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.22f, size.height * 0.78f),
                    end = Offset(size.width * 0.78f, size.height * 0.78f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
            StartSettingsCategory.Sound -> {
                // Eighth note
                val stemX = size.width * 0.58f
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.16f,
                    center = Offset(size.width * 0.38f, size.height * 0.68f),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(stemX, size.height * 0.22f),
                    end = Offset(stemX, size.height * 0.68f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                val flag = Path().apply {
                    moveTo(stemX, size.height * 0.22f)
                    quadraticTo(
                        size.width * 0.88f,
                        size.height * 0.32f,
                        stemX + size.width * 0.02f,
                        size.height * 0.48f,
                    )
                }
                drawPath(flag, color = tint, style = stroke)
            }
            StartSettingsCategory.Scrape -> {
                // Folder
                val path = Path().apply {
                    moveTo(size.width * 0.14f, size.height * 0.36f)
                    lineTo(size.width * 0.14f, size.height * 0.28f)
                    lineTo(size.width * 0.42f, size.height * 0.28f)
                    lineTo(size.width * 0.50f, size.height * 0.36f)
                    lineTo(size.width * 0.86f, size.height * 0.36f)
                    lineTo(size.width * 0.86f, size.height * 0.78f)
                    lineTo(size.width * 0.14f, size.height * 0.78f)
                    close()
                }
                drawPath(path, color = tint, style = stroke)
            }
            StartSettingsCategory.Social -> {
                // Plug
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(size.width * 0.28f, size.height * 0.38f),
                    size = Size(size.width * 0.44f, size.height * 0.36f),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.40f, size.height * 0.20f),
                    end = Offset(size.width * 0.40f, size.height * 0.38f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.60f, size.height * 0.20f),
                    end = Offset(size.width * 0.60f, size.height * 0.38f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.74f),
                    end = Offset(size.width * 0.50f, size.height * 0.88f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
            }
            StartSettingsCategory.Notifications -> {
                // Bell
                val dome = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    quadraticTo(
                        size.width * 0.28f,
                        size.height * 0.22f,
                        size.width * 0.50f,
                        size.height * 0.20f,
                    )
                    quadraticTo(
                        size.width * 0.72f,
                        size.height * 0.22f,
                        size.width * 0.72f,
                        size.height * 0.52f,
                    )
                    lineTo(size.width * 0.78f, size.height * 0.68f)
                    lineTo(size.width * 0.22f, size.height * 0.68f)
                    close()
                }
                drawPath(dome, color = tint, style = stroke)
                drawLine(
                    color = tint,
                    start = Offset(size.width * 0.50f, size.height * 0.12f),
                    end = Offset(size.width * 0.50f, size.height * 0.20f),
                    strokeWidth = stroke.width,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = tint,
                    radius = size.minDimension * 0.05f,
                    center = Offset(size.width * 0.50f, size.height * 0.12f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.38f, size.height * 0.64f),
                    size = Size(size.width * 0.24f, size.height * 0.22f),
                    style = stroke,
                )
            }
            StartSettingsCategory.General -> {
                // Gear (simple)
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = size.minDimension * 0.28f
                drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = stroke)
                drawCircle(
                    color = tint,
                    radius = r * 0.38f,
                    center = Offset(cx, cy),
                    style = stroke,
                )
                for (i in 0 until 6) {
                    val angle = Math.toRadians((i * 60).toDouble())
                    val inner = r * 1.05f
                    val outer = r * 1.45f
                    drawLine(
                        color = tint,
                        start = Offset(
                            cx + (inner * kotlin.math.cos(angle)).toFloat(),
                            cy + (inner * kotlin.math.sin(angle)).toFloat(),
                        ),
                        end = Offset(
                            cx + (outer * kotlin.math.cos(angle)).toFloat(),
                            cy + (outer * kotlin.math.sin(angle)).toFloat(),
                        ),
                        strokeWidth = stroke.width * 1.2f,
                        cap = StrokeCap.Round,
                    )
                }
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

private fun categoryTitle(category: StartSettingsCategory): String = when (category) {
    StartSettingsCategory.Display -> "Display"
    StartSettingsCategory.Themes -> "Themes"
    StartSettingsCategory.Sound -> "Sound"
    StartSettingsCategory.Scrape -> "Scrape"
    StartSettingsCategory.Social -> "Social"
    StartSettingsCategory.Notifications -> "Notifications"
    StartSettingsCategory.General -> "General"
}

@XoraPreview
@Composable
private fun StartSettingsPanelPreview() {
    XoraPreviewTheme {
        StartSettingsPanel(
            state = previewStartSettings(),
            onSelectCategory = {},
            onSelectRow = {},
            onActivate = {},
            onDismiss = {},
        )
    }
}
