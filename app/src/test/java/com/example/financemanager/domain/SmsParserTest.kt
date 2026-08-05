package com.example.financemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    @Test
    fun parsesKotakCreditMessage() {
        val parsed = SmsParser.parse(
            sender = "VM-KOTAKB",
            message = "Received Rs.100.00 in your Kotak Bank AC X2769 from Rahul on 28-07-26. UPI Ref 123456789012. Bal:5000.00"
        )

        assertNotNull(parsed)
        assertEquals("Kotak", parsed?.accountName)
        assertEquals("credit", parsed?.type)
        assertEquals("Rs.100.00", parsed?.amount)
        assertEquals("Rahul", parsed?.counterparty)
    }

    @Test
    fun ignoresNonTransactionMessage() {
        val parsed = SmsParser.parse(
            sender = "VK-SBI",
            message = "Your OTP for login is 481921. Do not share it with anyone."
        )

        assertNull(parsed)
    }
}
