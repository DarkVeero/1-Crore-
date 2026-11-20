package com.example.floating

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.local.AppDatabase
import com.example.groups.GroupItem
import com.example.groups.GroupRepository
import com.example.groups.GroupType
import com.example.sync.NetworkMonitor
import com.example.sync.SyncWorker
import com.example.widget.OneCroreWidgetProvider
import com.example.widget.WidgetAndFloatingBridge
import com.example.widget.WidgetCountsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class FloatingTargetMode {
    MAIN,
    GROUP
}

data class FloatingBubbleUiState(
    val isRunning: Boolean = false,
    val mode: FloatingTargetMode = FloatingTargetMode.MAIN,
    val activeGroupId: String? = null,
    val activeGroupName: String? = null,
    val activeGroupTotal: Long = 0L,
    val sessionCount: Long = 0L,
    val flashingNumber: Long? = null,
    val tapPulseTick: Long = 0L,
    val isDragging: Boolean = false,
    val isOverCloseTarget: Boolean = false
) {
    val isMainRunning: Boolean
        get() = isRunning && mode == FloatingTargetMode.MAIN

    fun isGroupRunning(groupId: String): Boolean =
        isRunning && mode == FloatingTargetMode.GROUP && activeGroupId == groupId
}

/**
 * Manages the Floating Counter Button state (Main Durood OR one specific Main Durood group,
 * strictly mutually exclusive), tap feedback (session flash for Main, live total + pulse for Group),
 * drag-to-close target tracking at the bottom-center of the screen, and the Android overlay permission flow.
 */
object FloatingCounterManager {
    const val FLASH_DURATION_MILLIS = 750L
    const val CLOSE_TARGET_RESTING_SIZE_DP = 56f
    const val CLOSE_TARGET_HOVERED_SIZE_DP = 68f
    const val CLOSE_TARGET_HIT_RADIUS_DP = 64f
    const val CLOSE_TARGET_BOTTOM_MARGIN_DP = 44f

    private const val PREFS_NAME = "one_crore_floating_counter_prefs"
    private const val KEY_IS_RUNNING = "floating_is_running"
    private const val KEY_TARGET_MODE = "floating_target_mode"
    private const val KEY_TARGET_GROUP_ID = "floating_target_group_id"
    private const val KEY_PENDING_PERMISSION = "floating_pending_overlay_permission"
    private const val KEY_PENDING_MODE = "floating_pending_mode"
    private const val KEY_PENDING_GROUP_ID = "floating_pending_group_id"
    private const val KEY_PERMISSION_ASKED_ONCE = "floating_overlay_permission_asked_once"

    private const val MODE_MAIN_WIRE = "main"
    private const val MODE_GROUP_WIRE = "group"

    private val groupSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(FloatingBubbleUiState())
    val state: StateFlow<FloatingBubbleUiState> = _state.asStateFlow()

    @Volatile
    private var overlayPermissionOverrideForTest: Boolean? = null

    fun setOverlayPermissionOverrideForTest(granted: Boolean?) {
        overlayPermissionOverrideForTest = granted
    }

    fun canDrawOverlays(context: Context): Boolean {
        overlayPermissionOverrideForTest?.let { return it }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isRunning(context: Context): Boolean {
        return _state.value.isRunning || FloatingOverlayController.isOverlayShowing()
    }

    fun isMainRunning(context: Context): Boolean {
        return _state.value.isMainRunning ||
            (FloatingOverlayController.isOverlayShowing() && _state.value.mode == FloatingTargetMode.MAIN)
    }

    fun isGroupRunning(groupId: String): Boolean {
        return _state.value.isGroupRunning(groupId)
    }

    fun isPendingStartAfterPermission(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING_PERMISSION, false)
    }

