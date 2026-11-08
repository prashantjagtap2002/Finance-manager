package com.example.financemanager.services.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.example.financemanager.R
import com.example.financemanager.ui.components.moneyString

/**
 * "Coming up": the next three things due — bills, recurring income and IOUs, overdue ones first —
 * and the running balance of what friends owe against what is owed to them.
 */
class UpcomingWidgetProvider : FinanceAppWidget() {

    override suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_upcoming)
        views.setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
        fun color(res: Int) = ContextCompat.getColor(context, res)

        val rows = listOf(
            intArrayOf(R.id.w_due1, R.id.w_due1_name, R.id.w_due1_when, R.id.w_due1_amount),
            intArrayOf(R.id.w_due2, R.id.w_due2_name, R.id.w_due2_when, R.id.w_due2_amount),
            intArrayOf(R.id.w_due3, R.id.w_due3_name, R.id.w_due3_when, R.id.w_due3_amount)
        )

        val snapshot = runCatching { loadUpcomingSnapshot(context) }.getOrNull()
        if (snapshot == null) {
            rows.forEach { views.setViewVisibility(it[0], View.GONE) }
            views.setViewVisibility(R.id.w_due_empty, View.VISIBLE)
            views.setTextViewText(R.id.w_due_empty, "Tap to open")
            views.setTextViewText(R.id.w_due_total, "")
            return views
        }

        views.setTextViewText(
            R.id.w_due_total,
            if (snapshot.outgoing > 0) "${moneyString(snapshot.outgoing, decimals = 0)} out in 30 days" else "Next 30 days"
        )

        rows.forEachIndexed { index, (rowId, nameId, whenId, amountId) ->
            val item = snapshot.items.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(rowId, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(nameId, item.title)

            val days = daysFromToday(item.date)
            val (whenText, whenColor) = when {
                days < 0 -> "${-days}d overdue" to R.color.widget_warn
                days == 0 -> "today" to R.color.widget_accent
                days == 1 -> "tomorrow" to R.color.widget_accent
                else -> "in $days days" to R.color.widget_muted
            }
            views.setTextViewText(whenId, whenText)
            views.setTextColor(whenId, color(whenColor))

            // Money coming in reads sage with a plus; money going out stays neutral, so the list
            // doesn't turn red just for listing ordinary bills.
            val incoming = item.kind == DueKind.INCOME || item.kind == DueKind.OWED_TO_YOU
            val amount = moneyString(item.amount, decimals = 0)
            views.setTextViewText(amountId, if (incoming) "+$amount" else amount)
            views.setTextColor(amountId, color(if (incoming) R.color.widget_ok else R.color.widget_on_surface))
        }
        views.setViewVisibility(R.id.w_due_empty, if (snapshot.items.isEmpty()) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.w_due_empty, "Nothing due in the next 30 days")

        // The IOU balance, both directions. Hidden halves rather than "₹0" noise.
        val owed = snapshot.owedToYou > 0.005
        val owe = snapshot.youOwe > 0.005
        if (!owed && !owe) {
            views.setTextViewText(R.id.w_owed_to_you, "No open IOUs")
            views.setTextColor(R.id.w_owed_to_you, color(R.color.widget_muted))
            views.setViewVisibility(R.id.w_you_owe, View.GONE)
        } else {
            views.setTextViewText(
                R.id.w_owed_to_you,
                if (owed) "Owed to you ${moneyString(snapshot.owedToYou, decimals = 0)}" else ""
            )
            views.setTextColor(R.id.w_owed_to_you, color(R.color.widget_ok))
            views.setViewVisibility(R.id.w_you_owe, if (owe) View.VISIBLE else View.GONE)
            views.setTextViewText(R.id.w_you_owe, "You owe ${moneyString(snapshot.youOwe, decimals = 0)}")
        }
        return views
    }
}
