package com.example.financemanager.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.financemanager.data.ExpenseGroup
import com.example.financemanager.data.ExpenseGroupExpense
import com.example.financemanager.data.ExpenseGroupMember
import com.example.financemanager.domain.SettlementTransfer
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSCardStyle
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupExpensesContent(viewModel: FinanceViewModel, modifier: Modifier = Modifier) {
    val groups by viewModel.expenseGroups.collectAsState()
    var selectedGroupId by remember { mutableLongStateOf(0L) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }
    var showAddExpense by remember { mutableStateOf(false) }
    var selectedQr by remember { mutableStateOf<SettlementTransfer?>(null) }

    LaunchedEffect(groups) {
        if (selectedGroupId == 0L || groups.none { it.id == selectedGroupId }) {
            selectedGroupId = groups.firstOrNull()?.id ?: 0L
        }
    }

    val selectedGroup = groups.firstOrNull { it.id == selectedGroupId }
    val members by if (selectedGroup != null) {
        viewModel.expenseGroupMembers(selectedGroup.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val expenses by if (selectedGroup != null) {
        viewModel.expenseGroupExpenses(selectedGroup.id).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList()) }
    }
    val settlements = viewModel.calculateGroupSettlements(members, expenses)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (groups.isNotEmpty()) {
                ScrollableTabRow(
                    selectedTabIndex = groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    divider = {},
                    modifier = Modifier.weight(1f)
                ) {
                    groups.forEachIndexed { index, group ->
                        Tab(
                            selected = index == groups.indexOfFirst { it.id == selectedGroupId },
                            onClick = { selectedGroupId = group.id },
                            text = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            } else {
                Text("Your shared bills", style = Typography.titleMedium.copy(color = TextPrimary))
            }
            IconButton(onClick = { showCreateGroup = true }) {
                Icon(Icons.Default.Add, contentDescription = "Create group", tint = SecondaryTeal)
            }
        }

        if (selectedGroup == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Group, null, tint = SecondaryTeal, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Create a group for a trip or shared home", color = TextPrimary)
                    Spacer(Modifier.height(16.dp))
                    iOSButton(onClick = { showCreateGroup = true }, accentColor = SecondaryTeal) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Create group")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    iOSCard(style = iOSCardStyle.Elevated, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(selectedGroup.name, style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                                    Text("${members.size} people  •  ${expenses.size} receipts", color = TextSecondary, fontSize = 13.sp)
                                }
                                Text(moneyString(expenses.sumOf { it.amount }), style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                iOSButton(
                                    onClick = { showAddMember = true },
                                    variant = iOSButtonVariant.Tinted,
                                    accentColor = SecondaryTeal,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Add person") }
                                iOSButton(
                                    onClick = { showAddExpense = true },
                                    variant = iOSButtonVariant.Accent,
                                    accentColor = PrimaryViolet,
                                    enabled = members.size >= 2,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Add receipt") }
                            }
                        }
                    }
                }

                item {
                    Text("Settle up", style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
                if (settlements.isEmpty()) {
                    item {
                        iOSCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (expenses.isEmpty()) "Add a receipt to calculate who owes whom."
                                else "Everyone is settled up.",
                                modifier = Modifier.padding(16.dp), color = TextSecondary
                            )
                        }
                    }
                } else {
                    items(settlements, key = { "settlement_${it.fromMemberId}_${it.toMemberId}" }) { transfer ->
                        val from = members.firstOrNull { it.id == transfer.fromMemberId }
                        val to = members.firstOrNull { it.id == transfer.toMemberId }
                        if (from != null && to != null) {
                            iOSCard(modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${from.name} pays ${to.name}", style = Typography.bodyLarge.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                                        Text(moneyString(transfer.amount), style = Typography.titleMedium.copy(color = AlertRed, fontWeight = FontWeight.Bold))
                                    }
                                    if (to.upiId.isNotBlank()) {
                                        IconButton(onClick = { selectedQr = transfer }) {
                                            Icon(Icons.Default.QrCode2, contentDescription = "Show UPI QR", tint = SecondaryTeal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Receipts", style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold))
                }
                if (expenses.isEmpty()) {
                    item { Text("No receipts logged yet.", color = TextSecondary) }
                } else {
                    items(expenses, key = { it.id }) { expense ->
                        val payer = members.firstOrNull { it.id == expense.paidByMemberId }?.name ?: "Unknown"
                        iOSCard(modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ReceiptLong, null, tint = SecondaryTeal)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(expense.description, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Paid by $payer  •  ${expense.participantMemberIds.split(',').size} people", color = TextSecondary, fontSize = 12.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(moneyString(expense.amount), color = TextPrimary, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.deleteExpenseGroupExpense(expense) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, "Delete receipt", tint = AlertRed, modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateGroup) {
        CreateGroupDialog(
            onDismiss = { showCreateGroup = false },
            onSave = { name, user, upi -> viewModel.createExpenseGroup(name, user, upi); showCreateGroup = false }
        )
    }
    if (showAddMember && selectedGroup != null) {
        AddMemberDialog(
            onDismiss = { showAddMember = false },
            onSave = { name, upi -> viewModel.addExpenseGroupMember(selectedGroup.id, name, upi); showAddMember = false }
        )
    }
    if (showAddExpense && selectedGroup != null) {
        AddGroupExpenseDialog(
            members = members,
            onDismiss = { showAddExpense = false },
            onSave = { description, amount, payer, participants ->
                viewModel.addExpenseGroupExpense(selectedGroup.id, description, amount, payer, participants)
                showAddExpense = false
            }
        )
    }
    selectedQr?.let { transfer ->
        val recipient = members.firstOrNull { it.id == transfer.toMemberId }
        if (recipient != null) {
            UpiQrDialog(
                recipient = recipient,
                amount = transfer.amount,
                onDismiss = { selectedQr = null }
            )
        }
    }
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("You") }
    var upiId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create group") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true)
            OutlinedTextField(userName, { userName = it }, label = { Text("Your name") }, singleLine = true)
            OutlinedTextField(upiId, { upiId = it }, label = { Text("Your UPI ID (optional)") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank() && userName.isNotBlank()) onSave(name, userName, upiId) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddMemberDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add person") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            OutlinedTextField(upiId, { upiId = it }, label = { Text("UPI ID (optional)") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onSave(name, upiId) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGroupExpenseDialog(
    members: List<ExpenseGroupMember>,
    onDismiss: () -> Unit,
    onSave: (String, Double, Long, List<Long>) -> Unit
) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var payerId by remember { mutableLongStateOf(members.firstOrNull()?.id ?: 0L) }
    var selectedIds by remember(members) { mutableStateOf(members.map { it.id }.toSet()) }
    var payerExpanded by remember { mutableStateOf(false) }
    val parsedAmount = amount.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add group receipt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("What was it?") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (INR)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                ExposedDropdownMenuBox(expanded = payerExpanded, onExpandedChange = { payerExpanded = !payerExpanded }) {
                    OutlinedTextField(
                        value = members.firstOrNull { it.id == payerId }?.name ?: "Select payer",
                        onValueChange = {}, readOnly = true, label = { Text("Paid by") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(payerExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = payerExpanded, onDismissRequest = { payerExpanded = false }) {
                        members.forEach { member ->
                            DropdownMenuItem(text = { Text(member.name) }, onClick = { payerId = member.id; payerExpanded = false })
                        }
                    }
                }
                Text("Split between", style = Typography.labelMedium.copy(color = TextSecondary))
                members.forEach { member ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        selectedIds = if (member.id in selectedIds) selectedIds - member.id else selectedIds + member.id
                    }) {
                        Checkbox(checked = member.id in selectedIds, onCheckedChange = { checked ->
                            selectedIds = if (checked) selectedIds + member.id else selectedIds - member.id
                        })
                        Text(member.name, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (description.isNotBlank() && parsedAmount != null && parsedAmount > 0 && payerId > 0 && selectedIds.isNotEmpty()) onSave(description, parsedAmount, payerId, selectedIds.toList()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UpiQrDialog(recipient: ExpenseGroupMember, amount: Double, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val upiUri = "upi://pay?pa=${encode(recipient.upiId)}&pn=${encode(recipient.name)}&am=${"%.2f".format(Locale.US, amount)}&cu=INR"
    val bitmap = remember(upiUri) {
        val matrix = QRCodeWriter().encode(upiUri, BarcodeFormat.QR_CODE, 720, 720)
        android.graphics.Bitmap.createBitmap(720, 720, android.graphics.Bitmap.Config.RGB_565).also { image ->
            for (x in 0 until 720) for (y in 0 until 720) image.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay ${recipient.name}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.Image(bitmap.asImageBitmap(), contentDescription = "UPI payment QR code", modifier = Modifier.size(240.dp))
                Spacer(Modifier.height(8.dp))
                Text("Scan to pay ${moneyString(amount)}", color = TextSecondary)
                Text(recipient.upiId, color = TextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Pay ${recipient.name} ${moneyString(amount)} via UPI: $upiUri")
                }
                context.startActivity(Intent.createChooser(share, "Share UPI payment"))
            }) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(4.dp))
                Text("Share UPI")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
