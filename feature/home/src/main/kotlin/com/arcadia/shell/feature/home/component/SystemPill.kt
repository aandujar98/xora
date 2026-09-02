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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraOutlinedText
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.xoraForegroundShadow
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
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val ScoreAmber = Color(0xFFFFA22B)
private val OnlineGreen = Color(0xFF37D6A0)
private val AwayAmber = Color(0xFFFFC24B)
private val BusyRose = Color(0xFFFF5C6C)
private val FocusRing = Color(0xFF4AE39A)
private val BadgeBorder = Color(0xFFF0A030)

/** Frosted plate rim, matching the expanded RetroAchievements card. */
private val CardEdge = Color.White.copy(alpha = 0.25f)
private val OutlineInk = Color(0xFF10202A)
private val BubbleFill = Color(0xFFE8EAEA)
private val BubbleInk = Color(0xFF4A4F52)
private val PlaytimeGreen = Color(0xFF5FE06A)
private val FooterInk = Color(0xFF9FB0B8)

/** Collapsed RT chrome is the profile picture alone, tucked into the corner. */
private val CollapsedAvatarSize = 88.dp

/** Figma crops the disc on both screen edges; this clears the pane padding to get there. */
private val CollapsedAvatarBleed = 24.dp

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
    val maxPanelHeight = LocalConfiguration.current.screenHeightDp.dp * 0.90f

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

    Column(
        modifier = modifier.widthIn(max = if (expanded) 380.dp else 300.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Collapsed status chrome hides while the card is open; RT / B restores it.
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)),
            exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)),
        ) {
            // Just the avatar, pushed past the pane padding so it tucks into the screen corner;
            // status readouts live in the expanded card footer. Soap-bubble overlay matches
            // Figma Make (165° + soft-light) and must not steal the avatar's click target.
            val userBubbleGlass = ImageBitmap.imageResource(R.drawable.vita_bubble_glass)
            ProfileAvatar(
                displayName = profile.displayName,
                presetId = profile.avatarPresetId,
                size = CollapsedAvatarSize,
                imageModel = avatarImageModel,
                borderColor = Color.White.copy(alpha = 0.9f),
                onClick = onToggle,
                modifier = Modifier
                    .offset(x = CollapsedAvatarBleed, y = -CollapsedAvatarBleed)
                    .xoraForegroundShadow(CircleShape)
                    .graphicsLayer { clip = false }
                    .drawWithContent {
                        drawContent()
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

        AnimatedVisibility(
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
            val cardShape = RoundedCornerShape(30.dp)
            val cardScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 380.dp)
                    .heightIn(max = maxPanelHeight)
                    .xoraForegroundShadow(cardShape)
                    .liquidGlass(
                        shape = cardShape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .border(1.5.dp, CardEdge, cardShape)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(cardScroll)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        profile = profile,
                        avatarImageModel = avatarImageModel,
                        raScore = raScore,
                        systemProfile = systemProfile,
                        statusSelected = systemRows.getOrNull(selectedRowIndex) is SystemPanelRow.Status,
                        editSelected = systemRows.getOrNull(selectedRowIndex) is SystemPanelRow.EditProfile,
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
                        onEditProfile = {
                            val idx = systemRows.indexOfFirst { it is SystemPanelRow.EditProfile }
                            if (idx >= 0) {
                                onSelectRow(idx)
                                onActivateRow(idx)
                            }
                        },
                        onStatusDraftChange = onStatusDraftChange,
                        onSaveCustomStatus = onSaveCustomStatus,
                        onClearCustomStatus = onClearCustomStatus,
                    )

                    RecentlyEarnedStrip(unlocks = recentAchievements)

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

                ProfileCardFooter(
                    wifiConnected = wifiConnected,
                    timeText = timeText,
                    dateText = dateShort,
                    batteryPercent = batteryPercent,
                    charging = charging,
                )
                }
            }
        }
    }
}

