package org.fossify.messages.helpers

import android.content.Context
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.isSenderBlocked

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
     * Checks only the sender: blocked number / alphanumeric sender ID, or
     * "block unknown numbers" when that option is enabled.
     * Returns one of the SPAM_REASON_* constants, or null if the sender is fine.
     */
    fun getAddressSpamReason(context: Context, address: String): Int? {
        if (context.isSenderBlocked(address)) {
            return SPAM_REASON_BLOCKED_NUMBER
        }

        if (context.baseConfig.blockUnknownNumbers) {
            val privateCursor = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            val result = SimpleContactsHelper(context).existsSync(address, privateCursor)
            if (result == ContactLookupResult.NotFound) {
                return SPAM_REASON_UNKNOWN_SENDER
            }
        }

        return null
    }

    /**
     * Full spam check (keywords first, then the sender), same order as before.
     * Returns one of the SPAM_REASON_* constants, or null if the message is not spam.
     */
    fun getSpamReason(context: Context, address: String, body: String): Int? {
        if (isMessageFilteredOut(context, body)) {
            return SPAM_REASON_BLOCKED_KEYWORD
        }

        return getAddressSpamReason(context, address)
    }
}
