package com.arcadia.shell.feature.home.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadia.shell.datastore.AvatarSource
import com.arcadia.shell.datastore.LocalProfile
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.rememberGlassTokens

/**
 * Profile display-name / avatar editor.
 *
 * Overlay (not AlertDialog) so it can open on a secondary [android.app.Presentation] without a
 * nested window. Photo picks are requested via [onRequestPhoto]; Activity Result launchers live
 * only in the Activity-rooted shell.
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
    onXoraPresenceMode: (com.arcadia.shell.xoranetwork.XoraPresenceMode) -> Unit = {},
) {
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var presetId by remember(profile.avatarPresetId) { mutableStateOf(profile.avatarPresetId) }

    val glass = rememberGlassTokens(GlassTone.Surface)
    BackHandler(onBack = onDismiss)

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
                .heightIn(max = 560.dp)
                .clip(ArcadiaGlass.CardShape)
                .background(glass.tintStrong)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Customize profile",
                color = glass.content,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileAvatar(
                    displayName = name.ifBlank { "P" },
                    presetId = presetId,
                    size = 72.dp,
                    imageModel = avatarImageModel,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Profile picture", color = glass.content)
                    Text(
                        text = when (profile.avatarSource) {
                            AvatarSource.Default -> "Colour preset"
                            AvatarSource.Local -> "Custom photo"
                            AvatarSource.RetroAchievements -> "RetroAchievements"
                            AvatarSource.Discord -> "Discord"
                            AvatarSource.XoraNetwork -> "XOrA Network"
                        },
                        color = Color.White.copy(alpha = 0.55f),
                    )
                }
            }

            // Primary actions — always visible at the top so landscape / TV layouts don't hide them.
            Button(
                onClick = onRequestPhoto,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Choose photo")
            }

            OutlinedButton(
                onClick = onUseXoraAvatar,
                enabled = xoraSignedIn,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Use XOrA Network avatar")
            }
            if (!xoraSignedIn) {
                Text(
                    text = "Sign in to XOrA Network (Dashboard) to use that avatar.",
                    color = Color.White.copy(alpha = 0.55f),
                )
            } else {
                Text(text = "XOrA Network status", color = glass.content)
                val selectedMode = com.arcadia.shell.xoranetwork.parseXoraPresenceMode(profile.xoraPresenceMode)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val rows = listOf(
                        listOf(
                            com.arcadia.shell.xoranetwork.XoraPresenceMode.Online to "Online",
                            com.arcadia.shell.xoranetwork.XoraPresenceMode.Away to "Away",
                        ),
                        listOf(
                            com.arcadia.shell.xoranetwork.XoraPresenceMode.Busy to "Busy",
                            com.arcadia.shell.xoranetwork.XoraPresenceMode.Invisible to "Offline",
                        ),
                    )
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { (mode, label) ->
                                val selected = mode == selectedMode
                                OutlinedButton(
                                    onClick = { onXoraPresenceMode(mode) },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = when (selectedMode) {
                        com.arcadia.shell.xoranetwork.XoraPresenceMode.Online ->
                            "Friends see you online, and what you're playing."
                        com.arcadia.shell.xoranetwork.XoraPresenceMode.Away ->
                            "Friends see you as Away."
                        com.arcadia.shell.xoranetwork.XoraPresenceMode.Busy ->
                            "Friends see you as Busy."
                        com.arcadia.shell.xoranetwork.XoraPresenceMode.Invisible ->
                            "You stay signed in, but appear offline."
                    },
                    color = Color.White.copy(alpha = 0.55f),
                )
            }

            OutlinedButton(
                onClick = onUseRaAvatar,
                enabled = raConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Use RetroAchievements avatar")
            }
            if (!raConfigured) {
                Text(
                    text = "Sign in to RetroAchievements (X) to use your RA avatar.",
                    color = Color.White.copy(alpha = 0.55f),
                )
            }

            OutlinedButton(
                onClick = onUseDiscordAvatar,
                enabled = discordLinked,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Use Discord avatar")
            }
            if (!discordLinked) {
                Text(
                    text = "Link Discord under Social to use your Discord avatar.",
                    color = Color.White.copy(alpha = 0.55f),
                )
            }

            if (profile.avatarSource != AvatarSource.Default) {
                TextButton(onClick = onClearAvatar) {
                    Text(text = "Clear avatar")
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text(text = "Display name") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(text = "Colour presets", color = glass.content)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarPresets.forEach { preset ->
                    ProfileAvatar(
                        displayName = name.ifBlank { "P" },
                        presetId = preset.id,
                        size = 44.dp,
                        onClick = {
                            presetId = preset.id
                            onSelectAvatarPreset(preset.id)
                        },
                        borderColor = if (
                            profile.avatarSource == AvatarSource.Default &&
                            preset.id == presetId
                        ) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.2f)
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(text = "Cancel") }
                TextButton(
                    onClick = {
                        onSave(name, presetId)
                        onDismiss()
                    },
                ) {
                    Text(text = "Save")
                }
            }
        }
    }
}
