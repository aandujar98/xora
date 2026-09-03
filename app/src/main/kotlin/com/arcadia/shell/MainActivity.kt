package com.arcadia.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arcadia.shell.audio.BackgroundMusicController
import com.arcadia.shell.audio.OnboardingMusicController
import com.arcadia.shell.audio.UiSoundController
import com.arcadia.shell.datastore.resolveDarkTheme
import com.arcadia.shell.designsystem.ArcadiaTheme
import com.arcadia.shell.display.DisplayRefresh
import com.arcadia.shell.display.ImmersiveMode
import com.arcadia.shell.feature.home.HomeViewModel
import com.arcadia.shell.home.ShellViewModel
import com.arcadia.shell.launcher.discord.DiscordRichPresence
import com.arcadia.shell.launcher.notifications.ShellSystemNotifier
import com.arcadia.shell.scraper.SpotifyAuth
import com.arcadia.shell.scraper.SteamOpenId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val shellViewModel: ShellViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()

    @Inject lateinit var backgroundMusic: BackgroundMusicController
    @Inject lateinit var onboardingMusic: OnboardingMusicController
    @Inject lateinit var uiSounds: UiSoundController
    @Inject lateinit var discordRichPresence: DiscordRichPresence
    @Inject lateinit var shellSystemNotifier: ShellSystemNotifier

    private val postNotificationsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            shellSystemNotifier.clearPendingPermissionPrompt()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ImmersiveMode.apply(window)
        DisplayRefresh.preferSixtyHertz(window)
        discordRichPresence.attachHostActivity(this)
        handleExternalAuthIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                shellSystemNotifier.permissionRequests.collect {
                    promptPostNotificationsIfNeeded()
                }
            }
        }

        setContent {
            val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()
            val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = shellState.themeMode.resolveDarkTheme(isSystemInDarkTheme())

            LaunchedEffect(homeState.music.nowPlaying.isPlaying) {
                backgroundMusic.setLibraryMusicActive(homeState.music.nowPlaying.isPlaying)
            }

            LaunchedEffect(homeState.bootIntroOpen, homeState.homeIntroReveal) {
                backgroundMusic.setBootIntroActive(
                    homeState.bootIntroOpen && !homeState.homeIntroReveal,
                )
            }

            LaunchedEffect(shellState.prefsReady, shellState.showOnboarding) {
                val holdShellBgm = !shellState.prefsReady || shellState.showOnboarding
                backgroundMusic.setOnboardingActive(holdShellBgm)
                onboardingMusic.setActive(shellState.showOnboarding)
            }

            ArcadiaTheme(
                darkTheme = darkTheme,
                shellThemeId = shellState.shellThemeId,
                uiTextScale = shellState.uiTextScale,
                uiLayoutScale = shellState.primaryUiLayoutScale,
            ) {
                ArcadiaShell(
                    shellState = shellState,
                    homeViewModel = homeViewModel,
                    onSetHomeCandidate = shellViewModel::setHomeCandidate,
                    onOpenHomeSettings = shellViewModel::openHomeSettings,
                    onRestartOnboarding = shellViewModel::restartOnboarding,
                    onOnboardingFinished = shellViewModel::clearForceOnboarding,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalAuthIntent(intent)
    }

    private fun handleExternalAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        when {
            SteamOpenId.isReturnUri(uri) -> homeViewModel.applySteamOpenIdReturn(uri)
            SpotifyAuth.isReturnUri(uri) -> homeViewModel.applySpotifyAuthReturn(uri)
        }
    }

    /**
     * Controller input is intercepted here rather than through Compose focus handling.
     *
     * `dispatchKeyEvent` sees events before any view consumes them, which matters because the
     * selection model is index-based and must react even when the tile it is moving to has not been
     * composed yet.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val consumed = when (event.action) {
            KeyEvent.ACTION_DOWN -> homeViewModel.gamepadDispatcher.onKeyDown(event.keyCode, event)
            KeyEvent.ACTION_UP -> homeViewModel.gamepadDispatcher.onKeyUp(event.keyCode, event)
            else -> false
        }
        return consumed || super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean =
        homeViewModel.gamepadDispatcher.onGenericMotionEvent(event) ||
            super.onGenericMotionEvent(event)

    override fun onResume() {
        super.onResume()
        ImmersiveMode.apply(window)
        DisplayRefresh.preferSixtyHertz(window)
        shellViewModel.refresh()
        homeViewModel.onResumed()
        backgroundMusic.onForeground()
        onboardingMusic.onForeground()
        uiSounds.onForeground()
        // Re-bind Social SDK engine activity after Discord Custom Tab / OAuth returns.
        discordRichPresence.attachHostActivity(this)
        discordRichPresence.onAppForeground()
        if (shellSystemNotifier.pendingPermissionPrompt) {
            promptPostNotificationsIfNeeded()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) ImmersiveMode.apply(window)
    }

    override fun onPause() {
        super.onPause()
        val power = getSystemService(PowerManager::class.java)
        homeViewModel.onPaused(screenInteractive = power?.isInteractive != false)
        backgroundMusic.onBackground()
        onboardingMusic.onBackground()
        uiSounds.onBackground()
        discordRichPresence.onAppBackground()
        // A direction can still be held when an emulator takes over, and a repeat timer left
        // running would keep scrolling the grid in the background.
        homeViewModel.gamepadDispatcher.reset()
    }

    private fun promptPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) {
            shellSystemNotifier.clearPendingPermissionPrompt()
            return
        }
        if (shellSystemNotifier.hasPostPermission()) {
            shellSystemNotifier.clearPendingPermissionPrompt()
            return
        }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            shellSystemNotifier.clearPendingPermissionPrompt()
            return
        }
        postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
