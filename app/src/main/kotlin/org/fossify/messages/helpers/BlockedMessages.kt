package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getNameFromAddress
import org.fossify.messages.extensions.getThreadId
import org.fossify.messages.extensions.insertNewSMS
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.extensions.shouldUnarchive
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.models.Message

object BlockedMessages {

    /**
     * Puts a quarantined SMS into the normal inbox (the same steps SmsReceiver does for a new message,
     * but without showing a notification). Must be called from a background thread.
     */
    fun restore(context: Context, item: BlockedMessagesDb.Item) {
        val address = item.address
        val threadId = context.getThreadId(address)
        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX

        val newMessageId = context.insertNewSMS(
            address = address,
            subject = item.subject,
            body = item.body,
            date = item.date,
            read = 0,
            threadId = threadId,
            type = type,
            subscriptionId = item.subscriptionId
        )

        context.getConversations(threadId).firstOrNull()?.let { conversation ->
            runCatching { context.insertOrUpdateConversation(conversation) }
        }

        val senderName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
            context.getNameFromAddress(address, it)
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
            body = item.body,
            type = type,
            status = Telephony.Sms.STATUS_NONE,
            participants = arrayListOf(participant),
            date = (item.date / 1000).toInt(),
            read = false,
            threadId = threadId,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = address,
            senderName = senderName,
            senderPhotoUri = photoUri,
            subscriptionId = item.subscriptionId
        )

        context.messagesDB.insertOrUpdate(message)

        if (context.shouldUnarchive()) {
            context.updateConversationArchivedStatus(threadId, false)
        }

        refreshMessages()
        refreshConversations()
    }
}
