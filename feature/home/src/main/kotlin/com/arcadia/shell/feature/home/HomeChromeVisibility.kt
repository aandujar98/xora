package com.arcadia.shell.feature.home

/**
 * Whether the collapsed LT social capsule and RT profile bubble should stay off-screen.
 *
 * Start Settings (and Advanced Settings, which unmounts Home) must not keep those pills in
 * the top corners — they sit on top of the glass overlay and the Setup plate.
 */
fun shouldHideHomePillChrome(
    startSettingsOpen: Boolean,
    launchPageOpen: Boolean = false,
    photosOverlayOpen: Boolean = false,
    raLibraryOpen: Boolean = false,
): Boolean = startSettingsOpen || launchPageOpen || photosOverlayOpen || raLibraryOpen
