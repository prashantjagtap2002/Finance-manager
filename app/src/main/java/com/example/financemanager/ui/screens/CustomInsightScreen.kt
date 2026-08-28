package com.example.financemanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.example.financemanager.data.*
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSTopAppBar
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

private enum class VisualType(val label: String) { BAR("Bar chart"), LINE("Line chart"), PIE("Pie chart"), TABLE("Table") }
private enum class InsightMetric(val label: String) { SPENDING("Spending"), INCOME("Income"), NET("Net cash flow") }
private enum class InsightPeriod(val label: String, val months: Int) { THREE("3 months", 3), SIX("6 months", 6), TWELVE("12 months", 12) }
private data class InsightPoint(val label: String, val value: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomInsightScreen(viewModel: FinanceViewModel, onNavigateBack: () -> Unit) {
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val privacy by viewModel.isPrivacyMode.collectAsState()
    var visual by remember { mutableStateOf(VisualType.BAR) }
    var metric by remember { mutableStateOf(InsightMetric.SPENDING) }
    var period by remember { mutableStateOf(InsightPeriod.SIX) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var openMenu by remember { mutableStateOf<String?>(null) }

    val points = remember(transactions, metric, period, categoryId) {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        (period.months - 1 downTo 0).map { offset ->
            calendar.timeInMillis = now
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.add(Calendar.MONTH, -offset)
            val start = calendar.timeInMillis
            calendar.add(Calendar.MONTH, 1)
            val end = calendar.timeInMillis
            val matching = transactions.filter { it.date >= start && it.date < end && (categoryId == null || it.categoryId == categoryId) }
            val value = when (metric) {
                InsightMetric.SPENDING -> matching.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                InsightMetric.INCOME -> matching.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                InsightMetric.NET -> matching.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } - matching.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            }
            calendar.add(Calendar.MONTH, -1)
            InsightPoint(SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time), value)
        }
    }

    Scaffold(
        topBar = { iOSTopAppBar("Create Insight", navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) },
        containerColor = DeepBackground
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Text("Choose a view", color = TextSecondary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VisualType.entries.forEach { type -> FilterChip(selected = visual == type, onClick = { visual = type }, label = { Text(type.label) }) }
                }
            }
            item {
                iOSCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        InsightDropdown("Metric", metric.label, openMenu == "metric", { openMenu = if (openMenu == "metric") null else "metric" }) {
                            InsightMetric.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { metric = option; openMenu = null }) }
                        }
                        InsightDropdown("Period", period.label, openMenu == "period", { openMenu = if (openMenu == "period") null else "period" }) {
                            InsightPeriod.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { period = option; openMenu = null }) }
                        }
                        InsightDropdown("Category", categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name } ?: "All categories", openMenu == "category", { openMenu = if (openMenu == "category") null else "category" }) {
                            DropdownMenuItem(text = { Text("All categories") }, onClick = { categoryId = null; openMenu = null })
                            categories.forEach { category -> DropdownMenuItem(text = { Text(category.name) }, onClick = { categoryId = category.id; openMenu = null }) }
                        }
                    }
                }
            }
            item {
                iOSCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${metric.label} • ${period.label}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        when (visual) {
                            VisualType.TABLE -> InsightTable(points, privacy)
                            VisualType.PIE -> InsightPie(points, privacy)
                            VisualType.BAR -> InsightBars(points)
                            VisualType.LINE -> InsightLine(points)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightDropdown(label: String, value: String, expanded: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("$label: $value", color = TextPrimary, modifier = Modifier.weight(1f)); Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
        DropdownMenu(expanded = expanded, onDismissRequest = onClick, modifier = Modifier.background(DarkSurface), content = content)
    }
}

@Composable private fun InsightTable(points: List<InsightPoint>, privacy: Boolean) { points.forEach { point -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(point.label, color = TextSecondary); Text(moneyString(point.value, privacy), color = TextPrimary) } } }

@Composable private fun InsightBars(points: List<InsightPoint>) {
    val maxValue = max(1.0, points.maxOfOrNull { kotlin.math.abs(it.value) } ?: 1.0)
    Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) { points.forEach { point -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.width(22.dp).height((120 * kotlin.math.abs(point.value) / maxValue).dp).background(PrimaryViolet, RoundedCornerShape(6.dp))); Text(point.label, color = TextMuted, style = MaterialTheme.typography.labelSmall) } } }
}

@Composable private fun InsightLine(points: List<InsightPoint>) {
    val lineColor = SecondaryTeal
    val pathColor = PrimaryViolet
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val maxValue = max(1.0, points.maxOfOrNull { kotlin.math.abs(it.value) } ?: 1.0)
        val step = if (points.size > 1) size.width / (points.size - 1) else size.width
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = index * step
            val y = size.height / 2f - (point.value / maxValue).toFloat() * size.height / 2.2f
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(lineColor, 5f, androidx.compose.ui.geometry.Offset(x, y))
        }
        drawPath(path, pathColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
    }
}

@Composable private fun InsightPie(points: List<InsightPoint>, privacy: Boolean) {
    val total = points.sumOf { kotlin.math.abs(it.value) }.coerceAtLeast(1.0)
    val palette = ChartPalette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(150.dp)) { var start = -90f; points.forEachIndexed { index, point -> val sweep = (kotlin.math.abs(point.value) / total * 360).toFloat(); drawArc(palette[index % palette.size], start, sweep, true); start += sweep } }
        Column { points.forEachIndexed { index, point -> Text("■ ${point.label}: ${moneyString(point.value, privacy)}", color = palette[index % palette.size], style = MaterialTheme.typography.labelSmall) } }
    }
}
