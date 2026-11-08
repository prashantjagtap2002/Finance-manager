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

    /** Larger than any amount a person types on purpose; anything above is a slip or a paste. */
    const val MAX_INPUT_AMOUNT = 100_000_000_000.0

    /**
     * What the user typed as an amount, as money: positive, finite, at most [MAX_INPUT_AMOUNT],
     * rounded to the minor unit — or null when it isn't one.
     *
     * `String.toDoubleOrNull` alone accepts "NaN" and "Infinity" (both pasteable), and a NaN slips
     * past every `amount <= 0` check and turns the account balance into NaN for good. Commas are
     * read the way they're written: "1,234" and "1,23,456" are grouping, but a single comma with
     * one or two digits after it ("12,50") is a decimal comma, since that keyboard layout exists.
     */
    fun parseInput(text: String): Double? {
        var s = text.trim().replace(" ", "").replace(" ", "")
        if (s.isEmpty() || s.any { !(it.isDigit() || it == '.' || it == ',') }) return null
        s = when {
            ',' !in s -> s
            '.' in s -> s.replace(",", "")
            s.count { it == ',' } == 1 && s.substringAfter(',').length in 1..2 -> s.replace(',', '.')
            else -> s.replace(",", "")
        }
        val value = s.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value > MAX_INPUT_AMOUNT) return null
        return roundToMinorUnit(value).takeIf { it > 0.0 }
    }

    /** Whether two amounts are the same money, ignoring representation noise. */
    fun sameAmount(a: Double, b: Double): Boolean = kotlin.math.abs(a - b) <= EPSILON

    /** Whether [amount] is close enough to zero to be no money at all. */
    fun isZero(amount: Double): Boolean = kotlin.math.abs(amount) <= EPSILON
}
