package com.arcadia.shell.feature.home.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.XoraOutlinedText
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.motionMillis
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.supportsGlassBlurEffect
import com.arcadia.shell.designsystem.xoraForegroundShadow
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.designsystem.xoraTextScale
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.feature.home.SystemFavoriteGame
import com.arcadia.shell.feature.home.SystemPanelRow
import com.arcadia.shell.feature.home.SystemProfileCardState
import com.arcadia.shell.feature.home.buildSystemPanelRows
import com.arcadia.shell.model.Game
import com.arcadia.shell.retroachievements.RaCompletionGame
import com.arcadia.shell.retroachievements.RaRecentUnlock
import com.arcadia.shell.xoranetwork.xoraAppearanceLabel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

private val ScoreAmber = Color(0xFFFFA22B)
private val OnlineGreen = Color(0xFF37D6A0)
private val AwayAmber = Color(0xFFFFC24B)
private val BusyRose = Color(0xFFFF5C6C)
private val FocusRing = Color(0xFF4AE39A)
private val BadgeBorder = Color(0xFFFECF67)

/** Frosted plate rim — thicker glass edge on the profile modal. */
private val OutlineInk = Color.Black
private val BubbleInk = Color(0xFF474747)
private val FooterInk = Color.White.copy(alpha = 0.50f)
private val DullFillTop = Color.White
private val DullFillBottom = Color(0xFFA1A1A1)
private val ChromeStrokeTop = Color.White
private val ChromeStrokeBottom = Color(0xFFB0B0B0)
private val PlaytimeFillTop = Color(0xFFADFF7B)
private val PlaytimeFillBottom = Color(0xFF30C942)

private val DullFillBrush = Brush.verticalGradient(listOf(DullFillTop, DullFillBottom))
private val ChromeStrokeBrush = Brush.verticalGradient(listOf(ChromeStrokeTop, ChromeStrokeBottom))
private val PlaytimeFillBrush = Brush.verticalGradient(listOf(PlaytimeFillTop, PlaytimeFillBottom))
private val StatusBubbleFillBrush = Brush.verticalGradient(
    listOf(ChromeStrokeTop, ChromeStrokeBottom),
)

private val CardStroke = 3.dp
private val CardAssetShadowDp = 4.dp
private val CardShadowInk = Color(0xFF000000)
private val Game0Border = 3.dp
private val FavoritePlateW = 210.dp
private val FavoritePlateH = 108.dp
private val FavoritePlateRadius = 12.dp
private val ProfileCardWidth = 464.dp
/** Figma 464×444 card (nodes 363:1927 / 710:1769), grown so Favorite Game clears the footer. */
private val ProfileCardHeight = 540.dp
private val ProfileCardRadius = 24.dp
private val ProfileCardPadStart = 22.dp
private val ProfileCardPadEnd = 22.dp
private val ProfileCardPadTop = 20.dp
private val ProfileCardPadBottom = 10.dp
private val ProfileIdentityGap = 10.dp
private val StatusBubbleTextSize = 16.sp
private val RecentlyEarnedBadgeSlots = 6
private val RecentlyEarnedBadgeSize = 60.dp
private val RecentlyEarnedBadgeGap = 12.dp
private val RecentlyEarnedLabelGap = 25.dp
private val FavoriteLabelGap = 30.dp
private val HeaderToRecentGap = 21.dp
private val RecentToFavoriteGap = 15.dp
private val FavoriteToFooterGap = 12.dp
private val PresenceDotSize = 12.dp
private val TrophyGlyphW = 24.dp
private val TrophyGlyphH = TrophyGlyphW * (120f / 130f)

private fun vibrantFillBrush(accent: Color): Brush =
    Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.42f), accent))

/** Collapsed RT chrome is the profile picture alone, tucked into the corner. */
private val CollapsedAvatarSize = 88.dp

/** Figma crops the disc on both screen edges; this clears the pane padding to get there. */
private val CollapsedAvatarBleed = 24.dp

/** Expanded header disc — Figma 464×444 card (nodes 363:1927 / 710:1686). */
private val ProfileAvatarSelectedSize = 96.dp

/** Selected drop shadow: X4 Y4 B4 S0. Idle chrome stays 10 / 10 / 15. */
private val ProfileBubbleSelectedShadow = 4.dp

/** Two horizontal coin-flips as the bubble settles into the expanded header. */
private const val ProfileBubbleFlipDeg = 720f
/** 20% longer than the original 500ms settle, same end easing. */
private const val ProfileBubbleFlipMs = 600

/** Stronger ease-out so the last degrees of the flip settle instead of hitting a wall. */
private val ProfileBubbleEasing = CubicBezierEasing(0.12f, 0.82f, 0.08f, 1f)

