package com.example.financemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsAmountFormatterTest {
    @Test
    fun removesLeadingPunctuationFromLegacySmsAmount() {
        assertEquals("315.00", SmsAmountFormatter.number("Rs..315.00"))
        assertEquals("Rs.315.00", SmsAmountFormatter.display(".315.00"))
    }
}
