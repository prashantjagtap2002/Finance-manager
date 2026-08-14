package com.example.financemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantKeyTest {

    @Test
    fun collapsesVpaAndPosVariantsOfTheSameMerchant() {
        val fromVpa = MerchantKey.normalize("swiggy@ybl")
        val fromPos = MerchantKey.normalize("SWIGGY*ORDER 8821")
        val fromUpi = MerchantKey.normalize("UPI/Swiggy/Payment")

        assertEquals(fromVpa, fromPos)
        assertEquals(fromVpa, fromUpi)
        assertEquals("swiggy", fromVpa)
    }

    @Test
    fun keepsDistinctMerchantsApart() {
        assertNotEquals(MerchantKey.normalize("swiggy@ybl"), MerchantKey.normalize("zomato@paytm"))
    }

    @Test
    fun stripsCorporateSuffixes() {
        assertEquals("reliance retail", MerchantKey.normalize("Reliance Retail Pvt Ltd"))
    }

    @Test
    fun returnsEmptyWhenNothingIdentifyingIsLeft() {
        assertEquals("", MerchantKey.normalize(""))
        assertEquals("", MerchantKey.normalize("UPI"))
        assertEquals("", MerchantKey.normalize("123456789"))
    }

    @Test
    fun cappedSoAnOverlongDescriptorStillKeys() {
        val key = MerchantKey.normalize("A".repeat(200))
        assertTrue(key.length <= 48)
    }
}
