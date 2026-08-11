package com.example.financemanager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.financemanager.data.Account
import com.example.financemanager.data.Category
import com.example.financemanager.data.SmsTransaction
import com.example.financemanager.data.Debt
import com.example.financemanager.data.TransactionType
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SmsFilter(
    val label: String
) {
    PENDING("Pending"),
    APPROVED("Approved"),
    ALL("All Logs"),
    IGNORED("Ignored")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsTransactionsScreen(
    viewModel: FinanceViewModel,
    showBackButton: Boolean = true,
    onNavigateBack: () -> Unit
) {
    val allTxs by viewModel.smsTransactions.collectAsState()
    val pendingTxs by viewModel.pendingSmsTransactions.collectAsState()
    val ignoredTxs by viewModel.ignoredSmsTransactions.collectAsState()
    val approvedTxs by viewModel.approvedSmsTransactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val context = LocalContext.current

    var showApproveDialog by remember { mutableStateOf<SmsTransaction?>(null) }
    var selectedCategoryId by remember { mutableStateOf<Long>(0L) }
    var selectedAccountId by remember { mutableStateOf<Long>(0L) }
    var approveNote by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(SmsFilter.PENDING) }
    var smsPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importSmsJson(context, uri)
                Toast.makeText(context, "Importing SMS data...", Toast.LENGTH_SHORT).show()
            }
        }
    )
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        smsPermissionGranted = granted
        if (!granted) {
            Toast.makeText(context, "SMS permission is needed for automatic capture", Toast.LENGTH_SHORT).show()
        }
    }

    val visibleTransactions = when (selectedFilter) {
        SmsFilter.ALL -> allTxs
        SmsFilter.PENDING -> pendingTxs
        SmsFilter.IGNORED -> ignoredTxs
        SmsFilter.APPROVED -> approvedTxs
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SMS Transactions", style = Typography.titleLarge.copy(color = TextPrimary))
                        if (pendingTxs.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = AlertRed,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text(
                                    "${pendingTxs.size}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import JSON", tint = SecondaryTeal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        containerColor = DeepBackground
    ) { paddingValues ->
        if (allTxs.isEmpty()) {
            EmptySmsState(
                modifier = Modifier.padding(paddingValues),
                onImport = { importLauncher.launch(arrayOf("application/json")) }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!smsPermissionGranted) {
                    item {
                        SmsPermissionCard(
                            onGrant = { smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) }
                        )
                    }
                }

                item {
                    SummaryRow(
                        pendingCount = pendingTxs.size,
                        totalCount = allTxs.size,
                        ignoredCount = ignoredTxs.size
                    )
                }

                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmsFilter.entries.forEach { filter ->
                            val count = when (filter) {
                                SmsFilter.PENDING -> pendingTxs.size
                                SmsFilter.APPROVED -> approvedTxs.size
                                SmsFilter.ALL -> allTxs.size
                                SmsFilter.IGNORED -> ignoredTxs.size
                            }
                            val isSelected = selectedFilter == filter
                            Column(
                                modifier = Modifier
                                    .clickable { selectedFilter = filter }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "${filter.label} ($count)",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(32.dp)
                                        .background(if (isSelected) TextPrimary else Color.Transparent)
                                )
                            }
                        }
                    }
                }

                if (visibleTransactions.isEmpty()) {
                    item {
                        iOSCard(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No items in ${selectedFilter.label.lowercase()} right now.",
                                    style = Typography.bodyMedium.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                } else {
                    items(visibleTransactions, key = { it.id }) { tx ->
                        SmsTransactionCard(
                            tx = tx,
                            onApprove = {
                                selectedAccountId = getDefaultAccountId(accounts, tx.accountName)
                                selectedCategoryId = categories.firstOrNull()?.id ?: 0L
                                approveNote = ""
                                showApproveDialog = tx
                            },
                            onIgnore = { viewModel.ignoreSmsTransaction(tx) },
                            onRestore = { viewModel.unignoreSmsTransaction(tx) },
                            onDelete = { viewModel.deleteSmsTransaction(tx) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    showApproveDialog?.let { tx ->
        ApproveSmsDialog(
            tx = tx,
            accounts = accounts,
            categories = categories,
            debts = debts,
            selectedAccountId = selectedAccountId,
            selectedCategoryId = selectedCategoryId,
            note = approveNote,
            onAccountChange = { selectedAccountId = it },
            onCategoryChange = { selectedCategoryId = it },
            onNoteChange = { approveNote = it },
            onConfirmStandard = { txTypeOverride ->
                if (selectedAccountId > 0 && selectedCategoryId > 0) {
                    viewModel.approveSmsTransaction(tx, selectedCategoryId, selectedAccountId, approveNote, txTypeOverride)
                    showApproveDialog = null
                    Toast.makeText(context, "Transaction approved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Please select account and envelope budget", Toast.LENGTH_SHORT).show()
                }
            },
            onConfirmDebt = { debt, paymentAmount ->
                if (selectedAccountId > 0) {
                    val account = accounts.find { it.id == selectedAccountId }
                    if (account != null) {
                        viewModel.approveSmsAsDebtPayment(tx, debt, paymentAmount, account)
                        showApproveDialog = null
                        Toast.makeText(context, "IOU settlement approved!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Please select an account", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showApproveDialog = null }
        )
    }
}

@Composable
private fun EmptySmsState(
    modifier: Modifier = Modifier,
    onImport: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DarkSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Sms,
                    contentDescription = null,
                    tint = SecondaryTeal,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("No SMS transactions yet", style = Typography.titleMedium.copy(color = TextPrimary))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Bank transaction SMS will appear here automatically,\nor import them from a JSON file.",
                style = Typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            iOSButton(
                onClick = onImport,
                variant = iOSButtonVariant.Accent, accentColor = SecondaryTeal
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import JSON", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SummaryRow(
    pendingCount: Int,
    totalCount: Int,
    ignoredCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryChip(
            modifier = Modifier.weight(1f),
            label = "Pending",
            count = pendingCount,
            color = WarningAmber
        )
        SummaryChip(
            modifier = Modifier.weight(1f),
            label = "Total",
            count = totalCount,
            color = SecondaryTeal
        )
        SummaryChip(
            modifier = Modifier.weight(1f),
            label = "Ignored",
            count = ignoredCount,
            color = TextSecondary
        )
    }
}

@Composable
private fun SummaryChip(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    color: Color
) {
    iOSCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$count",
                style = Typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 22.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                label,
                style = Typography.labelSmall.copy(color = TextSecondary)
            )
        }
    }
}

@Composable
private fun SmsTransactionCard(
    tx: SmsTransaction,
    onApprove: () -> Unit,
    onIgnore: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val isCredit = tx.type == "credit"
    val isDebit = tx.type == "debit"
    val accentColor = when {
        tx.isApproved -> AccentGreen
        tx.isIgnored -> TextSecondary
        isCredit -> AccentGreen
        isDebit -> AlertRed
        else -> WarningAmber
    }
    val statusLabel = when {
        tx.isApproved -> "Approved"
        tx.isIgnored -> "Ignored"
        else -> "Pending"
    }
    val statusColor = when {
        tx.isApproved -> AccentGreen
        tx.isIgnored -> TextSecondary
        else -> WarningAmber
    }
    val bankInitial = tx.accountName.firstOrNull()?.uppercaseChar() ?: '?'

    iOSCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$bankInitial",
                        style = Typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            fontSize = 16.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tx.amount.ifEmpty { "N/A" },
                            style = Typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (isCredit) AccentGreen else if (isDebit) AlertRed else TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = statusColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                statusLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = Typography.labelSmall.copy(
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    if (tx.counterparty.isNotEmpty()) {
                        Text(
                            tx.counterparty.take(50),
                            style = Typography.bodyMedium.copy(color = TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tx.accountName.isNotEmpty()) {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = SecondaryTeal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                tx.accountName,
                                style = Typography.labelMedium.copy(color = SecondaryTeal)
                            )
                        } else {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Bank not detected",
                                style = Typography.labelMedium.copy(color = WarningAmber)
                            )
                        }
                        if (tx.balance.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Bal: ${tx.balance}",
                                style = Typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                        }
                    }
                }
            }

            if (tx.body.length > 5) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    color = DeepBackground,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        tx.body.take(180),
                        modifier = Modifier.padding(10.dp),
                        style = Typography.labelSmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        ),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatSmsTimestamp(tx.rawTimestamp),
                    style = Typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        tx.isIgnored -> {
                            TextButton(onClick = onDelete) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = AlertRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", color = AlertRed, fontSize = 13.sp)
                            }
                            iOSButton(
                                onClick = onRestore,
                                variant = iOSButtonVariant.Accent, accentColor = SecondaryTeal
                            ) {
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore")
                            }
                        }
                        tx.isApproved -> {
                            OutlinedButton(
                                onClick = onIgnore,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Text("Move to Ignore", fontSize = 13.sp)
                            }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = onIgnore,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ignore", fontSize = 12.sp)
                            }
                            iOSButton(
                                onClick = onApprove,
                                variant = iOSButtonVariant.Accent, accentColor = AccentGreen
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SmsPermissionCard(
    onGrant: () -> Unit
) {
    iOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Enable SMS capture", style = Typography.titleSmall.copy(color = TextPrimary))
                    Text(
                        "Grant SMS permission for automatic capture.",
                        style = Typography.bodySmall.copy(color = TextSecondary)
                    )
                }
            }
            iOSButton(
                onClick = onGrant,
                variant = iOSButtonVariant.Accent, accentColor = SecondaryTeal
            ) {
                Text("Grant")
            }
        }
    }
}

