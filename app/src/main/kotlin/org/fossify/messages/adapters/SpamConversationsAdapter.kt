package org.fossify.messages.adapters

import android.view.Menu
import android.widget.Toast
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.deleteBlockedNumber
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.activities.SimpleActivity
import org.fossify.messages.extensions.deleteConversation
import org.fossify.messages.extensions.unmarkConversationAsSpam
import org.fossify.messages.helpers.SpamFilter
import org.fossify.messages.helpers.refreshConversations
import org.fossify.messages.models.Conversation

class SpamConversationsAdapter(
    activity: SimpleActivity, recyclerView: MyRecyclerView, onRefresh: () -> Unit, itemClick: (Any) -> Unit
) : BaseConversationsAdapter(activity, recyclerView, onRefresh, itemClick) {
    override fun getActionMenuId() = R.menu.cab_spam_conversations

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_not_spam -> markConversationsAsNotSpam()
            R.id.cab_select_all -> selectAll()
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val items = resources.getQuantityString(R.plurals.delete_conversations, itemsCnt, itemsCnt)

        val baseString = org.fossify.commons.R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteConversations()
            }
        }
    }

    private fun deleteConversations() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val conversationsToRemove = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
        conversationsToRemove.forEach {
            activity.deleteConversation(it.threadId)
            activity.notificationManager.cancel(it.threadId.hashCode())
        }

        removeConversationsFromList(conversationsToRemove)
    }

    private fun markConversationsAsNotSpam() {
        if (selectedKeys.isEmpty()) {
            return
        }

        ensureBackgroundThread {
            val conversationsToRestore = currentList.filter { selectedKeys.contains(it.hashCode()) } as ArrayList<Conversation>
            var stillBlocked = false
            conversationsToRestore.forEach {
                // conversations of blocked senders are hidden from the main list, so try to
                // remove the sender from the blocked numbers as well
                runCatching { activity.deleteBlockedNumber(it.phoneNumber) }
                if (runCatching { SpamFilter.isSenderBlocked(activity, it.phoneNumber) }.getOrDefault(false)) {
                    stillBlocked = true
                }
                activity.unmarkConversationAsSpam(it.threadId)
            }

            if (stillBlocked) {
                activity.toast(R.string.not_spam_still_blocked, Toast.LENGTH_LONG)
            }

            removeConversationsFromList(conversationsToRestore)
        }
    }

    private fun removeConversationsFromList(removedConversations: List<Conversation>) {
        val newList = try {
            currentList.toMutableList().apply { removeAll(removedConversations) }
        } catch (ignored: Exception) {
            currentList.toMutableList()
        }

        activity.runOnUiThread {
            if (newList.none { selectedKeys.contains(it.hashCode()) }) {
                refreshConversations()
                finishActMode()
            } else {
                submitList(newList)
                if (newList.isEmpty()) {
                    refreshConversations()
                }
            }
        }
    }
}
