package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.designsystem.XoraModalGlass
import com.arcadia.shell.designsystem.xoraForegroundShadow
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.feature.home.AchievementsUiState
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaGameLookup
import com.arcadia.shell.retroachievements.RaGameProgress
import com.arcadia.shell.retroachievements.RaProfile

/** Soft bar radius — same as RT, so in-pill circles aren't clipped into ovals. */
private val CollapsedBarShape = RoundedCornerShape(20.dp)
private val DividerColor = Color.White.copy(alpha = 0.28f)

// Expanded card uses the same 30dp tinted-glass plate as Friends / other modals.
private val CardShape = XoraModalGlass.Shape
private val CardEdge = Color.White.copy(alpha = 0.25f)
private val CardInk = Color.White
private val EarnedBadgeEdge = Color(0xFFEFBD17)
private val ScoreGoldTop = Color(0xFFFFC95E)
private val ScoreGoldBottom = Color(0xFFFF9B1B)
private val RuleStart = Color(0xFF989CB3).copy(alpha = 0.25f)
private val RuleEnd = Color(0xFF4D4655).copy(alpha = 0.25f)
private val BoxArtSize = 67.dp
private val TrophyBadgeSize = 28.dp
private val TrophyBadgeShape = RoundedCornerShape(3.dp)
private const val TROPHY_BADGE_SLOTS = 7
private val PlayerAvatarSize = 25.dp
private val CardTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.5f),
    offset = Offset(2f, 2f),
    blurRadius = 4f,
)

@Composable
fun AchievementsPill(
    expanded: Boolean,
    state: AchievementsUiState,
    onToggle: () -> Unit,
    @Suppress("UNUSED_PARAMETER")
    onSelectTab: (com.arcadia.shell.feature.home.AchievementsPaneTab) -> Unit,
    onLogin: (username: String, password: String) -> Unit,
    onLoginWithApiKey: (username: String, apiKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val needsLogin = !state.credentials.isConfigured || state.needsLogin
    val matched = state.gameLookup as? RaGameLookup.Matched
    val progress = matched?.progress

    Column(
        modifier = modifier.widthIn(max = if (expanded) 372.dp else 240.dp),
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
                    .xoraModalGlass(CardShape)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 10.dp)
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
                    // Only the summary is full-bleed (its rule spans the card), so every other
                    // branch pads itself.
                    needsLogin -> Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                        LoginForm(
                            initial = state.credentials,
                            isBusy = state.isLoggingIn,
                            error = state.error,
                            pendingWebApiUsername = state.pendingWebApiUsername,
                            onLogin = onLogin,
                            onLoginWithApiKey = onLoginWithApiKey,
                        )
                    }
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
                        mutedColor = glass.contentMuted,
                    )
                    else -> ExpandedEmptyState(
                        state = state,
                        contentColor = glass.content,
                        mutedColor = glass.contentMuted,
                        modifier = Modifier.padding(horizontal = 14.dp),
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
            .xoraForegroundShadow(CollapsedBarShape)
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
    mutedColor: Color,
) {
    val badgeSlots = remember(progress.achievements) { trophyBadgeSlots(progress) }
    val playerUrls = listOfNotNull(
        profile?.userPicUrl ?: RaProfile.userPicUrlFor(credentials.username),
    )
    val extraPlayers = (progress.numDistinctPlayers - playerUrls.size).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BoxArtThumb(url = progress.imageIconUrl)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = progress.title,
                        fontFamily = XoraFonts.Secondary,
                        fontSize = 16.sp,
                        color = CardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(shadow = CardTextShadow),
                        modifier = Modifier.weight(1f),
                    )
                    ConsoleBadge(label = consoleShortName(progress.consoleName))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    badgeSlots.forEach { achievement ->
                        TrophyBadge(achievement = achievement)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TrophyGlyph(modifier = Modifier.size(22.dp), tint = Color.White)
            ScoreReadout(
                earned = progress.numAwardedToUser,
                total = progress.numAchievements,
            )
            ProgressGauge(
                fraction = progress.completionFraction,
                modifier = Modifier.weight(1f),
            )
        }

        // Full-bleed rule, so the row padding above and below cannot clip it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(RuleStart, RuleEnd))),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ClockGlyph(modifier = Modifier.size(22.dp), tint = Color.White)
            Text(
                text = "Recently Played:",
                fontFamily = XoraFonts.Title,
                fontSize = 11.sp,
                letterSpacing = XoraFonts.TitleLetterSpacing,
                color = CardInk,
                maxLines = 1,
                style = TextStyle(shadow = CardTextShadow),
            )
            Spacer(modifier = Modifier.weight(1f))
            RecentPlayers(
                urls = playerUrls,
                extraCount = extraPlayers.coerceAtMost(99),
            )
        }
    }
}

