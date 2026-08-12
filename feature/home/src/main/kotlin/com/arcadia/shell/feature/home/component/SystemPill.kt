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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.SystemFavoriteGame
import com.arcadia.shell.feature.home.SystemPanelRow
import com.arcadia.shell.feature.home.SystemProfileCardState
import com.arcadia.shell.feature.home.buildSystemPanelRows
import com.arcadia.shell.retroachievements.RaCompletionGame
import com.arcadia.shell.retroachievements.RaRecentUnlock
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val ScoreAmber = Color(0xFFFFC24B)
private val OnlineGreen = Color(0xFF37D6A0)
private val FocusRing = Color(0xFF4AE39A)
private val BadgeBorder = Color(0xFFFF9A3C)
private val PanelCharcoal = Color(0xFF1A1C1F)

@Composable
fun SystemPill(
    profile: LocalProfile,
    avatarImageModel: String?,
    raUsername: String?,
    raScore: Int?,
    recentAchievements: List<RaRecentUnlock>,
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
    val maxPanelHeight = LocalConfiguration.current.screenHeightDp.dp * 0.82f

    val systemRows = remember(
        systemProfile.favoritePickerOpen,
        systemProfile.favoritePickerGames,
    ) {
        buildSystemPanelRows(
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

    val glass = rememberGlassTokens(GlassTone.OverMedia)
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
        // Collapsed status pill: Wi‑Fi · time · date · battery · PFP
        Row(
            modifier = Modifier
                .liquidGlass(
                    shape = ArcadiaGlass.PillShape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Standard,
                )
                .clickable(onClick = onToggle)
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WifiGlyph(connected = wifiConnected, tint = glass.content)
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(glass.contentMuted.copy(alpha = 0.35f)),
            )
            Text(
                text = dateShort,
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )
            BatteryGlyph(
                percent = batteryPercent,
                charging = charging,
                tint = glass.content,
            )
            Text(
                text = if (charging) "$batteryPercent%+" else "$batteryPercent%",
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )
            Box {
                ProfileAvatar(
                    displayName = profile.displayName,
                    presetId = profile.avatarPresetId,
                    size = 30.dp,
                    imageModel = avatarImageModel,
                    borderColor = Color.White.copy(alpha = 0.45f),
                )
                TriggerGlyph(
                    letter = "R",
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
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
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .widthIn(max = 380.dp)
                    .heightIn(max = maxPanelHeight)
                    .liquidGlass(
                        shape = RoundedCornerShape(28.dp),
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

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
            size = 72.dp,
            imageModel = avatarImageModel,
            borderColor = Color.White.copy(alpha = 0.55f),
            onClick = onEditProfile,
            modifier = Modifier
                .then(
                    if (editSelected) {
                        Modifier.border(2.dp, FocusRing, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(OnlineGreen),
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = XoraFonts.Title,
                        letterSpacing = XoraFonts.TitleLetterSpacing,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = usernameAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onEditProfile),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrophyMiniGlyph(tint = Color.White.copy(alpha = 0.9f))
                Text(
                    text = "POINTS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = XoraFonts.Title,
                        letterSpacing = 0.08.sp,
                    ),
                    color = Color.White.copy(alpha = 0.72f),
                )
                Text(
                    text = formatPoints(raScore),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = XoraFonts.Title,
                    ),
                    fontWeight = FontWeight.Bold,
                    color = ScoreAmber,
                )
            }
        }
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
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = if (selected) 0.16f else 0.10f))
            .then(
                if (selected) Modifier.border(1.5.dp, FocusRing.copy(alpha = 0.85f), shape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), shape),
            )
            .clickable {
                onSelect()
                if (!editing) onActivate()
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (editing) {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                cursorBrush = SolidColor(FocusRing),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (draft.isBlank()) {
                            Text(
                                text = "Custom status…",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                        inner()
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSave) {
                    Text("Save", color = OnlineGreen)
                }
                if (isCustom || draft.isNotBlank()) {
                    TextButton(onClick = onClear) {
                        Text("Clear", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentlyEarnedStrip(unlocks: List<RaRecentUnlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "RECENTLY EARNED",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = XoraFonts.Title,
                letterSpacing = 0.08.sp,
            ),
            color = Color.White.copy(alpha = 0.78f),
        )
        if (unlocks.isEmpty()) {
            Text(
                text = "No recent unlocks yet",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.45f),
            )
        } else {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.5.dp, BadgeBorder.copy(alpha = 0.9f), RoundedCornerShape(12.dp)),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(platformContext)
                .data(unlock.badgeUrl)
                .crossfade(120)
                .build(),
            contentDescription = unlock.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(11.dp)),
        )
    }
}

@Composable
private fun FavoriteGameSection(
    favorite: SystemFavoriteGame?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "FAVORITE GAME",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = XoraFonts.Title,
                letterSpacing = 0.08.sp,
            ),
            color = Color.White.copy(alpha = 0.78f),
        )
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color.White.copy(alpha = if (selected) 0.14f else 0.06f))
                .then(
                    if (selected) Modifier.border(1.5.dp, FocusRing.copy(alpha = 0.85f), shape)
                    else Modifier,
                )
                .clickable(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (favorite == null) {
                Box(
                    modifier = Modifier
                        .size(width = 108.dp, height = 60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Add favorite",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = "Pick from your RetroAchievements list",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 108.dp, height = 60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                ) {
                    if (favorite.imageIconUrl.isNotBlank()) {
                        AsyncImage(
                            model = favorite.imageIconUrl,
                            contentDescription = favorite.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = favorite.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ClockMiniGlyph(tint = Color.White.copy(alpha = 0.75f))
                        val hours = TimeUnit.MILLISECONDS.toHours(favorite.playTimeMs)
                        Text(
                            text = if (favorite.playTimeMs >= 60_000L) {
                                "$hours"
                            } else {
                                "—"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = XoraFonts.Title,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = OnlineGreen,
                        )
                        if (favorite.playTimeMs >= 60_000L) {
                            Text(
                                text = "HR",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = XoraFonts.Title,
                                ),
                                color = Color.White.copy(alpha = 0.75f),
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WifiGlyph(connected = wifiConnected, tint = Color.White.copy(alpha = 0.85f))
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(12.dp)
                .background(Color.White.copy(alpha = 0.25f)),
        )
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.weight(1f))
        BatteryGlyph(
            percent = batteryPercent,
            charging = charging,
            tint = Color.White.copy(alpha = 0.85f),
        )
        Text(
            text = if (charging) "$batteryPercent%+" else "$batteryPercent%",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun TrophyMiniGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
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
private fun ClockMiniGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(12.dp)) {
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
private fun WifiGlyph(connected: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(16.dp)) {
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
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 20.dp, height = 12.dp)) {
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
