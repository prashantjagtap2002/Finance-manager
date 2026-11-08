package com.example.financemanager.ui.screens

import com.example.financemanager.ui.components.formatDate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.domain.CashForecast
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.IosCard
import com.example.financemanager.ui.components.moneyString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where the balance is heading by the end of the pay cycle, and which envelopes won't make it.
 * The arithmetic is spelled out under the headline so the projection can be checked, not just
 * believed.
 */
@Composable
fun ForecastCard(forecast: CashForecast, privacy: Boolean, onOpenBudget: () -> Unit) {
    val negative = forecast.projectedBalance < 0

    IosCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenBudget)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "FORECAST · ${formatDate(forecast.cycleEnd, "d MMM").uppercase()}",
                    style = iOSCaption2.copy(color = TextMuted, letterSpacing = 1.sp),
                    modifier = Modifier.weight(1f)
                )
                if (forecast.isEarlyEstimate) {
                    Text(
                        "Early estimate",
                        style = Typography.labelSmall.copy(color = TextMuted),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SubtleSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                moneyString(forecast.projectedBalance, privacy),
                style = Typography.headlineSmall.copy(
                    color = if (negative) AlertRed else TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                if (negative) "Projected shortfall by cycle end · ${forecast.daysLeft} days left"
                else "Projected balance at cycle end · ${forecast.daysLeft} days left",
                style = Typography.labelMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ForecastFigure("Everyday", "−" + moneyString(forecast.projectedEverydaySpend, privacy, decimals = 0),
                    "${moneyString(forecast.dailyPace, privacy, decimals = 0)}/day")
                ForecastFigure("Bills", "−" + moneyString(forecast.upcomingBills, privacy, decimals = 0), "scheduled")
                ForecastFigure("Income", "+" + moneyString(forecast.upcomingIncome, privacy, decimals = 0), "expected")
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BorderColor)
            Spacer(modifier = Modifier.height(10.dp))

            if (forecast.envelopesAtRisk.isEmpty()) {
                Text(
                    "Every envelope lasts the cycle at this pace",
                    style = Typography.labelMedium.copy(color = AccentGreen)
                )
            } else {
                val now = System.currentTimeMillis()
                forecast.envelopesAtRisk.take(3).forEach { env ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            env.name,
                            style = Typography.bodyMedium.copy(color = TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (env.runsOutAt <= now) "Already over"
                            else "Runs out ~${formatDate(env.runsOutAt, "d MMM")}",
                            style = Typography.labelMedium.copy(
                                color = if (env.runsOutAt <= now) AlertRed else WarningAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ForecastFigure(label: String, value: String, caption: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(SubtleSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, style = Typography.labelSmall.copy(color = TextMuted))
        Text(value, style = Typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold), maxLines = 1)
        Text(caption, style = Typography.labelSmall.copy(color = TextMuted), maxLines = 1)
    }
}

/**
 * Things waiting on the user, one tappable row each. Only rendered when at least one count is
 * non-zero, so on a quiet day it takes no space at all.
 */
@Composable
fun NeedsAttentionCard(
    pendingSms: Int,
    duplicates: Int,
    onReviewSms: () -> Unit,
    onReviewDuplicates: () -> Unit
) {
    IosCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            if (pendingSms > 0) {
                AttentionRow(
                    icon = Icons.Default.Sms,
                    tint = SecondaryTeal,
                    title = if (pendingSms == 1) "1 bank alert to review" else "$pendingSms bank alerts to review",
                    subtitle = "Swipe through them in seconds",
                    onClick = onReviewSms
                )
            }
            if (pendingSms > 0 && duplicates > 0) HorizontalDivider(color = BorderColor)
            if (duplicates > 0) {
                AttentionRow(
                    icon = Icons.Default.ContentCopy,
                    tint = WarningAmber,
                    title = if (duplicates == 1) "1 possible duplicate" else "$duplicates possible duplicates",
                    subtitle = "Same amount logged twice within minutes",
                    onClick = onReviewDuplicates
                )
            }
        }
    }
}

@Composable
private fun AttentionRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon, contentDescription = null, tint = tint,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.12f))
                .padding(8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = Typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
            Text(subtitle, style = Typography.labelMedium.copy(color = TextSecondary))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextMuted)
    }
}
