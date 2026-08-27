package com.arcadia.shell.model

/**
 * Turns a ROM filename into something worth putting under box art.
 *
 * Dump filenames carry a lot of provenance noise: No-Intro region and language tags, TOSEC dump
 * flags, revision markers, and scene group suffixes. Stripping them also makes the result a much
 * better search key when a hash lookup misses and the scraper has to fall back to title matching.
 */
object TitleCleaner {

    private val bracketedGroups = Regex("""[\(\[][^\)\]]*[\)\]]""")
    private val separators = Regex("""[_.]+""")
    private val collapseSpaces = Regex("""\s{2,}""")
    private val discMarker = Regex("""\b(disc|disk|cd)\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val leadingArticle = Regex("""^(the|a|an)\s+""", RegexOption.IGNORE_CASE)

    /** "Super Mario 64 (USA) [!].z64" becomes "Super Mario 64". */
    fun clean(fileName: String): String {
        val withoutExtension = fileName.substringBeforeLast('.', fileName)

        var title = withoutExtension
            .replace(bracketedGroups, " ")
            .replace(separators, " ")
            .replace(collapseSpaces, " ")
            .trim()
            .trim('-', ' ')

        // A trailing "Disc 2" is real information, so it survives when the tags around it do not.
        val disc = discMarker.find(withoutExtension)
        if (disc != null && !title.contains(disc.value, ignoreCase = true)) {
            title = "$title (Disc ${disc.groupValues[2]})"
        }

        return title.ifBlank { withoutExtension }
    }

    /** Sort key that ignores a leading article and case, so "The Legend of Zelda" files under L. */
    fun sortKey(title: String): String =
        title.replace(leadingArticle, "").lowercase().trim()

    fun extensionOf(fileName: String): String =
        fileName.substringAfterLast('.', "").lowercase()
}
