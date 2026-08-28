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
import com.example.financemanager.domain.RESOLUTION_REVERSAL
import com.example.financemanager.domain.RESOLUTION_TRANSFER
import com.example.financemanager.domain.SmsLink
import com.example.financemanager.domain.SmsLinkKind
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import com.example.financemanager.domain.SmsAmountFormatter
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
    val links by viewModel.smsLinks.collectAsState()
    val pendingTxs by viewModel.pendingSmsTransactions.collectAsState()
    val ignoredTxs by viewModel.ignoredSmsTransactions.collectAsState()
    val approvedTxs by viewModel.approvedSmsTransactions.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val debts by viewModel.debts.collectAsState()
    val context = LocalContext.current

    var showApproveDialog by remember { mutableStateOf<SmsTransaction?>(null) }
    var showTransferDialog by remember { mutableStateOf<SmsLink?>(null) }
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
                // Sniff the leading byte rather than trusting the picked mime type or a file
                // extension SAF doesn't reliably expose: '<' means an SMS Backup & Restore XML
                // export (full history, auto-approved for anything before the user's first
                // logged transaction), anything else is treated as the existing JSON format.
                val looksLikeXml = context.contentResolver.openInputStream(uri)?.use { stream ->
                    val head = ByteArray(256)
                    val read = stream.read(head)
                    read > 0 && String(head, 0, read, Charsets.UTF_8).trimStart().startsWith("<")
                } ?: false

                if (looksLikeXml) {
                    viewModel.importSmsXmlHistorical(context, uri)
                    Toast.makeText(context, "Importing SMS history…", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.importSmsJson(context, uri)
                    Toast.makeText(context, "Importing SMS data...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    )
    val historicalImportResult by viewModel.historicalImportResult.collectAsState()
    LaunchedEffect(historicalImportResult) {
        historicalImportResult?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearHistoricalImportResult()
        }
    }
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
                            Box(
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(AlertRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    pendingTxs.size.toString(),
                                    color = Color.White,
                                    fontSize = if (pendingTxs.size > 99) 8.sp else 11.sp,
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
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/xml", "application/xml")) }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Import SMS backup", tint = SecondaryTeal)
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
                onImport = { importLauncher.launch(arrayOf("application/json", "text/xml", "application/xml")) }
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

                // Pairs that cancel out (a self transfer, or money that came back) are resolved
                // together above the inbox — approving either leg on its own is what double-counts
                // the money in the first place.
                if (selectedFilter == SmsFilter.PENDING && links.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.SwapHoriz,
                                contentDescription = null,
                                tint = SecondaryTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Matched pairs (${links.size})",
                                style = Typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    items(links, key = { it.key }) { link ->
                        SmsLinkCard(
                            link = link,
                            accounts = accounts,
                            onLogAsTransfer = { showTransferDialog = link },
                            onCancelOut = {
                                viewModel.resolveSmsLinkAsReversal(link)
                                Toast.makeText(
                                    context,
                                    "Cancelled out — nothing counted as spending",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDismiss = { viewModel.dismissSmsLink(link) }
                        )
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
                            isPartOfPair = links.any { it.debit.id == tx.id || it.credit.id == tx.id },
                            onApprove = {
                                // Reuse how this merchant was filed last time; fall back to the
                                // account matching the bank and the first envelope otherwise.
                                val learned = viewModel.learnedRuleFor(tx.counterparty)
                                val learnedAccount = learned?.accountId?.takeIf { id ->
                                    accounts.any { it.id == id }
                                }
                                val learnedCategory = learned?.categoryId?.takeIf { id ->
                                    categories.any { it.id == id }
                                }
                                selectedAccountId = learnedAccount ?: getDefaultAccountId(accounts, tx.accountName)
                                selectedCategoryId = learnedCategory ?: categories.firstOrNull()?.id ?: 0L
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

    showTransferDialog?.let { link ->
        LogTransferDialog(
            link = link,
            accounts = accounts,
            onConfirm = { fromId, toId, note ->
                viewModel.resolveSmsLinkAsTransfer(link, fromId, toId, note)
                showTransferDialog = null
                Toast.makeText(context, "Logged as one transfer", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showTransferDialog = null }
        )
    }

    showApproveDialog?.let { tx ->
        ApproveSmsDialog(
            tx = tx,
            accounts = accounts,
            categories = categories,
            debts = debts,
            selectedAccountId = selectedAccountId,
            selectedCategoryId = selectedCategoryId,
            isAutoFilled = viewModel.learnedRuleFor(tx.counterparty) != null,
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
                val account = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
                if (account != null) {
                    viewModel.approveSmsAsDebtPayment(tx, debt, paymentAmount, account)
                    showApproveDialog = null
                    Toast.makeText(context, "IOU settlement approved!", Toast.LENGTH_SHORT).show()
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
                Text("Import SMS Backup", fontWeight = FontWeight.SemiBold)
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
    isPartOfPair: Boolean = false,
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
        tx.resolution == RESOLUTION_TRANSFER -> "Self transfer"
        tx.resolution == RESOLUTION_REVERSAL -> "Cancelled out"
        tx.isApproved -> "Approved"
        tx.isIgnored -> "Ignored"
        else -> "Pending"
    }
    val statusColor = when {
        tx.resolution.isNotEmpty() -> SecondaryTeal
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
                            SmsAmountFormatter.display(tx.amount),
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

            if (isPartOfPair) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = SecondaryTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Matched with another alert — resolve it in Matched pairs above",
                        style = Typography.labelSmall.copy(color = SecondaryTeal, fontSize = 11.sp)
                    )
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
    isAutoFilled: Boolean,
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

    val activeDebts = debts.filter { !it.isSettled }
    val parsedAmountStr = SmsAmountFormatter.number(tx.amount)
    var settleAmountStr by remember { mutableStateOf(parsedAmountStr) }
    var selectedDebtId by remember { mutableStateOf<Long>(activeDebts.firstOrNull()?.id ?: 0L) }
    var currentTxType by remember { mutableStateOf(if (tx.type == "credit") TransactionType.INCOME else TransactionType.EXPENSE) }

    LaunchedEffect(activeDebts) {
        if (selectedDebtId == 0L && activeDebts.isNotEmpty()) {
            selectedDebtId = activeDebts.first().id
        }
    }

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
                                if (isAutoFilled) {
                                    "Envelope filled in from how you filed this merchant before"
                                } else {
                                    "Review the details before logging"
                                },
                                style = Typography.bodySmall.copy(
                                    color = if (isAutoFilled) AccentGreen else TextSecondary
                                )
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
                                        SmsAmountFormatter.display(tx.amount),
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
                                        text = { Text("${d.personName} (${moneyString(d.amount - d.paidAmount)})") },
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
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Column {
                        iOSButton(
                            onClick = {
                                if (isSettleIou) {
                                    val debt = activeDebts.find { it.id == selectedDebtId } ?: activeDebts.firstOrNull()
                                    val cleanAmt = SmsAmountFormatter.number(settleAmountStr).toDoubleOrNull() ?: 0.0
                                    when {
                                        activeDebts.isEmpty() -> {
                                            android.widget.Toast.makeText(context, "No active IOUs to settle", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        debt == null -> {
                                            android.widget.Toast.makeText(context, "Please select an IOU debt to settle", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        cleanAmt <= 0.0 -> {
                                            android.widget.Toast.makeText(context, "Please enter a valid settlement amount", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        else -> {
                                            onConfirmDebt(debt, cleanAmt)
                                        }
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

/**
 * One debit alert and the credit alert that cancels it, resolved as a pair.
 *
 * Both legs are shown in full because the decision is the user's: only they know whether two
 * equal amounts minutes apart were their own money moving between accounts, an IPO refund, or a
 * coincidence.
 */
@Composable
private fun SmsLinkCard(
    link: SmsLink,
    accounts: List<Account>,
    onLogAsTransfer: () -> Unit,
    onCancelOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val isReversal = link.kind == SmsLinkKind.REVERSAL
    val accent = if (isReversal) WarningAmber else SecondaryTeal
    val title = if (isReversal) "Money came back" else "Looks like a self transfer"
    val subtitle = if (isReversal) {
        "Debited and credited on the same account — a refund, a failed payment, or an IPO block released."
    } else {
        "The same amount left one account and landed in another."
    }
    val evidence = when {
        link.sharedReference -> "Same reference ${link.debit.reference}"
        link.statedReversal -> "The credit says it was reversed"
        else -> "${formatGap(link.gapMillis)} apart"
    }
    // Only worth warning about when there is something to undo.
    val alreadyLogged = link.debit.isApproved || link.credit.isApproved

    iOSCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isReversal) Icons.Default.Undo else Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = Typography.titleSmall.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                    Text(
                        evidence,
                        style = Typography.labelSmall.copy(color = accent, fontSize = 11.sp)
                    )
                }
                Text(
                    moneyString(link.amount),
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(subtitle, style = Typography.bodySmall.copy(color = TextSecondary, lineHeight = 17.sp))
            Spacer(modifier = Modifier.height(10.dp))

            Surface(color = DeepBackground, shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    SmsLinkLeg(
                        label = "Debited",
                        color = AlertRed,
                        sms = link.debit
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SmsLinkLeg(
                        label = "Credited",
                        color = AccentGreen,
                        sms = link.credit
                    )
                }
            }

            if (alreadyLogged) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "One leg is already logged — resolving this removes it, so it stops counting " +
                        "toward your spending.",
                    style = Typography.labelSmall.copy(color = WarningAmber, fontSize = 11.sp, lineHeight = 15.sp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            iOSButton(
                onClick = if (isReversal) onCancelOut else onLogAsTransfer,
                modifier = Modifier.fillMaxWidth(),
                variant = iOSButtonVariant.Accent,
                accentColor = accent
            ) {
                Icon(
                    if (isReversal) Icons.Default.Undo else Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (isReversal) "Cancel both out" else "Log as one transfer",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = if (isReversal) onLogAsTransfer else onCancelOut,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, BorderColor),
                    enabled = !isReversal || accounts.size > 1
                ) {
                    Text(
                        if (isReversal) "It was a transfer" else "It came back",
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, BorderColor)
                ) {
                    Text("Not related", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SmsLinkLeg(
    label: String,
    color: Color,
    sms: SmsTransaction
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = Typography.labelSmall.copy(color = color, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                sms.accountName.ifEmpty { sms.sender },
                style = Typography.bodySmall.copy(color = TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(sms.counterparty, formatSmsTimestamp(sms.rawTimestamp))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = Typography.labelSmall.copy(color = TextSecondary, fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Picks the two accounts a matched pair moved money between, then logs it as one transfer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogTransferDialog(
    link: SmsLink,
    accounts: List<Account>,
    onConfirm: (Long, Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var fromAccountId by remember { mutableStateOf(getDefaultAccountId(accounts, link.debit.accountName)) }
    var toAccountId by remember {
        mutableStateOf(
            accounts.firstOrNull {
                link.credit.accountName.isNotBlank() && it.name.contains(link.credit.accountName, ignoreCase = true)
            }?.id ?: accounts.firstOrNull { it.id != fromAccountId }?.id ?: 0L
        )
    }
    var note by remember { mutableStateOf("") }
    var fromExpanded by remember { mutableStateOf(false) }
    var toExpanded by remember { mutableStateOf(false) }

    val fieldColors = approveFieldColors()
    val isValid = fromAccountId > 0 && toAccountId > 0 && fromAccountId != toAccountId

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column {
                    Text(
                        "Log as transfer",
                        style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "${moneyString(link.amount)} moved between your own accounts. It won't count " +
                            "as spending or income.",
                        style = Typography.bodySmall.copy(color = TextSecondary, lineHeight = 17.sp)
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = fromExpanded,
                    onExpandedChange = { fromExpanded = !fromExpanded }
                ) {
                    OutlinedTextField(
                        value = accounts.find { it.id == fromAccountId }?.name ?: "Select account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("From (debited)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fromExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(expanded = fromExpanded, onDismissRequest = { fromExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    fromAccountId = account.id
                                    fromExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = toExpanded,
                    onExpandedChange = { toExpanded = !toExpanded }
                ) {
                    OutlinedTextField(
                        value = accounts.find { it.id == toAccountId }?.name ?: "Select account",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("To (credited)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )
                    ExposedDropdownMenu(expanded = toExpanded, onDismissRequest = { toExpanded = false }) {
                        accounts.filter { it.id != fromAccountId }.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    toAccountId = account.id
                                    toExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 2,
                    colors = fieldColors
                )

                if (!isValid) {
                    Text(
                        "Pick two different accounts.",
                        style = Typography.labelSmall.copy(color = WarningAmber)
                    )
                }

                Column {
                    iOSButton(
                        onClick = { if (isValid) onConfirm(fromAccountId, toAccountId, note) },
                        modifier = Modifier.fillMaxWidth(),
                        variant = iOSButtonVariant.Accent,
                        accentColor = SecondaryTeal
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log transfer", fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        }
    }
}

/** "4 min", "3 hr", "6 days" — how far apart the two alerts were. */
private fun formatGap(millis: Long): String {
    val minutes = millis / 60000
    return when {
        minutes < 1 -> "Seconds"
        minutes < 60 -> "$minutes min"
        minutes < 60 * 24 -> "${minutes / 60} hr"
        else -> "${minutes / (60 * 24)} days"
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
