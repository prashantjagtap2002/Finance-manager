package com.example.financemanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.services.NotificationHelper
import com.example.financemanager.theme.FinanceManagerTheme
import com.example.financemanager.ui.screens.OnboardingScreen
import java.util.concurrent.Executor
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.financemanager.services.RecurringTransactionWorker
import com.example.financemanager.services.SyncWorker
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    private val isUnlockedState = mutableStateOf(false)
    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional feature — no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FinancePreferences.init(this)
        val prefs = getSharedPreferences("finance_prefs", Context.MODE_PRIVATE)
        val appLockEnabled = prefs.getBoolean("app_lock_enabled", true)

        NotificationHelper.ensureChannels(this)
        requestSmsPermissionIfNeeded()
        requestNotificationPermissionIfNeeded()

        // Initialize Auto-Execution Worker
        val workRequest = PeriodicWorkRequestBuilder<RecurringTransactionWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RecurringTxWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        // Initialize Cloud Sync Worker
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CloudSyncWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlockedState.value = true
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Finance Manager Secure Unlock")
            .setSubtitle("Authenticate using your device credentials")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        // Only prompt when the user hasn't disabled app lock in Settings
        if (appLockEnabled) {
            triggerBiometricUnlock()
        } else {
            isUnlockedState.value = true
        }

        val openQuickEntry = intent.getBooleanExtra("open_quick_entry", false)

        setContent {
            val themeMode by FinancePreferences.themeModeFlow.collectAsState()
            val financeViewModel: com.example.financemanager.ui.viewmodel.FinanceViewModel = viewModel()

            FinanceManagerTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isUnlocked by remember { isUnlockedState }
                    var showOnboarding by remember {
                        mutableStateOf(!prefs.getBoolean("onboarding_done", false))
                    }

                    when {
                        showOnboarding -> OnboardingScreen(
                            viewModel = financeViewModel,
                            onFinish = {
                                prefs.edit().putBoolean("onboarding_done", true).apply()
                                showOnboarding = false
                            }
                        )
                        isUnlocked -> MainNavigation(
                            viewModel = financeViewModel,
                            openQuickEntry = openQuickEntry
                        )
                        else -> BiometricLockScreen(onUnlockClick = { triggerBiometricUnlock() })
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestSmsPermissionIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
        }
    }

    private fun triggerBiometricUnlock() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        if (canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
            canAuthenticate == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
            canAuthenticate == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        ) {
            // No biometric/device credential is configured on this device at all, so there is
            // nothing an app-level lock can meaningfully protect against here. Unlock rather
            // than permanently locking the user out with no way to authenticate.
            isUnlockedState.value = true
            return
        }
        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            // Do NOT unlock here: authenticate() throwing is not proof the device has no lock
            // configured (that case is already handled above). Fail closed and let the user
            // retry via the "Unlock App" button.
            Toast.makeText(applicationContext, "Unable to start authentication: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun BiometricLockScreen(onUnlockClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Secured App",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Finance Manager Secured",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please authenticate to access your transactions",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onUnlockClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock App")
        }
    }
}
