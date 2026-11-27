package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val dailyTarget: Int = 2600,
    val setupCompleted: Boolean = false,
    val reminder1Hour: Int = 18,
    val reminder1Minute: Int = 0,
    val reminder2Hour: Int = 21,
    val reminder2Minute: Int = 0,
    val remindersEnabled: Boolean = true,
    val startedAtMillis: Long = 0L,
    val lastSyncedAtMillis: Long = 0L,
    val pendingSync: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val themeMode: String = "bw"
)

@Entity(
    tableName = "daily_counts",
    primaryKeys = ["userId", "dateKey"]
)
data class DailyCountEntity(
    val userId: String,
    val dateKey: String,
    val count: Long,
    val pendingSync: Boolean = true,
    val isManualEdit: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    /**
     * The daily target that was in force when this day was recorded (R4-C9).
     * 0 = not recorded (rows written before this field existed) — History falls
     * back to the current setting's target for those days.
     */
    val dailyTarget: Int = 0
)

@Entity(tableName = "custom_counters")
data class CustomCounterEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val targetCount: Long,
    val currentCount: Long = 0L,
    val pendingSync: Boolean = true,
    val isManualEdit: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
