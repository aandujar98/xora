package com.arcadia.shell.feature.home.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.datastore.AvatarSource
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.xoranetwork.XoraPresenceMode
import com.arcadia.shell.xoranetwork.parseXoraPresenceMode

private const val NAME_MAX = 24

// Same presence palette the Friends card uses, so a status reads identically in both places.
private val StatusOnline = Color(0xFF37D6A0)
private val StatusAway = Color(0xFFFFC24B)
private val StatusBusy = Color(0xFFFF5C6C)
private val StatusOffline = Color(0xFF9AA3AD)

private val ChipShape = RoundedCornerShape(12.dp)
private val SelectedFill = Color.White.copy(alpha = 0.18f)
private val RestFill = Color.White.copy(alpha = 0.06f)
private val SelectedEdge = Color.White
private val RestEdge = Color.White.copy(alpha = 0.18f)
private val MutedInk = Color.White.copy(alpha = 0.62f)

/**
 * One avatar source, mapped 1:1 onto [AvatarSource] so the chip row and the stored source can
 * never disagree. [iconRes] of null draws the live colour-preset swatch instead of a glyph.
 */
private data class AvatarSourceOption(
    val source: AvatarSource,
    val label: String,
    val iconRes: Int?,
    val available: Boolean,
    val onPick: () -> Unit,
)

/**
 * Profile display-name / avatar editor.
 *
 * Overlay (not AlertDialog) so it can open on a secondary [android.app.Presentation] without a
 * nested window. Photo picks are requested via [onRequestPhoto]; Activity Result launchers live
 * only in the Activity-rooted shell.
 *
 * Header and footer are pinned and only the middle scrolls, so Save stays reachable on short
 * landscape / TV layouts instead of hiding below a long column of buttons.
 */
