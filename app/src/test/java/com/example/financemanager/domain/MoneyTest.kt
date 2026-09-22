package com.example.financemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun repeatedAdditionStaysOnTheMinorUnit() {
        // 0.1 + 0.2 is 0.30000000000000004 in binary floating point, and a balance that absorbs a
        // few thousand such additions walks away from the sum of its transactions. Rounding at
        // every write is what stops the error compounding.
        var balance = 0.0
        repeat(1_000) { balance = Money.roundToMinorUnit(balance + 0.1) }

        assertEquals(100.0, balance, 0.0)
    }

    @Test
    fun aHalfPaiseRoundsAwayFromZeroTheWayMoneyDoes() {
        // BigDecimal.valueOf, not the BigDecimal(double) constructor: the constructor keeps the
        // double's full binary expansion, so 1.005 arrives as 1.00499… and rounds the wrong way.
        assertEquals(1.01, Money.roundToMinorUnit(1.005), 0.0)
        assertEquals(2.68, Money.roundToMinorUnit(2.675), 0.0)
        assertEquals(-1.01, Money.roundToMinorUnit(-1.005), 0.0)
    }

    @Test
    fun anAmountAlreadyOnTheMinorUnitIsLeftAlone() {
        assertEquals(1234.56, Money.roundToMinorUnit(1234.56), 0.0)
        assertEquals(0.0, Money.roundToMinorUnit(0.0), 0.0)
    }

    @Test
    fun anUnrepresentableAmountBecomesZeroRatherThanPoisoningABalance() {
        // Division by a zero quantity is one keystroke away on the investment sheet, and NaN
        // written into a balance makes every comparison afterwards false — including the drift
        // check that would otherwise catch it.
        assertEquals(0.0, Money.roundToMinorUnit(Double.NaN), 0.0)
        assertEquals(0.0, Money.roundToMinorUnit(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun noiseBelowHalfAPaiseIsNotRealMoney() {
        assertTrue(Money.isZero(0.0001))
        assertFalse(Money.isZero(0.01))
        assertTrue(Money.sameAmount(100.0, 100.004))
        assertFalse(Money.sameAmount(100.0, 100.01))
    }
}
