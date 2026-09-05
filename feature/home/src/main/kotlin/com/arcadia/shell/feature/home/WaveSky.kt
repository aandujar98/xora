package com.arcadia.shell.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.max
import kotlin.math.min

/** The Figma artboard the shell is authored against; every design measurement is in its units. */
internal const val XORA_DESIGN_WIDTH = 1920f
internal const val XORA_DESIGN_HEIGHT = 1080f

/**
 * Contain-fits the 1920×1080 artboard inside the host. Chrome that uses this stays on the XMB
 * plate instead of drawing into the letterbox bars around a non-16:9 panel.
 */
fun Modifier.xoraDesignCanvas(): Modifier = layout { measurable, constraints ->
    val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
    val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
    val unit = min(maxW / XORA_DESIGN_WIDTH, maxH / XORA_DESIGN_HEIGHT)
    val width = (XORA_DESIGN_WIDTH * unit).toInt().coerceIn(1, constraints.maxWidth)
    val height = (XORA_DESIGN_HEIGHT * unit).toInt().coerceIn(1, constraints.maxHeight)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(
            x = (constraints.maxWidth - placeable.width) / 2,
            y = (constraints.maxHeight - placeable.height) / 2,
        )
    }
}

private val WaveGradientStart = Color(0xFF2ACBFD)
private val WaveGradientEnd = Color(0xFFB6FCFD)
private val WaveOutline = Color.White.copy(alpha = 0.15f)
private const val WAVE_FILL_ALPHA = 0.75f

/**
 * Sky plate used across the shell's Vita-styled screens: a vertical gradient with overlapping
 * wave blobs on top. The blobs composite with [BlendMode.Lighten] as authored, so they only ever
 * brighten the sky instead of muddying its pale end.
 */
@Composable
fun WaveSky(
    topColor: Color,
    bottomColor: Color,
    field: WaveField,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawRect(brush = Brush.verticalGradient(listOf(topColor, bottomColor)))

        val scale = max(size.width / XORA_DESIGN_WIDTH, size.height / XORA_DESIGN_HEIGHT)
        val originX = ((size.width - (XORA_DESIGN_WIDTH * scale)) / 2f) + (field.left * scale)
        val originY = ((size.height - (XORA_DESIGN_HEIGHT * scale)) / 2f) + (field.top * scale)

        withTransform({
            translate(originX, originY)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            field.layers.forEach { layer ->
                layer.fills.forEach { path ->
                    drawPath(
                        path = path,
                        brush = layer.brush,
                        alpha = WAVE_FILL_ALPHA,
                        blendMode = BlendMode.Lighten,
                    )
                }
                layer.outlines.forEach { path ->
                    drawPath(path = path, color = WaveOutline, style = Stroke(width = 1f))
                }
            }
        }
    }
}

/**
 * A parsed wave group positioned at [left]/[top] within the design frame. Paths are parsed once
 * per field — re-parsing them every frame would be wasteful for shapes that never change.
 */
@Immutable
class WaveField internal constructor(
    internal val layers: List<WaveLayer>,
    internal val left: Float,
    internal val top: Float,
)

internal class WaveLayer(
    fillData: List<String>,
    outlineData: List<String>,
    gradientStart: Offset,
    gradientEnd: Offset,
) {
    val fills: List<Path> = fillData.map(::parseWavePath)
    val outlines: List<Path> = outlineData.map(::parseWavePath)
    val brush: Brush = Brush.linearGradient(
        colorStops = arrayOf(0f to WaveGradientStart, 0.677885f to WaveGradientEnd),
        start = gradientStart,
        end = gradientEnd,
    )
}

private fun parseWavePath(data: String): Path = PathParser().parsePathString(data).toPath()

