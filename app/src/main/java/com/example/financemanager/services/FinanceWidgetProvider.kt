package com.example.financemanager.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.financemanager.MainActivity
import com.example.financemanager.R
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.data.*
import com.example.financemanager.ui.components.moneyString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/** The two numbers the widget shows, read in one pass over the database. */
private data class WidgetSnapshot(
    val spentToday: Double,
    val tightestEnvelopeName: String?,
    val tightestEnvelopeRemaining: Double
)

class FinanceWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Broadcast this (explicitly) to refresh the widget outside the system's own schedule. */
        const val ACTION_UPDATE_WIDGET = "com.example.financemanager.UPDATE_WIDGET"

        // Tied to the process rather than to a receiver instance: Android creates a new provider
        // object per broadcast and discards it as soon as onReceive returns.
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // A widget update can start the process on its own, without MainActivity ever running,
        // so the currency symbol and privacy flag have to be loaded here too.
        FinancePreferences.init(context)

        // goAsync keeps the process alive until the database read finishes. Without it the work
        // was racing a teardown that begins the moment onReceive returns.
        val pendingResult = goAsync()
        widgetScope.launch {
            val snapshot = runCatching { loadSnapshot(context) }.getOrNull()
            try {
                appWidgetIds.forEach { render(context, appWidgetManager, it, snapshot) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, FinanceWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, ids)
        }
    }

    /**
     * Today's spending and the envelope with the least left in it.
     *
     * The envelope used to be hardcoded as category id 1, labelled "Food" — an id that is only
     * "Food & Dining" on a database that still has its seed data, and that points at whatever
     * inherited the id after a category was deleted or an account merged. The tightest envelope is
     * both correct by construction and the more useful number to see on a home screen.
     */
    private suspend fun loadSnapshot(context: Context): WidgetSnapshot {
        val dao = FinanceDatabase.getDatabase(context).financeDao()

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = startOfDay.timeInMillis
        startOfDay.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = startOfDay.timeInMillis

        val monthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val spentToday = dao.getTransactionsBetweenDates(dayStart, dayEnd)
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val monthExpenses = dao.getTransactionsBetweenDates(monthStart, System.currentTimeMillis())
            .filter { it.type == TransactionType.EXPENSE }
        val spentByCategory = monthExpenses.groupBy { it.categoryId }.mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val tightest = dao.getCategoriesList()
            .filter { it.budgetLimit > 0 }
            .minByOrNull { it.budgetLimit - (spentByCategory[it.id] ?: 0.0) }

        return WidgetSnapshot(
            spentToday = spentToday,
            tightestEnvelopeName = tightest?.name,
            tightestEnvelopeRemaining = tightest
                ?.let { maxOf(0.0, it.budgetLimit - (spentByCategory[it.id] ?: 0.0)) }
                ?: 0.0
        )
    }

    private fun render(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: WidgetSnapshot?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_quick_entry", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_quick_add, pendingIntent)

        // Masked under privacy mode: the home screen is the easiest surface to shoulder-surf.
        if (snapshot != null) {
            views.setTextViewText(R.id.txt_spent_today, "Spent Today: ${moneyString(snapshot.spentToday)}")
            views.setTextViewText(
                R.id.txt_budget_left,
                snapshot.tightestEnvelopeName
                    ?.let { "$it left: ${moneyString(snapshot.tightestEnvelopeRemaining)}" }
                    ?: "No envelope budgets set"
            )
        } else {
            // The database read failed. Leaving the last values on screen would present a stale
            // number as a current one, which on a spending widget is worse than showing nothing.
            views.setTextViewText(R.id.txt_spent_today, "Spent Today: --")
            views.setTextViewText(R.id.txt_budget_left, "Tap to open Finance Manager")
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
