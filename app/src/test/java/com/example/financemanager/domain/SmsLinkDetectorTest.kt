package com.example.financemanager.domain

import com.example.financemanager.data.SmsTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsLinkDetectorTest {

    private var nextId = 1L

    private fun sms(
        type: String,
        amount: String,
        account: String,
        minutesFromStart: Long,
        reference: String = "",
        body: String = "",
        isApproved: Boolean = false,
        isIgnored: Boolean = false
    ): SmsTransaction {
        val id = nextId++
        return SmsTransaction(
            id = id,
            smsHash = "hash$id",
            sender = "VM-BANK",
            body = body,
            accountName = account,
            type = type,
            amount = amount,
            reference = reference,
            rawTimestamp = START + minutesFromStart * 60_000L,
            isApproved = isApproved,
            isIgnored = isIgnored
        )
    }

    @Test
    fun pairsDebitAndCreditAcrossAccountsAsSelfTransfer() {
        val debit = sms("debit", "Rs.5000.00", "SBI", 0)
        val credit = sms("credit", "Rs.5000.00", "Kotak", 2)

        val links = SmsLinkDetector.detect(listOf(debit, credit))

        assertEquals(1, links.size)
        assertEquals(SmsLinkKind.SELF_TRANSFER, links[0].kind)
        assertEquals(5000.0, links[0].amount, 0.001)
        assertEquals(debit.id, links[0].debit.id)
        assertEquals(credit.id, links[0].credit.id)
    }

    @Test
    fun pairsRefundBackIntoTheSameAccountAsReversal() {
        val debit = sms("debit", "Rs.14750.00", "SBI", 0, body = "IPO application ASBA block")
        val credit = sms(
            "credit",
            "Rs.14750.00",
            "SBI",
            9 * 24 * 60,
            body = "Your blocked amount has been released as no shares were allotted."
        )

        val links = SmsLinkDetector.detect(listOf(debit, credit))

        assertEquals(1, links.size)
        assertEquals(SmsLinkKind.REVERSAL, links[0].kind)
        assertTrue(links[0].statedReversal)
    }

    @Test
    fun pairsAnAlreadyApprovedDebitSoTheExpenseCanBeUndone() {
        val debit = sms("debit", "Rs.2000.00", "SBI", 0, isApproved = true)
        val credit = sms("credit", "Rs.2000.00", "SBI", 60, body = "Refund processed")

        val links = SmsLinkDetector.detect(listOf(debit, credit))

        assertEquals(1, links.size)
        assertTrue(links[0].debit.isApproved)
    }

    @Test
    fun ignoresCreditThatArrivedBeforeTheDebit() {
        val credit = sms("credit", "Rs.900.00", "Kotak", 0)
        val debit = sms("debit", "Rs.900.00", "SBI", 30)

        assertTrue(SmsLinkDetector.detect(listOf(debit, credit)).isEmpty())
    }

    /**
     * Candidates are bucketed by whole paise to avoid comparing every debit against every credit.
     * A pair whose amounts differ by less than the epsilon but round into *adjacent* buckets is
     * the case that bucketing can silently drop, so it is pinned here.
     */
    @Test
    fun pairsAmountsThatStraddleAPaiseBoundary() {
        val debit = sms("debit", "Rs.5000.004", "SBI", 0)
        val credit = sms("credit", "Rs.5000.006", "Kotak", 2)

        val links = SmsLinkDetector.detect(listOf(debit, credit))

        assertEquals(1, links.size)
        assertEquals(debit.id, links[0].debit.id)
        assertEquals(credit.id, links[0].credit.id)
    }

    /** The other side of that boundary: a gap wider than the epsilon must still not pair. */
    @Test
    fun ignoresAmountsJustOutsideTheEpsilon() {
        val debit = sms("debit", "Rs.5000.00", "SBI", 0)
        val credit = sms("credit", "Rs.5000.02", "Kotak", 2)

        assertEquals(0, SmsLinkDetector.detect(listOf(debit, credit)).size)
    }

    @Test
    fun ignoresDifferentAmounts() {
        val debit = sms("debit", "Rs.500.00", "SBI", 0)
        val credit = sms("credit", "Rs.501.00", "Kotak", 1)

        assertTrue(SmsLinkDetector.detect(listOf(debit, credit)).isEmpty())
    }

    @Test
    fun ignoresSameAccountRefundLongAfterTheDebitWithNoEvidence() {
        val debit = sms("debit", "Rs.700.00", "SBI", 0)
        val credit = sms("credit", "Rs.700.00", "SBI", 10 * 24 * 60, body = "Amount credited by transfer")

        assertTrue(SmsLinkDetector.detect(listOf(debit, credit)).isEmpty())
    }

    @Test
    fun keepsFarApartLegsWhenTheyShareAReference() {
        val debit = sms("debit", "Rs.700.00", "SBI", 0, reference = "IMPS998877")
        val credit = sms("credit", "Rs.700.00", "SBI", 20 * 24 * 60, reference = "IMPS998877")

        val links = SmsLinkDetector.detect(listOf(debit, credit))

        assertEquals(1, links.size)
        assertTrue(links[0].sharedReference)
    }

    @Test
    fun matchesRepeatedApplicationsOneToOneInsteadOfAllToTheSameLeg() {
        val firstDebit = sms("debit", "Rs.15000.00", "SBI", 0, reference = "IPO111")
        val secondDebit = sms("debit", "Rs.15000.00", "SBI", 60, reference = "IPO222")
        val firstCredit = sms("credit", "Rs.15000.00", "SBI", 7 * 24 * 60, reference = "IPO111", body = "refund")
        val secondCredit = sms("credit", "Rs.15000.00", "SBI", 7 * 24 * 60 + 5, reference = "IPO222", body = "refund")

        val links = SmsLinkDetector.detect(listOf(firstDebit, secondDebit, firstCredit, secondCredit))

        assertEquals(2, links.size)
        assertTrue(links.all { it.sharedReference })
        assertEquals(
            setOf(firstDebit.id to firstCredit.id, secondDebit.id to secondCredit.id),
            links.map { it.debit.id to it.credit.id }.toSet()
        )
    }

    @Test
    fun readsAmountsInTheFormatTheParserWrites() {
        assertEquals(1234.5, SmsLinkDetector.amountOf(sms("debit", "Rs.1,234.50", "SBI", 0))!!, 0.001)
    }

    @Test
    fun skipsPairsTheUserDismissed() {
        val debit = sms("debit", "Rs.5000.00", "SBI", 0)
        val credit = sms("credit", "Rs.5000.00", "Kotak", 2)
        val key = linkKey(debit, credit)

        assertTrue(SmsLinkDetector.detect(listOf(debit, credit), setOf(key)).isEmpty())
    }

    @Test
    fun skipsIgnoredAlerts() {
        val debit = sms("debit", "Rs.5000.00", "SBI", 0, isIgnored = true)
        val credit = sms("credit", "Rs.5000.00", "Kotak", 2)

        assertTrue(SmsLinkDetector.detect(listOf(debit, credit)).isEmpty())
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
