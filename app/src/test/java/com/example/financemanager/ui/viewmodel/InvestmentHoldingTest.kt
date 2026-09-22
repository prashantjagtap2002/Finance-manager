package com.example.financemanager.ui.viewmodel

import com.example.financemanager.data.Investment
import com.example.financemanager.data.InvestmentTransaction
import com.example.financemanager.data.InvestmentTxnType
import com.example.financemanager.data.InvestmentType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cost-basis walk behind the portfolio numbers.
 *
 * The previous arithmetic averaged every buy that had ever happened and multiplied by the current
 * quantity, which is only correct while nothing has been sold. These cases are the ones it got
 * wrong.
 */
class InvestmentHoldingTest {

    private val stock = Investment(
        id = 1,
        name = "TCS",
        symbol = "TCS",
        type = InvestmentType.STOCK,
        currentPrice = 400.0
    )

    private fun trade(
        type: InvestmentTxnType,
        quantity: Double,
        price: Double,
        date: Long,
        id: Long = date
    ) = InvestmentTransaction(
        id = id,
        investmentId = 1,
        type = type,
        quantity = quantity,
        pricePerUnit = price,
        amount = quantity * price,
        date = date,
        sourceAccountId = 1
    )

    @Test
    fun aPlainBuyIsWorthWhatItCostUntilThePriceMoves() {
        val holding = computeHolding(
            stock.copy(currentPrice = 300.0),
            listOf(trade(InvestmentTxnType.BUY, 10.0, 300.0, date = 1))
        )

        assertEquals(10.0, holding.quantity, 0.0001)
        assertEquals(3000.0, holding.investedAmount, 0.001)
        assertEquals(300.0, holding.avgBuyPrice, 0.001)
        assertEquals(0.0, holding.gainLoss, 0.001)
        assertEquals(0.0, holding.realizedGain, 0.001)
    }

    @Test
    fun sellingHalfBooksTheProfitAndLeavesTheRestUnrealised() {
        // 10 units at 300, half sold at 500. The 5 units that went cost 1500 and fetched 2500, so
        // 1000 is booked. The 5 left still cost 1500 and are worth 2000 at today's 400.
        val holding = computeHolding(
            stock,
            listOf(
                trade(InvestmentTxnType.BUY, 10.0, 300.0, date = 1),
                trade(InvestmentTxnType.SELL, 5.0, 500.0, date = 2)
            )
        )

        assertEquals(5.0, holding.quantity, 0.0001)
        assertEquals(1500.0, holding.investedAmount, 0.001)
        assertEquals(300.0, holding.avgBuyPrice, 0.001)
        assertEquals("only the units still held count as unrealised", 500.0, holding.gainLoss, 0.001)
        assertEquals("profit taken is booked, not blended", 1000.0, holding.realizedGain, 0.001)
    }

    @Test
    fun sellingHighAndBuyingBackLowerIsNotALoss() {
        // The case the old average-price arithmetic inverted: after booking a gain and re-entering
        // cheaper, it averaged both buys together and reported the holding as down.
        val holding = computeHolding(
            stock,
            listOf(
                trade(InvestmentTxnType.BUY, 10.0, 300.0, date = 1),
                trade(InvestmentTxnType.SELL, 10.0, 600.0, date = 2),
                trade(InvestmentTxnType.BUY, 10.0, 350.0, date = 3)
            )
        )

        assertEquals(10.0, holding.quantity, 0.0001)
        assertEquals("the re-entry stands on its own cost", 3500.0, holding.investedAmount, 0.001)
        assertEquals(3000.0, holding.realizedGain, 0.001)
        assertEquals("the re-entry is 500 up at today's price", 500.0, holding.gainLoss, 0.001)
        assertEquals(3500.0, holding.totalGain, 0.001)
    }

    @Test
    fun afterAFullExitNothingIsHeldAndNothingIsInvested() {
        val holding = computeHolding(
            stock,
            listOf(
                trade(InvestmentTxnType.BUY, 10.0, 300.0, date = 1),
                trade(InvestmentTxnType.SELL, 10.0, 450.0, date = 2)
            )
        )

        assertEquals(0.0, holding.quantity, 0.0001)
        assertEquals("a closed position holds no cost", 0.0, holding.investedAmount, 0.001)
        assertEquals("nor any unrealised gain", 0.0, holding.gainLoss, 0.001)
        assertEquals(1500.0, holding.realizedGain, 0.001)
    }

    @Test
    fun tradesArePricedInDateOrderNotStorageOrder() {
        // The flow hands them over in whatever order the query returned. A sell priced against a
        // buy that hadn't happened yet would book a profit out of thin air.
        val outOfOrder = listOf(
            trade(InvestmentTxnType.SELL, 5.0, 500.0, date = 2),
            trade(InvestmentTxnType.BUY, 10.0, 300.0, date = 1)
        )

        assertEquals(
            computeHolding(stock, outOfOrder.reversed()).realizedGain,
            computeHolding(stock, outOfOrder).realizedGain,
            0.001
        )
        assertEquals(1000.0, computeHolding(stock, outOfOrder).realizedGain, 0.001)
    }

    @Test
    fun aSipBuildsTheSamePositionAsAPlainBuy() {
        val holding = computeHolding(
            stock,
            listOf(
                trade(InvestmentTxnType.SIP_INSTALLMENT, 5.0, 300.0, date = 1),
                trade(InvestmentTxnType.SIP_INSTALLMENT, 5.0, 500.0, date = 2)
            )
        )

        assertEquals(10.0, holding.quantity, 0.0001)
        assertEquals(4000.0, holding.investedAmount, 0.001)
        assertEquals(400.0, holding.avgBuyPrice, 0.001)
        assertEquals(true, holding.hasSip)
    }

    @Test
    fun aHistoricalOversellCannotDriveTheCostBasisNegative() {
        // Selling more than is held is refused at entry now, but a row written by an older version
        // must not be able to produce a negative holding priced as a negative investment.
        val holding = computeHolding(
            stock,
            listOf(
                trade(InvestmentTxnType.BUY, 5.0, 300.0, date = 1),
                trade(InvestmentTxnType.SELL, 20.0, 400.0, date = 2)
            )
        )

        assertEquals(0.0, holding.quantity, 0.0001)
        assertEquals(0.0, holding.investedAmount, 0.001)
    }
}
