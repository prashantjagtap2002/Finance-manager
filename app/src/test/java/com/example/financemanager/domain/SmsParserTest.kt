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

    // --- Generic fallback: banks with no dedicated rules ---

    @Test
    fun genericFallbackParsesHdfcUpiDebit() {
        val parsed = SmsParser.parse(
            sender = "VM-HDFCBK",
            message = "Rs.1,250.00 debited from a/c **1234 on 12-08-25 to VPA chaipoint@ybl. " +
                "Ref 523456789012. Not you? Call 18002586161"
        )

        assertNotNull(parsed)
        assertEquals("debit", parsed?.type)
        assertEquals("Rs.1250.00", parsed?.amount)
        assertEquals("chaipoint@ybl", parsed?.counterparty)
        assertEquals("523456789012", parsed?.reference)
        assertEquals("HDFC Bank 1234", parsed?.accountName)
    }

    @Test
    fun genericFallbackTakesTransactedAmountNotBalance() {
        val parsed = SmsParser.parse(
            sender = "AD-ICICIB",
            message = "Dear Customer, Acct XX123 is credited with Rs 5,000.00 on 12-Aug-25 " +
                "from RAHUL SHARMA. Avl Bal Rs 12,345.67 -ICICI Bank"
        )

        assertNotNull(parsed)
        assertEquals("credit", parsed?.type)
        assertEquals("Rs.5000.00", parsed?.amount)
        assertEquals("Rs.12345.67", parsed?.balance)
    }

    @Test
    fun genericFallbackParsesCardSpend() {
        val parsed = SmsParser.parse(
            sender = "AX-AXISBK",
            message = "Spent Card no. XX1234 INR 899.00 12-08-25 SWIGGY Avl Lmt INR 45,000.00"
        )

        assertNotNull(parsed)
        assertEquals("debit", parsed?.type)
        assertEquals("Rs.899.00", parsed?.amount)
    }

    @Test
    fun genericFallbackParsesWalletPayment() {
        val parsed = SmsParser.parse(
            sender = "VM-PHONPE",
            message = "You paid Rs.250 to Chai Point via UPI. UPI transaction ID 123456789012."
        )

        assertNotNull(parsed)
        assertEquals("debit", parsed?.type)
        assertEquals("Rs.250", parsed?.amount)
        assertEquals("Chai Point", parsed?.counterparty)
        assertEquals("PhonePe", parsed?.accountName)
    }

    @Test
    fun genericFallbackIgnoresScheduledDebit() {
        val parsed = SmsParser.parse(
            sender = "VM-HDFCBK",
            message = "Rs.2,500.00 will be debited from your a/c XX1234 on 05-Sep-25 towards your SIP."
        )

        assertNull(parsed)
    }

    @Test
    fun genericFallbackIgnoresBillReminder() {
        val parsed = SmsParser.parse(
            sender = "AD-AXISBK",
            message = "Your credit card bill of Rs.8,432.00 is due on 18-Aug-25. Min amount due Rs.500.00."
        )

        assertNull(parsed)
    }

    @Test
    fun genericFallbackIgnoresCollectRequest() {
        val parsed = SmsParser.parse(
            sender = "VM-PAYTMB",
            message = "Rahul has requested Rs.500 from you on UPI. Approve in the app."
        )

        assertNull(parsed)
    }

    @Test
    fun genericFallbackIgnoresFailedPayment() {
        val parsed = SmsParser.parse(
            sender = "VM-HDFCBK",
            message = "Your payment of Rs.500 to Amazon has failed. A/c XX1234 was not debited."
        )

        assertNull(parsed)
    }

    @Test
    fun genericFallbackIgnoresPricedPromoWithoutBankingContext() {
        val parsed = SmsParser.parse(
            sender = "VM-SHOPPY",
            message = "Flat Rs.500 off on your next order! Shop now and get more."
        )

        assertNull(parsed)
    }
}
