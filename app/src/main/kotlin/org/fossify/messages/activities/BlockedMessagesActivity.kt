package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.adapters.BlockedMessagesAdapter
import org.fossify.messages.databinding.ActivityBlockedMessagesBinding
import org.fossify.messages.helpers.BlockedMessages
import org.fossify.messages.helpers.BlockedMessagesDb

/** The "Blocked messages" folder. */
class BlockedMessagesActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityBlockedMessagesBinding::inflate)
    private var adapter: BlockedMessagesAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.blockedMessagesList))
        setupMaterialScrollListener(
            scrollingView = binding.blockedMessagesList,
            topAppBar = binding.blockedMessagesAppbar
        )
        updateTextColors(binding.blockedMessagesWrapper)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.blockedMessagesAppbar, NavigationIcon.Arrow)
        refreshItems()
    }

    private fun setupOptionsMenu() {
        binding.blockedMessagesToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.delete_all_blocked_messages -> {
                    confirmDeleteAll()
                    true
                }

                else -> false
            }
        }
    }

    private fun refreshItems() {
        ensureBackgroundThread {
            val items = BlockedMessagesDb.get(this).list()
            runOnUiThread {
                val current = adapter
                if (current == null) {
                    val newAdapter = BlockedMessagesAdapter(this, items) { item -> showActions(item) }
                    adapter = newAdapter
                    binding.blockedMessagesList.adapter = newAdapter
                } else {
                    current.submit(items)
                }
                binding.blockedMessagesPlaceholder.beVisibleIf(items.isEmpty())
            }
        }
    }

    private fun showActions(item: BlockedMessagesDb.Item) {
        val actions = arrayListOf(
            RadioItem(0, getString(R.string.blocked_message_restore)),
            RadioItem(1, getString(R.string.blocked_message_never_block)),
            RadioItem(2, getString(R.string.blocked_message_copy)),
            RadioItem(3, getString(R.string.filter_delete))
        )

        RadioGroupDialog(this, actions, -1) {
            when (it as Int) {
                0 -> restore(item)
                1 -> neverBlockSender(item)
                2 -> copyToClipboard(item.body)
                3 -> {
                    ensureBackgroundThread {
                        BlockedMessagesDb.get(this).delete(item.id)
                        runOnUiThread { refreshItems() }
                    }
                }
            }
        }
    }

    private fun restore(item: BlockedMessagesDb.Item) {
        ensureBackgroundThread {
            try {
                BlockedMessages.restore(applicationContext, item)
                BlockedMessagesDb.get(this).delete(item.id)
                runOnUiThread {
                    toast(R.string.blocked_message_restored, Toast.LENGTH_LONG)
                    refreshItems()
                }
            } catch (e: Exception) {
                runOnUiThread { showErrorToast(e) }
            }
        }
    }

    private fun neverBlockSender(item: BlockedMessagesDb.Item) {
        Intent(this, FilterRuleEditActivity::class.java).apply {
            putExtra(FilterRuleEditActivity.EXTRA_PREFILL_SENDER, item.address)
            startActivity(this)
        }
    }

    private fun confirmDeleteAll() {
        ConfirmationDialog(this, getString(R.string.blocked_delete_all_confirm)) {
            ensureBackgroundThread {
                BlockedMessagesDb.get(this).clear()
                runOnUiThread { refreshItems() }
            }
        }
    }
}