/** Card inner padding the selected bubble must land in (header top-left). */
private val ProfileBubbleSelectedInsetStart = 22.dp
private val ProfileBubbleSelectedInsetTop = 19.dp

/**
 * Afterimage samples along the coin-flip. White discs share the live bubble's Y-spin so
 * the trail reads as a lagged echo, not a stacked face or a flat ribbon.
 */
private const val ProfileBubbleEchoCount = 24
private const val ProfileBubbleEchoSpan = 0.22f
private val ProfileBubbleEchoLags = FloatArray(ProfileBubbleEchoCount) { i ->
    val t = (i + 1f) / ProfileBubbleEchoCount
    t * t * ProfileBubbleEchoSpan
}
private val ProfileBubbleEchoInk = Color.White

/** Slim bubble depth as a fraction of diameter — keeps the flip from collapsing to a line. */
private const val ProfileBubbleThickness = 0.24f
private const val ProfileBubbleAnimCamera = 8f
private const val ProfileBubbleRestCamera = 16f

/**
 * Figma Make top-right bubble: inner 188.044 over Ellipse56 182.495, rotated 165°,
 * mix-blend soft-light. Same PNG as the Vita tray glass ([R.drawable.vita_bubble_glass]).
 */
private const val UserBubbleOverAvatar = 188.044f / 182.495f
private const val UserBubbleRotationDeg = 165f

@Composable
fun SystemPill(
    profile: LocalProfile,
    avatarImageModel: String?,
    raUsername: String?,
    raScore: Int?,
    recentAchievements: List<RaRecentUnlock>,
    jumpBackGames: List<Game> = emptyList(),
    systemProfile: SystemProfileCardState,
    expanded: Boolean,
    selectedRowIndex: Int,
    hideCollapsedChrome: Boolean = false,
    onToggle: () -> Unit,
    onSelectRow: (Int) -> Unit,
    onActivateRow: (Int?) -> Unit,
    onStatusDraftChange: (String) -> Unit,
    onSaveCustomStatus: () -> Unit,
    onClearCustomStatus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var batteryPercent by remember { mutableIntStateOf(readBatteryPercent(context)) }
    var charging by remember { mutableStateOf(isCharging(context)) }
    var wifiConnected by remember { mutableStateOf(isWifiConnected(context)) }
    val listState = rememberLazyListState()
    // The pane already reserves its own 12dp inset, so cap on the frame rather than a fraction
    // of it — a fractional cap clipped the card before it reached its designed height.
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(320).dp

    val systemRows = remember(
        systemProfile.favoritePickerOpen,
        systemProfile.favoritePickerGames,
    ) {
        buildSystemPanelRows(
            jumpBackGames = jumpBackGames.map { it.id },
            favoritePickerOpen = systemProfile.favoritePickerOpen,
            favoritePickerGameIds = systemProfile.favoritePickerGames.map { it.gameId },
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            wifiConnected = isWifiConnected(context)
            delay(15_000)
        }
    }

    LaunchedEffect(selectedRowIndex, expanded, systemRows.size, systemProfile.favoritePickerOpen) {
        if (!expanded || systemRows.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(selectedRowIndex.coerceIn(0, systemRows.lastIndex))
    }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                batteryPercent = readBatteryPercent(context)
                charging = isCharging(context)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    val timeText = remember(now) {
        DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(now)
    }
    val dateShort = remember(now) {
        android.text.format.DateFormat.format("MM/dd/yy", now).toString()
    }

    val presetColor = avatarPreset(profile.avatarPresetId).color
    val usernameAccent = rememberAvatarAccentColor(
        imageModel = avatarImageModel,
        fallback = presetColor,
    )
    val displayName = (raUsername?.takeIf { it.isNotBlank() } ?: profile.displayName)
        .uppercase(Locale.getDefault())
    val editSelected = expanded &&
        systemRows.getOrNull(selectedRowIndex) is SystemPanelRow.EditProfile
    val onEditProfile = {
        val idx = systemRows.indexOfFirst { it is SystemPanelRow.EditProfile }
        if (idx >= 0) {
            onSelectRow(idx)
            onActivateRow(idx)
        }
    }

    Column(
        modifier = modifier.widthIn(max = if (expanded) ProfileCardWidth else 300.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Box {
        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = arcadiaTween<IntSize>(ArcadiaMotion.Medium),
            ),
            exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = arcadiaTween<IntSize>(ArcadiaMotion.Fast),
            ),
        ) {
            val cardShape = RoundedCornerShape(ProfileCardRadius)
            val pickerScroll = rememberScrollState()
            val cardHeight = ProfileCardHeight.coerceAtMost(maxPanelHeight)
            Column(
                modifier = Modifier
                    .width(ProfileCardWidth)
                    .height(cardHeight)
                    .xoraModalGlass(cardShape)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .then(
                            if (systemProfile.favoritePickerOpen) {
                                Modifier
                            } else {
                                Modifier.verticalScroll(pickerScroll)
                            },
                        )
                        .padding(
                            start = ProfileCardPadStart,
                            end = ProfileCardPadEnd,
                            top = ProfileCardPadTop,
                        )
                        .fillMaxWidth(),
                ) {
                if (systemProfile.favoritePickerOpen) {
                    FavoritePickerPanel(
                        state = systemProfile,
                        selectedRowIndex = selectedRowIndex,
                        listState = listState,
                        onSelectRow = onSelectRow,
                        onActivateRow = onActivateRow,
                    )
                } else {
                    ProfileCardHeader(
                        displayName = displayName,
                        usernameAccent = usernameAccent,
                        raScore = raScore,
                        systemProfile = systemProfile,
                        statusSelected = systemRows.getOrNull(selectedRowIndex) is SystemPanelRow.Status,
                        onSelectStatus = {
                            val idx = systemRows.indexOfFirst { it is SystemPanelRow.Status }
                            if (idx >= 0) onSelectRow(idx)
                        },
                        onActivateStatus = {
                            val idx = systemRows.indexOfFirst { it is SystemPanelRow.Status }
                            if (idx >= 0) {
                                onSelectRow(idx)
                                onActivateRow(idx)
                            }
                        },
                        onEditProfile = onEditProfile,
                        onStatusDraftChange = onStatusDraftChange,
                        onSaveCustomStatus = onSaveCustomStatus,
                        onClearCustomStatus = onClearCustomStatus,
                    )

                    Spacer(modifier = Modifier.height(HeaderToRecentGap))

                    RecentlyEarnedStrip(unlocks = recentAchievements)

                    Spacer(modifier = Modifier.height(RecentToFavoriteGap))

                    FavoriteGameSection(
                        favorite = systemProfile.favorite,
                        selected = systemRows.getOrNull(selectedRowIndex) is SystemPanelRow.FavoriteGame,
                        onClick = {
                            val idx = systemRows.indexOfFirst { it is SystemPanelRow.FavoriteGame }
                            if (idx >= 0) {
                                onSelectRow(idx)
                                onActivateRow(idx)
                            }
                        },
                    )
                }
                }
                ProfileCardFooter(
                    wifiConnected = wifiConnected,
                    timeText = timeText,
                    dateText = dateShort,
                    batteryPercent = batteryPercent,
                    charging = charging,
                    modifier = Modifier.padding(
                        start = ProfileCardPadStart,
                        end = ProfileCardPadEnd,
                        top = FavoriteToFooterGap,
                        bottom = ProfileCardPadBottom,
                    ),
                )
            }
        }

            androidx.compose.animation.AnimatedVisibility(
                visible = expanded || !hideCollapsedChrome,
                enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)),
                exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                ProfileSelectBubble(
                    expanded = expanded,
                    editSelected = editSelected,
                    profile = profile,
                    avatarImageModel = avatarImageModel,
                    onClick = if (expanded) onEditProfile else onToggle,
                )
            }
        }
    }
}

