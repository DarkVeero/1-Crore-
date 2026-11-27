package com.example.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val dailyTarget: Long = 2600L,
    val setupCompleted: Boolean = false,
    val reminder1Hour: Long = 18L,
    val reminder1Minute: Long = 0L,
    val reminder2Hour: Long = 21L,
    val reminder2Minute: Long = 0L,
    val remindersEnabled: Boolean = true,
    val themeMode: String = "bw",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreDailyCount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val count: Long = 0L,
    val clientUpdatedAt: Long = 0L,
    // R4-C9: the daily target in force when this day was recorded (0 = not recorded).
    val dailyTarget: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreCustomCounter(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val targetCount: Long = 1L,
    val currentCount: Long = 0L,
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
