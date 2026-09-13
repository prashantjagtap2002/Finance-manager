package com.example.financemanager.data

import androidx.room.Entity
import androidx.room.Index
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

enum class InvestmentType {
    STOCK, MUTUAL_FUND
}

enum class InvestmentTxnType {
    BUY, SELL, SIP_INSTALLMENT
}

@Entity(tableName = "expense_groups")
data class ExpenseGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currency: String = "INR",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expense_group_members", indices = [Index(value = ["groupId"])])
data class ExpenseGroupMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val upiId: String = "",
    val isCurrentUser: Boolean = false
)

@Entity(tableName = "expense_group_expenses", indices = [Index(value = ["groupId"])])
data class ExpenseGroupExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val description: String,
    val amount: Double,
    val paidByMemberId: Long,
    /** Comma-separated member ids. A receipt can be split among any subset of the group. */
    val participantMemberIds: String,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val balance: Double,
    val currency: String = "INR",
    /**
     * What the account held before any recorded transaction. Together with the transactions it
     * defines the ledger invariant `balance == openingBalance + net effect of transactions`, which
     * [com.example.financemanager.domain.AccountLedger] maintains and can re-derive to repair
     * drift left by older versions.
     */
    val openingBalance: Double = 0.0
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

@Entity(
    tableName = "transactions",
    // Every list and aggregate query filters or orders on these, and the table is the one that
    // grows without bound. Without them SQLite scans the whole table for each.
    indices = [
        Index(value = ["date"]),
        Index(value = ["categoryId"]),
        Index(value = ["sourceAccountId"]),
        Index(value = ["destinationAccountId"])
    ]
)
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

@Entity(tableName = "debts", indices = [Index(value = ["date"])])
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

@Entity(
    tableName = "sms_transactions",
    indices = [
        Index(value = ["smsHash"], unique = true),
        // Every inbox list orders by this, and a historical XML import can leave thousands of rows.
        Index(value = ["rawTimestamp"])
    ]
)
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
    val approvedAccountId: Long = 0,
    /**
     * The transaction this alert was logged as, or 0 when it hasn't been approved (or was
     * approved by a version that didn't record the link). Without it, undoing an approval — which
     * is what resolving a refund does — has to guess which row to remove.
     */
    val loggedTransactionId: Long = 0,
    /** The other leg of a self transfer or refund, once the pair has been resolved together. */
    val linkedSmsId: Long = 0,
    /** How the pair was resolved: `""`, `"TRANSFER"` or `"REVERSAL"`. */
    val resolution: String = ""
)

@Entity(tableName = "investments")
data class Investment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val symbol: String? = null, // Ticker for live price lookup, e.g. "TCS", "RELIANCE"
    val exchange: String? = null, // "NSE" or "BSE"
    val type: InvestmentType,
    val currentPrice: Double = 0.0, // Last known price/NAV per unit, cached for offline display
    val lastPriceUpdate: Long = 0,
    val currency: String = "INR",
    val notes: String = ""
)

@Entity(tableName = "investment_transactions", indices = [Index(value = ["investmentId"])])
data class InvestmentTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val investmentId: Long,
    val type: InvestmentTxnType,
    val quantity: Double,
    val pricePerUnit: Double,
    val amount: Double, // quantity * pricePerUnit, stored to avoid recomputation/rounding drift
    val date: Long,
    val sourceAccountId: Long,
    val isSip: Boolean = false
)

/**
 * Remembers which category the user filed a merchant under, so the next alert from that merchant
 * comes pre-categorised instead of asking again.
 *
 * The key is [com.example.financemanager.domain.MerchantKey.normalize]d, and [accountId] carries
 * the account the user picked alongside it (0 when they haven't settled on one).
 */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
    val accountId: Long = 0,
    val hitCount: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)
