package com.example.financemanager.domain

import com.example.financemanager.data.SmsTransaction

/** What a matched debit/credit pair actually was. */
enum class SmsLinkKind {
    /** Money moved between two accounts the user owns. Net worth is unchanged. */
    SELF_TRANSFER,

    /**
     * The money came straight back to the account it left — a refund, a failed payment, or an
     * IPO/ASBA block released because nothing was allotted. Nothing was spent.
     */
    REVERSAL
}

/**
 * A debit alert and the credit alert that cancels it.
 *
 * Both legs are real SMS the bank sent, so logging each one on its own counts the money twice:
 * a self transfer shows up as an expense *and* an income, and an IPO application that wasn't
 * allotted leaves a permanent expense behind once the block is released. [SmsLinkDetector] pairs
 * them so the user can resolve both at once.
 */
data class SmsLink(
    val debit: SmsTransaction,
    val credit: SmsTransaction,
    val kind: SmsLinkKind,
    val amount: Double,
    /** Both legs quote the same bank reference — the strongest possible evidence of a pair. */
    val sharedReference: Boolean,
    /** The credit says it is a refund/reversal/release in so many words. */
    val statedReversal: Boolean
) {
    /** Stable identity for the pair, so a dismissal survives the rows being re-queried. */
    val key: String get() = linkKey(debit, credit)

    /** Gap between the two alerts, in milliseconds. */
    val gapMillis: Long get() = kotlin.math.abs(credit.rawTimestamp - debit.rawTimestamp)
}

fun linkKey(debit: SmsTransaction, credit: SmsTransaction): String =
    debit.smsHash + "|" + credit.smsHash

/** [com.example.financemanager.data.SmsTransaction.resolution] when a pair was logged as one transfer. */
const val RESOLUTION_TRANSFER = "TRANSFER"

/** [com.example.financemanager.data.SmsTransaction.resolution] when a pair cancelled out and nothing was logged. */
const val RESOLUTION_REVERSAL = "REVERSAL"

/**
 * Finds debit/credit pairs that cancel each other out.
 *
 * Deliberately a suggestion engine, not an auto-classifier: every match is confirmed by the user
 * before anything is logged, so the rules aim to surface the real pairs without drowning the
 * inbox in coincidences. Precision comes from three signals, in descending order of strength —
 * a shared bank reference, explicit refund wording, and closeness in time.
 */
object SmsLinkDetector {

    /** Two banks alerting on the same instant transfer land within a few minutes of each other. */
    private const val TRANSFER_WINDOW = 20 * 60 * 1000L

    /** With a shared reference the timing hardly matters — NEFT legs can be hours apart. */
    private const val REFERENCED_TRANSFER_WINDOW = 24 * 60 * 60 * 1000L

    /** IPO blocks are released around allotment, up to a couple of weeks after applying. */
    private const val REVERSAL_WINDOW = 30L * 24 * 60 * 60 * 1000L

    /** Without a reference or refund wording, only a same-day-ish return is worth suggesting. */
    private const val UNSTATED_REVERSAL_WINDOW = 3L * 24 * 60 * 60 * 1000L

    /** The credit can be stamped a touch before the debit when the two banks' clocks disagree. */
    private const val CLOCK_SKEW = 2 * 60 * 1000L

    private const val AMOUNT_EPSILON = 0.005

    private val reversalWordsRegex = Regex(
        "\\b(revers(?:al|ed)|refund(?:ed)?|returned|released|unblock(?:ed)?|" +
            "cancel(?:led|ed)?|charge ?back|not utilis(?:ed|ing)|unutilised|" +
            "asba|ipo|mandate|failed transaction|transaction failed)\\b",
        RegexOption.IGNORE_CASE
    )

    /**
     * `Rs.1234.50` to `1234.50`. Null when the parser couldn't read an amount.
     *
     * Matches the number rather than stripping the currency, because the dot in the `Rs.` prefix
     * would otherwise survive and make the whole string unparseable.
     */
    fun amountOf(sms: SmsTransaction): Double? =
        numberRegex.find(sms.amount.replace(",", ""))?.value?.toDoubleOrNull()?.takeIf { it > 0.0 }

    private val numberRegex = Regex("[0-9]+(?:\\.[0-9]+)?")

