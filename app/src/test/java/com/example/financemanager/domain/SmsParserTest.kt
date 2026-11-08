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

    @Test
    fun ignoresMarketingMessageWithBankingContext() {
        val parsed = SmsParser.parse(
            sender = "VM-HDFCBN",
            message = "Prashant, Swipe & Win Rs.250 Amazon voucher when you spend Rs.1000 " +
                "in the next 2 days on your HDFC Bank Debit Card. Click hdfcbk.io/example T&C"
        )

        assertNull(parsed)
    }

    @Test
    fun ignoresWalletGamingBonus() {
        val parsed = SmsParser.parse(
            sender = "BP-iPAYTM",
            message = "Rs 15 has been credited into your Paytm First Games Bonus Account. " +
                "Use it to play and win Paytm Cash. Click http://example.invalid"
        )

        assertNull(parsed)
    }

    @Test
    fun keepsCompletedTransferWhenMerchantContainsMarketingWord() {
        val parsed = SmsParser.parse(
            sender = "AD-SBIUPI",
            message = "Dear SBI User, your A/c X8914-debited by Rs175.0 transfer to DIMPLE WINES " +
                "Ref No 235943395508. If not done by u, call 1800111109."
        )

        assertNotNull(parsed)
        assertEquals("debit", parsed?.type)
    }

    // --- Sender trust: only banks and wallets, not shops and scammers ---

    @Test
    fun ignoresShopStoreCreditFromUnknownSender() {
        val parsed = SmsParser.parse(
            sender = "VM-BEWAKF-S",
            message = "Hi! Rs.300 has been credited to your Bewakoof account. " +
                "It will expire on 30-Oct. Happy shopping!"
        )

        assertNull(parsed)
    }

    @Test
    fun ignoresMoneyCreditedClaimFromPhoneNumber() {
        val parsed = SmsParser.parse(
            sender = "+919876543210",
            message = "Dear customer, your A/c XX1234 is credited with Rs.25,000.00 Ref 523456789012. " +
                "To claim, verify at bit.ly/example"
        )

        assertNull(parsed)
    }

    @Test
    fun ignoresAnythingFromPromotionalHeader() {
        val parsed = SmsParser.parse(
            sender = "VM-HDFCBK-P",
            message = "Rs.500 credited to your a/c XX1234. Ref 523456789012"
        )

        assertNull(parsed)
    }

    @Test
    fun ignoresStoreCreditEvenFromKnownWalletSender() {
        val parsed = SmsParser.parse(
            sender = "VM-PHONPE-S",
            message = "Rs.50 store credit has been credited to your account for your order."
        )

        assertNull(parsed)
    }

    @Test
    fun keepsUnknownBankAlertWithMaskedAccount() {
        val parsed = SmsParser.parse(
            sender = "VM-COSMOS-S",
            message = "Your A/c XX4521 is debited by Rs.1,200.00 on 09-10-26 towards UPI " +
                "Ref 612345678901. Avl Bal Rs.8,400.00 -Cosmos Bank"
        )

        assertNotNull(parsed)
        assertEquals("debit", parsed?.type)
        assertEquals("Rs.1200.00", parsed?.amount)
    }

    @Test
    fun keepsKnownBankTransactionalHeader() {
        val parsed = SmsParser.parse(
            sender = "AD-HDFCBK-T",
            message = "Rs.640.00 debited from a/c **1234 on 09-10-26 to VPA swiggy@icici. Ref 612345678902."
        )

        assertNotNull(parsed)
        assertEquals("debit", parsed?.type)
    }

    @Test
    fun keepsCashfreeTransferDespiteWordFree() {
        val parsed = SmsParser.parse(
            sender = "VK-SBIINB",
            message = "Your a/c no. XXXXXXXX8914 is credited by Rs.1.00 on 30-11-21 " +
                "by a/c linked to mobile 6XXXXXX977-Cashfree Private Li (IMPS Ref no 133414774032)."
        )

        assertNotNull(parsed)
        assertEquals("credit", parsed?.type)
    }

    // --- Kotak payee names with spaces (most real names) ---

    @Test
    fun kotakSentKeepsMultiWordPayee() {
        val parsed = SmsParser.parse(
            "AD-KOTAKB-S",
            "Sent Rs.30.00 from Kotak Bank A/c X2769 to SHRI SAI VENUE PARKI on 07-11-26. UPI Ref 531112345678. " +
                "Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
        )
        assertEquals("debit", parsed?.type)
        assertEquals("Rs.30.00", parsed?.amount)
        assertEquals("SHRI SAI VENUE PARKI", parsed?.counterparty)
        assertEquals("531112345678", parsed?.reference)
    }

    @Test
    fun kotakSentKeepsMixedCasePayee() {
        val parsed = SmsParser.parse(
            "JX-KOTAKB-S",
            "Sent Rs.150.00 from Kotak Bank A/c X2769 to Vipin Chandra Shukla on 06-11-26. UPI Ref 531012345678. " +
                "Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
        )
        assertEquals("Vipin Chandra Shukla", parsed?.counterparty)
    }

    @Test
    fun kotakSentKeepsUpiIdPayee() {
        val parsed = SmsParser.parse(
            "JM-KOTAKB-S",
            "Sent Rs.21.00 from Kotak Bank A/c X2769 to abc@okaxis on 07-11-26. UPI Ref 531112345679."
        )
        assertEquals("abc@okaxis", parsed?.counterparty)
    }

    @Test
    fun kotakReceivedKeepsMultiWordPayer() {
        val parsed = SmsParser.parse(
            "VM-KOTAKB-S",
            "Received Rs.500.00 in your Kotak Bank AC X2769 from Rahul Kumar Sharma on 05-11-26. UPI Ref 530912345678."
        )
        assertEquals("credit", parsed?.type)
        assertEquals("Rahul Kumar Sharma", parsed?.counterparty)
    }

    // --- Phone-number senders can't borrow a bank's name from the message text ---

    @Test
    fun phoneNumberCannotPoseAsKotak() {
        assertNull(
            SmsParser.parse(
                "+919812345678",
                "Received Rs.25,000.00 in your Kotak Bank A/c X1234 from x@okaxis on 05-11-26. UPI Ref 123456789012"
            )
        )
    }

    @Test
    fun phoneNumberCannotPoseAsSbi() {
        assertNull(
            SmsParser.parse(
                "9812345678",
                "Dear UPI user A/C X8914 debited by 500.0 on date 05Nov26 trf to Rahul Refno 530912345678. -SBI"
            )
        )
    }

    // --- "Credit card" names the card, not the direction ---

    @Test
    fun creditCardSpendIsADebit() {
        val parsed = SmsParser.parse(
            "VM-HDFCBK",
            "Thank you for using your HDFC Bank Credit Card ending 1234 for Rs 500.00 at AMAZON"
        )
        assertEquals("debit", parsed?.type)
        assertEquals("Rs.500.00", parsed?.amount)
    }

    @Test
    fun creditCardRefundIsStillACredit() {
        val parsed = SmsParser.parse(
            "VM-HDFCBK",
            "Rs 500.00 credited to your HDFC Bank Credit Card XX1234 as refund from AMAZON. Ref 531012345678"
        )
        assertEquals("credit", parsed?.type)
    }

    // --- Sender trust is by header, not by any three letters inside it ---

    @Test
    fun lookalikeHeaderIsNotABank() {
        // "BOBBLE" starts with BOB but is not Bank of Baroda, and the message carries no masked account.
        assertNull(
            SmsParser.parse("VM-BOBBLE-S", "Rs.300 credited to your account. Ref 531012345678")
        )
    }

    @Test
    fun unlistedBankHeaderStillTrusted() {
        // Not in the table, but PNB plus a bank-style suffix — and no masked account to fall back on.
        val parsed = SmsParser.parse("AD-PNBBNK-S", "Rs.300 credited to your account. Ref 531012345678")
        assertEquals("credit", parsed?.type)
        assertEquals("Punjab National Bank", parsed?.accountName)
    }
}
