package com.example.financemanager.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.financemanager.data.*
import com.example.financemanager.domain.NotificationTransactionParser
import com.example.financemanager.domain.ParsedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Locale

/**
 * Turns payment notifications from banking and wallet apps into pending alerts in the same
 * approval queue bank SMS lands in.
 *
 * It used to write straight to the ledger with [com.example.financemanager.domain.AccountLedger],
 * guessing an account ("id 1, or id 4 for wallets") and an envelope ("Food & Dining unless the
 * merchant name says otherwise"). Three things were wrong with that: the ids were assumed rather
 * than looked up, so they pointed at whatever happened to hold them after a delete or a merge;
 * a UPI payment that also sends a bank SMS got counted twice, once silently here and once when
 * the user approved the SMS; and Android reposts a notification whenever it is updated, so the
 * same payment could be logged again on every repost.
 *
 * Routing into the queue fixes all three: the user picks the account and envelope, nothing moves
 * a balance until they do, and the unique index on [SmsTransaction.smsHash] collapses reposts.
 * A payment that arrives both here and by SMS now shows up as two pending alerts rather than one
 * silent transaction and one pending alert — visible, and dismissable with a single tap.
 */
class SmsNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        /** Kept here for callers that already reference it; the list itself lives with the parser. */
        val TRUSTED_PACKAGES: Set<String> get() = NotificationTransactionParser.TRUSTED_PACKAGES
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (sbn.packageName !in NotificationTransactionParser.TRUSTED_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text".trim()

        val parsed = NotificationTransactionParser.parse(content) ?: return
        capture(sbn, parsed, content)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun capture(sbn: StatusBarNotification, parsed: ParsedNotification, content: String) {
        val packageName = sbn.packageName
        // The notification's own key is stable across the updates Android delivers as reposts, so
        // hashing it with the text means a repost of the same payment lands on the same hash and
        // the insert is ignored, while a genuinely new payment gets a new row.
        val hash = notificationHash(packageName, sbn.key, content)
        val appLabel = applicationLabel(packageName)
        val postedAt = sbn.postTime

        serviceScope.launch {
            val dao = FinanceDatabase.getDatabase(applicationContext).dao()
            val insertedId = dao.insertSmsTransaction(
                SmsTransaction(
                    smsHash = hash,
                    sender = packageName,
                    body = content,
                    accountName = appLabel,
                    // Every template this parser carries matches money going out.
                    type = "debit",
                    amount = parsed.amount,
                    counterparty = parsed.merchant,
                    rawTimestamp = postedAt
                )
            )
            if (insertedId > 0) SyncWorker.enqueueNow(applicationContext)
        }
    }

    /** The app's display name, which reads better in the queue than its package name. */
    private fun applicationLabel(packageName: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun notificationHash(packageName: String, key: String, content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Locale.ROOT: "%02x" with the default locale renders its digits in whatever numbering
        // system the device is set to, so the same message would hash differently after a locale
        // change and the dedupe index would stop recognising it.
        return digest.digest("$packageName|$key|$content".toByteArray())
            .joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }
}
