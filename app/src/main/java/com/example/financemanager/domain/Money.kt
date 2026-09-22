package com.example.financemanager.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money arithmetic helpers.
 *
 * Amounts are stored as [Double], which cannot represent most decimal fractions exactly: 0.1 + 0.2
 * is 0.30000000000000004, and a balance that absorbs thousands of such additions drifts away from
 * the sum of its transactions by a little more each time. Rounding to the minor unit at every point
 * a balance is *written* keeps the stored number to something a currency can actually express, so
 * the error cannot accumulate across edits even though the type still can't hold every value
 * exactly.
 *
 * This is a containment measure, not the real fix. Storing minor units (paise/cents) as integers
 * would remove the problem at the root, but that means rewriting every money column, every cloud
 * DTO, the backup format and every screen — a change with more risk to real balances than the
 * imprecision it removes. [roundToMinorUnit] is what makes the present representation behave.
 */
object Money {

    /** Smallest difference that is real money rather than floating-point noise. */
    const val EPSILON = 0.005

    /**
     * [amount] rounded to two decimal places, half away from zero — the way money rounds.
     *
     * `BigDecimal.valueOf` rather than the `BigDecimal(double)` constructor: the constructor keeps
     * the double's full binary expansion, so 1.005 arrives as 1.00499999… and rounds down.
     */
    fun roundToMinorUnit(amount: Double): Double {
        if (!amount.isFinite()) return 0.0
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    /** Whether two amounts are the same money, ignoring representation noise. */
    fun sameAmount(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) <= EPSILON

    /** Whether [amount] is close enough to zero to be no money at all. */
    fun isZero(amount: Double): Boolean = kotlin.math.abs(amount) <= EPSILON
}
