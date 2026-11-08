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
    val openingBalance: Double = 0.0,
    /**
     * When this row last changed, in epoch millis. The cloud merge compares it against the
     * server's copy to decide which side is newer — without it a sync could only ever add rows it
     * had never seen, so an edit made on one device never reached another.
     *
     * Stamped by [com.example.financemanager.domain.FinanceRepository] and
     * [com.example.financemanager.domain.AccountLedger], never by the DAO: a row arriving from the
     * cloud has to keep the timestamp it was written with, or it would look locally edited the
     * moment it landed and be pushed straight back.
     */
    val updatedAt: Long = System.currentTimeMillis()
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
    val displayOrder: Int = 0,
    /** When this row last changed; see [Account.updatedAt]. */
    val updatedAt: Long = System.currentTimeMillis()
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
    val isVerified: Boolean = true,
    /** When this row last changed; see [Account.updatedAt]. */
    val updatedAt: Long = System.currentTimeMillis()
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
    val createdAt: Long = System.currentTimeMillis(),
    /** When this row last changed; see [Account.updatedAt]. */
    val updatedAt: Long = System.currentTimeMillis()
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
    val isPaused: Boolean = false,
    /** When this row last changed; see [Account.updatedAt]. */
    val updatedAt: Long = System.currentTimeMillis()
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
    val minimumPayment: Double? = null,
    /** When this row last changed; see [Account.updatedAt]. */
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * The ledger row that moved the account when this IOU was opened from Quick Transaction, or 0
     * for one that moved no money (added on the Debt screen, or from before the link existed).
     * Deleting an unpaid IOU revokes this row, so an IOU made by mistake gives its money back.
     * Local only: the cloud schema has no such column, and a pulled row keeps the local value.
     */
    val ledgerTransactionId: Long = 0
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
    val resolution: String = "",
    /** When this row last changed; see [Account.updatedAt]. */
    val updatedAt: Long = System.currentTimeMillis()
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
    val isSip: Boolean = false,
    /**
     * The ledger row this trade's cash leg created, or 0 for trades logged before the link
     * existed.
     *
     * A buy takes money out of an account and a sell puts it back, recorded as an ordinary
     * transfer so the balance and the ledger keep agreeing. Without knowing which row that was,
     * editing or deleting the trade could only post a fresh offsetting adjustment and hope the
     * amounts still matched — and editing did not even do that, so a corrected quantity left the
     * account permanently wrong.
     */
    val ledgerTransactionId: Long = 0
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

/**
 * A row this device deleted, remembered so the cloud merge can tell "deleted here" apart from
 * "created on another device and not pulled yet".
 *
 * Without it the merge had to read a missing row as a deletion, which is only true when this
 * device has already seen everything the server holds. The moment a second device adds a
 * transaction, the first one sees a server row it has never had and — under that reading — deletes
 * the user's data from the cloud. A tombstone makes the deletion an explicit fact instead of an
 * inference, so the only thing an unknown row can do now is arrive.
 *
 * Tombstones are written for the seven tables cloud sync covers, on the paths where the *user*
 * deleted something. Bulk replacements (a backup restore, a full wipe) deliberately write none:
 * those are "this is my data now" rather than "delete that", and turning them into cloud deletions
 * is exactly the irreversible move this table exists to prevent.
 *
 * [rowId] is the primary key of the deleted row, and [tableName] the cloud table it belonged to.
 * They are a composite key so re-deleting the same id is idempotent.
 */
@Entity(tableName = "deleted_records", primaryKeys = ["tableName", "rowId"])
data class DeletedRecord(
    val tableName: String,
    val rowId: Long,
    val deletedAt: Long = System.currentTimeMillis()
)

/** The cloud tables a [DeletedRecord] can name — the seven that sync. */
object SyncTables {
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val TRANSACTIONS = "transactions"
    const val SAVINGS_GOALS = "savings_goals"
    const val RECURRING_TRANSACTIONS = "recurring_transactions"
    const val DEBTS = "debts"
    const val SMS_TRANSACTIONS = "sms_transactions"
}

/**
 * A photo of the paper receipt behind a transaction. The image itself lives in the app's private
 * files directory under [fileName]; this row only says which transaction it belongs to.
 *
 * Local only — receipts are not part of cloud sync or the JSON backup, and a receipt whose
 * transaction no longer exists is cleaned up by [com.example.financemanager.core.ReceiptStore].
 * Deleting the transaction does not remove it straight away, so the delete's Undo can bring the
 * photo back along with the row.
 */
@Entity(tableName = "receipts", indices = [Index(value = ["transactionId"])])
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis()
)
