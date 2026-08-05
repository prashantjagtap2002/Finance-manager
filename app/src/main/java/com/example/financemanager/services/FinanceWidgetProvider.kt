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
import com.example.financemanager.data.*
import com.example.financemanager.ui.components.moneyString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class FinanceWidgetProvider : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.example.financemanager.UPDATE_WIDGET") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, FinanceWidgetProvider::class.java))
            onUpdate(context, appWidgetManager, ids)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        // Set pending intent to open MainActivity (Quick Entry flow)
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

        // Query database in background
        widgetScope.launch {
            try {
                val db = FinanceDatabase.getDatabase(context, this)
                val dao = db.financeDao()

                // Calculate today's start and end bounds
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                val endOfDay = calendar.timeInMillis

                val todayTransactions = dao.getTransactionsBetweenDatesFlow(startOfDay, endOfDay).first()
                val spentToday = todayTransactions
                    .filter { it.type == TransactionType.EXPENSE }
                    .sumOf { it.amount }

                // Fetch food category budget limits & spent
                val foodCategory = dao.getCategoryById(1L) // ID 1 = Food & Dining
                val allTransactions = dao.getTransactionsFlow().first()
                
                // Get current month bounds
                val monthCalendar = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                }
                val startOfMonth = monthCalendar.timeInMillis
                
                val foodSpentThisMonth = allTransactions
                    .filter { it.categoryId == 1L && it.type == TransactionType.EXPENSE && it.date >= startOfMonth }
                    .sumOf { it.amount }

                val foodLimit = foodCategory?.budgetLimit ?: 5000.0
                val foodRemaining = maxOf(0.0, foodLimit - foodSpentThisMonth)

                // Update text views
                views.setTextViewText(R.id.txt_spent_today, "Spent Today: ${moneyString(spentToday, false)}")
                views.setTextViewText(R.id.txt_budget_left, "Food Left: ${moneyString(foodRemaining, false)}")

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                // Keep default layout texts on error
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
