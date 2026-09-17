package com.example.financemanager.domain

/** The amount and merchant read out of a payment notification, as text, ready for the queue. */
data class ParsedNotification(
    val amount: String,
    val merchant: String
)

/**
 * Reads payment notifications from banking and wallet apps.
 *
 * Split out of [com.example.financemanager.services.SmsNotificationListenerService] so the
 * matching can be exercised without a device — it decides what becomes a pending transaction, and
 * it was the one part of that service nothing could test.
 */
object NotificationTransactionParser {

    private data class BankTemplate(
        val bankName: String,
        val regex: Regex,
        val amountGroup: Int,
        val merchantGroup: Int
    )

    /**
     * Only notifications from these apps are ever parsed as potential transactions. Without the
     * allowlist, ANY notification whose text happened to match a template — "spent Rs. 400 at
     * cafe" forwarded in a chat — would become a pending transaction.
     */
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

    private val bankTemplates = listOf(
        // HDFC / General: Spent Rs. 400 at XYZ Store
        BankTemplate("HDFC", Regex("""(?i)(?:spent|debited|paid|sent)\s*(?:rs\.?|inr|₹)\s*(\d+(?:\.\d{1,2})?)\s*(?:at|to|on)\s*(.+?)(?:\b|using|\s|$)"""), 1, 2),
        // SBI / General: Rs. 250 Spent at XYZ
        BankTemplate("SBI", Regex("""(?i)(?:rs\.?|inr|₹)\s*(\d+(?:\.\d{1,2})?)\s*(?:spent|debited|paid|sent)\s*(?:at|to|on)\s*(.+?)(?:\b|using|\s|$)"""), 1, 2),
        // Paytm / GPay: Paid to XYZ Rs. 150. Ahead of the looser "Paid XYZ" below, which would
        // otherwise match first and hand back "to XYZ" as the merchant — a name that reads wrong
        // in the queue and normalises to a different merchant rule than the same shop's other
        // alerts.
        BankTemplate("Paytm/GPay", Regex("""(?i)(?:paid\sto)\s*(.+?)\s*(?:rs\.?|inr|₹)\s*(\d+(?:\.\d{1,2})?)"""), 2, 1),
        // ICICI / General: Paid XYZ Rs. 150
        BankTemplate("ICICI", Regex("""(?i)paid\s*(.+?)\s*(?:rs\.?|inr|₹)\s*(\d+(?:\.\d{1,2})?)"""), 2, 1)
    )

    /** A connector left on the front of a captured merchant by a looser template. */
    private val leadingConnector = Regex("""^(?:to|at|on|from)\s+""", RegexOption.IGNORE_CASE)

    /** The payment [content] describes, or null when it isn't one. */
    fun parse(content: String): ParsedNotification? {
        // Notification text can look like a payment even when it is a card offer, a cashback
        // campaign or a game promotion. Same high-confidence guard the SMS import uses.
        if (SmsParser.isPromotionalMessage(content)) return null

        for (template in bankTemplates) {
            val match = template.regex.find(content) ?: continue
            if (match.groupValues.size <= maxOf(template.amountGroup, template.merchantGroup)) continue

            val amount = match.groupValues[template.amountGroup]
            if (amount.toDoubleOrNull() == null) continue

            val merchant = match.groupValues[template.merchantGroup]
                .trim()
                .replace(leadingConnector, "")
                .trimEnd('.')
                .take(50)
            return ParsedNotification(amount = amount, merchant = merchant)
        }
        return null
    }
}
