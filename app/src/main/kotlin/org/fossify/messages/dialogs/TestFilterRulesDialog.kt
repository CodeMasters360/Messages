package org.fossify.messages.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.value
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogTestFilterRulesBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.filter.FilterEngine

/** Lets the user try a sender + text against the current rules without waiting for a real message. */
class TestFilterRulesDialog(val activity: BaseSimpleActivity) {

    init {
        val binding = DialogTestFilterRulesBinding.inflate(activity.layoutInflater)

        fun runTest() {
            val sender = binding.testFilterSenderEdittext.value
            val body = binding.testFilterBodyEdittext.value
            if (sender.isEmpty() && body.isEmpty()) {
                binding.testFilterResult.text = ""
                return
            }

            val result = FilterEngine.evaluate(activity.config.filterRules, sender, body)
            val name = result.rule?.name.orEmpty()
            binding.testFilterResult.text = when (result.decision) {
                FilterEngine.Decision.BLOCKED -> activity.getString(R.string.filter_test_blocked, name)
                FilterEngine.Decision.ALLOWED -> activity.getString(R.string.filter_test_allowed, name)
                FilterEngine.Decision.NO_MATCH -> activity.getString(R.string.filter_test_no_match)
            }
        }

        binding.testFilterSenderEdittext.onTextChangeListener { runTest() }
        binding.testFilterBodyEdittext.onTextChangeListener { runTest() }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.test_filter_rules)
            }
    }
}
