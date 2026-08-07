package com.arcadia.shell.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.max

/** The Figma artboard the tray is authored against; every measurement below is in its units. */
internal const val VITA_DESIGN_WIDTH = 1920f
internal const val VITA_DESIGN_HEIGHT = 1080f

internal val VitaSkyTop = Color(0xFF2ACBFD)
internal val VitaSkyBottom = Color(0xFFDEF9FF)

private val WaveGradientStart = Color(0xFF2ACBFD)
private val WaveGradientEnd = Color(0xFFB6FCFD)
private val WaveOutline = Color.White.copy(alpha = 0.15f)

private const val WAVE_LAYER_LEFT = -933.49f
private const val WAVE_LAYER_TOP = -373.2f
private const val WAVE_FILL_ALPHA = 0.75f

/**
 * Sky plate behind the bubble field: the vertical gradient plus the two overlapping wave blobs
 * from the design. The blobs are composited with [BlendMode.Lighten] as authored, so they only
 * ever brighten the sky instead of muddying the pale lower half.
 */
@Composable
fun VitaTraySky(modifier: Modifier = Modifier) {
    val waves = remember { VitaWaveShapes() }
    Canvas(modifier = modifier) {
        drawRect(brush = Brush.verticalGradient(listOf(VitaSkyTop, VitaSkyBottom)))

        val scale = max(size.width / VITA_DESIGN_WIDTH, size.height / VITA_DESIGN_HEIGHT)
        val originX = ((size.width - (VITA_DESIGN_WIDTH * scale)) / 2f) + (WAVE_LAYER_LEFT * scale)
        val originY = ((size.height - (VITA_DESIGN_HEIGHT * scale)) / 2f) + (WAVE_LAYER_TOP * scale)

        withTransform({
            translate(originX, originY)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            drawWaveGroup(waves.lowerFills, waves.lowerOutline, waves.lowerGradient)
            drawWaveGroup(waves.upperFills, waves.upperOutline, waves.upperGradient)
        }
    }
}

private fun DrawScope.drawWaveGroup(fills: List<Path>, outline: Path, brush: Brush) {
    fills.forEach { path ->
        drawPath(path = path, brush = brush, alpha = WAVE_FILL_ALPHA, blendMode = BlendMode.Lighten)
    }
    drawPath(path = outline, color = WaveOutline, style = Stroke(width = 1f))
}

/** Parsed once — the blob outlines are static and re-parsing them per frame is wasteful. */
@Immutable
private class VitaWaveShapes {
    val lowerFills: List<Path> = listOf(LOWER_WAVE_A, LOWER_WAVE_B).map(::parsePath)
    val lowerOutline: Path = parsePath(LOWER_WAVE_OUTLINE)
    val upperFills: List<Path> = listOf(UPPER_WAVE_A, UPPER_WAVE_B).map(::parsePath)
    val upperOutline: Path = parsePath(UPPER_WAVE_OUTLINE)

    val lowerGradient: Brush = waveGradient(Offset(407.108f, 979.685f), Offset(2845.7f, 1426.6f))
    val upperGradient: Brush = waveGradient(Offset(291.023f, 916.659f), Offset(2799.87f, 1610.97f))
}

private fun parsePath(data: String): Path = PathParser().parsePathString(data).toPath()

private fun waveGradient(start: Offset, end: Offset): Brush = Brush.linearGradient(
    colorStops = arrayOf(0f to WaveGradientStart, 0.677885f to WaveGradientEnd),
    start = start,
    end = end,
)

private const val LOWER_WAVE_A =
    "M1589.82 696.204C1690.32 1293.48 3117.95 1141.82 2845.22 1524.56C2457.03 2069.35 277.329 " +
        "2038.28 226.201 1435.19C175.072 832.094 -68.3813 198.088 249.098 215.983C399.011 " +
        "224.434 1481.13 50.2749 1589.82 696.204Z"

private const val LOWER_WAVE_B =
    "M1603.37 706.4C1703.88 1303.67 3131.5 1152.02 2858.77 1534.76C2470.58 2079.54 290.883 " +
        "2048.48 239.754 1445.38C188.625 842.289 -54.8278 208.283 262.651 226.179C412.565 " +
        "234.629 1494.68 60.4704 1603.37 706.4Z"

private const val LOWER_WAVE_OUTLINE =
    "M154.576 238.09C178.318 221.206 209.359 213.243 249.126 215.485C267.829 216.539 301.097 " +
        "214.744 345.26 212.052C389.406 209.36 444.406 205.773 506.526 203.25C630.767 198.203 " +
        "783.514 197.41 934.981 216.543C1086.44 235.677 1236.66 274.74 1355.83 349.433C1372.75 " +
        "360.043 1389.05 371.373 1404.64 383.466C1505.8 457.235 1579.43 561.064 1603.87 " +
        "706.317C1621.83 813.075 1682.26 895.893 1767.87 962.119C1798.25 984.351 1831.52 " +
        "1004.66 1866.96 1023.34C2010.61 1099.07 2189.91 1148.05 2357.6 1190.35C2525.25 " +
        "1232.63 2681.36 1268.24 2778.48 1317.21C2800.02 1328.07 2818.68 1339.6 2833.93 " +
        "1352.02C2860.17 1370.33 2878.74 1390.58 2887.72 1413.59C2900.94 1447.46 2893.32 " +
        "1487.13 2859.18 1535.05C2762.02 1671.4 2552.85 1771.64 2293.09 1835.92C2033.31 " +
        "1900.21 1722.81 1928.55 1422.85 1921.02C1122.9 1913.5 833.455 1870.09 615.785 " +
        "1790.86C520.08 1756.02 438.23 1714.25 375.46 1665.54C361.872 1655.62 349.091 1645.41 " +
        "337.163 1634.9C271.843 1577.34 232.107 1510.78 225.702 1435.23C212.922 1284.48 " +
        "188.123 1131.79 163.07 987.826C138.019 843.869 112.711 708.617 98.9243 592.774C85.1395 " +
        "476.949 82.8584 380.431 103.917 313.973C114.451 280.731 130.835 254.972 154.576 238.09Z"

private const val UPPER_WAVE_A =
    "M1792.49 821.702C1843.49 1450.42 3247.68 1128.14 2927.49 1500.7C2471.73 2030.99 59.1275 " +
        "2003.29 60.3337 1372.97C61.5399 742.654 -135.758 62.9611 194.928 110.093C351.078 " +
        "132.349 1737.33 141.764 1792.49 821.702Z"

private const val UPPER_WAVE_B =
    "M1409.49 821.702C1459.61 1447.07 3127.7 1343.06 2807.06 1713.95C2350.68 2241.87 72.8392 " +
        "2016.8 74.9169 1389.8C76.9947 762.792 -119.316 86.8535 211.226 133.437C367.308 155.433 " +
        "1355.28 145.39 1409.49 821.702Z"

private const val UPPER_WAVE_OUTLINE =
    "M93.9993 124.541C120.376 109.164 153.583 103.696 194.999 109.599C272.968 120.711 658.705 " +
        "128.649 1031.7 218.781C1218.21 263.851 1401.61 329.486 1541.84 426.383C1682.08 523.286 " +
        "1779.18 651.484 1792.98 821.662C1805.72 978.604 1902.87 1076.22 2036.59 " +
        "1140.19C2170.32 1204.18 2340.55 1234.47 2499.25 1256.77C2578.59 1267.91 2655.06 " +
        "1277.06 2722.6 1287.42C2790.14 1297.78 2848.81 1309.36 2892.57 1325.38C2936.29 " +
        "1341.39 2965.3 1361.89 2973.22 1390.21C2981.14 1418.54 2967.91 1454.44 2927.86 " +
        "1501.03C2902.8 1530.19 2871.83 1557.66 2835.68 1583.44C2837.93 1587.09 2839.9 1590.8 " +
        "2841.58 1594.57C2856.72 1628.7 2847.58 1667.85 2807.44 1714.28C2693.22 1846.4 2465.13 " +
        "1931.34 2187.41 1974.68C1909.67 2018.03 1582.17 2019.8 1269.01 1985.47C955.857 " +
        "1951.14 657.002 1880.71 436.55 1779.65C326.325 1729.13 235.675 1670.93 172.639 " +
        "1605.74C145.966 1578.16 124.235 1549.32 108.057 1519.27C76.6791 1474.76 59.7324 1426 " +
        "59.8339 1372.97C60.1354 1215.42 48.0324 1054.77 34.8725 903.147C21.7135 751.535 " +
        "7.49561 608.934 3.57604 487.505C-0.342917 366.095 6.0267 265.755 34.0975 " +
        "198.703C48.1386 165.163 67.6244 139.917 93.9993 124.541Z"
