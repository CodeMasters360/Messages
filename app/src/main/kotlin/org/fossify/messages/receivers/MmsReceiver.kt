package org.fossify.messages.receivers

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony.Mms
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getConversations
import org.fossify.messages.extensions.getMMS
import org.fossify.messages.extensions.getLatestMMS
import org.fossify.messages.extensions.getNameFromAddress
import org.fossify.messages.extensions.insertOrUpdateConversation
import org.fossify.messages.extensions.isConversationSpam
import org.fossify.messages.extensions.markConversationAsSpam
import org.fossify.messages.extensions.shouldUnarchive
import org.fossify.messages.extensions.showReceivedMessageNotification
import org.fossify.messages.extensions.unmarkConversationAsSpam
import org.fossify.messages.extensions.updateConversationArchivedStatus
import org.fossify.messages.helpers.ReceiverUtils.isMessageFilteredOut
import org.fossify.messages.helpers.SpamFilter
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.models.Message

class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        // When the Spam folder is enabled the MMS must be downloaded, otherwise there is
        // nothing to move to Spam. The spam check is done in handleMmsMessage() instead.
        if (context.config.moveBlockedToSpam) return false

        return SpamFilter.isSenderBlocked(context, address)
            || SpamFilter.isBlockedAsUnknownNumber(context, address)
    }

    override fun isContentBlocked(context: Context, content: String): Boolean {
        if (context.config.moveBlockedToSpam) return false

        return isMessageFilteredOut(context, content)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri?) {
        val mms = if (messageUri != null) {
            context.getMMS(messageUri)
        } else {
            context.getLatestMMS()
        } ?: return

        val address = mms.getSender()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: mms.senderPhoneNumber
        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        ensureBackgroundThread {
            handleMmsMessage(context, mms, size, address)
        }
    }

    override fun onError(context: Context, error: String) {
        context.showErrorToast(context.getString(R.string.couldnt_download_mms))
    }

    private fun handleMmsMessage(
        context: Context,
        mms: Message,
        size: Int,
        address: String
    ) {
        val isSpam = context.config.moveBlockedToSpam &&
            SpamFilter.isSpam(context, address, mms.body)
        if (isSpam) {
            handleSpamMms(context, mms)
            return
        }

        val glideBitmap = try {
            Glide.with(context)
                .asBitmap()
                .load(mms.attachment!!.attachments.first().getUri())
                .centerCrop()
                .into(size, size)
                .get()
        } catch (e: Exception) {
            null
        }


        val senderName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
            context.getNameFromAddress(address, it)
        }

        context.showReceivedMessageNotification(
            messageId = mms.id,
            isMms = true,
            address = address,
            senderName = senderName,
            body = mms.body,
            threadId = mms.threadId,
            bitmap = glideBitmap
        )

        val conversation = context.getConversations(mms.threadId).firstOrNull() ?: return
        runCatching { context.insertOrUpdateConversation(conversation) }
        // a regular message in a conversation that was moved to Spam brings it back
        if (context.isConversationSpam(mms.threadId)) {
            context.unmarkConversationAsSpam(mms.threadId)
        }
        if (context.shouldUnarchive()) {
            context.updateConversationArchivedStatus(mms.threadId, false)
        }
        refreshMessages()
        refreshConversations()
    }

    /**
     * Keeps the MMS, but silently: it's marked as read, its conversation is moved to the
     * Spam folder and no notification is shown.
     */
    private fun handleSpamMms(context: Context, mms: Message) {
        runCatching {
            val values = ContentValues().apply {
                put(Mms.READ, 1)
                put(Mms.SEEN, 1)
            }
            context.contentResolver.update(
                Mms.CONTENT_URI,
                values,
                "${Mms._ID} = ?",
                arrayOf(mms.id.toString())
            )
        }

        // flag the thread first, so it never shows up in the main conversations list
        context.markConversationAsSpam(mms.threadId)
        context.getConversations(mms.threadId).firstOrNull()?.let { conversation ->
            runCatching { context.insertOrUpdateConversation(conversation) }
        }
        refreshMessages()
        refreshConversations()
    }
}
