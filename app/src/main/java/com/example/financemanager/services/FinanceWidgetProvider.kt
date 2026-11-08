package com.example.financemanager.services

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.financemanager.R
import com.example.financemanager.services.widgets.CycleSnapshot
import com.example.financemanager.services.widgets.FinanceAppWidget
import com.example.financemanager.services.widgets.loadCycleSnapshot
import com.example.financemanager.ui.components.moneyString

/** "Spending today": today's total, the cycle against its budget, and the tightest envelopes. */
class FinanceWidgetProvider : FinanceAppWidget() {

    companion object {
        /** Kept for callers that predate the other widgets; refreshes all of them. */
        fun refresh(context: Context) = refreshAll(context)

        /**
         * Height each envelope-row count needs, in dp, measured off widget_spending.xml: 90 for
         * the header and cycle line, then 36 per row, plus a little slack for font scaling. The
         * old breakpoint showed three rows from 190dp, which cut the third in half on a 4x2 Pixel.
         */
        private val rowBreakpoints = listOf(0 to 95f, 1 to 135f, 2 to 172f, 3 to 210f)
    }

    override suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
        // A failed read shows dashes rather than leaving the last values up: a stale number
        // presented as current is worse than none on a spending widget.
        val snapshot = runCatching { loadCycleSnapshot(context) }.getOrNull()
        val small = bind(context, R.layout.widget_small, snapshot, envelopeRows = -1)

        // Android 12+: hand the launcher every size and let it pick as the user resizes, with no
        // round trip through this process. Breakpoints are in dp.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = mutableMapOf(SizeF(110f, 40f) to small)
            rowBreakpoints.forEach { (rows, height) ->
                sizes[SizeF(200f, height)] = bind(context, R.layout.widget_spending, snapshot, rows)
            }
            return RemoteViews(sizes)
        }
        val options = manager.getAppWidgetOptions(widgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
        if (width < 200 || height < rowBreakpoints.first().second) return small
        val rows = rowBreakpoints.last { height >= it.second }.first
        return bind(context, R.layout.widget_spending, snapshot, rows)
    }

    /** [envelopeRows] of -1 means the 2x1 strip, which has no cycle line at all. */
    private fun bind(context: Context, layout: Int, snapshot: CycleSnapshot?, envelopeRows: Int): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        views.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.w_add, openAppIntent(context, quickEntry = true))

        // Amounts go through moneyString, so privacy mode masks them: the home screen is the
        // easiest surface to shoulder-surf. Whole rupees: paise only add width here.
        if (snapshot == null) {
            views.setTextViewText(R.id.w_today, "—")
            if (envelopeRows >= 0) views.setTextViewText(R.id.w_cycle, "Tap to open")
            return views
        }
        views.setTextViewText(R.id.w_today, moneyString(snapshot.spentToday, decimals = 0))
        if (envelopeRows < 0) return views

        views.setTextViewText(
            R.id.w_cycle,
            if (snapshot.cycleBudget > 0) {
                "${moneyString(snapshot.spentThisCycle, decimals = 0)} of ${moneyString(snapshot.cycleBudget, decimals = 0)} this cycle"
            } else {
                "${moneyString(snapshot.spentThisCycle, decimals = 0)} spent this cycle"
            }
        )

        val rows = listOf(
            intArrayOf(R.id.w_env1, R.id.w_env1_name, R.id.w_env1_left, R.id.w_env1_bar),
            intArrayOf(R.id.w_env2, R.id.w_env2_name, R.id.w_env2_left, R.id.w_env2_bar),
            intArrayOf(R.id.w_env3, R.id.w_env3_name, R.id.w_env3_left, R.id.w_env3_bar)
        )
        rows.forEachIndexed { index, (rowId, nameId, leftId, barId) ->
            val envelope = snapshot.envelopes.getOrNull(index)
            if (index >= envelopeRows || envelope == null) {
                views.setViewVisibility(rowId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(nameId, envelope.name)
            val over = envelope.remaining < 0
            views.setTextViewText(
                leftId,
                if (over) "${moneyString(-envelope.remaining, decimals = 0)} over"
                else "${moneyString(envelope.remaining, decimals = 0)} left"
            )
            views.setTextColor(
                leftId,
                ContextCompat.getColor(context, if (over) R.color.widget_warn else R.color.widget_muted)
            )
            views.setProgressBar(barId, 1000, (envelope.fraction.coerceIn(0.0, 1.0) * 1000).toInt(), false)
            // Three states rather than one warning colour from 90%: when every row was "warn",
            // none of them stood out. Gold is fine, amber is close, coral is over.
            // Tinting a widget's progress bar needs Android 12; before that it stays gold.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val tint = when {
                    envelope.fraction >= 1.0 -> R.color.widget_warn
                    envelope.fraction >= 0.85 -> R.color.widget_caution
                    else -> R.color.widget_accent
                }
                views.setColorStateList(barId, "setProgressTintList", tint)
            }
        }
        val showEmpty = snapshot.envelopes.isEmpty() && envelopeRows > 0
        views.setViewVisibility(R.id.w_empty, if (showEmpty) View.VISIBLE else View.GONE)
        return views
    }
}
