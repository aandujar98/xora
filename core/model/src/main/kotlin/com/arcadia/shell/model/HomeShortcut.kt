package com.arcadia.shell.model

/** User-pinned tile on the Home hub shortcuts page. */
enum class HomeShortcutKind {
    /** Installed Android package — [HomeShortcut.target] is the package name. */
    AndroidApp,
    /** Library ROM / game — [HomeShortcut.target] is the game id. */
    Game,
    /** Still image — [HomeShortcut.target] is an absolute file path. */
    Picture,
    /** Animated GIF — [HomeShortcut.target] is an absolute file path. */
    Gif,
}

/**
 * How many grid cells a Home shortcut occupies (columns × rows).
 *
 * Board density is user-configurable (default 6×3); spans larger than the board are clamped.
 */
enum class ShortcutSpan(
    val colSpan: Int,
    val rowSpan: Int,
) {
    /** Standard square tile. */
    OneByOne(1, 1),
    /** Wide rectangle. */
    TwoByOne(2, 1),
    /** Large featured square. */
    TwoByTwo(2, 2),
    /** Extra-large featured block. */
    ThreeByTwo(3, 2),
    ;

    val label: String
        get() = "${colSpan}×${rowSpan}"

    fun next(): ShortcutSpan {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }

    /** True when this span fits on a board of [columns] × [rows]. */
    fun fitsIn(columns: Int, rows: Int): Boolean =
        colSpan <= columns && rowSpan <= rows

    /** Next span that still fits the board (cycles among allowed sizes). */
    fun nextFitting(columns: Int, rows: Int): ShortcutSpan {
        val allowed = allowedFor(columns, rows)
        if (allowed.isEmpty()) return Default
        val idx = allowed.indexOf(this)
        val start = if (idx < 0) 0 else idx
        return allowed[(start + 1) % allowed.size]
    }

    /**
     * Shrinks to the closest allowed span when the board is too small for this size.
     */
    fun clampTo(columns: Int, rows: Int): ShortcutSpan {
        if (fitsIn(columns, rows)) return this
        return allowedFor(columns, rows)
            .minByOrNull { kotlin.math.abs(it.colSpan - colSpan) + kotlin.math.abs(it.rowSpan - rowSpan) }
            ?: Default
    }

    companion object {
        val Default: ShortcutSpan = OneByOne

        fun fromStored(name: String?): ShortcutSpan =
            entries.firstOrNull { it.name == name } ?: Default

        fun allowedFor(columns: Int, rows: Int): List<ShortcutSpan> =
            entries.filter { it.fitsIn(columns, rows) }
    }
}

data class HomeShortcut(
    val id: String,
    val kind: HomeShortcutKind,
    val title: String,
    /** Package name, game id, or absolute media path depending on [kind]. */
    val target: String,
    /** Optional thumbnail path (absolute). */
    val artPath: String? = null,
    /** Tile size on the Home shortcuts grid. */
    val span: ShortcutSpan = ShortcutSpan.Default,
)
