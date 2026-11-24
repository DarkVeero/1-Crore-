package com.example.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.DuroodRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val currentUser = Firebase.auth.currentUser ?: return Result.success()
        val repository = DuroodRepository(applicationContext)
        val syncResult = repository.syncWithCloud()
        runCatching {
            com.example.groups.GroupRepository.flushDirtyGroupsToCloud(applicationContext, currentUser.uid)
        }
        return if (syncResult.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_SYNC_WORK = "one_crore_background_sync"
        private const val UNIQUE_PERIODIC_SYNC_WORK = "one_crore_periodic_sync"

        fun enqueueWhenOnline(context: Context) {
            val appContext = context.applicationContext
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()
            try {
                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(UNIQUE_SYNC_WORK, ExistingWorkPolicy.REPLACE, request)
            } catch (_: IllegalStateException) {
                // Ignored in test environments where WorkManager is intentionally uninitialized
            }
        }

        // FIX: 15-minute periodic sync that also runs when the app is closed.
        // Call once at app start from OneCroreApp.onCreate(); KEEP makes
        // repeat calls a no-op.
        fun enqueuePeriodicSync(context: Context) {
            val appContext = context.applicationContext
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            try {
                WorkManager.getInstance(appContext)
                    .enqueueUniquePeriodicWork(
                        UNIQUE_PERIODIC_SYNC_WORK,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                    )
            } catch (_: IllegalStateException) {
                // Ignored in test environments where WorkManager is intentionally uninitialized
            }
        }
    }
}
