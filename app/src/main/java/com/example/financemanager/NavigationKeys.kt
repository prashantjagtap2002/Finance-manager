package com.example.financemanager

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Dashboard : NavKey
@Serializable data object QuickEntry : NavKey
@Serializable data object Budget : NavKey
@Serializable data object Goals : NavKey
@Serializable data object Insights : NavKey
@Serializable data object CustomInsight : NavKey
@Serializable data object Settings : NavKey
@Serializable data object TransactionLogs : NavKey
@Serializable data class AccountTransactions(val accountId: Long) : NavKey
@Serializable data object Subscriptions : NavKey
@Serializable data class CategoryDetails(val categoryId: Long, val year: String, val month: String) : NavKey
@Serializable data object Debt : NavKey
@Serializable data object NetWorth : NavKey
@Serializable data object Search : NavKey
@Serializable data object SmsTransactions : NavKey
