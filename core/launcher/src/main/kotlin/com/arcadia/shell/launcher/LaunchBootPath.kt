package com.arcadia.shell.launcher

import com.arcadia.shell.model.Game

/**
 * Value for emulators that fopen() a string extra (NetherSX2 / AetherSX2 `bootPath`).
 *
 * Those cores treat the extra as a filename. Wrapping a real path in this app's FileProvider
 * (`content://com.sora.shell.files/rom_library/…`) makes the emulator try to open that URI as a
 * path, which fails with "Requested filename 'content://…'". Prefer the filesystem path; fall
 * back to a persisted SAF document URI the emulator can open itself. Never mint a FileProvider URI.
 */
internal object LaunchBootPath {
    const val PLACEHOLDER = "{file.bootpath}"

    fun resolve(game: Game): String {
        game.filePath?.takeIf { it.isNotBlank() }?.let { return it }
        game.documentUri?.takeIf { it.isNotBlank() }?.let { return it }
        throw MissingPlaceholderException(
            PLACEHOLDER,
            "${game.title} has neither a file path nor a document uri.",
        )
    }
}
