package com.arcadia.shell.model

/**
 * A launch recipe for one emulator, stored as data so support for a new emulator never requires a
 * code change. The schema deliberately mirrors Daijisho's player format so community-authored
 * configurations can be imported as-is.
 */
data class Player(
    val uniqueId: String,
    val name: String,
    /**
     * An `am start` style template, for example
     * `-n org.dolphinemu.dolphinemu/.ui.main.MainActivity -a android.intent.action.VIEW
     *  -e AutoStartFile {file.path}`.
     */
    val amStartArguments: String,
    /** Only files matching this survive assignment to the player. */
    val acceptedFilenameRegex: String,
    /**
     * Some emulators refuse to boot a second game while a previous session is resident, so the
     * shell can force-stop them first.
     */
    val killPackageProcesses: Boolean,
    val platformIds: Set<String>,
    val builtIn: Boolean,
) {
    /**
     * The package half of the `-n` component, used to check whether the emulator is installed.
     * Derived rather than stored so it can never disagree with [amStartArguments].
     */
    val packageName: String? by lazy {
        val component = COMPONENT_ARG.find(amStartArguments)?.groupValues?.getOrNull(1)
        component?.substringBefore('/')?.takeIf { it.isNotBlank() }
    }

    fun accepts(fileName: String): Boolean = runCatching {
        acceptedFilenameRegex.isBlank() || Regex(acceptedFilenameRegex).containsMatchIn(fileName)
    }.getOrDefault(false)

    private companion object {
        val COMPONENT_ARG = Regex("""-n\s+(\S+)""")
    }
}