@Composable
private fun ProfileSelectBubble(
    expanded: Boolean,
    editSelected: Boolean,
    profile: LocalProfile,
    avatarImageModel: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = motionMillis(ProfileBubbleFlipMs),
            easing = ProfileBubbleEasing,
        ),
        label = "profileBubbleSelect",
    )
    val userBubbleGlass = ImageBitmap.imageResource(R.drawable.vita_bubble_glass)
    val moving = !reduceMotion && progress > 0.02f && progress < 0.98f
    val canBlurTrail = supportsGlassBlurEffect()
    val spinDeg = ProfileBubbleFlipDeg * progress
    val spinRad = Math.toRadians(spinDeg.toDouble())
    val spinSin = sin(spinRad).toFloat()
    val absCos = abs(cos(spinRad)).toFloat()
    val volumeScaleX = if (moving) {
        absCos * (1f - ProfileBubbleThickness) + ProfileBubbleThickness
    } else {
        1f
    }
    val faceAlpha = if (moving) {
        ((absCos - 0.06f) / 0.22f).coerceIn(0.22f, 1f)
    } else {
        1f
    }
    val bubbleSize = profileBubbleSize(progress)
    val shadow = XoraForegroundShadow.DesignOffset +
        (ProfileBubbleSelectedShadow.value - XoraForegroundShadow.DesignOffset) * progress

    Box(
        modifier = modifier.graphicsLayer { clip = false },
        contentAlignment = Alignment.TopEnd,
    ) {
        if (moving) {
            val echoCount = ProfileBubbleEchoLags.size
            for (index in echoCount - 1 downTo 0) {
                val lag = ProfileBubbleEchoLags[index]
                val echoProgress = (progress - lag).coerceIn(0f, 1f)
                val trailT = index / (echoCount - 1f).coerceAtLeast(1f)
                ProfileBubbleEcho(
                    progress = echoProgress,
                    trailT = trailT,
                    canBlur = canBlurTrail,
                )
            }
        }

        Box(
            modifier = Modifier
                .profileBubblePlacement(progress)
                .size(bubbleSize)
                .graphicsLayer { clip = false },
        ) {
            if (moving) {
                ProfileBubbleVolumeShell(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = volumeScaleX
                            cameraDistance = ProfileBubbleAnimCamera * density
                            transformOrigin = TransformOrigin.Center
                            clip = false
                        },
                )
            }
            ProfileAvatar(
                displayName = profile.displayName,
                presetId = profile.avatarPresetId,
                size = bubbleSize,
                imageModel = avatarImageModel,
                borderColor = Color.White.copy(alpha = 0.9f),
                onClick = onClick,
                modifier = Modifier
                    .then(
                        if (editSelected) {
                            Modifier.border(2.5.dp, FocusRing, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .xoraForegroundShadow(
                        shape = CircleShape,
                        offset = shadow.dp,
                        blur = shadow.dp,
                    )
                    .graphicsLayer {
                        rotationY = spinDeg
                        cameraDistance = (if (moving) {
                            ProfileBubbleAnimCamera
                        } else {
                            ProfileBubbleRestCamera
                        }) * density
                        alpha = faceAlpha
                        transformOrigin = TransformOrigin.Center
                        clip = false
                    }
                    .drawWithContent {
                        drawContent()
                        if (moving) {
                            val w = size.width
                            val h = size.height
                            val recede = spinSin
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.40f to Color.Transparent,
                                    1.00f to Color.Black.copy(alpha = 0.22f * abs(recede)),
                                    center = Offset(w * (0.50f - 0.16f * recede), h * 0.52f),
                                    radius = size.minDimension * 0.62f,
                                ),
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.00f to Color.White.copy(alpha = 0.40f),
                                    0.50f to Color.White.copy(alpha = 0.08f),
                                    1.00f to Color.Transparent,
                                    center = Offset(
                                        w * (0.30f + 0.22f * recede),
                                        h * 0.28f,
                                    ),
                                    radius = size.minDimension * 0.34f,
                                ),
                            )
                        }
                        withTransform({
                            rotate(UserBubbleRotationDeg)
                            val factor = (size.minDimension * UserBubbleOverAvatar) /
                                userBubbleGlass.width
                            scale(factor, factor)
                        }) {
                            drawImage(
                                image = userBubbleGlass,
                                topLeft = Offset(
                                    (size.width - userBubbleGlass.width) / 2f,
                                    (size.height - userBubbleGlass.height) / 2f,
                                ),
                                blendMode = BlendMode.Softlight,
                            )
                        }
                    },
            )
        }
    }
}

