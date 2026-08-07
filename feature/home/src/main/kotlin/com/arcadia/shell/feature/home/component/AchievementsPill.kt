package com.arcadia.shell.feature.home.component

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.datastore.RetroAchievementsCredentials
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.AchievementsUiState
import com.arcadia.shell.retroachievements.RaGameLookup
import com.arcadia.shell.retroachievements.RaGameProgress
import com.arcadia.shell.retroachievements.RaProfile

/** Soft bar radius — same as RT, so in-pill circles aren't clipped into ovals. */
private val CollapsedBarShape = RoundedCornerShape(20.dp)
private val BadgeShape = RoundedCornerShape(8.dp)
private val ProgressTrack = Color(0xFF3A3A3C)
private val ProgressFill = Color.White
private val DividerColor = Color.White.copy(alpha = 0.28f)

@Composable
fun AchievementsPill(
    expanded: Boolean,
    state: AchievementsUiState,
    onToggle: () -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onSelectTab: (com.arcadia.shell.feature.home.AchievementsPaneTab) -> Unit,
    onLogin: (username: String, password: String) -> Unit,
    onLoginWithApiKey: (username: String, apiKey: String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val needsLogin = !state.credentials.isConfigured || state.needsLogin
    val matched = state.gameLookup as? RaGameLookup.Matched
    val progress = matched?.progress

    Column(
        modifier = modifier.widthIn(max = if (expanded) 320.dp else 240.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Collapsed X chrome hides while the panel is open; Back / X restores it.
        AnimatedVisibility(
            visible = !expanded,
            enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + scaleIn(
                animationSpec = arcadiaTween(ArcadiaMotion.Medium),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.9f, 1f),
            ),
            exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + scaleOut(
                animationSpec = arcadiaTween(ArcadiaMotion.Fast),
                targetScale = 0.96f,
                transformOrigin = TransformOrigin(0.9f, 1f),
            ),
        ) {
            CollapsedRaPill(
                state = state,
                progress = progress,
                contentColor = glass.content,
                mutedColor = glass.contentMuted,
                onToggle = onToggle,
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
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .liquidGlass(
                        shape = ArcadiaGlass.PanelShape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .clickable(onClick = onToggle)
                    .padding(14.dp)
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .then(
                        if (needsLogin) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    needsLogin -> LoginForm(
                        initial = state.credentials,
                        isBusy = state.isLoggingIn,
                        error = state.error,
                        pendingWebApiUsername = state.pendingWebApiUsername,
                        onLogin = onLogin,
                        onLoginWithApiKey = onLoginWithApiKey,
                    )
                    state.isLoading && progress == null -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = glass.content,
                            strokeWidth = 2.dp,
                        )
                    }
                    progress != null -> ExpandedRaSummary(
                        progress = progress,
                        profile = state.profile,
                        credentials = state.credentials,
                        contentColor = glass.content,
                        mutedColor = glass.contentMuted,
                        onSignOut = onSignOut,
                    )
                    else -> ExpandedEmptyState(
                        state = state,
                        contentColor = glass.content,
                        mutedColor = glass.contentMuted,
                        onSignOut = onSignOut,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsedRaPill(
    state: AchievementsUiState,
    progress: RaGameProgress?,
    contentColor: Color,
    mutedColor: Color,
    onToggle: () -> Unit,
) {
    val label = when {
        !state.credentials.isConfigured -> "Sign in"
        progress != null -> progress.progressLabel
        else -> state.profile?.totalPoints?.let { "$it pts" } ?: "…"
    }
    val avatarUrls = collapsedAvatarUrls(state)

    Row(
        modifier = Modifier
            .liquidGlass(
                shape = CollapsedBarShape,
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Standard,
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics { contentDescription = "RetroAchievements $label" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrophyGlyph(modifier = Modifier.size(16.dp), tint = contentColor)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(DividerColor),
        )
        ClockGlyph(modifier = Modifier.size(14.dp), tint = mutedColor)
        OverlappingAvatars(
            urls = avatarUrls,
            size = 18.dp,
            overlap = 6.dp,
            maxVisible = 3,
            extraCount = 0,
        )
        TriggerGlyph(letter = "X")
    }
}

@Composable
private fun ExpandedRaSummary(
    progress: RaGameProgress,
    profile: RaProfile?,
    credentials: RetroAchievementsCredentials,
    contentColor: Color,
    mutedColor: Color,
    onSignOut: () -> Unit,
) {
    val earnedBadges = progress.achievements.filter { it.earned }
    val badgeSlots = buildList {
        addAll(earnedBadges.take(5))
        while (size < 7) add(null)
    }
    val playerUrls = listOfNotNull(
        profile?.userPicUrl ?: RaProfile.userPicUrlFor(credentials.username),
    )
    val extraPlayers = (progress.numDistinctPlayers - playerUrls.size).coerceAtLeast(0)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
    // Header — game icon, title, console chip
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameIconThumb(url = progress.imageIconUrl, size = 40.dp)
        Text(
            text = progress.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ConsoleBadge(label = consoleShortName(progress.consoleName))
    }

    // Achievement badge strip
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badgeSlots.forEach { achievement ->
            if (achievement != null) {
                BadgeImage(
                    url = achievement.badgeUrl,
                    locked = false,
                    size = 34.dp,
                    corner = 7.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                )
            }
        }
    }

    // Trophy progress + bar
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TrophyGlyph(modifier = Modifier.size(16.dp), tint = contentColor)
        Text(
            text = progress.progressLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(CollapsedBarShape)
                .background(ProgressTrack.copy(alpha = 0.55f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.completionFraction)
                    .clip(CollapsedBarShape)
                    .background(ProgressFill.copy(alpha = 0.92f)),
            )
        }
    }

    // Recent players
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClockGlyph(modifier = Modifier.size(14.dp), tint = mutedColor)
        Text(
            text = "Recent Players:",
            style = MaterialTheme.typography.labelMedium,
            color = mutedColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        OverlappingAvatars(
            urls = playerUrls,
            size = 22.dp,
            overlap = 7.dp,
            maxVisible = 4,
            extraCount = extraPlayers.coerceAtMost(99),
            showEmptySlots = 3,
        )
    }

    TextButton(
        onClick = onSignOut,
        modifier = Modifier.align(Alignment.End),
    ) {
        Text("Sign out", color = mutedColor)
    }
    }
}

@Composable
private fun ExpandedEmptyState(
    state: AchievementsUiState,
    contentColor: Color,
    mutedColor: Color,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = state.profile?.username ?: state.credentials.username,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
        Text(
            text = when (state.gameLookup) {
                is RaGameLookup.NoHash -> "This title can’t be hashed for RetroAchievements."
                is RaGameLookup.NoGame -> "No RetroAchievements set for this ROM hash."
                is RaGameLookup.Failed -> state.gameLookup.message
                null -> "Select a game to load achievements."
                else -> state.error ?: "No achievement data yet."
            },
            style = MaterialTheme.typography.bodySmall,
            color = mutedColor,
        )
        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.End)) {
            Text("Sign out", color = mutedColor)
        }
    }
}

@Composable
private fun ConsoleBadge(label: String) {
    if (label.isBlank()) return
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun GameIconThumb(url: String, size: Dp) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(BadgeShape)
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(120)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun OverlappingAvatars(
    urls: List<String>,
    size: Dp,
    overlap: Dp,
    maxVisible: Int,
    extraCount: Int,
    showEmptySlots: Int = 0,
) {
    val visible = urls.take(maxVisible)
    val empties = (showEmptySlots - visible.size).coerceAtLeast(0)
    val totalSlots = visible.size + empties
    if (totalSlots <= 0 && extraCount <= 0) return

    val stackWidth = size + (size - overlap) * (totalSlots - 1).coerceAtLeast(0) +
        if (extraCount > 0) (size - overlap) else 0.dp

    Box(
        modifier = Modifier
            .width(stackWidth.coerceAtLeast(size))
            .height(size),
    ) {
        visible.forEachIndexed { index, url ->
            AvatarCircle(
                url = url,
                size = size,
                modifier = Modifier.offset(x = (size - overlap) * index),
            )
        }
        repeat(empties) { index ->
            val slot = visible.size + index
            Box(
                modifier = Modifier
                    .offset(x = (size - overlap) * slot)
                    .size(size)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color(0xFF0C1524), CircleShape),
            )
        }
        if (extraCount > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (size - overlap) * totalSlots)
                    .size(size)
                    .clip(CircleShape)
                    .background(Color(0xFF2C2C2E))
                    .border(1.dp, Color(0xFF0C1524), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$extraCount",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AvatarCircle(
    url: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(100)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, Color(0xFF0C1524), CircleShape)
            .background(Color.White.copy(alpha = 0.12f)),
    )
}

@Composable
private fun BadgeImage(
    url: String,
    locked: Boolean,
    size: Dp = 40.dp,
    corner: Dp = 8.dp,
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(120)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = if (locked) 0.08f else 0.12f))
            .border(
                width = 1.5.dp,
                color = if (locked) Color.White.copy(alpha = 0.12f) else Color(0xFFFFC857),
                shape = RoundedCornerShape(corner),
            ),
        alpha = if (locked) 0.45f else 1f,
    )
}

