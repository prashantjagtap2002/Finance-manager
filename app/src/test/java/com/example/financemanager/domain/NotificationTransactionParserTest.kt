package com.example.financemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTransactionParserTest {

    @Test
    fun readsAmountAndMerchantFromASpendAlert() {
        val parsed = NotificationTransactionParser.parse("Spent Rs.250.50 at Amazon Pay using credit card")

        assertEquals("250.50", parsed?.amount)
        assertEquals("Amazon", parsed?.merchant)
    }

    @Test
    fun readsTheAmountFirstLayoutToo() {
        val parsed = NotificationTransactionParser.parse("Rs. 1200 debited at BigBasket")

        assertEquals("1200", parsed?.amount)
        assertEquals("BigBasket", parsed?.merchant)
    }

    @Test
    fun readsTheMerchantFirstLayout() {
        val parsed = NotificationTransactionParser.parse("Paid to Swiggy Rs. 349")

        assertEquals("349", parsed?.amount)
        assertEquals("Swiggy", parsed?.merchant)
    }

    @Test
    fun ignoresAnythingThatIsntAPayment() {
        assertNull(NotificationTransactionParser.parse("Your order has been delivered"))
        assertNull(NotificationTransactionParser.parse(""))
    }

    @Test
    fun ignoresMarketing() {
        // A promotion that quotes an amount is the case that used to become a real transaction.
        assertNull(
            NotificationTransactionParser.parse(
                "Get cashback when you have spent Rs. 400 at any partner store. Use code SAVE40"
            )
        )
    }
}