@Composable
private fun ProfileBubbleEcho(
    progress: Float,
    trailT: Float,
    canBlur: Boolean,
) {
    val fade = (exp(-3.4f * trailT * trailT) * 0.28f * (1f - progress * 0.18f))
        .coerceAtLeast(0.03f)
    val taper = 1f - 0.08f * trailT
    val echoSize = profileBubbleSize(progress) * taper
    Box(
        modifier = Modifier
            .profileBubblePlacement(progress)
            .size(echoSize)
            .graphicsLayer {
                rotationY = ProfileBubbleFlipDeg * progress
                cameraDistance = ProfileBubbleAnimCamera * density
                alpha = fade
                transformOrigin = TransformOrigin.Center
                clip = false
                compositingStrategy = CompositingStrategy.Offscreen
                if (canBlur) {
                    renderEffect = BlurEffect(
                        4f + 10f * trailT,
                        4f + 10f * trailT,
                        TileMode.Decal,
                    )
                }
            }
            .clip(CircleShape)
            .background(ProfileBubbleEchoInk),
    )
}

@Composable
private fun ProfileBubbleVolumeShell(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val strokePx = with(density) { CardStroke.toPx() }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .drawBehind {
                drawCircle(brush = StatusBubbleFillBrush)
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to Color.White.copy(alpha = 0.55f),
                        0.55f to Color.White.copy(alpha = 0.14f),
                        1.00f to Color.White.copy(alpha = 0.04f),
                    ),
                )
                drawCircle(
                    brush = ChromeStrokeBrush,
                    style = Stroke(width = strokePx, join = StrokeJoin.Round),
                )
            },
    )
}

private fun profileBubbleSize(progress: Float): Dp {
    val start = CollapsedAvatarSize.value
    val end = ProfileAvatarSelectedSize.value
    return (start + (end - start) * progress).dp
}

