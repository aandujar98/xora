package com.arcadia.shell.feature.home.component

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.SystemPanelRow
import com.arcadia.shell.feature.home.buildSystemPanelRows
import com.arcadia.shell.model.Game
import com.arcadia.shell.retroachievements.RaRecentUnlock
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val ScoreGreen = Color(0xFF37D6A0)
private val FocusRing = Color(0xFF4AE39A)

@Composable
fun SystemPill(
    profile: LocalProfile,
    avatarImageModel: String?,
    raScore: Int?,
    recentAchievements: List<RaRecentUnlock>,
    jumpBackGames: List<Game>,
    expanded: Boolean,
    selectedRowIndex: Int,
    notificationUnreadCount: Int = 0,
    onToggle: () -> Unit,
    onSelectRow: (Int) -> Unit,
    onActivateRow: (Int?) -> Unit,
    onOpenNotifications: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    var batteryPercent by remember { mutableIntStateOf(readBatteryPercent(context)) }
    var charging by remember { mutableStateOf(isCharging(context)) }
    var wifiConnected by remember { mutableStateOf(isWifiConnected(context)) }
    var brightness by remember { mutableFloatStateOf(readBrightness(context)) }
    val canWriteBrightness = remember { Settings.System.canWrite(context) }
    val listState = rememberLazyListState()
    val maxPanelHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f

    val systemRows = remember(jumpBackGames) {
        buildSystemPanelRows(jumpBackGames.map { it.id })
    }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            wifiConnected = isWifiConnected(context)
            delay(15_000)
        }
    }

    LaunchedEffect(selectedRowIndex, expanded, systemRows.size) {
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
        android.text.format.DateFormat.format("MM/dd", now).toString()
    }
    val dateMedium = remember(now) {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(now)
    }

    val glass = rememberGlassTokens(GlassTone.OverMedia)

    Column(
        modifier = modifier.widthIn(max = if (expanded) 360.dp else 300.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Collapsed RT chrome hides while the panel is open; Back / RT restores it.
        // Soft rounded bar (not 50% PillShape) so the in-pill circle isn't clipped oval.
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + scaleIn(
                animationSpec = arcadiaTween(ArcadiaMotion.Medium),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.9f, 0f),
            ),
            exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + scaleOut(
                animationSpec = arcadiaTween(ArcadiaMotion.Fast),
                targetScale = 0.96f,
                transformOrigin = TransformOrigin(0.9f, 0f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .liquidGlass(
                        shape = RoundedCornerShape(20.dp),
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Standard,
                    )
                    .clickable(onClick = onToggle)
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
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
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenNotifications),
                    contentAlignment = Alignment.Center,
                ) {
                    BellIcon(
                        tint = Color(0xFFFFC857),
                        showBadge = notificationUnreadCount > 0,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box {
                    ProfileAvatar(
                        displayName = profile.displayName,
                        presetId = profile.avatarPresetId,
                        size = 40.dp,
                        imageModel = avatarImageModel,
                        borderColor = Color.White.copy(alpha = 0.45f),
                    )
                    NotificationDot(
                        visible = notificationUnreadCount > 0,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp, end = 1.dp),
                    )
                    TriggerGlyph(
                        letter = "RT",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 0.dp),
                    )
                }
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
                    .widthIn(max = 360.dp)
                    .heightIn(max = maxPanelHeight)
                    .liquidGlass(
                        shape = ArcadiaGlass.PanelShape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                SystemProfileHeader(
                    profile = profile,
                    avatarImageModel = avatarImageModel,
                    raScore = raScore,
                    showNotificationDot = notificationUnreadCount > 0,
                    editSelected = systemRows.getOrNull(selectedRowIndex) is SystemPanelRow.EditProfile,
                    onEditProfile = {
                        val idx = systemRows.indexOfFirst { it is SystemPanelRow.EditProfile }
                        if (idx >= 0) {
                            onSelectRow(idx)
                            onActivateRow(idx)
                        }
                    },
                    onOpenNotifications = onOpenNotifications,
                    glassContent = glass.content,
                    glassMuted = glass.contentMuted,
                )

                Text(
                    text = "$timeText · $dateMedium",
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.contentMuted.copy(alpha = 0.65f),
                )

                RecentAchievementStrip(
                    unlocks = recentAchievements,
                    muted = glass.contentMuted,
                )

                if (jumpBackGames.isNotEmpty()) {
                    Text(
                        text = "Jump back into",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = glass.contentMuted,
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (maxPanelHeight - 220.dp).coerceAtLeast(140.dp)),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                ) {
                    itemsIndexed(
                        items = systemRows,
                        key = { _, row ->
                            when (row) {
                                is SystemPanelRow.JumpBack -> "jump_${row.gameId}"
                                else -> row::class.simpleName.orEmpty()
                            }
                        },
                    ) { index, row ->
                        val selected = index == selectedRowIndex
                        when (row) {
                            SystemPanelRow.Notifications -> {
                                val shape = RoundedCornerShape(14.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .then(
                                            if (selected) {
                                                Modifier
                                                    .background(Color.White.copy(alpha = 0.16f))
                                                    .border(
                                                        1.5.dp,
                                                        FocusRing.copy(alpha = 0.85f),
                                                        shape,
                                                    )
                                            } else {
                                                Modifier.background(Color.White.copy(alpha = 0.06f))
                                            },
                                        )
                                        .clickable {
                                            onSelectRow(index)
                                            onActivateRow(index)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    BellIcon(
                                        tint = Color(0xFFFFC857),
                                        showBadge = notificationUnreadCount > 0,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Notifications",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = glass.content,
                                        )
                                        Text(
                                            text = if (notificationUnreadCount > 0) {
                                                "$notificationUnreadCount unread"
                                            } else {
                                                "History"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = glass.contentMuted,
                                        )
                                    }
                                }
                            }
                            SystemPanelRow.EditProfile -> Unit // header button handles this
                            is SystemPanelRow.JumpBack -> {
                                val game = jumpBackGames.firstOrNull { it.id == row.gameId }
                                if (game != null) {
                                    JumpBackRow(
                                        game = game,
                                        selected = selected,
                                        onClick = {
                                            onSelectRow(index)
                                            onActivateRow(index)
                                        },
                                    )
                                }
                            }
                            SystemPanelRow.Brightness -> {
                                if (index == systemRows.indexOfFirst { it is SystemPanelRow.Brightness }) {
                                    Text(
                                        text = "Quick settings",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = glass.contentMuted,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                    )
                                }
                                BrightnessRow(
                                    brightness = brightness,
                                    canWrite = canWriteBrightness,
                                    selected = selected,
                                    onBrightnessChange = { value ->
                                        brightness = value
                                        writeBrightness(context, value)
                                    },
                                    onSelect = { onSelectRow(index) },
                                    onActivate = { onActivateRow(index) },
                                    onRequestWritePermission = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                data = android.net.Uri.parse(
                                                    "package:${context.packageName}",
                                                )
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                    },
                                )
                            }
                            SystemPanelRow.Wifi -> SettingsRow(
                                title = "Wi‑Fi",
                                subtitle = if (wifiConnected) "Connected — open settings" else "Not connected — open settings",
                                selected = selected,
                                onClick = {
                                    onSelectRow(index)
                                    onActivateRow(index)
                                },
                            )
                            SystemPanelRow.Bluetooth -> SettingsRow(
                                title = "Bluetooth",
                                subtitle = "Open Bluetooth settings",
                                selected = selected,
                                onClick = {
                                    onSelectRow(index)
                                    onActivateRow(index)
                                },
                            )
                            SystemPanelRow.AllSettings -> {
                                val shape = ArcadiaGlass.ChipShape
                                TextButton(
                                    onClick = {
                                        onSelectRow(index)
                                        onActivateRow(index)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(shape)
                                        .then(
                                            if (selected) {
                                                Modifier
                                                    .background(Color.White.copy(alpha = 0.16f))
                                                    .border(
                                                        1.5.dp,
                                                        FocusRing.copy(alpha = 0.85f),
                                                        shape,
                                                    )
                                            } else {
                                                Modifier
                                            },
                                        ),
                                ) {
                                    Text(text = "Settings")
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "RT toggle · U/D · A activate · B close",
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.contentMuted.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun SystemProfileHeader(
    profile: LocalProfile,
    avatarImageModel: String?,
    raScore: Int?,
    showNotificationDot: Boolean,
    editSelected: Boolean,
    onEditProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    glassContent: Color,
    glassMuted: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            ProfileAvatar(
                displayName = profile.displayName,
                presetId = profile.avatarPresetId,
                size = 72.dp,
                imageModel = avatarImageModel,
                borderColor = Color.White.copy(alpha = 0.4f),
            )
            NotificationDot(
                visible = showNotificationDot,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onOpenNotifications),
            contentAlignment = Alignment.Center,
        ) {
            BellIcon(
                tint = Color(0xFFFFC857),
                showBadge = showNotificationDot,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = glassContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (raScore != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ScoreGreen.copy(alpha = 0.22f))
                            .border(1.dp, ScoreGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "★ $raScore",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ScoreGreen,
                        )
                    }
                }
            }
            val shape = RoundedCornerShape(12.dp)
            TextButton(
                onClick = onEditProfile,
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (editSelected) {
                            Modifier
                                .background(Color.White.copy(alpha = 0.14f))
                                .border(1.5.dp, FocusRing.copy(alpha = 0.85f), shape)
                        } else {
                            Modifier.background(Color.White.copy(alpha = 0.08f))
                        },
                    ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "Edit profile",
                    style = MaterialTheme.typography.labelMedium,
                    color = glassContent,
                )
            }
        }
    }
}

@Composable
private fun RecentAchievementStrip(
    unlocks: List<RaRecentUnlock>,
    muted: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Recent Achievement",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = muted,
        )
        if (unlocks.isEmpty()) {
            Text(
                text = "No recent unlocks",
                style = MaterialTheme.typography.bodySmall,
                color = muted.copy(alpha = 0.55f),
            )
        } else {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                unlocks.take(10).forEach { unlock ->
                    AchievementBadge(unlock = unlock)
                }
            }
        }
    }
}

