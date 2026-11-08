package com.example.financemanager.domain

import com.example.financemanager.data.Transaction
import kotlin.math.abs

/** Two transactions that look like the same payment recorded twice. [first] is the older one. */
data class DuplicatePair(val first: Transaction, val second: Transaction) {
    val key: String get() = DuplicateDetector.pairKey(first.id, second.id)
}

/**
 * Finds the same payment logged twice — most often typed in by hand at the till and then logged
 * again from the bank's SMS a minute later.
 *
 * Matching on amount and time alone would flag every pair of ₹20 teas, so a pair also has to look
 * like two *sources* for one event: one row logged from an alert and one typed by hand, or two
 * rows that agree on account and note as well.
 */
object DuplicateDetector {

    /** How far apart two records of one payment can be. SMS alerts usually land within a minute. */
    const val WINDOW_MS = 15 * 60 * 1000L

    fun pairKey(a: Long, b: Long): String = if (a < b) "$a:$b" else "$b:$a"

    fun findPairs(
        transactions: List<Transaction>,
        dismissed: Set<String> = emptySet(),
        windowMs: Long = WINDOW_MS
    ): List<DuplicatePair> {
        val sorted = transactions.sortedBy { it.date }
        val pairs = mutableListOf<DuplicatePair>()
        val claimed = HashSet<Long>()

        for (i in sorted.indices) {
            val a = sorted[i]
            if (a.id in claimed || !isCandidate(a)) continue
            var j = i + 1
            while (j < sorted.size && sorted[j].date - a.date <= windowMs) {
                val b = sorted[j]
                if (b.id !in claimed && isCandidate(b) && looksLikeSamePayment(a, b) &&
                    pairKey(a.id, b.id) !in dismissed
                ) {
                    pairs += DuplicatePair(a, b)
                    // A row is offered in one pair at most; resolving it settles the rest.
                    claimed += a.id
                    claimed += b.id
                    break
                }
                j++
            }
        }
        return pairs.sortedByDescending { it.second.date }
    }

    /**
     * An already-logged transaction that an incoming alert for [amount] at [date] would duplicate,
     * or null. Used before approving an SMS, where the alert has no row of its own yet.
     */
    fun findExisting(
        amount: Double,
        isCredit: Boolean,
        date: Long,
        transactions: List<Transaction>,
        windowMs: Long = WINDOW_MS
    ): Transaction? = transactions
        .asSequence()
        .filter { isCandidate(it) && !it.isAutoLogged }
        .filter { sameAmount(it.amount, amount) && abs(it.date - date) <= windowMs }
        .filter { (it.type == com.example.financemanager.data.TransactionType.INCOME) == isCredit }
        .minByOrNull { abs(it.date - date) }

    // Recurring rows are generated, and split legs share an event on purpose: neither is a
    // double entry.
    private fun isCandidate(t: Transaction) = !t.isRecurring && t.splitGroupId == null && t.amount > 0.0

    private fun looksLikeSamePayment(a: Transaction, b: Transaction): Boolean {
        if (a.type != b.type || !sameAmount(a.amount, b.amount)) return false
        val differentSources = a.isAutoLogged != b.isAutoLogged
        val identical = a.sourceAccountId == b.sourceAccountId &&
            a.note.trim().equals(b.note.trim(), ignoreCase = true)
        return differentSources || identical
    }

    private fun sameAmount(a: Double, b: Double) = abs(a - b) < 0.005
}
