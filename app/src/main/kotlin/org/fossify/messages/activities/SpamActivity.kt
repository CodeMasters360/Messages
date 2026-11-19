package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.messages.R
import org.fossify.messages.adapters.SpamMessagesAdapter
import org.fossify.messages.databinding.ActivitySpamBinding
import org.fossify.messages.extensions.restoreSpamMessages
import org.fossify.messages.extensions.spamDB
import org.fossify.messages.models.SpamMessage

/**
 * Shows the messages that were detected as spam (blocked numbers, blocked sender names,
 * blocked keywords, unknown senders). They never trigger a notification.
 */
class SpamActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivitySpamBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.spamList))
        setupMaterialScrollListener(
            scrollingView = binding.spamList,
            topAppBar = binding.spamAppbar
        )
        updateTextColors(binding.spamWrapper)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.spamAppbar, NavigationIcon.Arrow)
        loadSpamMessages()
    }

    override fun refreshItems() {
        loadSpamMessages()
    }

    private fun setupOptionsMenu() {
        binding.spamToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.empty_spam -> {
                    emptySpamFolder()
                    true
                }

                else -> false
            }
        }
    }

    private fun loadSpamMessages() {
        ensureBackgroundThread {
            val messages: ArrayList<SpamMessage> = try {
                ArrayList(spamDB.getAll())
            } catch (_: Exception) {
                ArrayList()
            }

            runOnUiThread {
                showSpamMessages(messages)
            }
        }
    }

    private fun showSpamMessages(messages: ArrayList<SpamMessage>) {
        SpamMessagesAdapter(
            activity = this,
            spamMessages = messages,
            listener = this,
            recyclerView = binding.spamList
        ) {
            onSpamMessageClicked(it as SpamMessage)
        }.apply {
            binding.spamList.adapter = this
        }

        binding.spamPlaceholder.beVisibleIf(messages.isEmpty())
        binding.spamToolbar.menu.findItem(R.id.empty_spam)?.isVisible = messages.isNotEmpty()
    }

    private fun onSpamMessageClicked(spamMessage: SpamMessage) {
        val text = spamMessage.body.ifBlank { getString(R.string.spam_mms_placeholder) }
        val title = spamMessage.address.ifBlank { getString(R.string.spam_unknown_sender) }
        ConfirmationDialog(
            activity = this,
            message = text,
            positive = R.string.not_spam,
            negative = org.fossify.commons.R.string.cancel,
            dialogTitle = title
        ) {
            ensureBackgroundThread {
                restoreSpamMessages(listOf(spamMessage))
                runOnUiThread {
                    toast(R.string.messages_moved_out_of_spam)
                    loadSpamMessages()
                }
            }
        }
    }

    private fun emptySpamFolder() {
        ConfirmationDialog(
            activity = this,
            message = "",
            messageId = R.string.empty_spam_confirmation,
            positive = org.fossify.commons.R.string.yes,
            negative = org.fossify.commons.R.string.no
        ) {
            ensureBackgroundThread {
                try {
                    spamDB.deleteAll()
                } catch (_: Exception) {
                }
                loadSpamMessages()
            }
        }
    }
}
