package com.example.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        AppUpdateManager.checkForUpdate(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_UPDATE_CHECK_WORK = "one_crore_daily_update_check"

        fun scheduleDailyCheck(context: Context) {
            val appContext = context.applicationContext
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            try {
                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    UNIQUE_UPDATE_CHECK_WORK,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } catch (_: IllegalStateException) {
                // Ignored in test environments where WorkManager is intentionally uninitialized
            }
        }
    }
}
