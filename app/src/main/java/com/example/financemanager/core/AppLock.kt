package com.example.financemanager.core

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.mutableStateOf
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Owns everything about the app lock: whether it is on, the fallback PIN, how long the app may sit
 * in the background before it re-locks, and the current locked/unlocked state.
 *
 * Lock state lives here rather than in the Activity so that backgrounding the app can re-lock it,
 * and it is Compose snapshot state so the UI swaps to the lock screen the moment it flips.
 */
object AppLock {

    private const val PREFS_NAME = "finance_prefs"
    private const val KEY_ENABLED = "app_lock_enabled"
    private const val KEY_PIN_HASH = "app_lock_pin_hash"
    private const val KEY_PIN_SALT = "app_lock_pin_salt"
    private const val KEY_PIN_ALGORITHM = "app_lock_pin_algorithm"
    private const val KEY_TIMEOUT_MS = "app_lock_timeout_ms"
    private const val KEY_FAILED_ATTEMPTS = "app_lock_failed_attempts"
    private const val KEY_LOCKED_OUT_UNTIL = "app_lock_locked_out_until"
    private const val KEY_ONBOARDING_DONE = "onboarding_done"

    /** PIN length the UI collects and enforces. */
    const val PIN_LENGTH = 4

    /** Wrong PINs tolerated before entry is thrown into a cooldown. */
    const val MAX_FAILED_ATTEMPTS = 5

    /**
     * How long the app may spend in the background before it re-locks. A short grace period keeps
     * the lock screen from firing every time the user steps out to the camera, a file picker or the
     * share sheet, which are all separate apps as far as the OS is concerned.
     */
    val timeoutOptions = listOf(
        0L to "Immediately",
        30_000L to "After 30 seconds",
        60_000L to "After 1 minute",
        300_000L to "After 5 minutes"
    )

    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private const val PBKDF2_ITERATIONS = 120_000
    private const val PBKDF2_KEY_BITS = 256
    private const val ALGORITHM_SHA256 = "PBKDF2WithHmacSHA256"
    private const val ALGORITHM_SHA1 = "PBKDF2WithHmacSHA1"

    private lateinit var appContext: Context
    private var initialized = false

    private val _isLocked = mutableStateOf(false)
    val isLocked: Boolean get() = _isLocked.value

    private val _isEnabled = mutableStateOf(true)
    val isEnabled: Boolean get() = _isEnabled.value

    private val _hasPin = mutableStateOf(false)
    val hasPin: Boolean get() = _hasPin.value

    private val _timeoutMs = mutableStateOf(DEFAULT_TIMEOUT_MS)
    val timeoutMs: Long get() = _timeoutMs.value

    /** Elapsed-realtime-independent wall clock of when the app was last backgrounded. */
    private var backgroundedAt: Long? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        // Re-entrant: the Activity, the ViewModel and the widget all call this. Only the first call
        // may set the lock state, or a later one would re-lock an app the user just unlocked.
        if (initialized) return
        initialized = true