/** `BG WAVE` from the Vita shortcut frame. */
val VitaWaveField: WaveField by lazy {
    WaveField(
        layers = listOf(
            WaveLayer(
                fillData = listOf(VITA_FILL_0, VITA_FILL_1),
                outlineData = listOf(VITA_OUTLINE_0),
                gradientStart = Offset(407.108f, 979.685f),
                gradientEnd = Offset(2845.7f, 1426.6f),
            ),
            WaveLayer(
                fillData = listOf(VITA_FILL_2, VITA_FILL_3),
                outlineData = listOf(VITA_OUTLINE_1),
                gradientStart = Offset(291.023f, 916.659f),
                gradientEnd = Offset(2799.87f, 1610.97f),
            ),
        ),
        left = -933.49f,
        top = -373.2f,
    )
}

/** `WAVE H` from the platform picker frame — larger, sweeping further across the panel. */
val PlatformWaveField: WaveField by lazy {
    WaveField(
        layers = listOf(
            WaveLayer(
                fillData = listOf(PLATFORM_FILL_0),
                outlineData = listOf(PLATFORM_OUTLINE_0),
                gradientStart = Offset(481.742f, 1901.83f),
                gradientEnd = Offset(3054.28f, 1503.78f),
            ),
            WaveLayer(
                fillData = listOf(PLATFORM_FILL_1),
                outlineData = listOf(PLATFORM_OUTLINE_1),
                gradientStart = Offset(595.974f, 1868.25f),
                gradientEnd = Offset(3185.33f, 1463.6f),
            ),
        ),
        left = -994.2f,
        top = -554f,
    )
}

private const val VITA_FILL_0 =
    "M1589.82 696.204C1690.32 1293.48 3117.95 1141.82 2845.22 1524.56C2457.03 2069.35 " +
        "277.329 2038.28 226.201 1435.19C175.072 832.094 -68.3813 198.088 249.098 " +
        "215.983C399.011 224.434 1481.13 50.2749 1589.82 696.204Z"

private const val VITA_FILL_1 =
    "M1603.37 706.4C1703.88 1303.67 3131.5 1152.02 2858.77 1534.76C2470.58 2079.54 " +
        "290.883 2048.48 239.754 1445.38C188.625 842.289 -54.8278 208.283 262.651 " +
        "226.179C412.565 234.629 1494.68 60.4704 1603.37 706.4Z"

private const val VITA_FILL_2 =
    "M1792.49 821.702C1843.49 1450.42 3247.68 1128.14 2927.49 1500.7C2471.73 2030.99 " +
        "59.1275 2003.29 60.3337 1372.97C61.5399 742.654 -135.758 62.9611 194.928 " +
        "110.093C351.078 132.349 1737.33 141.764 1792.49 821.702Z"

private const val VITA_FILL_3 =
    "M1409.49 821.702C1459.61 1447.07 3127.7 1343.06 2807.06 1713.95C2350.68 2241.87 " +
        "72.8392 2016.8 74.9169 1389.8C76.9947 762.792 -119.316 86.8535 211.226 " +
        "133.437C367.308 155.433 1355.28 145.39 1409.49 821.702Z"

private const val VITA_OUTLINE_0 =
    "M154.576 238.09C178.318 221.206 209.359 213.243 249.126 215.485C267.829 216.539 " +
        "301.097 214.744 345.26 212.052C389.406 209.36 444.406 205.773 506.526 " +
        "203.25C630.767 198.203 783.514 197.41 934.981 216.543C1086.44 235.677 1236.66 " +
        "274.74 1355.83 349.433C1372.75 360.043 1389.05 371.373 1404.64 383.466C1505.8 " +
        "457.235 1579.43 561.064 1603.87 706.317C1621.83 813.075 1682.26 895.893 1767.87 " +
        "962.119C1798.25 984.351 1831.52 1004.66 1866.96 1023.34C2010.61 1099.07 " +
        "2189.91 1148.05 2357.6 1190.35C2525.25 1232.63 2681.36 1268.24 2778.48 " +
        "1317.21C2800.02 1328.07 2818.68 1339.6 2833.93 1352.02C2860.17 1370.33 2878.74 " +
        "1390.58 2887.72 1413.59C2900.94 1447.46 2893.32 1487.13 2859.18 1535.05C2762.02 " +
        "1671.4 2552.85 1771.64 2293.09 1835.92C2033.31 1900.21 1722.81 1928.55 1422.85 " +
        "1921.02C1122.9 1913.5 833.455 1870.09 615.785 1790.86C520.08 1756.02 438.23 " +
        "1714.25 375.46 1665.54C361.872 1655.62 349.091 1645.41 337.163 1634.9C271.843 " +
        "1577.34 232.107 1510.78 225.702 1435.23C212.922 1284.48 188.123 1131.79 163.07 " +
        "987.826C138.019 843.869 112.711 708.617 98.9243 592.774C85.1395 476.949 82.8584 " +
        "380.431 103.917 313.973C114.451 280.731 130.835 254.972 154.576 238.09Z"

