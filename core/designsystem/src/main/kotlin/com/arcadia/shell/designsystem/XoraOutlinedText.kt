package com.arcadia.shell.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times

/**
 * White fill + black outline — the XOrA display treatment for titles and secondary copy
 * over wallpapers / XMB.
 */
@Composable
fun XoraOutlinedText(
    text: String,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily = XoraFonts.Title,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = 16.sp,
    fillColor: Color = Color.White,
    outlineColor: Color = Color.Black,
    outlineWidth: Dp = XoraOutlineWidth.forSize(fontSize),
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
) {
    val scale = xoraTextScale()
    val scaledSize = fontSize * scale
    val scaledOutline = outlineWidth * scale
    val outlinePx = with(LocalDensity.current) { scaledOutline.toPx() }
    val base = TextStyle(
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize = scaledSize,
        letterSpacing = letterSpacing,
    )
    // [modifier] sizes this Box, but the two Text layers only ever measure to their content, so
    // textAlign alone cannot move them — a centred label in a fixed-width box would sit hard
    // against its left edge. Align the layers in the Box instead; they stay exactly on top of
    // each other because both measure identically.
    val alignment = when (textAlign) {
        TextAlign.Center -> Alignment.TopCenter
        TextAlign.End, TextAlign.Right -> Alignment.TopEnd
        else -> Alignment.TopStart
    }
    Box(modifier = modifier, contentAlignment = alignment) {
        Text(
            text = text,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap,
            textAlign = textAlign,
            style = base.copy(
                color = outlineColor,
                drawStyle = Stroke(
                    width = outlinePx,
                    join = StrokeJoin.Round,
                    miter = 4f,
                ),
            ),
        )
        Text(
            text = text,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap,
            textAlign = textAlign,
            style = base.copy(
                color = fillColor,
                drawStyle = Fill,
            ),
        )
    }
}

/** Title / primary menu text (XOIREQE). */
@Composable
fun XoraTitleText(
    text: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.SemiBold,
    fontSize: TextUnit = 22.sp,
    fillColor: Color = Color.White,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    XoraOutlinedText(
        text = text,
        modifier = modifier,
        fontFamily = XoraFonts.Title,
        fontWeight = fontWeight,
        fontSize = fontSize,
        fillColor = fillColor,
        letterSpacing = XoraFonts.TitleLetterSpacing,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** Bio / info / secondary copy (FOT-NewRodin Pro DB). */
@Composable
fun XoraSecondaryText(
    text: String,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = 13.sp,
    fillColor: Color = Color.White,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    XoraOutlinedText(
        text = text,
        modifier = modifier,
        fontFamily = XoraFonts.Secondary,
        fontWeight = fontWeight,
        fontSize = fontSize,
        fillColor = fillColor,
        outlineWidth = XoraOutlineWidth.forSize(fontSize),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}

object XoraOutlineWidth {
    fun forSize(fontSize: TextUnit): Dp {
        val sp = fontSize.value
        return when {
            sp >= 26f -> 3.dp
            sp >= 18f -> 2.5.dp
            sp >= 14f -> 2.dp
            else -> 1.5.dp
        }
    }
}
