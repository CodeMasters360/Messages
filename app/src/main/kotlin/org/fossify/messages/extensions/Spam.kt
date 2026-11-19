package org.fossify.messages.extensions

import android.content.Context
import android.provider.Telephony
import org.fossify.commons.extensions.deleteBlockedNumber
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.BlockedNumber
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.helpers.SPAM_REASON_BLOCKED_NUMBER
import org.fossify.messages.helpers.SPAM_RETENTION_MS
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.interfaces.SpamMessagesDao
import org.fossify.messages.models.Message
import org.fossify.messages.models.SpamMessage

val Context.spamDB: SpamMessagesDao
    get() = getMessagesDB().SpamMessagesDao()

/**
 * Same as the Commons `isNumberBlocked`, but ALSO handles alphanumeric sender IDs
 * (e.g. "Google", "MyBank"): those are compared case-insensitively and may use
 * `*` wildcards (e.g. `Goo*`). Phone numbers keep using the Commons logic.
 */
fun Context.isSenderBlocked(
    address: String?,
    blockedNumbers: ArrayList<BlockedNumber> = getBlockedNumbers()
): Boolean {
    val sender = address?.trim().orEmpty()
    if (sender.isEmpty() || blockedNumbers.isEmpty()) {
        return false
    }

    if (isNumberBlocked(sender, blockedNumbers)) {
        return true
    }

    return blockedNumbers.any { blocked -> matchesBlockedEntry(sender, blocked.number) }
}

private fun matchesBlockedEntry(sender: String, rawEntry: String): Boolean {
    val entry = rawEntry.trim()
    if (entry.isEmpty()) {
        return false
    }

    return if (entry.contains("*")) {
        val pattern = entry.split("*").joinToString(separator = ".*") { Regex.escape(it) }
        Regex(pattern, RegexOption.IGNORE_CASE).matches(sender)
    } else {
        entry.equals(sender, ignoreCase = true)
    }
}

/**
 * Stores a message that was detected as spam. Nothing is written to the telephony
 * provider and no notification is shown. Never throws.
 */
fun Context.saveSpamMessage(
    address: String,
    subject: String,
    body: String,
    date: Long,
    subscriptionId: Int,
    reason: Int,
    isMms: Boolean = false
) {
    try {
        spamDB.insert(
            SpamMessage(
                address = address,
                subject = subject,
                body = body,
                date = date,
                subscriptionId = subscriptionId,
                reason = reason,
                isMms = isMms
            )
        )
    } catch (_: Exception) {
    }
}

fun Context.deleteOldSpamMessages() {
    try {
        spamDB.deleteOlderThan(System.currentTimeMillis() - SPAM_RETENTION_MS)
    } catch (_: Exception) {
    }
}

fun Context.deleteSpamMessages(messages: Collection<SpamMessage>) {
    try {
        messages.map { it.id }.chunked(SPAM_DELETE_CHUNK_SIZE).forEach { chunk ->
            spamDB.deleteByIds(chunk)
        }
    } catch (_: Exception) {
    }
}

private const val SPAM_DELETE_CHUNK_SIZE = 500

/**
 * "Not spam": moves messages from the spam folder into the normal inbox.
 * If the sender was blocked by number, the matching (non-wildcard) entries are
 * removed from the blocked list so that the conversation becomes visible again.
 */
fun Context.restoreSpamMessages(messages: List<SpamMessage>) {
    try {
        val blockedNumbers = getBlockedNumbers()
        val touchedThreads = HashSet<Long>()

        messages.forEach { spam ->
            if (spam.reason == SPAM_REASON_BLOCKED_NUMBER) {
                unblockSender(spam.address, blockedNumbers)
            }

            // MMS that were dropped before being downloaded have no content to restore
            if (spam.body.isNotBlank()) {
                restoreSingleSpamMessage(spam)?.let { touchedThreads.add(it) }
            }
        }

        deleteSpamMessages(messages)
        if (touchedThreads.isNotEmpty()) {
            refreshMessages()
            refreshConversations()
        }
    } catch (_: Exception) {
    }
}

private fun Context.unblockSender(address: String, blockedNumbers: ArrayList<BlockedNumber>) {
    blockedNumbers
        .filter { !it.number.contains("*") }
        .filter { isNumberBlocked(address, arrayListOf(it)) || it.number.trim().equals(address.trim(), true) }
        .forEach { deleteBlockedNumber(it.number) }
}

private fun Context.restoreSingleSpamMessage(spam: SpamMessage): Long? {
    val address = spam.address
    if (address.isBlank()) {
        return null
    }

    val threadId = getThreadId(address)
    val type = Telephony.Sms.MESSAGE_TYPE_INBOX
    val newMessageId = insertNewSMS(
        address = address,
        subject = spam.subject,
        body = spam.body,
        date = spam.date,
        read = 0,
        threadId = threadId,
        type = type,
        subscriptionId = spam.subscriptionId
    )

    getConversations(threadId).firstOrNull()?.let { conversation ->
        runCatching { insertOrUpdateConversation(conversation) }
    }

    val photoUri = SimpleContactsHelper(this).getPhotoUriFromPhoneNumber(address)
    val senderName = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
        getNameFromAddress(address, it)
    }

    val participant = SimpleContact(
        rawId = 0,
        contactId = 0,
        name = senderName,
        photoUri = photoUri,
        phoneNumbers = arrayListOf(PhoneNumber(value = address, type = 0, label = "", normalizedNumber = address)),
        birthdays = ArrayList(),
        anniversaries = ArrayList()
    )

    val message = Message(
        id = newMessageId,
        body = spam.body,
        type = type,
        status = -1,
        participants = arrayListOf(participant),
        date = (spam.date / 1000).toInt(),
        read = false,
        threadId = threadId,
        isMMS = false,
        attachment = null,
        senderPhoneNumber = address,
        senderName = senderName,
        senderPhotoUri = photoUri,
        subscriptionId = spam.subscriptionId
    )

    if (newMessageId != 0L) {
        messagesDB.insertOrUpdate(message)
    }

    return threadId
}
