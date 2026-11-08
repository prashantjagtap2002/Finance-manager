package com.example.financemanager.domain

/**
 * Reduces the many ways one merchant shows up across SMS alerts to a single lookup key, so a
 * category the user picked once for "SWIGGY*ORDER 8821" also matches "swiggy@ybl" next week.
 */
object MerchantKey {

    /** Payment-rail noise that says nothing about who was actually paid. */
    private val noiseWords = setOf(
        // Rails and processors
        "upi", "vpa", "pos", "atm", "neft", "imps", "rtgs", "ach", "nach", "emi", "txn",
        "payment", "payments", "paytm", "phonepe", "gpay", "bharatpe", "razorpay", "billdesk", "paid",
        // Descriptor boilerplate that varies per transaction
        "order", "orders", "purchase", "purchases", "bill", "billing", "recharge",
        "transaction", "transactions", "sale", "ref",
        // Corporate suffixes
        "pvt", "pvtltd", "ltd", "limited", "llp", "inc", "corp", "enterprises",
        "services", "service", "solutions", "technologies",
        "india", "in", "co", "store", "stores", "the", "and", "of"
    )

    private val separators = Regex("[^a-z0-9]+")
    private val trailingDigits = Regex("\\d{2,}$")

    /**
     * Returns the lookup key for [raw], or an empty string when nothing usable is left — callers
     * should skip storing or matching a rule in that case rather than bucketing every unnamed
     * transaction together.
     */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        // A VPA identifies the payee by its handle; the bank suffix after @ does not.
        val withoutHandle = raw.substringBefore('@')

        val words = withoutHandle
            .lowercase()
            .split(separators)
            .map { it.replace(trailingDigits, "") }
            .filter { it.length > 1 && it !in noiseWords && !it.all(Char::isDigit) }

        return words.joinToString(" ").take(48)
    }

    /** Shorter than this, a prefix says too little: "jio" would claim "jiomart" and "jio fiber". */
    private const val MIN_PREFIX_MATCH = 8

    /**
     * The stored rule key that [key] should use: [key] itself when a rule has exactly it, otherwise
     * the longest stored key that one of the two truncates — banks cut payee names at different
     * lengths, so one person arrives as "RAMESHKUMAR SH" in one alert and "RAMESHKUMAR SHRIGHUR"
     * in the next. Without this, reading the full name lost every category learned under the
     * short one. Null when nothing matches.
     */
    fun bestMatch(key: String, ruleKeys: Collection<String>): String? {
        if (key.isEmpty()) return null
        if (key in ruleKeys) return key
        return ruleKeys
            .filter { stored ->
                minOf(stored.length, key.length) >= MIN_PREFIX_MATCH &&
                    (key.startsWith(stored) || stored.startsWith(key))
            }
            .maxByOrNull { it.length }
    }
}
