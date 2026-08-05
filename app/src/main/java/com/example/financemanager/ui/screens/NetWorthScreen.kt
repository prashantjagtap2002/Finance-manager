package com.example.financemanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.util.Collections
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val netWorthHistory by viewModel.netWorthHistory.collectAsState()

    val currentNetWorth = netWorthHistory.values.lastOrNull() ?: 0.0
    val previousNetWorth = if (netWorthHistory.size > 1) {
        netWorthHistory.values.toList()[netWorthHistory.size - 2]
    } else currentNetWorth

    val diff = currentNetWorth - previousNetWorth
    val isPositive = diff >= 0

    Scaffold(
        containerColor = DeepBackground,
        topBar = {
            TopAppBar(
                title = { Text("Net Worth", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Summary Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Total Net Worth", style = Typography.labelMedium.copy(color = TextSecondary))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        moneyString(currentNetWorth, false),
                        style = Typography.headlineLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPositive) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = if (isPositive) AccentGreen else AlertRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${if (isPositive) "+" else ""}${moneyString(diff, false)} this month",
                            style = Typography.labelMedium.copy(color = if (isPositive) AccentGreen else AlertRed)
                        )
                    }
                }
            }

            // Chart Card
            iOSCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("6-Month History", style = Typography.titleMedium.copy(color = TextPrimary))
                    Spacer(modifier = Modifier.height(16.dp))

                    if (netWorthHistory.isEmpty()) {
                        Text("No data available", color = TextSecondary)
                    } else {
                        NetWorthLineChart(
                            data = netWorthHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
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
    
    // Add some padding to max and min
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
                
                // Draw zero line if min is negative and max is positive
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

                drawPath(
                    path = path,
                    color = primaryVioletColor,
                    style = Stroke(width = 6f)
                )

                // Draw dots
                points.forEachIndexed { index, point ->
                    val isTouched = index == touchedIndex
                    val dotRadius = if (isTouched) 12f else 8f
                    val strokeWidth = if (isTouched) 6f else 4f
                    
                    if (isTouched) {
                        // Draw vertical dashed line for selected point
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
            
            // Tooltip Overlay
            touchedIndex?.let { index ->
                val entry = entries[index]
                val stepX = if (chartWidth > 0) chartWidth / (entries.size - 1).coerceAtLeast(1) else 0f
                val xPos = index * stepX
                
                // Determine tooltip alignment based on x position
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

        // X-Axis labels
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
