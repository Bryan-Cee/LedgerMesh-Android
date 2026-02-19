package com.example.ledgermesh.ingestion.sms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.ledgermesh.data.SmsPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant

/**
 * Periodic WorkManager worker that performs incremental SMS imports.
 *
 * On each execution it reads [SmsPreferences.lastScanTimestamp] to decide
 * whether to run a full scan (first run, timestamp is 0) or an incremental
 * scan (only messages newer than the last scan). After a successful import
 * the timestamp is updated so the next run picks up where this one left off.
 *
 * Retries up to 3 times on transient failures before reporting failure.
 */
@HiltWorker
class SmsMonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val smsImportUseCase: SmsImportUseCase,
    private val smsPreferences: SmsPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val lastScan = smsPreferences.lastScanTimestamp
            if (lastScan > 0) {
                smsImportUseCase.importSince(lastScan)
            } else {
                smsImportUseCase.importAll()
            }
            smsPreferences.lastScanTimestamp = Instant.now().toEpochMilli()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "sms_monitor"
        private const val MAX_RETRIES = 3
    }
}
