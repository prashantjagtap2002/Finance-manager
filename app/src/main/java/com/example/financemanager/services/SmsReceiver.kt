package com.example.financemanager.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.financemanager.core.SmsRuleStore
import com.example.financemanager.data.FinanceDatabase
import com.example.financemanager.data.SmsTransaction
import com.example.financemanager.domain.SmsParser
import com.example.financemanager.services.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {

    /**
     * The receiver is exported — it has to be, for the system to deliver SMS to it — so the first
     * thing it does is check that this really is an SMS broadcast.
     *
     * The action was checked before too, but only after the work had already been handed to a
     * coroutine and a `goAsync()` token taken out. Anything that could reach the receiver got a
     * background thread and a held wake token out of it before being turned away. Rejecting an
     * unexpected intent here costs nothing and starts nothing. `BROADCAST_SMS` in the manifest is
     * the real gate; this is the part that does not depend on the manifest being right.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processSms(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processSms(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty().trim()
        val messageBody = buildString {
            messages.forEach { append(it.displayMessageBody.orEmpty()) }
        }.trim()
        val receivedAtMillis = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        if (sender.isBlank() || messageBody.isBlank()) return

        // The receiver can start the process on its own, before any activity has loaded the
        // user's rules from Settings → SMS Rules.
        SmsRuleStore.ensureLoaded(context)
        val parsed = SmsParser.parse(sender, messageBody) ?: return

        val smsHash = generateSmsHash(sender, messageBody, receivedAtMillis)

        val db = FinanceDatabase.getDatabase(context.applicationContext)
        val dao = db.dao()

        val insertedId = dao.insertSmsTransaction(
            SmsTransaction(
                smsHash = smsHash,
                sender = parsed.rawSender,
                body = parsed.rawMessage,
                accountName = parsed.accountName,
                type = parsed.type,
                amount = parsed.amount,
                balance = parsed.balance,
                counterparty = parsed.counterparty,
                reference = parsed.reference,
                rawTimestamp = receivedAtMillis
            )
        )

        if (insertedId > 0) {
            SyncWorker.enqueueNow(context.applicationContext)
        }
    }

    private fun generateSmsHash(sender: String, body: String, timestamp: Long): String {
        val input = "$sender|$body|$timestamp"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        // Locale.ROOT: "%02x" with the default locale renders its digits in whatever numbering
        // system the device is set to, so the same message would hash differently after a locale
        // change and the dedupe index would stop recognising it.
        return hashBytes.joinToString("") { String.format(Locale.ROOT, "%02x", it) }
    }
}
