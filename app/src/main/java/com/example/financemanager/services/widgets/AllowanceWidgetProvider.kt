package com.example.financemanager.services.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.financemanager.R
import com.example.financemanager.ui.components.moneyString
import kotlin.math.abs

/**
 * "Safe to spend": the rest of this pay cycle's envelope budget spread evenly over the days left.
 * One number for the question people actually ask in a shop — can I afford this today — plus
 * whether spending so far is running ahead of an even pace.
 */
class AllowanceWidgetProvider : FinanceAppWidget() {

    override suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_allowance)
        views.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
        fun color(res: Int) = ContextCompat.getColor(context, res)

        val snapshot = runCatching { loadCycleSnapshot(context) }.getOrNull()
        if (snapshot == null) {
            views.setTextViewText(R.id.w_allow_amount, "—")
            views.setTextViewText(R.id.w_allow_days, "Tap to open")
            views.setViewVisibility(R.id.w_allow_status, View.GONE)
            return views
        }
        val allowance = allowanceOf(snapshot)
        val days = if (allowance.daysLeft == 1) "today" else "for the next ${allowance.daysLeft} days"

        if (allowance.state == PaceState.NO_BUDGET) {
            views.setTextViewText(R.id.w_allow_amount, "No budget")
            views.setTextColor(R.id.w_allow_amount, color(R.color.widget_on_surface))
            views.setViewVisibility(R.id.w_allow_unit, View.GONE)
            views.setTextViewText(R.id.w_allow_days, "Set envelope budgets to see this")
            views.setProgressBar(R.id.w_allow_bar, 1000, 0, false)
            views.setViewVisibility(R.id.w_allow_status, View.GONE)
            return views
        }

        views.setViewVisibility(R.id.w_allow_unit, View.VISIBLE)
        views.setViewVisibility(R.id.w_allow_status, View.VISIBLE)
        views.setTextViewText(R.id.w_allow_amount, moneyString(allowance.perDay, decimals = 0))
        views.setTextViewText(
            R.id.w_allow_days,
            if (allowance.state == PaceState.OVER_BUDGET) {
                "${moneyString(-allowance.remaining, decimals = 0)} over this cycle"
            } else {
                "$days · ${moneyString(allowance.remaining, decimals = 0)} left"
            }
        )

        val gap = moneyString(abs(allowance.paceGap), decimals = 0)
        val (status, statusColor, amountColor, barTint) = when (allowance.state) {
            PaceState.OVER_BUDGET -> Quad("Over budget", R.color.widget_warn, R.color.widget_warn, R.color.widget_warn)
            PaceState.OVER_PACE -> Quad("$gap ahead of pace", R.color.widget_caution, R.color.widget_accent, R.color.widget_caution)
            PaceState.UNDER_PACE -> Quad("$gap under pace", R.color.widget_ok, R.color.widget_accent, R.color.widget_accent)
            else -> Quad("On track", R.color.widget_ok, R.color.widget_accent, R.color.widget_accent)
        }
        views.setTextViewText(R.id.w_allow_status, status)
        views.setTextColor(R.id.w_allow_status, color(statusColor))
        views.setTextColor(R.id.w_allow_amount, color(amountColor))

        val used = if (snapshot.cycleBudget > 0) snapshot.spentThisCycle / snapshot.cycleBudget else 0.0
        views.setProgressBar(R.id.w_allow_bar, 1000, (used.coerceIn(0.0, 1.0) * 1000).toInt(), false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.w_allow_bar, "setProgressTintList", barTint)
        }
        return views
    }

    private data class Quad(val text: String, val statusColor: Int, val amountColor: Int, val barTint: Int)
}
