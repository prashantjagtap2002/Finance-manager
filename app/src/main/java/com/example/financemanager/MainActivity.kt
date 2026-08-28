package com.example.financemanager

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.financemanager.core.AppLock
import com.example.financemanager.core.FinancePreferences
import com.example.financemanager.services.NotificationHelper
import com.example.financemanager.theme.FinanceManagerTheme
import com.example.financemanager.ui.screens.AppLockScreen
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

    /** Set while the biometric sheet is up, so onStart doesn't stack a second prompt on it. */
    private var biometricPromptShowing = false

    private val smsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional feature — no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()

        FinancePreferences.init(this)
        AppLock.init(this)
        val prefs = getSharedPreferences("finance_prefs", Context.MODE_PRIVATE)

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
                    biometricPromptShowing = false
                    // A cancel is not a failure worth shouting about — the PIN pad is right there.
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        Toast.makeText(applicationContext, "Authentication error: $errString", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricPromptShowing = false
                    AppLock.unlock()
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

        // Unlocking is driven from onStart so that returning from the background re-authenticates
        // too, not just a cold launch.

        val openQuickEntry = intent.getBooleanExtra("open_quick_entry", false)

        setContent {
            val themeMode by FinancePreferences.themeModeFlow.collectAsState()
            val financeViewModel: com.example.financemanager.ui.viewmodel.FinanceViewModel = viewModel()

            FinanceManagerTheme(themeMode = themeMode) {
                // Keep balances out of the recents thumbnail and out of screenshots while the
                // lock is armed. Reading the snapshot state here re-runs this when it's toggled.
                val lockArmed = AppLock.isEnabled
                LaunchedEffect(lockArmed) { applySecureFlag(lockArmed) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                        AppLock.isLocked -> AppLockScreen(
                            canUseBiometrics = canAuthenticateWithBiometrics(),
                            onBiometricRequest = { triggerBiometricUnlock() }
                        )
                        else -> MainNavigation(
                            viewModel = financeViewModel,
                            openQuickEntry = openQuickEntry
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // This app is single-Activity, so its own start/stop is the app's foreground/background.
        AppLock.onEnterForeground()
        if (AppLock.isLocked) promptForUnlock()
    }

    override fun onStop() {
        super.onStop()
        AppLock.onEnterBackground()
    }

    /**
     * Decides how the user gets back in. Biometrics are offered first when the device has them;
     * otherwise the PIN pad on [AppLockScreen] takes over. With neither available there is nothing
     * to authenticate against, so stay out of the user's way rather than locking them out for good.
     */
    private fun promptForUnlock() {
        when {
            canAuthenticateWithBiometrics() -> triggerBiometricUnlock()
            AppLock.hasPin -> Unit // The lock screen collects the PIN.
            else -> AppLock.unlock()
        }
    }

    private fun canAuthenticateWithBiometrics(): Boolean {
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        return canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun applySecureFlag(secure: Boolean) {
        if (secure) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** Let Android schedule this activity on the smoothest mode the device offers. */
    private fun requestHighRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val preferredRate = display?.supportedModes
            ?.map { it.refreshRate }
            ?.filter { it >= 120f }
            ?.maxOrNull()
            ?: return

        // This is a hint; Android may still lower it for battery or thermal policy.
        window.attributes = window.attributes.apply {
            preferredRefreshRate = preferredRate
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
        if (!canAuthenticateWithBiometrics()) {
            // No biometric or device credential is enrolled. A PIN is the only thing left that can
            // protect the data; without one there is nothing to authenticate against, so unlock
            // rather than stranding the user with no way in.
            if (!AppLock.hasPin) AppLock.unlock()
            return
        }
        if (biometricPromptShowing) return
        try {
            biometricPromptShowing = true
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            // Do NOT unlock here: authenticate() throwing is not proof the device has no lock
            // configured (that case is already handled above). Fail closed and let the user
            // retry via the lock screen.
            biometricPromptShowing = false
            Toast.makeText(applicationContext, "Unable to start authentication: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
