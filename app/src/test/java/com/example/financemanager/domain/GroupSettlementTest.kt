package com.example.financemanager.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GroupSettlementTest {
    @Test
    fun splitsReceiptAndUsesOneTransferPerDebtor() {
        val transfers = GroupSettlement.calculate(
            members = listOf(1L, 2L, 3L),
            expenses = listOf(
                GroupExpenseForSettlement(90.0, 1L, listOf(1L, 2L, 3L))
            )
        )

        assertEquals(2, transfers.size)
        assertEquals(setOf(2L, 3L), transfers.map { it.fromMemberId }.toSet())
        assertEquals(listOf(30.0, 30.0), transfers.map { it.amount })
        assertEquals(setOf(1L), transfers.map { it.toMemberId }.toSet())
    }

    @Test
    fun ignoresInvalidParticipantsAndRoundsAtTheCent() {
        val transfers = GroupSettlement.calculate(
            members = listOf(1L, 2L, 3L),
            expenses = listOf(
                GroupExpenseForSettlement(100.0, 1L, listOf(1L, 2L, 3L, 99L)),
                GroupExpenseForSettlement(20.0, 2L, listOf(1L, 2L))
            )
        )

        assertEquals(2, transfers.size)
        assertEquals(23.33, transfers.first { it.fromMemberId == 2L }.amount, 0.001)
        assertEquals(33.33, transfers.first { it.fromMemberId == 3L }.amount, 0.001)
    }
}