@Composable
private fun AchievementBadge(unlock: RaRecentUnlock) {
    val platformContext = LocalPlatformContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(52.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(unlock.badgeUrl)
                    .crossfade(120)
                    .build(),
                contentDescription = unlock.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = unlock.title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun JumpBackRow(
    game: Game,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val shape = ArcadiaGlass.ChipShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, FocusRing.copy(alpha = 0.85f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            ArtworkImage(
                path = game.gridArt ?: game.boxArtPath,
                contentDescription = game.title,
                fallbackText = game.title.take(1),
                contentScale = ContentScale.Crop,
                decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = glass.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = game.platform.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = glass.contentMuted.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrightnessRow(
    brightness: Float,
    canWrite: Boolean,
    selected: Boolean,
    onBrightnessChange: (Float) -> Unit,
    onSelect: () -> Unit,
    onActivate: () -> Unit,
    onRequestWritePermission: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val shape = ArcadiaGlass.ChipShape
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, FocusRing.copy(alpha = 0.85f), shape)
                } else {
                    Modifier
                },
            )
            .clickable {
                onSelect()
                onActivate()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Brightness",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = glass.content,
        )
        if (canWrite) {
            Slider(
                value = brightness,
                onValueChange = { value ->
                    onSelect()
                    onBrightnessChange(value)
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                text = "Allow brightness control, or open Display settings with A",
                style = MaterialTheme.typography.labelSmall,
                color = glass.contentMuted.copy(alpha = 0.65f),
            )
            TextButton(onClick = onRequestWritePermission) {
                Text(text = "Allow brightness control")
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val shape = ArcadiaGlass.ChipShape
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f),
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, FocusRing.copy(alpha = 0.85f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = glass.content,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = glass.contentMuted.copy(alpha = 0.65f),
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
            charging -> ScoreGreen
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

private fun readBrightness(context: Context): Float {
    val raw = Settings.System.getInt(
        context.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        128,
    )
    return (raw / 255f).coerceIn(0f, 1f)
}

private fun writeBrightness(context: Context, value: Float) {
    if (!Settings.System.canWrite(context)) return
    val level = (value * 255f).toInt().coerceIn(1, 255)
    runCatching {
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            level,
        )
    }
}
