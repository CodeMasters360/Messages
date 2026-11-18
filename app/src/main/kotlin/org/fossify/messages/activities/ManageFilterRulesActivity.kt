package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.messages.adapters.FilterRulesAdapter
import org.fossify.messages.databinding.ActivityManageFilterRulesBinding
import org.fossify.messages.dialogs.TestFilterRulesDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.filter.FilterRule

class ManageFilterRulesActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityManageFilterRulesBinding::inflate)
    private var adapter: FilterRulesAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.manageFilterRulesList))
        setupMaterialScrollListener(
            scrollingView = binding.manageFilterRulesList,
            topAppBar = binding.filterRulesAppbar
        )
        updateTextColors(binding.manageFilterRulesWrapper)

        binding.manageFilterRulesPlaceholder2.apply {
            underlineText()
            setTextColor(getProperPrimaryColor())
            setOnClickListener { openEditor(null) }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.filterRulesAppbar, NavigationIcon.Arrow)
        refreshRules()
    }

    private fun setupOptionsMenu() {
        binding.filterRulesToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_filter_rule -> {
                    openEditor(null)
                    true
                }

                R.id.test_filter_rules -> {
                    TestFilterRulesDialog(this)
                    true
                }

                else -> false
            }
        }
    }

    private fun refreshRules() {
        val rules = config.filterRules
        val current = adapter
        if (current == null) {
            val newAdapter = FilterRulesAdapter(
                activity = this,
                rules = rules,
                onToggle = { rule, enabled ->
                    val updated = rule.copy(enabled = enabled)
                    config.upsertFilterRule(updated)
                    adapter?.updateRule(updated)
                },
                onClick = { rule -> openEditor(rule) }
            )
            adapter = newAdapter
            binding.manageFilterRulesList.adapter = newAdapter
        } else {
            current.submit(rules)
        }

        binding.manageFilterRulesPlaceholder.beVisibleIf(rules.isEmpty())
        binding.manageFilterRulesPlaceholder2.beVisibleIf(rules.isEmpty())
    }

    private fun openEditor(rule: FilterRule?) {
        Intent(this, FilterRuleEditActivity::class.java).apply {
            if (rule != null) putExtra(FilterRuleEditActivity.EXTRA_RULE_ID, rule.id)
            startActivity(this)
        }
    }
}
