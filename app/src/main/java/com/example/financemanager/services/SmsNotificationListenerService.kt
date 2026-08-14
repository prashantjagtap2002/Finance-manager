package com.example.financemanager.services

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.financemanager.domain.AccountLedger
import com.example.financemanager.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    data class BankTemplate(
        val bankName: String,
        val regex: Regex,
        val amountGroup: Int,
        val merchantGroup: Int
    )

    private val bankTemplates = listOf(
        // HDFC / General: Spent Rs. 400 at XYZ Store
        BankTemplate("HDFC", Regex("""(?i)(?:spent|debited|paid|sent)\s*(?:rs\.?|inr|₹)\s*(\d+(?:\.\d{1,2})?)\s*(?:at|to|on)\s*(.+?)(?:\b|using|\s|$)"""), 1, 2),
        // SBI / General: Rs. 250 Spent at XYZ
        BankTemplate("SBI", Regex("""(?i)(?:rs\.?|inr|₹)\s*(\d+(?:\.\d{1,2})?)\s*(?:spent|debited|paid|sent)\s*(?:at|to|on)\s*(.+?)(?:\b|using|\s|$)"""), 1, 2),
        // ICICI / General: Paid XYZ Rs. 150
        BankTemplate("ICICI", Regex("""(?i)paid\s*(.+?)\s*(?:rs\.?|inr|₹)\s*(\d+(?:\.\d{1,2})?)"""), 2, 1),
        // Paytm / GPay specific
        BankTemplate("Paytm/GPay", Regex("""(?i)(?:paid\sto)\s*(.+?)\s*(?:rs\.?|inr|₹)\s*(\d+(?:\.\d{1,2})?)"""), 2, 1)
    )

    companion object {
        // Only notifications from these apps are ever parsed as potential transactions.
        // Without this allowlist, ANY notification on the device (WhatsApp, games, email,
        // etc.) whose text happened to match a bank regex — e.g. "spent Rs. 400 at cafe"
        // forwarded in a chat message — would silently create a real transaction and mutate
        // a real account balance. Package names cover major Indian bank/UPI/wallet apps.
        val TRUSTED_PACKAGES = setOf(
            "com.snapwork.hdfc", "com.hdfcbank.payzapp", "net.one97.paytm",
            "com.google.android.apps.nbu.paisa.user", // Google Pay
            "com.phonepe.app",
            "com.sbi.SBIFreedomPlus", "com.sbi.SBIFreedom", "com.sbi.yono", "com.sbi.retail",
            "com.msf.kbank.mobile", // Kotak
            "com.csam.icici.bank.imobile", "com.icicibank.pockets",
            "in.amazon.mShop.android.shopping", // Amazon (Amazon Pay notifications)
            "com.amazon.mShop.android.shopping"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName
        if (packageName !in TRUSTED_PACKAGES) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val content = "$title $text"

        parseNotificationContent(content, packageName)
    }

    fun parseNotificationContent(content: String, sourceApp: String) {
        for (template in bankTemplates) {
            val match = template.regex.find(content)
            if (match != null && match.groupValues.size > maxOf(template.amountGroup, template.merchantGroup)) {
                val amountStr = match.groupValues[template.amountGroup]
                val merchant = match.groupValues[template.merchantGroup]

                val amount = amountStr.toDoubleOrNull() ?: continue
                val cleanMerchant = merchant.trim().trimEnd('.').take(50)

                // Log the transaction to DB in background
                serviceScope.launch {
                    val db = FinanceDatabase.getDatabase(applicationContext, this)
                    val dao = db.financeDao()

                    // Try to match category based on merchant name
                    var categoryId: Long = 1 // Default to Food & Dining (id 1)
                    
                    val lowercaseMerchant = cleanMerchant.lowercase()
                    if (lowercaseMerchant.contains(Regex("cafe|restaurant|food|swiggy|zomato|dining|chai|bakery|canteen"))) {
                        categoryId = 1 // Food
                    } else if (lowercaseMerchant.contains(Regex("amazon|flipkart|myntra|zara|shop|mart|retail|mall|supermarket"))) {
                        categoryId = 2 // Shopping
                    } else if (lowercaseMerchant.contains(Regex("bill|electricity|airtel|jio|recharge|power|gas|bescom|water"))) {
                        categoryId = 3 // Utilities
                    } else if (lowercaseMerchant.contains(Regex("netflix|spotify|hotstar|multiplex|pvr|movie|ticket|bookmyshow"))) {
                        categoryId = 5 // Entertainment
                    }

                    // For digital wallets, typically subtract from Digital Wallet account, otherwise Bank account.
                    val targetAccountId = if (sourceApp.contains("paytm") || sourceApp.contains("wallet")) 4L else 1L

                    if (dao.getAccountById(targetAccountId) != null) {
                        AccountLedger.post(
                            dao,
                            Transaction(
                                amount = amount,
                                type = TransactionType.EXPENSE,
                                categoryId = categoryId,
                                sourceAccountId = targetAccountId,
                                note = "Auto-logged from notification: $cleanMerchant",
                                date = System.currentTimeMillis(),
                                isAutoLogged = true,
                                merchantName = cleanMerchant,
                                isVerified = false
                            )
                        )
                    }
                }
                break // Matched, don't check other patterns
            }
        }
    }
}
