package com.example.financemanager.services.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.example.financemanager.MainActivity
import com.example.financemanager.R
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.services.FinanceWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * What every home-screen widget in the app shares: reading the database off the main thread while
 * the system keeps the process alive, redrawing on resize, and the app's own refresh broadcast.
 * A subclass only says how to draw one widget.
 */
abstract class FinanceAppWidget : AppWidgetProvider() {

    /** Builds the views for one placed widget. Runs on [Dispatchers.IO] and may read the database. */
    protected abstract suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int): RemoteViews

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // A widget update can start the process on its own, without MainActivity ever running,
        // so the currency symbol and privacy flag have to be loaded here too.
        FinancePreferences.init(context)

        // goAsync keeps the process alive until the database read finishes. Without it the work
        // races a teardown that begins the moment onReceive returns.
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                appWidgetIds.forEach { id ->
                    runCatching { render(context, appWidgetManager, id) }
                        .onSuccess { appWidgetManager.updateAppWidget(id, it) }
                        .onFailure {
                            Log.e("FinanceAppWidget", "Couldn't draw widget $id", it)
                            // Replace whatever is showing with a plain error rather than leave it:
                            // a stale draw is yesterday's numbers presented as today's.
                            runCatching { appWidgetManager.updateAppWidget(id, errorViews(context)) }
                        }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Redraws on resize: some widgets pick their content, or draw a chart, to the size they get. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_UPDATE_WIDGET) {
            val manager = AppWidgetManager.getInstance(context)
            onUpdate(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
        }
    }

    companion object {
        /** Broadcast this (explicitly) to refresh a widget outside the system's own schedule. */
        const val ACTION_UPDATE_WIDGET = "com.example.financemanager.UPDATE_WIDGET"

        // Tied to the process rather than to a receiver instance: Android creates a new provider
        // object per broadcast and discards it as soon as onReceive returns.
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val providers = listOf(
            FinanceWidgetProvider::class.java,
            AllowanceWidgetProvider::class.java,
            WeekWidgetProvider::class.java,
            UpcomingWidgetProvider::class.java
        )

        /**
         * Redraws every placed widget of every kind now. Called whenever the numbers behind them
         * change; the system's own schedule is every 30 minutes at best.
         */
        fun refreshAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            providers.forEach { provider ->
                if (manager.getAppWidgetIds(ComponentName(app, provider)).isNotEmpty()) {
                    app.sendBroadcast(Intent(app, provider).setAction(ACTION_UPDATE_WIDGET))
                }
            }
        }

        /** Shown when a draw fails: no figures at all, and a tap opens the app. */
        private fun errorViews(context: Context): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_loading).apply {
                setTextViewText(R.id.w_status, "Couldn't load · tap to open")
                setOnClickPendingIntent(android.R.id.background, openAppIntent(context))
            }

        /** Opens the app, or straight into Quick Entry. */
        fun openAppIntent(context: Context, quickEntry: Boolean = false): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                if (quickEntry) putExtra("open_quick_entry", true)
            }
            return PendingIntent.getActivity(
                context,
                if (quickEntry) 1 else 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
