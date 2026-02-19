package com.example.ledgermesh.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight preferences store for SMS monitoring configuration.
 *
 * Tracks the last scan timestamp (for incremental imports), whether the
 * periodic auto-import is enabled, and the scan interval in minutes.
 */
@Singleton
class SmsPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Epoch-millis timestamp of the most recent completed SMS scan. */
    var lastScanTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SCAN, value).apply()

    /** Whether the periodic background SMS scan is enabled. */
    var autoImportEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_IMPORT, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_IMPORT, value).apply()

    /** Interval between periodic scans, in minutes. Minimum 15 (WorkManager floor). */
    var scanIntervalMinutes: Int
        get() = prefs.getInt(KEY_SCAN_INTERVAL, DEFAULT_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_SCAN_INTERVAL, value).apply()

    companion object {
        private const val PREFS_NAME = "sms_prefs"
        private const val KEY_LAST_SCAN = "last_scan_timestamp"
        private const val KEY_AUTO_IMPORT = "auto_import_enabled"
        private const val KEY_SCAN_INTERVAL = "scan_interval_minutes"
        private const val DEFAULT_INTERVAL_MINUTES = 15
    }
}
