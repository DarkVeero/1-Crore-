package com.example.update

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import java.io.File

// Round 1 U-P2-2 (user, 28 Nov): when the app is closed or killed, a finished
// update download used to surface only through the system DownloadManager
// notification — nothing ever brought the install popup to the front. This
// receiver posts our own "Install now" notification; tapping it opens the
// app straight into the install dialog. The app-open path is already covered
// (ReadyToInstall sets showDialog = true; U-P2-5 re-attaches mid-download).
class UpdateDownloadCompleteReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_OPEN_UPDATE_INSTALL = "extra_open_update_install"
        private const val CHANNEL_ID = "one_crore_updates"
        private const val NOTIFICATION_ID = 7001
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return
        val appContext = context.applicationContext
        // Only our own update file counts — any other completed download is ignored.
        findValidCompletedApk(appContext, downloadId) ?: return
        ensureNotificationChannel(appContext)
        if (!canPostNotifications(appContext)) return
        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE_INSTALL, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Update download complete")
            .setContentText("Tap to install the new version of 1 Crore.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification) }
    }

    private fun findValidCompletedApk(context: Context, downloadId: Long): File? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return null
        var status = -1
        var localUri: String? = null
        dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
            if (cursor.moveToFirst()) {
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                if (uriIndex >= 0) {
                    localUri = cursor.getString(uriIndex)
                }
            }
        }
        if (status != DownloadManager.STATUS_SUCCESSFUL) return null
        val uriFile = localUri?.let { runCatching { Uri.parse(it).path }.getOrNull() }?.let { File(it) }
            ?: return null
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        if (uriFile.parentFile?.canonicalPath != downloadsDir.canonicalPath) return null
        if (!uriFile.name.startsWith("1crore-update-") || !uriFile.name.endsWith(".apk")) return null
        if (!uriFile.exists() || uriFile.length() == 0L) return null
        return if (AppUpdateManager.isValidApkFile(context, uriFile)) uriFile else null
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.update_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.update_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
}
