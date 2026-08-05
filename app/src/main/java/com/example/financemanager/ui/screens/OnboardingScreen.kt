package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.core.PayCycleFrequency
import com.example.financemanager.theme.AccentGreen
import com.example.financemanager.theme.BorderColor
import com.example.financemanager.theme.DarkSurface
import com.example.financemanager.theme.DeepBackground
import com.example.financemanager.theme.PrimaryViolet
import com.example.financemanager.theme.TextPrimary
import com.example.financemanager.theme.TextSecondary
import com.example.financemanager.theme.Typography
import com.example.financemanager.ui.viewmodel.FinanceViewModel

private data class WizardStep(
    val icon: ImageVector,
    val title: String,
    val body: String
)

private val wizardSteps = listOf(
    WizardStep(
        icon = Icons.Default.Lock,
        title = "Private budgeting, not bank scraping",
        body = "Track envelopes, scan receipts on-device, and stay in control without linking your bank."
    ),
    WizardStep(
        icon = Icons.Default.Wallet,
        title = "Set your payday rhythm",
        body = "Choose the cycle that matches your paycheck so budget pacing follows real life instead of calendar months."
    ),
    WizardStep(
        icon = Icons.Default.AutoAwesome,
        title = "Pick your home currency",
        body = "Every amount across the app will follow the symbol you choose here."
    ),
    WizardStep(
        icon = Icons.Default.Savings,
        title = "Start with envelopes that feel useful",
        body = "Use the default template and trim it down, or start with none and build your own."
    )
)

@Composable
fun OnboardingScreen(
    viewModel: FinanceViewModel,
    onFinish: () -> Unit
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var step by remember { mutableIntStateOf(0) }
    var frequency by remember { mutableStateOf(PayCycleFrequency.MONTHLY) }
    var anchorDayText by remember { mutableStateOf("1") }
    var selectedCurrency by remember { mutableStateOf(FinancePreferences.supportedCurrencies.first().code) }
    val templateSelections = remember {
        mutableStateListOf("Rent", "Groceries", "Transport", "Fun Money", "Savings")
    }

    val currentStep = wizardSteps[step]
    val isLastStep = step == wizardSteps.lastIndex

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeepBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onFinish()
                }) {
                    Text("Skip", style = Typography.labelLarge.copy(color = TextSecondary))
                }
            }

            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(DarkSurface, CircleShape)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = currentStep.icon,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                currentStep.title,
                style = Typography.headlineMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(currentStep.body, style = Typography.bodyLarge.copy(color = TextSecondary))
            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    when (step) {
                        0 -> IntroStep()
                        1 -> PayCycleStep(
                            frequency = frequency,
                            anchorDayText = anchorDayText,
                            onFrequencyChange = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                frequency = it 
                            },
                            onAnchorDayChange = { anchorDayText = it }
                        )
                        2 -> CurrencyStep(
                            selectedCurrency = selectedCurrency,
                            onCurrencySelected = { 
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                selectedCurrency = it 
                            }
                        )
                        else -> TemplateStep(
                            selectedTemplates = templateSelections,
                            onToggle = { name ->
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                if (templateSelections.contains(name)) templateSelections.remove(name)
                                else templateSelections.add(name)
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(wizardSteps.size) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .background(
                                if (index <= step) PrimaryViolet else BorderColor,
                                RoundedCornerShape(8.dp)
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (isLastStep) {
                        val anchorDay = anchorDayText.toIntOrNull()?.coerceIn(1, 28) ?: 1
                        viewModel.setPayCycle(frequency, anchorDay)
                        viewModel.setCurrency(selectedCurrency)
                        viewModel.createTemplateCategories(templateSelections.toList())
                        onFinish()
                    } else {
                        step++
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    if (isLastStep) "Start Budgeting" else "Next",
                    style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun IntroStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureCard("No bank linking", "Manual accounts and envelopes only.")
        FeatureCard("On-device receipt OCR", "Receipts are scanned locally with ML Kit.")
        FeatureCard("Paycheck-synced pacing", "Budget progress will follow your chosen pay cycle.")
    }
}

@Composable
private fun PayCycleStep(
    frequency: PayCycleFrequency,
    anchorDayText: String,
    onFrequencyChange: (PayCycleFrequency) -> Unit,
    onAnchorDayChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PayCycleFrequency.values().forEach { option ->
            SelectRow(
                selected = frequency == option,
                title = option.name.lowercase().replaceFirstChar { it.titlecase() },
                subtitle = when (option) {
                    PayCycleFrequency.WEEKLY -> "Best for weekly paychecks"
                    PayCycleFrequency.BIWEEKLY -> "Every 14 days"
                    PayCycleFrequency.SEMIMONTHLY -> "Twice each month"
                    PayCycleFrequency.MONTHLY -> "Once per month"
                },
                onClick = { onFrequencyChange(option) }
            )
        }
        OutlinedTextField(
            value = anchorDayText,
            onValueChange = { onAnchorDayChange(it.filter { ch -> ch.isDigit() }.take(2)) },
            label = { Text("Anchor day (1-28)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun CurrencyStep(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FinancePreferences.supportedCurrencies.forEach { option ->
            SelectRow(
                selected = selectedCurrency == option.code,
                title = "${option.symbol} ${option.code}",
                subtitle = option.label,
                onClick = { onCurrencySelected(option.code) }
            )
        }
    }
}

@Composable
private fun TemplateStep(
    selectedTemplates: List<String>,
    onToggle: (String) -> Unit
) {
    val options = listOf("Rent", "Groceries", "Transport", "Fun Money", "Savings")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "These start checked so you can move quickly. Uncheck anything you don't want.",
            style = Typography.bodyMedium.copy(color = TextSecondary)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { name ->
                val selected = selectedTemplates.contains(name)
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) PrimaryViolet.copy(alpha = 0.2f) else DarkSurface,
                            RoundedCornerShape(20.dp)
                        )
                        .border(1.dp, if (selected) PrimaryViolet else BorderColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    TextButton(onClick = { onToggle(name) }) {
                        Text(name, color = if (selected) TextPrimary else TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, body: String) {
    Surface(
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = Typography.titleMedium.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
            Spacer(modifier = Modifier.height(4.dp))
            Text(body, style = Typography.bodyMedium.copy(color = TextSecondary))
        }
    }
}

@Composable
private fun SelectRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(title, style = Typography.bodyLarge.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = Typography.labelMedium.copy(color = TextSecondary))
            }
        }
    }
}