/** Fill every slot from earned first, then remaining locked — never pad blanks while badges exist. */
private fun trophyBadgeSlots(progress: RaGameProgress): List<RaAchievement?> {
    val earned = progress.achievements.filter { it.earned }
    val locked = progress.achievements.filterNot { it.earned }
    val filled = (earned + locked).take(TROPHY_BADGE_SLOTS)
    return filled + List(TROPHY_BADGE_SLOTS - filled.size) { null }
}

@Composable
private fun BoxArtThumb(url: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .xoraForegroundShadow(RoundedCornerShape(5.dp))
            .size(BoxArtSize)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .border(1.5.dp, CardEdge, RoundedCornerShape(5.dp)),
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
private fun TrophyBadge(achievement: RaAchievement?) {
    val context = LocalContext.current
    val earned = achievement?.earned == true
    val edge = if (earned) EarnedBadgeEdge else CardEdge
    val grayMatrix = remember {
        ColorMatrix().apply { setToSaturation(0f) }
    }
    Box(
        modifier = Modifier
            .size(TrophyBadgeSize)
            .clip(TrophyBadgeShape)
            .background(Color.Black.copy(alpha = if (earned) 0.12f else 0.45f))
            .border(1.5.dp, edge, TrophyBadgeShape),
    ) {
        if (achievement != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(achievement.badgeUrl)
                    .crossfade(120)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (earned) null else ColorFilter.colorMatrix(grayMatrix),
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (earned) {
                            Modifier
                        } else {
                            Modifier.drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.28f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.35f),
                                        ),
                                    ),
                                )
                            }
                        },
                    ),
            )
        }
    }
}

/** Earned count in gold over the muted total, as authored in the Figma card. */
@Composable
private fun ScoreReadout(earned: Int, total: Int) {
    val goldBrush = remember { Brush.verticalGradient(listOf(ScoreGoldTop, ScoreGoldBottom)) }
    val totalBrush = remember {
        Brush.verticalGradient(listOf(Color.White, Color(0xFFA1A1A1)))
    }
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "$earned",
            style = TextStyle(
                brush = goldBrush,
                fontFamily = XoraFonts.Title,
                fontSize = 16.sp,
                letterSpacing = XoraFonts.TitleLetterSpacing,
                shadow = CardTextShadow,
            ),
        )
        Text(
            text = "/$total",
            style = TextStyle(
                brush = totalBrush,
                fontFamily = XoraFonts.Title,
                fontSize = 9.sp,
                letterSpacing = XoraFonts.TitleLetterSpacing,
                shadow = CardTextShadow,
            ),
            modifier = Modifier.padding(bottom = 1.dp),
        )
    }
}

@Composable
private fun ProgressGauge(fraction: Float, modifier: Modifier = Modifier) {
    val fillBrush = remember { Brush.verticalGradient(listOf(ScoreGoldTop, ScoreGoldBottom)) }
    Box(
        modifier = modifier
            .height(15.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .border(1.5.dp, Color.White, CircleShape)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(fillBrush),
        )
    }
}

@Composable
private fun RecentPlayers(urls: List<String>, extraCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val slots = urls.take(4)
        slots.forEach { url ->
            AvatarCircle(url = url, size = PlayerAvatarSize)
        }
        repeat((4 - slots.size).coerceAtLeast(0)) {
            Box(
                modifier = Modifier
                    .size(PlayerAvatarSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.5.dp, CardEdge, CircleShape),
            )
        }
        if (extraCount > 0) {
            Box(
                modifier = Modifier
                    .height(PlayerAvatarSize)
                    .widthIn(min = 35.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$extraCount",
                    fontFamily = XoraFonts.Secondary,
                    fontSize = 12.sp,
                    color = CardInk,
                    maxLines = 1,
                    style = TextStyle(shadow = CardTextShadow),
                )
            }
        }
    }
}

@Composable
private fun ExpandedEmptyState(
    state: AchievementsUiState,
    contentColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
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
    Image(
        painter = painterResource(R.drawable.trophy),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
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
