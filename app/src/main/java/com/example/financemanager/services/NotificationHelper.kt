package com.example.financemanager.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.financemanager.MainActivity
import com.example.financemanager.R
import com.example.financemanager.ui.components.moneyString

/**
 * Central place for all local notifications: envelope budget alerts,
 * upcoming bill reminders and goal milestones.
 */
object NotificationHelper {

    const val CHANNEL_BUDGET = "budget_alerts"
    const val CHANNEL_BILLS = "bill_reminders"
    const val CHANNEL_GOALS = "goal_milestones"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BUDGET, "Budget Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Warnings when an envelope budget is close to or over its limit"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BILLS, "Bill Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders for upcoming subscriptions and scheduled bills"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_GOALS, "Goal Milestones", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress milestones for your savings goals"
            }
        )
    }

    private fun canNotify(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun notify(context: Context, channel: String, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between check and notify — ignore.
        }
    }

    /** 80% warning or 100% overspend alert for an envelope. */
    fun notifyBudgetThreshold(context: Context, categoryName: String, spent: Double, limit: Double, isOver: Boolean) {
        val id = 1000 + categoryName.hashCode().mod(1000)
        if (isOver) {
            notify(
                context, CHANNEL_BUDGET, id,
                "Budget exceeded: $categoryName",
                "You've spent ${moneyString(spent, false)} of your ${moneyString(limit, false)} envelope. Time to slow down!"
            )
        } else {
            notify(
                context, CHANNEL_BUDGET, id,
                "80% of $categoryName budget used",
                "${moneyString(limit - spent, false)} left in this envelope for the month."
            )
        }
    }

    /** Summary reminder for bills due within the next 48 hours. */
    fun notifyUpcomingBills(context: Context, billCount: Int, totalAmount: Double, firstBillNote: String) {
        val text = if (billCount == 1) {
            "\"$firstBillNote\" (${moneyString(totalAmount, false)}) is due within 2 days."
        } else {
            "$billCount bills totalling ${moneyString(totalAmount, false)} are due within 2 days, including \"$firstBillNote\"."
        }
        notify(context, CHANNEL_BILLS, 2000, "Upcoming bills", text)
    }

    /** Simple Reminder. */
    fun notifyReminder(context: Context, id: Long, title: String, message: String) {
        notify(context, CHANNEL_BILLS, 2500 + id.hashCode().mod(500), title, message)
    }

    /** Celebration when a savings goal crosses a milestone (50%, 100%). */
    fun notifyGoalMilestone(context: Context, goalName: String, percent: Int) {
        val (title, text) = if (percent >= 100) {
            "Goal achieved! 🎉" to "You've fully funded \"$goalName\". Congratulations!"
        } else {
            "Halfway there!" to "\"$goalName\" is now $percent% funded. Keep going!"
        }
        notify(context, CHANNEL_GOALS, 3000 + goalName.hashCode().mod(1000), title, text)
    }
}
