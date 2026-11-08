package com.example.financemanager

import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import com.example.financemanager.theme.LocalThemeIsDark
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.filled.Sms
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
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

// Bottom navigation destinations.
private val bottomTabs = listOf(
    BottomTab(Dashboard, "Overview", Icons.Outlined.Home, Icons.Filled.Home),
    BottomTab(Budget, "Budget", Icons.Outlined.AccountBalance, Icons.Filled.AccountBalance),
    BottomTab(Goals, "Goals", Icons.Outlined.Savings, Icons.Filled.Savings),
    BottomTab(Insights, "Insights", Icons.Outlined.PieChart, Icons.Filled.PieChart),
    BottomTab(SmsTransactions, "SMS", Icons.Outlined.Sms, Icons.Filled.Sms)
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
    val tabItems = remember { bottomTabs.map { IosTabItem(it.icon, it.label, it.selectedIcon) } }

    // The screens are recorded into this layer as they draw, and the tab bar draws the slice that
    // lies beneath it, blurred. Recording costs nothing extra: the layer is what goes to screen.
    val backdrop = rememberGraphicsLayer()
    val density = LocalDensity.current
    var tabBarHeight by remember { mutableStateOf(0.dp) }

    // The tab bar floats over the screens instead of shortening them, so content runs on beneath
    // the translucent material. Top-level screens read the bar's height to keep their last row
    // and their FAB clear of it; pushed screens see zero, since the bar slides away for them.
    Box(
        Modifier
            .fillMaxSize()
            .background(if (LocalThemeIsDark.current) iOSBackgroundDark else iOSBackgroundLight)
    ) {
        CompositionLocalProvider(LocalTabBarHeight provides if (isTopLevel) tabBarHeight else 0.dp) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    },
                transitionSpec = {
                    // Root replaced by another root: that only happens when a tab is tapped. Tabs are
                    // siblings, not a drill-down, so they swap with a short crossfade instead of the
                    // push slide: half the duration, and no full-width layer travelling across the
                    // screen, which is what made hopping between tabs feel heavy.
                    if (initialState.previousEntries.isEmpty() && targetState.previousEntries.isEmpty()) {
                        return@NavDisplay fadeIn(tween(110)) togetherWith fadeOut(tween(70))
                    }
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
                            onNavigateToSettings = { backStack.add(Settings) },
                            onNavigateToLogs = { backStack.add(TransactionLogs) },
                            onNavigateToAccountTransactions = { accountId ->
                                backStack.add(AccountTransactions(accountId))
                            },
                            onNavigateToSubscriptions = { backStack.add(Subscriptions) },
                            onNavigateToSearch = { backStack.add(Search) },
                            onNavigateToGoals = { backStack.add(Goals) },
                            onNavigateToDebt = { backStack.add(Debt) },
                            onNavigateToNetWorth = { backStack.add(NetWorth) },
                            onNavigateToSmsTransactions = { backStack.add(SmsTransactions) },
                            onNavigateToCategory = { categoryId, year, month ->
                                backStack.add(CategoryDetails(categoryId, year.toString(), month.toString()))
                            },
                            onNavigateToSmsReview = { backStack.add(SmsReview) },
                            onNavigateToDuplicates = { backStack.add(Duplicates) },
                            onNavigateToCalendar = { backStack.add(SpendingCalendar) }
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
                            onNavigateToCalendar = { backStack.add(SpendingCalendar) },
                            onNavigateToCategory = { categoryId, year, month ->
                                backStack.add(CategoryDetails(categoryId, year, month))
                            }
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
                            onNavigateToSmsTransactions = { backStack.add(SmsTransactions) },
                            onNavigateToSmsRules = { backStack.add(SmsRules) }
                        )
                    }
                    entry<SmsRules> {
                        com.example.financemanager.ui.screens.SmsRulesScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<TransactionLogs> {
                        TransactionLogsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<AccountTransactions> {
                        TransactionLogsScreen(
                            viewModel = viewModel,
                            initialAccountId = it.accountId,
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
                            onNavigateBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                            onNavigateToReview = { backStack.add(SmsReview) }
                        )
                    }
                    entry<SmsReview> {
                        com.example.financemanager.ui.screens.SmsReviewScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<SpendingCalendar> {
                        com.example.financemanager.ui.screens.SpendingCalendarScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<Duplicates> {
                        com.example.financemanager.ui.screens.DuplicatesScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                }
            )
        }

        // The bar leaves along the path it arrived on: down out of the bottom edge, and back up.
        AnimatedVisibility(
            visible = isTopLevel,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            IosTabBar(
                selectedTab = selectedTabIndex,
                onTabSelected = { index ->
                    val tab = bottomTabs[index]
                    if (currentKey != tab.key) {
                        // Reset the stack to the chosen root tab
                        backStack.clear()
                        backStack.add(tab.key)
                    }
                },
                tabs = tabItems,
                backdrop = backdrop,
                modifier = Modifier.onSizeChanged { tabBarHeight = with(density) { it.height.toDp() } }
            )
        }
    }
}
