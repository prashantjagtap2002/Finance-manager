package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.financemanager.data.RecurringInterval
import com.example.financemanager.data.RecurringTransaction
import com.example.financemanager.data.TransactionType
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Normalizes any recurring interval to an approximate monthly cost. */
fun monthlyCostOf(rec: RecurringTransaction): Double = when (rec.interval) {
    RecurringInterval.DAILY -> rec.amount * 30.44
    RecurringInterval.WEEKLY -> rec.amount * 4.35
    RecurringInterval.MONTHLY -> rec.amount
    RecurringInterval.YEARLY -> rec.amount / 12.0
}

fun daysUntil(timestamp: Long, now: Long = System.currentTimeMillis()): Int {
    return ((timestamp - now) / (24L * 60 * 60 * 1000)).toInt()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val recurring by viewModel.recurringTransactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()

    var deletingRec by remember { mutableStateOf<RecurringTransaction?>(null) }
    var splittingRec by remember { mutableStateOf<RecurringTransaction?>(null) }

    val expenses = recurring
        .filter { it.type == TransactionType.EXPENSE }
        .sortedBy { it.nextExecutionDate }
    val income = recurring
        .filter { it.type == TransactionType.INCOME }
        .sortedBy { it.nextExecutionDate }
    val monthlyTotal = expenses.sumOf { monthlyCostOf(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions & Bills", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        containerColor = DeepBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Monthly commitment summary
            item {
                iOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("MONTHLY COMMITMENT", style = Typography.labelMedium.copy(color = TextSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            moneyString(monthlyTotal, isPrivacy),
                            style = Typography.headlineMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "${expenses.size} recurring bill${if (expenses.size != 1) "s" else ""} • normalized to a monthly rate",
                            style = Typography.labelMedium.copy(color = TextMuted)
                        )
                    }
                }
            }

            if (expenses.isEmpty() && income.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.EventRepeat,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "No subscriptions or scheduled bills yet.\nMark a transaction as “Subscription / Bill” when logging it.",
                            style = Typography.bodyMedium.copy(color = TextMuted),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            if (expenses.isNotEmpty()) {
                item {
                    Text("Upcoming Bills", style = Typography.titleMedium.copy(color = TextPrimary))
                }
                items(expenses, key = { "rec-${it.id}" }) { rec ->
                    val categoryName = categories.firstOrNull { it.id == rec.categoryId }?.name ?: "Uncategorized"
                    SubscriptionItem(
                        rec = rec,
                        categoryName = categoryName,
                        privacy = isPrivacy,
                        onDelete = { deletingRec = rec },
                        onTogglePause = { viewModel.toggleSubscriptionPause(rec) },
                        onLogAndSplit = { splittingRec = rec }
                    )
                }
            }

            if (income.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Recurring Income", style = Typography.titleMedium.copy(color = TextPrimary))
                }
                items(income, key = { "inc-${it.id}" }) { rec ->
                    val categoryName = categories.firstOrNull { it.id == rec.categoryId }?.name ?: "Income"
                    SubscriptionItem(
                        rec = rec,
                        categoryName = categoryName,
                        privacy = isPrivacy,
                        onDelete = { deletingRec = rec },
                        onTogglePause = { viewModel.toggleSubscriptionPause(rec) },
                        onLogAndSplit = { splittingRec = rec }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }

    deletingRec?.let { rec ->
        AlertDialog(
            onDismissRequest = { deletingRec = null },
            title = { Text("Stop this schedule?", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Text(
                    "\"${rec.note.ifEmpty { "Recurring transaction" }}\" will no longer be auto-logged. Past transactions are kept.",
                    style = Typography.bodyMedium.copy(color = TextSecondary)
                )
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        viewModel.deleteRecurringTransaction(rec)
                        deletingRec = null
                    },
                    variant = iOSButtonVariant.Accent,
                    accentColor = AlertRed
                ) { Text("Stop schedule") }
            },
            dismissButton = {
                TextButton(onClick = { deletingRec = null }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }

    splittingRec?.let { rec ->
        var friendName by remember { mutableStateOf("") }
        var friendShareText by remember { mutableStateOf((rec.amount / 2).toString()) }
        
        AlertDialog(
            onDismissRequest = { splittingRec = null },
            title = { Text("Log & Split Bill", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Log ${rec.note.ifEmpty { "Subscription" }} and split the cost with a friend.",
                        style = Typography.bodyMedium.copy(color = TextSecondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = friendName,
                        onValueChange = { friendName = it },
                        label = { Text("Friend's Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = friendShareText,
                        onValueChange = { friendShareText = it },
                        label = { Text("Friend's Share (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        val share = friendShareText.toDoubleOrNull() ?: 0.0
                        if (friendName.isNotBlank() && share > 0) {
                            viewModel.logAndSplitRecurring(rec, friendName, share)
                            splittingRec = null
                        }
                    },
                    variant = iOSButtonVariant.Accent,
                    accentColor = PrimaryViolet
                ) { Text("Log & Split") }
            },
            dismissButton = {
                TextButton(onClick = { splittingRec = null }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun SubscriptionItem(
    rec: RecurringTransaction,
    categoryName: String,
    privacy: Boolean,
    onDelete: () -> Unit,
    onTogglePause: () -> Unit,
    onLogAndSplit: () -> Unit
) {
    val days = daysUntil(rec.nextExecutionDate)
    val isExpense = rec.type == TransactionType.EXPENSE
    val accent = if (isExpense) {
        when {
            days <= 2 -> AlertRed
            days <= 7 -> WarningAmber
            else -> SecondaryTeal
        }
    } else AccentGreen

    val dueLabel = when {
        days < 0 -> "Overdue"
        days == 0 -> "Due today"
        days == 1 -> "Due tomorrow"
        else -> "In $days days"
    }

    iOSCard(
        modifier = Modifier.fillMaxWidth().let {
            if (rec.isPaused) it.background(Color.Black.copy(alpha = 0.4f)) else it
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isExpense) Icons.Default.EventRepeat else Icons.Default.Payments,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rec.note.ifEmpty { categoryName },
                    style = Typography.titleMedium.copy(color = TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$categoryName • ${rec.interval.name.lowercase().replaceFirstChar { it.uppercase() }}" + 
                        (if (!rec.isAutoLog) " • Reminder Only" else ""),
                    style = Typography.labelMedium.copy(color = TextSecondary)
                )
                Text(
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(rec.nextExecutionDate)),
                    style = Typography.labelSmall.copy(color = TextMuted)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    moneyString(rec.amount, privacy),
                    style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(dueLabel, style = Typography.labelSmall.copy(color = accent))
                }
            }
            if (!rec.isAutoLog && days <= 0 && !rec.isPaused) {
                IconButton(onClick = onLogAndSplit, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = "Log & Split", tint = AccentGreen, modifier = Modifier.size(20.dp))
                }
            }
            IconButton(onClick = onTogglePause, modifier = Modifier.size(40.dp)) {
                Icon(if (rec.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = "Pause/Resume", tint = TextMuted, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Stop schedule", tint = TextMuted, modifier = Modifier.size(20.dp))
            }
        }
    }
}
