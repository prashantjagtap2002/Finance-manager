package com.example.financemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun parseInput_rejectsWhatWouldPoisonABalance() {
        for (bad in listOf("NaN", "Infinity", "-Infinity", "-5", "0", "0.00", "", "  ", "abc", "1e400", "1e5", "12.3.4", "999999999999999")) {
            assertEquals("'$bad' must not parse", null, Money.parseInput(bad))
        }
    }

    @Test
    fun parseInput_readsGroupingAndDecimalCommas() {
        assertEquals(1234.0, Money.parseInput("1,234")!!, 0.0)
        assertEquals(123456.0, Money.parseInput("1,23,456")!!, 0.0)
        assertEquals(1234.5, Money.parseInput("1,234.50")!!, 0.0)
        assertEquals(12.5, Money.parseInput("12,50")!!, 0.0)
        assertEquals(12.5, Money.parseInput("12,5")!!, 0.0)
        assertEquals(10.01, Money.parseInput(" 10.005 ")!!, 0.0)
        assertEquals(500.0, Money.parseInput("500")!!, 0.0)
        assertEquals(0.5, Money.parseInput(".5")!!, 0.0)
    }

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
