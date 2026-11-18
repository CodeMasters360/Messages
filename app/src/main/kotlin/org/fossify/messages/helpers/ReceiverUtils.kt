package org.fossify.messages.helpers

import android.content.Context
import org.fossify.messages.extensions.config
import org.fossify.messages.filter.FilterEngine

object ReceiverUtils {

    fun isMessageFilteredOut(context: Context, body: String): Boolean {
        for (blockedKeyword in context.config.blockedKeywords) {
            if (body.contains(blockedKeyword, ignoreCase = true)) {
                return true
            }
        }

        return false
    }

    /**
     * Evaluates the advanced filter rules. [sender] or [body] can be null when not known yet (MMS).
     * ALLOWED means "never block, skip every other block check".
     */
    fun evaluateFilterRules(context: Context, sender: String?, body: String?): FilterEngine.Result {
        return FilterEngine.evaluate(context.config.filterRules, sender, body)
    }
}
