package com.example.financemanager.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSCardStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.data.RecurringInterval
import com.example.financemanager.data.TransactionType
import com.example.financemanager.services.OcrAnalyzer
import com.example.financemanager.services.OcrResult
import com.example.financemanager.theme.AccentGreen
import com.example.financemanager.theme.AlertRed
import com.example.financemanager.theme.BorderColor
import com.example.financemanager.theme.DarkSurface
import com.example.financemanager.theme.DeepBackground
import com.example.financemanager.theme.PrimaryViolet
import com.example.financemanager.theme.SecondaryTeal
import com.example.financemanager.theme.TextMuted
import com.example.financemanager.theme.TextPrimary
import com.example.financemanager.theme.TextSecondary
import com.example.financemanager.theme.Typography
import com.example.financemanager.theme.WarningAmber

import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class SplitItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String = "",
    val categoryId: Long? = null,
    val amount: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val amountInput by viewModel.amountInput.collectAsState()
    val context = LocalContext.current

    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var destinationAccountId by remember { mutableStateOf<Long?>(null) }
    var noteInput by remember { mutableStateOf("") }
    var hashtagInput by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
    var isSplitTransaction by remember { mutableStateOf(false) }
    var splitItems by remember { mutableStateOf(listOf(SplitItem())) }
    var nlpTextInput by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    var recurringInterval by remember { mutableStateOf(RecurringInterval.MONTHLY) }
    var isAutoLog by remember { mutableStateOf(true) }
    var subscriptionDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var isScanningReceipt by remember { mutableStateOf(false) }
    var receiptResult by remember { mutableStateOf<OcrResult?>(null) }
    var showUpgradePrompt by remember { mutableStateOf(false) }

    fun applyNlp(text: String) {
        val result = viewModel.parseNlpPhrase(text)
        result.amount?.let { viewModel.setAmount(it) }
        result.categoryName?.let { catName ->
            categories.firstOrNull { it.name.contains(catName, ignoreCase = true) }?.let { selectedCategoryId = it.id }
        }
        selectedType = result.transactionType
        if (result.note.isNotBlank()) noteInput = result.note
    }

    val receiptPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        if (!viewModel.consumeReceiptScanQuota()) {
            showUpgradePrompt = true
            return@rememberLauncherForActivityResult
        }

        isScanningReceipt = true
        OcrAnalyzer.analyzeReceipt(
            context = context,
            imageUri = uri,
            onSuccess = { result ->
                isScanningReceipt = false
                receiptResult = result
                result.total?.let { viewModel.setAmount(it) }
                result.merchant?.let { merchant ->
                    noteInput = merchant
                    applyNlp(merchant)
                }
                selectedType = TransactionType.EXPENSE
                Toast.makeText(context, if (result.total == null) "Receipt scanned. Review the extracted details." else "Receipt scanned.", Toast.LENGTH_SHORT).show()
            },
            onFailure = {
                isScanningReceipt = false
                Toast.makeText(context, "Receipt scan failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        )
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                nlpTextInput = spoken
                applyNlp(spoken)
            }
        }
    }

    fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something like coffee 120 or Uber 350")
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Voice input isn't available on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(amountInput) {
        if (amountInput != "0" && amountInput.isNotBlank()) {
            amountText = amountInput
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Transaction", style = Typography.titleLarge.copy(color = TextPrimary)) },
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
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
            ) { DatePicker(state = datePickerState) }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        viewModel.setAmount(it.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("Amount", color = TextSecondary) },
                    textStyle = Typography.headlineMedium.copy(
                        color = if (selectedType == TransactionType.EXPENSE) AlertRed else AccentGreen,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryViolet,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                Text("Quick presets", style = Typography.labelMedium.copy(color = TextSecondary))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Coffee" to 150, "Lunch" to 300, "Groceries" to 1000, "Rent" to 10000).forEach { (label, amount) ->
                        item {
                            AssistChip(
                                onClick = { amountText = amount.toString(); viewModel.setAmount(amount.toDouble()); if (noteInput.isBlank()) noteInput = label },
                                label = { Text("$label ₹$amount") },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransactionType.values().forEach { type ->
                        val selected = selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { selectedType = type }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                type.name,
                                style = Typography.labelMedium.copy(
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            if (selectedType == TransactionType.EXPENSE) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Category Envelope", style = Typography.labelMedium.copy(color = TextSecondary))
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        if (isSplitTransaction) {
                            SplitEditor(
                                categories = categories,
                                splitItems = splitItems,
                                onChange = { splitItems = it }
                            )
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(categories, key = { it.id }) { category ->
                                    val selected = selectedCategoryId == category.id
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (selected) Color(android.graphics.Color.parseColor(category.colorHex)) else DarkSurface)
                                            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                            .clickable { selectedCategoryId = if (selected) null else category.id }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(
                                                imageVector = getCategoryIcon(category.iconName),
                                                contentDescription = category.name,
                                                tint = if (selected) TextPrimary else Color(android.graphics.Color.parseColor(category.colorHex)),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(category.name, style = Typography.labelMedium.copy(color = if (selected) TextPrimary else TextPrimary))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                AccountPicker(
                    title = if (selectedType == TransactionType.TRANSFER) "Transfer From Account" else "Payment Account",
                    selectedId = selectedAccountId,
                    accountNames = accounts.associate { it.id to it.name },
                    onSelect = {
                        selectedAccountId = if (selectedAccountId == it) null else it
                        if (selectedAccountId == destinationAccountId) destinationAccountId = null
                    }
                )
            }

            if (selectedType == TransactionType.TRANSFER) {
                item {
                    AccountPicker(
                        title = "Transfer To Account",
                        selectedId = destinationAccountId,
                        accountNames = accounts.filter { it.id != selectedAccountId }.associate { it.id to it.name },
                        onSelect = { destinationAccountId = if (destinationAccountId == it) null else it }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date", style = Typography.bodyMedium.copy(color = TextSecondary))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(selectedDateMillis)), style = Typography.bodyMedium.copy(color = TextPrimary))
                        Icon(Icons.Default.CalendarToday, contentDescription = "Date", tint = SecondaryTeal, modifier = Modifier.size(18.dp))
                    }
                }
            }

            item {
                TextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    placeholder = { Text("Note (Optional)", style = TextStyle(color = TextMuted)) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        disabledContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            item {
                TextField(
                    value = hashtagInput,
                    onValueChange = { hashtagInput = it },
                    placeholder = { Text("Hashtags (e.g. #tax-deductible #vacation-goa)", style = TextStyle(color = TextMuted)) },
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        disabledContainerColor = DarkSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }

            item {
                Text("Smart Entry", style = Typography.labelMedium.copy(color = TextSecondary))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = nlpTextInput,
                        onValueChange = {
                            nlpTextInput = it
                            applyNlp(it)
                        },
                        placeholder = { Text("e.g. Starbucks coffee 280", style = TextStyle(color = TextMuted)) },
                        leadingIcon = {
                            IconButton(onClick = { launchVoiceInput() }) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice", tint = SecondaryTeal)
                            }
                        },
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface,
                            disabledContainerColor = DarkSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { applyNlp(nlpTextInput) })
                    )

                    IconButton(
                        onClick = { receiptPickerLauncher.launch("image/*") },
                        enabled = !isScanningReceipt,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurface)
                            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    ) {
                        if (isScanningReceipt) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = AccentGreen)
                        } else {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Scan receipt", tint = AccentGreen)
                        }
                    }
                }
            }

            receiptResult?.takeIf { it.items.isNotEmpty() }?.let { result ->
                item {
                    iOSCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Scanned Items (${result.items.size})", style = Typography.bodyLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                            result.items.take(4).forEach { item ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.first, style = Typography.bodyMedium.copy(color = TextSecondary))
                                    Text(moneyString(item.second), style = Typography.bodyMedium.copy(color = TextPrimary))
                                }
                            }
                            TextButton(
                                onClick = {
                                    isSplitTransaction = true
                                    splitItems = result.items.map { (label, amount) ->
                                        val matchedCategoryId = viewModel.parseNlpPhrase(label).categoryName
                                            ?.let { guess -> categories.firstOrNull { it.name.contains(guess, ignoreCase = true) }?.id }
                                        SplitItem(
                                            label = label,
                                            categoryId = matchedCategoryId,
                                            amount = String.format("%.2f", amount).removeSuffix(".00")
                                        )
                                    }
                                }
                            ) {
                                Text("Split by Receipt Items", color = AccentGreen)
                            }
                        }
                    }
                }
            }

            item {
                var showSubDatePicker by remember { mutableStateOf(false) }
                if (showSubDatePicker) {
                    val subDatePickerState = rememberDatePickerState(initialSelectedDateMillis = subscriptionDateMillis)
                    DatePickerDialog(
                        onDismissRequest = { showSubDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                subDatePickerState.selectedDateMillis?.let { subscriptionDateMillis = it }
                                showSubDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showSubDatePicker = false }) { Text("Cancel") } }
                    ) { DatePicker(state = subDatePickerState) }
                }

                iOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = iOSCardStyle.Grouped
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Set as Subscription / Bill", style = Typography.bodyMedium.copy(color = TextPrimary))
                                if (isRecurring) {
                                    Text("Repeats automatically", style = Typography.labelSmall.copy(color = TextSecondary))
                                }
                            }
                            Switch(
                                checked = isRecurring,
                                onCheckedChange = { isRecurring = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentGreen)
                            )
                        }

                        if (isRecurring) {
                            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(start = 16.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        recurringInterval = when (recurringInterval) {
                                            RecurringInterval.DAILY -> RecurringInterval.WEEKLY
                                            RecurringInterval.WEEKLY -> RecurringInterval.MONTHLY
                                            RecurringInterval.MONTHLY -> RecurringInterval.YEARLY
                                            RecurringInterval.YEARLY -> RecurringInterval.DAILY
                                        }
                                     }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Frequency", style = Typography.bodyMedium.copy(color = TextPrimary))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(recurringInterval.name, style = Typography.bodyMedium.copy(color = TextSecondary))
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                                }
                            }
                            
                            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(start = 16.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showSubDatePicker = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Next Billing Date", style = Typography.bodyMedium.copy(color = TextPrimary))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(subscriptionDateMillis)), style = Typography.bodyMedium.copy(color = TextSecondary))
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted)
                                }
                            }

                            HorizontalDivider(color = BorderColor, modifier = Modifier.padding(start = 16.dp))
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Log Transaction", style = Typography.bodyMedium.copy(color = TextPrimary))
                                    Text(if (isAutoLog) "Deducts balance automatically" else "Sends a push notification reminder", style = Typography.labelSmall.copy(color = if (isAutoLog) AccentGreen else WarningAmber))
                                }
                                Switch(
                                    checked = isAutoLog,
                                    onCheckedChange = { isAutoLog = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentGreen)
                                )
                            }
                        }
                    }
                }
            }

            item {
                iOSButton(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        val srcAccountId = selectedAccountId
                        if (amount <= 0.0) {
                            Toast.makeText(context, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
                            return@iOSButton
                        }
                        if (srcAccountId == null) {
                            Toast.makeText(context, "Please select a source account.", Toast.LENGTH_SHORT).show()
                            return@iOSButton
                        }
                        if (selectedType == TransactionType.TRANSFER && destinationAccountId == null && noteInput.isBlank()) {
                            Toast.makeText(context, "Select a destination account or enter a transfer note.", Toast.LENGTH_SHORT).show()
                            return@iOSButton
                        }

                        if (isSplitTransaction) {
                            val parsedSplits = splitItems.mapNotNull { item ->
                                val itemAmount = item.amount.toDoubleOrNull() ?: 0.0
                                if (item.categoryId != null && itemAmount > 0) item.categoryId to itemAmount else null
                            }
                            val splitTotal = parsedSplits.sumOf { it.second }
                            if (kotlin.math.abs(splitTotal - amount) > 0.01) {
                                Toast.makeText(context, "Split amounts must sum to the total amount.", Toast.LENGTH_SHORT).show()
                                return@iOSButton
                            }
                            val taggedNote = listOf(noteInput.ifBlank { nlpTextInput.ifBlank { "Split transaction" } }, hashtagInput.trim()).filter { it.isNotBlank() }.joinToString(" ")
                            viewModel.addSplitTransaction(
                                totalAmount = amount,
                                splits = parsedSplits,
                                accountId = srcAccountId,
                                note = taggedNote,
                                date = selectedDateMillis
                            )
                        } else {
                            val taggedNote = listOf(noteInput.ifBlank {
                                if (selectedType == TransactionType.TRANSFER) {
                                    val destinationName = accounts.firstOrNull { it.id == destinationAccountId }?.name ?: "External transfer"
                                    "Transfer to $destinationName"
                                } else nlpTextInput.ifBlank { "Quick transaction" }
                            }, hashtagInput.trim()).filter { it.isNotBlank() }.joinToString(" ")
                            viewModel.addManualTransaction(
                                amount = amount,
                                type = selectedType,
                                categoryId = if (selectedType == TransactionType.TRANSFER) 0L else (selectedCategoryId ?: 0L),
                                accountId = srcAccountId,
                                note = taggedNote,
                                date = selectedDateMillis,
                                isRecurring = isRecurring,
                                recurringInterval = if (isRecurring) recurringInterval else null,
                                destinationAccountId = if (selectedType == TransactionType.TRANSFER) destinationAccountId else null,
                                isAutoLog = isAutoLog,
                                subscriptionDate = if (isRecurring) subscriptionDateMillis else null
                            )
                        }
                        onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    variant = iOSButtonVariant.Accent,
                    accentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text("Save Transaction", style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
    }

    if (showUpgradePrompt) {
        AlertDialog(
            onDismissRequest = { showUpgradePrompt = false },
            title = { Text("Upgrade to PocketPal Premium", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = { Text("You've used all free receipt scans for this month. Upgrade to Premium for unlimited scans.", style = Typography.bodyMedium.copy(color = TextSecondary)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.toggleProUser()
                    showUpgradePrompt = false
                }) { Text("Upgrade (Simulate)", color = AccentGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showUpgradePrompt = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun AccountPicker(
    title: String,
    selectedId: Long?,
    accountNames: Map<Long, String>,
    onSelect: (Long) -> Unit
) {
    Column {
        Text(title, style = Typography.labelMedium.copy(color = TextSecondary))
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accountNames.entries.toList(), key = { it.key }) { account ->
                val selected = selectedId == account.key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                        .clickable { onSelect(account.key) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        account.value,
                        style = Typography.labelMedium.copy(
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SplitEditor(
    categories: List<com.example.financemanager.data.Category>,
    splitItems: List<SplitItem>,
    onChange: (List<SplitItem>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        splitItems.forEachIndexed { index, splitItem ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (splitItem.label.isNotBlank()) {
                        Text(splitItem.label, style = Typography.labelSmall.copy(color = TextSecondary))
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories, key = { it.id }) { category ->
                            val selected = splitItem.categoryId == category.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Color(android.graphics.Color.parseColor(category.colorHex)) else DarkSurface)
                                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        val updated = splitItems.toMutableList()
                                        updated[index] = splitItem.copy(categoryId = if (selected) null else category.id)
                                        onChange(updated)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(category.name, style = Typography.labelSmall.copy(color = if (selected) TextPrimary else TextPrimary))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = splitItem.amount,
                    onValueChange = { newAmount ->
                        val updated = splitItems.toMutableList()
                        updated[index] = splitItem.copy(amount = newAmount)
                        onChange(updated)
                    },
                    label = { Text("Amt", color = TextSecondary) },
                    modifier = Modifier.width(92.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = Typography.bodyMedium.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryViolet,
                        unfocusedBorderColor = BorderColor,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )
                if (splitItems.size > 1) {
                    IconButton(
                        onClick = {
                            val updated = splitItems.toMutableList()
                            updated.removeAt(index)
                            onChange(updated)
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove split", tint = AlertRed)
                    }
                }
            }
        }
        TextButton(onClick = { onChange(splitItems + SplitItem()) }) {
            Text("+ Add Split", color = PrimaryViolet)
        }
    }
}
