package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financemanager.data.Category
import com.example.financemanager.theme.*
import com.example.financemanager.ui.screens.TransactionItem
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accounts.collectAsState()

    // Real-time search query observation
    val searchResults by produceState(
        initialValue = emptyList<com.example.financemanager.data.Transaction>(),
        key1 = searchQuery,
        key2 = selectedCategoryId
    ) {
        viewModel.searchTransactions(searchQuery).map { txs ->
            if (selectedCategoryId != null) {
                txs.filter { it.categoryId == selectedCategoryId }
            } else {
                txs
            }
        }.collect { value = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search Transactions", style = Typography.titleLarge.copy(color = TextPrimary)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search merchant or note...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryViolet,
                    unfocusedBorderColor = BorderColor,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Category Filters
            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text("All", color = if (selectedCategoryId == null) DeepBackground else TextPrimary) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryViolet,
                            containerColor = DarkSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BorderColor,
                            enabled = true, selected = selectedCategoryId == null
                        )
                    )
                }
                items(categories) { category ->
                    val isSelected = selectedCategoryId == category.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryId = category.id },
                        label = { Text(category.name, color = if (isSelected) DeepBackground else TextPrimary) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(android.graphics.Color.parseColor(category.colorHex)),
                            containerColor = DarkSurface
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = BorderColor,
                            enabled = true, selected = isSelected
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Results List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searchResults.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No transactions found.", style = Typography.bodyMedium.copy(color = TextSecondary))
                        }
                    }
                } else {
                    items(searchResults, key = { it.id }) { tx ->
                        val cat = categories.firstOrNull { it.id == tx.categoryId }
                        val catName = cat?.name ?: "Unknown"
                        val catColor = cat?.colorHex ?: "#888888"
                        
                        TransactionItem(
                            transaction = tx,
                            accountName = accounts.firstOrNull { it.id == tx.sourceAccountId }?.name ?: "Unknown",
                            categoryName = catName,
                            privacy = isPrivacy,
                            onEdit = { },
                            onDelete = { }
                        )
                    }
                }
            }
        }
    }
}
