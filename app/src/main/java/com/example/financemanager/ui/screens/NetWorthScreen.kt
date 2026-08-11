package com.example.financemanager.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.data.AccountType
import com.example.financemanager.data.DebtType
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSCardStyle
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlin.math.roundToInt

enum class NetWorthPeriodFilter(val label: String, val monthsCount: Int) {
    ONE_MONTH("1M", 1),
    THREE_MONTHS("3M", 3),
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    ALL("ALL", 999)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val accounts by viewModel.accounts.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val netWorthHistory by viewModel.netWorthHistory.collectAsState()
    val monthlySpent by viewModel.monthlyExpenseTotal.collectAsState()

    var selectedFilter by remember { mutableStateOf(NetWorthPeriodFilter.SIX_MONTHS) }

    // Calculate Assets vs Liabilities
    val assetAccounts = accounts.filter { it.balance >= 0 }
    val liabilityAccounts = accounts.filter { it.balance < 0 }
    val totalAssets = assetAccounts.sumOf { it.balance }
    
    val totalBorrowedDebts = debts.filter { it.type == DebtType.BORROWED && !it.isSettled }.sumOf { it.amount - it.paidAmount }
    val totalCreditCardDebt = liabilityAccounts.sumOf { -it.balance }
    val totalLiabilities = totalBorrowedDebts + totalCreditCardDebt

    val currentNetWorth = totalAssets - totalLiabilities

    // Filter net worth history map based on selected filter
    val filteredHistory = remember(netWorthHistory, selectedFilter) {
        val list = netWorthHistory.entries.toList()
        if (selectedFilter == NetWorthPeriodFilter.ALL || list.size <= selectedFilter.monthsCount) {
            netWorthHistory
        } else {
            list.takeLast(selectedFilter.monthsCount).associate { it.key to it.value }
        }
    }

    val historyValues = filteredHistory.values.toList()
    val previousNetWorth = if (historyValues.size > 1) historyValues.first() else currentNetWorth
    val diff = currentNetWorth - previousNetWorth
    val percentageDiff = if (previousNetWorth != 0.0) (diff / kotlin.math.abs(previousNetWorth)) * 100 else 0.0
    val isPositive = diff >= 0

