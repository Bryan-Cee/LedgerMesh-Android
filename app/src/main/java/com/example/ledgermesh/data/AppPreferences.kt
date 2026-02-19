package com.example.ledgermesh.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central preferences store for app-wide settings.
 *
 * Persists user choices across sessions via SharedPreferences and exposes
 * an observable [darkModeFlow] so the theme layer can react immediately.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // -- Dark Mode (observable for theme) --

    private val _darkModeFlow = MutableStateFlow(prefs.getBoolean(KEY_DARK_MODE, false))
    val darkModeFlow: StateFlow<Boolean> = _darkModeFlow.asStateFlow()

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            _darkModeFlow.value = value
        }

    // -- Biometric Authentication --

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    // -- PIN (stored as SHA-256 hash) --

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        private set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    val hasPin: Boolean get() = pinHash != null

    fun setPin(rawPin: String) {
        pinHash = hashPin(rawPin)
    }

    fun clearPin() {
        prefs.edit().remove(KEY_PIN_HASH).apply()
    }

    fun verifyPin(rawPin: String): Boolean {
        val stored = pinHash ?: return false
        return hashPin(rawPin) == stored
    }

    // -- Reconciliation Settings --

    var confidenceThreshold: Int
        get() = prefs.getInt(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD)
        set(value) = prefs.edit().putInt(KEY_CONFIDENCE_THRESHOLD, value.coerceIn(10, 100)).apply()

    var timeWindowHours: Int
        get() = prefs.getInt(KEY_TIME_WINDOW_HOURS, DEFAULT_TIME_WINDOW_HOURS)
        set(value) = prefs.edit().putInt(KEY_TIME_WINDOW_HOURS, value.coerceIn(1, 168)).apply()

    var amountToleranceCents: Int
        get() = prefs.getInt(KEY_AMOUNT_TOLERANCE_CENTS, DEFAULT_AMOUNT_TOLERANCE_CENTS)
        set(value) = prefs.edit().putInt(KEY_AMOUNT_TOLERANCE_CENTS, value.coerceIn(0, 10000)).apply()

    /** Reset all settings to defaults. */
    fun resetAll() {
        prefs.edit().clear().apply()
        _darkModeFlow.value = false
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"

        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
        private const val KEY_TIME_WINDOW_HOURS = "time_window_hours"
        private const val KEY_AMOUNT_TOLERANCE_CENTS = "amount_tolerance_cents"

        const val DEFAULT_CONFIDENCE_THRESHOLD = 75
        const val DEFAULT_TIME_WINDOW_HOURS = 48
        const val DEFAULT_AMOUNT_TOLERANCE_CENTS = 50
    }
}
