package com.arcadia.shell.feature.home

import android.util.Log
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.display.DisplayTopologyMonitor
import com.arcadia.shell.model.Game
import com.arcadia.shell.retroachievements.RaGameLookup
import com.arcadia.shell.scraper.GameCompanionDetailRepository
import com.arcadia.shell.scraper.insight.GameInsight
import com.arcadia.shell.scraper.insight.GameInsightRepository
import com.arcadia.shell.scraper.insight.GameScreenshotRepository
import com.arcadia.shell.scraper.insight.InsightSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the companion bottom screen for one game session.
 *
 * Lives outside the ViewModel because it has to outlive the shell Activity: the panel's whole point
 * is to stay on screen while an emulator owns the foreground, at which point nothing
 * Activity-scoped is alive to hold state. The overlay window host in the app module and the
 * Activity's own secondary pane both render from the single [session] flow, so the panel looks
 * identical whichever window happens to be carrying it.
 */
@Singleton
class GameCompanionController @Inject constructor(
    private val preferences: ShellPreferences,
    topologyMonitor: DisplayTopologyMonitor,
    private val insightRepository: GameInsightRepository,
    private val screenshotRepository: GameScreenshotRepository,
    private val detailRepository: GameCompanionDetailRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _session = MutableStateFlow<GameCompanionUiState?>(null)
    val session: StateFlow<GameCompanionUiState?> = _session.asStateFlow()

    /**
     * The screen the panel belongs on, or null when there is no usable second display. Read from
     * the display topology rather than passed in, so the controller stays independent of whichever
     * window is currently hosting the panel.
     */
    val companionDisplayId: StateFlow<Int?> = topologyMonitor.topology()
        .map { it.secondary?.displayId }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, topologyMonitor.current().secondary?.displayId)

    private var resolveJob: Job? = null

    /** True once the shell has actually gone to the background since the session started. */
    private var shellBackgrounded = false

    /**
     * Starts a session for [game] if it qualifies: dual-screen mode with a second physical display,
     * a system that does not paint its own bottom screen, and a game that did not boot *onto* the
     * second display.
     */
    fun onGameLaunched(
        game: Game,
        launchDisplayId: Int?,
        raLookup: RaGameLookup?,
    ) {
        scope.launch {
            val secondaryId = companionDisplayId.value
            if (secondaryId == null) return@launch
            if (!game.supportsCompanionScreen) return@launch
            // The game itself took the second screen, so there is nothing to companion.
            if (launchDisplayId == secondaryId) return@launch
            if (preferences.settings.first().displayMode != DisplayMode.Dual) return@launch

            start(game, raLookup)
        }
    }

    /** Called from the shell's onPause: the emulator (or anything else) took the foreground. */
    fun onShellBackgrounded() {
        if (_session.value != null) shellBackgrounded = true
    }

    /**
     * Called from the shell's onResume. A session only ends once the shell has actually been away,
     * because the launch animation plays while the shell is still in front.
     */
    fun onShellForegrounded() {
        if (shellBackgrounded) endSession()
    }

    fun endSession() {
        resolveJob?.cancel()
        resolveJob = null
        shellBackgrounded = false
        _session.value = null
    }

    fun selectAction(action: GameCompanionAction) {
        _session.update { it.copy(focusedAction = action) }
    }

    fun openFocusedAction() {
        val current = _session.value ?: return
        when (current.focusedAction) {
            GameCompanionAction.About -> _session.value =
                current.copy(overlay = GameCompanionOverlay.About)
            GameCompanionAction.Manual -> if (!current.manualMissing) {
                _session.value = current.copy(overlay = GameCompanionOverlay.Manual)
            }
        }
    }

    fun dismissOverlay() {
        _session.update { it.copy(overlay = GameCompanionOverlay.None) }
    }

    private fun start(game: Game, raLookup: RaGameLookup?) {
        resolveJob?.cancel()
        shellBackgrounded = false

        val matched = (raLookup as? RaGameLookup.Matched)?.progress
        val cachedInsight = insightRepository.cached(game.id)
        val cachedDetail = detailRepository.cached(game.id)

        _session.value = GameCompanionUiState(
            gameId = game.id,
            title = game.title,
            platformLabel = game.platform.displayName,
            backdropPath = backdropFor(game),
            about = cachedInsight?.toCompanionAbout(isLoading = false)
                ?: GameInsightUiState(
                    gameId = game.id,
                    isLoading = true,
                    platformLabel = game.platform.displayName,
                ),
            detailLoading = cachedDetail == null,
            manualPath = cachedDetail?.manualPath,
            manualResolved = cachedDetail?.manualResolved == true,
            players = cachedDetail?.players,
            ratingPercent = cachedDetail?.ratingPercent,
            publisher = cachedDetail?.publisher,
            raTitle = matched?.title,
            raProgressLabel = matched?.progressLabel,
        )

        // Three independent lookups, each publishing as it lands. Chaining them would leave the
        // Manual button saying "Looking up…" until Wikipedia had answered, which it has no reason
        // to wait for.
        resolveJob = scope.launch {
            val credentials = preferences.credentials.first()

            if (cachedInsight == null) {
                launch {
                    val insight = runCatching { insightRepository.insightFor(game, credentials) }
                        .onFailure { Log.w(TAG, "Companion insight failed for ${game.title}", it) }
                        .getOrNull()
                    updateSession(game.id) { current ->
                        current.copy(
                            about = insight?.toCompanionAbout(isLoading = false)
                                ?: current.about.copy(isLoading = false),
                        )
                    }
                }
            }

            if (cachedDetail == null) {
                launch {
                    val detail = runCatching { detailRepository.detailFor(game) }
                        .onFailure { Log.w(TAG, "Companion detail failed for ${game.title}", it) }
                        .getOrNull()
                    updateSession(game.id) { current ->
                        current.copy(
                            detailLoading = false,
                            manualPath = detail?.manualPath,
                            manualResolved = true,
                            players = detail?.players ?: current.players,
                            ratingPercent = detail?.ratingPercent ?: current.ratingPercent,
                            publisher = detail?.publisher ?: current.publisher,
                            about = current.about.copy(
                                // A ScreenScraper synopsis describes this exact release, so it wins
                                // over the Wikipedia / IGDB prose when both exist.
                                summary = detail?.synopsis?.takeIf { it.isNotBlank() }
                                    ?: current.about.summary,
                                genre = current.about.genre ?: detail?.genre,
                                developer = current.about.developer ?: detail?.developer,
                                releaseYear = current.about.releaseYear ?: detail?.releaseYear,
                            ),
                        )
                    }
                }
            }

            // Screenshots stand in as the backdrop when the ROM has no scraped hero or box art.
            if (backdropFor(game) == null) {
                launch {
                    val shots = screenshotRepository.cached(game.id)
                        ?: runCatching { screenshotRepository.screenshotsFor(game, credentials) }
                            .getOrDefault(emptyList())
                    val path = shots.firstOrNull() ?: return@launch
                    updateSession(game.id) { it.copy(backdropPath = path) }
                }
            }
        }
    }

    /** Applies [transform] only while [gameId] is still the live session. */
    private fun updateSession(
        gameId: String,
        transform: (GameCompanionUiState) -> GameCompanionUiState,
    ) {
        val current = _session.value ?: return
        if (current.gameId != gameId) return
        _session.value = transform(current)
    }

    /** Fanart / hero art first, box art second; screenshots fill in later if both are missing. */
    private fun backdropFor(game: Game): String? = game.heroImagePath ?: game.boxArtPath

    private fun GameInsight.toCompanionAbout(isLoading: Boolean): GameInsightUiState = GameInsightUiState(
        gameId = gameId,
        isLoading = isLoading,
        summary = summary,
        summarySourceLabel = when (summarySource) {
            InsightSource.Wikipedia -> "Wikipedia"
            InsightSource.Igdb -> "IGDB"
            InsightSource.Local -> "Library"
            InsightSource.Speedrun -> "Speedrun.com"
            null -> null
        },
        releaseYear = releaseYear,
        developer = developer,
        genre = genre,
        platformLabel = platformLabel,
        speedrunBlurb = speedrunBlurb,
        trivia = trivia,
    )

    /** Guards every mutation against a session that ended while the user was tapping. */
    private fun MutableStateFlow<GameCompanionUiState?>.update(
        transform: (GameCompanionUiState) -> GameCompanionUiState,
    ) {
        value = value?.let(transform)
    }

    private companion object {
        const val TAG = "GameCompanion"
    }
}
