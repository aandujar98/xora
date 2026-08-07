package com.arcadia.shell.feature.home.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade

@Composable
fun ProfileAvatar(
    displayName: String,
    presetId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = Color.White.copy(alpha = 0.35f),
    /** Local file path or remote URL when the user has chosen an image avatar. */
    imageModel: String? = null,
) {
    val preset = avatarPreset(presetId)
    val initial = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val platformContext = LocalPlatformContext.current

    Box(
        modifier = modifier
            // requiredSize keeps a true circle even when a parent tries to squash height/width.
            .requiredSize(size)
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(preset.color)
            .border(width = 1.5.dp, color = borderColor, shape = CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageModel.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(platformContext)
                    .data(imageModel)
                    .crossfade(160)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}
