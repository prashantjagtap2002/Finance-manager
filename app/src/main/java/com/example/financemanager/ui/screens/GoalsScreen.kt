package com.example.financemanager.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.example.financemanager.data.SavingsGoal
import com.example.financemanager.theme.*
import com.example.financemanager.theme.GoldAccent
import com.example.financemanager.ui.components.iOSCard
import com.example.financemanager.ui.components.iOSCardStyle
import com.example.financemanager.ui.components.iOSButton
import com.example.financemanager.ui.components.iOSButtonVariant
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun getGoalIcon(iconName: String): ImageVector = when (iconName) {
    "savings" -> Icons.Default.Savings
    "flight" -> Icons.Default.Flight
    "home" -> Icons.Default.Home
    "phone" -> Icons.Default.PhoneAndroid
    "car" -> Icons.Default.DirectionsCar
    "school" -> Icons.Default.School
    "favorite" -> Icons.Default.Favorite
    "shield" -> Icons.Default.Shield
    else -> Icons.Default.Savings
}

private val goalIconOptions = listOf("savings", "shield", "flight", "home", "phone", "car", "school", "favorite")
private val goalColorOptions = listOf("#10B981", "#3B82F6", "#F59E0B", "#EC4899", "#8B5CF6", "#EF4444")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val goals by viewModel.savingsGoals.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val isPrivacy by viewModel.isPrivacyMode.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var contributingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var deletingGoal by remember { mutableStateOf<SavingsGoal?>(null) }
    var celebratingGoalId by remember { mutableStateOf<Long?>(null) }

    val totalSaved = goals.sumOf { it.savedAmount }
    val totalTarget = goals.sumOf { it.targetAmount }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Savings Goals", style = Typography.titleLarge.copy(color = TextPrimary)) },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Goal", tint = AccentGreen)
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Summary card
            if (goals.isNotEmpty()) {
                item {
                    iOSCard(modifier = Modifier.fillMaxWidth(), style = iOSCardStyle.Grouped) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TOTAL SAVED", style = Typography.labelMedium.copy(color = TextSecondary))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    moneyString(totalSaved, isPrivacy),
                                    style = Typography.headlineMedium.copy(color = AccentGreen, fontWeight = FontWeight.Bold)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("of ${moneyString(totalTarget, isPrivacy)}", style = Typography.titleMedium.copy(color = TextPrimary))
                                Text(
                                    "across ${goals.size} goal${if (goals.size > 1) "s" else ""}",
                                    style = Typography.labelMedium.copy(color = TextSecondary)
                                )
                            }
                        }
                    }
                }
            }

            if (goals.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(DarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Savings,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "No goals yet",
                            style = Typography.titleLarge.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Save toward a trip, an emergency fund,\nor that new phone — one goal at a time.",
                            style = Typography.bodyMedium.copy(color = TextSecondary),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        iOSButton(
                            onClick = { showAddDialog = true },
                            variant = iOSButtonVariant.Accent,
                            accentColor = AccentGreen,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create your first goal", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(goals, key = { "goal-${it.id}" }) { goal ->
                GoalCard(
                    goal = goal,
                    privacy = isPrivacy,
                    onContribute = { contributingGoal = goal },
                    onDelete = { deletingGoal = goal },
                    onCelebrate = { celebratingGoalId = goal.id },
                    modifier = Modifier.animateItem()
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        }

        celebratingGoalId?.let {
            GoalConfettiOverlay(
                modifier = Modifier.fillMaxSize(),
                onFinished = { celebratingGoalId = null }
            )
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, target, targetDate, icon, color ->
                viewModel.createSavingsGoal(name, target, targetDate, icon, color)
                showAddDialog = false
            }
        )
    }

    contributingGoal?.let { goal ->
        ContributeDialog(
            goal = goal,
            accounts = accounts,
            onDismiss = { contributingGoal = null },
            onConfirm = { amount, accountId ->
                viewModel.contributeToGoal(goal, amount, accountId)
                contributingGoal = null
            }
        )
    }

    deletingGoal?.let { goal ->
        AlertDialog(
            onDismissRequest = { deletingGoal = null },
            title = { Text("Delete goal?", style = Typography.titleLarge.copy(color = TextPrimary)) },
            text = {
                Text(
                    "\"${goal.name}\" and its saved progress record will be removed. Money already in your accounts is not affected.",
                    style = Typography.bodyMedium.copy(color = TextSecondary)
                )
            },
            confirmButton = {
                iOSButton(
                    onClick = {
                        viewModel.deleteSavingsGoal(goal)
                        deletingGoal = null
                    },
                    variant = iOSButtonVariant.Accent,
                    accentColor = AlertRed
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingGoal = null }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun GoalProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 10f
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "goalProgress"
    )
    val isComplete = progress >= 1f
    val infiniteTransition = rememberInfiniteTransition(label = "glowTransition")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Canvas(modifier = modifier) {
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        
        if (isComplete) {
            drawCircle(
                color = color.copy(alpha = glowAlpha),
                radius = size.width / 2,
                center = Offset(size.width / 2, size.height / 2)
            )
        }
        
        // Track
        drawArc(
            color = color.copy(alpha = 0.15f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke
        )
        // Progress
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = animatedProgress * 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke
        )
    }
}

@Composable
private fun GoalCard(
    goal: SavingsGoal,
    privacy: Boolean,
    onContribute: () -> Unit,
    onDelete: () -> Unit,
    onCelebrate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = try {
        Color(android.graphics.Color.parseColor(goal.colorHex))
    } catch (e: Exception) {
        AccentGreen
    }
    val progress = if (goal.targetAmount > 0) (goal.savedAmount / goal.targetAmount).toFloat() else 0f
    val isComplete = progress >= 1f

    val cardModifier = if (isComplete) {
        modifier.fillMaxWidth().border(2.dp, GoldAccent, RoundedCornerShape(16.dp))
    } else {
        modifier.fillMaxWidth()
    }

    iOSCard(
        modifier = cardModifier.clickable(enabled = isComplete, onClick = onCelebrate),
        style = iOSCardStyle.Grouped
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    GoalProgressRing(
                        progress = progress,
                        color = color,
                        modifier = Modifier.size(64.dp)
                    )
                    Icon(
                        imageVector = getGoalIcon(goal.iconName),
                        contentDescription = goal.name,
                        tint = color,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            goal.name,
                            style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isComplete) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Verified, contentDescription = "Complete", tint = AccentGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "${moneyString(goal.savedAmount, privacy)} of ${moneyString(goal.targetAmount, privacy)}",
                        style = Typography.bodyMedium.copy(color = TextSecondary)
                    )
                    goal.targetDate?.let { date ->
                        val formatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(date))
                        val daysLeft = ((date - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).toInt()
                        Text(
                            if (daysLeft >= 0) "By $formatted • $daysLeft days left" else "Was due $formatted",
                            style = Typography.labelSmall.copy(color = if (daysLeft in 0..14) WarningAmber else TextMuted)
                        )
                    }
                }
                Text(
                    "${(progress * 100).toInt()}%",
                    style = Typography.titleLarge.copy(color = color, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                iOSButton(
                    onClick = onContribute,
                    enabled = !isComplete,
                    modifier = Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(10.dp)),
                    variant = iOSButtonVariant.Accent,
                    accentColor = color.copy(alpha = 0.18f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isComplete) "Goal achieved 🎉" else "Add money", fontWeight = FontWeight.SemiBold)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete goal", tint = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun GoalConfettiOverlay(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit
) {
    val colors = listOf(AccentGreen, GoldAccent, PrimaryViolet, SecondaryTeal, WarningAmber)
    val particles = remember {
        List(48) { index ->
            Triple((index * 37 % 100) / 100f, (index * 17 % 100) / 100f, colors[index % colors.size])
        }
    }
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(2200),
        label = "confettiProgress"
    )

    LaunchedEffect(Unit) {
        delay(2300)
        onFinished()
    }

    Canvas(modifier = modifier) {
        particles.forEachIndexed { index, (xSeed, ySeed, color) ->
            val x = size.width * xSeed
            val drop = ((progress + index * 0.03f) % 1f) * size.height
            val width = 8.dp.toPx()
            val height = 14.dp.toPx()
            drawRect(
                color = color.copy(alpha = 1f - progress.coerceAtMost(0.85f)),
                topLeft = Offset(x, drop + size.height * ySeed * 0.1f),
                size = Size(width, height)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGoalDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, target: Double, targetDate: Long?, icon: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetStr by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(goalIconOptions.first()) }
    var selectedColor by remember { mutableStateOf(goalColorOptions.first()) }
    var targetDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Savings Goal", style = Typography.titleLarge.copy(color = TextPrimary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Goal name (e.g. Goa Trip)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetStr,
                    onValueChange = { targetStr = it },
                    label = { Text("Target amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Icon", style = Typography.labelMedium.copy(color = TextSecondary))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(goalIconOptions) { icon ->
                        val isSelected = selectedIcon == icon
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(android.graphics.Color.parseColor(selectedColor)).copy(alpha = 0.25f) else DarkSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) Color(android.graphics.Color.parseColor(selectedColor)) else BorderColor,
                                    CircleShape
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                getGoalIcon(icon),
                                contentDescription = icon,
                                tint = if (isSelected) Color(android.graphics.Color.parseColor(selectedColor)) else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Text("Color", style = Typography.labelMedium.copy(color = TextSecondary))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    goalColorOptions.forEach { hex ->
                        val c = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (selectedColor == hex) 3.dp else 0.dp,
                                    color = TextPrimary,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        targetDate?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it)) }
                            ?: "Target date (optional)",
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            iOSButton(
                onClick = {
                    val target = targetStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && target > 0) {
                        onConfirm(name.trim(), target, targetDate, selectedIcon, selectedColor)
                    }
                },
                enabled = name.isNotBlank() && (targetStr.toDoubleOrNull() ?: 0.0) > 0,
                variant = iOSButtonVariant.Accent,
                accentColor = AccentGreen
            ) { Text("Create", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    targetDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun ContributeDialog(
    goal: SavingsGoal,
    accounts: List<com.example.financemanager.data.Account>,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, accountId: Long?) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    val remaining = (goal.targetAmount - goal.savedAmount).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to \"${goal.name}\"", style = Typography.titleLarge.copy(color = TextPrimary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount — ${moneyString(remaining)} to go") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Deduct from account (optional — logs a transfer)",
                    style = Typography.labelMedium.copy(color = TextSecondary)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(accounts, key = { it.id }) { account ->
                        val isSelected = selectedAccountId == account.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) AccentGreen else DarkSurface)
                                .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                                .clickable {
                                    selectedAccountId = if (isSelected) null else account.id
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                account.name,
                                style = Typography.labelMedium.copy(color = if (isSelected) TextPrimary else TextPrimary)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            iOSButton(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    if (amount > 0) onConfirm(amount, selectedAccountId)
                },
                enabled = (amountStr.toDoubleOrNull() ?: 0.0) > 0,
                variant = iOSButtonVariant.Accent,
                accentColor = AccentGreen
            ) { Text("Add", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )
}
