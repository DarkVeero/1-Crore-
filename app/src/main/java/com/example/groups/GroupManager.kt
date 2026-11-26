package com.example.groups

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.R
import com.example.data.DuroodRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

object GroupCelebrationStore {
    private const val PREFS_NAME = "one_crore_group_celebration_prefs"

    private fun seenKey(userId: String): String {
        val safe = userId.trim().ifBlank { "default" }
        return "seen_celebrations_$safe"
    }

    fun getSeenIds(context: Context, userId: String): Set<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(seenKey(userId), emptySet()) ?: emptySet()
    }

    fun markSeen(context: Context, userId: String, celebrationId: String) {
        if (celebrationId.isBlank()) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = (prefs.getStringSet(seenKey(userId), emptySet()) ?: emptySet()).toMutableSet()
        if (updated.add(celebrationId)) {
            prefs.edit().putStringSet(seenKey(userId), updated).apply()
        }
    }

    fun getNextUnseenCelebration(
        context: Context,
        userId: String,
        groups: List<GroupItem>,
        today: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis()
    ): GroupCelebrationNotice? {
        val seen = getSeenIds(context, userId)
        val allNotices = groups.flatMap { group ->
            GroupLogic.computeCelebrations(
                group = group,
                currentUserId = userId,
                today = today,
                nowMillis = nowMillis
            )
        }
        return allNotices.firstOrNull { notice ->
            notice.id !in seen && (notice.targetUserId == null || notice.targetUserId == userId)
        }
    }

    fun clearUser(context: Context, userId: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(seenKey(userId)).apply()
    }

    fun resetForTest(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}

object GroupRepository {
    private val groupWriteMutex = Mutex()
    private const val LOG_TAG = "Groups"
    private const val PREFS_NAME = "one_crore_groups_store_prefs"
    private const val KEY_GROUPS_JSON = "groups_registry_json"
    private const val KEY_DELETED_GROUP_IDS = "deleted_group_ids"
    private const val KEY_PENDING_CREATE_SYNC_IDS = "pending_create_sync_ids"
    private const val KEY_PENDING_JOIN_SYNC_IDS = "pending_join_sync_ids"
    private const val KEY_PENDING_LEAVE_SYNC_IDS = "pending_leave_sync_ids"
    private const val KEY_PENDING_DELETE_SYNC_IDS = "pending_delete_sync_ids"
    private const val KEY_PENDING_RENAME_SYNC_IDS = "pending_rename_sync_ids"
    private const val KEY_PENDING_RENAME_NAME_PREFIX = "pending_rename_name_"
    private const val KEY_DIRTY_GROUP_SYNC_IDS = "dirty_group_sync_ids"
    private const val KEY_GROUP_FLOATING_UNSYNCED_TAPS = "group_floating_unsynced_taps"
    private const val GROUP_FLOATING_BATCH_SYNC_THRESHOLD = 100L
    private const val FIRESTORE_OP_TIMEOUT_MS = 8_000L