private fun Modifier.profileBubblePlacement(progress: Float): Modifier {
    val selectedSize = ProfileAvatarSelectedSize.value
    val endX = -(ProfileCardWidth.value - ProfileBubbleSelectedInsetStart.value - selectedSize)
    val startX = CollapsedAvatarBleed.value
    val x = startX + (endX - startX) * progress
    val y = -CollapsedAvatarBleed.value +
        (ProfileBubbleSelectedInsetTop.value + CollapsedAvatarBleed.value) * progress
    return offset(x = x.dp, y = y.dp)
}

@Composable
private fun ProfileCardHeader(
    displayName: String,
    usernameAccent: Color,
    raScore: Int?,
    systemProfile: SystemProfileCardState,
    statusSelected: Boolean,
    onSelectStatus: () -> Unit,
    onActivateStatus: () -> Unit,
    onEditProfile: () -> Unit,
    onStatusDraftChange: (String) -> Unit,
    onSaveCustomStatus: () -> Unit,
    onClearCustomStatus: () -> Unit,
) {
    val identityStart = ProfileAvatarSelectedSize + ProfileIdentityGap
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = identityStart),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        StatusBubble(
            text = systemProfile.statusLine,
            selected = statusSelected,
            editing = systemProfile.statusEditorOpen,
            draft = systemProfile.statusDraft,
            isCustom = systemProfile.isCustomStatus,
            onSelect = onSelectStatus,
            onActivate = onActivateStatus,
            onDraftChange = onStatusDraftChange,
            onSave = onSaveCustomStatus,
            onClear = onClearCustomStatus,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PresenceDot(color = xoraPresenceColor(systemProfile))
            CardTitleText(
                text = displayName,
                fontSize = 20.sp,
                fillBrush = vibrantFillBrush(usernameAccent),
                modifier = Modifier.clickable(onClick = onEditProfile),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrophyMiniGlyph()
            CardTitleText(
                text = "POINTS",
                fontSize = 14.sp,
                fillBrush = DullFillBrush,
                letterSpacing = 0.sp,
            )
            CardTitleText(
                text = formatPoints(raScore),
                fontSize = 18.sp,
                fillBrush = vibrantFillBrush(ScoreAmber),
                letterSpacing = 0.sp,
                overflow = TextOverflow.Visible,
                softWrap = false,
            )
        }
    }
}

/** Section label — blocky white with the XOrA dark outline. */
@Composable
private fun CardSectionLabel(text: String) {
    CardTitleText(
        text = text,
        fontSize = 14.sp,
        fillBrush = DullFillBrush,
    )
}

@Composable
private fun CardTitleText(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fillBrush: Brush,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    letterSpacing: androidx.compose.ui.unit.TextUnit = XoraFonts.TitleLetterSpacing,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    softWrap: Boolean = true,
) {
    XoraOutlinedText(
        text = text,
        modifier = modifier,
        fontFamily = XoraFonts.Title,
        fontWeight = FontWeight.Bold,
        fontSize = fontSize,
        fillBrush = fillBrush,
        outlineColor = OutlineInk,
        outlineWidth = CardStroke,
        letterSpacing = letterSpacing,
        shadow = cardAssetShadow(),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
    )
}

@Composable
private fun cardAssetShadow(): Shadow {
    val px = with(LocalDensity.current) { CardAssetShadowDp.toPx() }
    return Shadow(
        color = CardShadowInk.copy(alpha = 0.50f),
        offset = Offset(px, px),
        blurRadius = px,
    )
}

private val StatusTailWidth = 14.dp
private val StatusTailHeight = 8.dp
private val StatusBubbleCorner = 16.dp
private val StatusTailStart = 12.dp

private fun speechBubblePath(
    size: Size,
    tailW: Float,
    tailH: Float,
    corner: Float,
    tailLeft: Float,
): Path {
    val bodyH = (size.height - tailH).coerceAtLeast(corner * 2f)
    val radius = corner.coerceAtMost(minOf(size.width, bodyH) / 2f)
    val body = Path().apply {
        addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = bodyH,
                radiusX = radius,
                radiusY = radius,
            ),
        )
    }
    val tail = Path().apply {
        val attachY = bodyH - 0.5f
        moveTo(tailLeft + tailW * 0.18f, attachY)
        lineTo(tailLeft + tailW, attachY)
        lineTo(tailLeft, bodyH + tailH)
        close()
    }
    return Path().apply { op(body, tail, PathOperation.Union) }
}

