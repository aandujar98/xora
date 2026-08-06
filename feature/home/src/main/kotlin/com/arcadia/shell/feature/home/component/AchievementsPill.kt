package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
import com.arcadia.shell.feature.home.AchievementsPaneTab
import com.arcadia.shell.feature.home.AchievementsUiState
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewAchievementsNeedsLogin
import com.arcadia.shell.feature.home.preview.previewAchievementsSignedIn
import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaGameLookup
import com.arcadia.shell.retroachievements.RaRecentUnlock

@Composable
fun AchievementsPill(
    expanded: Boolean,
    state: AchievementsUiState,
    onToggle: () -> Unit,
    onSelectTab: (AchievementsPaneTab) -> Unit,
    onLogin: (username: String, password: String) -> Unit,
    onLoginWithApiKey: (username: String, apiKey: String) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)

    Column(
        modifier = modifier.widthIn(max = if (expanded) 360.dp else 200.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(
            modifier = Modifier
                .liquidGlass(
                    shape = ArcadiaGlass.PillShape,
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Standard,
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TriggerGlyph(letter = "X")
            TrophyGlyph(
                modifier = Modifier
                    .size(18.dp)
                    .semantics { contentDescription = "RetroAchievements" },
            )
            Text(
                text = collapsedLabel(state),
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = arcadiaTween<IntSize>(ArcadiaMotion.Medium),
            ),
            exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + shrinkVertically(
                shrinkTowards = Alignment.Bottom,
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
                    )
                    .padding(14.dp)
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .then(
                        if (!state.credentials.isConfigured || state.needsLogin) {
                            Modifier.verticalScroll(rememberScrollState())
                        } else {
                            Modifier
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    !state.credentials.isConfigured || state.needsLogin -> LoginForm(
                        initial = state.credentials,
                        isBusy = state.isLoggingIn,
                        error = state.error,
                        pendingWebApiUsername = state.pendingWebApiUsername,
                        onLogin = onLogin,
                        onLoginWithApiKey = onLoginWithApiKey,
                    )
                    else -> LoggedInContent(
                        state = state,
                        onSelectTab = onSelectTab,
                        onSignOut = onSignOut,
                    )
                }
            }
        }
    }
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

@Composable
private fun LoggedInContent(
    state: AchievementsUiState,
    onSelectTab: (AchievementsPaneTab) -> Unit,
    onSignOut: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = state.profile?.username ?: state.credentials.username,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            state.profile?.let { profile ->
                Text(
                    text = "${profile.totalPoints} pts",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        TextButton(onClick = onSignOut) { Text("Sign out") }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.tab == AchievementsPaneTab.ThisGame,
            onClick = { onSelectTab(AchievementsPaneTab.ThisGame) },
            label = { Text("This game") },
        )
        FilterChip(
            selected = state.tab == AchievementsPaneTab.Recent,
            onClick = { onSelectTab(AchievementsPaneTab.Recent) },
            label = { Text("Recent") },
        )
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        return
    }

    state.error?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    when (state.tab) {
        AchievementsPaneTab.ThisGame -> ThisGameList(state.gameLookup)
        AchievementsPaneTab.Recent -> RecentList(state.recent)
    }
}

@Composable
private fun ThisGameList(lookup: RaGameLookup?) {
    when (lookup) {
        null -> Text(
            text = "Select a game to load achievements.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        RaGameLookup.NoHash -> Text(
            text = "This title cannot be hashed for RetroAchievements.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        is RaGameLookup.NoGame -> Text(
            text = "No RetroAchievements set matches this ROM hash " +
                "(md5 ${lookup.md5.take(8)}…). " +
                "If the game has a set, the dump may differ from linked hashes.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        is RaGameLookup.Failed -> Text(
            text = lookup.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        is RaGameLookup.Matched -> {
            Text(
                text = "${lookup.progress.title} · ${lookup.progress.progressLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(lookup.progress.achievements, key = { it.id }) { achievement ->
                    AchievementRow(achievement)
                }
            }
        }
    }
}

@Composable
private fun RecentList(recent: List<RaRecentUnlock>) {
    if (recent.isEmpty()) {
        Text(
            text = "No recent unlocks yet.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
        )
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(recent, key = { "${it.achievementId}-${it.date}" }) { unlock ->
            RecentRow(unlock)
        }
    }
}

@Composable
private fun AchievementRow(achievement: RaAchievement) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgeImage(url = achievement.badgeUrl, locked = !achievement.earned)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (achievement.earned) Color.White else Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = achievement.description,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${achievement.points}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun RecentRow(unlock: RaRecentUnlock) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BadgeImage(url = unlock.badgeUrl, locked = false)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = unlock.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${unlock.gameTitle} · ${unlock.consoleName}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${unlock.points}",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
}

@Composable
private fun BadgeImage(url: String, locked: Boolean) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(120)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = if (locked) 0.08f else 0.12f))
            .then(if (locked) Modifier else Modifier),
        alpha = if (locked) 0.45f else 1f,
    )
}

private fun collapsedLabel(state: AchievementsUiState): String {
    if (!state.credentials.isConfigured) return "Sign in"
    return when (val lookup = state.gameLookup) {
        is RaGameLookup.Matched -> lookup.progress.progressLabel
        else -> state.profile?.totalPoints?.let { "$it pts" } ?: "…"
    }
}

@Composable
private fun TrophyGlyph(modifier: Modifier = Modifier) {
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
        drawPath(cupPath, color = Color.White)
        // Handles
        drawArc(
            color = Color.White,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.08f, h * 0.18f),
            size = Size(w * 0.28f, h * 0.28f),
            style = stroke,
        )
        drawArc(
            color = Color.White,
            startAngle = 270f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.64f, h * 0.18f),
            size = Size(w * 0.28f, h * 0.28f),
            style = stroke,
        )
        // Stem + base
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.44f, cupBottom),
            size = Size(w * 0.12f, h * 0.18f),
            cornerRadius = CornerRadius(w * 0.04f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.30f, h * 0.78f),
            size = Size(w * 0.40f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.04f),
        )
    }
}

@XoraPreview
@Composable
private fun AchievementsPillCollapsedPreview() {
    XoraPreviewTheme {
        AchievementsPill(
            expanded = false,
            state = previewAchievementsNeedsLogin(),
            onToggle = {},
            onSelectTab = {},
            onLogin = { _, _ -> },
            onLoginWithApiKey = { _, _ -> },
            onSignOut = {},
        )
    }
}

@XoraPreview
@Composable
private fun AchievementsPillExpandedPreview() {
    XoraPreviewTheme {
        AchievementsPill(
            expanded = true,
            state = previewAchievementsSignedIn(),
            onToggle = {},
            onSelectTab = {},
            onLogin = { _, _ -> },
            onLoginWithApiKey = { _, _ -> },
            onSignOut = {},
        )
    }
}
