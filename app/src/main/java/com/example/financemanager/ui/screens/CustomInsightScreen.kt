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

private enum class VisualType(val label: String) {
    BAR("Bar chart"), LINE("Line chart"), AREA("Area chart"),
    HORIZONTAL_BAR("Horizontal bars"), PIE("Pie chart"), DONUT("Donut chart"),
    CUMULATIVE("Cumulative line"), SCATTER("Scatter plot"), TABLE("Table")
}
private enum class InsightMetric(val label: String, val suffix: String = "") {
    SPENDING("Spending"), INCOME("Income"), NET("Net cash flow"),
    SAVINGS("Savings"), TRANSACTIONS("Transaction count", " tx"),
    AVERAGE("Average transaction"), SAVINGS_RATE("Savings rate", "%"),
    TAX_TAGGED("Tax/reimbursable spend")
}
private enum class InsightPeriod(val label: String, val months: Int) { ONE("1 month", 1), THREE("3 months", 3), SIX("6 months", 6), TWELVE("12 months", 12), TWENTY_FOUR("24 months", 24) }
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
                InsightMetric.SAVINGS -> (matching.filter { it.type == TransactionType.INCOME }.sumOf { it.amount } - matching.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }).coerceAtLeast(0.0)
                InsightMetric.TRANSACTIONS -> matching.size.toDouble()
                InsightMetric.AVERAGE -> if (matching.isEmpty()) 0.0 else matching.sumOf { it.amount } / matching.size
                InsightMetric.SAVINGS_RATE -> {
                    val earned = matching.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    if (earned > 0) ((earned - matching.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }) / earned * 100).coerceIn(-100.0, 100.0) else 0.0
                }
                InsightMetric.TAX_TAGGED -> matching.filter { Regex("#(?:tax-deductible|work-reimbursable)", RegexOption.IGNORE_CASE).containsMatchIn(it.note) }.sumOf { it.amount }
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
                        Text("Live preview", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                        Text("${metric.label} • ${period.label} • ${visual.label}", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        when (visual) {
                            VisualType.TABLE -> InsightTable(points, privacy, metric)
                            VisualType.PIE -> InsightPie(points, privacy)
                            VisualType.DONUT -> InsightDonut(points, privacy)
                            VisualType.BAR -> InsightBars(points)
                            VisualType.HORIZONTAL_BAR -> InsightHorizontalBars(points, privacy, metric)
                            VisualType.LINE -> InsightLine(points)
                            VisualType.AREA -> InsightArea(points)
                            VisualType.CUMULATIVE -> InsightLine(points.runningCumulative())
                            VisualType.SCATTER -> InsightScatter(points)
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

@Composable private fun InsightTable(points: List<InsightPoint>, privacy: Boolean, metric: InsightMetric) {
    points.forEach { point ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(point.label, color = TextSecondary)
            Text(formatInsightValue(point.value, privacy, metric), color = TextPrimary)
        }
    }
}

@Composable
private fun formatInsightValue(value: Double, privacy: Boolean, metric: InsightMetric): String = when (metric) {
    InsightMetric.TRANSACTIONS -> "${value.toInt()}${metric.suffix}"
    InsightMetric.SAVINGS_RATE -> "${"%.1f".format(Locale.getDefault(), value)}${metric.suffix}"
    else -> moneyString(value, privacy)
}

@Composable private fun InsightBars(points: List<InsightPoint>) {
    val maxValue = max(1.0, points.maxOfOrNull { kotlin.math.abs(it.value) } ?: 1.0)
    Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) { points.forEach { point -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) { Box(Modifier.width(22.dp).height((120 * kotlin.math.abs(point.value) / maxValue).dp).background(PrimaryViolet, RoundedCornerShape(6.dp))); Text(point.label, color = TextMuted, style = MaterialTheme.typography.labelSmall) } } }
}

@Composable
private fun InsightHorizontalBars(points: List<InsightPoint>, privacy: Boolean, metric: InsightMetric) {
    val maxValue = max(1.0, points.maxOfOrNull { kotlin.math.abs(it.value) } ?: 1.0)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        points.forEach { point ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(point.label, color = TextSecondary, modifier = Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall)
                Box(Modifier.weight(1f).height(18.dp).background(SubtleSurface, RoundedCornerShape(5.dp))) {
                    Box(Modifier.fillMaxWidth((kotlin.math.abs(point.value) / maxValue).toFloat()).fillMaxHeight().background(SecondaryTeal, RoundedCornerShape(5.dp)))
                }
                Text(formatInsightValue(point.value, privacy, metric), color = TextPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun List<InsightPoint>.runningCumulative(): List<InsightPoint> {
    var total = 0.0
    return map { point -> total += point.value; point.copy(value = total) }
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

@Composable
private fun InsightArea(points: List<InsightPoint>) {
    val lineColor = SecondaryTeal
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val maxValue = max(1.0, points.maxOfOrNull { kotlin.math.abs(it.value) } ?: 1.0)
        val step = if (points.size > 1) size.width / (points.size - 1) else size.width
        val line = Path()
        points.forEachIndexed { index, point ->
            val x = index * step
            val y = size.height / 2f - (point.value / maxValue).toFloat() * size.height / 2.2f
            if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, lineColor.copy(alpha = 0.22f))
        drawPath(line, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
    }
}

@Composable
private fun InsightScatter(points: List<InsightPoint>) {
    val outerColor = PrimaryViolet
    val innerColor = AccentGreen
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val maxValue = max(1.0, points.maxOfOrNull { kotlin.math.abs(it.value) } ?: 1.0)
        val step = if (points.size > 1) size.width / (points.size - 1) else size.width
        points.forEachIndexed { index, point ->
            val x = index * step
            val y = size.height / 2f - (point.value / maxValue).toFloat() * size.height / 2.2f
            drawCircle(outerColor, 9f, androidx.compose.ui.geometry.Offset(x, y))
            drawCircle(innerColor, 4f, androidx.compose.ui.geometry.Offset(x, y))
        }
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

@Composable
private fun InsightDonut(points: List<InsightPoint>, privacy: Boolean) {
    val total = points.sumOf { kotlin.math.abs(it.value) }.coerceAtLeast(1.0)
    val palette = ChartPalette
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.dp)) {
                var start = -90f
                points.forEachIndexed { index, point ->
                    val sweep = (kotlin.math.abs(point.value) / total * 360).toFloat()
                    drawArc(palette[index % palette.size], start, sweep, false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 30f))
                    start += sweep
                }
            }
            Text(moneyString(total, privacy), color = TextPrimary, style = MaterialTheme.typography.labelSmall)
        }
        Column { points.take(8).forEachIndexed { index, point -> Text("■ ${point.label}: ${moneyString(point.value, privacy)}", color = palette[index % palette.size], style = MaterialTheme.typography.labelSmall) } }
    }
}