private const val VITA_OUTLINE_1 =
    "M93.9993 124.541C120.376 109.164 153.583 103.696 194.999 109.599C272.968 120.711 " +
        "658.705 128.649 1031.7 218.781C1218.21 263.851 1401.61 329.486 1541.84 " +
        "426.383C1682.08 523.286 1779.18 651.484 1792.98 821.662C1805.72 978.604 1902.87 " +
        "1076.22 2036.59 1140.19C2170.32 1204.18 2340.55 1234.47 2499.25 1256.77C2578.59 " +
        "1267.91 2655.06 1277.06 2722.6 1287.42C2790.14 1297.78 2848.81 1309.36 2892.57 " +
        "1325.38C2936.29 1341.39 2965.3 1361.89 2973.22 1390.21C2981.14 1418.54 2967.91 " +
        "1454.44 2927.86 1501.03C2902.8 1530.19 2871.83 1557.66 2835.68 1583.44C2837.93 " +
        "1587.09 2839.9 1590.8 2841.58 1594.57C2856.72 1628.7 2847.58 1667.85 2807.44 " +
        "1714.28C2693.22 1846.4 2465.13 1931.34 2187.41 1974.68C1909.67 2018.03 1582.17 " +
        "2019.8 1269.01 1985.47C955.857 1951.14 657.002 1880.71 436.55 1779.65C326.325 " +
        "1729.13 235.675 1670.93 172.639 1605.74C145.966 1578.16 124.235 1549.32 108.057 " +
        "1519.27C76.6791 1474.76 59.7324 1426 59.8339 1372.97C60.1354 1215.42 48.0324 " +
        "1054.77 34.8725 903.147C21.7135 751.535 7.49561 608.934 3.57604 " +
        "487.505C-0.342917 366.095 6.0267 265.755 34.0975 198.703C48.1386 165.163 " +
        "67.6244 139.917 93.9993 124.541Z"

private const val PLATFORM_FILL_0 =
    "M1534.7 1311.97C2400.1 1633.68 2938.2 982.966 3164.2 1659.47C3385.76 2322.67 " +
        "1520.63 2857.59 1262.7 2282.47C1004.76 1707.34 -258.168 1298.95 62.6718 " +
        "1206.02C214.173 1162.13 879.697 1068.47 1534.7 1311.97Z"

private const val PLATFORM_FILL_1 =
    "M1710.2 1178.42C2462.2 1628.7 2748.27 971.057 3225.2 1628.7C3640.74 2201.69 " +
        "1644.64 2837.14 1383.52 2254.93C1122.41 1672.72 -138.337 1272.63 196.935 " +
        "1297.47C474.697 1318.04 1122.83 826.723 1710.2 1178.42Z"

