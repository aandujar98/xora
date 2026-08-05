package com.arcadia.shell.input

/**
 * Semantic navigation intent, deliberately decoupled from the physical button that produced it.
 * The ViewModel applies these to a selection index and never sees a key code.
 */
enum class NavAction {
    Left,
    Right,
    Up,
    Down,

    /**
     * Shoulder buttons: switch Home pages (LB → RSS feed, RB → game selector).
     * The default center page is the Home hub. Names are historical; page routing is
     * applied by the Home ViewModel.
     */
    PreviousPlatform,
    NextPlatform,

    /** Triggers: expand or collapse the hero chrome pills. */
    ToggleAccountPanel,
    ToggleSystemPanel,

    /** Face button X: RetroAchievements pill. */
    ToggleAchievementsPanel,

    Confirm,
    Cancel,
    /**
     * Per-game overrides (emulator, display target, …). Historically face-button adjacent;
     * currently bound to right stick click.
     */
    Options,
    /**
     * Scrape & library menu (Select on game select): scrapers, favourite, Choose Emulator.
     * On Home shortcuts, Select opens shortcut customize instead.
     */
    ScrapeMenu,
    /** Kept for call sites; favourite is primarily toggled from [ScrapeMenu]. */
    ToggleFavorite,
    Menu,
    SwapScreens,
    /**
     * Start+Select chord: open or close the Xbox-Guide-style shell menu.
     * Emitted once per chord (edge-triggered); individual Start/Select actions are suppressed.
     */
    ToggleGuide,
    ;

    val isDirectional: Boolean
        get() = this == Left || this == Right || this == Up || this == Down
}
