package com.example.financemanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.financemanager.core.AppLock
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.core.ThemePreference
import com.example.financemanager.data.Account
import com.example.financemanager.data.AccountType
import com.example.financemanager.services.SyncWorker
import kotlinx.coroutines.launch
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSubscriptions: () -> Unit = {},
    onNavigateToSmsTransactions: () -> Unit = {}
) {
    val accounts by viewModel.accounts.collectAsState()
    val isAppLockEnabled by viewModel.isAppLockEnabled.collectAsState()
    val hasSecurityPin by viewModel.hasSecurityPin.collectAsState()
    val appLockTimeoutMs by viewModel.appLockTimeoutMs.collectAsState()
    val isPrivacyMode by viewModel.isPrivacyMode.collectAsState()
    val currencyCode by viewModel.currencyCode.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()
    val isProUser by viewModel.isProUser.collectAsState()
    val scanCount by viewModel.scanCount.collectAsState()
    val context = LocalContext.current
    val syncStatus by produceState("Automatic sync is enabled every 12 hours", context) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(context).getWorkInfosForUniqueWork("CloudSyncWorker").get()
            }.getOrNull()?.let { infos ->
                when {
                    infos.any { it.state == androidx.work.WorkInfo.State.RUNNING } -> "Syncing now…"
                    infos.any { it.state == androidx.work.WorkInfo.State.FAILED } -> "Last sync failed — tap Sync Now to retry"
                    infos.any { it.state == androidx.work.WorkInfo.State.SUCCEEDED } -> "Cloud sync is up to date"
                    else -> "Automatic sync is enabled every 12 hours"
                }
            } ?: "Automatic sync is enabled every 12 hours"
        }
    }

    var showAddAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var deletingAccount by remember { mutableStateOf<Account?>(null) }
    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showLockTimeoutDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var selectedImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.exportBackupToUri(
                    context = context,
                    uri = uri,
                    onSuccess = { Toast.makeText(context, "Backup exported successfully!", Toast.LENGTH_SHORT).show() },
                    onError = { err -> Toast.makeText(context, "Export failed: $err", Toast.LENGTH_LONG).show() }
                )
            }
        }
    )

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            if (uri != null) {
                viewModel.exportTransactionsToUri(
                    context = context,
                    uri = uri,
                    onSuccess = { Toast.makeText(context, "Transactions exported successfully!", Toast.LENGTH_SHORT).show() },
                    onError = { err -> Toast.makeText(context, "Export failed: $err", Toast.LENGTH_LONG).show() }
                )
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                selectedImportUri = uri
                showImportConfirmDialog = true
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = Typography.titleLarge.copy(color = TextPrimary)) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // 1. Accounts Management
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manage Accounts", style = Typography.titleMedium.copy(color = TextPrimary))
                    IconButton(onClick = { showAddAccountDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Account", tint = SecondaryTeal)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                iOSCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        accounts.forEach { account ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(account.name, style = Typography.bodyLarge)
                                    Text(account.type.name, style = Typography.labelMedium.copy(color = TextSecondary))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        moneyString(account.balance, isPrivacyMode, decimals = 2),
                                        style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    IconButton(onClick = { editingAccount = account }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Account", tint = SecondaryTeal, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { deletingAccount = account }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Account", tint = AlertRed, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                            if (account != accounts.last()) {
                                HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }

                        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.reconcileBalances { drifts ->
                                        val message = when {
                                            drifts.isEmpty() -> "All balances match your transactions"
                                            drifts.size == 1 ->
                                                "Corrected ${drifts[0].account.name} by ${moneyString(drifts[0].difference)}"
                                            else -> "Corrected ${drifts.size} account balances"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Recalculate Balances", style = Typography.bodyLarge)
                                Text(
                                    "Re-derives each balance from its transactions and fixes any that drifted",
                                    style = Typography.labelMedium.copy(color = TextSecondary)
                                )
                            }
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = SecondaryTeal)
                        }
                    }
                }
            }

            item {
                Text("Preferences", style = Typography.titleMedium.copy(color = TextPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                iOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCurrencyDialog = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Currency & Region", style = Typography.bodyLarge)
                                Text(
                                    FinancePreferences.currencyOption(currencyCode).let { "${it.symbol} ${it.code}" },
                                    style = Typography.labelMedium.copy(color = TextSecondary)
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                        HorizontalDivider(color = BorderColor)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showThemeDialog = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Appearance", style = Typography.bodyLarge)
                                Text(
                                    when (themePreference) {
                                        ThemePreference.SYSTEM -> "Follow system"
                                        ThemePreference.LIGHT -> "Light mode"
                                        ThemePreference.DARK -> "Dark mode"
                                    },
                                    style = Typography.labelMedium.copy(color = TextSecondary)
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                        }
                    }
                }
            }

            // 2. Security & Privacy
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Security & Privacy", style = Typography.titleMedium.copy(color = TextPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                iOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("App Lock", style = Typography.bodyLarge)
                                Text(
                                    "Require fingerprint, device credential or PIN, and keep balances " +
                                        "out of screenshots and the recents preview",
                                    style = Typography.labelMedium.copy(color = TextSecondary)
                                )
                            }
                            Switch(
                                checked = isAppLockEnabled,
                                onCheckedChange = { viewModel.setAppLockEnabled(it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
                            )
                        }
                        if (isAppLockEnabled) {
                            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPinDialog = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Unlock PIN", style = Typography.bodyLarge)
                                    Text(
                                        if (hasSecurityPin) {
                                            "A PIN is set — used when biometrics aren't available"
                                        } else {
                                            "Not set. Without a PIN, a device with no screen lock can't be protected."
                                        },
                                        style = Typography.labelMedium.copy(color = TextSecondary)
                                    )
                                }
                                Text(
                                    if (hasSecurityPin) "Change" else "Set",
                                    style = Typography.labelLarge.copy(color = PrimaryViolet)
                                )
                            }
                            if (hasSecurityPin) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.clearSecurityPin() }
                                        .padding(top = 6.dp)
                                ) {
                                    Text(
                                        "Remove PIN",
                                        style = Typography.labelLarge.copy(color = AlertRed)
                                    )
                                }
                            }
                            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLockTimeoutDialog = true },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Lock", style = Typography.bodyLarge)
                                    Text(
                                        "Re-lock after leaving the app",
                                        style = Typography.labelMedium.copy(color = TextSecondary)
                                    )
                                }
                                Text(
                                    AppLock.timeoutOptions.firstOrNull { it.first == appLockTimeoutMs }?.second
                                        ?: "After 30 seconds",
                                    style = Typography.labelLarge.copy(color = PrimaryViolet)
                                )
                            }
                        }
                        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Privacy Mode", style = Typography.bodyLarge)
                                Text(
                                    "Mask balances and amounts across the app",
                                    style = Typography.labelMedium.copy(color = TextSecondary)
                                )
                            }
                            Switch(
                                checked = isPrivacyMode,
                                onCheckedChange = { viewModel.togglePrivacyMode() },
                                colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
                            )
                        }
                    }
                }
            }



            // 3. Subscriptions shortcut
            item {
                iOSCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSubscriptions() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Subscriptions & Bills", style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "Review recurring payments and stop unwanted ones",
                                style = Typography.labelMedium.copy(color = TextSecondary)
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                    }
                }
            }
            // SMS Transactions shortcut
            item {
                iOSCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToSmsTransactions() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SMS Transactions", style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Text(
                                "Auto-captured bank SMS with approve/review flow",
                                style = Typography.labelMedium.copy(color = TextSecondary)
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Data Management", style = Typography.titleMedium.copy(color = TextPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                iOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        iOSButton(
                            onClick = {
                                csvExportLauncher.launch("transactions.csv")
                            },
                            variant = iOSButtonVariant.Accent, accentColor = PrimaryViolet,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Export to CSV / Excel")
                        }
                        
                        val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
                        ) { uri ->
                            if (uri != null) {
                                viewModel.importTransactionsFromCsv(uri, context)
                                Toast.makeText(context, "Importing transactions...", Toast.LENGTH_SHORT).show()
                            }
                        }

                        iOSButton(
                            onClick = { importLauncher.launch("text/comma-separated-values") },
                            variant = iOSButtonVariant.Accent, accentColor = DarkSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import Transactions from CSV")
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Cloud Sync", style = Typography.titleMedium.copy(color = TextPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                iOSCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Supabase Cloud Backup",
                            style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Manually trigger a sync to back up your local database to Supabase Postgres.",
                            style = Typography.bodyMedium.copy(color = TextSecondary)
                        )
                        Text(syncStatus, style = Typography.labelMedium.copy(color = if (syncStatus.contains("failed", true)) AlertRed else AccentGreen))
                        iOSButton(
                            onClick = { 
                                val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
                                WorkManager.getInstance(context).enqueue(request)
                                Toast.makeText(context, "Cloud sync started in background...", Toast.LENGTH_SHORT).show()
                            },
                            variant = iOSButtonVariant.Accent, accentColor = AccentGreen,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = "Sync to Cloud")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now", fontWeight = FontWeight.Bold)
                        }

                        // Removed Google Sign in
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Data & Local Backups", style = Typography.titleMedium.copy(color = TextPrimary))
                Spacer(modifier = Modifier.height(8.dp))
                iOSCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "JSON Backup Management",
                            style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Export all accounts, envelopes, transactions, and scheduled items to a JSON file, or restore them.",
                            style = Typography.bodyMedium.copy(color = TextSecondary)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            iOSButton(
                                onClick = { exportLauncher.launch("financemanager_backup.json") },
                                variant = iOSButtonVariant.Accent, accentColor = PrimaryViolet,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export JSON")
                            }
                            iOSButton(
                                onClick = { importLauncher.launch(arrayOf("application/json")) },
                                variant = iOSButtonVariant.Accent, accentColor = SecondaryTeal,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = "Import")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore JSON")
                            }
                        }

                        HorizontalDivider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "PDF Reports",
                            style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Generate a professional monthly PDF report of all transactions.",
                            style = Typography.bodyMedium.copy(color = TextSecondary)
                        )
                        iOSButton(
                            onClick = { 
                                val monthFormat = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                                val currentMonth = monthFormat.format(java.util.Date())
                                val path = viewModel.exportMonthlyPdf(context, currentMonth)
                                if (path != null) {
                                    Toast.makeText(context, "PDF saved to: $path", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                                }
                            },
                            variant = iOSButtonVariant.Accent, accentColor = TextPrimary,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = DeepBackground)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Monthly PDF")
                        }
                        }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    deletingAccount?.let { account ->
        DeleteAccountDialog(
            account = account,
            accounts = accounts,
            viewModel = viewModel,
            onDismiss = { deletingAccount = null }
        )
    }

    if (showPinDialog) {
        SetPinDialog(
            onDismiss = { showPinDialog = false },
            onConfirm = { pin ->
                viewModel.setSecurityPin(pin)
                showPinDialog = false
                Toast.makeText(context, "PIN saved", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showLockTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showLockTimeoutDialog = false },
            title = { Text("Auto-lock", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "How long the app can stay in the background before it asks you to unlock again.",
                        style = Typography.labelMedium.copy(color = TextSecondary)
                    )
                    AppLock.timeoutOptions.forEach { (millis, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setAppLockTimeout(millis)
                                    showLockTimeoutDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = appLockTimeoutMs == millis,
                                onClick = {
                                    viewModel.setAppLockTimeout(millis)
                                    showLockTimeoutDialog = false
                                }
                            )
                            Text(label, style = Typography.bodyLarge.copy(color = TextPrimary))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLockTimeoutDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Choose currency", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FinancePreferences.supportedCurrencies.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setCurrency(option.code)
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currencyCode == option.code,
                                onClick = {
                                    viewModel.setCurrency(option.code)
                                    showCurrencyDialog = false
                                }
                            )
                            Column {
                                Text("${option.symbol} ${option.code}", style = Typography.bodyLarge.copy(color = TextPrimary))
                                Text(option.label, style = Typography.labelMedium.copy(color = TextSecondary))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Appearance", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePreference.values().forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemePreference(option)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themePreference == option,
                                onClick = {
                                    viewModel.setThemePreference(option)
                                    showThemeDialog = false
                                }
                            )
                            Text(
                                when (option) {
                                    ThemePreference.SYSTEM -> "Follow system"
                                    ThemePreference.LIGHT -> "Light mode"
                                    ThemePreference.DARK -> "Dark mode"
                                },
                                style = Typography.bodyLarge.copy(color = TextPrimary)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Create Account Dialog
    if (showAddAccountDialog) {
        var accountName by remember { mutableStateOf("") }
        var accountBalance by remember { mutableStateOf("") }
        var selectedType by remember { mutableStateOf(AccountType.BANK) }

        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Add Financial Account", style = Typography.titleLarge) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        label = { Text("Account Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accountBalance,
                        onValueChange = { accountBalance = it },
                        label = { Text("Initial Balance (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Account Type", style = Typography.labelMedium.copy(color = TextSecondary))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AccountType.values().forEach { type ->
                            val isSelected = selectedType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SecondaryTeal else BorderColor)
                                    .clickable { selectedType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.name.take(6),
                                    style = Typography.labelMedium.copy(
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        if (accountName.isNotEmpty()) {
                            val balance = accountBalance.toDoubleOrNull() ?: 0.0
                            viewModel.createAccount(accountName, selectedType, balance)
                            showAddAccountDialog = false
                        }
                    },
                    variant = iOSButtonVariant.Accent, accentColor = PrimaryViolet
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Edit Account Dialog
    editingAccount?.let { account ->
        var editName by remember { mutableStateOf(account.name) }
        var editBalance by remember { mutableStateOf(account.balance.toString()) }
        var editType by remember { mutableStateOf(account.type) }

        AlertDialog(
            onDismissRequest = { editingAccount = null },
            title = { Text("Edit Financial Account", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Account Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editBalance,
                        onValueChange = { editBalance = it },
                        label = { Text("Current Balance (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Account Type", style = Typography.labelMedium.copy(color = TextSecondary))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AccountType.values().forEach { type ->
                            val isSelected = editType == type
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SecondaryTeal else BorderColor)
                                    .clickable { editType = type }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.name.take(6),
                                    style = Typography.labelMedium.copy(
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        if (editName.isNotEmpty()) {
                            val newBalance = editBalance.toDoubleOrNull() ?: account.balance
                            val updatedAccount = account.copy(name = editName, type = editType, balance = newBalance)
                            viewModel.updateAccount(updatedAccount)
                            editingAccount = null
                        }
                    },
                    variant = iOSButtonVariant.Accent, accentColor = SecondaryTeal
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAccount = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    if (showImportConfirmDialog && selectedImportUri != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                selectedImportUri = null
            },
            title = { Text("Restore Backup?", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Text(
                    text = "WARNING: Restoring this backup will completely delete all current local transactions, accounts, and envelopes. This action cannot be undone.",
                    style = Typography.bodyMedium.copy(color = TextSecondary)
                )
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        val uri = selectedImportUri
                        if (uri != null) {
                            viewModel.importBackupFromUri(
                                context = context,
                                uri = uri,
                                onSuccess = {
                                    Toast.makeText(context, "Backup restored successfully!", Toast.LENGTH_SHORT).show()
                                },
                                onError = { err ->
                                    Toast.makeText(context, "Restore failed: $err", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                        showImportConfirmDialog = false
                        selectedImportUri = null
                    },
                    variant = iOSButtonVariant.Accent, accentColor = AlertRed
                ) {
                    Text("Overwrite & Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        selectedImportUri = null
                    }
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