@Composable
private fun LoginForm(
    initial: RetroAchievementsCredentials,
    isBusy: Boolean,
    error: String?,
    pendingWebApiUsername: String?,
    onLogin: (username: String, password: String) -> Unit,
    onLoginWithApiKey: (username: String, apiKey: String) -> Unit,
) {
    var username by remember(initial.username, pendingWebApiUsername) {
        mutableStateOf(pendingWebApiUsername ?: initial.username)
    }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var useAdvancedKey by remember(pendingWebApiUsername) {
        mutableStateOf(!pendingWebApiUsername.isNullOrBlank())
    }

    Text(
        text = "Sign in to RetroAchievements",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
    )

    if (!pendingWebApiUsername.isNullOrBlank()) {
        Text(
            text = "Password accepted for $pendingWebApiUsername. Paste your Web API key " +
                "from the RA control panel (Keys) once — Connect tokens are separate.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            singleLine = true,
            label = { Text("Web API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(
            onClick = {
                onLoginWithApiKey(pendingWebApiUsername, apiKey)
                apiKey = ""
            },
            enabled = !isBusy && apiKey.isNotBlank(),
        ) {
            Text(text = if (isBusy) "Signing in…" else "Save API key")
        }
        return
    }

    Text(
        text = "Use your RetroAchievements username and password.",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.6f),
    )
    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        singleLine = true,
        label = { Text("Username") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        singleLine = true,
        label = { Text("Password") },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    TextButton(
        onClick = {
            val pass = password
            password = ""
            onLogin(username, pass)
        },
        enabled = !isBusy && username.isNotBlank() && password.isNotEmpty(),
    ) {
        Text(text = if (isBusy) "Signing in…" else "Sign in")
    }

    TextButton(
        onClick = { useAdvancedKey = !useAdvancedKey },
        enabled = !isBusy,
    ) {
        Text(text = if (useAdvancedKey) "Hide API key option" else "Paste Web API key instead")
    }

    if (useAdvancedKey) {
        Text(
            text = "Optional: skip password and paste the control-panel Web API key.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            singleLine = true,
            label = { Text("Web API key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                onLoginWithApiKey(username, apiKey)
                apiKey = ""
            },
            enabled = !isBusy && username.isNotBlank() && apiKey.isNotBlank(),
        ) {
            Text(text = if (isBusy) "Signing in…" else "Sign in with API key")
        }
    }
}

private fun collapsedAvatarUrls(state: AchievementsUiState): List<String> {
    val user = state.profile?.userPicUrl
        ?: state.credentials.username.takeIf { it.isNotBlank() }?.let(RaProfile::userPicUrlFor)
    return listOfNotNull(user)
}

private fun consoleShortName(consoleName: String): String {
    val n = consoleName.trim()
    if (n.isEmpty()) return ""
    return when {
        n.contains("Portable", ignoreCase = true) -> "PSP"
        n.contains("PlayStation 2", ignoreCase = true) -> "PS2"
        n.contains("PlayStation", ignoreCase = true) -> "PS1"
        n.contains("Nintendo 64", ignoreCase = true) -> "N64"
        n.contains("Game Boy Advance", ignoreCase = true) -> "GBA"
        n.contains("Game Boy Color", ignoreCase = true) -> "GBC"
        n.contains("Game Boy", ignoreCase = true) -> "GB"
        n.contains("Nintendo DS", ignoreCase = true) -> "NDS"
        n.contains("3DS", ignoreCase = true) -> "3DS"
        n.contains("Dreamcast", ignoreCase = true) -> "DC"
        n.contains("Genesis", ignoreCase = true) ||
            n.contains("Mega Drive", ignoreCase = true) -> "GEN"
        n.contains("Master System", ignoreCase = true) -> "SMS"
        n.contains("SNES", ignoreCase = true) ||
            n.contains("Super Nintendo", ignoreCase = true) -> "SNES"
        n.contains("NES", ignoreCase = true) ||
            n.contains("Famicom", ignoreCase = true) -> "NES"
        n.contains("Saturn", ignoreCase = true) -> "SAT"
        else -> n.take(4).uppercase()
    }
}

@Composable
private fun TrophyGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        val cupTop = h * 0.12f
        val cupBottom = h * 0.55f
        val cupPath = Path().apply {
            moveTo(w * 0.28f, cupTop)
            lineTo(w * 0.72f, cupTop)
            quadraticTo(w * 0.78f, h * 0.38f, w * 0.58f, cupBottom)
            lineTo(w * 0.42f, cupBottom)
            quadraticTo(w * 0.22f, h * 0.38f, w * 0.28f, cupTop)
            close()
        }
        drawPath(cupPath, color = tint)
        drawArc(
            color = tint,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.08f, h * 0.18f),
            size = Size(w * 0.28f, h * 0.28f),
            style = stroke,
        )
        drawArc(
            color = tint,
            startAngle = 270f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.64f, h * 0.18f),
            size = Size(w * 0.28f, h * 0.28f),
            style = stroke,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.44f, cupBottom),
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
private fun ClockGlyph(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round)
        drawCircle(
            color = tint,
            radius = w * 0.42f,
            style = stroke,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, w * 0.28f),
            end = Offset(w * 0.5f, w * 0.52f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, w * 0.52f),
            end = Offset(w * 0.68f, w * 0.62f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}
