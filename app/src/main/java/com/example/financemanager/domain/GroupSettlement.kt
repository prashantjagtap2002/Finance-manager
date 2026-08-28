package com.example.financemanager.domain

import kotlin.math.roundToLong

data class SettlementTransfer(
    val fromMemberId: Long,
    val toMemberId: Long,
    val amount: Double
)

data class GroupExpenseForSettlement(
    val amount: Double,
    val paidByMemberId: Long,
    val participantIds: List<Long>
)

/** Calculates a minimal set of transfers from each member's net group balance. */
object GroupSettlement {
    fun calculate(
        members: List<Long>,
        expenses: List<GroupExpenseForSettlement>
    ): List<SettlementTransfer> {
        val balances = members.associateWith { 0L }.toMutableMap()
        expenses.forEach { expense ->
            if (expense.amount <= 0.0) return@forEach
            val participantIds = expense.participantIds.filter { it in balances }.distinct()
            if (participantIds.isEmpty() || expense.paidByMemberId !in balances) return@forEach

            val totalCents = (expense.amount * 100.0).roundToLong()
            val baseShare = totalCents / participantIds.size
            var remainder = totalCents % participantIds.size
            balances[expense.paidByMemberId] = balances.getValue(expense.paidByMemberId) + totalCents
            participantIds.forEach { memberId ->
                val share = baseShare + if (remainder-- > 0) 1L else 0L
                balances[memberId] = balances.getValue(memberId) - share
            }
        }

        val creditors = balances.filterValues { it > 0 }.map { it.key to it.value }.toMutableList()
        val debtors = balances.filterValues { it < 0 }.map { it.key to -it.value }.toMutableList()
        val transfers = mutableListOf<SettlementTransfer>()
        var creditorIndex = 0
        var debtorIndex = 0

        while (creditorIndex < creditors.size && debtorIndex < debtors.size) {
            val (creditorId, credit) = creditors[creditorIndex]
            val (debtorId, debt) = debtors[debtorIndex]
            val amount = minOf(credit, debt)
            if (amount > 0) {
                transfers += SettlementTransfer(debtorId, creditorId, amount / 100.0)
            }
            creditors[creditorIndex] = creditorId to (credit - amount)
            debtors[debtorIndex] = debtorId to (debt - amount)
            if (credit == amount) creditorIndex++
            if (debt == amount) debtorIndex++
        }
        return transfers
    }
}
