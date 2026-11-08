package com.example.financemanager.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A chart built in Create Insight and pinned to the Insights page. Names match its enums. */
data class SavedInsight(
    val id: String,
    val visual: String,
    val metric: String,
    val period: String,
    val categoryId: Long? = null
) {
    /** The key this insight takes in [InsightLayout.order]. */
    val sectionKey: String get() = CUSTOM_PREFIX + id

    companion object {
        const val CUSTOM_PREFIX = "custom:"
    }
}

/**
 * How the Insights page is arranged: the order of its sections, which built-in ones are hidden,
 * and the custom charts pinned to it. [order] holds built-in keys (see `InsightSection`) and
 * `custom:<id>` keys side by side, so a custom chart can sit anywhere among the built-in ones.
 */
data class InsightLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val saved: List<SavedInsight> = emptyList()
)

/** The Insights page's built-in sections, in their default order. */
enum class InsightSection(val key: String, val title: String) {
    DAILY_BARS("daily_bars", "Daily Spending Trend"),
    DAILY_LINE("daily_line", "Daily Amount"),
    CATEGORY_DONUT("category_donut", "Expense Category Breakdown"),
    INCOME_VS_EXPENSE("income_vs_expense", "Income vs Expense"),
    CUMULATIVE("cumulative", "Cumulative Cash Flow"),
    KEY_INSIGHTS("key_insights", "Key Insights"),
    ACHIEVEMENTS("achievements", "Achievements"),
    FORECAST("forecast", "Cash Flow Forecast")
}

/**
 * The order to draw the Insights page in: the user's saved order, minus anything that no longer
 * exists, plus anything new appended in its default place at the end — so a section added in a
 * later version, or a chart pinned before any reordering, still shows up.
 */
fun resolvedInsightOrder(layout: InsightLayout): List<String> {
    val valid = InsightSection.entries.map { it.key } + layout.saved.map { it.sectionKey }
    val ordered = layout.order.filter { it in valid }.distinct()
    return ordered + valid.filterNot { it in ordered }
}

object InsightLayoutStore {

    private const val PREFS = "insight_layout"
    private const val KEY_LAYOUT = "layout"

    private val _layout = MutableStateFlow(InsightLayout())
    val layout: StateFlow<InsightLayout> = _layout.asStateFlow()

    @Volatile
    private var loaded = false

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _layout.value = runCatching {
                fromJson(JSONObject(prefs(context).getString(KEY_LAYOUT, "{}") ?: "{}"))
            }.getOrDefault(InsightLayout())
            loaded = true
        }
    }

    /**
     * Writes a new section order. Callers pass the full order as the screen currently shows it,
     * so a move made before the user ever customised anything starts from what they are looking at.
     */
    fun setOrder(context: Context, order: List<String>) = update(context) { it.copy(order = order) }

    fun setHidden(context: Context, key: String, hidden: Boolean) = update(context) {
        it.copy(hidden = if (hidden) it.hidden + key else it.hidden - key)
    }

    /** Pins [insight] to the top of the page, where the user will see that it landed. */
    fun addSaved(context: Context, insight: SavedInsight, currentOrder: List<String>) = update(context) {
        it.copy(
            saved = it.saved + insight,
            order = listOf(insight.sectionKey) + currentOrder.filterNot { key -> key == insight.sectionKey }
        )
    }

    fun removeSaved(context: Context, id: String) = update(context) {
        it.copy(
            saved = it.saved.filterNot { s -> s.id == id },
            order = it.order - (SavedInsight.CUSTOM_PREFIX + id)
        )
    }

    fun reset(context: Context) = update(context) { it.copy(order = emptyList(), hidden = emptySet()) }

    /** Everything, for the JSON backup. */
    fun exportJson(): JSONObject = toJson(_layout.value)

    fun importJson(context: Context, json: JSONObject) = update(context) { fromJson(json) }

    private fun update(context: Context, change: (InsightLayout) -> InsightLayout) {
        ensureLoaded(context)
        val next = change(_layout.value)
        _layout.value = next
        prefs(context).edit().putString(KEY_LAYOUT, toJson(next).toString()).apply()
    }

    private fun toJson(layout: InsightLayout) = JSONObject().apply {
        put("order", JSONArray(layout.order))
        put("hidden", JSONArray(layout.hidden.toList()))
        put("saved", JSONArray().apply {
            layout.saved.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("visual", s.visual)
                    put("metric", s.metric)
                    put("period", s.period)
                    s.categoryId?.let { put("categoryId", it) }
                })
            }
        })
    }

    private fun fromJson(json: JSONObject): InsightLayout {
        fun strings(key: String) = json.optJSONArray(key)?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotEmpty() }
        }.orEmpty()
        val saved = json.optJSONArray("saved")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SavedInsight(
                    id = o.optString("id").ifEmpty { return@mapNotNull null },
                    visual = o.optString("visual"),
                    metric = o.optString("metric"),
                    period = o.optString("period"),
                    categoryId = if (o.has("categoryId")) o.optLong("categoryId") else null
                )
            }
        }.orEmpty()
        return InsightLayout(order = strings("order"), hidden = strings("hidden").toSet(), saved = saved)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
