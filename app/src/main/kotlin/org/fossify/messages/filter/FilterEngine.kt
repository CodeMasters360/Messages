package org.fossify.messages.filter

import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Decides whether a message (sender + text) is blocked. No Android dependencies. */
object FilterEngine {

    enum class Decision { NO_MATCH, BLOCKED, ALLOWED }

    data class Result(val decision: Decision, val rule: FilterRule? = null) {
        val isBlocked: Boolean get() = decision == Decision.BLOCKED
        val isAllowed: Boolean get() = decision == Decision.ALLOWED
    }

    enum class Problem { EMPTY, NO_SENDER_PATTERN, NO_WORDS, SENDER_REGEX, BODY_REGEX }

    data class Validation(val problem: Problem, val detail: String = "")

    /**
     * ALLOW rules are checked first and always win over BLOCK rules.
     * A null [sender] or [body] means "unknown": rules with a condition on the unknown part are skipped.
     * (Used for MMS where the sender and the text arrive in separate callbacks.)
     */
    fun evaluate(rules: List<FilterRule>, sender: String?, body: String?): Result {
        for (rule in rules) {
            if (rule.enabled && rule.action == FilterAction.ALLOW && matches(rule, sender, body)) {
                return Result(Decision.ALLOWED, rule)
            }
        }
        for (rule in rules) {
            if (rule.enabled && rule.action == FilterAction.BLOCK && matches(rule, sender, body)) {
                return Result(Decision.BLOCKED, rule)
            }
        }
        return Result(Decision.NO_MATCH)
    }

    fun matches(rule: FilterRule, sender: String?, body: String?): Boolean {
        if (!rule.hasSender && !rule.hasBody) return false
        if (rule.hasSender && (sender == null || !matchSender(rule, sender))) return false
        if (rule.hasBody && (body == null || !matchBody(rule, body))) return false
        return true
    }

    /** @return null when the rule is fine. */
    fun validate(rule: FilterRule): Validation? {
        if (!rule.hasSender && !rule.hasBody) return Validation(Problem.EMPTY)

        if (rule.hasSender) {
            if (rule.senderMode == SenderMode.REGEX) {
                checkRegex(splitLines(rule.senderText), rule.matchCase)?.let {
                    return Validation(Problem.SENDER_REGEX, it)
                }
            } else if (splitTerms(rule.senderText).isEmpty()) {
                return Validation(Problem.NO_SENDER_PATTERN)
            }
        }
        if (rule.hasBody) {
            if (rule.bodyMode == BodyMode.REGEX) {
                checkRegex(splitLines(rule.bodyText), rule.matchCase)?.let {
                    return Validation(Problem.BODY_REGEX, it)
                }
            } else if (splitTerms(rule.bodyText).isEmpty()) {
                return Validation(Problem.NO_WORDS)
            }
        }
        return null
    }

    // ------------------------------------------------------------------ sender

    private fun matchSender(rule: FilterRule, sender: String): Boolean {
        val s = sender.trim()
        if (rule.senderMode == SenderMode.REGEX) {
            return regexFindAny(splitLines(rule.senderText), s, rule.matchCase)
        }
        return splitTerms(rule.senderText).any { wildcardSender(it, s) }
    }

    private fun wildcardSender(pattern: String, sender: String): Boolean {
        val p = pattern.trim()
        if (p.isEmpty()) return false

        // plain case-insensitive comparison: "google", "*bank*", "+1456*" ...
        if (globMatch(p, sender, ignoreCase = true)) return true

        // phone numbers: ignore "+", spaces, dashes, brackets and a leading 00
        if (looksNumeric(p) && looksNumeric(sender)) {
            val np = normalizePhone(p)
            val ns = normalizePhone(sender)
            if (np.isNotEmpty() && globMatch(np, ns, ignoreCase = false)) return true
        }
        return false
    }

    private fun looksNumeric(s: String): Boolean =
        s.isNotEmpty() && s.all { it in '0'..'9' || "+*-(). ".indexOf(it) >= 0 }

    private fun normalizePhone(input: String): String {
        val s = input.filter { it in '0'..'9' || it == '*' }
        return if (s.startsWith("00")) s.substring(2) else s
    }

    private fun globMatch(glob: String, text: String, ignoreCase: Boolean): Boolean {
        val parts = glob.split("*")
        val re = StringBuilder()
        parts.forEachIndexed { i, part ->
            if (i > 0) re.append(".*")
            if (part.isNotEmpty()) re.append(Pattern.quote(part))
        }
        var flags = Pattern.DOTALL
        if (ignoreCase) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return Pattern.compile(re.toString(), flags).matcher(text).matches()
    }

    // ------------------------------------------------------------------ body

    private fun matchBody(rule: FilterRule, body: String): Boolean {
        if (rule.bodyMode == BodyMode.REGEX) {
            return regexFindAny(splitLines(rule.bodyText), body, rule.matchCase)
        }

        val terms = splitTerms(rule.bodyText)
        if (terms.isEmpty()) return false

        val haystack = prepare(body, rule.matchCase)
        val all = rule.bodyMode == BodyMode.ALL
        for (term in terms) {
            val found = haystack.contains(prepare(term, rule.matchCase))
            if (all && !found) return false
            if (!all && found) return true
        }
        return all
    }

    private fun prepare(s: String, matchCase: Boolean): String {
        val folded = fold(s)
        return if (matchCase) folded else folded.lowercase(Locale.ROOT)
    }

    // ------------------------------------------------------------------ regex helpers

    private fun regexFlags(matchCase: Boolean): Int {
        var flags = Pattern.DOTALL
        if (!matchCase) flags = flags or Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        return flags
    }

    private fun regexFindAny(patterns: List<String>, text: String, matchCase: Boolean): Boolean {
        val flags = regexFlags(matchCase)
        val folded = fold(text)
        for (p in patterns) {
            try {
                val compiled = Pattern.compile(p, flags)
                if (compiled.matcher(LimitedCharSequence(text)).find()) return true
                if (folded != text && compiled.matcher(LimitedCharSequence(folded)).find()) return true
            } catch (ignored: RuntimeException) {
                // invalid or far too slow pattern: treat as "no match"
            } catch (ignored: StackOverflowError) {
            }
        }
        return false
    }

    private fun checkRegex(patterns: List<String>, matchCase: Boolean): String? {
        val flags = regexFlags(matchCase)
        for (p in patterns) {
            try {
                Pattern.compile(p, flags)
            } catch (e: PatternSyntaxException) {
                return "${e.description} in: $p"
            }
        }
        return null
    }

    /** CharSequence that gives up after too many reads, so a bad regex cannot freeze the app. */
    private class LimitedCharSequence(private val inner: CharSequence) : CharSequence {
        private var budget = 2_000_000

        override val length: Int get() = inner.length

        override fun get(index: Int): Char {
            if (--budget < 0) throw IllegalStateException("Regex took too long")
            return inner[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            inner.subSequence(startIndex, endIndex)

        override fun toString(): String = inner.toString()
    }

    // ------------------------------------------------------------------ text helpers

    /** One entry per line (used for regex). */
    internal fun splitLines(text: String): List<String> =
        text.split(Regex("[\\r\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }

    /** One entry per line, or separated by commas / semicolons. */
    internal fun splitTerms(text: String): List<String> =
        text.split(Regex("[\\r\\n,;]+")).map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Makes Persian / Arabic text comparable: Arabic-Indic and Persian digits become 0-9,
     * Arabic yeh and kaf become the Persian letters.
     */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch) {
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    '\u064A' -> '\u06CC'
                    '\u0643' -> '\u06A9'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}
