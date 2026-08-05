package com.example.financemanager.data.remote

import com.example.financemanager.data.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: Long,
    val name: String,
    val type: String,
    val balance: Double,
    val currency: String,
    @SerialName("user_id") val userId: String
) {
    fun toEntity() = Account(
        id = id,
        name = name,
        type = try { AccountType.valueOf(type) } catch (e: Exception) { AccountType.BANK },
        balance = balance,
        currency = currency
    )
}

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    @SerialName("iconname") val iconName: String,
    @SerialName("colorhex") val colorHex: String,
    @SerialName("budgetlimit") val budgetLimit: Double,
    @SerialName("iszerobased") val isZeroBased: Boolean,
    @SerialName("rolloveramount") val rolloverAmount: Double,
    @SerialName("isrolloverenabled") val isRolloverEnabled: Boolean,
    @SerialName("displayorder") val displayOrder: Int,
    @SerialName("user_id") val userId: String
) {
    fun toEntity() = Category(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        budgetLimit = budgetLimit,
        isZeroBased = isZeroBased,
        rolloverAmount = rolloverAmount,
        isRolloverEnabled = isRolloverEnabled,
        displayOrder = displayOrder
    )
}

@Serializable
data class TransactionDto(
    val id: Long,
    val amount: Double,
    val type: String,
    @SerialName("categoryid") val categoryId: Long,
    @SerialName("sourceaccountid") val sourceAccountId: Long,
    @SerialName("destinationaccountid") val destinationAccountId: Long? = null,
    val note: String,
    @SerialName("splitgroupid") val splitGroupId: String? = null,
    val date: Long,
    val currency: String,
    @SerialName("isrecurring") val isRecurring: Boolean,
    @SerialName("recurringid") val recurringId: Long? = null,
    @SerialName("isautologged") val isAutoLogged: Boolean,
    @SerialName("merchantname") val merchantName: String? = null,
    @SerialName("originalamount") val originalAmount: Double? = null,
    @SerialName("originalcurrency") val originalCurrency: String? = null,
    @SerialName("user_id") val userId: String
) {
    fun toEntity() = Transaction(
        id = id,
        amount = amount,
        type = try { TransactionType.valueOf(type) } catch (e: Exception) { TransactionType.EXPENSE },
        categoryId = categoryId,
        sourceAccountId = sourceAccountId,
        destinationAccountId = destinationAccountId,
        note = note,
        splitGroupId = splitGroupId,
        date = date,
        currency = currency,
        isRecurring = isRecurring,
        recurringId = recurringId,
        isAutoLogged = isAutoLogged,
        merchantName = merchantName,
        originalAmount = originalAmount,
        originalCurrency = originalCurrency
    )
}

@Serializable
data class SavingsGoalDto(
    val id: Long,
    val name: String,
    @SerialName("iconname") val iconName: String,
    @SerialName("colorhex") val colorHex: String,
    @SerialName("targetamount") val targetAmount: Double,
    @SerialName("savedamount") val savedAmount: Double,
    @SerialName("targetdate") val targetDate: Long? = null,
    @SerialName("createdat") val createdAt: Long,
    @SerialName("user_id") val userId: String
) {
    fun toEntity() = SavingsGoal(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        targetAmount = targetAmount,
        savedAmount = savedAmount,
        targetDate = targetDate,
        createdAt = createdAt
    )
}

@Serializable
data class RecurringTransactionDto(
    val id: Long,
    val amount: Double,
    val type: String,
    @SerialName("categoryid") val categoryId: Long,
    @SerialName("accountid") val accountId: Long,
    val interval: String,
    val note: String,
    @SerialName("startdate") val startDate: Long,
    @SerialName("nextexecutiondate") val nextExecutionDate: Long,
    @SerialName("isautolog") val isAutoLog: Boolean,
    @SerialName("ispaused") val isPaused: Boolean,
    @SerialName("user_id") val userId: String
) {
    fun toEntity() = RecurringTransaction(
        id = id,
        amount = amount,
        type = try { TransactionType.valueOf(type) } catch (e: Exception) { TransactionType.EXPENSE },
        categoryId = categoryId,
        accountId = accountId,
        interval = try { RecurringInterval.valueOf(interval) } catch (e: Exception) { RecurringInterval.MONTHLY },
        note = note,
        startDate = startDate,
        nextExecutionDate = nextExecutionDate,
        isAutoLog = isAutoLog,
        isPaused = isPaused
    )
}

@Serializable
data class DebtDto(
    val id: Long,
    @SerialName("personname") val personName: String,
    val amount: Double,
    val type: String,
    @SerialName("issettled") val isSettled: Boolean,
    val date: Long,
    @SerialName("duedate") val dueDate: Long? = null,
    @SerialName("paidamount") val paidAmount: Double,
    val notes: String,
    @SerialName("interestrate") val interestRate: Double? = null,
    @SerialName("minimumpayment") val minimumPayment: Double? = null,
    @SerialName("user_id") val userId: String
) {
    fun toEntity() = Debt(
        id = id,
        personName = personName,
        amount = amount,
        type = try { DebtType.valueOf(type) } catch (e: Exception) { DebtType.BORROWED },
        isSettled = isSettled,
        date = date,
        dueDate = dueDate,
        paidAmount = paidAmount,
        notes = notes,
        interestRate = interestRate,
        minimumPayment = minimumPayment
    )
}

@Serializable
data class SmsTransactionDto(
    val id: Long,
    @SerialName("smshash") val smsHash: String,
    val sender: String,
    val body: String,
    @SerialName("accountname") val accountName: String,
    val type: String,
    val amount: String,
    val balance: String,
    val counterparty: String,
    val reference: String,
    @SerialName("rawtimestamp") val rawTimestamp: Long,
    @SerialName("createdat") val createdAt: Long,
    @SerialName("isapproved") val isApproved: Boolean,
    @SerialName("isignored") val isIgnored: Boolean,
    @SerialName("approvedcategoryid") val approvedCategoryId: Long,
    @SerialName("approvedaccountid") val approvedAccountId: Long,
    @SerialName("user_id") val userId: String
) {
    fun toEntity() = SmsTransaction(
        id = id,
        smsHash = smsHash,
        sender = sender,
        body = body,
        accountName = accountName,
        type = type,
        amount = amount,
        balance = balance,
        counterparty = counterparty,
        reference = reference,
        rawTimestamp = rawTimestamp,
        createdAt = createdAt,
        isApproved = isApproved,
        isIgnored = isIgnored,
        approvedCategoryId = approvedCategoryId,
        approvedAccountId = approvedAccountId
    )
}

