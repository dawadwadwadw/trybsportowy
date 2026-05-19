package com.trybsportowy.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trybsportowy.TrybsportowyApplication

/**
 * Background sync (CLAUDE.md §10.4). AuthFailed returns success() — do NOT
 * retry on a bad secret; the user must fix it in Settings (§1.9).
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as TrybsportowyApplication
        return when (app.syncRepository().syncOnce()) {
            SyncOutcome.NothingToPush -> Result.success()
            is SyncOutcome.Pushed -> Result.success()
            is SyncOutcome.RetryableFailure -> Result.retry()
            SyncOutcome.AuthFailed -> Result.success()
        }
    }
}
