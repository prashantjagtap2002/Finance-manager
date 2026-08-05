package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.financemanager.data.Transaction
import com.example.financemanager.theme.AlertRed
import com.example.financemanager.theme.DarkSurface
import com.example.financemanager.theme.DeepBackground
import com.example.financemanager.theme.PrimaryViolet
import com.example.financemanager.theme.TextPrimary
import com.example.financemanager.theme.TextSecondary
import com.example.financemanager.theme.Typography
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsByCategoryScreen(
    categoryId: Long,
    year: String,
    month: String,
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val allTransactions by viewModel.transactions.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val startPickerState = rememberDatePickerState()
    val endPickerState = rememberDatePickerState()
    
    var filterStartDate by remember { mutableStateOf<Long?>(null) }
    var filterEndDate by remember { mutableStateOf<Long?>(null) }

    val transactions = remember(allTransactions, searchQuery, filterStartDate, filterEndDate, categoryId, year, month) {
        allTransactions.filter { t ->
            var match = t.categoryId == categoryId
            
            if (searchQuery.isNotBlank()) {
                val searchStr = searchQuery.lowercase()
                match = match && ((t.merchantName?.lowercase()?.contains(searchStr) == true) || 
                                 t.note.lowercase().contains(searchStr))
            }
            
            if (filterStartDate != null && filterEndDate != null) {
                // inclusive date range
                match = match && t.date >= filterStartDate!! && t.date <= (filterEndDate!! + 86400000L)
            } else {
                // Default year/month filter if no custom range is set
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = t.date }
                val tYear = cal.get(java.util.Calendar.YEAR).toString()
                val tMonth = cal.get(java.util.Calendar.MONTH).toString()
                match = match && tYear == year && tMonth == month
            }
            match
        }.sortedByDescending { it.date }
    }
    val categories by viewModel.categories.collectAsState()
    
    val category = categories.firstOrNull { it.id == categoryId }
    val categoryName = category?.name ?: "Category"
    val categoryColor = category?.colorHex?.let { Color(android.graphics.Color.parseColor(it)) } ?: Color.Gray

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(categoryName, style = Typography.titleLarge.copy(color = TextPrimary))
                        val monthInt = month.toIntOrNull() ?: 0
                        val monthName = java.text.DateFormatSymbols().months[monthInt]
                        Text("$monthName $year", style = Typography.bodyMedium.copy(color = TextSecondary))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    if (filterStartDate != null) {
                        IconButton(onClick = { filterStartDate = null; filterEndDate = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Filter", tint = AlertRed)
                        }
                    }
                    IconButton(onClick = { showStartPicker = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter by Date", tint = PrimaryViolet)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        containerColor = DeepBackground
    ) { paddingValues ->
        // Start Date Picker
        if (showStartPicker) {
            DatePickerDialog(
                onDismissRequest = { showStartPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        filterStartDate = startPickerState.selectedDateMillis
                        showStartPicker = false
                        showEndPicker = true // chain end picker
                    }) { Text("Next") }
                },
                dismissButton = {
                    TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = startPickerState, title = { Text("Select Start Date", modifier = Modifier.padding(16.dp)) })
            }
        }
        
        // End Date Picker
        if (showEndPicker) {
            DatePickerDialog(
                onDismissRequest = { showEndPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        filterEndDate = endPickerState.selectedDateMillis
                        showEndPicker = false
                    }) { Text("Apply Filter") }
                },
                dismissButton = {
                    TextButton(onClick = { showEndPicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = endPickerState, title = { Text("Select End Date", modifier = Modifier.padding(16.dp)) })
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search transactions...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryViolet,
                    unfocusedBorderColor = DarkSurface
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transactions found.",
                        style = Typography.bodyMedium.copy(color = TextSecondary)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                
                items(transactions) { transaction ->
                    TransactionCategoryItem(transaction = transaction, categoryColor = categoryColor)
                }
                
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
fun TransactionCategoryItem(transaction: Transaction, categoryColor: Color) {
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    
    iOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Color indicator dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(categoryColor)
                )
                
                Column {
                    Text(
                        text = transaction.merchantName?.takeIf { it.isNotBlank() } ?: transaction.note,
                        style = Typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        maxLines = 1
                    )
                    Text(
                        text = dateFormatter.format(Date(transaction.date)),
                        style = Typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            
            Text(
                text = moneyString(transaction.amount, false),
                style = Typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}
