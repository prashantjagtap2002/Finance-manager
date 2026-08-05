package com.example.financemanager.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseSeeder {

    @JvmStatic
    fun seed(dao: FinanceDao) {
        CoroutineScope(Dispatchers.IO).launch {
            // Seed Default Accounts
            val cashId = dao.insertAccount(Account(name = "Cash", type = AccountType.CASH, balance = 0.0, currency = "INR"))
            val kotakId = dao.insertAccount(Account(name = "Kotak Bank", type = AccountType.BANK, balance = 0.0, currency = "INR"))
            val tjsbId = dao.insertAccount(Account(name = "TJSB Bank", type = AccountType.BANK, balance = 0.0, currency = "INR"))
            val sbiId = dao.insertAccount(Account(name = "SBI Bank", type = AccountType.BANK, balance = 0.0, currency = "INR"))

            // Seed Default Categories (Envelopes)
            dao.insertCategory(Category(name = "Food & Dining", iconName = "restaurant", colorHex = "#4CAF50", budgetLimit = 5000.0))
            dao.insertCategory(Category(name = "Shopping", iconName = "shopping_bag", colorHex = "#2196F3", budgetLimit = 3000.0))
            dao.insertCategory(Category(name = "Utilities & Bills", iconName = "receipt_long", colorHex = "#F44336", budgetLimit = 4000.0))
            dao.insertCategory(Category(name = "Housing & Rent", iconName = "home", colorHex = "#FF9800", budgetLimit = 10000.0))
            dao.insertCategory(Category(name = "Entertainment", iconName = "movie", colorHex = "#9C27B0", budgetLimit = 2000.0))
            dao.insertCategory(Category(name = "Investments", iconName = "trending_up", colorHex = "#009688", budgetLimit = 5000.0))
            
            // Seed transactions from CSV
            try {
                val sdf = java.text.SimpleDateFormat("dd MMM yyyy, h:mm a", java.util.Locale.ENGLISH)
                val rows = SeedData.CSV_DATA.trim().split("\n")
                for (i in 1 until rows.size) {
                    val row = rows[i].trim()
                    if (row.isEmpty()) continue
                    
                    val dateStr = row.substringAfter("\"").substringBefore("\"")
                    val rest = row.substringAfter("\",").split(",")
                    if (rest.size < 4) continue
                    
                    val accName = rest[0].trim()
                    val status = rest[1].trim()
                    val amountStr = rest[2].trim().replace("Rs.", "")
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    
                    val receiver = if (rest.size > 4) rest[4].trim() else ""
                    val ref = if (rest.size > 5) rest[5].trim() else ""
                    val note = listOf(receiver, ref).filter { it.isNotEmpty() }.joinToString(" | ")
                    
                    val type = if (status.equals("credited", true)) TransactionType.INCOME else TransactionType.EXPENSE
                    
                    val accId = when {
                        accName.contains("Kotak", true) -> kotakId
                        accName.contains("TJSB", true) -> tjsbId
                        accName.contains("SBI", true) -> sbiId
                        else -> cashId
                    }
                    
                    val time = try {
                        sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
                    } catch(e: Exception) {
                        System.currentTimeMillis()
                    }
                    
                    dao.insertTransaction(
                        Transaction(
                            amount = amount,
                            type = type,
                            categoryId = 0L, // uncategorized
                            sourceAccountId = accId,
                            note = note,
                            date = time,
                            merchantName = receiver.ifEmpty { null }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
