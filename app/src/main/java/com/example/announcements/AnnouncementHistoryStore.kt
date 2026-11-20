package com.example.announcements

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONArray
import org.json.JSONObject

data class AnnouncementHistoryEntry(
    val announcement: AnnouncementItem,
    val recordedAtMillis: Long,
    val status: String
)

object AnnouncementHistoryStore {
    private const val PREFS_NAME = "one_crore_announcement_history_prefs"
    private const val MAX_HISTORY_ITEMS = 100

    const val STATUS_SHOWN = "shown"
    const val STATUS_DISMISSED = "dismissed"
    const val STATUS_ACTED = "acted"
    const val STATUS_PUSHED = "pushed"

    private val _historyUpdates = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val historyUpdates: SharedFlow<String> = _historyUpdates.asSharedFlow()

    private fun historyKey(userId: String): String {
        val safeUser = userId.trim().ifBlank { "default" }
        return "history_items_$safeUser"
    }

    fun recordAnnouncement(
        context: Context,
        userId: String,
        announcement: AnnouncementItem,
        status: String,
        recordedAtMillis: Long = System.currentTimeMillis()
    ) {
        if (announcement.id.isBlank()) return
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = getHistoryNewestFirst(appContext, userId)
        val previous = existing.firstOrNull { it.announcement.id == announcement.id }

        // Keep newer timestamp when upgrading status on an existing message,
        // or advance timestamp when newly received/shown/dismissed.
        val effectiveTimestamp = if (previous != null) {
            maxOf(previous.recordedAtMillis, recordedAtMillis)
        } else {
            recordedAtMillis
        }
        val mergedItem = if (previous != null && announcement.body.isBlank()) {
            previous.announcement
        } else {
            announcement
        }
        val newEntry = AnnouncementHistoryEntry(
            announcement = mergedItem,
            recordedAtMillis = effectiveTimestamp,
            status = status
        )

        val updated = (listOf(newEntry) + existing.filterNot { it.announcement.id == announcement.id })
            .sortedWith(
                compareByDescending<AnnouncementHistoryEntry> { it.recordedAtMillis }
                    .thenBy { it.announcement.id }
            )
            .take(MAX_HISTORY_ITEMS)

        val arr = JSONArray()
        updated.forEach { entry ->
            val obj = JSONObject().apply {
                put("announcement", entry.announcement.toJsonObject())
                put("recordedAtMillis", entry.recordedAtMillis)
                put("status", entry.status)
            }
            arr.put(obj)
        }
        prefs.edit().putString(historyKey(userId), arr.toString()).apply()
        _historyUpdates.tryEmit(userId)
    }

    fun getHistoryNewestFirst(
        context: Context,
        userId: String
    ): List<AnnouncementHistoryEntry> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(historyKey(userId), null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val annObj = obj.optJSONObject("announcement") ?: continue
                    val item = AnnouncementItem.fromJsonObject(annObj) ?: continue
                    val recordedAt = obj.optLong("recordedAtMillis", 0L)
                    val status = obj.optString("status", STATUS_SHOWN)
                    add(
                        AnnouncementHistoryEntry(
                            announcement = item,
                            recordedAtMillis = recordedAt,
                            status = status
                        )
                    )
                }
            }.sortedWith(
                compareByDescending<AnnouncementHistoryEntry> { it.recordedAtMillis }
                    .thenBy { it.announcement.id }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun resetForTest(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
