package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.data.Category
import com.example.financemanager.data.TransactionType
import com.example.financemanager.theme.*
import com.example.financemanager.theme.Typography
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSCardStyle
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BudgetScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategory: (Long, String, String) -> Unit = { _, _, _ -> },
    showBackButton: Boolean = true
) {
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val monthlyCategorySpend by viewModel.monthlyCategorySpend.collectAsState(initial = emptyMap())
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()

    // Zero-Based Budget Math
    val totalIncome = transactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount }
        
    val totalAllocated = categories.sumOf { it.budgetLimit }
    val leftToAssign = totalIncome - totalAllocated

    // Dialog state for adding custom category
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }

    // Dialog state for editing a category limit & rollover
    var categoryToEditLimit by remember { mutableStateOf<Category?>(null) }
    var categoryToUpdateRollover by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var editLimitInput by remember { mutableStateOf("") }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Budget Envelopes", style = Typography.titleLarge.copy(color = TextPrimary)) },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Envelope", tint = SecondaryTeal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        containerColor = DeepBackground
    ) { paddingValues ->
        
        var categoryList by remember { mutableStateOf(categories) }
        LaunchedEffect(categories) {
            categoryList = categories
        }

        val haptic = LocalHapticFeedback.current
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            val fromIndex = categoryList.indexOfFirst { it.id == from.key }
            val toIndex = categoryList.indexOfFirst { it.id == to.key }
            if (fromIndex != -1 && toIndex != -1) {
                categoryList = categoryList.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Zero-Based Budget Assistant Card
            item(key = "zero_based_budget_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            leftToAssign == 0.0 && totalIncome > 0 -> SuccessSoft
                            leftToAssign < 0.0 -> ErrorSoft
                            else -> DarkSurface
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Give every rupee a job",
                            style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Monthly Income", style = Typography.labelMedium.copy(color = TextSecondary))
                                Text(moneyString(totalIncome), style = Typography.titleLarge.copy(color = AccentGreen))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Envelope Budgets", style = Typography.labelMedium.copy(color = TextSecondary))
                                Text(moneyString(totalAllocated), style = Typography.titleLarge.copy(color = PrimaryViolet))
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = BorderColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        when {
                            leftToAssign == 0.0 && totalIncome > 0 -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Perfect", tint = AccentGreen)
                                    Text("Perfect Zero-Based Budget! Every rupee has a designated job.", style = Typography.bodyMedium.copy(color = AccentGreen))
                                }
                            }
                            leftToAssign < 0.0 -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Error, contentDescription = "Over allocated", tint = AlertRed)
                                    Text("Over-allocated by ${moneyString(-leftToAssign)}. Reduce category limits.", style = Typography.bodyMedium.copy(color = AlertRed))
                                }
                            }
                            else -> {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "${moneyString(leftToAssign)} left to assign",
                                        style = Typography.titleMedium.copy(color = WarningAmber, fontWeight = FontWeight.Bold)
                                    )
                                    Text("Put the rest into envelopes so none of your income is unplanned.", style = Typography.bodyMedium.copy(color = TextSecondary))
                                }
                            }
                        }
                    }
                }
            }

            item(key = "budget_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    iOSButton(
                        onClick = { showTransferDialog = true },
                        modifier = Modifier.weight(1f),
                        variant = iOSButtonVariant.Outlined
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Move money")
                    }
                    iOSButton(
                        onClick = {
                            viewModel.stuffEnvelopesForNewCycle()
                            scope.launch {
                                snackbarHostState.showSnackbar("Envelopes filled with leftover funds")
                            }
                        },
                        modifier = Modifier.weight(1f),
                        variant = iOSButtonVariant.Accent,
                        accentColor = AccentGreen
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fill envelopes")
                    }
                }
            }

            // 2. Budget Envelopes Header
            item(key = "envelopes_header") {
                Text("Envelopes List", style = Typography.titleMedium.copy(color = TextPrimary))
            }

            // 3. Category Envelopes List
            items(categoryList, key = { it.id }) { category ->
                ReorderableItem(reorderableState, key = category.id) { isDragging ->
                    val elevation = if (isDragging) 8.dp else 0.dp
                    
                    var menuExpanded by remember { mutableStateOf(false) }
                    val envelopeLimit = category.budgetLimit + category.rolloverAmount
                    val envelopeSpent = monthlyCategorySpend[category.id] ?: 0.0
                    val envelopeRemaining = envelopeLimit - envelopeSpent
                    val envelopeRatio = if (envelopeLimit > 0) {
                        (envelopeSpent / envelopeLimit).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    
                    iOSCard(
                        style = iOSCardStyle.Grouped,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val cal = java.util.Calendar.getInstance()
                                val monthStr = cal.get(java.util.Calendar.MONTH).toString()
                                val yearStr = cal.get(java.util.Calendar.YEAR).toString()
                                onNavigateToCategory(category.id, yearStr, monthStr)
                            }
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .longPressDraggableHandle(
                                        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.reorderCategories(categoryList)
                                        }
                                    )
                                    .padding(end = 4.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(category.colorHex)).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(category.iconName),
                                    contentDescription = category.name,
                                    tint = Color(android.graphics.Color.parseColor(category.colorHex)),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    category.name,
                                    style = Typography.titleMedium.copy(color = TextPrimary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "of ${moneyString(category.budgetLimit, isPrivacy)}",
                                    style = Typography.labelMedium.copy(color = TextSecondary)
                                )
                                if (category.rolloverAmount > 0.0) {
                                    Text("Rolled Over: ${moneyString(category.rolloverAmount, isPrivacy)}", style = Typography.labelMedium.copy(color = AccentGreen))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // This screen used to show only the limit, so the envelope list you
                            // open to think about budgets told you less than the home summary did.
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${moneyString(envelopeRemaining, isPrivacy)} left",
                                    style = Typography.titleMedium.copy(
                                        color = if (envelopeRemaining >= 0) TextPrimary else AlertRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    "Spent: ${moneyString(envelopeSpent, isPrivacy)}",
                                    style = Typography.labelMedium.copy(color = TextSecondary)
                                )
                            }
                            
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = TextSecondary)
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Envelope", color = SecondaryTeal) },
                                        onClick = {
                                            menuExpanded = false
                                            categoryToEditLimit = category
                                            editLimitInput = category.budgetLimit.toInt().toString()
                                        },
                                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SecondaryTeal) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Update Rollover", color = AccentGreen) },
                                        onClick = {
                                            menuExpanded = false
                                            categoryToUpdateRollover = category
                                        },
                                        leadingIcon = { Icon(Icons.Default.Autorenew, contentDescription = null, tint = AccentGreen) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete Envelope", color = AlertRed) },
                                        onClick = {
                                            menuExpanded = false
                                            categoryToDelete = category
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AlertRed) }
                                    )
                                }
                            }
                        }
                    }
                    if (envelopeLimit > 0) {
                        LinearProgressIndicator(
                            progress = { envelopeRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                .height(6.dp)
                                .clip(CircleShape),
                            color = when {
                                envelopeRatio >= 1f -> AlertRed
                                envelopeRatio > 0.8f -> WarningAmber
                                else -> AccentGreen
                            },
                            trackColor = SubtleSurface
                        )
                    }
                }
                } // End ReorderableItem
            }
            
            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }

    categoryToDelete?.let { category ->
        DeleteCategoryDialog(
            category = category,
            categories = categories,
            viewModel = viewModel,
            onDismiss = { categoryToDelete = null }
        )
    }

    if (showTransferDialog) {
        var sourceId by remember { mutableStateOf<Long?>(categories.firstOrNull()?.id) }
        var destinationId by remember { mutableStateOf<Long?>(categories.drop(1).firstOrNull()?.id) }
        var amountText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Transfer Between Envelopes", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Source envelope", style = Typography.labelMedium.copy(color = TextSecondary))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 140.dp)) {
                        items(categories, key = { it.id }) { category ->
                            SelectableEnvelopeRow(
                                category = category,
                                selected = sourceId == category.id,
                                onClick = {
                                    sourceId = category.id
                                    if (destinationId == category.id) destinationId = null
                                }
                            )
                        }
                    }
                    Text("Destination envelope", style = Typography.labelMedium.copy(color = TextSecondary))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.heightIn(max = 140.dp)) {
                        items(categories.filter { it.id != sourceId }, key = { it.id }) { category ->
                            SelectableEnvelopeRow(
                                category = category,
                                selected = destinationId == category.id,
                                onClick = { destinationId = category.id }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        if (sourceId != null && destinationId != null) {
                            viewModel.transferEnvelopeBudget(sourceId!!, destinationId!!, amount)
                        }
                        showTransferDialog = false
                    }
                ) {
                    Text("Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Modal Edit Category/Envelope Dialog
    categoryToEditLimit?.let { category ->
        var editName by remember { mutableStateOf(category.name) }
        var editLimitInput by remember { mutableStateOf(category.budgetLimit.toString()) }
        var selectedIcon by remember { mutableStateOf(category.iconName) }
        var selectedColor by remember { mutableStateOf(category.colorHex) }
        var isRollover by remember { mutableStateOf(category.isRolloverEnabled) }

        val presetColors = listOf("#4CAF50", "#2196F3", "#F44336", "#FF9800", "#9C27B0", "#E91E63", "#009688")
        val presetIcons = listOf("restaurant", "shopping_bag", "receipt_long", "home", "movie", "trending_up")

        AlertDialog(
            onDismissRequest = { categoryToEditLimit = null },
            title = { Text("Edit Envelope: ${category.name}", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Envelope Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editLimitInput,
                        onValueChange = { editLimitInput = it },
                        label = { Text("Monthly Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Budget Rollover", style = Typography.bodyMedium.copy(color = TextSecondary))
                        Switch(
                            checked = isRollover,
                            onCheckedChange = { isRollover = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = SecondaryTeal)
                        )
                    }

                    Text("Theme Color", style = Typography.labelMedium.copy(color = TextSecondary))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presetColors.forEach { hex ->
                            val isSelected = selectedColor == hex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .clickable { selectedColor = hex }
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Text("Icon Symbol", style = Typography.labelMedium.copy(color = TextSecondary))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presetIcons.forEach { iconKey ->
                            val isSelected = selectedIcon == iconKey
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { selectedIcon = iconKey },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(iconKey),
                                    contentDescription = iconKey,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        val amount = editLimitInput.toDoubleOrNull() ?: 0.0
                        val updated = category.copy(
                            name = editName,
                            budgetLimit = amount,
                            iconName = selectedIcon,
                            colorHex = selectedColor,
                            isRolloverEnabled = isRollover
                        )
                        viewModel.updateCategory(updated)
                        categoryToEditLimit = null
                    },
                    variant = iOSButtonVariant.Accent,
                    accentColor = MaterialTheme.colorScheme.secondary
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToEditLimit = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    categoryToUpdateRollover?.let { category ->
        var rolloverInput by remember { mutableStateOf(category.rolloverAmount.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { categoryToUpdateRollover = null },
            title = { Text("Update Rollover Funds", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add or update extra rollover funds carried into '${category.name}'.", style = Typography.bodySmall.copy(color = TextSecondary))
                    OutlinedTextField(
                        value = rolloverInput,
                        onValueChange = { rolloverInput = it },
                        label = { Text("Rollover Funds (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        val valInput = rolloverInput.toDoubleOrNull() ?: 0.0
                        viewModel.updateCategory(category.copy(rolloverAmount = valInput))
                        categoryToUpdateRollover = null
                    },
                    variant = iOSButtonVariant.Accent,
                    accentColor = AccentGreen
                ) {
                    Text("Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToUpdateRollover = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Modal Create Category Dialog
    if (showAddCategoryDialog) {
        var categoryName by remember { mutableStateOf("") }
        var categoryLimit by remember { mutableStateOf("") }
        var selectedIcon by remember { mutableStateOf("shopping_bag") }
        var selectedColor by remember { mutableStateOf("#4CAF50") }
        var isRollover by remember { mutableStateOf(false) }

        val presetColors = listOf("#4CAF50", "#2196F3", "#F44336", "#FF9800", "#9C27B0", "#E91E63", "#009688")
        val presetIcons = listOf("restaurant", "shopping_bag", "receipt_long", "home", "movie", "trending_up")

        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("Create Budget Envelope", style = Typography.titleLarge) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = { categoryName = it },
                        label = { Text("Envelope Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = categoryLimit,
                        onValueChange = { categoryLimit = it },
                        label = { Text("Monthly Budget Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Budget Rollover", style = Typography.bodyMedium.copy(color = TextSecondary))
                        Switch(
                            checked = isRollover,
                            onCheckedChange = { isRollover = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryViolet)
                        )
                    }

                    Text("Pick Icon", style = Typography.labelMedium.copy(color = TextSecondary))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presetIcons.forEach { iconName ->
                            val isSelected = selectedIcon == iconName
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    .clickable { selectedIcon = iconName },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(iconName),
                                    contentDescription = iconName,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Text("Pick Color", style = Typography.labelMedium.copy(color = TextSecondary))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presetColors.forEach { colorHex ->
                            val isSelected = selectedColor == colorHex
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorHex)))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorHex }
                             )
                        }
                    }
                }
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        if (categoryName.isNotEmpty()) {
                            val limit = categoryLimit.toDoubleOrNull() ?: 0.0
                            viewModel.createCategory(
                                name = categoryName,
                                iconName = selectedIcon,
                                colorHex = selectedColor,
                                budgetLimit = limit,
                                isRollover = isRollover
                            )
                            showAddCategoryDialog = false
                        }
                    },
                    variant = iOSButtonVariant.Accent,
                    accentColor = MaterialTheme.colorScheme.primary
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun SelectableEnvelopeRow(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) PrimaryViolet.copy(alpha = 0.18f) else DarkSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(category.name, style = Typography.bodyMedium.copy(color = TextPrimary))
            Text(moneyString(category.budgetLimit), style = Typography.labelMedium.copy(color = TextSecondary))
        }
    }
}
