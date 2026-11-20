package com.example.data

import com.example.data.local.CustomCounterEntity
import com.example.data.local.DailyCountEntity
import com.example.data.local.UserProfileEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupCustomCounter(
    val id: String,
    val name: String,
    val targetCount: Long,
    val currentCount: Long
)

data class BackupPayload(
    val displayName: String?,
    val dailyTarget: Int?,
    val reminder1Hour: Int?,
    val reminder1Minute: Int?,
    val reminder2Hour: Int?,
    val reminder2Minute: Int?,
    val remindersEnabled: Boolean?,
    val startedAtMillis: Long? = null,
    val days: List<Pair<String, Long>>,
    val customCounters: List<BackupCustomCounter>
)

object BackupCodec {
    private val dateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val idRegex = Regex("^[a-zA-Z0-9_\\-]+$")

    fun encode(
        profile: UserProfileEntity?,
        days: List<DailyCountEntity>,
        customCounters: List<CustomCounterEntity> = emptyList()
    ): String {
        val root = JSONObject().apply {
            put("app", "1 Crore")
            put("version", 2)
            put("exportedAtMillis", System.currentTimeMillis())
            if (profile != null) {
                put("profile", JSONObject().apply {
                    put("displayName", profile.displayName)
                    put("dailyTarget", profile.dailyTarget)
                    put("reminder1Hour", profile.reminder1Hour)
                    put("reminder1Minute", profile.reminder1Minute)
                    put("reminder2Hour", profile.reminder2Hour)
                    put("reminder2Minute", profile.reminder2Minute)
                    put("remindersEnabled", profile.remindersEnabled)
                    put("startedAtMillis", profile.startedAtMillis)
                })
            }
            val arr = JSONArray()
            days.sortedBy { it.dateKey }.forEach { day ->
                arr.put(JSONObject().apply {
                    put("dateKey", day.dateKey)
                    put("count", day.count)
                })
            }
            put("dailyCounts", arr)

            val countersArr = JSONArray()
            customCounters.filter { !it.isDeleted }.forEach { c ->
                countersArr.put(JSONObject().apply {
                    put("id", c.id)
                    put("name", c.name)
                    put("targetCount", c.targetCount)
                    put("currentCount", c.currentCount)
                })
            }
            put("customCounters", countersArr)
        }
        return root.toString(2)
    }

    fun decode(jsonText: String): BackupPayload {
        val root = JSONObject(jsonText)
        val profileObj = root.optJSONObject("profile")
        val arr = root.optJSONArray("dailyCounts") ?: JSONArray()
        val days = buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val dateKey = item.optString("dateKey", "").trim()
                val count = item.optLong("count", -1L)
                if (dateRegex.matches(dateKey) && count >= 0L) {
                    add(dateKey to count)
                }
            }
        }
        val countersArr = root.optJSONArray("customCounters") ?: JSONArray()
        val counters = buildList {
            for (i in 0 until countersArr.length()) {
                val item = countersArr.optJSONObject(i) ?: continue
                val id = item.optString("id", "").trim()
                val name = item.optString("name", "").trim()
                val target = item.optLong("targetCount", 0L)
                val current = item.optLong("currentCount", -1L)
                if (idRegex.matches(id) && name.isNotEmpty() && target >= 1L && current >= 0L) {
                    add(BackupCustomCounter(id, name.take(80), target, current))
                }
            }
        }
        return BackupPayload(
            displayName = profileObj?.optString("displayName")?.takeIf { it.isNotBlank() },
            dailyTarget = profileObj?.optInt("dailyTarget", 0)?.takeIf { it in 1..10_000_000 },
            reminder1Hour = profileObj?.optInt("reminder1Hour", -1)?.takeIf { it in 0..23 },
            reminder1Minute = profileObj?.optInt("reminder1Minute", -1)?.takeIf { it in 0..59 },
            reminder2Hour = profileObj?.optInt("reminder2Hour", -1)?.takeIf { it in 0..23 },
            reminder2Minute = profileObj?.optInt("reminder2Minute", -1)?.takeIf { it in 0..59 },
            remindersEnabled = if (profileObj?.has("remindersEnabled") == true) {
                profileObj.optBoolean("remindersEnabled")
            } else {
                null
            },
            startedAtMillis = profileObj?.optLong("startedAtMillis", 0L)?.takeIf { it > 0L },
            days = days,
            customCounters = counters
        )
    }

    /**
     * Restore-test gate: verifies that a backup JSON string is valid, contains recognizable
     * 1 Crore backup structure, and survives a full decode -> re-encode -> re-decode round-trip
     * without losing any daily count, custom counter, or startedAtMillis metadata.
     */
    fun verifyBackupRoundTrip(jsonText: String): Result<BackupPayload> {
        return try {
            val root = JSONObject(jsonText)
            val hasRecognizedStructure = root.has("dailyCounts") ||
                root.has("profile") ||
                root.has("customCounters") ||
                root.optString("app") == "1 Crore"
            if (!hasRecognizedStructure) {
                return Result.failure(IllegalArgumentException("Unrecognized backup format."))
            }

            val firstPass = decode(jsonText)
            val syntheticProfile = UserProfileEntity(
                userId = "verify_gate",
                displayName = firstPass.displayName ?: "Friend",
                dailyTarget = firstPass.dailyTarget ?: DuroodRepository.DEFAULT_DAILY_TARGET,
                setupCompleted = true,
                reminder1Hour = firstPass.reminder1Hour ?: 18,
                reminder1Minute = firstPass.reminder1Minute ?: 0,
                reminder2Hour = firstPass.reminder2Hour ?: 21,
                reminder2Minute = firstPass.reminder2Minute ?: 0,
                remindersEnabled = firstPass.remindersEnabled ?: true,
                startedAtMillis = firstPass.startedAtMillis ?: 0L
            )
            val syntheticDays = firstPass.days.map { (dateKey, count) ->
                DailyCountEntity(userId = "verify_gate", dateKey = dateKey, count = count)
            }
            val syntheticCounters = firstPass.customCounters.map { c ->
                CustomCounterEntity(
                    id = c.id,
                    userId = "verify_gate",
                    name = c.name,
                    targetCount = c.targetCount,
                    currentCount = c.currentCount
                )
            }
            val reEncoded = encode(syntheticProfile, syntheticDays, syntheticCounters)
            val secondPass = decode(reEncoded)

            if (firstPass.days.sortedBy { it.first } != secondPass.days.sortedBy { it.first }) {
                return Result.failure(IllegalStateException("Backup restore-test gate failed: daily counts mismatch."))
            }
            if (firstPass.customCounters != secondPass.customCounters) {
                return Result.failure(IllegalStateException("Backup restore-test gate failed: custom counters mismatch."))
            }
            if (firstPass.startedAtMillis != secondPass.startedAtMillis) {
                return Result.failure(IllegalStateException("Backup restore-test gate failed: startedAtMillis mismatch."))
            }
            Result.success(firstPass)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
