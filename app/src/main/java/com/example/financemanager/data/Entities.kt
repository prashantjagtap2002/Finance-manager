package com.example.financemanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AccountType {
    CASH, BANK, CREDIT_CARD, WALLET
}

enum class TransactionType {
    INCOME, EXPENSE, TRANSFER
}

enum class RecurringInterval {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

enum class DebtType {
    LENT, BORROWED
}

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val balance: Double,
    val currency: String = "INR"
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String, // Compose Vector icon name key, e.g. "Shopping", "Food"
    val colorHex: String, // Hex color value, e.g. "#FF5733"
    val budgetLimit: Double = 0.0, // Envelope Budget Limit
    val isZeroBased: Boolean = false,
    val rolloverAmount: Double = 0.0,
    val isRolloverEnabled: Boolean = false,
    val displayOrder: Int = 0
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long, // 0 if transfer/income
    val sourceAccountId: Long,
    val destinationAccountId: Long? = null, // for Transfer type
    val note: String = "",
    val splitGroupId: String? = null,
    val date: Long, // Epoch millisecond timestamp
    val currency: String = "INR",
    val isRecurring: Boolean = false,
    val recurringId: Long? = null,
    val isAutoLogged: Boolean = false,
    val merchantName: String? = null,
    val originalAmount: Double? = null,
    val originalCurrency: String? = null,
    val isVerified: Boolean = true
)

@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconName: String = "savings", // Compose icon key, matches getGoalIcon()
    val colorHex: String = "#10B981",
    val targetAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: Long? = null, // Optional deadline (epoch millis)
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recurring_transactions")
data class RecurringTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val accountId: Long,
    val interval: RecurringInterval,
    val note: String = "",
    val startDate: Long,
    val nextExecutionDate: Long,
    val isAutoLog: Boolean = true,
    val isPaused: Boolean = false
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val amount: Double,
    val type: DebtType,
    val isSettled: Boolean = false,
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val paidAmount: Double = 0.0,
    val notes: String = "",
    val interestRate: Double? = null,
    val minimumPayment: Double? = null
)

@Entity(tableName = "sms_transactions")
data class SmsTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val smsHash: String,
    val sender: String,
    val body: String,
    val accountName: String = "",
    val type: String = "",
    val amount: String = "",
    val balance: String = "",
    val counterparty: String = "",
    val reference: String = "",
    val rawTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val isApproved: Boolean = false,
    val isIgnored: Boolean = false,
    val approvedCategoryId: Long = 0,
    val approvedAccountId: Long = 0
)
