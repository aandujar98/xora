package com.arcadia.shell.launcher

sealed interface AmExtra {
    val key: String

    data class StringValue(override val key: String, val value: String) : AmExtra
    data class BooleanValue(override val key: String, val value: Boolean) : AmExtra
    data class IntValue(override val key: String, val value: Int) : AmExtra
    data class LongValue(override val key: String, val value: Long) : AmExtra
    data class FloatValue(override val key: String, val value: Float) : AmExtra
}

/**
 * The parsed form of an `am start` template. Kept as an inert description so a template can be
 * validated and shown to the user before anything is actually launched.
 */
data class AmArguments(
    val packageName: String? = null,
    val className: String? = null,
    val action: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
    val categories: List<String> = emptyList(),
    val extras: List<AmExtra> = emptyList(),
    val flags: Int = 0,
    val clearTask: Boolean = false,
    val clearTop: Boolean = false,
    val unknownTokens: List<String> = emptyList(),
) {
    val hasComponent: Boolean get() = packageName != null && className != null
}

/**
 * Parses the `am start` argument syntax that every Android emulation frontend has converged on.
 *
 * Storing launch recipes as text rather than code is what allows support for a new emulator to be
 * added by editing a field, and lets configurations authored for Daijisho be imported unchanged.
 */
object AmArgumentParser {

    private val KNOWN_FLAGS = setOf(
        "-n", "-a", "-d", "-t", "-c", "-f",
        "-e", "--es", "--ez", "--ei", "--el", "--ef",
        "--activity-clear-task", "--activity-clear-top",
    )

    fun parse(template: String): AmArguments {
        val tokens = tokenize(template)
        var result = AmArguments()
        var index = 0

        while (index < tokens.size) {
            val token = tokens[index]
            val first = tokens.valueAt(index + 1)
            val second = tokens.valueAt(index + 2)

            // Each branch reports how many *additional* tokens it consumed beyond the flag itself,
            // so a malformed trailing pair skips cleanly instead of being reparsed as a new flag.
            val consumed: Int = when (token) {
                "-n" -> if (first == null) 0 else {
                    result = result.copy(
                        packageName = first.substringBefore('/'),
                        className = expandClassName(first),
                    )
                    1
                }

                "-a" -> if (first == null) 0 else { result = result.copy(action = first); 1 }
                "-d" -> if (first == null) 0 else { result = result.copy(data = first); 1 }
                "-t" -> if (first == null) 0 else { result = result.copy(mimeType = first); 1 }
                "-c" -> if (first == null) 0 else {
                    result = result.copy(categories = result.categories + first)
                    1
                }

                "-e", "--es" -> if (first == null || second == null) 0 else {
                    result = result.addExtra(AmExtra.StringValue(first, second))
                    2
                }

                "--ez" -> if (first == null || second == null) 0 else {
                    // am accepts "true" and "1"; anything else is false.
                    val flag = second.equals("true", ignoreCase = true) || second == "1"
                    result = result.addExtra(AmExtra.BooleanValue(first, flag))
                    2
                }

                "--ei" -> if (first == null || second == null) 0 else {
                    second.toIntOrNull()?.let { result = result.addExtra(AmExtra.IntValue(first, it)) }
                    2
                }

                "--el" -> if (first == null || second == null) 0 else {
                    second.toLongOrNull()?.let { result = result.addExtra(AmExtra.LongValue(first, it)) }
                    2
                }

                "--ef" -> if (first == null || second == null) 0 else {
                    second.toFloatOrNull()?.let { result = result.addExtra(AmExtra.FloatValue(first, it)) }
                    2
                }

                "-f" -> if (first == null) 0 else {
                    parseFlag(first)?.let { result = result.copy(flags = result.flags or it) }
                    1
                }

                "--activity-clear-task" -> { result = result.copy(clearTask = true); 0 }
                "--activity-clear-top" -> { result = result.copy(clearTop = true); 0 }

                else -> {
                    if (token.isNotBlank()) {
                        result = result.copy(unknownTokens = result.unknownTokens + token)
                    }
                    0
                }
            }

            index += 1 + consumed
        }

        return result
    }

    /**
     * A token is only usable as a value if it is not itself a flag, so a template that is missing an
     * argument degrades to ignoring that one flag instead of eating the rest of the line.
     *
     * Matching against the known flag names rather than a leading dash is deliberate: `--ei slot -1`
     * has to keep working.
     */
    private fun List<String>.valueAt(index: Int): String? =
        getOrNull(index)?.takeIf { it !in KNOWN_FLAGS }

    private fun AmArguments.addExtra(extra: AmExtra): AmArguments = copy(extras = extras + extra)

    /** `-n pkg/.Activity` is shorthand for `-n pkg/pkg.Activity`, exactly as adb expands it. */
    private fun expandClassName(component: String): String? {
        val packageName = component.substringBefore('/')
        val className = component.substringAfter('/', "")
        return when {
            className.isEmpty() -> null
            className.startsWith(".") -> packageName + className
            else -> className
        }
    }

    private fun parseFlag(raw: String): Int? =
        if (raw.startsWith("0x", ignoreCase = true)) {
            raw.drop(2).toLongOrNull(16)?.toInt()
        } else {
            raw.toIntOrNull()
        }

    /**
     * Splits on whitespace while honouring quotes, because paths and titles routinely contain
     * spaces and a naive split would tear them apart.
     */
    internal fun tokenize(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null

        for (char in input) {
            when {
                quote != null && char == quote -> quote = null
                quote != null -> current.append(char)
                char == '"' || char == '\'' -> quote = char
                char.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) tokens += current.toString()

        return tokens
    }
}