        val prefs = prefs()
        _isEnabled.value = prefs.getBoolean(KEY_ENABLED, true)
        _hasPin.value = prefs.getString(KEY_PIN_HASH, null) != null
        _timeoutMs.value = prefs.getLong(KEY_TIMEOUT_MS, DEFAULT_TIMEOUT_MS)
        // A cold start always has to authenticate — except before onboarding has been completed,
        // where there is no data yet and an auth prompt is the first thing a new user would see.
        _isLocked.value = _isEnabled.value && prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    }

    fun setEnabled(enabled: Boolean) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled).apply()
        _isEnabled.value = enabled
        // Turning the lock off must not strand the user on the lock screen.
        if (!enabled) unlock()
    }

    fun setTimeoutMs(millis: Long) {
        prefs().edit().putLong(KEY_TIMEOUT_MS, millis).apply()
        _timeoutMs.value = millis
    }

    // --- Lock state ---

    fun unlock() {
        _isLocked.value = false
        backgroundedAt = null
        clearFailedAttempts()
    }

    fun lockNow() {
        if (_isEnabled.value) _isLocked.value = true
    }

    fun onEnterBackground(now: Long = System.currentTimeMillis()) {
        backgroundedAt = now
    }

    /**
     * Re-locks if the app sat in the background past the configured grace period. Called on every
     * return to the foreground, which is the gap the launch-only prompt used to leave open.
     */
    fun onEnterForeground(now: Long = System.currentTimeMillis()) {
        if (!_isEnabled.value) return
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (now - since >= _timeoutMs.value) _isLocked.value = true
    }

    // --- PIN ---

    /**
     * Hashes and stores [pin]. Slow by design: a 4-digit PIN has only 10k possibilities, so the
     * only thing standing between a leaked hash and the plaintext is the cost of each guess.
     * Run off the main thread.
     */
    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val algorithm = preferredAlgorithm()
        val hash = hashPin(pin, salt, algorithm)
        prefs().edit()
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_ALGORITHM, algorithm)
            .apply()
        _hasPin.value = true
        clearFailedAttempts()
    }

    fun clearPin() {
        prefs().edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_ALGORITHM)
            .apply()
        _hasPin.value = false
        clearFailedAttempts()
    }

    /**
     * Verifies [pin] against the stored hash, unlocking the app on success. Returns false both for
     * a wrong PIN and while a cooldown is in effect — call [lockoutRemainingMs] to tell them apart.
     * Run off the main thread.
     */
    fun verifyPin(pin: String): Boolean {
        if (lockoutRemainingMs() > 0) return false
        val prefs = prefs()
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val storedSalt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val algorithm = prefs.getString(KEY_PIN_ALGORITHM, ALGORITHM_SHA1) ?: ALGORITHM_SHA1

        val expected = Base64.decode(storedHash, Base64.NO_WRAP)
        val actual = hashPin(pin, Base64.decode(storedSalt, Base64.NO_WRAP), algorithm)

        return if (constantTimeEquals(expected, actual)) {
            unlock()
            true
        } else {
            registerFailedAttempt()
            false
        }
    }

    fun failedAttempts(): Int = prefs().getInt(KEY_FAILED_ATTEMPTS, 0)

    /** Milliseconds left on the cooldown triggered by repeated wrong PINs; 0 when entry is open. */
    fun lockoutRemainingMs(now: Long = System.currentTimeMillis()): Long {
        val until = prefs().getLong(KEY_LOCKED_OUT_UNTIL, 0L)
        return (until - now).coerceAtLeast(0L)
    }

    private fun registerFailedAttempt() {
        val attempts = failedAttempts() + 1
        val editor = prefs().edit().putInt(KEY_FAILED_ATTEMPTS, attempts)
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            // Back off harder the longer someone keeps guessing: 30s, 60s, 120s … capped at 5 min.
            val step = attempts - MAX_FAILED_ATTEMPTS
            val cooldown = (30_000L shl step.coerceAtMost(4)).coerceAtMost(300_000L)
            editor.putLong(KEY_LOCKED_OUT_UNTIL, System.currentTimeMillis() + cooldown)
        }
        editor.apply()
    }

    private fun clearFailedAttempts() {
        if (!initialized) return
        prefs().edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKED_OUT_UNTIL, 0L)
            .apply()
    }

    private fun preferredAlgorithm(): String =
        runCatching { SecretKeyFactory.getInstance(ALGORITHM_SHA256) }
            .fold(onSuccess = { ALGORITHM_SHA256 }, onFailure = { ALGORITHM_SHA1 })

    private fun hashPin(pin: String, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } catch (e: Exception) {
            // Only reachable if the stored algorithm is missing on this device (API 24/25 lack the
            // SHA-256 variant); the SHA-1 HMAC is still a sound KDF.
            SecretKeyFactory.getInstance(ALGORITHM_SHA1).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