@Composable
fun ProfileEditSheet(
    profile: LocalProfile,
    avatarImageModel: String?,
    raConfigured: Boolean,
    discordLinked: Boolean,
    onDismiss: () -> Unit,
    onSave: (displayName: String, avatarPresetId: String) -> Unit,
    onSelectAvatarPreset: (presetId: String) -> Unit,
    onRequestPhoto: () -> Unit,
    onUseRaAvatar: () -> Unit,
    onUseDiscordAvatar: () -> Unit,
    onUseXoraAvatar: () -> Unit = {},
    onClearAvatar: () -> Unit,
    xoraSignedIn: Boolean = false,
    onXoraPresenceMode: (XoraPresenceMode) -> Unit = {},
) {
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var presetId by remember(profile.avatarPresetId) { mutableStateOf(profile.avatarPresetId) }

    BackHandler(onBack = onDismiss)

    val sources = listOf(
        AvatarSourceOption(AvatarSource.Default, "Colour", null, true, onClearAvatar),
        AvatarSourceOption(
            AvatarSource.Local,
            "Photo",
            R.drawable.xmb_figma_photo,
            true,
            onRequestPhoto,
        ),
        AvatarSourceOption(
            AvatarSource.XoraNetwork,
            "XOrA",
            R.drawable.ic_brand_xora,
            xoraSignedIn,
            onUseXoraAvatar,
        ),
        AvatarSourceOption(
            AvatarSource.RetroAchievements,
            "RA",
            R.drawable.xmb_figma_trophy,
            raConfigured,
            onUseRaAvatar,
        ),
        AvatarSourceOption(
            AvatarSource.Discord,
            "Discord",
            R.drawable.ic_brand_discord,
            discordLinked,
            onUseDiscordAvatar,
        ),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .heightIn(max = 620.dp)
                .xoraModalGlass(ArcadiaGlass.CardShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(
                name = name,
                presetId = presetId,
                avatarImageModel = avatarImageModel,
                source = profile.avatarSource,
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Section(label = "Profile picture") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        sources.forEach { option ->
                            SourceChip(
                                option = option,
                                selected = option.source == profile.avatarSource,
                                swatch = avatarPreset(presetId).color,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    unavailableHint(sources)?.let { hint ->
                        XoraSecondaryText(
                            text = hint,
                            fontSize = 12.sp,
                            fillColor = MutedInk,
                        )
                    }
                }

                Section(label = "Colour preset") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AvatarPresets.forEach { preset ->
                            val active = profile.avatarSource == AvatarSource.Default &&
                                preset.id == presetId
                            ProfileAvatar(
                                displayName = name.ifBlank { "P" },
                                presetId = preset.id,
                                size = 44.dp,
                                onClick = {
                                    presetId = preset.id
                                    onSelectAvatarPreset(preset.id)
                                },
                                borderColor = if (active) SelectedEdge else RestEdge,
                            )
                        }
                    }
                }

                Section(label = "Display name") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(NAME_MAX) },
                        singleLine = true,
                        supportingText = {
                            Text(text = "${name.length}/$NAME_MAX", color = MutedInk)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (xoraSignedIn) {
                    val mode = parseXoraPresenceMode(profile.xoraPresenceMode)
                    Section(label = "XOrA Network status") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            XoraPresenceMode.entries.forEach { entry ->
                                PresenceChip(
                                    mode = entry,
                                    selected = entry == mode,
                                    onClick = { onXoraPresenceMode(entry) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        XoraSecondaryText(
                            text = presenceDescription(mode),
                            fontSize = 12.sp,
                            fillColor = MutedInk,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(text = "Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(name.trim(), presetId)
                        onDismiss()
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(text = "Save")
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    name: String,
    presetId: String,
    avatarImageModel: String?,
    source: AvatarSource,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProfileAvatar(
            displayName = name.ifBlank { "P" },
            presetId = presetId,
            size = 76.dp,
            imageModel = avatarImageModel,
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            XoraTitleText(text = "Customize Profile", fontSize = 22.sp, maxLines = 1)
            XoraSecondaryText(
                text = sourceLabel(source),
                fontSize = 13.sp,
                fillColor = MutedInk,
                maxLines = 1,
            )
        }
    }
}

/** Section label plus its content, so every group is spaced and titled the same way. */
@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        XoraSecondaryText(
            text = label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fillColor = MutedInk,
        )
        content()
    }
}

@Composable
private fun RowScope.SourceChip(
    option: AvatarSourceOption,
    selected: Boolean,
    swatch: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ChipShape)
            .background(if (selected) SelectedFill else RestFill)
            .border(
                width = if (selected) 2.dp else 1.5.dp,
                color = if (selected) SelectedEdge else RestEdge,
                shape = ChipShape,
            )
            .then(
                if (option.available) Modifier.clickable(onClick = option.onPick) else Modifier,
            )
            .alpha(if (option.available) 1f else 0.4f)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (option.iconRes == null) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(1.5.dp, RestEdge, CircleShape),
            )
        } else {
            Icon(
                painter = painterResource(option.iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        XoraSecondaryText(
            text = option.label,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun RowScope.PresenceChip(
    mode: XoraPresenceMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(ChipShape)
            .background(if (selected) SelectedFill else RestFill)
            .border(
                width = if (selected) 2.dp else 1.5.dp,
                color = if (selected) SelectedEdge else RestEdge,
                shape = ChipShape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(presenceColor(mode)),
        )
        XoraSecondaryText(
            text = presenceLabel(mode),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Offline is [XoraPresenceMode.Invisible]: still signed in, just not advertising presence. */
private fun presenceLabel(mode: XoraPresenceMode): String = when (mode) {
    XoraPresenceMode.Online -> "Online"
    XoraPresenceMode.Away -> "Away"
    XoraPresenceMode.Busy -> "Busy"
    XoraPresenceMode.Invisible -> "Offline"
}

private fun presenceColor(mode: XoraPresenceMode): Color = when (mode) {
    XoraPresenceMode.Online -> StatusOnline
    XoraPresenceMode.Away -> StatusAway
    XoraPresenceMode.Busy -> StatusBusy
    XoraPresenceMode.Invisible -> StatusOffline
}

private fun presenceDescription(mode: XoraPresenceMode): String = when (mode) {
    XoraPresenceMode.Online -> "Friends see you online, and what you're playing."
    XoraPresenceMode.Away -> "Friends see you as Away."
    XoraPresenceMode.Busy -> "Friends see you as Busy."
    XoraPresenceMode.Invisible -> "You stay signed in, but appear offline."
}

private fun sourceLabel(source: AvatarSource): String = when (source) {
    AvatarSource.Default -> "Colour preset"
    AvatarSource.Local -> "Custom photo"
    AvatarSource.RetroAchievements -> "RetroAchievements"
    AvatarSource.Discord -> "Discord"
    AvatarSource.XoraNetwork -> "XOrA Network"
}

/**
 * Folds what used to be three always-on paragraphs into one line naming only the sources that
 * are actually unavailable, or nothing at all once everything is linked.
 */
private fun unavailableHint(sources: List<AvatarSourceOption>): String? {
    val names = sources.filter { !it.available }.map { it.label }
    if (names.isEmpty()) return null
    val list = if (names.size == 1) {
        names.first()
    } else {
        names.dropLast(1).joinToString(", ") + " or " + names.last()
    }
    return "Sign in to $list to use those avatars."
}
