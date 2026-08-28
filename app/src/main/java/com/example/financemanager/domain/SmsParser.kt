package com.example.financemanager.domain

data class ParsedSms(
    val accountName: String,
    val type: String,
    val amount: String,
    val balance: String,
    val counterparty: String,
    val reference: String,
    val rawSender: String,
    val rawMessage: String
)

object SmsParser {

    fun parse(sender: String, message: String): ParsedSms? {
        var accountName = ""
        var type = ""
        var amount = ""
        var balance = ""
        var counterparty = ""
        var reference = ""

        val senderUpper = sender.uppercase()
        val msgTrimmed = message.trim()

        // ============================================================
        // 1. TJSB BANK
        // ============================================================
        if (senderUpper.contains("TJSB") || Regex("TJSB Bank[.]?\$").containsMatchIn(msgTrimmed)) {
            accountName = "TJSB"

            // --- UPI CREDIT ---
            if (message.contains("CREDITED") && message.contains(Regex("Rs\\.\\d"))) {
                type = "credit"
                amount = "Rs." + (Regex("Rs\\.([\\d,]+\\.?\\d*)\\s+CREDITED").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Avl BAL:Rs\\.([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("Upi-([\\d]+)-").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("Upi-\\d+-(.*?)-(?:UPI|Payment|Sent usi|Refund f|UPIInten|PayviaRa|scholars|yogesh m|Blinkit|UPIPAY)").find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: ""
            }
            // --- UPI DEBIT ---
            else if (message.contains("DEBITED") && message.contains(Regex("Rs\\.\\d"))) {
                type = "debit"
                amount = "Rs." + (Regex("Rs\\.([\\d,]+\\.?\\d*)\\s+DEBITED").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Avl BAL:Rs\\.([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("Upi-([\\d]+)-").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("Upi-\\d+-(.*?)-(?:Payment|Self tra|UPIInten|PayviaRa|Blinkit|UPIPAY|Nashik T|Refund f|Executio)").find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: ""
            }
            // --- NEFT/CLEARING DEBIT ---
            else if (Regex("Amount Rs\\.\\s*[\\d,]+\\.?\\d*\\s+is Debited", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("Amount Rs\\.\\s*([\\d,]+\\.?\\d*)\\s+is Debited", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Bal:\\s*([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("(?:IBNEFT|NEFT)/([A-Z0-9]+)/", RegexOption.IGNORE_CASE).find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("(?:IBNEFT|NEFT)/[^/]+/([^/]+)/", RegexOption.IGNORE_CASE).find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim()
                    ?: Regex("by (?:Clearing|Transfer)\\s+(.+?)\\.", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.trim()
                    ?: ""
            }
            // --- NEFT/CLEARING CREDIT ---
            else if (Regex("Amount Rs\\.\\s*[\\d,]+\\.?\\d*\\s+is Credited", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("Amount Rs\\.\\s*([\\d,]+\\.?\\d*)\\s+is Credited", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Bal:\\s*([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("NEFT/[^/]+/([A-Z0-9]+)/", RegexOption.IGNORE_CASE).find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("NEFT/([^/]+)/", RegexOption.IGNORE_CASE).find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: "NEFT Credit"
            }
            // --- IMPS DEBIT ---
            else if (Regex("is debited for Rs\\.", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("debited for Rs\\.([\\d,]+\\.?\\d*)/-", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("IMPS Ref no (\\d+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                counterparty = "Self Transfer (IMPS)"
            }
            // --- IMPS CREDIT ---
            else if (Regex("is credited by Rs\\.", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("credited by Rs\\.([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("IMPS Ref no (\\d+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                counterparty = "IMPS Credit"
            }
        }
        // ============================================================
        // 2. SBI
        // ============================================================
        else if (message.endsWith("-SBI") || message.contains(".-SBI") || senderUpper.contains("SBI")) {
            accountName = "SBI"

            // --- CREDIT (CDM/Transfer) ---
            if (Regex("Credited INR", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("Credited INR ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Avl Bal INR ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val cpM = Regex("-(?:Deposit|Credit) (?:by transfer |of Cash )?(?:at [\\w]+ )?(?:from (.+?))?\\. Avl", RegexOption.IGNORE_CASE).find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: "CDM/Transfer"
            }
            // --- CREDIT (NEFT) ---
            else if (Regex("credited to your A/c", RegexOption.IGNORE_CASE).containsMatchIn(message) && message.contains("NEFT", ignoreCase = true)) {
                type = "credit"
                amount = "Rs." + (Regex("INR ([\\d,]+\\.?\\d*) credited", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("UTR (\\w+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("by (.+?) -SBI", RegexOption.IGNORE_CASE).find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: "NEFT Credit"
            }
            // --- CREDIT (IMPS) ---
            else if (Regex("is credited by Rs\\.", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("credited by Rs\\.([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("IMPS Ref no (\\d+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("mobile [\\dX]+-(.+?) \\(").find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: "IMPS Credit"
            }
            // --- CREDIT (UPI short) ---
            else if (Regex("ur A/c\\w+ credited by Rs", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("credited by Rs([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("Ref no (\\d+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                counterparty = "UPI Credit"
            }
            // --- CREDIT (new format Aug 2024+) ---
            else if (Regex("A/c \\w+-credited by Rs\\.", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("credited by Rs\\.([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("Ref No (\\d+)", RegexOption.IGNORE_CASE).find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("transfer from (.+?) Ref No", RegexOption.IGNORE_CASE).find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: "UPI Credit"
            }
            // --- CREDIT (AEPS) ---
            else if (Regex("credited with Rs\\.", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("credited with Rs\\.\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("RRN (\\d+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                counterparty = "AEPS Cash Deposit"
            }
            // --- DEBIT (Transfer) ---
            else if (Regex("has a debit by transfer", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("transfer of Rs ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Avl Bal Rs ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
            }
            // --- DEBIT (UPI long, new) ---
            else if (Regex("debited by [\\d.]+ on date", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("debited by ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("Refno (\\d+)", RegexOption.IGNORE_CASE).find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("trf to (.+?) Refno", RegexOption.IGNORE_CASE).find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: ""
            }
            // --- DEBIT (new format Aug 2024+) ---
            else if (Regex("A/c \\w+-debited by Rs", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("debited by Rs([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("Ref No (\\d+)", RegexOption.IGNORE_CASE).find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                val cpM = Regex("transfer to (.+?) Ref No", RegexOption.IGNORE_CASE).find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: ""
            }
            // --- DEBIT (UPI old short) ---
            else if (Regex("debited(@|¡|à)?SBI UPI|debited SBI UPI", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("Rs([\\d,]+\\.?\\d*) debited", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("RefNo (\\d+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
            }
            // --- DEBIT (NACH/EMI) ---
            else if (Regex("debit by NACH", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("NACH of Rs ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Avl Bal Rs ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                counterparty = "NACH/EMI Auto Debit"
            }
        }
        // ============================================================
        // 3. KOTAK BANK
        // ============================================================
        else if (message.contains("Kotak Bank", ignoreCase = true) || senderUpper.contains("KOTAK")) {
            accountName = "Kotak"

            // --- UPI CREDIT ---
            if (Regex("^Received Rs\\.", RegexOption.MULTILINE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("Received Rs\\.([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Bal:([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val cpM = Regex("from ([^\\s]+) on").find(message)
                counterparty = cpM?.groupValues?.getOrNull(1) ?: ""
                val refM = Regex("UPI Ref[:\\s]?(\\d+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
            }
            // --- IMPS CREDIT ---
            else if (Regex("Received Rs\\.\\s*[\\d,]+").containsMatchIn(message) && message.contains("IMPS Ref no", ignoreCase = true)) {
                type = "credit"
                amount = "Rs." + (Regex("Received Rs\\.\\s*([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("IMPS Ref no (\\d+)", RegexOption.IGNORE_CASE).find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                counterparty = "IMPS Credit"
            }
            // --- REVERSAL CREDIT ---
            else if (Regex("is credited to Kotak Bank", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("Rs\\.([\\d,]+\\.?\\d*) is credited").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("UPI Ref no (\\d+)", RegexOption.IGNORE_CASE).find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                counterparty = "UPI Reversal"
            }
            // --- UPI DEBIT ---
            else if (Regex("^Sent Rs\\.", RegexOption.MULTILINE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("Sent Rs\\.([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Bal:([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val cpM = Regex("to ([^\\s]+) on").find(message)
                counterparty = cpM?.groupValues?.getOrNull(1) ?: ""
                val refM = Regex("UPI Ref (\\d+)").find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
            }
            // --- ATM DEBIT ---
            else if (Regex("INR [\\d,.]+ is debited from", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("INR ([\\d,]+\\.?\\d*) is debited", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("(?:Combined )?Available Balance is INR ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val cpM = Regex("towards (.+?)\\.").find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: "ATM/POS Debit"
            }
            // --- NACH/EMI DEBIT ---
            else if (Regex("INR [\\d,.]+ is debited to your Account", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("INR ([\\d,]+\\.?\\d*) is debited", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val cpM = Regex("towards (.+?) Kotak Bank", RegexOption.IGNORE_CASE).find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: "NACH/EMI Auto Debit"
            }
        }
        // ============================================================
        // 4. AMAZON PAY / JUSPAY
        // ============================================================
        else if (
            sender.contains("57575") ||
            sender == "575754" ||
            senderUpper.contains("JUSPAY") ||
            message.lowercase().contains("apay wallet")
        ) {
            accountName = "Amazon Pay"

            // --- CREDIT (Add Money) ---
            if (Regex("added to Amazon Pay", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("Rs ?\\.?([\\d,]+\\.?\\d*) added", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("(?:Updated )?Balance:?\\s*(?:Rs\\.?)?\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                counterparty = "Wallet Top-Up"
            }
            // --- DEBIT (Juspay) ---
            else if (Regex("Apay Wallet balance is debited for INR", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("debited for INR ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val refM = Regex("(?:Transaction )?Reference Number(?:\\s+is)?\\s+(\\d+)", RegexOption.IGNORE_CASE).find(message)
                reference = refM?.groupValues?.getOrNull(1) ?: ""
                counterparty = "Amazon Pay Wallet"
            }
            // --- DEBIT (Juspay summary) ---
            else if (Regex("using Apay Balance successful", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("Payment of Rs ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("(?:Updated )?Balance(?:\\s+is)?\\s+Rs\\s*([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                counterparty = "Amazon Pay Purchase"
            }
            // --- DEBIT (direct +9157 sender) ---
            else if (Regex("using Amazon Pay Balance is successful", RegexOption.IGNORE_CASE).containsMatchIn(message)) {
                type = "debit"
                amount = "Rs." + (Regex("Payment of Rs ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Updated Balance:\\s*Rs ([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                val cpM = Regex("successful at (.+?)\\.").find(message)
                counterparty = cpM?.groupValues?.getOrNull(1)?.trim() ?: "Amazon Pay Purchase"
            }
        }

        // Nothing bank-specific matched. Fall back to the shape almost every Indian bank and
        // wallet alert shares, so HDFC/ICICI/Axis/BoB/PNB/Paytm/PhonePe/GPay users get something
        // instead of nothing.
        if (type.isEmpty()) return parseGeneric(sender, message, accountName)

        return ParsedSms(
            accountName = accountName,
            type = type,
            amount = amount,
            balance = balance,
            counterparty = counterparty,
            reference = reference,
            rawSender = sender,
            rawMessage = message
        )
    }

    // ================================================================
    // GENERIC FALLBACK
    // ================================================================

    private const val AMOUNT = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"

    /** `Rs.500`, `Rs 500`, `INR 500`, `₹500`. */
    private val amountRegex = Regex("(?:INR|RS\\.?|\u20B9)\\s*$AMOUNT", RegexOption.IGNORE_CASE)

    private val debitRegex = Regex(
        "\\b(debited|debit|spent|paid|withdrawn|deducted|sent|purchase[d]?)\\b",
        RegexOption.IGNORE_CASE
    )
    private val creditRegex = Regex(
        "\\b(credited|credit|received|deposited|refund(?:ed)?|added)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * Phrases that mean "this is not a transaction that happened". Scheduled debits, bill
     * reminders, collect requests and failures all otherwise look exactly like the real thing.
     */
    private val notATransactionRegex = Regex(
        "\\b(otp|one[ -]?time password|do not share|" +
            "(?:will|shall|would) be (?:debited|deducted|credited|charged)|" +
            "is due|due on|due date|total due|payment due|min(?:imum)? (?:amt|amount) due|" +
            "has requested|is requesting|collect request|payment request|" +
            "failed|declined|unsuccessful|insufficient|could not be processed|" +
            "click here|apply now|pre-approved|pre-qualified|you are eligible|" +
            "welcome bonus|bonus (?:can be|is ready to be) credited|prize pool|" +
            "brokerage|free cash|gift card|added (?:to|in) your (?:\\w+\\s+){0,3}wallet)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Something in the message has to look like banking, or any SMS with a price would match. */
    private val bankingContextRegex = Regex(
        "\\b(a/c|acct|account|card|upi|imps|neft|rtgs|txn|transaction|ref|utr|rrn|wallet|vpa|bal|balance)\\b",
        RegexOption.IGNORE_CASE
    )

    private val balanceRegex = Regex(
        "\\b(?:avl|available|updated|closing|a/c|total)?\\s*bal(?:ance)?\\b[^0-9]{0,15}$AMOUNT",
        RegexOption.IGNORE_CASE
    )

    private val accountTailRegex = Regex(
        "\\b(?:a/c|acct|account|card)\\s*(?:no\\.?|number)?\\s*[:#]?\\s*[xX*.]{0,6}(\\d{3,6})\\b",
        RegexOption.IGNORE_CASE
    )

    private val referenceRegex = Regex(
        "\\b(?:ref(?:erence)?(?:\\s*(?:no|number|id))?|utr|rrn|txn\\s*id|transaction\\s*id)\\b" +
            "[\\s:.#-]*([A-Za-z0-9]{4,25})",
        RegexOption.IGNORE_CASE
    )

    private val vpaRegex = Regex("\\b([A-Za-z0-9._-]{2,}@[A-Za-z]{2,})\\b")

    private val debitCounterpartyRegex = Regex(
        "\\b(?:to|at|towards|in favour of)\\s+([A-Za-z0-9][A-Za-z0-9 &'@._-]{1,39}?)" +
            "(?=\\s*(?:\\.|,|;|$|\\b(?:on|via|using|with|from|ref|upi|a/c|avl|bal|info|not you|your)\\b))",
        RegexOption.IGNORE_CASE
    )
    private val creditCounterpartyRegex = Regex(
        "\\b(?:from|by)\\s+([A-Za-z0-9][A-Za-z0-9 &'@._-]{1,39}?)" +
            "(?=\\s*(?:\\.|,|;|$|\\b(?:on|via|using|with|to|ref|upi|a/c|avl|bal|info|not you|your)\\b))",
        RegexOption.IGNORE_CASE
    )

    /**
     * Sender IDs arrive DLT-formatted (`VM-HDFCBK`, `AD-ICICIB-S`). The middle token identifies
     * the institution; anything unrecognised is passed through so the user can still map it to an
     * account by hand.
     */
    private val knownSenders = mapOf(
        "HDFC" to "HDFC Bank", "HDFCBK" to "HDFC Bank",
        "ICICI" to "ICICI Bank", "ICICIB" to "ICICI Bank", "ICICIT" to "ICICI Bank",
        "AXIS" to "Axis Bank", "AXISBK" to "Axis Bank",
        "SBI" to "SBI", "SBIINB" to "SBI", "SBIUPI" to "SBI", "SBIPSG" to "SBI", "ATMSBI" to "SBI", "CBSSBI" to "SBI",
        "KOTAK" to "Kotak", "KOTAKB" to "Kotak",
        "PNB" to "Punjab National Bank", "PNBSMS" to "Punjab National Bank",
        "BOB" to "Bank of Baroda", "BOBTXN" to "Bank of Baroda", "BOBSMS" to "Bank of Baroda", "BOBIBK" to "Bank of Baroda",
        "CANBNK" to "Canara Bank", "CANARA" to "Canara Bank",
        "UNIONB" to "Union Bank", "UNIONBK" to "Union Bank", "UBIN" to "Union Bank",
        "IDFCFB" to "IDFC First Bank", "IDFC" to "IDFC First Bank",
        "INDUSB" to "IndusInd Bank", "INDUS" to "IndusInd Bank",
        "YESBNK" to "Yes Bank", "YESBK" to "Yes Bank",
        "IOBCHN" to "Indian Overseas Bank", "IOB" to "Indian Overseas Bank",
        "CENTBK" to "Central Bank of India",
        "UCOBNK" to "UCO Bank",
        "BOIIND" to "Bank of India", "BOI" to "Bank of India",
        "FEDBNK" to "Federal Bank",
        "RBLBNK" to "RBL Bank",
        "AUBANK" to "AU Small Finance Bank",
        "BANDHN" to "Bandhan Bank",
        "INDBNK" to "Indian Bank",
        "PAYTM" to "Paytm", "PAYTMB" to "Paytm", "PYTMPB" to "Paytm",
        "PHONPE" to "PhonePe", "PHONEPE" to "PhonePe",
        "GPAY" to "Google Pay", "GOOGLE" to "Google Pay",
        "AMZNPAY" to "Amazon Pay", "AMAZON" to "Amazon Pay",
        "SLICEIT" to "Slice", "JUPITER" to "Jupiter", "FIMONEY" to "Fi"
    )

    private fun parseGeneric(sender: String, message: String, knownAccountName: String): ParsedSms? {
        if (notATransactionRegex.containsMatchIn(message)) return null
        if (!bankingContextRegex.containsMatchIn(message)) return null

        val debitMatch = debitRegex.find(message)
        val creditMatch = creditRegex.find(message)
        val directionMatch = listOfNotNull(debitMatch, creditMatch).minByOrNull { it.range.first } ?: return null

        // Amounts quoted right after a balance/limit word describe the account, not the movement.
        val balanceMatch = balanceRegex.find(message)
        val balanceDigitsAt = balanceMatch?.groups?.get(1)?.range?.first
        val amounts = amountRegex.findAll(message)
            .filter { it.groups[1]?.range?.first != balanceDigitsAt }
            .toList()
        if (amounts.isEmpty()) return null

        // The transacted amount is the one sitting closest to the debited/credited word.
        val amountMatch = amounts.minByOrNull {
            kotlin.math.abs(it.range.first - directionMatch.range.first)
        } ?: return null

        val isDebit = debitMatch != null &&
            (creditMatch == null || debitMatch.range.first <= creditMatch.range.first)

        val counterpartyRegex = if (isDebit) debitCounterpartyRegex else creditCounterpartyRegex
        val counterparty = vpaRegex.find(message)?.groupValues?.get(1)
            ?: counterpartyRegex.find(message)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')
            ?: ""

        val accountName = knownAccountName.ifEmpty {
            val tail = accountTailRegex.find(message)?.groupValues?.get(1)
            val bank = bankFromSender(sender)
            when {
                bank.isNotEmpty() && tail != null -> "$bank $tail"
                bank.isNotEmpty() -> bank
                tail != null -> "A/c $tail"
                else -> sender
            }
        }

        return ParsedSms(
            accountName = accountName,
            type = if (isDebit) "debit" else "credit",
            amount = "Rs." + amountMatch.groupValues[1].replace(",", ""),
            balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.let { "Rs.$it" } ?: "",
            counterparty = counterparty,
            reference = referenceRegex.find(message)?.groupValues?.get(1) ?: "",
            rawSender = sender,
            rawMessage = message
        )
    }

    private fun bankFromSender(sender: String): String {
        val tokens = sender.uppercase().split("-", ".", "_").filter { it.isNotBlank() }
        // Skip the two-letter operator prefix ("VM", "AD", "JD") and any single-letter suffix.
        tokens.filter { it.length > 2 }.forEach { token ->
            knownSenders[token]?.let { return it }
            knownSenders.entries.firstOrNull { token.contains(it.key) }?.let { return it.value }
        }
        return ""
    }
}