    Scaffold(
        containerColor = DeepBackground,
        topBar = {
            TopAppBar(
                title = { Text("Net Worth & Wealth", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Total Net Worth Hero Card
            item {
                iOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = iOSCardStyle.Grouped
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Total Net Worth",
                            style = Typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Medium)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            moneyString(currentNetWorth, false, decimals = 2),
                            style = Typography.displayMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Change Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background((if (isPositive) AccentGreen else AlertRed).copy(alpha = 0.12f))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                                contentDescription = null,
                                tint = if (isPositive) AccentGreen else AlertRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${if (isPositive) "+" else ""}${moneyString(diff, false)} (${String.format("%.1f", percentageDiff)}%) ${selectedFilter.label}",
                                style = Typography.labelSmall.copy(
                                    color = if (isPositive) AccentGreen else AlertRed,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Metric Strip (Current Spend vs Assets vs Liabilities)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Monthly Spend", style = Typography.labelSmall.copy(color = TextMuted), textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(moneyString(monthlySpent, false), style = Typography.titleSmall.copy(color = AlertRed, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                            }
                            VerticalDivider(modifier = Modifier.height(28.dp), color = BorderColor.copy(alpha = 0.25f))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Total Assets", style = Typography.labelSmall.copy(color = TextMuted), textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(moneyString(totalAssets, false), style = Typography.titleSmall.copy(color = AccentGreen, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                            }
                            VerticalDivider(modifier = Modifier.height(28.dp), color = BorderColor.copy(alpha = 0.25f))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Liabilities", style = Typography.labelSmall.copy(color = TextMuted), textAlign = TextAlign.Center)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(moneyString(totalLiabilities, false), style = Typography.titleSmall.copy(color = TextSecondary, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // 2. Net Worth Trend Line Chart with Dynamic Period Filter Pills
            item {
                iOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = iOSCardStyle.Grouped
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Net Worth Trajectory", style = Typography.titleMedium.copy(color = TextPrimary))

                            // Filter Pills
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                NetWorthPeriodFilter.values().forEach { filter ->
                                    val isSelected = selectedFilter == filter
                                    val pillBg by animateColorAsState(
                                        targetValue = if (isSelected) PrimaryViolet else SubtleSurface,
                                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                                        label = "nw_pill_bg"
                                    )
                                    val pillText by animateColorAsState(
                                        targetValue = if (isSelected) OnAccent else TextSecondary,
                                        label = "nw_pill_text"
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(pillBg)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedFilter = filter
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            filter.label,
                                            style = Typography.labelSmall.copy(color = pillText, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (filteredHistory.isEmpty()) {
                            Text("No history data available for this range.", color = TextMuted)
                        } else {
                            NetWorthLineChart(
                                data = filteredHistory,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }
            }

            // 3. Assets vs Liabilities Ratio Bar & Allocation Breakdown
            item {
                iOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = iOSCardStyle.Grouped
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Assets vs. Liabilities", style = Typography.titleMedium.copy(color = TextPrimary))
                        Spacer(modifier = Modifier.height(12.dp))

                        val totalCombined = maxOf(1.0, totalAssets + totalLiabilities)
                        val assetRatio = (totalAssets / totalCombined).toFloat().coerceIn(0.05f, 1f)
                        val liabilityRatio = (totalLiabilities / totalCombined).toFloat().coerceIn(0f, 0.95f)

                        // Dual Bar Progress
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(BorderColor.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(assetRatio)
                                    .background(AccentGreen)
                            )
                            if (liabilityRatio > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .weight(liabilityRatio)
                                        .background(AlertRed)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AccentGreen))
                                Text(
                                    "Assets (${String.format("%.0f", (totalAssets / totalCombined) * 100)}%)",
                                    style = Typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(AlertRed))
                                Text(
                                    "Liabilities (${String.format("%.0f", (totalLiabilities / totalCombined) * 100)}%)",
                                    style = Typography.labelSmall.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Detailed Accounts Allocation List
            item {
                iOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = iOSCardStyle.Grouped
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Account Allocation", style = Typography.titleMedium.copy(color = TextPrimary))
                        Spacer(modifier = Modifier.height(12.dp))

                        if (accounts.isEmpty()) {
                            Text("No bank accounts added yet.", style = Typography.bodyMedium.copy(color = TextMuted))
                        } else {
                            accounts.forEachIndexed { index, acc ->
                                if (index > 0) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = BorderColor.copy(alpha = 0.15f))
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (acc.balance >= 0) AccentGreen.copy(alpha = 0.12f)
                                                    else AlertRed.copy(alpha = 0.12f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = when (acc.type) {
                                                    AccountType.BANK -> Icons.Default.AccountBalance
                                                    AccountType.CASH -> Icons.Default.Payments
                                                    AccountType.CREDIT_CARD -> Icons.Default.CreditCard
                                                    AccountType.WALLET -> Icons.Default.AccountBalanceWallet
                                                },
                                                contentDescription = null,
                                                tint = if (acc.balance >= 0) AccentGreen else AlertRed,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                acc.name,
                                                style = Typography.bodyMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(acc.type.name, style = Typography.labelSmall.copy(color = TextMuted))
                                        }
                                    }
                                    Text(
                                        moneyString(acc.balance, false),
                                        style = Typography.bodyMedium.copy(
                                            color = if (acc.balance >= 0) TextPrimary else AlertRed,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Financial Health Milestone Progress
            item {
                iOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = iOSCardStyle.Grouped
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                            Text("Wealth Milestones", style = Typography.titleMedium.copy(color = TextPrimary))
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val milestoneTarget = when {
                            currentNetWorth < 100000 -> 100000.0
                            currentNetWorth < 500000 -> 500000.0
                            currentNetWorth < 1000000 -> 1000000.0
                            else -> 5000000.0
                        }
                        val milestoneProgress = (currentNetWorth / milestoneTarget).toFloat().coerceIn(0f, 1f)

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Next Target: ${moneyString(milestoneTarget, false, decimals = 0)}",
                                    style = Typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    "${String.format("%.1f", milestoneProgress * 100)}%",
                                    style = Typography.labelMedium.copy(color = PrimaryViolet, fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { milestoneProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = PrimaryViolet,
                                trackColor = BorderColor.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetWorthLineChart(
    data: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    val entries = data.entries.toList()
    val maxVal = entries.maxOfOrNull { it.value } ?: 0.0
    val minVal = entries.minOfOrNull { it.value } ?: 0.0
    
    val range = (maxVal - minVal).let { if (it == 0.0) 1000.0 else it }
    val yMax = maxVal + (range * 0.1)
    val yMin = minVal - (range * 0.1)
    val yRange = yMax - yMin

    var touchedIndex by remember { mutableStateOf<Int?>(null) }
    var chartWidth by remember { mutableFloatStateOf(0f) }

    val textMutedColor = TextMuted
    val primaryVioletColor = PrimaryViolet
    val deepBackgroundColor = DeepBackground
    val accentGreenColor = AccentGreen

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(entries) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (chartWidth > 0) {
                                    val stepX = chartWidth / (entries.size - 1).coerceAtLeast(1)
                                    touchedIndex = (offset.x / stepX).roundToInt().coerceIn(0, entries.size - 1)
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (chartWidth > 0) {
                                    val stepX = chartWidth / (entries.size - 1).coerceAtLeast(1)
                                    touchedIndex = (change.position.x / stepX).roundToInt().coerceIn(0, entries.size - 1)
                                }
                            },
                            onDragEnd = { touchedIndex = null },
                            onDragCancel = { touchedIndex = null }
                        )
                    }
                    .pointerInput(entries) {
                        detectTapGestures(
                            onPress = { offset ->
                                if (chartWidth > 0) {
                                    val stepX = chartWidth / (entries.size - 1).coerceAtLeast(1)
                                    touchedIndex = (offset.x / stepX).roundToInt().coerceIn(0, entries.size - 1)
                                    tryAwaitRelease()
                                    touchedIndex = null
                                }
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height
                chartWidth = width

                val stepX = width / (entries.size - 1).coerceAtLeast(1)
                
                if (yMin < 0 && yMax > 0) {
                    val yZero = height - ((0 - yMin) / yRange * height).toFloat()
                    drawLine(
                        color = textMutedColor.copy(alpha = 0.5f),
                        start = Offset(0f, yZero),
                        end = Offset(width, yZero),
                        strokeWidth = 2f
                    )
                }

                val path = Path()
                val points = mutableListOf<Offset>()

                entries.forEachIndexed { index, entry ->
                    val x = index * stepX
                    val y = height - ((entry.value - yMin) / yRange * height).toFloat()
                    
                    points.add(Offset(x, y))
                    
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryVioletColor.copy(alpha = 0.35f), primaryVioletColor.copy(alpha = 0.0f))
                    )
                )

                drawPath(
                    path = path,
                    color = primaryVioletColor,
                    style = Stroke(width = 6f)
                )

                points.forEachIndexed { index, point ->
                    val isTouched = index == touchedIndex
                    val dotRadius = if (isTouched) 12f else 8f
                    val strokeWidth = if (isTouched) 6f else 4f
                    
                    if (isTouched) {
                        drawLine(
                            color = primaryVioletColor.copy(alpha = 0.5f),
                            start = Offset(point.x, 0f),
                            end = Offset(point.x, height),
                            strokeWidth = 4f
                        )
                    }
                    
                    drawCircle(
                        color = deepBackgroundColor,
                        radius = dotRadius,
                        center = point
                    )
                    drawCircle(
                        color = if (isTouched) accentGreenColor else primaryVioletColor,
                        radius = dotRadius,
                        center = point,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
            
            touchedIndex?.let { index ->
                val entry = entries[index]
                val stepX = if (chartWidth > 0) chartWidth / (entries.size - 1).coerceAtLeast(1) else 0f
                val xPos = index * stepX
                
                val alignEnd = xPos > chartWidth / 2
                
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier
                            .align(if (alignEnd) Alignment.TopEnd else Alignment.TopStart)
                            .padding(
                                start = if (alignEnd) 0.dp else 16.dp,
                                end = if (alignEnd) 16.dp else 0.dp,
                                top = 8.dp
                            ),
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurface.copy(alpha = 0.95f),
                        shadowElevation = 4.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = entry.key,
                                style = Typography.labelMedium.copy(color = TextSecondary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = moneyString(entry.value, false),
                                style = Typography.titleMedium.copy(
                                    color = if (entry.value >= 0) AccentGreen else AlertRed,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            entries.forEach { entry ->
                Text(
                    text = entry.key.take(3),
                    style = Typography.labelSmall.copy(color = TextSecondary),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
