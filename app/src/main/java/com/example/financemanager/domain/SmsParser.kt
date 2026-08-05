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

        if (type.isEmpty()) return null

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
}
