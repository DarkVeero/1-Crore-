package com.example.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.announcements.AnnouncementAction
import com.example.announcements.AnnouncementAudienceContext
import com.example.announcements.AnnouncementFrequencyStore
import com.example.announcements.AnnouncementHistoryEntry
import com.example.announcements.AnnouncementHistoryStore
import com.example.announcements.AnnouncementItem
import com.example.announcements.AnnouncementLaunchRequest
import com.example.announcements.AnnouncementScheduleStore
import com.example.announcements.AnnouncementScheduledDispatcher
import com.example.announcements.MilestoneAnnouncementManager
import com.example.badges.MilestoneBadge
import com.example.badges.MilestoneBadgeManager
import com.example.badges.MilestoneBadgeProgress
import com.example.data.DuroodRepository
import com.example.data.GlobalCounterStats
import com.example.data.GlobalStatsCache
import com.example.data.local.CustomCounterEntity
import com.example.data.local.DailyCountEntity
import com.example.data.local.UserProfileEntity
import com.example.groups.GroupCelebrationNotice
import com.example.groups.GroupCelebrationStore
import com.example.groups.GroupItem
import com.example.groups.GroupLogic
import com.example.groups.GroupRepository
import com.example.groups.GroupType
import com.example.notifications.ReminderScheduler
import com.example.sync.NetworkMonitor
import com.example.sync.SyncWorker
import com.example.ui.auth.AuthFlowStore
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemePreferenceStore
import com.example.update.ApkDownloadState
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateManager
import com.example.widget.OneCroreWidgetProvider
import com.example.widget.WidgetAndFloatingBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.TimeoutException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

enum class PostLoginDestination {
    LOGO_LOADING,
    OFFLINE_LOAD_ERROR,
    LINK_BACKUP_PASSWORD,
    SETUP,
    HOME
}

fun resolvePostLoginDestination(
    isLoading: Boolean,
    hasOfflineLoadError: Boolean,
    hasExistingSetupOrData: Boolean,
    isEmailSignIn: Boolean,
    isGoogleSignedIn: Boolean,
    hasPasswordLinked: Boolean,
    hasSkippedBackupPasswordPrompt: Boolean
): PostLoginDestination {
    if (isLoading) {
        return PostLoginDestination.LOGO_LOADING
    }
    if (hasOfflineLoadError && !hasExistingSetupOrData) {
        return PostLoginDestination.OFFLINE_LOAD_ERROR
    }
    if (hasExistingSetupOrData || isEmailSignIn) {
        return PostLoginDestination.HOME
    }
    if (isGoogleSignedIn && !hasPasswordLinked && !hasSkippedBackupPasswordPrompt) {
        return PostLoginDestination.LINK_BACKUP_PASSWORD
    }
    return PostLoginDestination.SETUP
}