    /**
     * Pairs the debits in [candidates] with the credits that cancel them.
     *
     * Each SMS is used at most once: the strongest matches claim their legs first, so a run of
     * identical IPO applications pairs up in order instead of all fighting over one refund.
     * Pairs whose [SmsLink.key] is in [dismissedKeys] are left out — the user has said those two
     * are unrelated.
     */
    fun detect(
        candidates: List<SmsTransaction>,
        dismissedKeys: Set<String> = emptySet()
    ): List<SmsLink> {
        val usable = candidates.filterNot { it.isIgnored }
        // A leg whose amount the parser couldn't read can never match, so it is dropped up front
        // rather than re-parsed inside the loop. Amount and refund wording are worked out once per
        // message here; the old nested loop re-ran both regexes on every debit/credit combination.
        val debits = usable.asSequence()
            .filter { it.type == "debit" }
            .mapIndexedNotNull { index, sms -> Leg.of(sms, index) }
            .toList()
        // An already-approved credit is settled income the user chose to log; only the debit side
        // is worth revisiting, because that's the leg that inflates spending.
        val credits = usable.asSequence()
            .filter { it.type == "credit" && !it.isApproved }
            .mapIndexedNotNull { index, sms -> Leg.of(sms, index) }
            .toList()
        if (debits.isEmpty() || credits.isEmpty()) return emptyList()

        // Two legs only pair when their amounts match to within AMOUNT_EPSILON, so there is no
        // reason to compare a debit against every credit in the history — an SMS XML import leaves
        // thousands of rows and this used to be one full pass per debit. Bucketing by whole paise
        // makes it a lookup instead. Epsilon is half a paisa, so two matching amounts can land at
        // most one bucket apart, and probing the neighbours keeps every real pair reachable.
        val creditsByPaise = credits.groupBy { it.paise }

        val possible = mutableListOf<SmsLink>()
        debits.forEach { debit ->
            val nearby = (-1..1).flatMap { creditsByPaise[debit.paise + it].orEmpty() }
            // Restore the original credit ordering, so equally-ranked pairs still break ties the
            // way the straight nested loop did.
            nearby.sortedBy { it.index }.forEach { credit ->
                link(debit, credit)?.let { if (it.key !in dismissedKeys) possible += it }
            }
        }

        // Strongest evidence first, then closest in time, so a weak coincidence never steals a
        // leg from a referenced match.
        val ranked = possible.sortedWith(
            compareByDescending<SmsLink> { it.sharedReference }
                .thenByDescending { it.statedReversal }
                .thenBy { it.gapMillis }
        )

        val claimed = mutableSetOf<Long>()
        return ranked.filter { candidate ->
            if (candidate.debit.id in claimed || candidate.credit.id in claimed) {
                false
            } else {
                claimed += candidate.debit.id
                claimed += candidate.credit.id
                true
            }
        }
    }

    /**
     * One side of a possible pair, with the two values worth computing only once: the parsed
     * amount, and whether the body says "refund"/"reversed"/"released" in so many words.
     * [index] is the message's position in its own list, used to keep tie-breaking stable.
     */
    private class Leg(
        val sms: SmsTransaction,
        val index: Int,
        val amount: Double,
        val statedReversal: Boolean
    ) {
        /** The amount in whole paise, which is what the bucketing keys on. */
        val paise: Long = Math.round(amount * 100)

        companion object {
            fun of(sms: SmsTransaction, index: Int): Leg? {
                val amount = amountOf(sms) ?: return null
                return Leg(sms, index, amount, reversalWordsRegex.containsMatchIn(sms.body))
            }
        }
    }

    /** The pair these two form, or null if they aren't one. */
    private fun link(debitLeg: Leg, creditLeg: Leg): SmsLink? {
        val debit = debitLeg.sms
        val credit = creditLeg.sms
        if (debit.id == credit.id) return null

        val debitAmount = debitLeg.amount
        val creditAmount = creditLeg.amount
        if (kotlin.math.abs(debitAmount - creditAmount) > AMOUNT_EPSILON) return null

        // Money can only come back after it left.
        val gap = credit.rawTimestamp - debit.rawTimestamp
        if (gap < -CLOCK_SKEW) return null
        val absGap = kotlin.math.abs(gap)

        val sharedReference = debit.reference.isNotBlank() &&
            debit.reference.equals(credit.reference, ignoreCase = true)
        val statedReversal = creditLeg.statedReversal

        val sameAccount = debit.accountName.isNotBlank() &&
            debit.accountName.equals(credit.accountName, ignoreCase = true)

        return if (sameAccount) {
            // Back into the account it left: a refund, a failed payment, an IPO block released.
            val within = if (sharedReference || statedReversal) {
                absGap <= REVERSAL_WINDOW
            } else {
                absGap <= UNSTATED_REVERSAL_WINDOW
            }
            if (!within) null
            else SmsLink(debit, credit, SmsLinkKind.REVERSAL, debitAmount, sharedReference, statedReversal)
        } else {
            // Different accounts — or one bank the parser couldn't name. Either way the shape of a
            // self transfer is two near-simultaneous alerts, or two alerts sharing a reference.
            val within = if (sharedReference) absGap <= REFERENCED_TRANSFER_WINDOW else absGap <= TRANSFER_WINDOW
            if (!within) null
            else SmsLink(debit, credit, SmsLinkKind.SELF_TRANSFER, debitAmount, sharedReference, statedReversal)
        }
    }
}
