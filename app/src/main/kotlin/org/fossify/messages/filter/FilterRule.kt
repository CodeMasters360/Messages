package org.fossify.messages.filter

import java.util.UUID

enum class FilterAction { BLOCK, ALLOW }

enum class SenderMode { WILDCARD, REGEX }

enum class BodyMode { ANY, ALL, REGEX }

/**
 * One filtering rule. The sender condition and the text condition are both optional but at least
 * one has to be set. If both are set, both must match.
 *
 * Sender (WILDCARD): one pattern per line or separated by commas, `*` matches anything.
 *   Works for numbers (`+1456*`) and for text sender names (`google`, `*bank*`), case-insensitive.
 * Text (ANY / ALL): words or phrases, one per line or separated by commas.
 * REGEX modes: one regular expression per line (Java syntax, partial match).
 */
data class FilterRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val action: FilterAction = FilterAction.BLOCK,
    val senderText: String = "",
    val senderMode: SenderMode = SenderMode.WILDCARD,
    val bodyText: String = "",
    val bodyMode: BodyMode = BodyMode.ANY,
    val matchCase: Boolean = false
) {
    val hasSender: Boolean get() = senderText.isNotBlank()
    val hasBody: Boolean get() = bodyText.isNotBlank()

    /** Short one-line description, e.g. for a list. */
    fun summary(): String = buildString {
        append(if (action == FilterAction.ALLOW) "ALLOW" else "BLOCK")
        if (hasSender) {
            append(" | sender")
            append(if (senderMode == SenderMode.REGEX) " regex: " else ": ")
            append(shorten(senderText))
        }
        if (hasBody) {
            append(" | text ")
            append(
                when (bodyMode) {
                    BodyMode.ALL -> "ALL of: "
                    BodyMode.REGEX -> "regex: "
                    BodyMode.ANY -> "ANY of: "
                }
            )
            append(shorten(bodyText))
        }
    }

    private fun shorten(s: String): String {
        val oneLine = s.trim().replace(Regex("\\s*[\\r\\n]+\\s*"), ", ")
        return if (oneLine.length > 50) oneLine.take(50) + "..." else oneLine
    }
}
