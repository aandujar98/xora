package com.arcadia.shell.feature.home

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.XoraEmulatorSettings
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * PS3-style Cross Media Bar over a live/frozen game session.
 *
 * The game frame is shown blurred behind the bar (like holding the PS button).
 * [XoraXmbAction.DrillXoraEmulator] / session actions are handled by [onAction].
 */
@Composable
fun XoraInGameXmbOverlay(
    frozenFrame: Bitmap?,
    gameTitle: String,
    profileName: String,
    emulatorSettings: XoraEmulatorSettings,
    raHardcore: Boolean = false,
    onAction: (XoraXmbAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var categoryIndex by remember { mutableIntStateOf(XoraXmbCategory.Games.ordinal) }
    var itemIndex by remember { mutableIntStateOf(0) }
    var depth by remember { mutableStateOf(XoraXmbDepth.Category) }
    val categoryScroll = remember { Animatable(categoryIndex.toFloat()) }
    val itemScroll = remember { Animatable(0f) }
    val listEnterAlpha = remember { Animatable(1f) }

    val category = XoraXmbCategory.entries.getOrElse(categoryIndex) { XoraXmbCategory.Games }
    val items = when (depth) {
        XoraXmbDepth.Emulator -> buildXoraEmulatorItems(emulatorSettings, raHardcore = raHardcore)
        else -> buildXoraCategoryItems(
            category = category,
            profileName = profileName,
            gamesSecondarySlot = GamesSecondarySlot.Continue,
            continueGame = null,
            favoriteGame = null,
            showXoraEmulator = true,
        ).map { item ->
            if (item.action is XoraXmbAction.ResumeGame) {
                item.copy(subtitle = gameTitle)
            } else {
                item
            }
        }
    }
    val safeItemIndex = itemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    LaunchedEffect(categoryIndex) {
        categoryScroll.animateTo(
            categoryIndex.toFloat(),
            tween(INGAME_XMB_SCROLL_MS, easing = FastOutSlowInEasing),
        )
    }
    LaunchedEffect(safeItemIndex, depth, categoryIndex) {
        itemScroll.snapTo(safeItemIndex.toFloat())
        listEnterAlpha.snapTo(0.35f)
        listEnterAlpha.animateTo(1f, tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(items.size) {
        if (itemIndex > items.lastIndex) itemIndex = items.lastIndex.coerceAtLeast(0)
    }

    fun selectCategory(index: Int) {
        if (depth != XoraXmbDepth.Category) {
            depth = XoraXmbDepth.Category
        }
        categoryIndex = index.coerceIn(0, XoraXmbCategory.entries.lastIndex)
        itemIndex = 0
    }

    fun activate() {
        val item = items.getOrNull(safeItemIndex) ?: return
        when (val action = item.action) {
            XoraXmbAction.ResumeGame -> onDismiss()
            XoraXmbAction.DrillXoraEmulator -> {
                depth = XoraXmbDepth.Emulator
                itemIndex = 0
            }
            XoraXmbAction.OpenFullXoraEmulatorSetup -> onAction(action)
            is XoraXmbAction.ToggleXoraEmulatorSetting -> onAction(action)
            XoraXmbAction.QuitGame,
            XoraXmbAction.SaveGameState,
            XoraXmbAction.LoadGameState,
            XoraXmbAction.ResetGame,
            -> onAction(action)
            is XoraXmbAction.OpenSettingsCategory -> onAction(action)
            else -> {
                // Other launcher rows are not live mid-session.
                onAction(action)
            }
        }
    }

    fun drillOut() {
        when (depth) {
            XoraXmbDepth.Emulator -> {
                depth = XoraXmbDepth.Category
                categoryIndex = XoraXmbCategory.Games.ordinal
                itemIndex = 0
            }
            else -> onDismiss()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Blurred game plate — never a translucent Compose sheet over a live ImageView sibling.
        if (frozenFrame != null && !frozenFrame.isRecycled) {
            val imageBitmap = remember(frozenFrame) { frozenFrame.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(28.dp)
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer {
                        scaleX = 1.06f
                        scaleY = 1.06f
                    },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99060A12)),
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val crossX = maxWidth * CROSS_X_FRACTION
            val catY = maxHeight * CATEGORY_Y_FRACTION
            val itemFocusY = catY + CATEGORY_TO_ITEM_GAP
            val categories = XoraXmbCategory.entries
            val atRoot = depth == XoraXmbDepth.Category
            val catIcon = CATEGORY_ICON
            val itemIcon = ITEM_ICON
            val itemRow = ITEM_ROW
            val itemPitch = ITEM_PITCH
            val categoryPitchPx = with(density) { CATEGORY_PITCH.toPx() }
            val itemPitchPx = with(density) { itemPitch.toPx() }
            val catIconPx = with(density) { catIcon.toPx() }
            val itemRowPx = with(density) { itemRow.toPx() }
            val crossXPx = with(density) { crossX.toPx() }
            val catYPx = with(density) { catY.toPx() }
            val itemFocusYPx = with(density) { itemFocusY.toPx() }
            val glyphSlotPx = with(density) { itemIcon.toPx() }
            val glyphGapPx = with(density) { 14.dp.toPx() }
            val catScroll = categoryScroll.value
            val rowScroll = itemScroll.value
            val enterAlpha = listEnterAlpha.value

            categories.forEachIndexed { index, cat ->
                val delta = index - catScroll
                val distance = abs(delta)
                val scale = when {
                    distance < 0.5f -> lerp(1.12f, 1.32f, 1f - distance / 0.5f)
                    distance < 1.5f -> lerp(0.86f, 1.12f, 1.5f - distance)
                    distance < 2.5f -> lerp(0.72f, 0.86f, 2.5f - distance)
                    else -> 0.58f
                }
                val alpha = when {
                    distance < 0.5f -> if (atRoot) 1f else 0.35f
                    distance < 1.5f -> if (atRoot) 0.58f else 0.18f
                    distance < 2.5f -> if (atRoot) 0.34f else 0.1f
                    else -> 0.08f
                }
                val xPx = crossXPx - catIconPx / 2f + categoryPitchPx * delta
                val yPx = catYPx - catIconPx / 2f
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = xPx
                            translationY = yPx
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            transformOrigin = TransformOrigin.Center
                        }
                        .size(catIcon)
                        .clickable(
                            interactionSource = remember(index) { MutableInteractionSource() },
                            indication = null,
                        ) { selectCategory(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    XmbVectorIcon(
                        icon = cat.toXmbIcon(),
                        tint = Color.White,
                        size = 34.dp,
                    )
                }
            }

            val catLabel = when (depth) {
                XoraXmbDepth.Emulator -> "XOrA Emulator"
                else -> category.label
            }
            XoraTitleText(
                text = catLabel,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = crossXPx - with(density) { 120.dp.toPx() } / 2f
                        translationY = catYPx + catIconPx / 2f + with(density) { 4.dp.toPx() }
                        alpha = if (atRoot) 0.95f else 0.45f
                    }
                    .width(120.dp),
            )

            if (items.isEmpty()) {
                XoraSecondaryText(
                    text = "Nothing here yet",
                    fontSize = 18.sp,
                    fillColor = Color.White,
                    modifier = Modifier.graphicsLayer {
                        translationX = crossXPx + glyphSlotPx / 2f + glyphGapPx
                        translationY = itemFocusYPx
                        alpha = enterAlpha
                    },
                )
            } else {
                val first = (rowScroll - VISIBLE_ITEM_RADIUS - 1f).toInt().coerceAtLeast(0)
                val last = (rowScroll + VISIBLE_ITEM_RADIUS + 1f).roundToInt()
                    .coerceAtMost(items.lastIndex)
                for (index in first..last) {
                    val item = items[index]
                    val delta = index - rowScroll
                    val distance = abs(delta)
                    val focus = (1f - distance).coerceIn(0f, 1f)
                    val scale = when {
                        distance < 0.5f -> lerp(1f, 1.48f, focus)
                        distance < 1.5f -> 0.88f
                        distance < 2.5f -> 0.78f
                        else -> 0.7f
                    }
                    val alpha = when {
                        distance < 0.5f -> 1f
                        distance < 1.5f -> 0.72f
                        distance < 2.5f -> 0.42f
                        distance < 3.5f -> 0.24f
                        else -> 0.1f
                    }
                    val yPx = itemFocusYPx - itemRowPx / 2f +
                        xmbInGameItemOffsetY(delta, itemPitchPx)
                    val xPx = crossXPx - glyphSlotPx / 2f
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = xPx
                                translationY = yPx
                                this.alpha = alpha * enterAlpha
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin.Center
                            }
                            .width(itemIcon)
                            .clickable(
                                interactionSource = remember(item.id) { MutableInteractionSource() },
                                indication = null,
                            ) {
                                itemIndex = index
                                scope.launch {
                                    itemScroll.animateTo(
                                        index.toFloat(),
                                        tween(INGAME_XMB_SCROLL_MS, easing = FastOutSlowInEasing),
                                    )
                                    activate()
                                }
                            },
                    ) {
                        XmbVectorIcon(
                            icon = item.icon,
                            tint = Color.White,
                            size = 28.dp,
                        )
                    }
                    Column(
                        modifier = Modifier.graphicsLayer {
                            translationX = crossXPx + glyphSlotPx / 2f + glyphGapPx
                            translationY = yPx + itemRowPx * 0.12f
                            this.alpha = alpha * enterAlpha
                            scaleX = lerp(0.92f, 1.05f, focus)
                            scaleY = lerp(0.92f, 1.05f, focus)
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        },
                    ) {
                        XoraTitleText(
                            text = item.title,
                            fontWeight = if (index == safeItemIndex) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
                            fontSize = if (index == safeItemIndex) 20.sp else 16.sp,
                            maxLines = 1,
                        )
                        if (!item.subtitle.isNullOrBlank() && index == safeItemIndex) {
                            XoraSecondaryText(
                                text = item.subtitle,
                                fontSize = 12.sp,
                                fillColor = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            XoraSecondaryText(
                text = "Back closes · A confirms · $gameTitle",
                fontSize = 11.sp,
                fillColor = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 22.dp),
            )
        }

        // Invisible key focus host — navigation is driven from the Activity gamepad path
        // via [XoraInGameXmbController] state held by the caller.
        InGameXmbNavBridge(
            itemCount = items.size,
            categoryCount = XoraXmbCategory.entries.size,
            atEmulatorDepth = depth == XoraXmbDepth.Emulator,
            onMoveCategory = { delta ->
                if (depth == XoraXmbDepth.Category) {
                    val size = XoraXmbCategory.entries.size
                    selectCategory((categoryIndex + delta).mod(size))
                }
            },
            onMoveItem = { delta ->
                if (items.isEmpty()) return@InGameXmbNavBridge
                itemIndex = (safeItemIndex + delta).mod(items.size)
            },
            onConfirm = { activate() },
            onCancel = { drillOut() },
        )
    }
}

/**
 * Holds the latest nav lambdas so [XoraLibretroActivity] can drive D-pad without
 * recomposing the whole overlay tree on every key.
 */
class XoraInGameXmbController {
    @Volatile var moveCategory: ((Int) -> Unit)? = null
    @Volatile var moveItem: ((Int) -> Unit)? = null
    @Volatile var confirm: (() -> Unit)? = null
    @Volatile var cancel: (() -> Unit)? = null
}

@Composable
private fun InGameXmbNavBridge(
    itemCount: Int,
    categoryCount: Int,
    atEmulatorDepth: Boolean,
    onMoveCategory: (Int) -> Unit,
    onMoveItem: (Int) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val controller = LocalInGameXmbController.current
    LaunchedEffect(itemCount, categoryCount, atEmulatorDepth, onMoveCategory, onMoveItem, onConfirm, onCancel) {
        controller?.moveCategory = onMoveCategory
        controller?.moveItem = onMoveItem
        controller?.confirm = onConfirm
        controller?.cancel = onCancel
    }
}

val LocalInGameXmbController = androidx.compose.runtime.staticCompositionLocalOf<XoraInGameXmbController?> {
    null
}

private fun xmbInGameItemOffsetY(delta: Float, pitchPx: Float): Float {
    val abs = abs(delta)
    val stretch = when {
        abs < 1f -> 1.15f
        abs < 2f -> 1.05f
        else -> 1f
    }
    return delta * pitchPx * stretch
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

private const val CROSS_X_FRACTION = 0.28f
private const val CATEGORY_Y_FRACTION = 0.30f
private val CATEGORY_TO_ITEM_GAP = 72.dp
private const val INGAME_XMB_SCROLL_MS = 340
private const val VISIBLE_ITEM_RADIUS = 4
private val CATEGORY_ICON = 56.dp
private val CATEGORY_PITCH = 72.dp
private val ITEM_ICON = 44.dp
private val ITEM_ROW = 52.dp
private val ITEM_PITCH = 58.dp
