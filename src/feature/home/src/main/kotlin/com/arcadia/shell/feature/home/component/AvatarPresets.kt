package com.arcadia.shell.feature.home.component

import androidx.compose.ui.graphics.Color

data class AvatarPreset(
    val id: String,
    val color: Color,
)

val AvatarPresets: List<AvatarPreset> = listOf(
    AvatarPreset("preset_0", Color(0xFF6E7BFF)),
    AvatarPreset("preset_1", Color(0xFF37D6A0)),
    AvatarPreset("preset_2", Color(0xFFFFC24B)),
    AvatarPreset("preset_3", Color(0xFFFF5C6C)),
    AvatarPreset("preset_4", Color(0xFFA6AEFF)),
    AvatarPreset("preset_5", Color(0xFF4ECDC4)),
)

fun avatarPreset(id: String): AvatarPreset =
    AvatarPresets.firstOrNull { it.id == id } ?: AvatarPresets.first()
