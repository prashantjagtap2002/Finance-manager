package com.example.financemanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.theme.PrimaryViolet
import com.example.financemanager.theme.TextSecondary

@Composable
fun TrendChartComponent(
    data: Map<String, Double>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text("No Data Available", color = TextSecondary)
        }
        return
    }

    val maxAmount = data.values.maxOrNull() ?: 1.0
    // To prevent division by zero or invisible bars if max is exactly 0
    val safeMaxAmount = if (maxAmount <= 0.0) 1.0 else maxAmount

    // Data keys (Months)
    val entries = data.entries.toList()

    val primaryVioletColor = PrimaryViolet
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(vertical = 16.dp)
        ) {
            val width = size.width
            val height = size.height

            val barCount = entries.size
            val barSpacing = 16.dp.toPx()
            val totalSpacing = barSpacing * (barCount - 1)
            val barWidth = (width - totalSpacing) / barCount

            entries.forEachIndexed { index, entry ->
                val barHeight = ((entry.value / safeMaxAmount) * height).toFloat()
                
                val x = index * (barWidth + barSpacing)
                val y = height - barHeight

                drawRoundRect(
                    color = primaryVioletColor,
                    topLeft = Offset(x = x, y = y),
                    size = Size(width = barWidth, height = barHeight),
                    cornerRadius = CornerRadius(8.dp.toPx())
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            entries.forEach { entry ->
                // "MMM yyyy" -> we want just "MMM" or "MMM yy"
                val label = entry.key.take(3)
                Text(
                    text = label,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
