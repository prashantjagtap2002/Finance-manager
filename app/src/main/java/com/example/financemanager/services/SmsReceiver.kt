package com.example.financemanager.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.financemanager.data.FinanceDatabase
import com.example.financemanager.data.SmsTransaction
import com.example.financemanager.domain.SmsParser
import com.example.financemanager.services.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress.orEmpty().trim()
        val messageBody = buildString {
            messages.forEach { append(it.displayMessageBody.orEmpty()) }
        }.trim()
        val receivedAtMillis = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        if (sender.isBlank() || messageBody.isBlank()) return

        val parsed = SmsParser.parse(sender, messageBody) ?: return

        val smsHash = generateSmsHash(sender, messageBody, receivedAtMillis)

        val db = FinanceDatabase.getDatabase(context.applicationContext)
        val dao = db.financeDao()

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
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
