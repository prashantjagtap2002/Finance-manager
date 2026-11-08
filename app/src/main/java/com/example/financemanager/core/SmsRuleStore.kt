package com.example.financemanager.core

import android.content.Context
import com.example.financemanager.domain.BuiltInSmsParser
import com.example.financemanager.domain.SmsParser
import com.example.financemanager.domain.SmsParserConfig
import com.example.financemanager.domain.SmsRule
import com.example.financemanager.domain.SmsRuleAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the user's SMS rules (Settings → SMS Rules) and hands them to [SmsParser].
 *
 * Its own preferences file rather than `finance_prefs`: the rules are written as one JSON blob
 * and have nothing to do with the rest. They also go into the JSON backup, so a reinstall
 * doesn't take them with it.
 *
 * [ensureLoaded] is cheap after the first call and every entry point calls it — MainActivity, and
 * [com.example.financemanager.services.SmsReceiver], which can start the process on its own with
 * no activity ever created.
 */
object SmsRuleStore {

    private const val PREFS = "sms_rules"
    private const val KEY_RULES = "rules"
    private const val KEY_DISABLED = "disabled_builtins"

    private val _config = MutableStateFlow(SmsParserConfig())
    val config: StateFlow<SmsParserConfig> = _config.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val prefs = prefs(context)
            val rules = runCatching {
                rulesFromJson(JSONArray(prefs.getString(KEY_RULES, "[]") ?: "[]"))
            }.getOrDefault(emptyList())
            val disabled = prefs.getStringSet(KEY_DISABLED, emptySet()).orEmpty()
                .mapNotNull { name -> BuiltInSmsParser.entries.firstOrNull { it.name == name } }
                .toSet()
            publish(SmsParserConfig(rules, disabled))
            loaded = true
        }
    }

    fun saveRule(context: Context, rule: SmsRule) {
        val rules = _config.value.customRules
        val updated = if (rules.any { it.id == rule.id }) {
            rules.map { if (it.id == rule.id) rule else it }
        } else {
            rules + rule
        }
        setRules(context, updated)
    }

    fun deleteRule(context: Context, id: String) =
        setRules(context, _config.value.customRules.filterNot { it.id == id })

    /** Rules run top to bottom and the first match wins, so order is the priority. */
    fun moveRule(context: Context, id: String, delta: Int) {
        val rules = _config.value.customRules.toMutableList()
        val from = rules.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in rules.indices) return
        rules.add(to, rules.removeAt(from))
        setRules(context, rules)
    }

    fun setRules(context: Context, rules: List<SmsRule>) {
        prefs(context).edit().putString(KEY_RULES, rulesToJson(rules).toString()).apply()
        publish(_config.value.copy(customRules = rules))
    }

    fun setBuiltInEnabled(context: Context, parser: BuiltInSmsParser, enabled: Boolean) {
        val disabled = _config.value.disabledBuiltIns.let { if (enabled) it - parser else it + parser }
        prefs(context).edit().putStringSet(KEY_DISABLED, disabled.map { it.name }.toSet()).apply()
        publish(_config.value.copy(disabledBuiltIns = disabled))
    }

    /** Everything, for the JSON backup. */
    fun exportJson(): JSONObject = JSONObject().apply {
        put("rules", rulesToJson(_config.value.customRules))
        put("disabledBuiltIns", JSONArray(_config.value.disabledBuiltIns.map { it.name }))
    }

    /** Restores what [exportJson] wrote. A backup without the key leaves the rules alone. */
    fun importJson(context: Context, json: JSONObject) {
        json.optJSONArray("rules")?.let { setRules(context, rulesFromJson(it)) }
        json.optJSONArray("disabledBuiltIns")?.let { arr ->
            val names = (0 until arr.length()).map { arr.optString(it) }.toSet()
            BuiltInSmsParser.entries.forEach { setBuiltInEnabled(context, it, it.name !in names) }
        }
    }

    private fun publish(config: SmsParserConfig) {
        _config.value = config
        SmsParser.config = config
    }

    private fun rulesToJson(rules: List<SmsRule>) = JSONArray().apply {
        rules.forEach { rule ->
            put(JSONObject().apply {
                put("id", rule.id)
                put("name", rule.name)
                put("senderContains", rule.senderContains)
                put("pattern", rule.pattern)
                put("isRegex", rule.isRegex)
                put("action", rule.action.name)
                put("accountName", rule.accountName)
                put("enabled", rule.enabled)
            })
        }
    }

    private fun rulesFromJson(arr: JSONArray): List<SmsRule> = (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        SmsRule(
            id = obj.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
            name = obj.optString("name"),
            senderContains = obj.optString("senderContains"),
            pattern = obj.optString("pattern"),
            isRegex = obj.optBoolean("isRegex", false),
            action = SmsRuleAction.entries.firstOrNull { it.name == obj.optString("action") }
                ?: SmsRuleAction.DEBIT,
            accountName = obj.optString("accountName"),
            enabled = obj.optBoolean("enabled", true)
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
