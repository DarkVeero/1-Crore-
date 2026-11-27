package com.example

import android.app.Application
import androidx.work.Configuration
import com.example.announcements.AnnouncementNotificationHelper
import com.example.notifications.ReminderScheduler
import com.example.update.UpdateCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OneCroreApp : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.ensureNotificationChannel(this)
        AnnouncementNotificationHelper.ensureNotificationChannel(this)
        appScope.launch {
            AnnouncementNotificationHelper.ensureSubscribedToAnnouncementsTopic(this@OneCroreApp)
            UpdateCheckWorker.scheduleDailyCheck(this@OneCroreApp)
            com.example.sync.SyncWorker.enqueuePeriodicSync(this@OneCroreApp)
            ReminderScheduler.rescheduleFromSavedStateIfPossible(this@OneCroreApp)
            // R4-D9: load the owner's saved daily announcement cap before any
            // dispatch decision runs.
            com.example.announcements.AnnouncementFrequencyStore.loadPersistedDailyCap(this@OneCroreApp)
            com.example.announcements.AnnouncementScheduledDispatcher.reschedulePendingAnnouncements(this@OneCroreApp)
            runCatching {
                val repo = com.example.data.DuroodRepository(
                    localDb = com.example.data.local.AppDatabase.getInstance(this@OneCroreApp),
                    firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                    auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                )
                repo.retryPendingAnnouncementCloudWrites(this@OneCroreApp)
            }
        }
    }
}
