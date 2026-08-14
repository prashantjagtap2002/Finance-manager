package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.financemanager.core.AppLock
import com.example.financemanager.theme.AlertRed
import com.example.financemanager.theme.BorderColor
import com.example.financemanager.theme.PrimaryViolet
import com.example.financemanager.theme.TextPrimary
import com.example.financemanager.theme.TextSecondary
import com.example.financemanager.theme.Typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The screen shown while the app is locked. Offers the PIN pad when a PIN is set and biometrics
 * whenever the device has them; with neither, it explains why nothing is being asked for.
 *
 * Unlocking is [AppLock]'s job — this screen only collects the PIN and reports the outcome.
 */
@Composable
fun AppLockScreen(
    canUseBiometrics: Boolean,
    onBiometricRequest: () -> Unit
) {
    val hasPin = AppLock.hasPin
    var entered by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var cooldownMs by remember { mutableStateOf(AppLock.lockoutRemainingMs()) }

    // Tick the cooldown down so the user can see when entry reopens.
    LaunchedEffect(cooldownMs > 0) {
        while (AppLock.lockoutRemainingMs() > 0) {
            cooldownMs = AppLock.lockoutRemainingMs()
            delay(500)
        }
        cooldownMs = 0
    }

    // Verify as soon as the last digit lands, so there is no "confirm" button to hunt for.
    LaunchedEffect(entered) {
        if (entered.length < AppLock.PIN_LENGTH) return@LaunchedEffect
        verifying = true
        // PIN hashing is deliberately slow; keep it off the main thread.
        val accepted = withContext(Dispatchers.Default) { AppLock.verifyPin(entered) }
        verifying = false
        if (!accepted) {
            entered = ""
            cooldownMs = AppLock.lockoutRemainingMs()
            errorMessage = when {
                cooldownMs > 0 -> "Too many attempts. Try again in ${(cooldownMs / 1000) + 1}s."
                else -> {
                    val left = AppLock.MAX_FAILED_ATTEMPTS - AppLock.failedAttempts()
                    if (left in 1..2) "Incorrect PIN — $left attempt${if (left == 1) "" else "s"} left"
                    else "Incorrect PIN"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Secured app",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Finance Manager Locked",
            style = Typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                hasPin -> "Enter your PIN to continue"
                canUseBiometrics -> "Authenticate to access your transactions"
                else -> "No screen lock or PIN is set up, so there is nothing to authenticate against. " +
                    "Set a PIN in Settings to protect your data."
            },
            style = Typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )

        if (hasPin) {
            Spacer(modifier = Modifier.height(28.dp))
            PinDots(filled = entered.length, error = errorMessage != null)

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage ?: " ",
                style = Typography.labelMedium.copy(color = AlertRed),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
            PinKeypad(
                enabled = !verifying && cooldownMs == 0L,
                showBiometricKey = canUseBiometrics,
                onDigit = { digit ->
                    if (entered.length < AppLock.PIN_LENGTH) {
                        errorMessage = null
                        entered += digit
                    }
                },
                onBackspace = {
                    errorMessage = null
                    entered = entered.dropLast(1)
                },
                onBiometric = onBiometricRequest
            )
        } else if (canUseBiometrics) {
            Spacer(modifier = Modifier.height(40.dp))
            Button(onClick = onBiometricRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Unlock App")
            }
        }
    }
}

/**
 * Collects and confirms a new unlock PIN. Used from Settings, both to set the first PIN and to
 * replace an existing one.
 */
@Composable
fun SetPinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val digitsOnly: (String) -> String = { it.filter(Char::isDigit).take(AppLock.PIN_LENGTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set unlock PIN", style = Typography.titleLarge.copy(color = TextPrimary)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "A ${AppLock.PIN_LENGTH}-digit PIN, used when biometrics are unavailable or dismissed.",
                    style = Typography.labelMedium.copy(color = TextSecondary)
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = digitsOnly(it); error = null },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = digitsOnly(it); error = null },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                error?.let {
                    Text(it, style = Typography.labelMedium.copy(color = AlertRed))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = when {
                    pin.length < AppLock.PIN_LENGTH -> "Enter all ${AppLock.PIN_LENGTH} digits"
                    pin != confirmation -> "The two PINs don't match"
                    else -> null
                }
                if (error == null) onConfirm(pin)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun PinDots(filled: Int, error: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(AppLock.PIN_LENGTH) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            error -> AlertRed
                            isFilled -> PrimaryViolet
                            else -> Color.Transparent
                        }
                    )
                    .border(1.5.dp, if (error) AlertRed else BorderColor, CircleShape)
            )
        }
    }
}

@Composable
private fun PinKeypad(
    enabled: Boolean,
    showBiometricKey: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { label ->
                    KeypadKey(enabled = enabled, onClick = { onDigit(label[0]) }) {
                        Text(label, style = Typography.headlineSmall.copy(color = TextPrimary))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (showBiometricKey) {
                KeypadKey(enabled = enabled, filled = false, onClick = onBiometric) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "Unlock with biometrics",
                        tint = PrimaryViolet
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(72.dp))
            }
            KeypadKey(enabled = enabled, onClick = { onDigit('0') }) {
                Text("0", style = Typography.headlineSmall.copy(color = TextPrimary))
            }
            KeypadKey(enabled = enabled, filled = false, onClick = onBackspace) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete last digit",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(
    enabled: Boolean,
    filled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
