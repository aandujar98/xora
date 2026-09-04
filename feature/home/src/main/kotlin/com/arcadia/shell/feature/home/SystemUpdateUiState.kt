package com.arcadia.shell.feature.home

/** Where the System Update window is in the check → download → install flow. */
enum class SystemUpdatePhase {
    /** Window just opened; nothing has been asked of GitHub yet. */
    Idle,
    Checking,
    /** Checked and the installed build is already the newest release. */
    UpToDate,
    /** A newer release exists and is waiting for the user to start the download. */
    Available,
    Downloading,
    /** APK is in the cache and ready to hand to the package installer. */
    ReadyToInstall,
    Failed,
}

/**
 * System Update window state. The window is the only place the updater reports progress, so it
 * carries both the release it found and the byte counts of an in-flight download.
 */
data class SystemUpdateUiState(
    val open: Boolean = false,
    val phase: SystemUpdatePhase = SystemUpdatePhase.Idle,
    val installedVersion: String = "",
    val availableVersion: String = "",
    val downloadedBytes: Long = 0L,
    /** 0 while the release did not report an asset size. */
    val totalBytes: Long = 0L,
    val error: String? = null,
    /** 0 = primary button, 1 = close. */
    val selectedButton: Int = 0,
) {
    /** Null while the total is unknown, so the panel can show an indeterminate bar. */
    val progress: Float?
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    val busy: Boolean
        get() = phase == SystemUpdatePhase.Checking || phase == SystemUpdatePhase.Downloading

    val headline: String
        get() = when (phase) {
            SystemUpdatePhase.Idle -> "Check for a new version"
            SystemUpdatePhase.Checking -> "Checking for updates…"
            SystemUpdatePhase.UpToDate -> "XOrA is up to date"
            SystemUpdatePhase.Available -> "XOrA $availableVersion is available"
            SystemUpdatePhase.Downloading -> "Downloading XOrA $availableVersion"
            SystemUpdatePhase.ReadyToInstall -> "XOrA $availableVersion is ready"
            SystemUpdatePhase.Failed -> "Update failed"
        }

    val detail: String
        get() = when (phase) {
            SystemUpdatePhase.Idle -> installedLine
            SystemUpdatePhase.Checking -> "Asking GitHub for the latest release"
            SystemUpdatePhase.UpToDate -> installedLine
            SystemUpdatePhase.Available -> "$installedLine · download to install"
            SystemUpdatePhase.Downloading -> transferLine
            SystemUpdatePhase.ReadyToInstall -> "Install to finish updating from $installedVersion"
            SystemUpdatePhase.Failed -> error ?: "Could not reach GitHub Releases."
        }

    /** Null when the phase has nothing for the user to press (a check / download is running). */
    val primaryLabel: String?
        get() = when (phase) {
            SystemUpdatePhase.Idle -> "Check for Updates"
            SystemUpdatePhase.Checking -> null
            SystemUpdatePhase.UpToDate -> "Check Again"
            SystemUpdatePhase.Available -> "Download Update"
            SystemUpdatePhase.Downloading -> null
            SystemUpdatePhase.ReadyToInstall -> "Install Update"
            SystemUpdatePhase.Failed -> "Try Again"
        }

    private val installedLine: String
        get() = if (installedVersion.isBlank()) {
            "Installed version unknown"
        } else {
            "Installed: $installedVersion"
        }

    private val transferLine: String
        get() = if (totalBytes > 0L) {
            "${formatUpdateBytes(downloadedBytes)} of ${formatUpdateBytes(totalBytes)}"
        } else {
            formatUpdateBytes(downloadedBytes)
        }
}

internal fun formatUpdateBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes < 1_048_576L -> "${(bytes / 1024L).coerceAtLeast(1L)} KB"
    else -> String.format("%.1f MB", bytes / 1_048_576.0)
}