    fun setPendingStartAfterPermission(context: Context, pending: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_PERMISSION, pending)
            .apply {
                if (pending) {
                    putString(KEY_PENDING_MODE, MODE_MAIN_WIRE)
                    remove(KEY_PENDING_GROUP_ID)
                } else {
                    remove(KEY_PENDING_MODE)
                    remove(KEY_PENDING_GROUP_ID)
                }
            }
            .apply()
    }

    private fun setPendingGroupStartAfterPermission(context: Context, groupId: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING_PERMISSION, true)
            .putString(KEY_PENDING_MODE, MODE_GROUP_WIRE)
            .putString(KEY_PENDING_GROUP_ID, groupId)
            .apply()
    }

    fun hasAskedOverlayPermissionOnce(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERMISSION_ASKED_ONCE, false)
    }

    private fun markOverlayPermissionAsked(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERMISSION_ASKED_ONCE, true)
            .apply()
    }

    /**
     * Turns the Main Durood floating button ON if overlay permission is granted;
     * otherwise requests "Display over other apps" permission once and remembers to start on return.
     * Mutually exclusive: turns off any active group floating button when started.
     */
    fun requestEnableFloatingButton(
        context: Context,
        openSettingsIfNeeded: Boolean = true
    ): Boolean {
        val appContext = context.applicationContext
        if (canDrawOverlays(appContext)) {
            setPendingStartAfterPermission(appContext, false)
            startFloatingButton(appContext)
            return true
        }
        setPendingStartAfterPermission(appContext, true)
        if (openSettingsIfNeeded) {
            markOverlayPermissionAsked(appContext)
            openOverlayPermissionSettings(context)
        }
        return false
    }

    /**
     * Turns a specific group's floating button ON if overlay permission is granted;
     * mutually exclusive with Main and other groups.
     */
    fun requestEnableGroupFloatingButton(
        context: Context,
        group: GroupItem,
        openSettingsIfNeeded: Boolean = true
    ): Boolean {
        val appContext = context.applicationContext
        if (canDrawOverlays(appContext)) {
            setPendingStartAfterPermission(appContext, false)
            startGroupFloatingButton(appContext, group)
            return true
        }
        setPendingGroupStartAfterPermission(appContext, group.id)
        if (openSettingsIfNeeded) {
            markOverlayPermissionAsked(appContext)
            openOverlayPermissionSettings(context)
        }
        return false
    }

    fun onHostResumeCheckPermission(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (isPendingStartAfterPermission(appContext)) {
            val pendingMode = prefs.getString(KEY_PENDING_MODE, MODE_MAIN_WIRE)
            val pendingGroupId = prefs.getString(KEY_PENDING_GROUP_ID, null)
            setPendingStartAfterPermission(appContext, false)
            if (canDrawOverlays(appContext)) {
                if (pendingMode == MODE_GROUP_WIRE && !pendingGroupId.isNullOrBlank()) {
                    val targetGroup = GroupRepository.getGroupById(appContext, pendingGroupId)
                    if (targetGroup != null) {
                        startGroupFloatingButton(appContext, targetGroup)
                        return true
                    }
                    return false
                }
                startFloatingButton(appContext)
                return true
            }
        } else if (isRunning(appContext) && !canDrawOverlays(appContext)) {
            stopFloatingButton(appContext)
        }
        return false
    }

    fun openOverlayPermissionSettings(context: Context): Boolean {
        return try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun startFloatingButton(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_RUNNING, true)
            .putString(KEY_TARGET_MODE, MODE_MAIN_WIRE)
            .remove(KEY_TARGET_GROUP_ID)
            .putBoolean(KEY_PENDING_PERMISSION, false)
            .remove(KEY_PENDING_MODE)
            .remove(KEY_PENDING_GROUP_ID)
            .apply()

        _state.value = FloatingBubbleUiState(
            isRunning = true,
            mode = FloatingTargetMode.MAIN,
            activeGroupId = null,
            activeGroupName = null,
            activeGroupTotal = 0L,
            sessionCount = 0L,
            flashingNumber = null,
            tapPulseTick = 0L,
            isDragging = false,
            isOverCloseTarget = false
        )

        runCatching {
            FloatingOverlayController.showOverlayViewsIfNeeded(appContext)
        }

        runCatching {
            val serviceIntent = Intent(appContext, FloatingCounterService::class.java).apply {
                action = FloatingCounterService.ACTION_SHOW
            }
            appContext.startService(serviceIntent)
        }

        runCatching {
            OneCroreWidgetProvider.updateAllWidgetsState(appContext)
        }
    }

    fun startGroupFloatingButton(context: Context, group: GroupItem) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_RUNNING, true)
            .putString(KEY_TARGET_MODE, MODE_GROUP_WIRE)
            .putString(KEY_TARGET_GROUP_ID, group.id)
            .putBoolean(KEY_PENDING_PERMISSION, false)
            .remove(KEY_PENDING_MODE)
            .remove(KEY_PENDING_GROUP_ID)
            .apply()

        _state.value = FloatingBubbleUiState(
            isRunning = true,
            mode = FloatingTargetMode.GROUP,
            activeGroupId = group.id,
            activeGroupName = group.name,
            activeGroupTotal = group.effectiveGroupTotal,
            sessionCount = 0L,
            flashingNumber = null,
            tapPulseTick = 0L,
            isDragging = false,
            isOverCloseTarget = false
        )

        runCatching {
            FloatingOverlayController.showOverlayViewsIfNeeded(appContext)
        }

        runCatching {
            val serviceIntent = Intent(appContext, FloatingCounterService::class.java).apply {
                action = FloatingCounterService.ACTION_SHOW
            }
            appContext.startService(serviceIntent)
        }

        // Main floating button is now OFF -> ensure home-screen widget reflects OFF state
        runCatching {
            OneCroreWidgetProvider.updateAllWidgetsState(appContext)
        }
    }

    internal fun restorePersistedFloatingMode(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_TARGET_MODE, MODE_MAIN_WIRE)
        val savedGroupId = prefs.getString(KEY_TARGET_GROUP_ID, null)
        if (savedMode == MODE_GROUP_WIRE && !savedGroupId.isNullOrBlank()) {
            val group = GroupRepository.getGroupById(appContext, savedGroupId)
            if (group != null) {
                startGroupFloatingButton(appContext, group)
                return
            }
            stopFloatingButton(appContext)
            return
        }
        startFloatingButton(appContext)
    }

    fun stopFloatingButton(context: Context) {
        val appContext = context.applicationContext
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_RUNNING, false)
            .remove(KEY_TARGET_MODE)
            .remove(KEY_TARGET_GROUP_ID)
            .putBoolean(KEY_PENDING_PERMISSION, false)
            .remove(KEY_PENDING_MODE)
            .remove(KEY_PENDING_GROUP_ID)
            .apply()

        _state.value = FloatingBubbleUiState(
            isRunning = false,
            mode = FloatingTargetMode.MAIN,
            activeGroupId = null,
            activeGroupName = null,
            activeGroupTotal = 0L,
            sessionCount = 0L,
            flashingNumber = null,
            tapPulseTick = 0L,
            isDragging = false,
            isOverCloseTarget = false
        )

        runCatching {
            FloatingOverlayController.removeOverlayViews()
        }

        runCatching {
            val serviceIntent = Intent(appContext, FloatingCounterService::class.java)
            appContext.stopService(serviceIntent)
        }

        runCatching {
            OneCroreWidgetProvider.updateAllWidgetsState(appContext)
        }
    }

    fun stopGroupFloatingButtonIfActive(context: Context) {
        val current = _state.value
        if (current.isRunning && current.mode == FloatingTargetMode.GROUP) {
            stopFloatingButton(context)
        }
    }

    fun onGroupRemovedOrLeft(context: Context, groupId: String) {
        val current = _state.value
        if (current.isRunning && current.mode == FloatingTargetMode.GROUP && current.activeGroupId == groupId) {
            stopFloatingButton(context)
        }
    }

    fun syncActiveGroupState(context: Context, currentUserId: String? = null) {
        val current = _state.value
        if (!current.isRunning || current.mode != FloatingTargetMode.GROUP) return
        val groupId = current.activeGroupId ?: return
        val appContext = context.applicationContext
        val group = GroupRepository.getGroupById(appContext, groupId)
        val isMember = currentUserId.isNullOrBlank() ||
            group?.members?.any { it.userId == currentUserId } == true
        if (group == null || !isMember) {
            stopFloatingButton(appContext)
            return
        }
        val latestTotal = group.effectiveGroupTotal
        if (latestTotal != current.activeGroupTotal || group.name != current.activeGroupName) {
            _state.value = current.copy(
                activeGroupName = group.name,
                activeGroupTotal = latestTotal
            )
            runCatching {
                FloatingOverlayController.syncOverlayPresentation(appContext)
            }
        }
    }

    fun toggleFloatingButton(context: Context): Boolean {
        return if (isMainRunning(context)) {
            stopFloatingButton(context)
            false
        } else {
            requestEnableFloatingButton(context)
        }
    }

    fun toggleGroupFloatingButton(context: Context, group: GroupItem): Boolean {
        return if (isGroupRunning(group.id)) {
            stopFloatingButton(context)
            false
        } else {
            requestEnableGroupFloatingButton(context, group)
        }
    }

    /**
     * Records a tap on the active floating button:
     * - In MAIN mode: increments the floating session counter (1, 2, ..., 20, 21, ...), flashes that number,
     *   and writes +1 to the shared Room database + updates the widget + enqueues cloud sync.
     * - In GROUP mode: increments ONLY that specific MAIN_DUROOD group (+1) locally with batched Firestore sync,
     *   updates the live group total on the floating pill, and NEVER touches Main Durood, other groups, or custom zikr.
     */
    suspend fun onFloatingButtonTapped(
        context: Context,
        db: AppDatabase = AppDatabase.getInstance(context),
        userIdOverride: String? = null,
        todayKey: String = LocalDate.now().toString()
    ): Pair<Long, WidgetCountsSnapshot> {
        val appContext = context.applicationContext
        val current = _state.value
        if (current.mode == FloatingTargetMode.GROUP && !current.activeGroupId.isNullOrBlank()) {
            val groupId = current.activeGroupId
            val userId = WidgetAndFloatingBridge.resolveUserId(appContext, db, userIdOverride)
            val existingGroup = GroupRepository.getGroupById(appContext, groupId)
            if (existingGroup == null) {
                stopFloatingButton(appContext)
                val snap = WidgetAndFloatingBridge.readCountsFromDatabase(appContext, db, userIdOverride, todayKey)
                return 0L to snap
            }
            val profileName = runCatching {
                db.userProfileDao().getProfile(userId)?.displayName
            }.getOrNull()
            val memberName = existingGroup.members.firstOrNull { it.userId == userId }?.displayName
            val displayName = profileName?.takeIf { it.isNotBlank() }
                ?: memberName?.takeIf { it.isNotBlank() }
                ?: "Friend"

            val adjustResult = GroupRepository.adjustMainGroupCount(
                context = appContext,
                userId = userId,
                displayName = displayName,
                groupId = groupId,
                delta = 1L,
                todayDateKey = todayKey,
                pushToCloud = false
            )

            val updatedGroup = adjustResult.getOrNull() ?: existingGroup
            if (adjustResult.isSuccess) {
                GroupRepository.markGroupDirtyForSync(appContext, groupId)
                SyncWorker.enqueueWhenOnline(appContext)
                if (GroupRepository.recordFloatingTapForGroupBatchSync(appContext) &&
                    NetworkMonitor.isCurrentlyOnline(appContext)
                ) {
                    groupSyncScope.launch {
                        runCatching {
                            GroupRepository.flushDirtyGroupsToCloud(appContext, userId)
                        }
                    }
                }
            }

            val updatedTotal = updatedGroup.effectiveGroupTotal
            _state.value = _state.value.copy(
                isRunning = true,
                mode = FloatingTargetMode.GROUP,
                activeGroupId = updatedGroup.id,
                activeGroupName = updatedGroup.name,
                activeGroupTotal = updatedTotal,
                sessionCount = _state.value.sessionCount + 1L,
                flashingNumber = null,
                tapPulseTick = _state.value.tapPulseTick + 1L,
                isDragging = false,
                isOverCloseTarget = false
            )
            runCatching {
                FloatingOverlayController.syncOverlayPresentation(appContext)
            }
            val readOnlyMainSnapshot = WidgetAndFloatingBridge.readCountsFromDatabase(
                context = appContext,
                db = db,
                userIdOverride = userIdOverride,
                todayKey = todayKey
            )
            return updatedTotal to readOnlyMainSnapshot
        }

        val nextSessionCount = _state.value.sessionCount + 1L
        _state.value = _state.value.copy(
            isRunning = true,
            mode = FloatingTargetMode.MAIN,
            sessionCount = nextSessionCount,
            flashingNumber = nextSessionCount,
            isDragging = false,
            isOverCloseTarget = false
        )
        val snapshot = WidgetAndFloatingBridge.incrementCountAndSync(
            context = appContext,
            delta = 1L,
            db = db,
            userIdOverride = userIdOverride,
            todayKey = todayKey
        )
        return nextSessionCount to snapshot
    }

    fun onFlashFinished(expectedNumber: Long? = null) {
        val current = _state.value
        if (expectedNumber == null || current.flashingNumber == expectedNumber) {
            _state.value = current.copy(flashingNumber = null)
        }
    }

    fun onDragStarted() {
        val current = _state.value
        if (current.isRunning) {
            _state.value = current.copy(
                isDragging = true,
                isOverCloseTarget = false
            )
        }
    }

    fun onDragTargetHoverChanged(hovered: Boolean) {
        val current = _state.value
        if (current.isRunning && current.isDragging && current.isOverCloseTarget != hovered) {
            _state.value = current.copy(isOverCloseTarget = hovered)
        }
    }

    fun onDragEnded(context: Context, droppedOnClose: Boolean): Boolean {
        val current = _state.value
        if (!current.isRunning) return false
        if (droppedOnClose) {
            stopFloatingButton(context)
            return true
        }
        _state.value = current.copy(
            isDragging = false,
            isOverCloseTarget = false
        )
        return false
    }

    fun isBubbleOverCloseTarget(
        bubbleCenterX: Float,
        bubbleCenterY: Float,
        targetCenterX: Float,
        targetCenterY: Float,
        hitRadiusPx: Float
    ): Boolean {
        if (hitRadiusPx <= 0f) return false
        val dx = bubbleCenterX - targetCenterX
        val dy = bubbleCenterY - targetCenterY
        return (dx * dx + dy * dy) <= (hitRadiusPx * hitRadiusPx)
    }

    fun resetForTest(context: Context) {
        overlayPermissionOverrideForTest = null
        runCatching { FloatingOverlayController.removeOverlayViews() }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        _state.value = FloatingBubbleUiState()
    }
}