private const val PLATFORM_OUTLINE_0 =
    "M20.0902 1226.75C28.4891 1218.42 42.4494 1211.35 62.5322 1205.53C92.8355 " +
        "1196.76 143.672 1185.99 210.851 1176.33C331.95 1132.97 515.228 1072.08 708.678 " +
        "1048.22C961.974 1016.99 1232.93 1049.21 1404.8 1267.46C1448.1 1280.74 1491.51 " +
        "1295.38 1534.87 1311.5C1967.43 1472.3 2318.17 1390.09 2589.15 1352.18C2724.62 " +
        "1333.23 2840.24 1325.34 2936.13 1364.55C3032.06 1403.78 3108.15 1490.1 3164.67 " +
        "1659.31C3174.8 1689.61 3180.57 1719.65 3182.34 1749.32C3224.26 1755.69 3263.37 " +
        "1762.99 3299.09 1771.51C3380.4 1790.92 3444.18 1816.69 3483.25 1852.36C3502.8 " +
        "1870.2 3516.18 1890.53 3522.46 1913.78C3528.75 1937.03 3527.94 1963.17 3519.18 " +
        "1992.61C3469.35 2159.96 3330.32 2323.05 3145.89 2464.59C2961.45 2606.14 " +
        "2731.54 2726.18 2499.86 2807.42C2268.19 2888.66 2034.72 2931.12 1843.15 " +
        "2917.46C1651.58 2903.81 1501.79 2834.03 1437.74 2690.67C1373.84 2547.65 " +
        "1237.46 2390.12 1073.77 2232.78C910.097 2075.46 719.176 1918.38 546.245 " +
        "1776.26C373.329 1634.15 218.377 1506.98 126.691 1409.5C103.768 1385.12 84.7892 " +
        "1362.6 70.4711 1342.15C62.6006 1330.91 56.1345 1320.29 51.1926 1310.33C43.7813 " +
        "1303.6 37.2922 1297.13 31.7983 1290.91C20.3979 1278.01 13.2421 1266.18 11.0503 " +
        "1255.46C8.84989 1244.7 11.6623 1235.1 20.0902 1226.75ZM53.2491 1312.18C57.9988 " +
        "1321.41 64.041 1331.22 71.2904 1341.57C85.5714 1361.97 104.515 1384.46 127.42 " +
        "1408.81C219.051 1506.23 373.94 1633.36 546.88 1775.49C719.805 1917.6 910.757 " +
        "2074.7 1074.46 2232.06C1238.16 2389.41 1374.67 2547.05 1438.65 2690.26C1502.49 " +
        "2833.13 1651.82 2902.82 1843.22 2916.47C2034.61 2930.11 2267.94 2887.69 " +
        "2499.53 2806.48C2731.12 2725.27 2960.93 2605.27 3145.28 2463.8C3329.64 2322.31 " +
        "3468.48 2159.38 3518.22 1992.32C3526.94 1963.02 3527.73 1937.08 3521.5 " +
        "1914.04C3515.27 1891.01 3502.01 1870.84 3482.58 1853.1C3443.7 1817.6 3380.13 " +
        "1791.88 3298.86 1772.49C3263.23 1763.98 3224.21 1756.7 3182.4 1750.34C3189.92 " +
        "1882.9 3117.68 2008.18 2997.17 2117.84C2849.37 2252.34 2628.86 2363.43 2393.4 " +
        "2435.82C2157.94 2508.2 1907.48 2541.89 1699.73 2521.51C1492.01 2501.14 1326.84 " +
        "2426.7 1262.24 2282.67C1197.81 2139 1070.58 2005.69 920.945 1885.1C771.315 " +
        "1764.5 599.318 1656.65 445.386 1563.84C291.477 1471.06 155.585 1393.3 78.2415 " +
        "1332.93C69.0678 1325.77 60.7143 1318.85 53.2491 1312.18ZM215.796 " +
        "1175.63C316.212 1161.44 452.074 1149.82 609.876 1150.72C841.279 1152.05 " +
        "1119.89 1180.32 1403.12 1266.95C1231.52 1050.12 961.527 1018.05 708.801 " +
        "1049.22C517.851 1072.76 336.885 1132.39 215.796 1175.63Z"

