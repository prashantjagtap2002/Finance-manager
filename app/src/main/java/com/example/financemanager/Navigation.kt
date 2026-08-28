package com.example.financemanager

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import com.example.financemanager.theme.LocalThemeIsDark
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.*
import com.example.financemanager.ui.screens.*
import com.example.financemanager.ui.viewmodel.FinanceViewModel

private data class BottomTab(
    val key: NavKey,
    val label: String,
    val icon: ImageVector
)

// Bottom navigation destinations.
private val bottomTabs = listOf(
    BottomTab(Dashboard, "Home", Icons.Outlined.Home),
    BottomTab(Budget, "Budget", Icons.Outlined.AccountBalance),
    BottomTab(Goals, "Goals", Icons.Outlined.Savings),
    BottomTab(Insights, "Insights", Icons.Outlined.PieChart),
    BottomTab(SmsTransactions, "SMS", Icons.Outlined.Sms)
)

@Composable
fun MainNavigation(
    viewModel: FinanceViewModel,
    openQuickEntry: Boolean = false
) {
    val backStack = rememberNavBackStack(Dashboard)

    // Handle widget "+ Quick Log" shortcut click
    LaunchedEffect(openQuickEntry) {
        if (openQuickEntry) {
            backStack.add(QuickEntry)
        }
    }

    val currentKey = backStack.lastOrNull()
    val isTopLevel = bottomTabs.any { it.key == currentKey }
    val selectedTabIndex = bottomTabs.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
    val emphasized = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
    val tabItems = remember { bottomTabs.map { iOSTabItem(it.icon, it.label) } }

    Scaffold(
        containerColor = if (LocalThemeIsDark.current) iOSBackgroundDark else iOSBackgroundLight,
        bottomBar = {
            AnimatedVisibility(
                visible = isTopLevel,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                // Compact bottom navigation shared by top-level destinations.
                iOSTabBar(
                    selectedTab = selectedTabIndex,
                    onTabSelected = { index ->
                        val tab = bottomTabs[index]
                        if (currentKey != tab.key) {
                            // Reset the stack to the chosen root tab
                            backStack.clear()
                            backStack.add(tab.key)
                        }
                    },
                        tabs = tabItems
                )
            }
        }
    ) { paddingValues ->
        // Short transitions leave more frame budget for the destination screen.
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding()),
            transitionSpec = {
                (slideInHorizontally(tween(220, easing = emphasized)) { it / 4 } +
                    fadeIn(tween(160, easing = emphasized))) togetherWith
                    (slideOutHorizontally(tween(220, easing = emphasized)) { -it / 8 } +
                        fadeOut(tween(100)))
            },
            popTransitionSpec = {
                (slideInHorizontally(tween(220, easing = emphasized)) { -it / 8 } +
                    fadeIn(tween(160, easing = emphasized))) togetherWith
                    (slideOutHorizontally(tween(220, easing = emphasized)) { it / 4 } +
                        fadeOut(tween(100)))
            },
            predictivePopTransitionSpec = {
                (slideInHorizontally(tween(220, easing = emphasized)) { -it / 8 } +
                    fadeIn(tween(160, easing = emphasized))) togetherWith
                    (slideOutHorizontally(tween(220, easing = emphasized)) { it / 4 } +
                        fadeOut(tween(100)))
            },
            entryProvider = entryProvider {
                entry<Dashboard> {
                    DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToQuickEntry = { backStack.add(QuickEntry) },
                        onNavigateToBudget = { backStack.add(Budget) },
                        onNavigateToInsights = { backStack.add(Insights) },
                        onNavigateToFinancialTools = { backStack.add(FinancialTools) },
                        onNavigateToSettings = { backStack.add(Settings) },
                        onNavigateToLogs = { backStack.add(TransactionLogs) },
                        onNavigateToSubscriptions = { backStack.add(Subscriptions) },
                        onNavigateToSearch = { backStack.add(Search) },
                        onNavigateToGoals = { backStack.add(Goals) },
                        onNavigateToDebt = { backStack.add(Debt) },
                        onNavigateToNetWorth = { backStack.add(NetWorth) },
                        onNavigateToSmsTransactions = { backStack.add(SmsTransactions) },
                        onNavigateToCategory = { categoryId, year, month ->
                            backStack.add(CategoryDetails(categoryId, year.toString(), month.toString()))
                        }
                    )
                }
                entry<QuickEntry> {
                    QuickEntryScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Budget> {
                    BudgetScreen(
                        viewModel = viewModel,
                        showBackButton = backStack.size > 1,
                        onNavigateBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        onNavigateToCategory = { categoryId, year, month ->
                            backStack.add(CategoryDetails(categoryId, year, month))
                        }
                    )
                }
                entry<Goals> {
                    GoalsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() },
                        showBackButton = false
                    )
                }
                entry<Insights> {
                    InsightsScreen(
                        viewModel = viewModel,
                        showBackButton = backStack.size > 1,
                        onNavigateBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        onNavigateToCustomInsight = { backStack.add(CustomInsight) },
                        onNavigateToCategory = { categoryId, year, month ->
                            backStack.add(CategoryDetails(categoryId, year, month))
                        }
                    )
                }
                entry<FinancialTools> {
                    FinancialToolsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<CustomInsight> {
                    CustomInsightScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onNavigateToSubscriptions = { backStack.add(Subscriptions) },
                        onNavigateToSmsTransactions = { backStack.add(SmsTransactions) }
                    )
                }
                entry<TransactionLogs> {
                    TransactionLogsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Subscriptions> {
                    SubscriptionsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Search> {
                    com.example.financemanager.ui.screens.SearchScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<CategoryDetails> {
                    val details = it
                    TransactionsByCategoryScreen(
                        categoryId = details.categoryId,
                        year = details.year,
                        month = details.month,
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<Debt> {
                    DebtScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<NetWorth> {
                    NetWorthScreen(
                        viewModel = viewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
                entry<SmsTransactions> {
                    com.example.financemanager.ui.screens.SmsTransactionsScreen(
                        viewModel = viewModel,
                        showBackButton = backStack.size > 1,
                        onNavigateBack = { if (backStack.size > 1) backStack.removeLastOrNull() }
                    )
                }
            }
        )
    }
}
