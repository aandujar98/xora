package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.launcher.notifications.ShellNotificationHistoryItem
import com.arcadia.shell.launcher.notifications.toCopy
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val FocusRing = Color(0xFF4AE39A)
private val BellAccent = Color(0xFFFFC857)
private val UnreadDot = Color(0xFFE53935)
private val ClearTint = Color(0xFFFF8A80)

/** Row 0 is Clear all when the list is not empty; items follow at index + 1. */
const val NOTIFICATION_CLEAR_ALL_ROW = 0

/**
 * RT notification center — full list of shell banners (Discord, Steam, trophies, etc.).
 */
@Composable
fun NotificationHistoryPanel(
    open: Boolean,
    items: List<ShellNotificationHistoryItem>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onActivate: () -> Unit,
    onClear: () -> Unit,
    onDismissItem: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val listState = rememberLazyListState()
    val rowCount = if (items.isEmpty()) 0 else items.size + 1

    LaunchedEffect(selectedIndex, rowCount, open) {
        if (!open || rowCount == 0) return@LaunchedEffect
        listState.animateScrollToItem(selectedIndex.coerceIn(0, rowCount - 1))
    }

    AnimatedVisibility(
        visible = open,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) +
            scaleIn(arcadiaTween(ArcadiaMotion.Medium), initialScale = 0.96f),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) +
            scaleOut(arcadiaTween(ArcadiaMotion.Fast), targetScale = 0.98f),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .fillMaxHeight(0.82f)
                    .liquidGlass(
                        shape = ArcadiaGlass.PanelShape,
                        tone = GlassTone.Surface,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .clickable(enabled = false, onClick = {})
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BellIcon(tint = BellAccent, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = glass.content,
                        )
                        Text(
                            text = if (items.isEmpty()) {
                                "No notifications yet"
                            } else {
                                "${items.size} recent · A open · Y clear all · B close"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.contentMuted,
                        )
                    }
                    if (items.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(ClearTint.copy(alpha = 0.18f))
                                .clickable(onClick = onClear)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Clear all",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = ClearTint,
                            )
                        }
                    }
                }

                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Friend messages, trophies, and downloads will show up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.contentMuted,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                    ) {
                        item(key = "clear-all") {
                            ClearAllRow(
                                selected = selectedIndex == NOTIFICATION_CLEAR_ALL_ROW,
                                onClick = {
                                    onSelectIndex(NOTIFICATION_CLEAR_ALL_ROW)
                                    onClear()
                                },
                            )
                        }
                        itemsIndexed(
                            items = items,
                            key = { _, item -> item.notification.id },
                        ) { index, item ->
                            val rowIndex = index + 1
                            val selected = rowIndex == selectedIndex
                            NotificationHistoryRow(
                                item = item,
                                selected = selected,
                                muted = glass.contentMuted,
                                onClick = {
                                    onSelectIndex(rowIndex)
                                    onActivate()
                                },
                                onDismiss = { onDismissItem(item.notification.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearAllRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) ClearTint.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.06f),
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, ClearTint.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Clear all notifications",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Y",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = ClearTint,
        )
    }
}

@Composable
private fun NotificationHistoryRow(
    item: ShellNotificationHistoryItem,
    selected: Boolean,
    muted: Color,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = item.notification.toCopy()
    val time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
        .format(Date(item.receivedAtMs))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) FocusRing.copy(alpha = 0.18f)
                else Color.White.copy(alpha = 0.06f),
            )
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, FocusRing.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (!item.read) UnreadDot else Color.Transparent),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = copy.category,
                style = MaterialTheme.typography.labelSmall,
                color = BellAccent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = copy.body,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (copy.subtitle.isNotBlank()) {
                Text(
                    text = copy.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun BellIcon(
    tint: Color,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize(0.92f)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.5f, h * 0.12f)
                cubicTo(w * 0.28f, h * 0.12f, w * 0.22f, h * 0.32f, w * 0.22f, h * 0.48f)
                lineTo(w * 0.22f, h * 0.62f)
                lineTo(w * 0.12f, h * 0.74f)
                lineTo(w * 0.88f, h * 0.74f)
                lineTo(w * 0.78f, h * 0.62f)
                lineTo(w * 0.78f, h * 0.48f)
                cubicTo(w * 0.78f, h * 0.32f, w * 0.72f, h * 0.12f, w * 0.5f, h * 0.12f)
                close()
            }
            drawPath(path, color = tint)
            drawCircle(
                color = tint,
                radius = w * 0.07f,
                center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.86f),
            )
            drawPath(path, color = tint.copy(alpha = 0.35f), style = Stroke(width = 1.5f))
        }
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(UnreadDot)
                    .border(1.dp, Color(0xFF0C1524), CircleShape),
            )
        }
    }
}

@Composable
fun NotificationDot(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .size(11.dp)
            .clip(CircleShape)
            .background(UnreadDot)
            .border(1.5.dp, Color(0xFF0C1524), CircleShape),
    )
}