private const val PLATFORM_OUTLINE_1 =
    "M155.823 1203.96C163.115 1192.55 176.571 1183.82 196.796 1177.94C272.943 " +
        "1155.83 425.706 1096.94 656.296 1090.49C724.872 1088.57 800.331 1091.29 " +
        "882.706 1100.99C1141.83 1033.75 1432.92 1011.81 1710.45 1177.99C1898.39 " +
        "1290.53 2057.2 1333.83 2197.14 1345.76C2337.1 1357.7 2458.2 1338.27 2570.79 " +
        "1325.3C2683.36 1312.34 2787.44 1305.85 2893.19 1343.75C2998.92 1381.65 3106.27 " +
        "1463.9 3225.49 1628.25C3319.36 1649.57 3402.87 1674.09 3471.18 1703.42C3542.77 " +
        "1734.16 3597.73 1770.22 3630.41 1813.5C3663.11 1856.8 3673.48 1907.3 3655.96 " +
        "1966.81C3606.14 2135.95 3466.45 2300.57 3280.98 2443.3C3095.49 2586.05 2864.16 " +
        "2706.93 2630.96 2788.58C2397.76 2870.23 2162.66 2912.64 1969.65 " +
        "2898.41C1776.65 2884.17 1625.61 2813.27 1560.77 2668.14C1496.07 2523.36 " +
        "1358.34 2363.74 1193.1 2204.26C1027.87 2044.79 835.193 1885.51 660.673 " +
        "1741.39C491.155 1601.41 338.747 1475.72 245.31 1378.09C230.056 1369.85 216.203 " +
        "1362.2 203.929 1355.15C185.143 1344.37 170.042 1335.01 159.27 1327.12C153.885 " +
        "1323.17 149.57 1319.59 146.415 1316.37C143.269 1313.17 141.232 1310.28 140.473 " +
        "1307.74C140.09 1306.46 140.027 1305.25 140.324 1304.12C140.621 1303 141.27 " +
        "1301.99 142.25 1301.11C144.193 1299.37 147.464 1298.1 152.049 1297.24C157.937 " +
        "1296.14 166.12 1295.7 176.754 1295.93C164.294 1277.36 155.799 1260.55 151.823 " +
        "1245.69C147.462 1229.39 148.524 1215.38 155.823 1203.96ZM248.184 " +
        "1379.64C341.746 1476.89 493.063 1601.69 661.31 1740.62C835.824 1884.73 1028.53 " +
        "2044.04 1193.79 2203.54C1359.04 2363.03 1496.9 2522.76 1561.68 2667.74C1626.3 " +
        "2812.37 1776.89 2883.18 1969.73 2897.41C2162.56 2911.63 2397.51 2869.25 " +
        "2630.63 2787.64C2863.74 2706.02 3094.98 2585.18 3280.37 2442.51C3465.77 " +
        "2299.84 3605.27 2135.37 3655 1966.53C3672.44 1907.31 3662.12 1857.14 3629.61 " +
        "1814.1C3597.09 1771.03 3542.31 1735.05 3470.79 1704.34C3402.81 1675.15 3319.75 " +
        "1650.73 3226.38 1629.48C3277.76 1700.86 3291.84 1773.24 3276.57 1844.1C3261.22 " +
        "1915.31 3216.24 1984.94 3149.81 2050.51C3016.96 2181.64 2798.16 2296.62 " +
        "2558.26 2375.49C2318.36 2454.36 2057.29 2497.13 1839.85 2483.78C1622.44 " +
        "2470.43 1448.47 2400.96 1383.07 2255.14C1317.84 2109.69 1190.15 1975.58 1040.5 " +
        "1856.6C890.859 1737.64 719.292 1633.83 566.343 1548.99C489.87 1506.57 418.054 " +
        "1468.9 355.963 1436.43C315.423 1415.24 279.025 1396.26 248.184 " +
        "1379.64ZM197.074 1178.9C176.971 1184.74 163.775 1193.37 156.665 1204.5C149.563 " +
        "1215.61 148.475 1229.31 152.789 1245.43C156.773 1260.32 165.352 1277.23 " +
        "177.979 1295.96C183.632 1296.11 189.956 1296.45 196.972 1296.97C266.292 1302.1 " +
        "358.782 1275.29 467.801 1237.61C576.778 1199.95 702.221 1151.44 837.327 " +
        "1113.29C851.459 1109.3 865.698 1105.42 880.034 1101.68C798.7 1092.23 724.142 " +
        "1089.59 656.324 1091.48C425.893 1097.93 273.275 1156.77 197.074 1178.9Z"
