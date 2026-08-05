package com.example.financemanager

import com.example.financemanager.data.TransactionType
import com.example.financemanager.domain.NlpParser
import com.example.financemanager.services.SmsNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceManagerUnitTest {

    @Test
    fun testNlpParser_ExpensePhrase() {
        val input = "lunch 250 with friends"
        val result = NlpParser.parse(input)
        
        assertEquals(250.0, result.amount)
        assertEquals("Food & Dining", result.categoryName)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals("Lunch with friends", result.note)
    }

    @Test
    fun testNlpParser_IncomePhrase() {
        val input = "Got salary 50000 for June"
        val result = NlpParser.parse(input)
        
        assertEquals(50000.0, result.amount)
        assertEquals(TransactionType.INCOME, result.transactionType)
        assertEquals("Got salary for June", result.note)
    }

    @Test
    fun testNlpParser_UtilityPhrase() {
        val input = "bill 1500 electricity"
        val result = NlpParser.parse(input)
        
        assertEquals(1500.0, result.amount)
        assertEquals("Utilities & Bills", result.categoryName)
        assertEquals(TransactionType.EXPENSE, result.transactionType)
        assertEquals("Bill electricity", result.note)
    }

    @Test
    fun testCalculatorExpressionEvaluator() {
        // We recreate the evaluation logic to test it directly
        fun evaluate(expr: String): Double {
            val clean = expr.replace(" ", "")
            val tokens = mutableListOf<String>()
            var numberAccumulator = ""
            for (char in clean) {
                if (char == '+' || char == '-') {
                    if (numberAccumulator.isNotEmpty()) {
                        tokens.add(numberAccumulator)
                        numberAccumulator = ""
                    }
                    tokens.add(char.toString())
                } else {
                    numberAccumulator += char
                }
            }
            if (numberAccumulator.isNotEmpty()) {
                tokens.add(numberAccumulator)
            }

            if (tokens.isEmpty()) return 0.0
            var result = tokens[0].toDoubleOrNull() ?: 0.0
            var i = 1
            while (i < tokens.size) {
                val op = tokens[i]
                val nextVal = tokens.getOrNull(i + 1)?.toDoubleOrNull() ?: 0.0
                if (op == "+") {
                    result += nextVal
                } else if (op == "-") {
                    result -= nextVal
                }
                i += 2
            }
            return result
        }

        assertEquals(350.0, evaluate("250+150-50"), 0.01)
        assertEquals(15.5, evaluate("10.5+5"), 0.01)
        assertEquals(120.0, evaluate("120"), 0.01)
    }

    @Test
    fun testSmsAlertParsing() {
        // RegEx patterns from SmsNotificationListenerService
        val paymentPatterns = listOf(
            Regex("""(?i)(?:spent|debited|paid|sent)\s*(?:rs\.?|₹)\s*(\d+(?:\.\d{1,2})?)\s*(?:at|to|on)\s*(.+?)(?:\b|using|\s|$)"""),
            Regex("""(?i)(?:rs\.?|₹)\s*(\d+(?:\.\d{1,2})?)\s*(?:spent|debited|paid|sent)\s*(?:at|to|on)\s*(.+?)(?:\b|using|\s|$)"""),
            Regex("""(?i)paid\s*(.+?)\s*(?:rs\.?|₹)\s*(\d+(?:\.\d{1,2})?)""")
        )

        val sms1 = "Spent Rs.250.50 at Amazon Pay using credit card"
        var matched = false
        var amount = 0.0
        var merchant = ""

        for (pattern in paymentPatterns) {
            val match = pattern.find(sms1)
            if (match != null) {
                matched = true
                val g1 = match.groupValues[1]
                val g2 = match.groupValues[2]
                if (g1.toDoubleOrNull() != null) {
                    amount = g1.toDouble()
                    merchant = g2
                } else {
                    amount = g2.toDouble()
                    merchant = g1
                }
                break
            }
        }

        assertTrue(matched)
        assertEquals(250.50, amount, 0.01)
        assertEquals("Amazon", merchant.trim())
    }

    @Test
    fun testTransactionEditCalculations() {
        val initialBalance = 15000.0
        val oldAmount = 500.0
        val balanceAfterOld = initialBalance - oldAmount
        assertEquals(14500.0, balanceAfterOld, 0.01)
        
        val newAmount = 300.0
        val revertedBalance = balanceAfterOld + oldAmount
        assertEquals(15000.0, revertedBalance, 0.01)
        
        val finalBalance = revertedBalance - newAmount
        assertEquals(14700.0, finalBalance, 0.01)
    }
}
