package org.fossify.messages.activities

import android.os.Bundle
import android.widget.Toast
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityFilterRuleEditBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.filter.BodyMode
import org.fossify.messages.filter.FilterAction
import org.fossify.messages.filter.FilterEngine
import org.fossify.messages.filter.FilterRule
import org.fossify.messages.filter.SenderMode

class FilterRuleEditActivity : SimpleActivity() {

    companion object {
        const val EXTRA_RULE_ID = "filter_rule_id"
        const val EXTRA_PREFILL_SENDER = "filter_prefill_sender"
    }

    private val binding by viewBinding(ActivityFilterRuleEditBinding::inflate)

    private var original = FilterRule()
    private var isNew = true
    private var action = FilterAction.BLOCK
    private var senderMode = SenderMode.WILDCARD
    private var bodyMode = BodyMode.ANY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.filterEditScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.filterEditScrollview,
            topAppBar = binding.filterEditAppbar
        )

        val id = intent.getStringExtra(EXTRA_RULE_ID)
        val existing = if (id != null) config.filterRules.firstOrNull { it.id == id } else null
        isNew = existing == null
        original = existing ?: newRuleFromIntent()

        action = original.action
        senderMode = original.senderMode
        bodyMode = original.bodyMode

        binding.filterNameEdittext.setText(original.name)
        binding.filterSenderEdittext.setText(original.senderText)
        binding.filterBodyEdittext.setText(original.bodyText)
        binding.filterMatchCase.isChecked = original.matchCase
        binding.filterEnabled.isChecked = original.enabled

        binding.filterEditToolbar.setTitle(if (isNew) R.string.add_filter_rule else R.string.edit_filter_rule)
        binding.filterEditToolbar.menu.findItem(R.id.delete_filter_rule)?.isVisible = !isNew

        setupMenu()
        setupActionRow()
        setupSenderModeRow()
        setupBodyModeRow()
        setupSwitchRows()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.filterEditAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.filterEditHolder)
        listOf(binding.filterSenderSectionLabel, binding.filterBodySectionLabel).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun newRuleFromIntent(): FilterRule {
        val prefill = intent.getStringExtra(EXTRA_PREFILL_SENDER)
        return if (prefill != null) {
            // coming from "never block this sender"
            FilterRule(senderText = prefill, action = FilterAction.ALLOW)
        } else {
            FilterRule()
        }
    }

    private fun setupMenu() {
        binding.filterEditToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.save_filter_rule -> {
                    save()
                    true
                }

                R.id.delete_filter_rule -> {
                    confirmDelete()
                    true
                }

                else -> false
            }
        }
    }

    private fun setupActionRow() = binding.apply {
        filterActionValue.text = actionText(action)
        filterActionHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(R.string.filter_action_block)),
                RadioItem(1, getString(R.string.filter_action_allow))
            )
            RadioGroupDialog(this@FilterRuleEditActivity, items, action.ordinal) {
                action = FilterAction.values()[it as Int]
                filterActionValue.text = actionText(action)
            }
        }
    }

    private fun setupSenderModeRow() = binding.apply {
        filterSenderModeValue.text = senderModeText(senderMode)
        filterSenderModeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(R.string.filter_sender_mode_wildcard)),
                RadioItem(1, getString(R.string.filter_mode_regex))
            )
            RadioGroupDialog(this@FilterRuleEditActivity, items, senderMode.ordinal) {
                senderMode = SenderMode.values()[it as Int]
                filterSenderModeValue.text = senderModeText(senderMode)
            }
        }
    }

    private fun setupBodyModeRow() = binding.apply {
        filterBodyModeValue.text = bodyModeText(bodyMode)
        filterBodyModeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(0, getString(R.string.filter_body_any)),
                RadioItem(1, getString(R.string.filter_body_all)),
                RadioItem(2, getString(R.string.filter_mode_regex))
            )
            RadioGroupDialog(this@FilterRuleEditActivity, items, bodyMode.ordinal) {
                bodyMode = BodyMode.values()[it as Int]
                filterBodyModeValue.text = bodyModeText(bodyMode)
            }
        }
    }

    private fun setupSwitchRows() = binding.apply {
        filterMatchCaseHolder.setOnClickListener { filterMatchCase.toggle() }
        filterEnabledHolder.setOnClickListener { filterEnabled.toggle() }
    }

    private fun actionText(a: FilterAction) = getString(
        if (a == FilterAction.ALLOW) R.string.filter_action_allow else R.string.filter_action_block
    )

    private fun senderModeText(m: SenderMode) = getString(
        if (m == SenderMode.REGEX) R.string.filter_mode_regex else R.string.filter_sender_mode_wildcard
    )

    private fun bodyModeText(m: BodyMode) = getString(
        when (m) {
            BodyMode.ANY -> R.string.filter_body_any
            BodyMode.ALL -> R.string.filter_body_all
            BodyMode.REGEX -> R.string.filter_mode_regex
        }
    )

    private fun save() {
        val rule = original.copy(
            name = binding.filterNameEdittext.value,
            enabled = binding.filterEnabled.isChecked,
            action = action,
            senderText = binding.filterSenderEdittext.value,
            senderMode = senderMode,
            bodyText = binding.filterBodyEdittext.value,
            bodyMode = bodyMode,
            matchCase = binding.filterMatchCase.isChecked
        )

        val problem = FilterEngine.validate(rule)
        if (problem != null) {
            val message = when (problem.problem) {
                FilterEngine.Problem.EMPTY -> getString(R.string.filter_error_empty)
                FilterEngine.Problem.NO_SENDER_PATTERN -> getString(R.string.filter_error_no_sender)
                FilterEngine.Problem.NO_WORDS -> getString(R.string.filter_error_no_words)
                FilterEngine.Problem.SENDER_REGEX -> getString(R.string.filter_error_sender_regex, problem.detail)
                FilterEngine.Problem.BODY_REGEX -> getString(R.string.filter_error_body_regex, problem.detail)
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            return
        }

        val named = if (rule.name.isEmpty()) {
            val number = config.filterRules.size + if (isNew) 1 else 0
            rule.copy(name = getString(R.string.filter_default_name, number))
        } else {
            rule
        }

        config.upsertFilterRule(named)
        finish()
    }

    private fun confirmDelete() {
        ConfirmationDialog(this, getString(R.string.filter_delete_confirm)) {
            config.removeFilterRule(original.id)
            finish()
        }
    }
}
