package com.example.ledgermesh.ingestion.sms

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ledgermesh.data.SmsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the periodic SMS scan WorkManager job.
 *
 * Call [enablePeriodicScan] to schedule (or reschedule) the periodic worker
 * with the interval stored in [SmsPreferences.scanIntervalMinutes]. Call
 * [disablePeriodicScan] to cancel the job and mark auto-import as disabled.
 *
 * The worker is constrained to run only when the battery is not low to avoid
 * unnecessary drain from content provider queries.
 */
@Singleton
class SmsMonitorManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val smsPreferences: SmsPreferences
) {

    /**
     * Enables and schedules the periodic SMS scan.
     *
     * Uses [ExistingPeriodicWorkPolicy.UPDATE] so that calling this again
     * (e.g. after changing the interval) replaces the existing schedule
     * without creating duplicate workers.
     */
    fun enablePeriodicScan() {
        smsPreferences.autoImportEnabled = true
        val intervalMinutes = smsPreferences.scanIntervalMinutes.toLong()

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SmsMonitorWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SmsMonitorWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Disables the periodic SMS scan and cancels any pending/running work.
     */
    fun disablePeriodicScan() {
        smsPreferences.autoImportEnabled = false
        WorkManager.getInstance(context).cancelUniqueWork(SmsMonitorWorker.WORK_NAME)
    }

    /**
     * Returns whether the periodic SMS scan is currently enabled.
     */
    fun isEnabled(): Boolean = smsPreferences.autoImportEnabled
}
