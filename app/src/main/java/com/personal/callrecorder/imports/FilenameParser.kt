package com.personal.callrecorder.imports

import com.personal.callrecorder.call.CallDirection

/**
 * Best-effort extraction of a phone number, direction, and a display name from
 * an OEM recording's filename. OEM naming varies wildly (OPPO/ColorOS embeds the
 * contact name or number plus a timestamp), so every field is optional and the
 * caller falls back gracefully. The reliable date comes from the file's
 * last-modified time, not the name, so we don't parse timestamps here.
 */
object FilenameParser {

    data class Parsed(
        val number: String?,
        val direction: CallDirection,
        val displayFallback: String?
    )

    private val phoneRegex = Regex("""\+?\d[\d\s\-]{5,14}\d""")
    private val dateLikeRegex = Regex("""^(19|20)\d{6,}$""")

    fun parse(fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.')
        val lower = base.lowercase()

        val direction = when {
            "incoming" in lower || "callin" in lower -> CallDirection.INCOMING
            "outgoing" in lower || "callout" in lower -> CallDirection.OUTGOING
            else -> CallDirection.UNKNOWN
        }

        val number = phoneRegex.findAll(base)
            .map { it.value.replace(Regex("[\\s\\-]"), "") }
            .filter { it.length in 7..15 }
            .filterNot { dateLikeRegex.matches(it.removePrefix("+")) }
            .firstOrNull()

        // Strip numbers/timestamps/separators to expose an embedded contact name.
        val nameGuess = base
            .replace(phoneRegex, " ")
            .replace(Regex("""\d{6,}"""), " ")
            .replace(Regex("""[_\-.@]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { guess -> guess.length in 2..40 && guess.any { it.isLetter() } }

        return Parsed(number, direction, nameGuess)
    }
}
