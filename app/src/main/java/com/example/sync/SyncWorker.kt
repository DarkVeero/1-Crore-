package com.example.sync

import android.content.Context
import androidx.work.BackoffPolicy
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
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class SyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val currentUser = Firebase.auth.currentUser ?: return Result.success()
        val repository = DuroodRepository(applicationContext)

        val syncResult: kotlin.Result<Unit> = runCatching {
            withTimeoutOrNull(DuroodRepository.CLOUD_SYNC_TIMEOUT_MS) {
                repository.syncWithCloud().getOrThrow()
            } ?: throw TimeoutException("SyncWorker syncWithCloud timed out")
        }

        val groupFlushResult: kotlin.Result<Unit> = runCatching {
            withTimeoutOrNull(DuroodRepository.CLOUD_SYNC_TIMEOUT_MS) {
                com.example.groups.GroupRepository.flushDirtyGroupsToCloud(applicationContext, currentUser.uid)
            } ?: throw TimeoutException("SyncWorker flushDirtyGroups timed out")
        }

        val syncError = syncResult.exceptionOrNull()
        val groupError = groupFlushResult.exceptionOrNull()

        if (isPermanentFailure(syncError) || isPermanentFailure(groupError)) {
            return Result.failure()
        }

        return if (syncResult.isSuccess && groupFlushResult.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    private fun isPermanentFailure(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val causes = generateSequence(throwable) { it.cause }
        for (cause in causes) {
            if (cause is FirebaseFirestoreException) {
                if (cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    cause.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
                ) {
                    return true
                }
            }
            if (cause is FirebaseAuthException) {
                return true
            }
        }
        return false
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
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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
