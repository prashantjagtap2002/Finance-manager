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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.financemanager.data.Account
import com.example.financemanager.data.Category
import com.example.financemanager.data.SmsTransaction
import com.example.financemanager.data.Debt
import com.example.financemanager.data.DebtType
import com.example.financemanager.data.TransactionType
import com.example.financemanager.domain.RESOLUTION_REVERSAL
import com.example.financemanager.domain.RESOLUTION_TRANSFER
import com.example.financemanager.domain.SmsLink
import com.example.financemanager.domain.SmsLinkKind
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.EmptyState
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import com.example.financemanager.domain.SmsAmountFormatter
import kotlinx.coroutines.launch
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    /** Confirmation that can be taken back. Toasts can't carry an action, so these are snackbars. */
    fun notify(message: String, undoLabel: String? = null, onUndo: (() -> Unit)? = null) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                withDismissAction = undoLabel == null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onUndo?.invoke()
        }
    }

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
                    notify("Importing SMS history…")
                } else {
                    viewModel.importSmsJson(context, uri)
                    notify("Importing SMS data…")
                }
            }
        }
    )
    val historicalImportResult by viewModel.historicalImportResult.collectAsState()
    LaunchedEffect(historicalImportResult) {
        historicalImportResult?.let { message ->
            notify(message)
            viewModel.clearHistoricalImportResult()
        }
    }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        smsPermissionGranted = granted
        if (!granted) {
            notify("SMS permission is needed for automatic capture")
        }
    }

    val visibleTransactions = when (selectedFilter) {
        SmsFilter.ALL -> allTxs
        SmsFilter.PENDING -> pendingTxs
        SmsFilter.IGNORED -> ignoredTxs
        SmsFilter.APPROVED -> approvedTxs
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                                notify("Cancelled out — nothing counted as spending")
                            },
                            onDismiss = { viewModel.dismissSmsLink(link) }
                        )
                    }
                }

                if (visibleTransactions.isEmpty()) {
                    item {
                        // Each tab means something different when it's empty: an empty Pending
                        // list is the goal, an empty Approved list just means nothing logged yet.
                        EmptyState(
                            icon = when (selectedFilter) {
                                SmsFilter.PENDING -> Icons.Default.DoneAll
                                SmsFilter.IGNORED -> Icons.Default.VisibilityOff
                                else -> Icons.Default.Sms
                            },
                            title = when (selectedFilter) {
                                SmsFilter.PENDING -> "All caught up"
                                SmsFilter.APPROVED -> "Nothing approved yet"
                                SmsFilter.IGNORED -> "Nothing ignored"
                                SmsFilter.ALL -> "No bank alerts yet"
                            },
                            message = when (selectedFilter) {
                                SmsFilter.PENDING -> "Every bank alert has been dealt with. New ones show up here automatically."
                                SmsFilter.APPROVED -> "Alerts you approve get logged as transactions and listed here."
                                SmsFilter.IGNORED -> "Alerts you dismiss are kept here in case you change your mind."
                                SmsFilter.ALL -> "Bank SMS alerts are read on your device and turned into suggestions here."
                            },
                            accent = if (selectedFilter == SmsFilter.PENDING) AccentGreen else SecondaryTeal
                        )
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
                            onIgnore = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.ignoreSmsTransaction(tx)
                                notify("Alert ignored", "Undo") {
                                    viewModel.unignoreSmsTransaction(tx)
                                }
                            },
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
                notify("Logged as one transfer")
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
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    notify("Transaction logged")
                } else {
                    notify("Pick an account and an envelope first")
                }
            },
            onConfirmDebt = { debt, paymentAmount ->
                val account = accounts.find { it.id == selectedAccountId } ?: accounts.firstOrNull()
                if (account != null) {
                    viewModel.approveSmsAsDebtPayment(tx, debt, paymentAmount, account)
                    showApproveDialog = null
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    notify("IOU settled and payment logged")
                } else {
                    notify("Pick an account first")
                }
            },
            onCreateIou = { personName, amount, iouNote ->
                if (selectedAccountId > 0 && personName.isNotBlank() && amount > 0.0) {
                    viewModel.createIouFromSms(tx, selectedAccountId, personName, amount, iouNote)
                    showApproveDialog = null
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    notify("IOU created and transaction logged")
                } else {
                    notify("Pick an account and person, and enter a valid amount")
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

/**
 * Equal-width tabs inside a pill. Every segment gets the same slot, so a long label is kept on
 * one line with an icon above it rather than wrapping and clipping against its neighbour.
 */
@Composable
private fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    selectedColors: List<Color>,
    modifier: Modifier = Modifier,
    icons: List<ImageVector> = emptyList()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DeepBackground, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val color = selectedColors.getOrElse(index) { TextPrimary }
            val contentColor = if (isSelected) color else TextSecondary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) color.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                icons.getOrNull(index)?.let { icon ->
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    label,
                    style = Typography.labelMedium.copy(
                        color = contentColor,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
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
    onCreateIou: (String, Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(0) } // 0 = standard, 1 = settle, 2 = create
    var validationError by remember { mutableStateOf<String?>(null) }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var debtExpanded by remember { mutableStateOf(false) }

    // A credit is normally money coming back from somebody who owes the user; a debit is
    // normally repayment of money the user borrowed. Keep unrelated IOUs out of the picker.
    val settleType = if (tx.type == "credit") DebtType.LENT else DebtType.BORROWED
    val activeDebts = debts.filter { !it.isSettled && it.type == settleType }
    val parsedAmountStr = SmsAmountFormatter.number(tx.amount)
    var settleAmountStr by remember { mutableStateOf(parsedAmountStr) }
    var iouPersonName by remember { mutableStateOf(tx.counterparty.ifEmpty { tx.sender }) }
    var iouAmountStr by remember { mutableStateOf(parsedAmountStr) }
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
    val modeTitle = when (mode) {
        1 -> "Settle an IOU"
        2 -> "Create an IOU"
        else -> "Review transaction"
    }
    val modeSubtitle = when (mode) {
        1 -> "Use this payment to reduce an existing IOU"
        2 -> if (tx.type == "credit") {
            "This money will be recorded as borrowed"
        } else {
            "This payment will be recorded as money lent"
        }
        else -> if (tx.type == "credit") "Money received · check the account and envelope" else "Money sent · check the account and envelope"
    }
    val actionLabel = when (mode) {
        1 -> "Settle & log payment"
        2 -> "Create IOU & log"
        else -> "Approve & log transaction"
    }
    val actionColor = when (mode) {
        1 -> GoldAccent
        2 -> SecondaryTeal
        else -> AccentGreen
    }

    // The platform default dialog width is too narrow for a three-tab toggle, so the window is
    // sized here instead: full width minus a margin, and only as tall as its content needs.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header and action bar are pinned; only the form between them scrolls, so the
                // approve button stays reachable no matter how tall the form grows.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 10.dp, top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            modeTitle,
                            style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            modeSubtitle,
                            style = Typography.bodySmall.copy(
                                color = if (mode == 2) SecondaryTeal else if (isAutoFilled) AccentGreen else TextSecondary,
                                lineHeight = 16.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp)

                Column(
                    modifier = Modifier
                        // fill = false keeps a short form compact instead of stretching the
                        // dialog to the full screen height.
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                        style = Typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = directionColor),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                Spacer(modifier = Modifier.width(8.dp))
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
                                        Row(
                                            modifier = Modifier.weight(1f, fill = false),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.AccountBalance,
                                                contentDescription = null,
                                                tint = SecondaryTeal,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                tx.accountName,
                                                style = Typography.labelSmall.copy(color = TextSecondary),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    if (tx.reference.isNotEmpty()) {
                                        Text(
                                            "Ref: ${tx.reference}",
                                            style = Typography.labelSmall.copy(color = TextSecondary),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Standard, settle, or create an IOU directly from this SMS.
                    SegmentedToggle(
                        options = listOf("Transaction", "Settle IOU", "New IOU"),
                        selectedIndex = mode,
                        onSelect = {
                            mode = it
                            validationError = null
                        },
                        selectedColors = listOf(SecondaryTeal, GoldAccent, AccentGreen),
                        icons = listOf(Icons.Default.ReceiptLong, Icons.Default.Autorenew, Icons.Default.PersonAdd)
                    )

                    if (mode == 1) {
                        Surface(
                            color = GoldAccent.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (tx.type == "credit") "Received money can reduce what someone owes you."
                                    else "This payment can reduce what you owe someone.",
                                    style = Typography.bodySmall.copy(color = TextPrimary, lineHeight = 16.sp)
                                )
                            }
                        }
                    }

                    if (mode == 0) {
                        SegmentedToggle(
                            options = listOf("Expense", "Income"),
                            selectedIndex = if (currentTxType == TransactionType.INCOME) 1 else 0,
                            onSelect = { currentTxType = if (it == 1) TransactionType.INCOME else TransactionType.EXPENSE },
                            selectedColors = listOf(AlertRed, AccentGreen),
                            icons = listOf(Icons.Default.CallMade, Icons.Default.CallReceived)
                        )
                    }

                    if (mode == 1) {
                        // IOU Fields
                        ExposedDropdownMenuBox(
                            expanded = debtExpanded,
                            onExpandedChange = { debtExpanded = !debtExpanded }
                        ) {
                            OutlinedTextField(
                                value = activeDebts.find { it.id == selectedDebtId }?.personName ?: "Select debt",
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
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

                        OutlinedTextField(
                            value = settleAmountStr,
                            onValueChange = { settleAmountStr = it },
                            label = { Text("Settlement amount") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                    }

                    if (mode == 2) {
                        Text(
                            if (tx.type == "credit") "Record this money as borrowed from this person"
                            else "Record this payment as money lent to this person",
                            style = Typography.bodySmall.copy(color = TextSecondary, lineHeight = 16.sp)
                        )
                        OutlinedTextField(
                            value = iouPersonName,
                            onValueChange = { iouPersonName = it },
                            label = { Text("Person Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                        OutlinedTextField(
                            value = iouAmountStr,
                            onValueChange = { iouAmountStr = it },
                            label = { Text("IOU amount") },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors
                        )
                    }

                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = !accountExpanded }
                    ) {
                        OutlinedTextField(
                            value = accounts.find { it.id == selectedAccountId }?.name ?: "Select account",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
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

                    if (mode == 0) {
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = !categoryExpanded }
                        ) {
                            OutlinedTextField(
                                value = categories.find { it.id == selectedCategoryId }?.name ?: "Select envelope",
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
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

                HorizontalDivider(color = BorderColor.copy(alpha = 0.4f), thickness = 0.5.dp)

                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    // A dialog can't host a snackbar, and a toast lands outside the sheet the user
                    // is looking at — so validation is answered in place, next to the control that
                    // needs fixing.
                    validationError?.let { error ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = AlertRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                error,
                                style = Typography.bodySmall.copy(color = AlertRed, lineHeight = 16.sp)
                            )
                        }
                    }
                    iOSButton(
                        onClick = {
                            validationError = null
                            if (mode == 1) {
                                val debt = activeDebts.find { it.id == selectedDebtId } ?: activeDebts.firstOrNull()
                                val cleanAmt = SmsAmountFormatter.number(settleAmountStr).toDoubleOrNull() ?: 0.0
                                when {
                                    activeDebts.isEmpty() -> {
                                        validationError = "You have no open IOUs of this kind to settle."
                                    }
                                    debt == null -> {
                                        validationError = "Choose which IOU this payment settles."
                                    }
                                    cleanAmt <= 0.0 -> {
                                        validationError = "Enter a settlement amount greater than zero."
                                    }
                                    else -> {
                                        onConfirmDebt(debt, cleanAmt)
                                    }
                                }
                            } else if (mode == 2) {
                                val cleanAmt = SmsAmountFormatter.number(iouAmountStr).toDoubleOrNull() ?: 0.0
                                onCreateIou(iouPersonName, cleanAmt, note)
                            } else {
                                onConfirmStandard(currentTxType)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = iOSButtonVariant.Accent, accentColor = actionColor
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            actionLabel,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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
