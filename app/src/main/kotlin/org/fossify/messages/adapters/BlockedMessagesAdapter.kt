package org.fossify.messages.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.updateTextColors
import org.fossify.messages.R
import org.fossify.messages.databinding.ItemBlockedMessageBinding
import org.fossify.messages.helpers.BlockedMessagesDb

class BlockedMessagesAdapter(
    private val activity: BaseSimpleActivity,
    items: List<BlockedMessagesDb.Item>,
    private val onClick: (BlockedMessagesDb.Item) -> Unit
) : RecyclerView.Adapter<BlockedMessagesAdapter.ViewHolder>() {

    private val items = items.toMutableList()

    class ViewHolder(val binding: ItemBlockedMessageBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(newItems: List<BlockedMessagesDb.Item>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBlockedMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.apply {
            blockedMessageSender.text = item.address
            val whenText = DateUtils.formatDateTime(
                activity,
                item.date,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR
            )
            blockedMessageMeta.text = whenText + "  |  " + activity.getString(R.string.blocked_message_rule, item.rule)
            blockedMessageBody.text = item.body
            blockedMessageHolder.setOnClickListener { onClick(item) }
        }
        activity.updateTextColors(holder.binding.blockedMessageHolder)
    }
}
