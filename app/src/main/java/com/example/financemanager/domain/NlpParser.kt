package com.example.financemanager.domain

import com.example.financemanager.data.TransactionType
import java.util.Locale

data class NlpResult(
    val amount: Double?,
    val categoryName: String?,
    val note: String,
    val transactionType: TransactionType
)

object NlpParser {

    private val amountRegex = Regex("""(?:\b|Rs\.?|₹|\$)\s*(\d+(?:\.\d{1,2})?)\b""")

    // Category keyword map
    private val categoryKeywords = mapOf(
        "Food & Dining" to listOf("food", "lunch", "dinner", "breakfast", "cafe", "restaurant", "eat", "coffee", "tea", "burger", "pizza", "grocery", "groceries", "swiggy", "zomato"),
        "Shopping" to listOf("shopping", "clothes", "shirt", "pant", "amazon", "flipkart", "shoes", "buy", "mall", "purchase"),
        "Utilities & Bills" to listOf("bill", "utility", "electric", "electricity", "power", "water", "gas", "recharge", "phone", "internet", "wifi"),
        "Housing & Rent" to listOf("rent", "maintenance", "flat", "room", "apartment", "house", "broker"),
        "Entertainment" to listOf("movie", "cinema", "netflix", "spotify", "subscription", "game", "gaming", "play", "party", "fun", "club"),
        "Investments" to listOf("investment", "invest", "stock", "mutual fund", "sip", "crypto", "gold", "share")
    )

    private val incomeKeywords = listOf("salary", "income", "freelance", "interest", "bonus", "dividend", "refund", "receive", "received")

    fun parse(input: String): NlpResult {
        val cleanInput = input.trim()
        if (cleanInput.isEmpty()) {
            return NlpResult(null, null, "", TransactionType.EXPENSE)
        }

        val lowercaseInput = cleanInput.lowercase(Locale.ROOT)

        // 1. Extract Amount
        val amountMatch = amountRegex.find(cleanInput)
        val amount = amountMatch?.groupValues?.get(1)?.toDoubleOrNull()

        // 2. Determine Transaction Type (Expense vs Income)
        var transactionType = TransactionType.EXPENSE
        for (keyword in incomeKeywords) {
            if (lowercaseInput.contains(keyword)) {
                transactionType = TransactionType.INCOME
                break
            }
        }

        // 3. Match Category
        var matchedCategory: String? = null
        for ((category, keywords) in categoryKeywords) {
            for (keyword in keywords) {
                if (lowercaseInput.contains(keyword)) {
                    matchedCategory = category
                    break
                }
            }
            if (matchedCategory != null) break
        }

        // 4. Construct Note
        // Remove the matched amount from the text to form the note
        var note = cleanInput
        amountMatch?.let {
            note = note.replace(it.value, "").trim()
        }

        // Clean up double spaces
        note = note.replace(Regex("""\s+"""), " ").trim()

        // If the note becomes empty, use a default description
        if (note.isEmpty()) {
            note = when (transactionType) {
                TransactionType.INCOME -> "Income"
                TransactionType.EXPENSE -> "Expense"
                TransactionType.TRANSFER -> "Transfer"
            }
        } else {
            // Capitalize first letter of note for professional look
            note = note.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        return NlpResult(
            amount = amount,
            categoryName = matchedCategory,
            note = note,
            transactionType = transactionType
        )
    }
}