data class MainUiState(
    val isLoading: Boolean = true,
    val initialLoadOfflineError: Boolean = false,
    val profile: UserProfileEntity? = null,
    val allDays: List<DailyCountEntity> = emptyList(),
    val customCounters: List<CustomCounterEntity> = emptyList(),
    val totalCount: Long = 0L,
    val todayCount: Long = 0L,
    val hasPendingSync: Boolean = false,
    val pendingSyncDaysCount: Int = 0,
    val pendingSyncDuroodCount: Long = 0L,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val accountCreatedAtMillis: Long = 0L,
    val currentDate: LocalDate = LocalDate.now(),
    val availableUpdate: AppUpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val isUpdateCheckSettled: Boolean = true,
    val isPreparingUpdate: Boolean = false,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Float? = null,
    val updateDownloadStatusText: String? = null,
    val needsInstallUnknownAppsPermission: Boolean = false,
    val pendingApkInstallReady: Boolean = false,
    val updateErrorMessage: String? = null,
    val globalStats: GlobalCounterStats? = null,
    val activeAnnouncement: AnnouncementItem? = null,
    val isDirectAnnouncement: Boolean = false,
    val announcementHistory: List<AnnouncementHistoryEntry> = emptyList(),
    val badgeProgress: MilestoneBadgeProgress = MilestoneBadgeManager.evaluatePureProgress(0L),
    val pendingMilestoneBadge: MilestoneBadge? = null,
    val userGroups: List<GroupItem> = emptyList(),
    val pendingGroupCelebration: GroupCelebrationNotice? = null,
    val isGroupRefreshing: Boolean = false,
    val isJoiningGroup: Boolean = false,
    val themeMode: AppThemeMode = AppThemeMode.BLACK_AND_WHITE
) {
    val displayName: String
        get() = profile?.displayName?.ifBlank { "Friend" } ?: "Friend"

    val dailyTarget: Int
        get() = profile?.dailyTarget?.coerceAtLeast(1) ?: DuroodRepository.DEFAULT_DAILY_TARGET

    val setupCompleted: Boolean
        get() = profile?.setupCompleted ?: false

    val hasExistingDataOrSetup: Boolean
        get() = setupCompleted || totalCount > 0L || allDays.isNotEmpty() || customCounters.isNotEmpty()

    val remaining: Long
        get() = (DuroodRepository.ONE_CRORE - totalCount).coerceAtLeast(0L)

    val progressFraction: Float
        get() = (totalCount.toDouble() / DuroodRepository.ONE_CRORE.toDouble()).toFloat().coerceIn(0f, 1f)

    val progressPercentFormatted: String
        get() = String.format(java.util.Locale.US, "%.2f%%", progressFraction * 100f)

    val daysLeft: Long
        get() = if (remaining <= 0L) 0L else ceil(remaining.toDouble() / dailyTarget.toDouble()).toLong()

    val estimatedFinishDate: LocalDate
        get() = currentDate.plusDays(daysLeft)

    val todayRemaining: Long
        get() = (dailyTarget.toLong() - todayCount).coerceAtLeast(0L)

    val activeDaysCount: Int
        get() = allDays.count { it.count > 0L }

    val bestDay: DailyCountEntity?
        get() = allDays.filter { it.count > 0L }.maxByOrNull { it.count }

    val averagePerActiveDay: Long
        get() {
            val active = activeDaysCount
            return if (active == 0) 0L else allDays.sumOf { it.count } / active
        }

    val currentStreak: Int
        get() {
            val target = dailyTarget.toLong()
            val byDate = allDays.associateBy { it.dateKey }
            var cursor = currentDate
            val todayDone = (byDate[cursor.toString()]?.count ?: 0L) >= target
            if (!todayDone) {
                cursor = cursor.minusDays(1)
            }
            var streak = 0
            while (true) {
                val entry = byDate[cursor.toString()]
                if (entry != null && entry.count >= target) {
                    streak++
                    cursor = cursor.minusDays(1)
                } else {
                    break
                }
            }
            return streak
        }

    val startDateFormatted: String?
        get() {
            val earliestDayMillis = allDays.minOfOrNull { it.dateKey }?.let { key ->
                try {
                    LocalDate.parse(key)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
            val millis = profile?.startedAtMillis?.takeIf { it > 0L }
                ?: accountCreatedAtMillis.takeIf { it > 0L }
                ?: earliestDayMillis
                ?: profile?.updatedAtMillis?.takeIf { it > 0L }
                ?: return null
            return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
        }
}

class MainViewModel(
    private val repository: DuroodRepository,
    private val currentUserId: String,
    private val fallbackDisplayName: String,
    private val accountCreatedAtMillis: Long,
    private val appContext: Context,
    private val isEmailSignInProvider: Boolean = false,
    private val isGoogleSignedInProvider: Boolean = false
) : ViewModel() {

    private val currentDateFlow = MutableStateFlow(LocalDate.now())
    private val isSyncingFlow = MutableStateFlow(false)
    private val syncInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private var syncJob: Job? = null
    private var syncStartedAtMillis: Long = 0L
    private val statusMessageFlow = MutableStateFlow<String?>(null)
    private val initializedFlow = MutableStateFlow(false)
    private val initialLoadOfflineErrorFlow = MutableStateFlow(false)
    private val updateStateFlow = MutableStateFlow(UpdateUiState())
    private val globalStatsFlow = MutableStateFlow(GlobalStatsCache.getCached(appContext))
    private val activeAnnouncementFlow = MutableStateFlow<AnnouncementItem?>(null)
    private val isDirectAnnouncementFlow = MutableStateFlow(false)
    private val announcementHistoryFlow = MutableStateFlow(
        AnnouncementHistoryStore.getHistoryNewestFirst(appContext, currentUserId)
    )
    private val badgeRefreshTickFlow = MutableStateFlow(0)
    private val pendingMilestoneBadgeFlow = MutableStateFlow<MilestoneBadge?>(null)
    private val userGroupsFlow = MutableStateFlow(
        GroupRepository.getGroupsForUser(appContext, currentUserId)
    )
    private val pendingGroupCelebrationFlow = MutableStateFlow<GroupCelebrationNotice?>(null)
    private val openGroupIdFlow = MutableStateFlow<String?>(null)
    private val isGroupRefreshingFlow = MutableStateFlow(false)
    private val isJoiningGroupFlow = MutableStateFlow(false)
    private var isCurrentAnnouncementShownRecorded: Boolean = false
    private var unsyncedTapCount: Int = 0
    private var initialLoadJob: Job? = null
    private var globalStatsRefreshJob: Job? = null
    private var globalStatsRetryJob: Job? = null
    private var globalStatsListenerRemover: (() -> Unit)? = null
    private var openGroupListenerRemover: (() -> Unit)? = null
    private var openGroupListenerGroupId: String? = null
    private var openGroupFlushJob: Job? = null
    private var openGroupRefreshJob: Job? = null
    private val dirtyGroupSyncIds = mutableSetOf<String>()
    private var announcementRetryJob: Job? = null
    private var announcementRefreshJob: Job? = null
    private var announcementHistoryRefreshJob: Job? = null
    private var latestCloudAnnouncementsForHistory: List<AnnouncementItem>? = null
    private var lastHistoryCloudFetchMillis: Long = 0L
    private val announcementRefreshToken = AtomicLong(0L)
    private var activeDownloadJob: Job? = null
    private var activeDownloadId: Long? = null
    private var downloadedApkFile: File? = null

    internal var groupFlushDebounceMillis: Long = GROUP_FLUSH_DEBOUNCE_MILLIS
    internal var groupListenerAttachHook: ((String, (GroupItem) -> Unit) -> (() -> Unit)?)? = null
    internal var groupSingleRefreshHook: (suspend (String) -> GroupItem?)? = null
    internal var groupBatchFlushHook: (suspend (String?, String) -> Unit)? = null
    internal val groupBatchFlushCountForTest = java.util.concurrent.atomic.AtomicInteger(0)

    fun isGroupDetailOpen(): Boolean = openGroupIdFlow.value != null
    fun getOpenGroupListenerCountForTest(): Int = if (openGroupListenerRemover != null) 1 else 0

    private val dateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshCurrentDate()
            ensureRemindersScheduled()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val todayStateFlow: Flow<Pair<LocalDate, Long>> = currentDateFlow.flatMapLatest { date ->
        repository.observeDayCount(currentUserId, date.toString()).map { count -> date to count }
    }

    private val pendingSummaryFlow = combine(
        repository.observeHasPendingSync(currentUserId),
        repository.observePendingDaysCount(currentUserId),
        repository.observePendingDuroodTotal(currentUserId)
    ) { hasPending, pendingDaysCount, pendingDuroodTotal ->
        Triple(hasPending, pendingDaysCount, pendingDuroodTotal)
    }

    private val optimisticTapDeltaFlow = MutableStateFlow(0L)
    val historyAllDaysFlow: StateFlow<List<DailyCountEntity>> = repository.observeAllDays(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )
    private val countMetricsFlow = combine(
        repository.observeProfile(currentUserId),
        repository.observeRecentDays(currentUserId, 366),
        repository.observeTotalCount(currentUserId),
        todayStateFlow,
        pendingSummaryFlow,
        optimisticTapDeltaFlow
    ) { args ->
        val profile = args[0] as UserProfileEntity?
        val days = args[1] as List<DailyCountEntity>
        val total = args[2] as Long
        val todayPair = args[3] as Pair<LocalDate, Long>
        val pendingTriple = args[4] as Triple<Boolean, Int, Long>
        val optimisticDelta = args[5] as Long
        CountMetrics(
            profile = profile,
            days = days,
            total = total + optimisticDelta,
            today = todayPair.second + optimisticDelta,
            pending = pendingTriple.first,
            pendingDaysCount = pendingTriple.second,
            pendingDuroodTotal = pendingTriple.third,
            currentDate = todayPair.first
        )
    }

    private val groupUiStateFlow = combine(
        userGroupsFlow,
        pendingGroupCelebrationFlow,
        isGroupRefreshingFlow,
        isJoiningGroupFlow
    ) { groups, pendingGroupCelebration, isRefreshing, isJoining ->
        GroupUiTuple(
            userGroups = groups,
            pendingGroupCelebration = pendingGroupCelebration,
            isGroupRefreshing = isRefreshing,
            isJoiningGroup = isJoining
        )
    }

    private val badgeAndGroupUiFlow = combine(
        repository.observeTotalCount(currentUserId),
        badgeRefreshTickFlow,
        pendingMilestoneBadgeFlow,
        groupUiStateFlow
    ) { total, _, pendingBadge, groupState ->
        BadgeAndGroupUiState(
            badgeProgress = MilestoneBadgeManager.syncAndGetProgress(
                context = appContext,
                userId = currentUserId,
                mainDuroodTotalCount = total
            ),
            pendingMilestoneBadge = pendingBadge,
            userGroups = groupState.userGroups,
            pendingGroupCelebration = groupState.pendingGroupCelebration,
            isGroupRefreshing = groupState.isGroupRefreshing,
            isJoiningGroup = groupState.isJoiningGroup
        )
    }

    private val extraUiFlow = combine(
        updateStateFlow,
        globalStatsFlow,
        activeAnnouncementFlow,
        isDirectAnnouncementFlow,
        announcementHistoryFlow
    ) { updateState, globalStats, activeAnnouncement, isDirectAnnouncement, announcementHistory ->
        ExtraUiState(
            updateState = updateState,
            globalStats = globalStats,
            activeAnnouncement = activeAnnouncement,
            isDirectAnnouncement = isDirectAnnouncement,
            announcementHistory = announcementHistory
        )
    }

    private val baseDataFlow = combine(
        countMetricsFlow,
        repository.observeCustomCounters(currentUserId),
        extraUiFlow,
        badgeAndGroupUiFlow,
        initialLoadOfflineErrorFlow
    ) { metrics, customCounters, extra, badgeAndGroup, offlineLoadError ->
        BaseData(
            profile = metrics.profile,
            days = metrics.days,
            customCounters = customCounters,
            total = metrics.total,
            today = metrics.today,
            pending = metrics.pending,
            pendingDaysCount = metrics.pendingDaysCount,
            pendingDuroodTotal = metrics.pendingDuroodTotal,
            currentDate = metrics.currentDate,
            updateState = extra.updateState,
            initialLoadOfflineError = offlineLoadError,
            globalStats = extra.globalStats,
            activeAnnouncement = extra.activeAnnouncement,
            isDirectAnnouncement = extra.isDirectAnnouncement,
            announcementHistory = extra.announcementHistory,
            badgeProgress = badgeAndGroup.badgeProgress,
            pendingMilestoneBadge = badgeAndGroup.pendingMilestoneBadge,
            userGroups = badgeAndGroup.userGroups,
            pendingGroupCelebration = badgeAndGroup.pendingGroupCelebration,
            isGroupRefreshing = badgeAndGroup.isGroupRefreshing,
            isJoiningGroup = badgeAndGroup.isJoiningGroup
        )
    }

    private val statusAndThemeFlow = combine(
        isSyncingFlow,
        statusMessageFlow,
        initializedFlow,
        ThemePreferenceStore.themeModeFlow(appContext)
    ) { isSyncing, statusMsg, initialized, themeMode ->
        StatusAndThemeUi(
            isSyncing = isSyncing,
            statusMessage = statusMsg,
            initialized = initialized,
            themeMode = themeMode
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        baseDataFlow,
        NetworkMonitor.observeOnline(appContext),
        statusAndThemeFlow
    ) { base, isOnline, statusAndTheme ->
        val hasExisting = (base.profile?.setupCompleted == true) ||
            base.total > 0L ||
            base.days.isNotEmpty() ||
            base.customCounters.isNotEmpty()
        MainUiState(
            isLoading = !statusAndTheme.initialized,
            initialLoadOfflineError = base.initialLoadOfflineError && !hasExisting,
            profile = base.profile,
            allDays = base.days,
            customCounters = base.customCounters,
            totalCount = base.total,
            todayCount = base.today,
            hasPendingSync = base.pending,
            pendingSyncDaysCount = base.pendingDaysCount,
            pendingSyncDuroodCount = base.pendingDuroodTotal,
            isOnline = isOnline,
            isSyncing = statusAndTheme.isSyncing,
            statusMessage = statusAndTheme.statusMessage,
            accountCreatedAtMillis = accountCreatedAtMillis,
            currentDate = base.currentDate,
            availableUpdate = base.updateState.availableUpdate,
            showUpdateDialog = base.updateState.showDialog,
            isUpdateCheckSettled = base.updateState.isCheckSettled,
            isPreparingUpdate = base.updateState.isPreparingUpdate,
            isDownloadingUpdate = base.updateState.isDownloading,
            updateDownloadProgress = base.updateState.downloadProgress,
            updateDownloadStatusText = base.updateState.downloadStatusText,
            needsInstallUnknownAppsPermission = base.updateState.needsInstallPermission,
            pendingApkInstallReady = base.updateState.pendingInstallReady,
            updateErrorMessage = base.updateState.errorMessage,
            globalStats = base.globalStats,
            activeAnnouncement = base.activeAnnouncement,
            isDirectAnnouncement = base.isDirectAnnouncement,
            announcementHistory = base.announcementHistory,
            badgeProgress = base.badgeProgress,
            pendingMilestoneBadge = base.pendingMilestoneBadge,
            userGroups = base.userGroups,
            pendingGroupCelebration = base.pendingGroupCelebration,
            isGroupRefreshing = base.isGroupRefreshing,
            isJoiningGroup = base.isJoiningGroup,
            themeMode = statusAndTheme.themeMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = MainUiState(
            isLoading = true,
            themeMode = ThemePreferenceStore.getCurrentThemeMode(appContext)
        )
    )

    init {
        val dateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            appContext,
            dateChangeReceiver,
            dateFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Roll over automatically at midnight even while the app remains open
        viewModelScope.launch {
            while (true) {
                val zone = ZoneId.systemDefault()
                val now = ZonedDateTime.now(zone)
                val today = now.toLocalDate()
                if (currentDateFlow.value != today) {
                    currentDateFlow.value = today
                }
                val nextMidnight = today.plusDays(1).atStartOfDay(zone)
                val millisUntilMidnight = Duration.between(now, nextMidnight).toMillis()
                val waitMillis = if (millisUntilMidnight <= 0L) {
                    500L
                } else {
                    minOf(millisUntilMidnight + 50L, 60_000L)
                }
                delay(waitMillis)
            }
        }

        ThemePreferenceStore.reloadFromDevicePrefs(appContext)
        AuthFlowStore.consumeFreshLoginFlag(currentUserId)
        WidgetAndFloatingBridge.setActiveUserId(appContext, currentUserId)
        AnnouncementFrequencyStore.recordUserAppOpen(appContext, currentUserId)
        AnnouncementScheduledDispatcher.reschedulePendingAnnouncements(appContext)
        viewModelScope.launch {
            AnnouncementScheduledDispatcher.dueAnnouncementEvents.collect {
                refreshAnnouncements()
            }
        }
        viewModelScope.launch {
            AnnouncementHistoryStore.historyUpdates.collect { updatedUserId ->
                if (updatedUserId == currentUserId) {
                    refreshAnnouncementHistory()
                }
            }
        }
        viewModelScope.launch {
            MilestoneBadgeManager.badgeUpdates.collect { updatedUserId ->
                if (updatedUserId == currentUserId) {
                    badgeRefreshTickFlow.value = badgeRefreshTickFlow.value + 1
                }
            }
        }
        viewModelScope.launch {
            GroupRepository.groupUpdates.collect {
                reloadUserGroupsAndCelebrations()
            }
        }
        // Observe Main Durood counts strictly for 50k milestone badges (never flows into groups)
        viewModelScope.launch {
            countMetricsFlow.collect { metrics ->
                val unseenBadge = MilestoneBadgeManager.getPendingCelebrationBadge(
                    context = appContext,
                    userId = currentUserId,
                    mainDuroodTotalCount = metrics.total
                )
                if (unseenBadge != null) {
                    pendingMilestoneBadgeFlow.value = unseenBadge
                }
                 
            }
        }
        ensureRemindersScheduled()
        refreshGlobalStats()
        loadInitialUserSession()
        reloadUserGroupsAndCelebrations()
        viewModelScope.launch {
            restoreDownloadedUpdateIfAvailable()
        }

        // Hourly background ticker while app remains open: refreshes global stats once >= 1 hour elapses
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val cached = GlobalStatsCache.getCached(appContext)
                val online = NetworkMonitor.isCurrentlyOnline(appContext) ||
                    NetworkMonitor.hasNetworkConnection(appContext)
                if (GlobalStatsCache.shouldFetchFromServer(cached, System.currentTimeMillis(), online)) {
                    refreshGlobalStats()
                }
            }
        }

        // Keep the home-screen widget live with Today and Total counts from the shared Room database
        viewModelScope.launch {
            combine(
                todayStateFlow,
                repository.observeTotalCount(currentUserId)
            ) { todayPair, total ->
                todayPair.second to total
            }.collect { (today, total) ->
                OneCroreWidgetProvider.updateAllWidgetsWithCounts(
                    context = appContext,
                    todayCount = today,
                    totalCount = total
                )
            }
        }

        viewModelScope.launch {
            val expired = runCatching { repository.retryPendingAnnouncementCloudWrites(appContext) }.getOrDefault(emptyList())
            if (expired.isNotEmpty()) {
                statusMessageFlow.value = "Could not send — announcement expired"
            }
        }

        // Auto-sync whenever internet connectivity returns
        viewModelScope.launch {
            var wasOnline = NetworkMonitor.isCurrentlyOnline(appContext)
            NetworkMonitor.observeOnline(appContext).collect { online ->
                if (online && !wasOnline) {
                    val expired = runCatching { repository.retryPendingAnnouncementCloudWrites(appContext) }.getOrDefault(emptyList())
                    if (expired.isNotEmpty()) {
                        statusMessageFlow.value = "Could not send — announcement expired"
                    }
                    if (initialLoadOfflineErrorFlow.value) {
                        retryInitialLoad()
                    } else {
                        triggerSync(silent = true)
                        refreshGlobalStats()
                    }
                    val activeGroupId = openGroupIdFlow.value
                    if (activeGroupId != null) {
                        refreshOpenGroupDetailNow(activeGroupId)
                    }
                }
                wasOnline = online
            }
        }

        // Periodic 5-minute batch auto-sync ONLY when local user counts are pending (NO group polling)
        viewModelScope.launch {
            while (true) {
                delay(AUTO_SYNC_INTERVAL_MILLIS)
                // R3-P2-32: uiState is stateIn(WhileSubscribed(5000)) — once
                // the UI stops collecting (app backgrounded) its value
                // freezes, so the loop tested a stale flag and background
                // auto-sync silently stopped. Read the repo directly.
                val hasPendingSync = runCatching {
                    repository.observeHasPendingSync(currentUserId).first()
                }.getOrDefault(false)
                if (NetworkMonitor.isCurrentlyOnline(appContext) && hasPendingSync) {
                    triggerSync(silent = true)
                }
            }
        }
    }

    fun isEmailSignInSession(): Boolean {
        if (AuthFlowStore.isNewEmailSignup(appContext, currentUserId)) {
            return false
        }
        if (AuthFlowStore.isExplicitEmailSignIn(appContext, currentUserId)) {
            return true
        }
        return isEmailSignInProvider && !isGoogleSignedInProvider
    }

    fun retryInitialLoad() {
        loadInitialUserSession()
    }

    private fun loadInitialUserSession() {
        initialLoadJob?.cancel()
        initialLoadJob = viewModelScope.launch {
            initializedFlow.value = false
            initialLoadOfflineErrorFlow.value = false

            val signupName = AuthFlowStore.getSignupDisplayName(appContext, currentUserId)
            val effectiveFallbackName = signupName?.takeIf { it.isNotBlank() } ?: fallbackDisplayName

            val hasLocalBeforeSync = repository.hasLocalExistingData(currentUserId)
            val online = NetworkMonitor.isCurrentlyOnline(appContext)

            if (hasLocalBeforeSync) {
                repository.ensureExistingAccountSetupCompleted(
                    userId = currentUserId,
                    fallbackName = effectiveFallbackName,
                    accountCreatedAtMillis = accountCreatedAtMillis
                )
                AuthFlowStore.clearNewEmailSignup(appContext, currentUserId)
                initializedFlow.value = true
                refreshGlobalStats()
                refreshAnnouncements()
                ensureRemindersScheduled()
                if (online) {
                    triggerSync(silent = true)
                }
                return@launch
            }

            if (online) {
                isSyncingFlow.value = true
                val hadPendingSyncBefore = repository.getProfileOnce(currentUserId)?.pendingSync == true
                val syncResult = try {
                    withTimeoutOrNull(INITIAL_CLOUD_SYNC_TIMEOUT_MS) {
                        repository.syncWithCloud()
                    }
                } finally {
                    isSyncingFlow.value = false
                }

                val hasLocalAfterSync = repository.hasLocalExistingData(currentUserId)
                if (hasLocalAfterSync) {
                    repository.ensureExistingAccountSetupCompleted(
                        userId = currentUserId,
                        fallbackName = effectiveFallbackName,
                        accountCreatedAtMillis = accountCreatedAtMillis
                    )
                    if (!hadPendingSyncBefore && syncResult?.isSuccess == true) {
                        val syncedProfile = repository.getProfileOnce(currentUserId)
                        if (syncedProfile != null && !syncedProfile.pendingSync) {
                            ThemePreferenceStore.setThemeMode(
                                appContext,
                                AppThemeMode.fromStorageKey(syncedProfile.themeMode)
                            )
                        }
                    }
                    AuthFlowStore.clearNewEmailSignup(appContext, currentUserId)
                    if (syncResult == null) {
                        triggerSync(silent = true)
                        SyncWorker.enqueueWhenOnline(appContext)
                    }
                } else if (syncResult == null) {
                    // Initial cloud sync timed out: proceed without setting offline error and
                    // keep syncing in the background so cloud data still arrives.
                    triggerSync(silent = true)
                    SyncWorker.enqueueWhenOnline(appContext)
                } else if (syncResult.isFailure) {
                    // Cloud fetch failed and there is no local data: do NOT show Setup!
                    initialLoadOfflineErrorFlow.value = true
                } else if (isEmailSignInSession()) {
                    // Email + password SIGN-IN: account already exists by definition; never show Setup
                    repository.ensureExistingAccountSetupCompleted(
                        userId = currentUserId,
                        fallbackName = effectiveFallbackName,
                        accountCreatedAtMillis = accountCreatedAtMillis
                    )
                    AuthFlowStore.clearNewEmailSignup(appContext, currentUserId)
                    repository.syncWithCloud()
                }
            } else {
                // Offline + no local data (fresh install, no internet): never show Setup
                initialLoadOfflineErrorFlow.value = true
            }

            initializedFlow.value = true
            refreshGlobalStats()
            refreshAnnouncements()
            ensureRemindersScheduled()
        }
    }

    fun ensureRemindersScheduled() {
        viewModelScope.launch {
            val profile = repository.getProfileOnce(currentUserId)
            if (profile != null && profile.setupCompleted) {
                ReminderScheduler.scheduleReminders(
                    context = appContext,
                    enabled = profile.remindersEnabled,
                    r1Hour = profile.reminder1Hour,
                    r1Minute = profile.reminder1Minute,
                    r2Hour = profile.reminder2Hour,
                    r2Minute = profile.reminder2Minute,
                    userId = currentUserId
                )
            }
        }
    }

    override fun onCleared() {
        globalStatsRefreshJob?.cancel()
        globalStatsRetryJob?.cancel()
        announcementRetryJob?.cancel()
        announcementRefreshJob?.cancel()
        announcementHistoryRefreshJob?.cancel()
        openGroupFlushJob?.cancel()
        openGroupRefreshJob?.cancel()
        globalStatsListenerRemover?.invoke()
        globalStatsListenerRemover = null
        openGroupListenerRemover?.invoke()
        openGroupListenerRemover = null
        openGroupListenerGroupId = null
        openGroupIdFlow.value = null
        try {
            appContext.unregisterReceiver(dateChangeReceiver)
        } catch (_: IllegalArgumentException) {
        }
        super.onCleared()
    }

    fun refreshCurrentDate(): LocalDate {
        val nowDate = LocalDate.now()
        if (currentDateFlow.value != nowDate) {
            currentDateFlow.value = nowDate
        }
        return nowDate
    }

    fun clearStatusMessage() {
        statusMessageFlow.value = null
    }

    fun getUnsyncedTapCountForTest(): Int = unsyncedTapCount

    fun onTapPlusOne() {
        optimisticTapDeltaFlow.value += 1L
        launchSafe {
            val activeDateKey = refreshCurrentDate().toString()
            try {
            repository.incrementCount(currentUserId, activeDateKey, 1L)
        } finally {
            optimisticTapDeltaFlow.value -= 1L
        }
             
            SyncWorker.enqueueWhenOnline(appContext)
            unsyncedTapCount++
            if (unsyncedTapCount >= BATCH_SYNC_TAP_THRESHOLD) {
                unsyncedTapCount = 0
                if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                    triggerSync(silent = true)
                }
            }
        }
    }

    fun onAppPaused() {
        val hasDirtyGroups = GroupRepository.getDirtyGroupSyncIds(appContext).isNotEmpty()
        SyncWorker.enqueueWhenOnline(appContext)
        if ((uiState.value.hasPendingSync || hasDirtyGroups) && NetworkMonitor.isCurrentlyOnline(appContext)) {
            // R3-P2-33: triggerSync resets the batch counter on success; do
            // not zero it here on attempt.
            triggerSync(silent = true)
        }
    }

    fun onAddCustomAmount(amount: Long) {
        if (amount <= 0L) return
        launchSafe {
            val activeDateKey = refreshCurrentDate().toString()
            repository.incrementCount(currentUserId, activeDateKey, amount)
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onEditDayCount(dateKey: String, newCount: Long, oldCount: Long) {
        launchSafe {
            repository.adjustDayCount(currentUserId, dateKey, newCount - oldCount)
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onCreateCustomCounter(
        name: String,
        targetCount: Long,
        onCreated: (String) -> Unit = {}
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || targetCount <= 0L) return
        launchSafe {
            val id = repository.createCustomCounter(currentUserId, cleanName, targetCount)
            onCreated(id)
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onTapCustomCounterPlusOne(counterId: String) {
        launchSafe {
            repository.incrementCustomCounter(currentUserId, counterId, 1L)
            SyncWorker.enqueueWhenOnline(appContext)
            unsyncedTapCount++
            if (unsyncedTapCount >= BATCH_SYNC_TAP_THRESHOLD) {
                unsyncedTapCount = 0
                if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                    triggerSync(silent = true)
                }
            }
        }
    }

    fun onAddCustomCounterAmount(counterId: String, amount: Long) {
        if (amount <= 0L) return
        launchSafe {
            repository.incrementCustomCounter(currentUserId, counterId, amount)
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onUpdateCustomCounter(
        counterId: String,
        name: String,
        targetCount: Long,
        currentCount: Long,
        countAtDialogOpen: Long = currentCount
    ) {
        launchSafe {
            // R3-P2-35: the edit dialog snapshots its count at open; a tap
            // made while it was open must not be overwritten by that stale
            // value. Apply only the user's intended change (typed - open)
            // on top of the LIVE count, and set name/target without
            // touching the count.
            val delta = currentCount - countAtDialogOpen
            if (delta != 0L) {
                repository.adjustCustomCounter(currentUserId, counterId, delta)
            }
            repository.updateCustomCounterMeta(
                userId = currentUserId,
                counterId = counterId,
                name = name,
                targetCount = targetCount
            )
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onDeleteCustomCounter(counterId: String) {
        launchSafe {
            repository.deleteCustomCounter(currentUserId, counterId)
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onCompleteSetup(displayName: String, dailyTarget: Int) {
        launchSafe {
            repository.completeSetup(
                userId = currentUserId,
                displayName = displayName,
                dailyTarget = dailyTarget,
                fallbackStartedAtMillis = accountCreatedAtMillis
            )
            AuthFlowStore.clearNewEmailSignup(appContext, currentUserId)
            initialLoadOfflineErrorFlow.value = false
            val profile = repository.getProfileOnce(currentUserId)
            if (profile != null) {
                ReminderScheduler.scheduleReminders(
                    context = appContext,
                    enabled = profile.remindersEnabled,
                    r1Hour = profile.reminder1Hour,
                    r1Minute = profile.reminder1Minute,
                    r2Hour = profile.reminder2Hour,
                    r2Minute = profile.reminder2Minute,
                    userId = currentUserId
                )
            }
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onUpdatePlan(dailyTarget: Int) {
        launchSafe {
            repository.updatePlan(currentUserId, dailyTarget)
            statusMessageFlow.value = "Plan updated: $dailyTarget / day"
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onUpdateDisplayName(displayName: String) {
        launchSafe {
            repository.updateDisplayName(currentUserId, displayName)
            statusMessageFlow.value = "Name updated"
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onUpdateReminders(
        enabled: Boolean,
        r1Hour: Int,
        r1Minute: Int,
        r2Hour: Int,
        r2Minute: Int
    ) {
        launchSafe {
            repository.updateReminders(currentUserId, enabled, r1Hour, r1Minute, r2Hour, r2Minute)
            ReminderScheduler.scheduleReminders(
                context = appContext,
                enabled = enabled,
                r1Hour = r1Hour,
                r1Minute = r1Minute,
                r2Hour = r2Hour,
                r2Minute = r2Minute,
                userId = currentUserId
            )
            statusMessageFlow.value = if (enabled) "Reminders updated" else "Reminders paused"
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun onUpdateThemeMode(mode: AppThemeMode) {
        ThemePreferenceStore.setThemeMode(appContext, mode)
        launchSafe {
            repository.updateThemeMode(currentUserId, mode, fallbackDisplayName)
            statusMessageFlow.value = "Theme: ${mode.displayLabel}"
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { statusMessageFlow.value = it.message ?: "Something went wrong." }
        }
    }

    fun triggerSync(silent: Boolean = false) {
        val now = System.currentTimeMillis()
        val currentJob = syncJob
        val isReallyActive = currentJob?.isActive == true

        if (!silent) {
            // Manual sync from Settings → DATA & BACKUP: must never be blocked by a stale flag
            if (isReallyActive && (now - syncStartedAtMillis > DuroodRepository.CLOUD_SYNC_TIMEOUT_MS + 2_000L)) {
                currentJob?.cancel()
                syncInFlight.set(false)
                isSyncingFlow.value = false
            } else if (isReallyActive) {
                return
            } else {
                syncInFlight.set(false)
                isSyncingFlow.value = false
            }
        } else {
            // Automated / silent sync: return if already active or syncing
            if (isReallyActive || isSyncingFlow.value) return
            if (syncInFlight.get() && (now - syncStartedAtMillis > DuroodRepository.CLOUD_SYNC_TIMEOUT_MS + 2_000L)) {
                syncInFlight.set(false)
            }
            if (!syncInFlight.compareAndSet(false, true)) return
        }

        syncInFlight.set(true)
        syncStartedAtMillis = now
        syncJob = viewModelScope.launch {
            try {
                if (!NetworkMonitor.isCurrentlyOnline(appContext)) {
                    SyncWorker.enqueueWhenOnline(appContext)
                    if (!silent) {
                        statusMessageFlow.value = "Offline — saved on phone, will sync when internet returns."
                    }
                    return@launch
                }
                val hadPendingSyncBefore = repository.getProfileOnce(currentUserId)?.pendingSync == true
                isSyncingFlow.value = true
                val result = withTimeoutOrNull(DuroodRepository.CLOUD_SYNC_TIMEOUT_MS) {
                    repository.syncWithCloud()
                } ?: Result.failure(TimeoutException("Sync timed out"))

                if (result.isSuccess) {
                    // R3-P2-33: reset the batch counters only on a successful
                    // sync — resetting on attempt meant a failed/offline sync
                    // wiped the counter and the next batch sync needed 100
                    // fresh taps.
                    unsyncedTapCount = 0
                    WidgetAndFloatingBridge.resetUnsyncedTapCount(appContext)
                    val profile = repository.getProfileOnce(currentUserId)
                    if (profile != null && !hadPendingSyncBefore && !profile.pendingSync) {
                        ThemePreferenceStore.setThemeMode(
                            appContext,
                            AppThemeMode.fromStorageKey(profile.themeMode)
                        )
                    }
                    if (profile != null && profile.setupCompleted) {
                        ReminderScheduler.scheduleReminders(
                            context = appContext,
                            enabled = profile.remindersEnabled,
                            r1Hour = profile.reminder1Hour,
                            r1Minute = profile.reminder1Minute,
                            r2Hour = profile.reminder2Hour,
                            r2Minute = profile.reminder2Minute,
                            userId = currentUserId
                        )
                    }
                    pushDirtyGroupStatsDuringBatchSync()
                }
                if (!silent) {
                    statusMessageFlow.value = if (result.isSuccess) {
                        "Synced with cloud"
                    } else {
                        "Sync queued — will retry automatically"
                    }
                }
                refreshGlobalStats()
            } finally {
                isSyncingFlow.value = false
                syncInFlight.set(false)
            }
        }
    }

    suspend fun exportBackupJson(): String {
        // Round 1 P1-3: groups are local-only, so the backup is their only
        // durability — include the user's groups as registry JSON.
        val groupJson = GroupRepository.getGroupsForUser(appContext, currentUserId)
            .map { GroupRepository.encodeGroupForBackup(it) }
        return repository.exportBackupJson(currentUserId, groupJson)
    }

    fun restoreBackupJson(jsonText: String) {
        viewModelScope.launch {
            val result = repository.restoreBackupJson(currentUserId, jsonText)
            // Round 1 P1-3: merge the backup's groups back into the local
            // registry (newer updatedAtMillis wins per group; locally deleted
            // groups are never resurrected).
            var restoredGroups = 0
            if (result.isSuccess) {
                val backupGroups = result.getOrNull()?.groupsJson
                    ?.mapNotNull { GroupRepository.decodeGroupFromBackup(it) }
                    .orEmpty()
                if (backupGroups.isNotEmpty()) {
                    restoredGroups = GroupRepository.restoreGroupsFromBackup(
                        appContext, currentUserId, backupGroups
                    )
                    if (restoredGroups > 0) reloadUserGroupsAndCelebrations()
                }
                val profile = repository.getProfileOnce(currentUserId)
                if (profile != null && profile.setupCompleted) {
                    ReminderScheduler.scheduleReminders(
                        context = appContext,
                        enabled = profile.remindersEnabled,
                        r1Hour = profile.reminder1Hour,
                        r1Minute = profile.reminder1Minute,
                        r2Hour = profile.reminder2Hour,
                        r2Minute = profile.reminder2Minute,
                        userId = currentUserId
                    )
                }
            }
            statusMessageFlow.value = result.fold(
                onSuccess = { outcome ->
                    val totalRestored = outcome.restoredItems + restoredGroups
                    "Restored $totalRestored records safely (higher local counts preserved)." +
                        if (restoredGroups > 0) " Includes $restoredGroups group${if (restoredGroups == 1) "" else "s"}." else "" +
                        if (outcome.skippedEntries > 0) " ${outcome.skippedEntries} entries skipped." else ""
                },
                onFailure = { "Invalid backup file." }
            )
            SyncWorker.enqueueWhenOnline(appContext)
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                triggerSync(silent = true)
            }
        }
    }

    fun refreshGlobalStats() {
        val cachedNow = GlobalStatsCache.getCached(appContext)
        if (globalStatsFlow.value == null && cachedNow != null) {
            globalStatsFlow.value = cachedNow
        }
        if (globalStatsFlow.value == null) {
            attachGlobalStatsListenerIfNeeded()
        }
        if (globalStatsRefreshJob?.isActive == true) return
        globalStatsRefreshJob = viewModelScope.launch {
            val currentCached = GlobalStatsCache.getCached(appContext)
            if (globalStatsFlow.value == null && currentCached != null) {
                globalStatsFlow.value = currentCached
            }
            val online = NetworkMonitor.isCurrentlyOnline(appContext) ||
                NetworkMonitor.hasNetworkConnection(appContext)
            val forceWhenEmpty = currentCached == null
            when (
                val outcome = repository.loadGlobalStatsDetailed(
                    context = appContext,
                    isOnline = online,
                    forceWhenEmpty = forceWhenEmpty
                )
            ) {
                is DuroodRepository.GlobalStatsLoadOutcome.Loaded -> {
                    onGlobalStatsUpdated(outcome.stats)
                    stopGlobalStatsBootstrapRecovery()
                }

                is DuroodRepository.GlobalStatsLoadOutcome.ConfirmedMissing -> {
                    Log.w(
                        DuroodRepository.GLOBAL_STATS_LOG_TAG,
                        "refreshGlobalStats outcome=ConfirmedMissing on primary DB"
                    )
                    globalStatsFlow.value = null
                    stopGlobalStatsBootstrapRecovery()
                }

                is DuroodRepository.GlobalStatsLoadOutcome.TransientUnavailable -> {
                    if (outcome.fallbackCached != null) {
                        onGlobalStatsUpdated(outcome.fallbackCached)
                        stopGlobalStatsBootstrapRecovery()
                    } else if (globalStatsFlow.value == null) {
                        Log.w(
                            DuroodRepository.GLOBAL_STATS_LOG_TAG,
                            "refreshGlobalStats outcome=TransientUnavailable(reason=${outcome.failureReason}); starting recovery"
                        )
                        startGlobalStatsBootstrapRecovery()
                    }
                }
            }
        }
    }

    private fun onGlobalStatsUpdated(stats: GlobalCounterStats) {
        globalStatsFlow.value = stats
        if (MilestoneAnnouncementManager.getEligibleMilestoneCandidates(
                context = appContext,
                userId = currentUserId,
                globalStats = stats
            ).isNotEmpty()
        ) {
            refreshAnnouncements()
        }
    }

    internal fun attachGlobalStatsListenerIfNeeded(
        attachObserver: (
            onStatsLoaded: (GlobalCounterStats) -> Unit,
            onConfirmedMissing: () -> Unit,
            onError: (Exception) -> Unit
        ) -> (() -> Unit)? = { onLoaded, onMissing, onErr ->
            repository.observeGlobalStatsOnce(
                context = appContext,
                onStatsLoaded = onLoaded,
                onConfirmedMissing = onMissing,
                onError = onErr
            )
        }
    ): Boolean {
        if (globalStatsListenerRemover != null) return true
        val remover = attachObserver(
            { stats ->
                onGlobalStatsUpdated(stats)
                stopGlobalStatsBootstrapRecovery()
            },
            {
                globalStatsFlow.value = null
                stopGlobalStatsBootstrapRecovery()
            },
            { error ->
                handleGlobalStatsListenerError(error)
            }
        )
        return if (remover != null) {
            globalStatsListenerRemover = remover
            true
        } else {
            false
        }
    }

    internal fun handleGlobalStatsListenerError(error: Exception) {
        Log.w(
            DuroodRepository.GLOBAL_STATS_LOG_TAG,
            "Global stats snapshot listener error: ${error::class.java.simpleName}: ${error.message}"
        )
        viewModelScope.launch {
            globalStatsListenerRemover?.invoke()
            globalStatsListenerRemover = null
            if (globalStatsFlow.value == null) {
                startGlobalStatsBootstrapRecovery(attachImmediately = false)
            }
        }
    }

    internal fun startGlobalStatsBootstrapRecovery(attachImmediately: Boolean = true) {
        if (attachImmediately) {
            attachGlobalStatsListenerIfNeeded()
        }
        if (globalStatsRetryJob?.isActive == true) return
        globalStatsRetryJob = viewModelScope.launch {
            val delays = longArrayOf(1_200L, 2_500L, 5_000L, 10_000L)
            for (waitMs in delays) {
                delay(waitMs)
                if (globalStatsFlow.value != null) {
                    stopGlobalStatsBootstrapRecovery()
                    break
                }
                attachGlobalStatsListenerIfNeeded()
                val online = NetworkMonitor.isCurrentlyOnline(appContext) ||
                    NetworkMonitor.hasNetworkConnection(appContext)
                when (
                    val outcome = repository.loadGlobalStatsDetailed(
                        context = appContext,
                        isOnline = online,
                        forceWhenEmpty = true
                    )
                ) {
                    is DuroodRepository.GlobalStatsLoadOutcome.Loaded -> {
                        onGlobalStatsUpdated(outcome.stats)
                        stopGlobalStatsBootstrapRecovery()
                        break
                    }

                    is DuroodRepository.GlobalStatsLoadOutcome.ConfirmedMissing -> {
                        Log.w(
                            DuroodRepository.GLOBAL_STATS_LOG_TAG,
                            "Bootstrap retry outcome=ConfirmedMissing on primary DB"
                        )
                        globalStatsFlow.value = null
                        stopGlobalStatsBootstrapRecovery()
                        break
                    }

                    is DuroodRepository.GlobalStatsLoadOutcome.TransientUnavailable -> {
                        Log.w(
                            DuroodRepository.GLOBAL_STATS_LOG_TAG,
                            "Bootstrap retry outcome=TransientUnavailable(reason=${outcome.failureReason}, listenerAttached=${globalStatsListenerRemover != null})"
                        )
                    }
                }
            }
            if (globalStatsFlow.value == null) {
                attachGlobalStatsListenerIfNeeded()
            }
        }
    }

    private fun stopGlobalStatsBootstrapRecovery() {
        globalStatsRetryJob?.cancel()
        globalStatsRetryJob = null
        globalStatsListenerRemover?.invoke()
        globalStatsListenerRemover = null
    }

    fun scheduleOrPublishAnnouncement(
        item: AnnouncementItem,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val result = repository.scheduleOrPublishAnnouncement(
                context = appContext,
                item = item,
                nowMillis = now
            )
            result.fold(
                onSuccess = { saved ->
                    val isFuture = saved.isScheduledForFuture(now)
                    val message = if (!saved.cloudPublished) {
                        "Saved on this phone — will send when online"
                    } else if (isFuture) {
                        "Announcement scheduled"
                    } else {
                        "Announcement scheduled"
                    }
                    statusMessageFlow.value = message
                    if (!isFuture && saved.cloudPublished) {
                        refreshAnnouncements()
                    }
                    onComplete?.invoke(true, message)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Failed to schedule announcement."
                    statusMessageFlow.value = msg
                    onComplete?.invoke(false, msg)
                }
            )
        }
    }

    fun deleteAnnouncement(
        item: AnnouncementItem,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (item.id.isBlank() || MilestoneAnnouncementManager.isMilestoneAnnouncementId(item.id)) {
            onComplete?.invoke(false, "Milestone announcements cannot be deleted.")
            return
        }
        viewModelScope.launch {
            val result = repository.deleteAnnouncement(item.id)
            result.fold(
                onSuccess = {
                    AnnouncementHistoryStore.removeAnnouncementById(
                        context = appContext,
                        userId = currentUserId,
                        announcementId = item.id
                    )
                    // Round 1 P2-3: also drop the local schedule + alarm, or a
                    // deleted future announcement still pops up / pushes on
                    // this phone until its window expires.
                    AnnouncementScheduledDispatcher.cancelScheduledAnnouncement(
                        appContext, item.id
                    )
                    latestCloudAnnouncementsForHistory = latestCloudAnnouncementsForHistory
                        ?.filterNot { it.id == item.id }
                    lastHistoryCloudFetchMillis = 0L
                    if (activeAnnouncementFlow.value?.id == item.id) {
                        isCurrentAnnouncementShownRecorded = false
                        isDirectAnnouncementFlow.value = false
                        activeAnnouncementFlow.value = null
                    }
                    refreshAnnouncementHistory()
                    val message = "Announcement deleted"
                    statusMessageFlow.value = message
                    onComplete?.invoke(true, message)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Failed to delete announcement."
                    statusMessageFlow.value = msg
                    onComplete?.invoke(false, msg)
                }
            )
        }
    }

    internal suspend fun resolveCurrentAudienceContext(
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AnnouncementAudienceContext {
        val todayKey = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toString()
        val profile = runCatching { repository.getProfileOnce(currentUserId) }.getOrNull()
        val allDays = runCatching { repository.observeAllDays(currentUserId).first() }.getOrDefault(emptyList())
        val todayCount = allDays.firstOrNull { it.dateKey == todayKey }?.count
            ?: runCatching { repository.getDayCountOnce(currentUserId, todayKey) }.getOrDefault(0L)
        val dailyTarget = profile?.dailyTarget?.coerceAtLeast(1) ?: DuroodRepository.DEFAULT_DAILY_TARGET

        val activePastDays = allDays.filter { it.count > 0L && it.dateKey != todayKey }
        val latestActiveDay = allDays.filter { it.count > 0L }.maxByOrNull { it.dateKey }
        val latestPastDayMillis = activePastDays.maxOfOrNull { day ->
            val dateStartMillis = runCatching {
                LocalDate.parse(day.dateKey).atStartOfDay(zoneId).toInstant().toEpochMilli()
            }.getOrDefault(0L)
            maxOf(day.updatedAtMillis, dateStartMillis)
        } ?: 0L

        val storedAppOpenMillis = AnnouncementFrequencyStore.getEffectiveLastActiveAtMillis(
            context = appContext,
            userId = currentUserId,
            isAppInForegroundSession = true
        )
        val cachedAudience = AnnouncementFrequencyStore.readCachedAudienceContext(appContext, currentUserId)

        val resolvedLastActiveMillis = when {
            todayCount > 0L -> nowMillis
            storedAppOpenMillis > 0L && latestPastDayMillis > 0L -> maxOf(storedAppOpenMillis, latestPastDayMillis)
            storedAppOpenMillis > 0L -> storedAppOpenMillis
            latestPastDayMillis > 0L -> latestPastDayMillis
            cachedAudience != null && cachedAudience.lastActiveAtMillis > 0L -> cachedAudience.lastActiveAtMillis
            else -> 0L
        }

        return AnnouncementAudienceContext(
            todayCount = todayCount,
            dailyTarget = dailyTarget,
            lastActiveAtMillis = resolvedLastActiveMillis,
            latestActiveDateKey = latestActiveDay?.dateKey ?: cachedAudience?.latestActiveDateKey
        ).also {
            AnnouncementFrequencyStore.cacheAudienceContext(appContext, currentUserId, it)
        }
    }

    fun refreshAnnouncements() {
        if (isDirectAnnouncementFlow.value) return
        if (announcementRefreshJob?.isActive == true) return
        announcementRefreshJob = viewModelScope.launch {
            performAnnouncementRefresh()
        }
    }

    private fun applyMergedAnnouncementHistory(
        cloudAnnouncements: List<AnnouncementItem>? = latestCloudAnnouncementsForHistory
    ) {
        AnnouncementHistoryStore.pruneExpiredAnnouncements(appContext, currentUserId)
        val localHistory = AnnouncementHistoryStore.getHistoryNewestFirst(
            appContext,
            currentUserId
        )
        announcementHistoryFlow.value = AnnouncementHistoryStore.mergeWithCloudAnnouncements(
            localEntries = localHistory,
            cloudAnnouncements = cloudAnnouncements
        )
    }

    fun refreshAnnouncementHistory() {
        val now = System.currentTimeMillis()
        if (!NetworkMonitor.isCurrentlyOnline(appContext)) {
            applyMergedAnnouncementHistory(cloudAnnouncements = null)
            return
        }
        applyMergedAnnouncementHistory(cloudAnnouncements = latestCloudAnnouncementsForHistory)
        if (announcementHistoryRefreshJob?.isActive == true || now - lastHistoryCloudFetchMillis < 2_000L) {
            return
        }
        lastHistoryCloudFetchMillis = now
        announcementHistoryRefreshJob = viewModelScope.launch {
            val fetchResult = try {
                repository.fetchActiveAnnouncementsDetailed(nowMillis = System.currentTimeMillis())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (fetchResult != null && !fetchResult.hadTransientError) {
                latestCloudAnnouncementsForHistory = fetchResult.items
                lastHistoryCloudFetchMillis = System.currentTimeMillis()
                applyMergedAnnouncementHistory(cloudAnnouncements = fetchResult.items)
            } else {
                latestCloudAnnouncementsForHistory = null
                applyMergedAnnouncementHistory(cloudAnnouncements = null)
            }
        }
    }

    internal suspend fun performAnnouncementRefresh(
        nowMillis: Long = System.currentTimeMillis(),
        audienceContextOverride: AnnouncementAudienceContext? = null,
        fetchFn: suspend (Long) -> DuroodRepository.AnnouncementFetchResult = repository::fetchActiveAnnouncementsDetailed
    ) {
        val refreshToken = announcementRefreshToken.incrementAndGet()
        if (isDirectAnnouncementFlow.value || refreshToken != announcementRefreshToken.get()) return

        val resolvedAudience = audienceContextOverride ?: resolveCurrentAudienceContext(nowMillis)
        if (refreshToken != announcementRefreshToken.get()) return

        val currentShown = activeAnnouncementFlow.value
        if (currentShown != null &&
            (nowMillis !in currentShown.effectiveStartAtMillis..currentShown.endAtMillis ||
                !AnnouncementFrequencyStore.isAudienceEligible(currentShown, resolvedAudience, nowMillis) ||
                AnnouncementFrequencyStore.hasActedOn(appContext, currentUserId, currentShown.id))
        ) {
            isCurrentAnnouncementShownRecorded = false
            activeAnnouncementFlow.value = null
        }

        val fetchResult = fetchFn(nowMillis)
        if (isDirectAnnouncementFlow.value || refreshToken != announcementRefreshToken.get()) return

        val localActive = AnnouncementScheduleStore.getActiveAnnouncementsAt(appContext, nowMillis)
        val milestoneCandidates = MilestoneAnnouncementManager.getEligibleMilestoneCandidates(
            context = appContext,
            userId = currentUserId,
            globalStats = globalStatsFlow.value,
            nowMillis = nowMillis
        )

        if (fetchResult.hadTransientError) {
            latestCloudAnnouncementsForHistory = null
            lastHistoryCloudFetchMillis = System.currentTimeMillis()
            applyMergedAnnouncementHistory(cloudAnnouncements = null)
            val fallbackCandidates = buildList {
                val seenIds = mutableSetOf<String>()
                localActive.forEach {
                    if (seenIds.add(it.id)) add(it)
                }
                milestoneCandidates.forEach {
                    if (seenIds.add(it.id)) add(it)
                }
            }
            if (fallbackCandidates.isEmpty()) {
                isCurrentAnnouncementShownRecorded = false
                activeAnnouncementFlow.value = null
                Log.w(
                    DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                    "Home refresh Failed (hadTransientError=true); cleared stale announcement and scheduling retry"
                )
                startAnnouncementBootstrapRetry(refreshToken)
                return
            }
            val eligibleLocal = AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context = appContext,
                userId = currentUserId,
                candidates = fallbackCandidates,
                nowMillis = nowMillis,
                audienceContext = resolvedAudience
            )
            if (eligibleLocal != null) {
                isDirectAnnouncementFlow.value = false
                isCurrentAnnouncementShownRecorded = false
                activeAnnouncementFlow.value = eligibleLocal
            } else {
                isCurrentAnnouncementShownRecorded = false
                activeAnnouncementFlow.value = null
            }
            startAnnouncementBootstrapRetry(refreshToken)
            return
        }

        latestCloudAnnouncementsForHistory = fetchResult.items
        lastHistoryCloudFetchMillis = System.currentTimeMillis()
        applyMergedAnnouncementHistory(cloudAnnouncements = fetchResult.items)

        val mergedCandidates = buildList {
            val seenIds = mutableSetOf<String>()
            fetchResult.items.forEach {
                if (seenIds.add(it.id)) add(it)
            }
            localActive.forEach {
                if (seenIds.add(it.id)) add(it)
            }
            milestoneCandidates.forEach {
                if (seenIds.add(it.id)) add(it)
            }
        }

        // If a passive dialog is already visible and still active/eligible-to-stay-open on the server,
        // update its content in place unless a newly eligible urgent announcement should take priority.
        val hasNewerUrgentCandidate = mergedCandidates.any { candidate ->
            candidate.isUrgent &&
                activeAnnouncementFlow.value?.id != candidate.id &&
                AnnouncementFrequencyStore.isEligibleToShow(
                    context = appContext,
                    userId = currentUserId,
                    announcement = candidate,
                    nowMillis = nowMillis,
                    audienceContext = resolvedAudience
                )
        }
        val stillActiveCurrent = if (hasNewerUrgentCandidate) {
            null
        } else {
            activeAnnouncementFlow.value?.let { shown ->
                val matched = mergedCandidates.firstOrNull { it.id == shown.id }
                    ?: shown.takeIf { MilestoneAnnouncementManager.isMilestoneAnnouncementId(it.id) }
                matched?.takeIf { candidate ->
                    nowMillis in candidate.effectiveStartAtMillis..candidate.endAtMillis &&
                        AnnouncementFrequencyStore.isAudienceEligible(candidate, resolvedAudience, nowMillis) &&
                        !AnnouncementFrequencyStore.hasActedOn(appContext, currentUserId, candidate.id)
                }
            }
        }
        if (stillActiveCurrent != null) {
            announcementRetryJob?.cancel()
            announcementRetryJob = null
            activeAnnouncementFlow.value = stillActiveCurrent
            return
        }

        val eligible = AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
            context = appContext,
            userId = currentUserId,
            candidates = mergedCandidates,
            nowMillis = nowMillis,
            audienceContext = resolvedAudience
        )
        if (eligible != null) {
            announcementRetryJob?.cancel()
            announcementRetryJob = null
            isDirectAnnouncementFlow.value = false
            isCurrentAnnouncementShownRecorded = false
            activeAnnouncementFlow.value = eligible
            Log.i(
                DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                "Home refresh Loaded id=${eligible.id} priority=${eligible.priority} audience=${eligible.targetAudience.wireValue} hasButton=${eligible.hasActionButton}"
            )
        } else {
            isCurrentAnnouncementShownRecorded = false
            activeAnnouncementFlow.value = null
            Log.i(
                DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                "Home refresh Loaded empty/ineligible (serverCandidates=${fetchResult.items.size}, localCandidates=${localActive.size}, milestones=${milestoneCandidates.size})"
            )
        }
    }

    private fun startAnnouncementBootstrapRetry(expectedToken: Long) {
        if (announcementRetryJob?.isActive == true) return
        announcementRetryJob = viewModelScope.launch {
            val delays = longArrayOf(1_500L, 3_500L, 7_000L)
            for (waitMs in delays) {
                delay(waitMs)
                if (isDirectAnnouncementFlow.value || expectedToken != announcementRefreshToken.get()) break
                val now = System.currentTimeMillis()
                val fetchResult = repository.fetchActiveAnnouncementsDetailed(nowMillis = now)
                if (isDirectAnnouncementFlow.value || expectedToken != announcementRefreshToken.get()) break
                val milestoneCandidates = MilestoneAnnouncementManager.getEligibleMilestoneCandidates(
                    context = appContext,
                    userId = currentUserId,
                    globalStats = globalStatsFlow.value,
                    nowMillis = now
                )
                if (fetchResult.hadTransientError) {
                    latestCloudAnnouncementsForHistory = null
                    lastHistoryCloudFetchMillis = System.currentTimeMillis()
                    applyMergedAnnouncementHistory(cloudAnnouncements = null)
                    val localActive = AnnouncementScheduleStore.getActiveAnnouncementsAt(appContext, now)
                    if (localActive.isEmpty() && milestoneCandidates.isEmpty()) {
                        activeAnnouncementFlow.value = null
                    }
                    Log.w(
                        DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                        "Bootstrap retry Failed (hadTransientError=true)"
                    )
                    continue
                }
                latestCloudAnnouncementsForHistory = fetchResult.items
                lastHistoryCloudFetchMillis = System.currentTimeMillis()
                applyMergedAnnouncementHistory(cloudAnnouncements = fetchResult.items)
                val localActive = AnnouncementScheduleStore.getActiveAnnouncementsAt(appContext, now)
                val mergedCandidates = buildList {
                    val seenIds = mutableSetOf<String>()
                    fetchResult.items.forEach {
                        if (seenIds.add(it.id)) add(it)
                    }
                    localActive.forEach {
                        if (seenIds.add(it.id)) add(it)
                    }
                    milestoneCandidates.forEach {
                        if (seenIds.add(it.id)) add(it)
                    }
                }
                val resolvedAudience = resolveCurrentAudienceContext(now)
                if (expectedToken != announcementRefreshToken.get()) break
                val eligible = AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                    context = appContext,
                    userId = currentUserId,
                    candidates = mergedCandidates,
                    nowMillis = now,
                    audienceContext = resolvedAudience
                )
                if (eligible != null) {
                    isDirectAnnouncementFlow.value = false
                    isCurrentAnnouncementShownRecorded = false
                    activeAnnouncementFlow.value = eligible
                    Log.i(
                        DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                        "Bootstrap retry Loaded id=${eligible.id}"
                    )
                } else {
                    isCurrentAnnouncementShownRecorded = false
                    activeAnnouncementFlow.value = null
                }
                break
            }
        }
    }

    fun openAnnouncementFromNotification(request: AnnouncementLaunchRequest) {
        viewModelScope.launch {
            announcementRetryJob?.cancel()
            announcementRetryJob = null
            announcementRefreshJob?.cancel()
            announcementRefreshJob = null

            val localSaved = AnnouncementScheduleStore.getAnnouncementById(appContext, request.announcementId)
            val hasFallbackContent = !request.fallbackTitle.isNullOrBlank() || !request.fallbackBody.isNullOrBlank()
            if (localSaved != null) {
                isDirectAnnouncementFlow.value = true
                activeAnnouncementFlow.value = localSaved
                recordDisplayedDirectAnnouncement(localSaved)
            } else if (hasFallbackContent) {
                val fallbackUrl = request.fallbackUrl?.trim()?.takeIf {
                    it.startsWith("https://", ignoreCase = true) ||
                        it.startsWith("http://", ignoreCase = true)
                }
                val fallbackItem = AnnouncementItem(
                    id = request.announcementId,
                    title = request.fallbackTitle.orEmpty(),
                    body = request.fallbackBody.orEmpty(),
                    buttonText = if (fallbackUrl != null) "Join" else null,
                    buttonAction = if (fallbackUrl != null) AnnouncementAction.LINK else AnnouncementAction.NONE,
                    buttonUrl = fallbackUrl,
                    isUrgent = request.fallbackIsUrgent
                )
                isDirectAnnouncementFlow.value = true
                activeAnnouncementFlow.value = fallbackItem
                recordDisplayedDirectAnnouncement(fallbackItem)
            } else if (activeAnnouncementFlow.value?.id != request.announcementId) {
                // Never leave an unrelated cached announcement on screen when tapping a specific notification
                isDirectAnnouncementFlow.value = false
                activeAnnouncementFlow.value = null
            }

            // Always fetch the live Firestore announcement doc directly (no stale cache)
            val fetched = repository.fetchAnnouncementById(request.announcementId)
                ?: repository.fetchActiveAnnouncements().firstOrNull { it.id == request.announcementId }

            if (fetched != null) {
                isDirectAnnouncementFlow.value = true
                activeAnnouncementFlow.value = fetched
                recordDisplayedDirectAnnouncement(fetched)
                Log.i(
                    DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                    "Notification open Loaded id=${fetched.id} hasButton=${fetched.hasActionButton}"
                )
            } else if (localSaved == null && !hasFallbackContent && activeAnnouncementFlow.value == null) {
                // Retry once more briefly after Auth warmup in case notification launched from killed state
                delay(1_200L)
                val retryFetched = repository.fetchAnnouncementById(request.announcementId)
                    ?: repository.fetchActiveAnnouncements().firstOrNull { it.id == request.announcementId }
                if (retryFetched != null) {
                    isDirectAnnouncementFlow.value = true
                    activeAnnouncementFlow.value = retryFetched
                    recordDisplayedDirectAnnouncement(retryFetched)
                    Log.i(
                        DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                        "Notification open retry Loaded id=${retryFetched.id} hasButton=${retryFetched.hasActionButton}"
                    )
                } else {
                    Log.w(
                        DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                        "Notification open Failed: could not load id=${request.announcementId}"
                    )
                }
            } else {
                Log.w(
                    DuroodRepository.ANNOUNCEMENT_LOG_TAG,
                    "Notification open Failed live upgrade for id=${request.announcementId} (showing fallback=${hasFallbackContent || localSaved != null})"
                )
            }
        }
    }

    private fun recordDisplayedDirectAnnouncement(announcement: AnnouncementItem) {
        if (MilestoneAnnouncementManager.isMilestoneAnnouncementId(announcement.id)) {
            MilestoneAnnouncementManager.markMilestoneCompletedForUser(appContext, currentUserId, announcement.id)
        }
        AnnouncementHistoryStore.recordAnnouncement(
            context = appContext,
            userId = currentUserId,
            announcement = announcement,
            status = AnnouncementHistoryStore.STATUS_SHOWN
        )
        refreshAnnouncementHistory()
    }

    fun onAnnouncementShown(announcement: AnnouncementItem) {
        if (activeAnnouncementFlow.value?.id != announcement.id) {
            return
        }
        val nowMillis = System.currentTimeMillis()
        if (MilestoneAnnouncementManager.isMilestoneAnnouncementId(announcement.id)) {
            MilestoneAnnouncementManager.markMilestoneCompletedForUser(appContext, currentUserId, announcement.id)
        }
        // Popup-rendering diagnosis: the HomeScreen LaunchedEffect re-fires
        // whenever the popup branch re-enters composition with the same
        // announcement (e.g. an update dialog briefly takes priority), which
        // recorded a duplicate STATUS_SHOWN entry in Messages History on
        // every re-show. Record the show once per announcement, like the
        // frequency-cap record below. (Direct announcements opened from
        // history use recordDisplayedDirectAnnouncement instead — untouched.)
        if (isCurrentAnnouncementShownRecorded) {
            return
        }
        isCurrentAnnouncementShownRecorded = true
        AnnouncementHistoryStore.recordAnnouncement(
            context = appContext,
            userId = currentUserId,
            announcement = announcement,
            status = AnnouncementHistoryStore.STATUS_SHOWN,
            recordedAtMillis = nowMillis
        )
        refreshAnnouncementHistory()
        if (isDirectAnnouncementFlow.value) {
            return
        }
        AnnouncementFrequencyStore.recordShown(
            context = appContext,
            userId = currentUserId,
            announcement = announcement,
            nowMillis = nowMillis
        )
    }

    fun dismissAnnouncement() {
        val current = activeAnnouncementFlow.value
        val wasDirect = isDirectAnnouncementFlow.value
        val nowMillis = System.currentTimeMillis()
        if (current != null) {
            if (!wasDirect && !isCurrentAnnouncementShownRecorded) {
                isCurrentAnnouncementShownRecorded = true
                AnnouncementFrequencyStore.recordShown(
                    context = appContext,
                    userId = currentUserId,
                    announcement = current,
                    nowMillis = nowMillis
                )
            }
            if (MilestoneAnnouncementManager.isMilestoneAnnouncementId(current.id)) {
                MilestoneAnnouncementManager.markMilestoneCompletedForUser(appContext, currentUserId, current.id)
                AnnouncementFrequencyStore.markActedOn(
                    context = appContext,
                    userId = currentUserId,
                    announcementId = current.id,
                    nowMillis = nowMillis
                )
            } else {
                // Round 1 P2: a dismiss suppresses for 10 minutes, then the
                // announcement is eligible again — urgent ones too. The old
                // code marked urgent dismisses actedOn, hiding them forever.
                // Only the Join/action tap (markActedOn) suppresses forever.
                AnnouncementFrequencyStore.markDismissed(
                    context = appContext,
                    userId = currentUserId,
                    announcementId = current.id,
                    nowMillis = nowMillis
                )
            }
            AnnouncementHistoryStore.recordAnnouncement(
                context = appContext,
                userId = currentUserId,
                announcement = current,
                status = AnnouncementHistoryStore.STATUS_DISMISSED,
                recordedAtMillis = nowMillis
            )
            refreshAnnouncementHistory()
        }
        isCurrentAnnouncementShownRecorded = false
        isDirectAnnouncementFlow.value = false
        activeAnnouncementFlow.value = null
    }

    fun onAnnouncementActionClicked(announcement: AnnouncementItem) {
        val wasDirect = isDirectAnnouncementFlow.value
        val nowMillis = System.currentTimeMillis()
        if (!wasDirect && !isCurrentAnnouncementShownRecorded) {
            isCurrentAnnouncementShownRecorded = true
            AnnouncementFrequencyStore.recordShown(
                context = appContext,
                userId = currentUserId,
                announcement = announcement,
                nowMillis = nowMillis
            )
        }
        if (MilestoneAnnouncementManager.isMilestoneAnnouncementId(announcement.id)) {
            MilestoneAnnouncementManager.markMilestoneCompletedForUser(appContext, currentUserId, announcement.id)
        }
        AnnouncementFrequencyStore.markActedOn(
            context = appContext,
            userId = currentUserId,
            announcementId = announcement.id,
            nowMillis = nowMillis
        )
        AnnouncementHistoryStore.recordAnnouncement(
            context = appContext,
            userId = currentUserId,
            announcement = announcement,
            status = AnnouncementHistoryStore.STATUS_ACTED,
            recordedAtMillis = nowMillis
        )
        refreshAnnouncementHistory()
        isCurrentAnnouncementShownRecorded = false
        isDirectAnnouncementFlow.value = false
        activeAnnouncementFlow.value = null

        executeAnnouncementAction(announcement)
    }

    fun onHistoryAnnouncementActionClicked(announcement: AnnouncementItem) {
        executeAnnouncementAction(announcement)
    }

    private fun executeAnnouncementAction(announcement: AnnouncementItem) {
        when (announcement.buttonAction) {
            AnnouncementAction.UPDATE -> {
                val existingUpdate = updateStateFlow.value.availableUpdate
                if (existingUpdate != null) {
                    openUpdateDialog()
                } else {
                    viewModelScope.launch {
                        val latest = AppUpdateManager.checkForUpdate(appContext)
                        if (latest != null) {
                            updateStateFlow.value = updateStateFlow.value.copy(
                                availableUpdate = latest,
                                showDialog = true,
                                isCheckSettled = true,
                                errorMessage = null
                            )
                        }
                    }
                }
            }

            AnnouncementAction.SHARE -> {
                viewModelScope.launch {
                    AppUpdateManager.shareApp(appContext)
                }
            }

            AnnouncementAction.LINK -> {
                val url = announcement.buttonUrl?.trim().orEmpty()
                if (url.startsWith("https://", ignoreCase = true) ||
                    url.startsWith("http://", ignoreCase = true)
                ) {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                    }
                }
            }

            AnnouncementAction.NONE -> Unit
        }
    }

    private suspend fun restoreDownloadedUpdateIfAvailable(targetUpdate: AppUpdateInfo? = null) {
        if (updateStateFlow.value.isDownloading || updateStateFlow.value.isPreparingUpdate) return
        val update = targetUpdate ?: AppUpdateManager.getCachedAvailableUpdate(appContext)
        if (update == null) {
            if (downloadedApkFile != null || updateStateFlow.value.pendingInstallReady) {
                activeDownloadId = null
                downloadedApkFile = null
                updateStateFlow.value = updateStateFlow.value.copy(
                    needsInstallPermission = false,
                    pendingInstallReady = false
                )
            }
            return
        }
        val completed = AppUpdateManager.findCompletedDownload(appContext, update.versionCode)
        if (completed != null) {
            val (downloadId, apkFile) = completed
            activeDownloadId = downloadId
            downloadedApkFile = apkFile
            val canInstall = AppUpdateManager.canInstallPackages(appContext)
            updateStateFlow.value = updateStateFlow.value.copy(
                availableUpdate = updateStateFlow.value.availableUpdate ?: update,
                needsInstallPermission = !canInstall,
                pendingInstallReady = true,
                downloadStatusText = "Download complete",
                errorMessage = null
            )
        } else if (downloadedApkFile != null || updateStateFlow.value.pendingInstallReady) {
            activeDownloadId = null
            downloadedApkFile = null
            updateStateFlow.value = updateStateFlow.value.copy(
                needsInstallPermission = false,
                pendingInstallReady = false
            )
        }
    }

    private fun restoreDownloadedApkIfReady(update: AppUpdateInfo, autoShow: Boolean) {
        val downloadsDir = appContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: return
        val file = java.io.File(downloadsDir, "1crore-update-${update.versionCode}.apk")
        if (!file.exists() || file.length() == 0L || !AppUpdateManager.isValidApkFile(appContext, file)) return
        downloadedApkFile = file
        updateStateFlow.value = updateStateFlow.value.copy(
            isDownloading = false,
            downloadProgress = 1f,
            downloadStatusText = "Download complete",
            needsInstallPermission = false,
            pendingInstallReady = true,
            // Round 1 U-P2-1: the dialog only auto-opens when the Later
            // cooldown allows it (autoShow). The old code set showDialog =
            // true whenever a downloaded APK existed, so "Later" was
            // bypassed on the next app open. The install stays reachable
            // meanwhile via Home's update badge / "Install now".
            showDialog = updateStateFlow.value.showDialog || autoShow,
            errorMessage = null
        )
    }

    fun checkForAppUpdate() {
        viewModelScope.launch {
            val settleWatchdog = launch {
                delay(2_000L)
                if (!updateStateFlow.value.isCheckSettled) {
                    updateStateFlow.value = updateStateFlow.value.copy(isCheckSettled = true)
                }
            }
            try {
                val cached = AppUpdateManager.getCachedAvailableUpdate(appContext)
                if (cached != null) {
                    val autoShow = AppUpdateManager.shouldAutoShowDialog(appContext, cached)
                    restoreDownloadedApkIfReady(cached, autoShow)
                    updateStateFlow.value = updateStateFlow.value.copy(
                        availableUpdate = cached,
                        showDialog = updateStateFlow.value.showDialog || autoShow,
                        isCheckSettled = true
                    )
                    restoreDownloadedUpdateIfAvailable(cached)
                    settleWatchdog.cancel()
                }

                val online = NetworkMonitor.isCurrentlyOnline(appContext) ||
                    NetworkMonitor.hasNetworkConnection(appContext)
                if (!online) return@launch

                val latest = AppUpdateManager.checkForUpdate(appContext)
                if (latest == null) {
                    activeDownloadId = null
                    downloadedApkFile = null
                    updateStateFlow.value = UpdateUiState(isCheckSettled = true)
                } else {
                    val autoShow = AppUpdateManager.shouldAutoShowDialog(appContext, latest)
                    restoreDownloadedApkIfReady(latest, autoShow)
                    updateStateFlow.value = updateStateFlow.value.copy(
                        availableUpdate = latest,
                        showDialog = updateStateFlow.value.showDialog || autoShow,
                        isCheckSettled = true
                    )
                    restoreDownloadedUpdateIfAvailable(latest)
                }
            } finally {
                settleWatchdog.cancel()
                if (!updateStateFlow.value.isCheckSettled) {
                    updateStateFlow.value = updateStateFlow.value.copy(isCheckSettled = true)
                }
            }
        }
    }

    fun openUpdateDialog() {
        val current = updateStateFlow.value.availableUpdate ?: return
        updateStateFlow.value = updateStateFlow.value.copy(
            availableUpdate = current,
            showDialog = true,
            isCheckSettled = true,
            errorMessage = null
        )
    }

    fun dismissUpdateDialogForToday() {
        val current = updateStateFlow.value.availableUpdate ?: return
        val state = updateStateFlow.value
        if (current.isForced || state.isPreparingUpdate) return
        updateStateFlow.value = state.copy(
            showDialog = false,
            isCheckSettled = true,
            errorMessage = null
        )
        viewModelScope.launch {
            AppUpdateManager.markDismissedForToday(appContext, current.versionCode)
        }
    }

    fun cancelUpdateDownload() {
        val state = updateStateFlow.value
        val current = state.availableUpdate
        if (current?.isForced == true) return
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        AppUpdateManager.cancelDownload(appContext, activeDownloadId, current?.versionCode)
        activeDownloadId = null
        downloadedApkFile = null
        updateStateFlow.value = state.copy(
            showDialog = false,
            isPreparingUpdate = false,
            isDownloading = false,
            downloadProgress = null,
            downloadStatusText = null,
            needsInstallPermission = false,
            pendingInstallReady = false,
            errorMessage = null
        )
    }

    fun onResumeInstallCheck() {
        val state = updateStateFlow.value
        val apkFile = downloadedApkFile
        if (state.needsInstallPermission && apkFile != null && apkFile.exists() &&
            AppUpdateManager.canInstallPackages(appContext)
        ) {
            updateStateFlow.value = state.copy(
                needsInstallPermission = false,
                pendingInstallReady = true,
                errorMessage = null
            )
            viewModelScope.launch {
                if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                    isSyncingFlow.value = true
                    try {
                        withTimeoutOrNull(5_000L) {
                            runCatching { repository.syncWithCloud() }
                        }
                    } finally {
                        isSyncingFlow.value = false
                    }
                }
                val launched = AppUpdateManager.launchApkInstaller(appContext, apkFile)
                if (!launched) {
                    updateStateFlow.value = updateStateFlow.value.copy(
                        errorMessage = "Could not open the Android installer for the downloaded APK."
                    )
                }
            }
        }
    }

    fun onUpdateNowClicked() {
        val state = updateStateFlow.value
        val current = state.availableUpdate ?: return
        if (state.isPreparingUpdate || state.isDownloading) return

        val readyFile = downloadedApkFile
        if (state.pendingInstallReady && readyFile != null && readyFile.exists()) {
            if (AppUpdateManager.canInstallPackages(appContext)) {
                // R4-D-U5: the old code fired triggerSync (async, un-awaited) and
                // opened the installer on the next line, so the install could
                // replace the app before the counts reached the cloud. Await
                // the pre-install sync first (5s timeout), like onResumeInstallCheck.
                viewModelScope.launch {
                    if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                        isSyncingFlow.value = true
                        try {
                            withTimeoutOrNull(5_000L) {
                                runCatching { repository.syncWithCloud() }
                            }
                        } finally {
                            isSyncingFlow.value = false
                        }
                    }
                    val launched = AppUpdateManager.launchApkInstaller(appContext, readyFile)
                    if (!launched) {
                        updateStateFlow.value = state.copy(
                            errorMessage = "Could not open the Android installer. Please try again."
                        )
                    }
                }
            } else {
                updateStateFlow.value = state.copy(
                    needsInstallPermission = true,
                    errorMessage = null
                )
                AppUpdateManager.openInstallUnknownAppsSettings(appContext)
            }
            return
        }

        activeDownloadJob = viewModelScope.launch {
            updateStateFlow.value = updateStateFlow.value.copy(
                isPreparingUpdate = true,
                isDownloading = false,
                downloadProgress = null,
                downloadStatusText = "Syncing your counts to cloud...",
                needsInstallPermission = false,
                pendingInstallReady = false,
                errorMessage = null
            )

            // Mandatory pre-install Firestore batch sync before starting download/install
            if (NetworkMonitor.isCurrentlyOnline(appContext)) {
                isSyncingFlow.value = true
                try {
                    withTimeoutOrNull(10_000L) { repository.syncWithCloud() }
                } finally {
                    isSyncingFlow.value = false
                }
            }

            updateStateFlow.value = updateStateFlow.value.copy(
                isPreparingUpdate = false,
                isDownloading = true,
                downloadProgress = null,
                downloadStatusText = "Starting download..."
            )

            AppUpdateManager.downloadApk(appContext, current).collect { downloadState ->
                when (downloadState) {
                    is ApkDownloadState.Downloading -> {
                        activeDownloadId = downloadState.downloadId
                        updateStateFlow.value = updateStateFlow.value.copy(
                            isDownloading = true,
                            downloadProgress = downloadState.progress,
                            downloadStatusText = AppUpdateManager.formatDownloadProgressText(
                                downloadState.bytesDownloaded,
                                downloadState.totalBytes
                            )
                        )
                    }

                    is ApkDownloadState.ReadyToInstall -> {
                        activeDownloadId = null
                        downloadedApkFile = downloadState.apkFile
                        updateStateFlow.value = updateStateFlow.value.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            downloadStatusText = "Download complete. Opening installer...",
                            needsInstallPermission = false,
                            pendingInstallReady = true,
                            showDialog = true,
                            errorMessage = null
                        )
                        // Sync once more before the installer opens, so the latest counts are on the server before the update restarts the app. A failed sync must never block the installer.
            withTimeoutOrNull(5_000L) { runCatching { repository.syncWithCloud() } }
            val launched = AppUpdateManager.launchApkInstaller(appContext, downloadState.apkFile)
                        if (!launched) {
                            updateStateFlow.value = updateStateFlow.value.copy(
                                errorMessage = "Could not open the Android installer for this APK."
                            )
                        }
                    }

                    is ApkDownloadState.NeedsInstallPermission -> {
                        activeDownloadId = null
                        downloadedApkFile = downloadState.apkFile
                        updateStateFlow.value = updateStateFlow.value.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            downloadStatusText = "Download complete",
                            needsInstallPermission = true,
                            pendingInstallReady = true,
                            errorMessage = null
                        )
                        AppUpdateManager.openInstallUnknownAppsSettings(appContext)
                    }

                    is ApkDownloadState.Failed -> {
                        activeDownloadId = null
                        downloadedApkFile = null
                        AppUpdateManager.cleanupPartialApkFiles(appContext, current.versionCode)
                        updateStateFlow.value = updateStateFlow.value.copy(
                            isPreparingUpdate = false,
                            isDownloading = false,
                            downloadProgress = null,
                            downloadStatusText = null,
                            needsInstallPermission = false,
                            pendingInstallReady = false,
                            errorMessage = downloadState.message
                        )
                    }
                }
            }
        }
    }

    fun dismissMilestoneBadgeCelebration() {
        val badge = pendingMilestoneBadgeFlow.value ?: return
        MilestoneBadgeManager.markBadgeCelebrated(appContext, currentUserId, badge.tier)
        pendingMilestoneBadgeFlow.value = MilestoneBadgeManager.getPendingCelebrationBadge(
            context = appContext,
            userId = currentUserId,
            mainDuroodTotalCount = uiState.value.totalCount
        )
        badgeRefreshTickFlow.value = badgeRefreshTickFlow.value + 1
    }

    fun dismissGroupCelebrationPopup() {
        val current = pendingGroupCelebrationFlow.value ?: return
        GroupCelebrationStore.markSeen(appContext, currentUserId, current.id)
        reloadUserGroupsAndCelebrations()
    }

    /**
     * Scopes ONE live single-document Firestore snapshot listener strictly to [groupId] while Group Detail is open,
     * flushes the current member's pending counts immediately if needed, and refreshes the group document on open.
     */
    fun openGroupDetail(groupId: String) {
        if (groupId.isBlank()) return
        openGroupIdFlow.value = groupId
        attachOpenGroupSingleDocListener(groupId)
        openGroupRefreshJob?.cancel()
        openGroupRefreshJob = viewModelScope.launch {
            performOpenGroupRefresh(groupId, reason = "open_refresh", forceCloudFlush = false)
        }
    }

    /**
     * Detaches the screen-scoped single-document listener immediately when Group Detail closes,
     * and flushes any unsent pending batch cleanly without keeping background listeners or polling alive.
     */
    fun closeGroupDetail(groupId: String) {
        if (openGroupListenerGroupId == groupId || openGroupIdFlow.value == groupId) {
            openGroupIdFlow.value = null
            openGroupFlushJob?.cancel()
            openGroupFlushJob = null
            openGroupRefreshJob?.cancel()
            openGroupRefreshJob = null
            isGroupRefreshingFlow.value = false
            openGroupListenerRemover?.invoke()
            openGroupListenerRemover = null
            openGroupListenerGroupId = null
            viewModelScope.launch {
                flushPendingCountsForGroup(groupId, reason = "close_flush", forceCloudFlush = false)
            }
        }
    }

    /**
     * Triggered when opening manual header Refresh or resuming while Group Detail is open.
     */
    fun refreshOpenGroupDetailNow(groupId: String? = openGroupIdFlow.value) {
        val targetId = groupId?.takeIf { it.isNotBlank() } ?: return
        openGroupFlushJob?.cancel()
        openGroupFlushJob = null
        openGroupRefreshJob?.cancel()
        openGroupRefreshJob = viewModelScope.launch {
            performOpenGroupRefresh(targetId, reason = "manual_refresh", forceCloudFlush = true)
        }
    }

    /**
     * One-shot fetch when opening the My Groups Hub (no persistent off-screen listener attached).
     */
    fun refreshGroupsHubOnce() {
        viewModelScope.launch {
            GroupRepository.refreshGroupsFromServer(appContext, currentUserId)
            reloadUserGroupsAndCelebrations()
        }
    }

    private fun attachOpenGroupSingleDocListener(groupId: String) {
        if (openGroupListenerRemover != null && openGroupListenerGroupId == groupId) return
        openGroupListenerRemover?.invoke()
        openGroupListenerRemover = null
        openGroupListenerGroupId = groupId
        val customHook = groupListenerAttachHook
        val remover = if (customHook != null) {
            customHook(groupId) { remoteGroup ->
                GroupRepository.upsertRemoteGroupLocally(appContext, remoteGroup)
                reloadUserGroupsAndCelebrations()
            }
        } else {
            GroupRepository.attachSingleGroupListener(
                context = appContext,
                groupId = groupId,
                onUpdated = {
                    reloadUserGroupsAndCelebrations()
                },
                onDeleted = {
                    reloadUserGroupsAndCelebrations()
                }
            )
        }
        if (remover != null) {
            openGroupListenerRemover = remover
        } else {
            openGroupListenerGroupId = null
        }
    }

    private suspend fun performOpenGroupRefresh(
        groupId: String,
        reason: String,
        forceCloudFlush: Boolean
    ) {
        isGroupRefreshingFlow.value = true
        try {
            flushPendingCountsForGroup(groupId, reason = reason, forceCloudFlush = forceCloudFlush)
            val customRefresh = groupSingleRefreshHook
            if (customRefresh != null) {
                val remote = customRefresh(groupId)
                if (remote != null) {
                    GroupRepository.upsertRemoteGroupLocally(appContext, remote)
                }
            } else {
                GroupRepository.refreshSingleGroupFromServer(appContext, groupId)
            }
            reloadUserGroupsAndCelebrations()
        } finally {
            isGroupRefreshingFlow.value = false
        }
    }

    private fun scheduleDebouncedOpenGroupFlush(groupId: String) {
        openGroupFlushJob?.cancel()
        openGroupFlushJob = viewModelScope.launch {
            delay(groupFlushDebounceMillis)
            if (openGroupIdFlow.value == groupId) {
                flushPendingCountsForGroup(
                    groupId = groupId,
                    reason = "debounced_open_flush",
                    forceCloudFlush = false
                )
            }
        }
    }

    private suspend fun flushPendingCountsForGroup(
        groupId: String,
        reason: String,
        forceCloudFlush: Boolean
    ) {
        val group = GroupRepository.getGroupById(appContext, groupId)
        val hasDirtyGroup = groupId in dirtyGroupSyncIds
        val shouldFlush = forceCloudFlush || hasDirtyGroup
        if (!shouldFlush) return

        val flushCountNow = groupBatchFlushCountForTest.incrementAndGet()
        Log.d("Groups", "flushPendingCountsForGroup($groupId, reason=$reason, batch=#$flushCountNow)")

        val customFlush = groupBatchFlushHook
        if (customFlush != null) {
            dirtyGroupSyncIds.remove(groupId)
            customFlush(groupId, reason)
            reloadUserGroupsAndCelebrations()
            return
        }

        val online = NetworkMonitor.isCurrentlyOnline(appContext) ||
            NetworkMonitor.hasNetworkConnection(appContext)
        if (!online) return

        if (group != null) {
            dirtyGroupSyncIds.remove(groupId)
            GroupRepository.pushGroupMemberSnapshotToCloud(appContext, currentUserId, groupId)
        }
    }

    private suspend fun pushDirtyGroupStatsDuringBatchSync() {
        val groupIds = (dirtyGroupSyncIds + GroupRepository.getDirtyGroupSyncIds(appContext)).distinct()
        groupIds.forEach { dirtyGroupId ->
            // Round 1 P2-1: push FIRST, clear the dirty flag only on success.
            // The old code cleared before pushing and discarded the result, so
            // a failed stats push was never retried via the dirty set.
            val pushed = GroupRepository.pushGroupMemberSnapshotToCloud(appContext, currentUserId, dirtyGroupId)
            if (pushed) {
                dirtyGroupSyncIds.remove(dirtyGroupId)
                GroupRepository.clearGroupDirtyForSync(appContext, dirtyGroupId)
            }
        }
    }

    private fun reloadUserGroupsAndCelebrations() {
        val groups = GroupRepository.getGroupsForUser(appContext, currentUserId)
        userGroupsFlow.value = groups
        com.example.floating.FloatingCounterManager.syncActiveGroupState(appContext, currentUserId)
        val today = currentDateFlow.value
        val nextCelebration = GroupCelebrationStore.getNextUnseenCelebration(
            context = appContext,
            userId = currentUserId,
            groups = groups,
            today = today
        )
        pendingGroupCelebrationFlow.value = nextCelebration
    }

    fun onCreateGroup(
        name: String,
        type: GroupType,
        zikrName: String? = null,
        targetCount: Long = DuroodRepository.ONE_CRORE,
        onCreated: (GroupItem) -> Unit = {}
    ) {
        viewModelScope.launch {
            val today = refreshCurrentDate()
            val todayKey = today.toString()
            val profile = runCatching { repository.getProfileOnce(currentUserId) }.getOrNull()
            val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackDisplayName

            val result = GroupRepository.createGroup(
                context = appContext,
                creatorUserId = currentUserId,
                creatorDisplayName = displayName,
                name = name,
                type = type,
                zikrName = zikrName,
                targetCount = targetCount,
                creatorTodayCount = 0L,
                creatorLifetimeTotal = 0L,
                creatorWeeklyCount = 0L,
                todayDateKey = todayKey
            )
            result.fold(
                onSuccess = { created ->
                    reloadUserGroupsAndCelebrations()
                    statusMessageFlow.value = "Group created · Code: ${created.inviteCode}"
                    onCreated(created)
                },
                onFailure = { err ->
                    statusMessageFlow.value = err.localizedMessage ?: "Could not create group."
                }
            )
        }
    }

    fun onJoinGroupByCode(
        code: String,
        onJoined: (GroupItem) -> Unit = {}
    ) {
        if (isJoiningGroupFlow.value) return
        isJoiningGroupFlow.value = true
        viewModelScope.launch {
            isJoiningGroupFlow.value = true
            try {
                val today = refreshCurrentDate()
                val todayKey = today.toString()
                val profile = runCatching { repository.getProfileOnce(currentUserId) }.getOrNull()
                val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackDisplayName

                val result = GroupRepository.joinGroupByCode(
                    context = appContext,
                    userId = currentUserId,
                    displayName = displayName,
                    rawInviteCode = code,
                    userTodayCount = 0L,
                    userLifetimeTotal = 0L,
                    userWeeklyCount = 0L,
                    todayDateKey = todayKey
                )
                result.fold(
                    onSuccess = { joined ->
                        reloadUserGroupsAndCelebrations()
                        statusMessageFlow.value = "Joined ${joined.name}"
                        onJoined(joined)
                    },
                    onFailure = { err ->
                        statusMessageFlow.value = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Invalid code"
                    }
                )
            } finally {
                isJoiningGroupFlow.value = false
            }
        }
    }

    fun onRenameGroup(groupId: String, newName: String) {
        viewModelScope.launch {
            val result = GroupRepository.renameGroup(
                context = appContext,
                userId = currentUserId,
                groupId = groupId,
                newName = newName
            )
            result.fold(
                onSuccess = {
                    reloadUserGroupsAndCelebrations()
                    statusMessageFlow.value = "Group renamed"
                },
                onFailure = { err ->
                    statusMessageFlow.value = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Could not rename group."
                }
            )
        }
    }

    fun onLeaveGroup(groupId: String, onLeft: () -> Unit = {}) {
        viewModelScope.launch {
            val result = GroupRepository.leaveGroup(appContext, currentUserId, groupId)
            result.fold(
                onSuccess = {
                    closeGroupDetail(groupId)
                    dirtyGroupSyncIds.remove(groupId)
                    reloadUserGroupsAndCelebrations()
                    statusMessageFlow.value = "Left group"
                    onLeft()
                },
                onFailure = { err ->
                    statusMessageFlow.value = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Could not leave group."
                }
            )
        }
    }

    fun onDeleteGroup(groupId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            closeGroupDetail(groupId)
            dirtyGroupSyncIds.remove(groupId)
            val result = GroupRepository.deleteGroup(appContext, currentUserId, groupId)
            result.fold(
                onSuccess = {
                    reloadUserGroupsAndCelebrations()
                    statusMessageFlow.value = "Group deleted"
                    onDeleted()
                },
                onFailure = { err ->
                    statusMessageFlow.value = err.localizedMessage?.takeIf { it.isNotBlank() } ?: "Could not delete group."
                }
            )
        }
    }

    fun onInviteGroupWhatsApp(group: GroupItem) {
        viewModelScope.launch {
            GroupRepository.launchMergedWhatsAppInvite(appContext, group)
        }
    }

    fun onAdjustMainGroupCount(groupId: String, delta: Long) {
        if (delta == 0L) return
        viewModelScope.launch {
            val today = refreshCurrentDate()
            val todayKey = today.toString()
            val profile = runCatching { repository.getProfileOnce(currentUserId) }.getOrNull()
            val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackDisplayName
            GroupRepository.adjustMainGroupCount(
                context = appContext,
                userId = currentUserId,
                displayName = displayName,
                groupId = groupId,
                delta = delta,
                todayDateKey = todayKey,
                pushToCloud = false
            ).onSuccess {
                dirtyGroupSyncIds.add(groupId)
                if (openGroupIdFlow.value == groupId) {
                    scheduleDebouncedOpenGroupFlush(groupId)
                }
            }.onFailure { err ->
                statusMessageFlow.value = err.localizedMessage ?: "Failed to update group count."
            }
            reloadUserGroupsAndCelebrations()
        }
    }

    fun onIncrementCustomZikrGroup(groupId: String, delta: Long) {
        if (delta == 0L) return
        viewModelScope.launch {
            val today = refreshCurrentDate()
            val todayKey = today.toString()
            val profile = runCatching { repository.getProfileOnce(currentUserId) }.getOrNull()
            val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: fallbackDisplayName
            GroupRepository.incrementCustomZikrGroup(
                context = appContext,
                userId = currentUserId,
                displayName = displayName,
                groupId = groupId,
                delta = delta,
                todayDateKey = todayKey,
                pushToCloud = false
            ).onSuccess {
                dirtyGroupSyncIds.add(groupId)
                if (openGroupIdFlow.value == groupId) {
                    scheduleDebouncedOpenGroupFlush(groupId)
                }
            }.onFailure { err ->
                statusMessageFlow.value = err.localizedMessage ?: "Failed to add zikr count."
            }
            reloadUserGroupsAndCelebrations()
        }
    }

    private fun computeLast7DaysTotal(days: List<DailyCountEntity>, today: LocalDate): Long {
        val cutoffKey = today.minusDays(6).toString()
        val todayKey = today.toString()
        return days
            .filter { it.dateKey in cutoffKey..todayKey }
            .sumOf { it.count.coerceAtLeast(0L) }
    }

    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
             
             
                        if (!NetworkMonitor.isCurrentlyOnline(appContext)) {
                statusMessageFlow.value = "Connect to the internet to delete your account"
                return@launch
            }
            val groupsSnapshot = GroupRepository.snapshotLocalGroups(appContext)
            val currentGroups = GroupRepository.getGroupsForUser(appContext, currentUserId)
            for (group in currentGroups) {
                val leaveResult = GroupRepository.leaveGroup(appContext, currentUserId, group.id)
                if (leaveResult.isFailure) {
                    GroupRepository.restoreLocalGroupsSnapshot(appContext, groupsSnapshot)
                    statusMessageFlow.value = (leaveResult.exceptionOrNull()?.localizedMessage ?: "Delete failed. Please try again.") + " If a group leave already went through, re-join with the invite codes."
                    return@launch
                }
            }
            ReminderScheduler.cancelAll(appContext)
            val result = repository.deleteAllUserData(currentUserId)
        if (result.isFailure) {
                GroupRepository.restoreLocalGroupsSnapshot(appContext, groupsSnapshot)
                statusMessageFlow.value = (result.exceptionOrNull()?.localizedMessage ?: "Delete failed. Please try again.") + " If a group leave already went through, re-join with the invite codes."
                return@launch
            }
        result.onSuccess({
                // Guard above already returned on failure, so this always runs.
                AuthFlowStore.clearUser(appContext, currentUserId)
                MilestoneBadgeManager.clearUser(appContext, currentUserId)
                GroupCelebrationStore.clearUser(appContext, currentUserId)
                GroupRepository.clearLocalGroupsStore(appContext)
                onSuccess()
                }
            )
        }
    }

    private data class UpdateUiState(
        val availableUpdate: AppUpdateInfo? = null,
        val showDialog: Boolean = false,
        val isCheckSettled: Boolean = false,
        val isPreparingUpdate: Boolean = false,
        val isDownloading: Boolean = false,
        val downloadProgress: Float? = null,
        val downloadStatusText: String? = null,
        val needsInstallPermission: Boolean = false,
        val pendingInstallReady: Boolean = false,
        val errorMessage: String? = null
    )

    private data class CountMetrics(
        val profile: UserProfileEntity?,
        val days: List<DailyCountEntity>,
        val total: Long,
        val today: Long,
        val pending: Boolean,
        val pendingDaysCount: Int,
        val pendingDuroodTotal: Long,
        val currentDate: LocalDate
    )

    private data class GroupUiTuple(
        val userGroups: List<GroupItem>,
        val pendingGroupCelebration: GroupCelebrationNotice?,
        val isGroupRefreshing: Boolean,
        val isJoiningGroup: Boolean
    )

    private data class BadgeAndGroupUiState(
        val badgeProgress: MilestoneBadgeProgress,
        val pendingMilestoneBadge: MilestoneBadge?,
        val userGroups: List<GroupItem>,
        val pendingGroupCelebration: GroupCelebrationNotice?,
        val isGroupRefreshing: Boolean = false,
        val isJoiningGroup: Boolean = false
    )

    private data class ExtraUiState(
        val updateState: UpdateUiState,
        val globalStats: GlobalCounterStats?,
        val activeAnnouncement: AnnouncementItem?,
        val isDirectAnnouncement: Boolean,
        val announcementHistory: List<AnnouncementHistoryEntry>
    )

    private data class StatusAndThemeUi(
        val isSyncing: Boolean,
        val statusMessage: String?,
        val initialized: Boolean,
        val themeMode: AppThemeMode
    )

    private data class BaseData(
        val profile: UserProfileEntity?,
        val days: List<DailyCountEntity>,
        val customCounters: List<CustomCounterEntity>,
        val total: Long,
        val today: Long,
        val pending: Boolean,
        val pendingDaysCount: Int,
        val pendingDuroodTotal: Long,
        val currentDate: LocalDate,
        val updateState: UpdateUiState,
        val initialLoadOfflineError: Boolean,
        val globalStats: GlobalCounterStats?,
        val activeAnnouncement: AnnouncementItem?,
        val isDirectAnnouncement: Boolean,
        val announcementHistory: List<AnnouncementHistoryEntry>,
        val badgeProgress: MilestoneBadgeProgress,
        val pendingMilestoneBadge: MilestoneBadge?,
        val userGroups: List<GroupItem>,
        val pendingGroupCelebration: GroupCelebrationNotice?,
        val isGroupRefreshing: Boolean = false,
        val isJoiningGroup: Boolean = false
    )

    companion object {
        const val BATCH_SYNC_TAP_THRESHOLD = 100
        const val AUTO_SYNC_INTERVAL_MILLIS = 5L * 60L * 1000L
        const val GROUP_FLUSH_DEBOUNCE_MILLIS = 1_500L
        const val INITIAL_CLOUD_SYNC_TIMEOUT_MS = 2_000L
    }
}
