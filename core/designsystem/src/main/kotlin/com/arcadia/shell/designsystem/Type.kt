package com.arcadia.shell.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times

/** User-facing text size multiplier from Display settings (default ~0.85). */
val LocalXoraTextScale = staticCompositionLocalOf { 0.85f }

@Composable
@ReadOnlyComposable
fun xoraTextScale(): Float = LocalXoraTextScale.current

/**
 * XOrA type ramp.
 *
 * - [XoraFonts.Title] — XOIREQE: titles, menu names, primary labels
 * - [XoraFonts.Secondary] — FOT-NewRodin Pro EB: bios, subtitles, info, secondary copy
 * - [XoraFonts.XmbLabel] — M PLUS Rounded 1c Light: XMB hover labels (New Rodin analog;
 *   the bundled FOT-NewRodin cut is ExtraBold-only and cannot thin)
 */
object XoraFonts {
    val Title: FontFamily = FontFamily(
        Font(R.font.xoireqe, FontWeight.Normal),
        Font(R.font.xoireqe, FontWeight.Medium),
        Font(R.font.xoireqe, FontWeight.SemiBold),
        Font(R.font.xoireqe, FontWeight.Bold),
        Font(R.font.xoireqe, FontWeight.ExtraBold),
    )

    val Secondary: FontFamily = FontFamily(
        Font(R.font.fot_newrodin_pro_eb_extract, FontWeight.Normal),
        Font(R.font.fot_newrodin_pro_eb_extract, FontWeight.Medium),
        Font(R.font.fot_newrodin_pro_eb_extract, FontWeight.SemiBold),
        Font(R.font.fot_newrodin_pro_eb_extract, FontWeight.Bold),
        Font(R.font.fot_newrodin_pro_eb_extract, FontWeight.ExtraBold),
    )

    /** Light rounded gothic — Figma Make hover / PS3 New Rodin Regular stand-in. */
    val XmbLabel: FontFamily = FontFamily(
        Font(R.font.mplus_rounded_1c_light, FontWeight.Light),
        Font(R.font.mplus_rounded_1c_light, FontWeight.Normal),
        Font(R.font.mplus_rounded_1c_light, FontWeight.Medium),
    )

    /** Slight tracking for XOIREQE primary / menu text. */
    val TitleLetterSpacing: TextUnit = 0.04.em
}

private fun titleStyle(
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit = XoraFonts.TitleLetterSpacing,
) = TextStyle(
    fontFamily = XoraFonts.Title,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)

private fun secondaryStyle(
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = XoraFonts.Secondary,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)

fun scaledArcadiaTypography(scale: Float): Typography {
    val s = scale.coerceIn(0.75f, 1.3f)
    fun TextStyle.scaled() = copy(
        fontSize = fontSize * s,
        lineHeight = lineHeight * s,
        letterSpacing = letterSpacing * s,
    )
    val base = ArcadiaTypography
    return Typography(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}

internal val ArcadiaTypography = Typography(
    displayLarge = titleStyle(FontWeight.Bold, 44.sp, 48.sp),
    displayMedium = titleStyle(FontWeight.Bold, 36.sp, 40.sp),
    displaySmall = titleStyle(FontWeight.Bold, 30.sp, 36.sp),
    headlineLarge = titleStyle(FontWeight.SemiBold, 28.sp, 34.sp),
    headlineMedium = titleStyle(FontWeight.SemiBold, 26.sp, 32.sp),
    headlineSmall = titleStyle(FontWeight.SemiBold, 22.sp, 28.sp),
    titleLarge = titleStyle(FontWeight.SemiBold, 20.sp, 26.sp),
    titleMedium = titleStyle(FontWeight.Medium, 17.sp, 22.sp),
    titleSmall = titleStyle(FontWeight.Medium, 15.sp, 20.sp),
    bodyLarge = secondaryStyle(FontWeight.Normal, 16.sp, 22.sp),
    bodyMedium = secondaryStyle(FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = secondaryStyle(FontWeight.Normal, 12.sp, 16.sp),
    labelLarge = secondaryStyle(FontWeight.Medium, 13.sp, 18.sp, 0.2.sp),
    labelMedium = secondaryStyle(FontWeight.Medium, 12.sp, 16.sp, 0.3.sp),
    labelSmall = secondaryStyle(FontWeight.Medium, 11.sp, 14.sp, 0.4.sp),
)

/** Title / primary text style helper for screens that build custom [TextStyle]s. */
@Composable
@ReadOnlyComposable
fun xoraTitleTextStyle(
    weight: FontWeight = FontWeight.SemiBold,
    fontSize: TextUnit = 22.sp,
    lineHeight: TextUnit = 28.sp,
): TextStyle = TextStyle(
    fontFamily = XoraFonts.Title,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = XoraFonts.TitleLetterSpacing,
)

/** Bio / info / secondary text style helper. */
@Composable
@ReadOnlyComposable
fun xoraSecondaryTextStyle(
    weight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = 13.sp,
    lineHeight: TextUnit = 18.sp,
): TextStyle = TextStyle(
    fontFamily = XoraFonts.Secondary,
    fontWeight = weight,
    fontSize = fontSize,
    lineHeight = lineHeight,
)