@Composable
private fun ProfileCardHeader(
    displayName: String,
    usernameAccent: Color,
    profile: LocalProfile,
    avatarImageModel: String?,
    raScore: Int?,
    systemProfile: SystemProfileCardState,
    statusSelected: Boolean,
    editSelected: Boolean,
    onSelectStatus: () -> Unit,
    onActivateStatus: () -> Unit,
    onEditProfile: () -> Unit,
    onStatusDraftChange: (String) -> Unit,
    onSaveCustomStatus: () -> Unit,
    onClearCustomStatus: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ProfileAvatar(
            displayName = profile.displayName,
            presetId = profile.avatarPresetId,
            size = 78.dp,
            imageModel = avatarImageModel,
            borderColor = Color.White.copy(alpha = 0.85f),
            onClick = onEditProfile,
            modifier = Modifier
                .then(
                    if (editSelected) {
                        Modifier.border(2.5.dp, FocusRing, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )

        Column(
            modifier = Modifier.weight(1f),
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
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                val presenceColor = xoraPresenceColor(systemProfile)
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(presenceColor)
                        .border(1.5.dp, OutlineInk.copy(alpha = 0.55f), CircleShape),
                )
                XoraOutlinedText(
                    text = displayName,
                    fontFamily = XoraFonts.Title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    fillColor = usernameAccent,
                    outlineColor = OutlineInk,
                    letterSpacing = XoraFonts.TitleLetterSpacing,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(onClick = onEditProfile),
                )
            }
            if (systemProfile.xoraNetworkSignedIn) {
                val presenceColor = xoraPresenceColor(systemProfile)
                val presenceLabel = xoraAppearanceLabel(
                    systemProfile.xoraPresenceMode,
                    systemProfile.xoraNetworkOnline,
                ).uppercase()
                Text(
                    text = "$presenceLabel · XOrA NETWORK",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = presenceColor,
                    maxLines = 1,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                TrophyMiniGlyph(tint = Color.White, modifier = Modifier.size(20.dp))
                XoraOutlinedText(
                    text = "POINTS",
                    fontFamily = XoraFonts.Title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    fillColor = Color.White,
                    outlineColor = OutlineInk,
                    letterSpacing = XoraFonts.TitleLetterSpacing,
                    maxLines = 1,
                )
                XoraOutlinedText(
                    text = formatPoints(raScore),
                    fontFamily = XoraFonts.Title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    fillColor = ScoreAmber,
                    outlineColor = OutlineInk,
                    letterSpacing = XoraFonts.TitleLetterSpacing,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Section label — blocky white with the XOrA dark outline. */
@Composable
private fun CardSectionLabel(text: String) {
    XoraOutlinedText(
        text = text,
        fontFamily = XoraFonts.Title,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        fillColor = Color.White,
        outlineColor = OutlineInk,
        letterSpacing = XoraFonts.TitleLetterSpacing,
        maxLines = 1,
    )
}

/** Tail under the status bubble, pointing back down toward the avatar. */
@Composable
private fun BubbleTail(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 18.dp, height = 10.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, 0f)
            lineTo(size.width, 0f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = BubbleFill)
    }
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
) {
    val shape = RoundedCornerShape(16.dp)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) {
            delay(16)
            bringIntoViewRequester.bringIntoView()
        }
    }
    Column(horizontalAlignment = Alignment.Start) {
        Column(
            modifier = Modifier
                .bringIntoViewRequester(bringIntoViewRequester)
                .clip(shape)
                .background(BubbleFill)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, FocusRing, shape)
                    } else {
                        Modifier
                    },
                )
                .clickable {
                    onSelect()
                    if (!editing) onActivate()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (editing) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = BubbleInk),
                    cursorBrush = SolidColor(BubbleInk),
                    modifier = Modifier.widthIn(min = 160.dp),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isBlank()) {
                                Text(
                                    text = "Custom status…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BubbleInk.copy(alpha = 0.5f),
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = BubbleInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        BubbleTail(modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun RecentlyEarnedStrip(unlocks: List<RaRecentUnlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardSectionLabel("RECENTLY EARNED")
        if (unlocks.isEmpty()) {
            Text(
                text = "No recent unlocks yet",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f),
            )
        } else {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                unlocks.take(8).forEach { unlock ->
                    AchievementBadge(unlock = unlock)
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(unlock: RaRecentUnlock) {
    val platformContext = LocalPlatformContext.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .size(56.dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CardSectionLabel("FAVORITE GAME")
        val artShape = RoundedCornerShape(10.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (selected) {
                        Modifier.border(2.dp, FocusRing, RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onClick)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 150.dp, height = 88.dp)
                    .clip(artShape)
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.55f), artShape),
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = XoraFonts.Secondary,
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (favorite == null) {
                    Text(
                        text = "Pick from your RetroAchievements list",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        ClockMiniGlyph(tint = Color.White, modifier = Modifier.size(18.dp))
                        val hours = TimeUnit.MILLISECONDS.toHours(favorite.playTimeMs)
                        XoraOutlinedText(
                            text = if (favorite.playTimeMs >= 60_000L) "$hours" else "—",
                            fontFamily = XoraFonts.Title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            fillColor = PlaytimeGreen,
                            outlineColor = OutlineInk,
                            letterSpacing = XoraFonts.TitleLetterSpacing,
                            maxLines = 1,
                        )
                        if (favorite.playTimeMs >= 60_000L) {
                            XoraOutlinedText(
                                text = "HR",
                                fontFamily = XoraFonts.Title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fillColor = Color.White,
                                outlineColor = OutlineInk,
                                letterSpacing = XoraFonts.TitleLetterSpacing,
                                maxLines = 1,
                            )
                        }
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
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WifiGlyph(
            connected = wifiConnected,
            tint = FooterInk,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = timeText,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = XoraFonts.Secondary),
            color = FooterInk,
        )
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .height(18.dp)
                .background(FooterInk.copy(alpha = 0.45f)),
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = XoraFonts.Secondary),
            color = FooterInk,
        )
        Spacer(modifier = Modifier.weight(1f))
        BatteryGlyph(
            percent = batteryPercent,
            charging = charging,
            tint = FooterInk,
            modifier = Modifier.size(width = 26.dp, height = 15.dp),
        )
        Text(
            text = if (charging) "$batteryPercent%+" else "$batteryPercent%",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = XoraFonts.Secondary),
            color = FooterInk,
        )
    }
}

@Composable
private fun TrophyMiniGlyph(tint: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cup = Path().apply {
            moveTo(w * 0.28f, h * 0.12f)
            lineTo(w * 0.72f, h * 0.12f)
            quadraticTo(w * 0.78f, h * 0.38f, w * 0.58f, h * 0.55f)
            lineTo(w * 0.42f, h * 0.55f)
            quadraticTo(w * 0.22f, h * 0.38f, w * 0.28f, h * 0.12f)
            close()
        }
        drawPath(cup, color = tint)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.44f, h * 0.55f),
            size = Size(w * 0.12f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.04f),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.30f, h * 0.78f),
            size = Size(w * 0.40f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.04f),
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
    if (score == null) return "—"
    return "%,d".format(Locale.US, score)
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