    private val _groupUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 32)
    val groupUpdates: SharedFlow<Unit> = _groupUpdates.asSharedFlow()

    @Volatile
    private var lastSharedInviteMessageForTest: String? = null

    private val activeGroupListenersForTest = java.util.concurrent.atomic.AtomicInteger(0)
    private val cloudGroupWritesForTest = java.util.concurrent.atomic.AtomicInteger(0)
    private val singleGroupRefreshesForTest = java.util.concurrent.atomic.AtomicInteger(0)

    fun getLastSharedInviteMessageForTest(): String? = lastSharedInviteMessageForTest
    fun getActiveGroupListenerCountForTest(): Int = activeGroupListenersForTest.get()
    fun getCloudGroupWriteCountForTest(): Int = cloudGroupWritesForTest.get()
    fun getSingleGroupRefreshCountForTest(): Int = singleGroupRefreshesForTest.get()

    private fun readStringSet(context: Context, key: String): MutableSet<String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
    }

    private fun writeStringSet(context: Context, key: String, values: Set<String>) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(key, values)
            .apply()
    }

    fun clearLocalGroupsStore(context: Context) {
        context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_GROUPS_JSON)
        .remove(KEY_DELETED_GROUP_IDS)
        .remove(KEY_PENDING_CREATE_SYNC_IDS)
        .remove(KEY_PENDING_JOIN_SYNC_IDS)
        .remove(KEY_PENDING_LEAVE_SYNC_IDS)
        .remove(KEY_PENDING_DELETE_SYNC_IDS)
            .remove(KEY_PENDING_RENAME_SYNC_IDS)
        .remove(KEY_DIRTY_GROUP_SYNC_IDS)
        .remove(KEY_GROUP_FLOATING_UNSYNCED_TAPS)
        .apply()
    }

    private fun markGroupDeletedLocally(context: Context, groupId: String) {
        if (groupId.isBlank()) return
        val deleted = readStringSet(context, KEY_DELETED_GROUP_IDS)
        if (deleted.add(groupId)) {
            writeStringSet(context, KEY_DELETED_GROUP_IDS, deleted)
        }
        val pendingCreate = readStringSet(context, KEY_PENDING_CREATE_SYNC_IDS)
        if (pendingCreate.remove(groupId)) {
            writeStringSet(context, KEY_PENDING_CREATE_SYNC_IDS, pendingCreate)
        }
    }

    private fun clearGroupDeletedLocally(context: Context, groupId: String) {
        if (groupId.isBlank()) return
        val deleted = readStringSet(context, KEY_DELETED_GROUP_IDS)
        if (deleted.remove(groupId)) {
            writeStringSet(context, KEY_DELETED_GROUP_IDS, deleted)
        }
    }

    private fun markPendingCreateSync(context: Context, groupId: String, pending: Boolean) {
        if (groupId.isBlank()) return
        val pendingSet = readStringSet(context, KEY_PENDING_CREATE_SYNC_IDS)
        val changed = if (pending) pendingSet.add(groupId) else pendingSet.remove(groupId)
        if (changed) {
            writeStringSet(context, KEY_PENDING_CREATE_SYNC_IDS, pendingSet)
        }
    }

    private fun markPendingJoinSync(context: Context, groupId: String, pending: Boolean) {
        if (groupId.isBlank()) return
        val pendingSet = readStringSet(context, KEY_PENDING_JOIN_SYNC_IDS)
        val changed = if (pending) pendingSet.add(groupId) else pendingSet.remove(groupId)
        if (changed) {
            writeStringSet(context, KEY_PENDING_JOIN_SYNC_IDS, pendingSet)
        }
    }

    private fun markPendingLeaveSync(context: Context, groupId: String, pending: Boolean) {
        if (groupId.isBlank()) return
        val pendingSet = readStringSet(context, KEY_PENDING_LEAVE_SYNC_IDS)
        val changed = if (pending) pendingSet.add(groupId) else pendingSet.remove(groupId)
        if (changed) {
            writeStringSet(context, KEY_PENDING_LEAVE_SYNC_IDS, pendingSet)
        }
    }

    private fun markPendingDeleteSync(context: Context, groupId: String, pending: Boolean) {
        if (groupId.isBlank()) return
        val pendingSet = readStringSet(context, KEY_PENDING_DELETE_SYNC_IDS)
        val changed = if (pending) pendingSet.add(groupId) else pendingSet.remove(groupId)
        if (changed) {
            writeStringSet(context, KEY_PENDING_DELETE_SYNC_IDS, pendingSet)
        }
    }

    private fun markPendingRenameSync(context: Context, groupId: String, newName: String?, pending: Boolean) {
        if (groupId.isBlank()) return
        val pendingSet = readStringSet(context, KEY_PENDING_RENAME_SYNC_IDS)
        val changed = if (pending) pendingSet.add(groupId) else pendingSet.remove(groupId)
        if (changed) {
            writeStringSet(context, KEY_PENDING_RENAME_SYNC_IDS, pendingSet)
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (pending) {
            if (!newName.isNullOrBlank()) {
                prefs.edit().putString(KEY_PENDING_RENAME_NAME_PREFIX + groupId, newName).apply()
            }
        } else {
            prefs.edit().remove(KEY_PENDING_RENAME_NAME_PREFIX + groupId).apply()
        }
    }

    private fun readPendingRenameName(context: Context, groupId: String): String? {
        if (groupId.isBlank()) return null
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_RENAME_NAME_PREFIX + groupId, null)
    }

    fun markGroupDirtyForSync(context: Context, groupId: String) {
        if (groupId.isBlank()) return
        val dirtySet = readStringSet(context, KEY_DIRTY_GROUP_SYNC_IDS)
        if (dirtySet.add(groupId)) {
            writeStringSet(context, KEY_DIRTY_GROUP_SYNC_IDS, dirtySet)
        }
    }

    fun getDirtyGroupSyncIds(context: Context): Set<String> {
        return readStringSet(context, KEY_DIRTY_GROUP_SYNC_IDS)
    }

    fun clearGroupDirtyForSync(context: Context, groupId: String) {
        if (groupId.isBlank()) return
        val dirtySet = readStringSet(context, KEY_DIRTY_GROUP_SYNC_IDS)
        if (dirtySet.remove(groupId)) {
            writeStringSet(context, KEY_DIRTY_GROUP_SYNC_IDS, dirtySet)
        }
    }

    fun recordFloatingTapForGroupBatchSync(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val next = prefs.getLong(KEY_GROUP_FLOATING_UNSYNCED_TAPS, 0L).coerceAtLeast(0L) + 1L
        return if (next >= GROUP_FLOATING_BATCH_SYNC_THRESHOLD) {
            prefs.edit().putLong(KEY_GROUP_FLOATING_UNSYNCED_TAPS, 0L).apply()
            true
        } else {
            prefs.edit().putLong(KEY_GROUP_FLOATING_UNSYNCED_TAPS, next).apply()
            false
        }
    }

    fun resetFloatingTapBatchCounter(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_GROUP_FLOATING_UNSYNCED_TAPS, 0L)
            .apply()
    }

    suspend fun flushDirtyGroupsToCloud(context: Context, userId: String) {
        if (userId.isBlank()) return
        val dirtyIds = getDirtyGroupSyncIds(context).toList()
        dirtyIds.forEach { groupId ->
            val group = getGroupById(context, groupId)
            val isMember = group?.members?.any { it.userId == userId } == true
            if (group == null || !isMember) {
                clearGroupDirtyForSync(context, groupId)
            } else {
                val member = group.members.firstOrNull { it.userId == userId }
                if (member != null) {
                    if (upsertMemberStatsTransactionBestEffort(context, groupId, member)) {
                        clearGroupDirtyForSync(context, groupId)
                    }
                }
            }
        }
        resetFloatingTapBatchCounter(context)
    }

    fun snapshotLocalGroups(context: Context): Map<String, GroupItem> {
        return readAllGroups(context).toMap()
    }

    fun restoreLocalGroupsSnapshot(context: Context, snapshot: Map<String, GroupItem>) {
        writeAllGroups(context, snapshot)
    }

    private fun readAllGroups(context: Context): MutableMap<String, GroupItem> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_GROUPS_JSON, null).orEmpty()
        if (raw.isBlank()) return linkedMapOf()
        val deleted = readStringSet(context, KEY_DELETED_GROUP_IDS)
        val result = linkedMapOf<String, GroupItem>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val group = decodeGroupJson(obj) ?: continue
                if (group.id !in deleted) {
                    result[group.id] = group
                }
            }
        }
        return result
    }

    private fun writeAllGroups(context: Context, groups: Map<String, GroupItem>) {
        val array = JSONArray()
        groups.values
            .sortedByDescending { it.updatedAtMillis }
            .forEach { group ->
                array.put(encodeGroupJson(group))
            }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GROUPS_JSON, array.toString())
            .apply()
        _groupUpdates.tryEmit(Unit)
    }

    fun getGroupsForUser(context: Context, userId: String): List<GroupItem> {
        if (userId.isBlank()) return emptyList()
        return readAllGroups(context).values
            .filter { group -> group.members.any { it.userId == userId } }
            .sortedByDescending { it.createdAtMillis }
    }

    fun getGroupById(context: Context, groupId: String): GroupItem? {
        if (groupId.isBlank()) return null
        return readAllGroups(context)[groupId]
    }

    fun upsertGroupLocally(context: Context, group: GroupItem) {
        if (group.id in readStringSet(context, KEY_DELETED_GROUP_IDS)) return
        val all = readAllGroups(context)
        all[group.id] = group
        writeAllGroups(context, all)
    }

    fun removeGroupLocally(context: Context, groupId: String) {
        if (groupId.isBlank()) return
        markGroupDeletedLocally(context, groupId)
        val all = readAllGroups(context)
        if (all.remove(groupId) != null) {
            writeAllGroups(context, all)
        }
    }

    fun upsertRemoteGroupLocally(context: Context, remote: GroupItem): GroupItem {
        val deleted = readStringSet(context, KEY_DELETED_GROUP_IDS)
        if (remote.id in deleted) return remote
        val all = readAllGroups(context)
        val merged = mergeRemoteAndLocalGroup(all[remote.id], remote)
        if (merged != all[remote.id]) {
            all[remote.id] = merged
            writeAllGroups(context, all)
        }
        return merged
    }

    suspend fun createGroup(
        context: Context,
        creatorUserId: String,
        creatorDisplayName: String,
        name: String,
        type: GroupType,
        zikrName: String? = null,
        targetCount: Long = GroupLogic.DEFAULT_UNIFIED_GROUP_TARGET,
        creatorTodayCount: Long = 0L,
        creatorLifetimeTotal: Long = 0L,
        creatorWeeklyCount: Long = 0L,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis()
    ): Result<GroupItem> {
        return groupWriteMutex.withLock {
            createGroupLocked(
                context = context,
                creatorUserId = creatorUserId,
                creatorDisplayName = creatorDisplayName,
                name = name,
                type = type,
                zikrName = zikrName,
                targetCount = targetCount,
                creatorTodayCount = creatorTodayCount,
                creatorLifetimeTotal = creatorLifetimeTotal,
                creatorWeeklyCount = creatorWeeklyCount,
                todayDateKey = todayDateKey,
                nowMillis = nowMillis
            )
        }
    }

    suspend fun createGroupLocked(
        context: Context,
        creatorUserId: String,
        creatorDisplayName: String,
        name: String,
        type: GroupType,
        zikrName: String? = null,
        targetCount: Long = GroupLogic.DEFAULT_UNIFIED_GROUP_TARGET,
        creatorTodayCount: Long = 0L,
        creatorLifetimeTotal: Long = 0L,
        creatorWeeklyCount: Long = 0L,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis()
    ): Result<GroupItem> {
        val cleanName = name.trim().take(60)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a group name."))
        }
        val cleanZikr = if (type == GroupType.CUSTOM_ZIKR) {
            zikrName?.trim()?.take(60).orEmpty()
        } else {
            null
        }
        if (type == GroupType.CUSTOM_ZIKR && cleanZikr.isNullOrEmpty()) {
            return Result.failure(IllegalArgumentException("Enter the zikr name for this group."))
        }
        val resolvedTarget = targetCount.coerceIn(1L, 100_000_000L)

        val currentGroups = getGroupsForUser(context, creatorUserId)
        if (currentGroups.size >= GroupLogic.MAX_GROUPS_PER_USER) {
            return Result.failure(
                IllegalStateException("You can be in at most ${GroupLogic.MAX_GROUPS_PER_USER} groups. Leave a group first.")
            )
        }

        val allGroups = readAllGroups(context)
        val existingCodes = allGroups.values.map { GroupLogic.normalizeInviteCode(it.inviteCode) }.toSet()
        var inviteCode = GroupLogic.generateInviteCode()
        while (inviteCode in existingCodes) {
            inviteCode = GroupLogic.generateInviteCode()
        }

        val cleanMemberName = creatorDisplayName.trim().ifBlank { "Friend" }.take(80)
        val initialMember = GroupMemberProgress(
            userId = creatorUserId,
            displayName = cleanMemberName,
            todayCount = 0L,
            todayDateKey = todayDateKey,
            lifetimeTotalCount = 0L,
            weeklyCount = 0L,
            updatedAtMillis = nowMillis
        )

        val group = GroupItem(
            id = "grp_${UUID.randomUUID().toString().replace("-", "").take(12)}",
            name = cleanName,
            inviteCode = inviteCode,
            type = type,
            zikrName = cleanZikr,
            targetCount = resolvedTarget,
            creatorUserId = creatorUserId,
            members = listOf(initialMember),
            serverTotalCount = initialMember.lifetimeTotalCount,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis
        )

        allGroups[group.id] = group
        writeAllGroups(context, allGroups)
        markPendingCreateSync(context, group.id, pending = true)
        val pushed = pushGroupToFirestoreBestEffort(context, group)
        if (pushed) {
            markPendingCreateSync(context, group.id, pending = false)
        }
        return Result.success(group)
    }

    suspend fun joinGroupByCode(
        context: Context,
        userId: String,
        displayName: String,
        rawInviteCode: String,
        userTodayCount: Long = 0L,
        userLifetimeTotal: Long = 0L,
        userWeeklyCount: Long = 0L,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis()
    ): Result<GroupItem> {
        return groupWriteMutex.withLock {
            joinGroupByCodeLocked(
                context = context,
                userId = userId,
                displayName = displayName,
                rawInviteCode = rawInviteCode,
                userTodayCount = userTodayCount,
                userLifetimeTotal = userLifetimeTotal,
                userWeeklyCount = userWeeklyCount,
                todayDateKey = todayDateKey,
                nowMillis = nowMillis
            )
        }
    }

    suspend fun joinGroupByCodeLocked(
        context: Context,
        userId: String,
        displayName: String,
        rawInviteCode: String,
        userTodayCount: Long = 0L,
        userLifetimeTotal: Long = 0L,
        userWeeklyCount: Long = 0L,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis()
    ): Result<GroupItem> {
        val code = GroupLogic.extractInviteCodeInput(rawInviteCode)
        if (code.length < 4) {
            return Result.failure(IllegalArgumentException("Invalid code"))
        }

        val allGroups = readAllGroups(context)
        var targetGroup = allGroups.values.firstOrNull {
            GroupLogic.normalizeInviteCode(it.inviteCode) == code
        }
        var foundViaCloud = false

        if (targetGroup == null) {
            when (val lookup = fetchGroupByCodeFromFirestore(context, code)) {
                is InviteLookupOutcome.Found -> {
                    foundViaCloud = true
                    targetGroup = lookup.group
                }
                is InviteLookupOutcome.Error -> {
                    return Result.failure(IllegalStateException(lookup.message))
                }
                InviteLookupOutcome.NotFound -> Unit
            }
        }

        if (targetGroup == null) {
            return Result.failure(IllegalStateException("Invalid code"))
        }

        val alreadyMember = targetGroup.members.any { it.userId == userId }
        val userGroups = allGroups.values.filter { g -> g.members.any { it.userId == userId } }
        if (!alreadyMember && userGroups.size >= GroupLogic.MAX_GROUPS_PER_USER) {
            return Result.failure(
                IllegalStateException("You can be in at most ${GroupLogic.MAX_GROUPS_PER_USER} groups. Leave a group first.")
            )
        }

        val cleanName = displayName.trim().ifBlank { "Friend" }.take(80)
        val existingMember = targetGroup.members.firstOrNull { it.userId == userId }
        val memberPayload = GroupMemberProgress(
            userId = userId,
            displayName = cleanName,
            todayCount = existingMember?.todayCount ?: 0L,
            todayDateKey = existingMember?.todayDateKey ?: todayDateKey,
            lifetimeTotalCount = existingMember?.lifetimeTotalCount ?: 0L,
            weeklyCount = existingMember?.weeklyCount ?: 0L,
            updatedAtMillis = nowMillis
        )

        val cloudResult = joinMemberInFirestoreTransaction(
            context = context,
            groupId = targetGroup.id,
            member = memberPayload,
            nowMillis = nowMillis
        )
        if (cloudResult != null) {
            when (cloudResult) {
                is CloudJoinOutcome.Success -> {
                    allGroups[cloudResult.group.id] = cloudResult.group
                    writeAllGroups(context, allGroups)
                    clearGroupDeletedLocally(context, cloudResult.group.id)
                markPendingJoinSync(context, cloudResult.group.id, pending = false)
                return Result.success(cloudResult.group)
                }
                CloudJoinOutcome.GroupNotFound -> {
                    allGroups.remove(targetGroup.id)
                    markGroupDeletedLocally(context, targetGroup.id)
                    writeAllGroups(context, allGroups)
                    return Result.failure(IllegalStateException("Invalid code"))
                }
                is CloudJoinOutcome.Error -> {
                    if (foundViaCloud) {
                        return Result.failure(IllegalStateException(cloudResult.message))
                    }
                }
            }
        }

        val updatedMembers = if (alreadyMember) {
            targetGroup.members.map { member ->
                if (member.userId == userId) memberPayload else member
            }
        } else {
            targetGroup.members + memberPayload
        }

        val updatedGroup = targetGroup.copy(
            members = updatedMembers,
            serverTotalCount = updatedMembers.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) },
            updatedAtMillis = maxOf(targetGroup.updatedAtMillis, nowMillis)
        )
        allGroups[updatedGroup.id] = updatedGroup
        writeAllGroups(context, allGroups)
        markPendingJoinSync(context, updatedGroup.id, pending = true)
        return Result.success(updatedGroup)
    }

    suspend fun renameGroup(
        context: Context,
        userId: String,
        groupId: String,
        newName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<GroupItem> {
        return groupWriteMutex.withLock {
            renameGroupLocked(
                context = context,
                userId = userId,
                groupId = groupId,
                newName = newName,
                nowMillis = nowMillis
            )
        }
    }

    suspend fun renameGroupLocked(
        context: Context,
        userId: String,
        groupId: String,
        newName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<GroupItem> {
        val cleanName = newName.trim().take(60)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a group name."))
        }
        val allGroups = readAllGroups(context)
        val group = allGroups[groupId]
            ?: return Result.failure(IllegalStateException("Group not found."))
        if (group.creatorUserId.isBlank() || group.creatorUserId != userId) {
            return Result.failure(IllegalStateException("Only the group creator can rename this group."))
        }

        val nextUpdatedAt = maxOf(nowMillis, group.updatedAtMillis + 1L)
        val renamedLocal = group.copy(
            name = cleanName,
            updatedAtMillis = nextUpdatedAt
        )
        allGroups[groupId] = renamedLocal
        writeAllGroups(context, allGroups)

        val remoteUpdated = renameGroupInFirestoreTransactionBestEffort(
            context = context,
            groupId = groupId,
            creatorUserId = userId,
            newName = cleanName,
            fallbackGroup = renamedLocal,
            nowMillis = nextUpdatedAt
        )
        if (remoteUpdated != null) {
            val latestAll = readAllGroups(context)
            latestAll[groupId] = remoteUpdated
            writeAllGroups(context, latestAll)
            return Result.success(remoteUpdated)
        }
        markPendingRenameSync(context, groupId, cleanName, pending = true)
    return Result.success(renamedLocal)
    }

    suspend fun deleteGroup(
        context: Context,
        userId: String,
        groupId: String
    ): Result<Unit> {
        return groupWriteMutex.withLock {
            deleteGroupLocked(
                context = context,
                userId = userId,
                groupId = groupId
            )
        }
    }

    suspend fun deleteGroupLocked(
        context: Context,
        userId: String,
        groupId: String
    ): Result<Unit> {
        val allGroups = readAllGroups(context)
        val existing = allGroups[groupId]
            ?: return Result.failure(IllegalStateException("Group not found"))
        if (existing.creatorUserId.isBlank() || existing.creatorUserId != userId) {
            return Result.failure(IllegalStateException("Only the group creator can delete this group."))
        }
        markGroupDeletedLocally(context, groupId)
        markPendingDeleteSync(context, groupId, pending = true)
        clearGroupDirtyForSync(context, groupId)
        allGroups.remove(groupId)
        writeAllGroups(context, allGroups)
        com.example.floating.FloatingCounterManager.onGroupRemovedOrLeft(context, groupId)
        deleteGroupInFirestoreBestEffort(context, groupId)
        return Result.success(Unit)
    }

    suspend fun leaveGroup(
        context: Context,
        userId: String,
        groupId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> {
        return groupWriteMutex.withLock {
            leaveGroupLocked(
                context = context,
                userId = userId,
                groupId = groupId,
                nowMillis = nowMillis
            )
        }
    }

    suspend fun leaveGroupLocked(
        context: Context,
        userId: String,
        groupId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<Unit> {
        val allGroups = readAllGroups(context)
        val group = allGroups[groupId] ?: return Result.failure(IllegalStateException("Group not found"))
        val remainingMembers = group.members.filterNot { it.userId == userId }
        if (group.creatorUserId == userId) {
            if (remainingMembers.isEmpty()) {
                return deleteGroupLocked(context, userId, groupId).map {}
            }
            val updatedGroup = group.copy(
                members = remainingMembers,
                creatorUserId = remainingMembers.first().userId,
                serverTotalCount = remainingMembers.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) },
                updatedAtMillis = maxOf(nowMillis, group.updatedAtMillis + 1L)
            )
            allGroups[groupId] = updatedGroup
            clearGroupDirtyForSync(context, groupId)
            writeAllGroups(context, allGroups)
            markPendingLeaveSync(context, groupId, pending = true)
            com.example.floating.FloatingCounterManager.onGroupRemovedOrLeft(context, groupId)
            removeMemberInFirestoreTransactionBestEffort(context, groupId, userId, updatedGroup.updatedAtMillis)
            return Result.success(Unit)
        }
        val updatedGroup = group.copy(
            members = remainingMembers,
            serverTotalCount = remainingMembers.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) },
            updatedAtMillis = maxOf(nowMillis, group.updatedAtMillis + 1L)
        )
        allGroups[groupId] = updatedGroup
        clearGroupDirtyForSync(context, groupId)
        writeAllGroups(context, allGroups)
        markPendingLeaveSync(context, groupId, pending = true)
        com.example.floating.FloatingCounterManager.onGroupRemovedOrLeft(context, groupId)
        removeMemberInFirestoreTransactionBestEffort(context, groupId, userId, updatedGroup.updatedAtMillis)
        return Result.success(Unit)
    }

    /**
     * Superseded in Run 4: Main Durood counts are strictly isolated from all groups and never flow into them.
     */
    @Suppress("UNUSED_PARAMETER")
    fun syncMainDuroodMemberStatsLocally(
        context: Context,
        userId: String,
        displayName: String,
        todayCount: Long,
        lifetimeTotalCount: Long,
        weeklyCount: Long,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis()
    ): List<GroupItem> = emptyList()

    /**
     * Superseded in Run 4: Main Durood counts are strictly isolated from all groups and never push to them.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun pushMainDuroodMemberStatsToCloud(
        context: Context,
        userId: String,
        displayName: String,
        todayCount: Long,
        lifetimeTotalCount: Long,
        weeklyCount: Long,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis(),
        targetGroupId: String? = null
    ) {
        return
    }

    /**
     * Pushes a single group's current member stats for [userId] via atomic Firestore transaction.
     */
    suspend fun pushGroupMemberSnapshotToCloud(
        context: Context,
        userId: String,
        groupId: String
    ) {
        if (userId.isBlank() || groupId.isBlank()) return
        val group = getGroupById(context, groupId) ?: return
        val member = group.members.firstOrNull { it.userId == userId } ?: return
        upsertMemberStatsTransactionBestEffort(context, group.id, member)
    }

    private fun currentWeekKey(today: LocalDate = LocalDate.now()): String {
        val weekFields = WeekFields.ISO
        val week = today.get(weekFields.weekOfWeekBasedYear())
        val year = today.get(weekFields.weekBasedYear())
        return String.format(Locale.US, "%04d-W%02d", year, week)
    }

    /**
     * Adds or reduces a count strictly inside the specified MAIN_DUROOD group for [userId].
     * Positive [delta] adds to this group's today + weekly + lifetime counts; negative [delta]
     * reduces them (clamped >= 0L). Completely isolated from Room main counter, global stats, and 50k badges.
     */
    suspend fun adjustMainGroupCount(
        context: Context,
        userId: String,
        displayName: String,
        groupId: String,
        delta: Long,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis(),
        pushToCloud: Boolean = false
    ): Result<GroupItem> {
        return groupWriteMutex.withLock {
            adjustMainGroupCountLocked(
                context = context,
                userId = userId,
                displayName = displayName,
                groupId = groupId,
                delta = delta,
                todayDateKey = todayDateKey,
                nowMillis = nowMillis,
                pushToCloud = pushToCloud
            )
        }
    }

    suspend fun adjustMainGroupCountLocked(
        context: Context,
        userId: String,
        displayName: String,
        groupId: String,
        delta: Long,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis(),
        pushToCloud: Boolean = false
    ): Result<GroupItem> {
        if (delta == 0L) {
            return Result.failure(IllegalArgumentException("Enter an amount greater than 0."))
        }
        val allGroups = readAllGroups(context)
        val group = allGroups[groupId]
            ?: return Result.failure(IllegalStateException("Group not found."))
        val cleanName = displayName.trim().ifBlank { "Friend" }.take(80)
        val hasMember = group.members.any { it.userId == userId }
        val resolvedNow = maxOf(group.updatedAtMillis + 1L, nowMillis)
        val newMembers = if (hasMember) {
            group.members.map { member ->
                if (member.userId == userId) {
                    val existingToday = if (member.todayDateKey == todayDateKey) member.todayCount else 0L
                    val newToday = (existingToday + delta).coerceAtLeast(0L)
                    val currentWeekKey = currentWeekKey()
                    val baseWeekly = if (member.weeklyDateKey == currentWeekKey) member.weeklyCount else 0L
                    val newWeekly = (baseWeekly + delta).coerceAtLeast(0L)
                    val newLifetime = (member.lifetimeTotalCount + delta).coerceAtLeast(0L)
                    member.copy(
                        displayName = cleanName,
                        todayCount = newToday,
                        todayDateKey = todayDateKey,
                        lifetimeTotalCount = newLifetime,
                        weeklyCount = newWeekly,
                        weeklyDateKey = currentWeekKey,
                        updatedAtMillis = resolvedNow
                    )
                } else {
                    member
                }
            }
        } else {
            return Result.failure(IllegalStateException("You are not a member of this group."))
        }

        val newSum = newMembers.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) }
        val updatedGroup = group.copy(
            members = newMembers,
            serverTotalCount = newSum,
            updatedAtMillis = resolvedNow
        )
        allGroups[groupId] = updatedGroup
        writeAllGroups(context, allGroups)
        if (pushToCloud) {
            val member = newMembers.firstOrNull { it.userId == userId }
            if (member != null) {
                upsertMemberStatsTransactionBestEffort(context, groupId, member)
            }
        }
        return Result.success(updatedGroup)
    }

    /**
     * Increments ONLY the specified CUSTOM_ZIKR group for this user and its group total.
     * Completely isolated from Main Durood Room counts, global stats, and 50k badges.
     * Defaults to local-only update ([pushToCloud] = false) so rapid +1 taps stay batched.
     */
    suspend fun incrementCustomZikrGroup(
        context: Context,
        userId: String,
        displayName: String,
        groupId: String,
        delta: Long,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis(),
        pushToCloud: Boolean = false
    ): Result<GroupItem> {
        return groupWriteMutex.withLock {
            incrementCustomZikrGroupLocked(
                context = context,
                userId = userId,
                displayName = displayName,
                groupId = groupId,
                delta = delta,
                todayDateKey = todayDateKey,
                nowMillis = nowMillis,
                pushToCloud = pushToCloud
            )
        }
    }

    suspend fun incrementCustomZikrGroupLocked(
        context: Context,
        userId: String,
        displayName: String,
        groupId: String,
        delta: Long,
        todayDateKey: String = LocalDate.now().toString(),
        nowMillis: Long = System.currentTimeMillis(),
        pushToCloud: Boolean = false
    ): Result<GroupItem> {
        if (delta == 0L) {
            return Result.failure(IllegalArgumentException("Enter an amount greater than 0."))
        }
        val allGroups = readAllGroups(context)
        val group = allGroups[groupId]
            ?: return Result.failure(IllegalStateException("Group not found."))
        if (group.type != GroupType.CUSTOM_ZIKR) {
            return Result.failure(IllegalStateException("Use in-group + Custom for Main Durood groups."))
        }
        val cleanName = displayName.trim().ifBlank { "Friend" }.take(80)
        val hasMember = group.members.any { it.userId == userId }
        val newMembers = if (hasMember) {
            group.members.map { member ->
                if (member.userId == userId) {
                    val existingToday = if (member.todayDateKey == todayDateKey) member.todayCount else 0L
                    val currentWeekKey = currentWeekKey()
                    val baseWeekly = if (member.weeklyDateKey == currentWeekKey) member.weeklyCount else 0L
                    member.copy(
                        displayName = cleanName,
                        todayCount = (existingToday + delta).coerceAtLeast(0L),
                        todayDateKey = todayDateKey,
                        lifetimeTotalCount = (member.lifetimeTotalCount + delta).coerceAtLeast(0L),
                        weeklyCount = (baseWeekly + delta).coerceAtLeast(0L),
                    updatedAtMillis = maxOf(group.updatedAtMillis + 1L, nowMillis),
                        weeklyDateKey = currentWeekKey,
                    )
                } else {
                    member
                }
            }
        } else {
            val clampedDelta = delta.coerceAtLeast(0L)
            group.members + GroupMemberProgress(
                userId = userId,
                displayName = cleanName,
                todayCount = clampedDelta,
                todayDateKey = todayDateKey,
                lifetimeTotalCount = clampedDelta,
                weeklyCount = clampedDelta,
                weeklyDateKey = currentWeekKey(),
                updatedAtMillis = maxOf(group.updatedAtMillis + 1L, nowMillis)
            )
        }

        val newSum = newMembers.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) }
        val updatedGroup = group.copy(
            members = newMembers,
            serverTotalCount = newSum,
            updatedAtMillis = maxOf(group.updatedAtMillis + 1L, nowMillis)
        )
        allGroups[groupId] = updatedGroup
        writeAllGroups(context, allGroups)
        if (pushToCloud) {
            val member = newMembers.firstOrNull { it.userId == userId }
            if (member != null) {
                upsertMemberStatsTransactionBestEffort(context, groupId, member)
            }
        }
        return Result.success(updatedGroup)
    }

    /**
     * One-shot fetch of a single group document (`groups/{groupId}`) on Group Detail open or manual refresh.
     */
    suspend fun refreshSingleGroupFromServer(context: Context, groupId: String): GroupItem? {
        if (groupId.isBlank()) return getGroupById(context, groupId)
        singleGroupRefreshesForTest.incrementAndGet()
        val db = firestoreOrNull(context) ?: return getGroupById(context, groupId)
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return getGroupById(context, groupId)
        if (authUser.uid.isBlank()) return getGroupById(context, groupId)

        val doc = runCatching {
            withTimeoutOrNull(FIRESTORE_OP_TIMEOUT_MS) {
                db.collection("groups").document(groupId).get().await()
            }
        }.getOrNull() ?: return getGroupById(context, groupId)

        if (!doc.exists()) {
            if (!doc.metadata.isFromCache && groupId !in readStringSet(context, KEY_PENDING_CREATE_SYNC_IDS)) {
                removeGroupLocally(context, groupId)
                return null
            }
            return getGroupById(context, groupId)
        }
        val remote = parseFirestoreGroupDoc(doc.id, doc.data) ?: return getGroupById(context, groupId)
        return upsertRemoteGroupLocally(context, remote)
    }

    /**
     * Attaches ONE screen-scoped snapshot listener strictly on `groups/{groupId}` while Group Detail is open.
     * Detach immediately on screen close/dispose to remain Firebase Spark free-tier safe.
     */
    fun attachSingleGroupListener(
        context: Context,
        groupId: String,
        onUpdated: (GroupItem) -> Unit,
        onDeleted: (() -> Unit)? = null
    ): (() -> Unit)? {
        if (groupId.isBlank()) return null
        val db = firestoreOrNull(context) ?: return null
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return null
        if (authUser.uid.isBlank()) return null

        val registration: ListenerRegistration = runCatching {
            db.collection("groups")
                .document(groupId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        return@addSnapshotListener
                    }
                    if (!snapshot.exists()) {
                        if (!snapshot.metadata.isFromCache &&
                            groupId !in readStringSet(context, KEY_PENDING_CREATE_SYNC_IDS)
                        ) {
                            removeGroupLocally(context, groupId)
                            onDeleted?.invoke()
                        }
                        return@addSnapshotListener
                    }
                    val remote = parseFirestoreGroupDoc(snapshot.id, snapshot.data) ?: return@addSnapshotListener
                    val merged = upsertRemoteGroupLocally(context, remote)
                    onUpdated(merged)
                }
        }.getOrNull() ?: return null

        activeGroupListenersForTest.incrementAndGet()
        val detached = java.util.concurrent.atomic.AtomicBoolean(false)
        return {
            if (detached.compareAndSet(false, true)) {
                activeGroupListenersForTest.updateAndGet { current -> maxOf(0, current - 1) }
                runCatching { registration.remove() }
            }
        }
    }

    // Retries a pending group join; true means the pending flag can be cleared
    // (Success, or GroupNotFound after removing the local group); false keeps the
    // flag so a transient failure is retried on the next refresh.
    private suspend fun retryPendingJoin(
        context: Context,
        groupId: String,
        member: GroupMemberProgress
    ): Boolean {
        val outcome = joinMemberInFirestoreTransaction(
            context = context,
            groupId = groupId,
            member = member,
            nowMillis = System.currentTimeMillis()
        )
        return when (outcome) {
            is CloudJoinOutcome.Success -> true
            CloudJoinOutcome.GroupNotFound -> {
                removeGroupLocally(context, groupId)
                true
            }
            else -> false
        }
    }

    suspend fun refreshGroupsFromServer(context: Context, userId: String) {
        if (userId.isBlank()) return
        val db = firestoreOrNull(context) ?: return
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return
        if (authUser.uid != userId) return

        // Flush any locally created groups whose initial cloud upload hasn't succeeded yet.
        val pendingCreates = readStringSet(context, KEY_PENDING_CREATE_SYNC_IDS).toList()
        if (pendingCreates.isNotEmpty()) {
            val allLocal = readAllGroups(context)
            pendingCreates.forEach { pendingId ->
                val pendingGroup = allLocal[pendingId]
                if (pendingGroup == null) {
                    markPendingCreateSync(context, pendingId, pending = false)
                } else if (pushGroupToFirestoreBestEffort(context, pendingGroup)) {
                    markPendingCreateSync(context, pendingId, pending = false)
                }
            }
        }

        // Retry any locally left groups whose cloud removal hasn't succeeded yet.
        val pendingLeaves = readStringSet(context, KEY_PENDING_LEAVE_SYNC_IDS).toList()
        if (pendingLeaves.isNotEmpty()) {
            pendingLeaves.forEach { pendingId ->
                removeMemberInFirestoreTransactionBestEffort(context, pendingId, userId, System.currentTimeMillis())
                markPendingLeaveSync(context, pendingId, pending = false)
            }
        }
        // Retry any locally deleted groups whose cloud deletion hasn't succeeded yet.
        val pendingDeletes = readStringSet(context, KEY_PENDING_DELETE_SYNC_IDS).toList()
        if (pendingDeletes.isNotEmpty()) {
            pendingDeletes.forEach { pendingId ->
                deleteGroupInFirestoreBestEffort(context, pendingId)
                markPendingDeleteSync(context, pendingId, pending = false)
            }
        }
        // Retry any locally renamed groups whose cloud rename hasn't succeeded yet.
    val pendingRenames = readStringSet(context, KEY_PENDING_RENAME_SYNC_IDS).toList()
    if (pendingRenames.isNotEmpty()) {
        val allLocal = readAllGroups(context)
        pendingRenames.forEach { pendingId ->
            val pendingGroup = allLocal[pendingId]
            val pendingName = readPendingRenameName(context, pendingId)?.takeIf { it.isNotBlank() }
                ?: pendingGroup?.name
            if (pendingGroup != null && pendingGroup.creatorUserId != userId) { markPendingRenameSync(context, pendingId, null, pending = false); return@forEach }
            if (pendingGroup == null || pendingName.isNullOrBlank()) {
                markPendingRenameSync(context, pendingId, null, pending = false)
            } else {
                val renamed = renameGroupInFirestoreTransactionBestEffort(
                    context = context,
                    groupId = pendingId,
                    creatorUserId = pendingGroup.creatorUserId,
                    newName = pendingName,
                    fallbackGroup = pendingGroup,
                    nowMillis = System.currentTimeMillis()
                )
                if (renamed != null) {
                    markPendingRenameSync(context, pendingId, null, pending = false)
                }
            }
        }
    }

    // Retry any locally joined groups whose cloud member add hasn't succeeded yet.
        val pendingJoinIds = readStringSet(context, KEY_PENDING_JOIN_SYNC_IDS).toList()
        if (pendingJoinIds.isNotEmpty()) {
            val allLocal = readAllGroups(context)
            pendingJoinIds.forEach { pendingId ->
                val pendingGroup = allLocal[pendingId]
                val pendingMember = pendingGroup?.members?.firstOrNull { it.userId == userId }
                if (pendingMember == null) {
                    markPendingJoinSync(context, pendingId, pending = false)
                } else if (retryPendingJoin(context, pendingId, pendingMember)) {
                    markPendingJoinSync(context, pendingId, pending = false)
                }
            }
        }
        val snap = runCatching {
            withTimeoutOrNull(FIRESTORE_OP_TIMEOUT_MS) {
                db.collection("groups")
                    .whereArrayContains("memberIds", userId)
                    .get()
                    .await()
            }
        }.getOrNull() ?: return

        val all = readAllGroups(context)
        val deletedIds = readStringSet(context, KEY_DELETED_GROUP_IDS)
        val remainingPendingCreates = readStringSet(context, KEY_PENDING_CREATE_SYNC_IDS)
        val pendingJoins = readStringSet(context, KEY_PENDING_JOIN_SYNC_IDS)
        val remoteIds = hashSetOf<String>()
        var changed = false

        snap.documents.forEach { doc ->
            remoteIds.add(doc.id)
            if (doc.id in deletedIds) return@forEach
            val remote = parseFirestoreGroupDoc(doc.id, doc.data) ?: return@forEach
            if (remote.id in pendingJoins) return@forEach
            val local = all[remote.id]
            val merged = mergeRemoteAndLocalGroup(local, remote)
            if (merged != local) {
                all[remote.id] = merged
                changed = true
            }
        }

        if (!snap.metadata.isFromCache) {
            val staleIds = all.values
                .filter { g ->
                    g.members.any { it.userId == userId } &&
                        g.id !in remoteIds &&
                        g.id !in remainingPendingCreates &&
                    g.id !in pendingJoins
                }
                .map { it.id }
            staleIds.forEach { staleId ->
                markGroupDeletedLocally(context, staleId)
                all.remove(staleId)
                changed = true
            }
        }

        if (changed) {
            writeAllGroups(context, all)
        }
    }

    suspend fun launchMergedWhatsAppInvite(
        context: Context,
        group: GroupItem
    ) {
        val message = GroupLogic.buildMergedWhatsAppInviteMessage(group = group)
        lastSharedInviteMessageForTest = message
        withContext(Dispatchers.Main) {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_SUBJECT, "Join ${group.name} on 1 Crore")
            }
            val whatsappInstalled = runCatching {
                context.packageManager.getPackageInfo("com.whatsapp", 0)
                true
            }.getOrDefault(false)

            if (whatsappInstalled) {
                val waIntent = Intent(sendIntent).apply {
                    `package` = "com.whatsapp"
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                val launchedWa = runCatching {
                    context.startActivity(waIntent)
                    true
                }.getOrDefault(false)
                if (launchedWa) return@withContext
            }

            runCatching {
                val chooser = Intent.createChooser(sendIntent, "Invite via WhatsApp").apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(chooser)
            }
        }
    }

    private sealed interface InviteLookupOutcome {
        data class Found(val group: GroupItem) : InviteLookupOutcome
        data object NotFound : InviteLookupOutcome
        data class Error(val message: String) : InviteLookupOutcome
    }

    private sealed interface CloudJoinOutcome {
        data class Success(val group: GroupItem) : CloudJoinOutcome
        data object GroupNotFound : CloudJoinOutcome
        data class Error(val message: String) : CloudJoinOutcome
    }

    private fun firestoreOrNull(context: Context): FirebaseFirestore? {
        return runCatching {
            val dbId = context.applicationContext.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
        }.getOrNull()
    }

    private suspend fun fetchGroupByCodeFromFirestore(
        context: Context,
        inviteCode: String
    ): InviteLookupOutcome {
        val db = firestoreOrNull(context) ?: return InviteLookupOutcome.NotFound
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return InviteLookupOutcome.NotFound
        if (authUser.uid.isBlank()) return InviteLookupOutcome.NotFound

        val outcome = runCatching {
            withTimeoutOrNull(FIRESTORE_OP_TIMEOUT_MS) {
                val snap = db.collection("groups")
                    .whereEqualTo("inviteCode", inviteCode)
                    .limit(1)
                    .get()
                    .await()
                val doc = snap.documents.firstOrNull() ?: return@withTimeoutOrNull InviteLookupOutcome.NotFound
                val parsed = parseFirestoreGroupDoc(doc.id, doc.data)
                    ?: return@withTimeoutOrNull InviteLookupOutcome.NotFound
                InviteLookupOutcome.Found(parsed)
            }
        }

        if (outcome.isSuccess) {
            return outcome.getOrNull()
                ?: InviteLookupOutcome.Error("Couldn't check invite code right now. Check internet and try again.")
        }

        val error = outcome.exceptionOrNull()
        Log.w(LOG_TAG, "Invite code lookup failed for $inviteCode", error)
        val fsCode = (error as? FirebaseFirestoreException)?.code
        return when (fsCode) {
            FirebaseFirestoreException.Code.NOT_FOUND -> InviteLookupOutcome.NotFound
            else -> InviteLookupOutcome.Error("Couldn't check invite code right now. Check internet and try again.")
        }
    }

    private suspend fun joinMemberInFirestoreTransaction(
        context: Context,
        groupId: String,
        member: GroupMemberProgress,
        nowMillis: Long
    ): CloudJoinOutcome? {
        val db = firestoreOrNull(context) ?: return null
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return null
        if (authUser.uid.isBlank()) return null

        cloudGroupWritesForTest.incrementAndGet()
        val docRef = db.collection("groups").document(groupId)
        val result = runCatching {
            withTimeoutOrNull(FIRESTORE_OP_TIMEOUT_MS) {
                db.runTransaction { tx ->
                    val snap = tx.get(docRef)
                    if (!snap.exists()) {
                        return@runTransaction CloudJoinOutcome.GroupNotFound
                    }
                    val base = parseFirestoreGroupDoc(snap.id, snap.data)
                        ?: return@runTransaction CloudJoinOutcome.GroupNotFound
                    val updatedMembers = base.members
                        .filterNot { it.userId == member.userId }
                        .plus(member)
                    val updated = base.copy(
                        members = updatedMembers,
                        serverTotalCount = updatedMembers.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) },
                        updatedAtMillis = maxOf(base.updatedAtMillis, nowMillis)
                    )
                    tx.set(docRef, toFirestoreMap(updated))
                    CloudJoinOutcome.Success(updated)
                }.await()
            }
        }
        return result.fold(
            onSuccess = { outcome ->
                outcome ?: CloudJoinOutcome.Error("Couldn't join group right now. Check internet and try again.")
            },
            onFailure = { err ->
                Log.w(LOG_TAG, "Group $groupId join transaction failed", err)
                CloudJoinOutcome.Error("Couldn't join group right now. Check internet and try again.")
            }
        )
    }

    private suspend fun pushGroupToFirestoreBestEffort(
        context: Context,
        group: GroupItem
    ): Boolean {
        val db = firestoreOrNull(context) ?: return false
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return false
        if (authUser.uid.isBlank()) return false

        cloudGroupWritesForTest.incrementAndGet()
        val pushed = runCatching {
            withTimeoutOrNull(FIRESTORE_OP_TIMEOUT_MS) {
                db.collection("groups")
                    .document(group.id)
                    .set(toFirestoreMap(group))
                    .await()
                true
            } ?: false
        }.onFailure { err ->
            Log.i(LOG_TAG, "Group ${group.id} saved locally (cloud sync deferred: ${err.message})")
        }.getOrDefault(false)
        return pushed
    }

    private suspend fun renameGroupInFirestoreTransactionBestEffort(
        context: Context,
        groupId: String,
        creatorUserId: String,
        newName: String,
        fallbackGroup: GroupItem,
        nowMillis: Long
    ): GroupItem? {
        val db = firestoreOrNull(context) ?: return null
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return null
        if (authUser.uid.isBlank()) return null

        cloudGroupWritesForTest.incrementAndGet()
        val docRef = db.collection("groups").document(groupId)
        return runCatching {
            withTimeoutOrNull(FIRESTORE_OP_TIMEOUT_MS) {
                db.runTransaction { tx ->
                    val snap = tx.get(docRef)
                    val base = if (snap.exists()) parseFirestoreGroupDoc(snap.id, snap.data) else return@runTransaction null
                    if (base == null) return@runTransaction null
                    if (base.creatorUserId.isNotBlank() && base.creatorUserId != creatorUserId) {
                        return@runTransaction null
                    }
                    val updated = base.copy(
                        name = newName,
                        updatedAtMillis = maxOf(base.updatedAtMillis + 1L, nowMillis)
                    )
                    tx.set(docRef, toFirestoreMap(updated))
                    updated
                }.await()
            }
        }.onFailure { err ->
            Log.i(LOG_TAG, "Group $groupId rename deferred (${err.message})")
        }.getOrNull()
    }

    private suspend fun deleteGroupInFirestoreBestEffort(
        context: Context,
        groupId: String
    ) {
        val db = firestoreOrNull(context) ?: return
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return
        if (authUser.uid.isBlank()) return

        cloudGroupWritesForTest.incrementAndGet()
        runCatching {
            withTimeoutOrNull(FIRESTORE_OP_TIMEOUT_MS) {
                db.collection("groups").document(groupId).delete().await()
            }
        }.onFailure { err ->
            Log.i(LOG_TAG, "Group $groupId cloud delete deferred (${err.message})")
        }
    }

    private suspend fun upsertMemberStatsTransactionBestEffort(
        context: Context,
        groupId: String,
        member: GroupMemberProgress
    ): Boolean {
        val db = firestoreOrNull(context) ?: return false
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return false
        if (authUser.uid.isBlank()) return false

        cloudGroupWritesForTest.incrementAndGet()
        val docRef = db.collection("groups").document(groupId)
        val mergedGroup = runCatching {
            withTimeoutOrNull(3_500L) {
                db.runTransaction { tx ->
                    val snap = tx.get(docRef)
                    val remote = if (snap.exists()) parseFirestoreGroupDoc(snap.id, snap.data) else null
                    val base = remote ?: return@runTransaction null
                    val updatedMembers = base.members
                        .filterNot { it.userId == member.userId }
                        .plus(member)
                    val newServerSum = updatedMembers.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) }
                    val updated = base.copy(
                        members = updatedMembers,
                        serverTotalCount = newServerSum,
                        updatedAtMillis = maxOf(base.updatedAtMillis, member.updatedAtMillis)
                    )
                    tx.set(docRef, toFirestoreMap(updated))
                    updated
                }.await()
            }
        }.onFailure { err ->
            Log.i(LOG_TAG, "Group $groupId tx deferred (${err.message})")
        }.getOrNull()

        if (mergedGroup != null) {
            upsertRemoteGroupLocally(context, mergedGroup)
            return true
        } else {
            return false
        }
    }

    private suspend fun removeMemberInFirestoreTransactionBestEffort(
        context: Context,
        groupId: String,
        userId: String,
        nowMillis: Long
    ) {
        val db = firestoreOrNull(context) ?: return
        val authUser = runCatching { Firebase.auth.currentUser }.getOrNull() ?: return
        if (authUser.uid.isBlank()) return

        cloudGroupWritesForTest.incrementAndGet()
        val docRef = db.collection("groups").document(groupId)
        runCatching {
            withTimeoutOrNull(3_500L) {
                db.runTransaction { tx ->
                    val snap = tx.get(docRef)
                    val base = if (snap.exists()) parseFirestoreGroupDoc(snap.id, snap.data) else return@runTransaction null
                    if (base == null) return@runTransaction null
                    val remaining = base.members.filterNot { it.userId == userId }
                    val updated = base.copy(
                        members = remaining,
                        serverTotalCount = remaining.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) },
                        updatedAtMillis = maxOf(base.updatedAtMillis + 1L, nowMillis)
                    )
                    tx.set(docRef, toFirestoreMap(updated))
                    updated
                }.await()
            }
        }.onFailure { err ->
            Log.i(LOG_TAG, "Group $groupId leave deferred (${err.message})")
        }
    }

    internal fun mergeRemoteAndLocalGroup(local: GroupItem?, remote: GroupItem): GroupItem {
        if (local == null) return remote
        val localByUid = local.members.associateBy { it.userId }
        val mergedFromRemote = remote.members.map { remoteMember ->
            val localMember = localByUid[remoteMember.userId]
            if (localMember != null && localMember.updatedAtMillis > remoteMember.updatedAtMillis) {
                localMember
            } else {
                remoteMember
            }
        }
        val mergedMembers = if (remote.updatedAtMillis >= local.updatedAtMillis) {
            mergedFromRemote
        } else {
            val remoteUids = remote.members.mapTo(mutableSetOf()) { it.userId }
            mergedFromRemote + local.members.filter { it.userId !in remoteUids }
        }
        val summed = mergedMembers.sumOf { it.lifetimeTotalCount.coerceAtLeast(0L) }
        val resolvedServerTotal = if (remote.updatedAtMillis >= local.updatedAtMillis) {
            (remote.serverTotalCount ?: summed).coerceAtLeast(0L)
        } else {
            summed
        }
        val isRemoteNewer = remote.updatedAtMillis >= local.updatedAtMillis
        val resolvedName = if (isRemoteNewer) remote.name else local.name
        val resolvedUpdatedAt = maxOf(remote.updatedAtMillis, local.updatedAtMillis)
        return if (isRemoteNewer) {
            remote.copy(
                name = resolvedName,
                members = mergedMembers,
                serverTotalCount = resolvedServerTotal,
                updatedAtMillis = resolvedUpdatedAt
            )
        } else {
            local.copy(
                name = resolvedName,
                members = mergedMembers,
                serverTotalCount = resolvedServerTotal,
                updatedAtMillis = resolvedUpdatedAt
            )
        }
    }

    private fun toFirestoreMap(group: GroupItem): Map<String, Any?> {
        return mapOf(
            "id" to group.id,
            "name" to group.name,
            "inviteCode" to GroupLogic.normalizeInviteCode(group.inviteCode),
            "type" to group.type.wireValue,
            "zikrName" to group.zikrName,
            "targetCount" to group.targetCount,
            "creatorUserId" to group.creatorUserId,
            "memberIds" to group.members.map { it.userId },
            "serverTotalCount" to group.effectiveGroupTotal,
            "createdAtMillis" to group.createdAtMillis,
            "updatedAtMillis" to group.updatedAtMillis,
            "members" to group.members.map { member ->
                mapOf(
                    "userId" to member.userId,
                    "displayName" to member.displayName,
                    "todayCount" to member.todayCount,
                    "todayDateKey" to member.todayDateKey,
                    "lifetimeTotalCount" to member.lifetimeTotalCount,
                    "weeklyCount" to member.weeklyCount,
                    "weeklyDateKey" to member.weeklyDateKey,
                    "updatedAtMillis" to member.updatedAtMillis
                )
            }
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseFirestoreGroupDoc(docId: String, data: Map<String, Any?>?): GroupItem? {
        if (data == null) return null
        val name = (data["name"] as? String)?.trim().orEmpty()
        val inviteCode = GroupLogic.normalizeInviteCode((data["inviteCode"] as? String).orEmpty())
        if (name.isEmpty() || inviteCode.isEmpty()) return null
        val type = GroupType.fromWire(data["type"] as? String)
        val zikrName = (data["zikrName"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val targetCount = (data["targetCount"] as? Number)?.toLong() ?: DuroodRepository.ONE_CRORE
        val creatorUserId = (data["creatorUserId"] as? String).orEmpty()
        val serverTotalCount = (data["serverTotalCount"] as? Number)?.toLong()
        val createdAt = (data["createdAtMillis"] as? Number)?.toLong() ?: 0L
        val updatedAt = (data["updatedAtMillis"] as? Number)?.toLong() ?: 0L
        val rawMembers = data["members"] as? List<Map<String, Any?>> ?: emptyList()
        val members = rawMembers.mapNotNull { m ->
            val uid = (m["userId"] as? String)?.trim().orEmpty()
            if (uid.isEmpty()) return@mapNotNull null
            GroupMemberProgress(
                userId = uid,
                displayName = (m["displayName"] as? String)?.trim()?.ifBlank { "Friend" } ?: "Friend",
                todayCount = (m["todayCount"] as? Number)?.toLong() ?: 0L,
                todayDateKey = (m["todayDateKey"] as? String).orEmpty(),
                lifetimeTotalCount = (m["lifetimeTotalCount"] as? Number)?.toLong() ?: 0L,
                weeklyCount = (m["weeklyCount"] as? Number)?.toLong() ?: 0L,
                weeklyDateKey = (m["weeklyDateKey"] as? String) ?: "",
                updatedAtMillis = (m["updatedAtMillis"] as? Number)?.toLong() ?: 0L
            )
        }
        return GroupItem(
            id = docId,
            name = name,
            inviteCode = inviteCode,
            type = type,
            zikrName = zikrName,
            targetCount = targetCount,
            creatorUserId = creatorUserId,
            members = members,
            serverTotalCount = serverTotalCount,
            createdAtMillis = createdAt,
            updatedAtMillis = updatedAt
        )
    }

    private fun encodeGroupJson(group: GroupItem): JSONObject {
        val membersArr = JSONArray()
        group.members.forEach { m ->
            membersArr.put(
                JSONObject().apply {
                    put("userId", m.userId)
                    put("displayName", m.displayName)
                    put("todayCount", m.todayCount)
                    put("todayDateKey", m.todayDateKey)
                    put("lifetimeTotalCount", m.lifetimeTotalCount)
                    put("weeklyCount", m.weeklyCount)
                    put("weeklyDateKey", m.weeklyDateKey)
                    put("updatedAtMillis", m.updatedAtMillis)
                }
            )
        }
        return JSONObject().apply {
            put("id", group.id)
            put("name", group.name)
            put("inviteCode", group.inviteCode)
            put("type", group.type.wireValue)
            if (group.zikrName != null) put("zikrName", group.zikrName)
            put("targetCount", group.targetCount)
            put("creatorUserId", group.creatorUserId)
            if (group.serverTotalCount != null) put("serverTotalCount", group.serverTotalCount)
            put("createdAtMillis", group.createdAtMillis)
            put("updatedAtMillis", group.updatedAtMillis)
            put("members", membersArr)
        }
    }

    private fun decodeGroupJson(obj: JSONObject): GroupItem? {
        val id = obj.optString("id", "").trim()
        val name = obj.optString("name", "").trim()
        val inviteCode = GroupLogic.normalizeInviteCode(obj.optString("inviteCode", ""))
        if (id.isEmpty() || name.isEmpty() || inviteCode.isEmpty()) return null
        val membersArr = obj.optJSONArray("members") ?: JSONArray()
        val members = buildList {
            for (i in 0 until membersArr.length()) {
                val m = membersArr.optJSONObject(i) ?: continue
                val uid = m.optString("userId", "").trim()
                if (uid.isEmpty()) continue
                add(
                    GroupMemberProgress(
                        userId = uid,
                        displayName = m.optString("displayName", "Friend").ifBlank { "Friend" },
                        todayCount = m.optLong("todayCount", 0L),
                        todayDateKey = m.optString("todayDateKey", ""),
                        lifetimeTotalCount = m.optLong("lifetimeTotalCount", 0L),
                        weeklyCount = m.optLong("weeklyCount", 0L),
                        weeklyDateKey = m.optString("weeklyDateKey", ""),
                        updatedAtMillis = m.optLong("updatedAtMillis", 0L)
                    )
                )
            }
        }
        val zikrRaw = obj.optString("zikrName", "").trim().takeIf { it.isNotEmpty() }
        val serverTotal = if (obj.has("serverTotalCount")) obj.optLong("serverTotalCount", 0L) else null
        return GroupItem(
            id = id,
            name = name,
            inviteCode = inviteCode,
            type = GroupType.fromWire(obj.optString("type", "")),
            zikrName = zikrRaw,
            targetCount = obj.optLong("targetCount", DuroodRepository.ONE_CRORE),
            creatorUserId = obj.optString("creatorUserId", ""),
            members = members,
            serverTotalCount = serverTotal,
            createdAtMillis = obj.optLong("createdAtMillis", 0L),
            updatedAtMillis = obj.optLong("updatedAtMillis", 0L)
        )
    }

    fun resetForTest(context: Context) {
        lastSharedInviteMessageForTest = null
        activeGroupListenersForTest.set(0)
        cloudGroupWritesForTest.set(0)
        singleGroupRefreshesForTest.set(0)
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
