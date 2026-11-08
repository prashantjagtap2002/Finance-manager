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

    /**
     * The user's own rules and which built-in parsers are switched off, from Settings → SMS Rules.
     * Set by `core.SmsRuleStore`; kept here rather than passed in so the receiver, the imports and
     * the tests all go through the same [parse] without each having to fetch it.
     */
    @Volatile
    var config: SmsParserConfig = SmsParserConfig()

    /**
     * Returns true only for messages that contain strong marketing signals without the
     * identity of a completed banking transaction. Words such as "win" and "free" are
     * deliberately matched as whole words: they occur in perfectly valid alerts such as
     * merchant names ("DIMPLE WINES") and "Cashfree".
     */
    fun isPromotionalMessage(message: String): Boolean {
        val promoSignal = Regex(
            "\\b(?:cashback|cash back|voucher|coupon|discount|offer|reward|bonus|prize|winner|" +
                "spin\\s*(?:&|and)\\s*win|credit card points?|points? worth|welcome offer|" +
                "shop now|apply now|use code|request (?:a|your) .*?card|terms? and conditions|t&c)\\b|" +
                "\\b(?:click|redeem)\\b.*https?://",
            RegexOption.IGNORE_CASE
        )
        if (!promoSignal.containsMatchIn(message)) return false

        // A reference/UTR/RRN alongside a completed debit/credit is a transaction alert even
        // when the counterparty or bank template happens to contain a marketing-looking word.
        val completedTransaction = Regex(
            "\\b(?:debited|credited|received|sent|paid|spent|withdrawn|deposited)\\b[\\s\\S]{0,220}" +
                "\\b(?:ref(?:erence)?(?:\\s*(?:no|number|id))?|utr|rrn|txn(?:\\s*id)?)\\b[\\s:#-]*[A-Za-z0-9]{4,}",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(message)
        return !completedTransaction
    }

    /**
     * India's DLT scheme makes every commercial sender ID carry its category as a suffix:
     * `-S` service, `-T` transactional, `-G` government, `-P` promotional. A promotional header
     * is not allowed to carry a transaction alert, so anything sent under one is marketing.
     */
    fun isPromotionalSender(sender: String): Boolean = promotionalSenderRegex.matches(sender.trim())

    private val promotionalSenderRegex = Regex("^[A-Z]{2}-[A-Z0-9]{3,9}-P$", RegexOption.IGNORE_CASE)

    fun parse(sender: String, message: String): ParsedSms? = parseDetailed(sender, message).parsed

    /**
     * [parse], plus what handled the message. Custom rules go first so they can override anything
     * built in, then the promotional filter, the bank-specific parsers and the generic fallback —
     * each of which can be switched off.
     */
    fun parseDetailed(
        sender: String,
        message: String,
        config: SmsParserConfig = this.config
    ): SmsParseResult {
        SmsRuleEngine.apply(config.customRules, sender, message)?.let { return it }
        if (config.isEnabled(BuiltInSmsParser.PROMO_FILTER) &&
            (isPromotionalSender(sender) || isPromotionalMessage(message))
        ) {
            return SmsParseResult(null, SmsParseSource.PromoFiltered)
        }
        return parseBuiltIn(sender, message, config)
    }

    private fun parseBuiltIn(sender: String, message: String, config: SmsParserConfig): SmsParseResult {
        var builtIn = BuiltInSmsParser.GENERIC

        var accountName = ""
        var type = ""
        var amount = ""
        var balance = ""
        var counterparty = ""
        var reference = ""

        val senderUpper = sender.uppercase()
        val msgTrimmed = message.trim()
        // The bank parsers below also recognise a bank from the message text alone ("Kotak Bank",
        // "-SBI"), which is what lets a phone number pass itself off as one. Only a real sender
        // header may be trusted on content; a phone number has to name the bank in its sender,
        // which it never can. Same rule as [isFromFinancialInstitution] applies to the fallback.
        val trustContent = !phoneNumberSenderRegex.matches(sender.trim())

        // ============================================================
        // 1. TJSB BANK
        // ============================================================
        if (config.isEnabled(BuiltInSmsParser.TJSB) &&
            (senderUpper.contains("TJSB") || (trustContent && Regex("TJSB Bank[.]?\$").containsMatchIn(msgTrimmed)))
        ) {
            accountName = "TJSB"
            builtIn = BuiltInSmsParser.TJSB

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
        else if (config.isEnabled(BuiltInSmsParser.SBI) &&
            ((trustContent && (message.endsWith("-SBI") || message.contains(".-SBI"))) || senderUpper.contains("SBI"))
        ) {
            accountName = "SBI"
            builtIn = BuiltInSmsParser.SBI

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
        else if (config.isEnabled(BuiltInSmsParser.KOTAK) &&
            ((trustContent && message.contains("Kotak Bank", ignoreCase = true)) || senderUpper.contains("KOTAK"))
        ) {
            accountName = "Kotak"
            builtIn = BuiltInSmsParser.KOTAK

            // --- UPI CREDIT ---
            if (Regex("^Received Rs\\.", RegexOption.MULTILINE).containsMatchIn(message)) {
                type = "credit"
                amount = "Rs." + (Regex("Received Rs\\.([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                balance = "Rs." + (Regex("Bal:([\\d,]+\\.?\\d*)").find(message)?.groupValues?.getOrNull(1)?.replace(",", "") ?: "")
                counterparty = kotakParty("from", message)
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
                counterparty = kotakParty("to", message)
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
        else if (config.isEnabled(BuiltInSmsParser.AMAZON_PAY) && (
            sender.contains("57575") ||
            sender == "575754" ||
            senderUpper.contains("JUSPAY") ||
            (trustContent && message.lowercase().contains("apay wallet"))
        )) {
            builtIn = BuiltInSmsParser.AMAZON_PAY
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
        if (type.isEmpty()) {
            if (!config.isEnabled(BuiltInSmsParser.GENERIC)) {
                return SmsParseResult(null, SmsParseSource.NotRecognised)
            }
            if (!isFromFinancialInstitution(sender, message, accountName)) {
                return SmsParseResult(null, SmsParseSource.NotFromBank)
            }
            val generic = parseGeneric(sender, message, accountName)
            return SmsParseResult(
                generic,
                if (generic != null) SmsParseSource.BuiltIn(BuiltInSmsParser.GENERIC) else SmsParseSource.NotRecognised
            )
        }

        return SmsParseResult(
            ParsedSms(
                accountName = accountName,
                type = type,
                amount = amount,
                balance = balance,
                counterparty = counterparty,
                reference = reference,
                rawSender = sender,
                rawMessage = message
            ),
            SmsParseSource.BuiltIn(builtIn)
        )
    }

    /**
     * The payee or payer in Kotak's UPI alerts: "… to SHRI SAI VENUE PARKI on 07-11-26", "… from
     * abc@okaxis on 05-11-26". The name runs up to " on <date>". This used to take one
     * whitespace-free token, which only fits a UPI id — any name with a space failed to match, and
     * the alert was filed under its sender header instead of who was paid.
     */
    private fun kotakParty(preposition: String, message: String): String =
        Regex("\\b$preposition (.+?) on \\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}").find(message)?.groupValues?.get(1)?.trim()
            ?: Regex("\\b$preposition (\\S+) on\\b").find(message)?.groupValues?.get(1)
            ?: ""

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

    private val cardNameRegex = Regex("\\b(?:credit|debit)\\s+card\\b", RegexOption.IGNORE_CASE)

    /** "Thank you for using your … Card", "Card XX1234 has been used for Rs …". */
    private val cardUsedRegex = Regex(
        "\\b(?:used|using)\\b[^.]{0,60}\\bcard\\b|\\bcard\\b[^.]{0,60}\\b(?:used|using)\\b",
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
            "brokerage|free cash|gift card|added (?:to|in) your (?:\\w+\\s+){0,3}wallet|" +
            // Store credit, loyalty and coins are never money in a bank account.
            "store credit|supercash|super ?coins?|loyalty points?|reward points?|referral (?:bonus|reward))\\b",
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

        // "Credit card" and "debit card" name the instrument, not the direction: "used your HDFC
        // Credit Card for Rs 500" is a spend, and reading the first direction word made it income.
        // The phrases are blanked to spaces rather than removed so match positions still line up
        // with [message] for the closest-amount pick below. A card that was "used" is a debit.
        val directionText = cardNameRegex.replace(message) { " ".repeat(it.value.length) }
        val debitMatch = debitRegex.find(directionText)
            ?: cardUsedRegex.find(message)?.takeIf { creditRegex.find(directionText) == null }
        val creditMatch = creditRegex.find(directionText)
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

    /**
     * The fallback reads any message shaped like "Rs.X credited to your account", and shops and
     * apps send exactly that shape about their own store credit ("Rs.300 credited to your
     * Bewakoof account"). A bank or wallet recognised by its sender ID is trusted; anyone else
     * has to show what only a real bank alert carries: a masked account or card number.
     *
     * A sender that is a plain phone number never counts. Indian banks send alerts only from
     * registered DLT headers, so "money credited" from a mobile number is the classic scam. A
     * genuine one-off can still be read with a custom rule, which runs before all of this.
     */
    private fun isFromFinancialInstitution(sender: String, message: String, knownAccountName: String): Boolean {
        if (phoneNumberSenderRegex.matches(sender.trim())) return false
        if (knownAccountName.isNotEmpty() || bankFromSender(sender).isNotEmpty()) return true
        return maskedAccountRegex.containsMatchIn(message)
    }

    private val phoneNumberSenderRegex = Regex("^\\+?[0-9][0-9 -]{6,}$")

    /** `A/c XX1234`, `a/c **1234`, `AC X2769`, `Card no. XXXXXXXX8914`, `A/c X8914-debited`. */
    private val maskedAccountRegex = Regex(
        "\\b(?:a/c|acct|account|ac|card)\\b[^0-9\\n]{0,15}[xX*]+\\d{3,6}\\b",
        RegexOption.IGNORE_CASE
    )

    private fun bankFromSender(sender: String): String {
        val tokens = sender.uppercase().split("-", ".", "_").filter { it.isNotBlank() }
        // Skip the two-letter operator prefix ("VM", "AD", "JD") and any single-letter suffix.
        tokens.filter { it.length > 2 }.forEach { token ->
            knownSenders[token]?.let { return it }
            knownSenders.entries.firstOrNull { (key, _) -> headerStartsWithBank(token, key) }?.let { return it.value }
        }
        return ""
    }

    /**
     * Whether a header the table doesn't list exactly ("HDFCBN", "SBIBNK") still belongs to [key]'s
     * bank. This decides trust, so it used to be too loose: a plain substring test let "BOBBLE"
     * pass as Bank of Baroda and "XYZSBI" as SBI. Now the header has to *start* with the key, and
     * a three-letter key (BOB, BOI, IOB, PNB, SBI) has to be followed by nothing but a bank-style
     * suffix, since three letters open plenty of ordinary words.
     */
    private fun headerStartsWithBank(token: String, key: String): Boolean {
        if (!token.startsWith(key)) return false
        if (key.length >= 4) return true
        return token.removePrefix(key) in shortKeySuffixes
    }

    private val shortKeySuffixes = setOf("", "BK", "BNK", "BANK", "SMS", "TXN", "IBK", "INB", "IND", "UPI", "PSG", "ALRT", "ALERT", "CRD", "CARD", "MSG")
}