@Composable
private fun StatusBubble(
    text: String,
    selected: Boolean,
    editing: Boolean,
    draft: String,
    isCustom: Boolean,
    onSelect: () -> Unit,
    onActivate: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val statusSize = StatusBubbleTextSize
    val tailW = with(density) { StatusTailWidth.toPx() }
    val tailH = with(density) { StatusTailHeight.toPx() }
    val corner = with(density) { StatusBubbleCorner.toPx() }
    val tailLeft = with(density) { StatusTailStart.toPx() }
    val strokePx = with(density) { CardStroke.toPx() }
    val bubbleShape = remember(tailW, tailH, corner, tailLeft) {
        GenericShape { size, _ ->
            addPath(speechBubblePath(size, tailW, tailH, corner, tailLeft))
        }
    }
    LaunchedEffect(selected) {
        if (selected) {
            delay(16)
            bringIntoViewRequester.bringIntoView()
        }
    }
    Column(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(CardStroke)
            .xoraForegroundShadow(
                shape = bubbleShape,
                offset = CardAssetShadowDp,
                blur = CardAssetShadowDp,
            )
            .drawBehind {
                val path = speechBubblePath(size, tailW, tailH, corner, tailLeft)
                if (selected) {
                    drawPath(
                        path,
                        color = FocusRing,
                        style = Stroke(width = (strokePx + 2.dp.toPx()) * 2f, join = StrokeJoin.Round),
                    )
                }
                drawPath(
                    path,
                    color = Color.Black,
                    style = Stroke(width = strokePx * 2f, join = StrokeJoin.Round),
                )
                drawPath(path, brush = StatusBubbleFillBrush)
            }
            .clip(bubbleShape)
            .clickable {
                onSelect()
                if (!editing) onActivate()
            }
            .padding(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 6.dp + StatusTailHeight,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = XoraFonts.XmbLabel,
                    fontSize = statusSize,
                    color = BubbleInk,
                ),
                cursorBrush = SolidColor(BubbleInk),
                modifier = Modifier.widthIn(min = 160.dp),
                decorationBox = { inner ->
                    Box {
                        if (draft.isBlank()) {
                            Text(
                                text = "Custom status…",
                                style = TextStyle(
                                    fontFamily = XoraFonts.XmbLabel,
                                    fontSize = statusSize,
                                    color = BubbleInk.copy(alpha = 0.5f),
                                ),
                            )
                        }
                        inner()
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSave) {
                    Text("Save", color = BubbleInk, fontWeight = FontWeight.Bold)
                }
                if (isCustom || draft.isNotBlank()) {
                    TextButton(onClick = onClear) {
                        Text("Clear", color = BubbleInk.copy(alpha = 0.7f))
                    }
                }
            }
        } else {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = XoraFonts.XmbLabel,
                    fontSize = statusSize * xoraTextScale(),
                    color = BubbleInk,
                    letterSpacing = 0.sp,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentlyEarnedStrip(unlocks: List<RaRecentUnlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(RecentlyEarnedLabelGap)) {
        CardSectionLabel("RECENTLY EARNED")
        if (unlocks.isEmpty()) {
            Text(
                text = "No recent unlocks yet",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(RecentlyEarnedBadgeGap),
            ) {
                val shown = unlocks.take(RecentlyEarnedBadgeSlots)
                shown.forEach { unlock ->
                    AchievementBadge(
                        unlock = unlock,
                        modifier = Modifier.size(RecentlyEarnedBadgeSize),
                    )
                }
                repeat(RecentlyEarnedBadgeSlots - shown.size) {
                    Spacer(modifier = Modifier.size(RecentlyEarnedBadgeSize))
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(
    unlock: RaRecentUnlock,
    modifier: Modifier = Modifier,
) {
    val platformContext = LocalPlatformContext.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.25f))
            .border(2.dp, BadgeBorder, shape),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(platformContext)
                .data(unlock.badgeUrl)
                .crossfade(120)
                .build(),
            contentDescription = unlock.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun FavoriteGameSection(
    favorite: SystemFavoriteGame?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            delay(16)
            bringIntoViewRequester.bringIntoView()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(FavoriteLabelGap)) {
        CardSectionLabel("FAVORITE GAME")
        val artShape = RoundedCornerShape(FavoritePlateRadius)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FavoritePlateH)
                .bringIntoViewRequester(bringIntoViewRequester)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, FocusRing, RoundedCornerShape(12.dp))
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val density = LocalDensity.current
            val strokePx = with(density) { Game0Border.toPx() }
            val cornerPx = with(density) { FavoritePlateRadius.toPx() }
            Box(
                modifier = Modifier
                    .size(width = FavoritePlateW, height = FavoritePlateH)
                    .xoraForegroundShadow(
                        shape = artShape,
                        offset = CardAssetShadowDp,
                        blur = CardAssetShadowDp,
                    )
                    .drawBehind {
                        drawRoundRect(
                            brush = ChromeStrokeBrush,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = strokePx * 2f, join = StrokeJoin.Round),
                        )
                    }
                    .clip(artShape)
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center,
            ) {
                if (favorite != null && favorite.imageIconUrl.isNotBlank()) {
                    AsyncImage(
                        model = favorite.imageIconUrl,
                        contentDescription = favorite.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(artShape),
                    )
                } else {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = favorite?.title ?: "Add favorite",
                    style = TextStyle(
                        fontFamily = XoraFonts.XmbLabel,
                        fontSize = 18.sp,
                        color = Color.White,
                        shadow = cardAssetShadow(),
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (favorite == null) {
                    CardTitleText(
                        text = "PICK FROM YOUR LIST",
                        fontSize = 14.sp,
                        fillBrush = DullFillBrush,
                        maxLines = 1,
                    )
                } else {
                    val (amount, unit) = playtimeParts(favorite.playTimeMs)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ClockMiniGlyph(
                            tint = PlaytimeFillTop,
                            modifier = Modifier.size(20.dp),
                        )
                        CardTitleText(
                            text = amount,
                            fontSize = 20.sp,
                            fillBrush = PlaytimeFillBrush,
                        )
                        CardTitleText(
                            text = unit,
                            fontSize = 14.sp,
                            fillBrush = PlaytimeFillBrush,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritePickerPanel(
    state: SystemProfileCardState,
    selectedRowIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelectRow: (Int) -> Unit,
    onActivateRow: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "PICK FAVORITE GAME",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = XoraFonts.Title,
                letterSpacing = 0.08.sp,
            ),
            color = Color.White.copy(alpha = 0.78f),
        )
        Text(
            text = "From your RetroAchievements progress",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )

        when {
            state.favoritePickerLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = FocusRing,
                        strokeWidth = 2.dp,
                    )
                }
            }
            state.favoritePickerError != null -> {
                Text(
                    text = state.favoritePickerError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                val rows = buildSystemPanelRows(
                    favoritePickerOpen = true,
                    favoritePickerGameIds = state.favoritePickerGames.map { it.gameId },
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(rows, key = { _, row ->
                        when (row) {
                            is SystemPanelRow.RaFavoritePick -> "ra_${row.gameId}"
                            else -> row::class.simpleName.orEmpty()
                        }
                    }) { index, row ->
                        val selected = index == selectedRowIndex
                        when (row) {
                            SystemPanelRow.ClearFavorite -> PickerRow(
                                title = "Clear favorite",
                                subtitle = "Show the + placeholder again",
                                selected = selected,
                                onClick = {
                                    onSelectRow(index)
                                    onActivateRow(index)
                                },
                            )
                            is SystemPanelRow.RaFavoritePick -> {
                                val game = state.favoritePickerGames.firstOrNull {
                                    it.gameId == row.gameId
                                }
                                if (game != null) {
                                    RaFavoritePickRow(
                                        game = game,
                                        selected = selected,
                                        onClick = {
                                            onSelectRow(index)
                                            onActivateRow(index)
                                        },
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RaFavoritePickRow(
    game: RaCompletionGame,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0.05f))
            .then(
                if (selected) Modifier.border(1.5.dp, FocusRing.copy(alpha = 0.85f), shape)
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            if (game.imageIconUrl.isNotBlank()) {
                AsyncImage(
                    model = game.imageIconUrl,
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${game.consoleName} · ${game.progressLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0.05f))
            .then(
                if (selected) Modifier.border(1.5.dp, FocusRing.copy(alpha = 0.85f), shape)
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ProfileCardFooter(
    wifiConnected: Boolean,
    timeText: String,
    dateText: String,
    batteryPercent: Int,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    val footerSize = 14.sp
    val footerStyle = TextStyle(
        fontFamily = XoraFonts.XmbLabel,
        fontSize = footerSize,
        color = FooterInk,
        shadow = cardAssetShadow(),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WifiGlyph(
            connected = wifiConnected,
            tint = FooterInk,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(text = timeText, style = footerStyle)
        Text(text = "  |  ", style = footerStyle)
        Text(text = dateText, style = footerStyle)
        Spacer(modifier = Modifier.weight(1f))
        BatteryGlyph(
            percent = batteryPercent,
            charging = charging,
            tint = FooterInk,
            modifier = Modifier.size(width = 26.dp, height = 15.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (charging) "$batteryPercent%+" else "$batteryPercent%",
            style = footerStyle,
        )
    }
}

@Composable
private fun PresenceDot(color: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val strokePx = with(density) { CardStroke.toPx() }
    Canvas(
        modifier = modifier
            .size(PresenceDotSize)
            .xoraForegroundShadow(
                shape = CircleShape,
                offset = CardAssetShadowDp,
                blur = CardAssetShadowDp,
            ),
    ) {
        val radius = (size.minDimension - strokePx) / 2f
        drawCircle(color = color, radius = radius)
        drawCircle(
            brush = ChromeStrokeBrush,
            radius = radius,
            style = Stroke(width = strokePx),
        )
    }
}

@Composable
private fun TrophyMiniGlyph(modifier: Modifier = Modifier) {
    val painter = painterResource(R.drawable.xmb_figma_trophy)
    val stroke = CardStroke
    val dirs = listOf(
        -1 to 0, 1 to 0, 0 to -1, 0 to 1,
        -1 to -1, 1 to -1, -1 to 1, 1 to 1,
    )
    Box(
        modifier = modifier
            .size(width = TrophyGlyphW, height = TrophyGlyphH)
            .graphicsLayer { clip = false }
            .xoraForegroundShadow(
                shape = RoundedCornerShape(4.dp),
                offset = CardAssetShadowDp,
                blur = CardAssetShadowDp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        dirs.forEach { (dx, dy) ->
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = ColorFilter.tint(CardShadowInk),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(width = TrophyGlyphW, height = TrophyGlyphH)
                    .offset(stroke * dx, stroke * dy)
                    .graphicsLayer { clip = false },
            )
        }
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = TrophyGlyphW, height = TrophyGlyphH)
                .graphicsLayer {
                    clip = false
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    drawRect(brush = DullFillBrush, blendMode = BlendMode.SrcIn)
                },
        )
    }
}

@Composable
private fun ClockMiniGlyph(tint: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
        drawCircle(
            color = tint,
            radius = size.minDimension * 0.42f,
            style = stroke,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.5f, size.height * 0.28f),
            end = Offset(size.width * 0.5f, size.height * 0.52f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.5f, size.height * 0.52f),
            end = Offset(size.width * 0.68f, size.height * 0.62f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun WifiGlyph(connected: Boolean, tint: Color, modifier: Modifier = Modifier.size(16.dp)) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
        val cx = size.width / 2f
        val cy = size.height * 0.72f
        val color = if (connected) tint else tint.copy(alpha = 0.35f)
        drawCircle(color = color, radius = size.minDimension * 0.08f, center = Offset(cx, cy))
        if (connected) {
            for (i in 1..3) {
                val r = size.minDimension * (0.18f + i * 0.18f)
                drawArc(
                    color = color.copy(alpha = 1f - i * 0.15f),
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = Size(r * 2, r * 2),
                    style = stroke,
                )
            }
        } else {
            val path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.2f)
                lineTo(size.width * 0.8f, size.height * 0.8f)
            }
            drawPath(path, color = tint.copy(alpha = 0.7f), style = stroke)
        }
    }
}

@Composable
private fun BatteryGlyph(
    percent: Int,
    charging: Boolean,
    tint: Color,
    modifier: Modifier = Modifier.size(width = 20.dp, height = 12.dp),
) {
    Canvas(modifier = modifier) {
        val bodyW = size.width * 0.82f
        val bodyH = size.height * 0.78f
        val top = (size.height - bodyH) / 2f
        drawRoundRect(
            color = tint.copy(alpha = 0.85f),
            topLeft = Offset(0f, top),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx()),
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.85f),
            topLeft = Offset(bodyW, size.height * 0.3f),
            size = Size(size.width - bodyW, size.height * 0.4f),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )
        val fillFrac = (percent / 100f).coerceIn(0f, 1f)
        val pad = 1.5.dp.toPx()
        val fillColor = when {
            charging -> OnlineGreen
            percent <= 20 -> Color(0xFFFF5C6C)
            else -> tint.copy(alpha = 0.75f)
        }
        drawRoundRect(
            color = fillColor,
            topLeft = Offset(pad, top + pad),
            size = Size((bodyW - pad * 2) * fillFrac, bodyH - pad * 2),
            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
        )
    }
}

private fun xoraPresenceColor(systemProfile: SystemProfileCardState): Color {
    if (!systemProfile.xoraNetworkSignedIn) return OnlineGreen
    return when (xoraAppearanceLabel(systemProfile.xoraPresenceMode, systemProfile.xoraNetworkOnline)) {
        "Away" -> AwayAmber
        "Busy" -> BusyRose
        "Online" -> OnlineGreen
        else -> FooterInk
    }
}

private fun formatPoints(score: Int?): String {
    val n = (score ?: 0).coerceAtLeast(0)
    return NumberFormat.getIntegerInstance(Locale.US).format(n)
}

private fun playtimeParts(playTimeMs: Long): Pair<String, String> {
    val hours = TimeUnit.MILLISECONDS.toHours(playTimeMs)
    return if (hours >= 1L) {
        hours.toString() to "HR"
    } else {
        TimeUnit.MILLISECONDS.toMinutes(playTimeMs).toString() to "MIN"
    }
}

private fun readBatteryPercent(context: Context): Int {
    val manager = context.getSystemService(BatteryManager::class.java) ?: return 0
    return manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
}

private fun isCharging(context: Context): Boolean {
    val manager = context.getSystemService(BatteryManager::class.java) ?: return false
    return manager.isCharging
}

private fun isWifiConnected(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