@Composable
private fun approveFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = DeepBackground,
    unfocusedContainerColor = DeepBackground,
    focusedBorderColor = SecondaryTeal,
    unfocusedBorderColor = Color.Transparent,
    focusedLeadingIconColor = SecondaryTeal,
    unfocusedLeadingIconColor = TextSecondary,
    focusedLabelColor = SecondaryTeal,
    unfocusedLabelColor = TextSecondary,
    cursorColor = SecondaryTeal
)

@Composable
private fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    selectedColors: List<Color>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DeepBackground, RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val color = selectedColors.getOrElse(index) { TextPrimary }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) color.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = Typography.labelMedium.copy(
                        color = if (isSelected) color else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApproveSmsDialog(
    tx: SmsTransaction,
    accounts: List<Account>,
    categories: List<Category>,
    debts: List<Debt>,
    selectedAccountId: Long,
    selectedCategoryId: Long,
    note: String,
    onAccountChange: (Long) -> Unit,
    onCategoryChange: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirmStandard: (TransactionType?) -> Unit,
    onConfirmDebt: (Debt, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var isSettleIou by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var debtExpanded by remember { mutableStateOf(false) }

    val parsedAmountStr = tx.amount.replace("Rs.", "").replace(",", "").trim()
    var settleAmountStr by remember { mutableStateOf(parsedAmountStr) }
    var selectedDebtId by remember { mutableStateOf<Long>(0L) }
    var currentTxType by remember { mutableStateOf(if (tx.type == "credit") TransactionType.INCOME else TransactionType.EXPENSE) }

    val activeDebts = debts.filter { !it.isSettled }
    val fieldColors = approveFieldColors()
    val directionColor = if (currentTxType == TransactionType.INCOME) AccentGreen else AlertRed
    val bankInitial = tx.accountName.firstOrNull()?.uppercaseChar() ?: '?'

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface
        ) {
            LazyColumn(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                "Approve Transaction",
                                style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Review the details before logging",
                                style = Typography.bodySmall.copy(color = TextSecondary)
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                item {
                    // Transaction details summary card
                    Surface(color = DeepBackground, shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(directionColor.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "$bankInitial",
                                        style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = directionColor)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        tx.amount,
                                        style = Typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = directionColor)
                                    )
                                    if (tx.counterparty.isNotEmpty()) {
                                        Text(
                                            tx.counterparty,
                                            style = Typography.bodyMedium.copy(color = TextPrimary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    if (currentTxType == TransactionType.INCOME) Icons.Default.CallReceived else Icons.Default.CallMade,
                                    contentDescription = null,
                                    tint = directionColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (tx.accountName.isNotEmpty() || tx.reference.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = BorderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (tx.accountName.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.AccountBalance,
                                                contentDescription = null,
                                                tint = SecondaryTeal,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(tx.accountName, style = Typography.labelSmall.copy(color = TextSecondary))
                                        }
                                    }
                                    if (tx.reference.isNotEmpty()) {
                                        Text(
                                            "Ref: ${tx.reference}",
                                            style = Typography.labelSmall.copy(color = TextSecondary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Standard vs Settle IOU Toggle
                item {
                    SegmentedToggle(
                        options = listOf("Standard Log", "Settle IOU"),
                        selectedIndex = if (isSettleIou) 1 else 0,
                        onSelect = { isSettleIou = it == 1 },
                        selectedColors = listOf(SecondaryTeal, GoldAccent)
                    )
                }

                if (!isSettleIou) {
                    item {
                        SegmentedToggle(
                            options = listOf("Expense", "Income"),
                            selectedIndex = if (currentTxType == TransactionType.INCOME) 1 else 0,
                            onSelect = { currentTxType = if (it == 1) TransactionType.INCOME else TransactionType.EXPENSE },
                            selectedColors = listOf(AlertRed, AccentGreen)
                        )
                    }
                }

                if (isSettleIou) {
                    // IOU Fields
                    item {
                        ExposedDropdownMenuBox(
                            expanded = debtExpanded,
                            onExpandedChange = { debtExpanded = !debtExpanded }
                        ) {
                            OutlinedTextField(
                                value = activeDebts.find { it.id == selectedDebtId }?.personName ?: "Select debt",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Settle IOU") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = debtExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors
                            )
                            ExposedDropdownMenu(
                                expanded = debtExpanded,
                                onDismissRequest = { debtExpanded = false }
                            ) {
                                activeDebts.forEach { d ->
                                    DropdownMenuItem(
                                        text = { Text("${d.personName} (${moneyString(d.amount - d.paidAmount, false)})") },
                                        onClick = {
                                            selectedDebtId = d.id
                                            debtExpanded = false
                                        }
                                    )
                                }
                                if (activeDebts.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No active IOUs") },
                                        onClick = { debtExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = settleAmountStr,
                            onValueChange = { settleAmountStr = it },
                            label = { Text("Settlement amount") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                    }
                }

                item {
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = !accountExpanded }
                    ) {
                        OutlinedTextField(
                            value = accounts.find { it.id == selectedAccountId }?.name ?: "Select account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account") },
                            leadingIcon = {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text(account.name) },
                                    onClick = {
                                        onAccountChange(account.id)
                                        accountExpanded = false
                                    },
                                    leadingIcon = if (account.id == selectedAccountId) {
                                        { Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                if (!isSettleIou) {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = !categoryExpanded }
                        ) {
                            OutlinedTextField(
                                value = categories.find { it.id == selectedCategoryId }?.name ?: "Select envelope",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Envelope budget") },
                                leadingIcon = {
                                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = fieldColors
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                categories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { Text(category.name) },
                                        onClick = {
                                            onCategoryChange(category.id)
                                            categoryExpanded = false
                                        },
                                        leadingIcon = if (category.id == selectedCategoryId) {
                                            { Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Notes, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3,
                        colors = fieldColors
                    )
                }

                item {
                    Column {
                        iOSButton(
                            onClick = {
                                if (isSettleIou) {
                                    val debt = activeDebts.find { it.id == selectedDebtId }
                                    val amount = settleAmountStr.toDoubleOrNull() ?: 0.0
                                    if (debt != null && amount > 0) {
                                        onConfirmDebt(debt, amount)
                                    }
                                } else {
                                    onConfirmStandard(currentTxType)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = iOSButtonVariant.Accent, accentColor = AccentGreen
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm & Log", fontWeight = FontWeight.SemiBold)
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel", color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

private fun getDefaultAccountId(accounts: List<Account>, accountName: String): Long {
    return accounts.find {
        it.name.contains(accountName, ignoreCase = true)
    }?.id ?: accounts.firstOrNull()?.id ?: 1L
}

private fun formatSmsTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
