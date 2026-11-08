package com.example.financemanager.services

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
 *
 * Amounts here go through [moneyString] with its default, so privacy mode masks them. These are
 * the one surface that shows numbers when the app isn't even open — on the lock screen, to anyone
 * holding the phone — which is exactly the shoulder-surfing privacy mode exists to stop. They used
 * to opt out of it. The envelope or bill name still comes through, so a masked alert is still
 * worth receiving.
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

    private fun notify(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        action: NotificationCompat.Action? = null
    ) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .apply { if (action != null) addAction(action) }
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
                "You've spent ${moneyString(spent)} of your ${moneyString(limit)} envelope. Time to slow down!"
            )
        } else {
            notify(
                context, CHANNEL_BUDGET, id,
                "80% of $categoryName budget used",
                "${moneyString(limit - spent)} left in this envelope for the month."
            )
        }
    }

    /** Summary reminder for bills due within the next 48 hours. */
    fun notifyUpcomingBills(context: Context, billCount: Int, totalAmount: Double, firstBillNote: String) {
        val text = if (billCount == 1) {
            "\"$firstBillNote\" (${moneyString(totalAmount)}) is due within 2 days."
        } else {
            "$billCount bills totalling ${moneyString(totalAmount)} are due within 2 days, including \"$firstBillNote\"."
        }
        notify(context, CHANNEL_BILLS, 2000, "Upcoming bills", text)
    }

    /** The id a "due today" reminder for a recurring template is posted under. */
    private fun reminderNotificationId(id: Long) = 2500 + id.hashCode().mod(500)

    /** The id a "due soon" notification for a recurring bill is posted under. */
    private fun billDueNotificationId(recurringId: Long) = 4000 + recurringId.hashCode().mod(1000)

    /**
     * Simple Reminder. With a [recurringId] it carries a "Mark paid" action for the occurrence that
     * fell due on [dueDate]; see [BillActionReceiver] for how that stays a single payment.
     */
    fun notifyReminder(
        context: Context,
        id: Long,
        title: String,
        message: String,
        recurringId: Long? = null,
        dueDate: Long = 0L
    ) {
        val notificationId = reminderNotificationId(id)
        val action = recurringId?.let { markPaidAction(context, it, dueDate, advance = false) }
        notify(context, CHANNEL_BILLS, notificationId, title, message, action)
    }

    /**
     * One bill coming up, with "Mark paid" right on the notification. Paying it there logs the
     * payment and moves the schedule on, so the bill never has to be opened in the app at all.
     * [dueDate] is the occurrence this notification is about — the receiver checks it against the
     * schedule at tap time, so a notification left over from an earlier day can't pay the wrong one.
     */
    fun notifyBillDue(context: Context, recurringId: Long, name: String, amount: Double, whenText: String, dueDate: Long) {
        val notificationId = billDueNotificationId(recurringId)
        notify(
            context, CHANNEL_BILLS, notificationId,
            "$name is due $whenText",
            "${moneyString(amount)} — tap Mark paid once it's done.",
            markPaidAction(context, recurringId, dueDate, advance = true)
        )
    }

    /**
     * Clears every notification about [recurringId]. A bill can be showing both a "due tomorrow"
     * and a "due today" notification at once; once either is paid, the other is a second Mark paid
     * button for money that has already gone.
     */
    fun cancelBillNotifications(context: Context, recurringId: Long) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(reminderNotificationId(recurringId))
        manager.cancel(billDueNotificationId(recurringId))
    }

    private fun markPaidAction(context: Context, recurringId: Long, dueDate: Long, advance: Boolean): NotificationCompat.Action {
        val intent = Intent(context, BillActionReceiver::class.java).apply {
            action = BillActionReceiver.ACTION_MARK_PAID
            // The data Uri makes each bill's intent distinct to PendingIntent matching, which
            // ignores extras. Without it two bills whose notification ids collide would share one
            // PendingIntent, and FLAG_UPDATE_CURRENT would point both buttons at the later bill.
            data = Uri.parse("financemanager://bill/$recurringId/${if (advance) "soon" else "today"}")
            putExtra(BillActionReceiver.EXTRA_RECURRING_ID, recurringId)
            putExtra(BillActionReceiver.EXTRA_DUE_DATE, dueDate)
            putExtra(BillActionReceiver.EXTRA_ADVANCE, advance)
        }
        val requestCode = if (advance) billDueNotificationId(recurringId) else reminderNotificationId(recurringId)
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, "Mark paid", pending).build()
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
