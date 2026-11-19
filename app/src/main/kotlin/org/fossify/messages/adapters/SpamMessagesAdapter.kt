package org.fossify.messages.adapters

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.formatDateOrTime
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyRecyclerView
import org.fossify.messages.R
import org.fossify.messages.databinding.ItemSpamMessageBinding
import org.fossify.messages.extensions.deleteSpamMessages
import org.fossify.messages.extensions.restoreSpamMessages
import org.fossify.messages.helpers.SPAM_REASON_BLOCKED_KEYWORD
import org.fossify.messages.helpers.SPAM_REASON_BLOCKED_NUMBER
import org.fossify.messages.helpers.SPAM_REASON_UNKNOWN_SENDER
import org.fossify.messages.models.SpamMessage

class SpamMessagesAdapter(
    activity: BaseSimpleActivity,
    var spamMessages: ArrayList<SpamMessage>,
    val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_spam

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_not_spam -> restoreSelection()
            R.id.cab_delete -> deleteSelection()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = spamMessages.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = spamMessages.getOrNull(position)?.id?.hashCode()

    override fun getItemKeyPosition(key: Int) = spamMessages.indexOfFirst { it.id.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSpamMessageBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val spamMessage = spamMessages[position]
        holder.bindView(spamMessage, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, spamMessage)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = spamMessages.size

    private fun getSelectedItems() = spamMessages.filter { selectedKeys.contains(it.id.hashCode()) }

    private fun setupView(view: View, spamMessage: SpamMessage) {
        ItemSpamMessageBinding.bind(view).apply {
            root.setupViewBackground(activity)
            spamItemHolder.isSelected = selectedKeys.contains(spamMessage.id.hashCode())

            spamItemAddress.apply {
                text = spamMessage.address.ifBlank { activity.getString(R.string.spam_unknown_sender) }
                setTextColor(textColor)
            }

            spamItemBody.apply {
                text = spamMessage.body.ifBlank { activity.getString(R.string.spam_mms_placeholder) }
                setTextColor(textColor)
            }

            val date = spamMessage.date.formatDateOrTime(
                context = activity,
                hideTimeOnOtherDays = false,
                showCurrentYear = false
            )
            spamItemInfo.apply {
                text = "$date · ${activity.getString(getReasonTextRes(spamMessage.reason))}"
                setTextColor(textColor)
            }

            overflowMenuIcon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }

            overflowMenuIcon.setOnClickListener {
                showPopupMenu(overflowMenuAnchor, spamMessage)
            }
        }
    }

    private fun getReasonTextRes(reason: Int) = when (reason) {
        SPAM_REASON_BLOCKED_NUMBER -> R.string.spam_reason_blocked_number
        SPAM_REASON_BLOCKED_KEYWORD -> R.string.spam_reason_blocked_keyword
        SPAM_REASON_UNKNOWN_SENDER -> R.string.spam_reason_unknown_sender
        else -> R.string.spam_folder
    }

    private fun showPopupMenu(view: View, spamMessage: SpamMessage) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            menu.findItem(R.id.cab_select_all)?.isVisible = false
            setOnMenuItemClickListener { item ->
                val key = spamMessage.id.hashCode()
                when (item.itemId) {
                    R.id.cab_not_spam -> executeItemMenuOperation(key) { restoreSelection() }
                    R.id.cab_delete -> executeItemMenuOperation(key) { deleteSelection() }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(key: Int, callback: () -> Unit) {
        selectedKeys.add(key)
        callback()
        selectedKeys.remove(key)
    }

    private fun removeFromList(selected: List<SpamMessage>) {
        val positions = getSelectedItemPositions()
        val ids = selected.map { it.id }.toSet()
        spamMessages.removeAll { it.id in ids }
        removeSelectedItems(positions)
    }

    private fun restoreSelection() {
        val selected = getSelectedItems()
        if (selected.isEmpty()) {
            return
        }

        removeFromList(selected)
        ensureBackgroundThread {
            activity.restoreSpamMessages(selected)
            activity.runOnUiThread {
                activity.toast(R.string.messages_moved_out_of_spam)
                if (spamMessages.isEmpty()) {
                    listener?.refreshItems()
                }
            }
        }
    }

    private fun deleteSelection() {
        val selected = getSelectedItems()
        if (selected.isEmpty()) {
            return
        }

        removeFromList(selected)
        ensureBackgroundThread {
            activity.deleteSpamMessages(selected)
            activity.runOnUiThread {
                if (spamMessages.isEmpty()) {
                    listener?.refreshItems()
                }
            }
        }
    }
}
