package org.fossify.messages.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.updateTextColors
import org.fossify.messages.databinding.ItemFilterRuleBinding
import org.fossify.messages.filter.FilterRule

class FilterRulesAdapter(
    private val activity: BaseSimpleActivity,
    rules: List<FilterRule>,
    private val onToggle: (FilterRule, Boolean) -> Unit,
    private val onClick: (FilterRule) -> Unit
) : RecyclerView.Adapter<FilterRulesAdapter.ViewHolder>() {

    private val rules = rules.toMutableList()

    class ViewHolder(val binding: ItemFilterRuleBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(newRules: List<FilterRule>) {
        rules.clear()
        rules.addAll(newRules)
        notifyDataSetChanged()
    }

    /** Keeps our copy in sync after a checkbox change, without re-binding the list. */
    fun updateRule(rule: FilterRule) {
        val index = rules.indexOfFirst { it.id == rule.id }
        if (index >= 0) rules[index] = rule
    }

    override fun getItemCount() = rules.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilterRuleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = rules[position]
        holder.binding.apply {
            filterRuleTitle.text = rule.name.ifEmpty { "-" }
            filterRuleSummary.text = rule.summary()

            filterRuleEnabled.setOnCheckedChangeListener(null)
            filterRuleEnabled.isChecked = rule.enabled
            filterRuleEnabled.setOnCheckedChangeListener { _, checked -> onToggle(rule, checked) }

            filterRuleHolder.setOnClickListener { onClick(rule) }
        }
        activity.updateTextColors(holder.binding.filterRuleHolder)
    }
}
