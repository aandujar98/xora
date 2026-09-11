package com.arcadia.shell.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * The one settings-window frame: a dark slate plate with a lit edge, a caps title, and a hairline
 * rule under it. Every modal settings surface uses this so they stop each inventing their own
 * panel.
 *
 * The plate is opaque on purpose. These windows sit over a blurred backdrop, and a translucent
 * fill on top of a blur turns the title mush at the exact moment the player is reading it.
 */
object XoraSettingsPanelDefaults {
    val Shape: Shape = RoundedCornerShape(26.dp)
    val TitleSize = 20.sp
    val ContentPadding = 18.dp

    /** Near-black through slate — lighter at the bottom, as in the design. */
    val Fill: Brush = Brush.verticalGradient(
        0f to Color(0xFF0A0C12),
        0.45f to Color(0xFF12161F),
        0.80f to Color(0xFF1B2430),
        1f to Color(0xFF28323E),
    )

    /** Lit rim: brightest along the top edge, falling off down the sides. */
    val Edge: Brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.85f),
            Color.White.copy(alpha = 0.38f),
            Color.White.copy(alpha = 0.22f),
        ),
        start = Offset.Zero,
        end = Offset(0f, Float.POSITIVE_INFINITY),
    )

    val EdgeWidth = 1.5.dp
    val RuleColor = Color.White.copy(alpha = 0.62f)
}

/** Plate surface without the header, for callers that supply their own top row. */
fun Modifier.xoraSettingsPanelSurface(
    shape: Shape = XoraSettingsPanelDefaults.Shape,
    contentPadding: Dp = XoraSettingsPanelDefaults.ContentPadding,
): Modifier = this
    .clip(shape)
    .background(XoraSettingsPanelDefaults.Fill, shape)
    .border(XoraSettingsPanelDefaults.EdgeWidth, XoraSettingsPanelDefaults.Edge, shape)
    .padding(horizontal = 20.dp, vertical = contentPadding)

/**
 * Caps title over a hairline rule — the header every settings window shares.
 *
 * Split from [XoraSettingsPanel] so a window with chrome on the title row (the pin picker's
 * search field, say) can lay out its own row and still get the same type and rule.
 */
@Composable
fun XoraSettingsPanelHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        XoraTitleText(
            text = title.uppercase(Locale.US),
            fontSize = XoraSettingsPanelDefaults.TitleSize,
            fontWeight = FontWeight.SemiBold,
        )
        XoraSettingsPanelRule(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun XoraSettingsPanelRule(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(XoraSettingsPanelDefaults.RuleColor),
    )
}

/**
 * Titled settings plate. [content] is laid out under the rule.
 */
@Composable
fun XoraSettingsPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.xoraSettingsPanelSurface()) {
        XoraSettingsPanelHeader(title)
        content()
    }
}

/**
 * The shell's selection cursor: a themed gradient bar behind the focused row.
 *
 * Lifted out of Start settings so every menu highlights the same way — the cursor is how the
 * player tracks focus across windows, and a different one per window reads as a different app.
 */
@Composable
fun Modifier.xoraFocusHighlight(
    selected: Boolean,
    shape: Shape = RoundedCornerShape(12.dp),
    strength: Float = 1f,
): Modifier {
    val theme = LocalShellTheme.current.colors
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "xoraFocusHighlight",
    )
    if (alpha <= 0.001f) return this.clip(shape)
    return this
        .clip(shape)
        .background(
            Brush.horizontalGradient(
                listOf(
                    theme.focusStart.copy(alpha = 0.42f * alpha * strength),
                    theme.focusEnd.copy(alpha = 0.38f * alpha * strength),
                ),
            ),
            shape,
        )
}
