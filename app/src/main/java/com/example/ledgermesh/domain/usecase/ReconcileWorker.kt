package com.example.ledgermesh.domain.usecase

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager worker that runs the reconciliation engine in the background.
 *
 * Uses [HiltWorker] so that the [ReconciliationEngine] dependency is
 * injected automatically via Hilt's assisted injection support.
 *
 * Retries up to 3 times on transient failures.
 */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reconciliationEngine: ReconciliationEngine
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            reconciliationEngine.reconcileAll()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "reconcile_observations"
    }
}
