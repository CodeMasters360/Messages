package org.fossify.messages.filter

import org.json.JSONArray
import org.json.JSONObject

/** JSON (de)serialisation of the rule list, used by Config. */
object FilterRuleStorage {

    fun toJson(rules: List<FilterRule>): String {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("name", r.name)
                    .put("enabled", r.enabled)
                    .put("action", r.action.name)
                    .put("senderText", r.senderText)
                    .put("senderMode", r.senderMode.name)
                    .put("bodyText", r.bodyText)
                    .put("bodyMode", r.bodyMode.name)
                    .put("matchCase", r.matchCase)
            )
        }
        return arr.toString()
    }

    fun fromJson(json: String): List<FilterRule> {
        val out = ArrayList<FilterRule>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    FilterRule(
                        id = o.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
                        name = o.optString("name", ""),
                        enabled = o.optBoolean("enabled", true),
                        action = enumOr(o.optString("action"), FilterAction.BLOCK),
                        senderText = o.optString("senderText", ""),
                        senderMode = enumOr(o.optString("senderMode"), SenderMode.WILDCARD),
                        bodyText = o.optString("bodyText", ""),
                        bodyMode = enumOr(o.optString("bodyMode"), BodyMode.ANY),
                        matchCase = o.optBoolean("matchCase", false)
                    )
                )
            }
        } catch (ignored: Exception) {
            // corrupt data: behave as "no rules"
        }
        return out
    }

    private inline fun <reified T : Enum<T>> enumOr(value: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
