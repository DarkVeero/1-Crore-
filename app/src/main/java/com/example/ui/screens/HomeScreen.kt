package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.announcements.AnnouncementItem
import com.example.announcements.AnnouncementNotificationHelper
import com.example.data.DuroodRepository
import com.example.data.GlobalCounterStats
import com.example.data.GlobalStatsCache
import com.example.data.local.CustomCounterEntity
import com.example.floating.FloatingCounterManager
import com.example.floating.SmallOneCroreLogoGlyph
import com.example.ui.MainUiState
import com.example.ui.components.AnnouncementDialog
import com.example.ui.components.BlackPillButton
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.MilestoneBadgeCelebrationDialog
import com.example.ui.components.OfflineSyncBadge
import com.example.ui.components.RollingNumberText
import com.example.ui.components.WhiteBorderPillButton
import com.example.ui.components.formatNumber
import com.example.ui.components.formatPendingSyncSummary
import com.example.ui.components.formatSyncTime
import com.example.ui.components.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ui.theme.AppThemeColors
import com.example.update.AppUpdateInfo
import com.example.update.AppUpdateManager
import java.time.format.DateTimeFormatter
import java.util.Locale

internal const val ARABIC_GREETING = "صَلُّوا عَلَيْهِ\nوَسَلِّمُوا تَسْلِيمًا"
internal const val HOME_AYAT_BASE_FONT_SIZE_SP = 22f

internal fun arabicGraphemeEndIndices(text: String): List<Int> {
    val ends = mutableListOf<Int>()
    var i = 0
    while (i < text.length) {
        if (text[i] == '\n') {
            i++
            continue
        }
        i++
        while (i < text.length) {
            val type = Character.getType(text[i])
            if (type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt()
            ) {
                i++
            } else {
                break
            }
        }
        ends.add(i)
    }
    return ends
}

@Composable
fun HomeScreen(
    uiState: MainUiState,
    canPresentPopups: Boolean = true,
    onOpenCounter: () -> Unit,
    onAddCustomAmount: (Long) -> Unit = {},
    initialShowCustomDialog: Boolean = false,
    onConsumedInitialCustomDialog: () -> Unit = {},
    onOpenCustomCounter: (String) -> Unit,
    onOpenStats: () -> Unit = {},
    onOpenGroups: () -> Unit = {},
    onDismissMilestoneBadge: () -> Unit = {},
    onDismissGroupCelebration: () -> Unit = {},
    onCheckForUpdate: () -> Unit,
    onRefreshGlobalStats: () -> Unit = {},
    onRefreshAnnouncements: () -> Unit = {},
    onAnnouncementShown: (AnnouncementItem) -> Unit = {},
    onDismissAnnouncement: () -> Unit = {},
    onAnnouncementAction: (AnnouncementItem) -> Unit = {},
    onOpenUpdateDialog: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onCancelUpdateDownload: () -> Unit,
    onUpdateNow: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val palette = AppThemeColors.palette
    var showShareChoiceDialog by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(initialShowCustomDialog) }

    LaunchedEffect(initialShowCustomDialog) {
        if (initialShowCustomDialog) {
            showCustomDialog = true
            onConsumedInitialCustomDialog()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            AnnouncementNotificationHelper.ensureSubscribedToAnnouncementsTopic(context)
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = uiState.progressFraction,
        animationSpec = tween(durationMillis = if (reduceMotion) 0 else 280, easing = FastOutSlowInEasing),
        label = "home_progress"
    )
    val finishDateFormatted = uiState.estimatedFinishDate.format(
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
    )

    LaunchedEffect(Unit) {
        onCheckForUpdate()
        onRefreshGlobalStats()
        onRefreshAnnouncements()
        AnnouncementNotificationHelper.ensureSubscribedToAnnouncementsTopic(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            AnnouncementNotificationHelper.shouldRequestNotificationPermissionOnHomeOpen(context)
        ) {
            AnnouncementNotificationHelper.markNotificationPermissionRequested(context)
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(uiState.isOnline) {
        if (uiState.isOnline) {
            onRefreshGlobalStats()
            onRefreshAnnouncements()
        }
    }

    LaunchedEffect(canPresentPopups) {
        if (canPresentPopups) {
            onRefreshAnnouncements()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalMargin = if (maxWidth < 340.dp) 16.dp else 24.dp
        FaintGridBackground(reduceMotion = reduceMotion)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = horizontalMargin,
                    end = horizontalMargin,
                    top = 12.dp,
                    bottom = 20.dp
                )
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_header_column")
            ) {
                val globalStats = uiState.globalStats
                val hasGlobalStats = globalStats != null &&
                    globalStats.totalCount > 0L &&
                    globalStats.userCount > 0L
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_header_top_row")
                ) {
                    val headerGutter = if (maxWidth < 312.dp) 8.dp else 12.dp
                    val maxAyatWidth = minOf((maxWidth - headerGutter) * 0.48f, 232.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(headerGutter)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (hasGlobalStats && globalStats != null) {
                                GlobalCounterTopLeftBlock(
                                    stats = globalStats,
                                    isOnline = uiState.isOnline,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        AnimatedArabicGreeting(
                            reduceMotion = reduceMotion,
                            modifier = Modifier.widthIn(max = maxAyatWidth)
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .testTag("home_header_below_ayat_spacer")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_welcome_row"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Welcome, ${uiState.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.onCanvas,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("home_welcome_text")
                        )
                        uiState.startDateFormatted?.let { started ->
                            Text(
                                text = "Started $started",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.onCanvasSubtext,
                                modifier = Modifier.testTag("home_started_date")
                            )
                        }
                    }
                    if (uiState.availableUpdate != null) {
                        UpdateBadgeIconButton(onClick = onOpenUpdateDialog)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                OfflineSyncBadge(
                    isOnline = uiState.isOnline,
                    hasPendingSync = uiState.hasPendingSync,
                    isSyncing = uiState.isSyncing,
                    lastSyncedAtMillis = uiState.profile?.lastSyncedAtMillis ?: 0L,
                    pendingSyncDuroodCount = uiState.pendingSyncDuroodCount,
                    pendingSyncDaysCount = uiState.pendingSyncDaysCount
                )
            }

            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL RECITED",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.onCardSubtext
                        )
                        Text(
                            text = uiState.progressPercentFormatted,
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.onCard,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    RollingNumberText(
                        value = uiState.totalCount,
                        style = MaterialTheme.typography.displayLarge,
                        reduceMotion = reduceMotion,
                        color = palette.onCard
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "out of ${formatNumber(DuroodRepository.ONE_CRORE)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCardSubtext
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(palette.track, RoundedCornerShape(999.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.coerceAtLeast(if (uiState.totalCount > 0L) 0.01f else 0f))
                                .fillMaxHeight()
                                .background(palette.accent, RoundedCornerShape(999.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${formatNumber(uiState.remaining)} remaining",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onCard
                        )
                        Text(
                            text = "Goal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onCardSubtext
                        )
                    }
                }
            }

            Surface(
                color = palette.cardSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        HomeMetricCell(
                            label = "TODAY VS TARGET",
                            value = "${formatNumber(uiState.todayCount)} / ${formatNumber(uiState.dailyTarget.toLong())}",
                            subtext = if (uiState.todayRemaining == 0L) "Today complete" else "${formatNumber(uiState.todayRemaining)} left today",
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(palette.hairline)
                        )
                        HomeMetricCell(
                            label = "DAYS LEFT",
                            value = formatNumber(uiState.daysLeft),
                            subtext = "at ${formatNumber(uiState.dailyTarget.toLong())} / day",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = palette.hairline, thickness = 1.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        HomeMetricCell(
                            label = "FINISH DATE",
                            value = finishDateFormatted,
                            subtext = "Estimated completion",
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(palette.hairline)
                        )
                        HomeMetricCell(
                            label = "LAST SYNCED",
                            value = formatSyncTime(uiState.profile?.lastSyncedAtMillis ?: 0L),
                            subtext = if (uiState.hasPendingSync) {
                                formatPendingSyncSummary(
                                    pendingDuroodCount = uiState.pendingSyncDuroodCount,
                                    pendingDaysCount = uiState.pendingSyncDaysCount
                                )
                            } else {
                                "Up to date"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_counter_actions_row"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BlackPillButton(
                    text = "Open Counter",
                    onClick = onOpenCounter,
                    reduceMotion = reduceMotion,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_open_counter_button")
                )
                WhiteBorderPillButton(
                    text = "Custom Add",
                    onClick = { showCustomDialog = true },
                    reduceMotion = reduceMotion,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_custom_add_button")
                )
            }

            if (uiState.customCounters.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_custom_counters_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CUSTOM COUNTERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onCanvasSubtext
                    )
                    Surface(
                        color = palette.cardSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            uiState.customCounters.forEachIndexed { index, counter ->
                                HomeCustomCounterRow(
                                    counter = counter,
                                    onClick = { onOpenCustomCounter(counter.id) }
                                )
                                if (index < uiState.customCounters.lastIndex) {
                                    HorizontalDivider(color = palette.hairline, thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }

            HomeMyGroupsCard(
                groups = uiState.userGroups,
                onOpenGroups = onOpenGroups
            )

            val floatingState by FloatingCounterManager.state.collectAsStateWithLifecycle()
            FloatingButtonHomeToggleRow(
                isEnabled = floatingState.isMainRunning,
                onToggle = {
                    FloatingCounterManager.toggleFloatingButton(context)
                }
            )

            WhiteBorderPillButton(
                text = "Share this app",
                onClick = {
                    showShareChoiceDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_share_app_button")
            )
        }
    }

    if (showShareChoiceDialog) {
        ShareChoiceDialog(
            onDismiss = { showShareChoiceDialog = false },
            onSelectProgressCard = {
                showShareChoiceDialog = false
                val freshToday = uiState.todayCount
                val freshTotal = uiState.totalCount
                scope.launch {
                    AppUpdateManager.shareProgressCard(
                        context = context,
                        todayCount = freshToday,
                        totalCount = freshTotal
                    )
                }
            },
            onSelectSimpleProgress = {
                showShareChoiceDialog = false
                scope.launch {
                    AppUpdateManager.shareApp(context)
                }
            }
        )
    }

    if (showCustomDialog) {
        CustomAmountDialog(
            onDismiss = { showCustomDialog = false },
            onConfirm = { amount ->
                triggerTapVibration(context, strong = true)
                onAddCustomAmount(amount)
                showCustomDialog = false
            }
        )
    }

    val availableUpdate = uiState.availableUpdate
    val isUpdateDialogVisible = canPresentPopups && uiState.showUpdateDialog && availableUpdate != null
    val isAnnouncementVisible = canPresentPopups &&
        (uiState.isDirectAnnouncement || uiState.isUpdateCheckSettled) &&
        uiState.activeAnnouncement != null
    val pendingBadge = uiState.pendingMilestoneBadge
    val pendingGroupCelebration = uiState.pendingGroupCelebration

    if (isUpdateDialogVisible && availableUpdate != null) {
        AppUpdateDialog(
            update = availableUpdate,
            isPreparing = uiState.isPreparingUpdate,
            isDownloading = uiState.isDownloadingUpdate,
            downloadProgress = uiState.updateDownloadProgress,
            downloadStatusText = uiState.updateDownloadStatusText,
            needsInstallPermission = uiState.needsInstallUnknownAppsPermission,
            pendingInstallReady = uiState.pendingApkInstallReady,
            errorMessage = uiState.updateErrorMessage,
            onUpdateNow = onUpdateNow,
            onDismiss = onDismissUpdateDialog,
            onCancelDownload = onCancelUpdateDownload
        )
    } else if (isAnnouncementVisible && uiState.activeAnnouncement != null) {
        val announcement = uiState.activeAnnouncement
        LaunchedEffect(announcement.id, uiState.isDirectAnnouncement) {
            onAnnouncementShown(announcement)
        }
        AnnouncementDialog(
            announcement = announcement,
            onDismiss = onDismissAnnouncement,
            onActionClick = onAnnouncementAction
        )
    } else if (canPresentPopups && pendingBadge != null) {
        MilestoneBadgeCelebrationDialog(
            badge = pendingBadge,
            earnedCount = uiState.badgeProgress.earnedCount,
            onDismiss = onDismissMilestoneBadge
        )
    } else if (canPresentPopups && pendingGroupCelebration != null) {
        GroupCelebrationPopupDialog(
            notice = pendingGroupCelebration,
            onDismiss = onDismissGroupCelebration
        )
    }
}

@Composable
fun GlobalCounterTopLeftBlock(
    stats: GlobalCounterStats,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    BoxWithConstraints(modifier = modifier.testTag("home_global_counter_block")) {
        var textScale by remember(maxWidth, stats.totalCount, stats.userCount, isOnline) {
            mutableFloatStateOf(1f)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = GlobalStatsCache.formatTotalLine(stats.totalCount),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = (11f * textScale).sp,
                    lineHeight = (14f * textScale).sp,
                    letterSpacing = 0.sp
                ),
                color = palette.onCanvas,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    if ((layoutResult.lineCount > 1 || layoutResult.hasVisualOverflow) && textScale > 0.78f) {
                        textScale = (textScale - 0.06f).coerceAtLeast(0.78f)
                    }
                },
                modifier = Modifier.testTag("home_global_counter_total")
            )
            Text(
                text = GlobalStatsCache.formatUsersLine(stats.userCount),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = (10f * textScale).sp,
                    lineHeight = (13f * textScale).sp,
                    letterSpacing = 0.sp
                ),
                color = palette.onCanvasSubtext,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("home_global_counter_users")
            )
            Text(
                text = GlobalStatsCache.formatSubtitleLine(
                    isOnline = isOnline,
                    fetchedAtMillis = stats.fetchedAtMillis
                ),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = (9f * textScale).sp,
                    lineHeight = (12f * textScale).sp,
                    letterSpacing = 0.sp
                ),
                color = palette.onCanvasSubtext,
                fontWeight = FontWeight.Normal,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    if (layoutResult.hasVisualOverflow && textScale > 0.78f) {
                        textScale = (textScale - 0.06f).coerceAtLeast(0.78f)
                    }
                },
                modifier = Modifier.testTag("home_global_counter_subtitle")
            )
        }
    }
}

@Composable
private fun UpdateBadgeIconButton(
    onClick: () -> Unit
) {
    val palette = AppThemeColors.palette
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .testTag("home_update_badge_button")
    ) {
        Box(modifier = Modifier.size(26.dp)) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdate,
                contentDescription = "Update available",
                tint = palette.onCanvas,
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.Center)
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .background(palette.accent, CircleShape)
                    .border(1.5.dp, palette.canvasBackground, CircleShape)
            )
        }
    }
}

@Composable
private fun AnimatedArabicGreeting(
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    val clusterEnds = remember { arabicGraphemeEndIndices(ARABIC_GREETING) }
    var visibleClusters by remember(reduceMotion) {
        mutableIntStateOf(if (reduceMotion) clusterEnds.size else 0)
    }
    val greetingAlpha = remember(reduceMotion) { Animatable(1f) }

    LaunchedEffect(reduceMotion) {
        if (!reduceMotion) {
            repeat(2) {
                greetingAlpha.snapTo(1f)
                visibleClusters = 0
                delay(200L)
                for (count in 1..clusterEnds.size) {
                    visibleClusters = count
                    delay(110L)
                }
                delay(2000L)
                greetingAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                )
                visibleClusters = 0
                delay(300L)
            }
        greetingAlpha.snapTo(1f)
            visibleClusters = clusterEnds.size
        } else {
            greetingAlpha.snapTo(1f)
            visibleClusters = clusterEnds.size
        }
    }

    val displayedGreeting = when {
        reduceMotion || visibleClusters >= clusterEnds.size -> ARABIC_GREETING
        visibleClusters <= 0 -> ""
        else -> ARABIC_GREETING.substring(0, clusterEnds[visibleClusters - 1])
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(modifier = modifier) {
            var greetingFontSizeSp by remember(maxWidth) { mutableFloatStateOf(HOME_AYAT_BASE_FONT_SIZE_SP) }
            val currentStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = greetingFontSizeSp.sp,
                lineHeight = (greetingFontSizeSp * 1.4f).sp,
                textAlign = TextAlign.Right,
                textDirection = TextDirection.Rtl
            )
            Box(contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = ARABIC_GREETING,
                    style = currentStyle,
                    textAlign = TextAlign.Right,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    color = palette.onCanvas,
                    fontWeight = FontWeight.SemiBold,
                    onTextLayout = { layoutResult ->
                        if ((layoutResult.hasVisualOverflow || layoutResult.lineCount > 2) && greetingFontSizeSp > 11f) {
                            greetingFontSizeSp = (greetingFontSizeSp - 1f).coerceAtLeast(11f)
                        }
                    },
                    modifier = Modifier.alpha(0f)
                )
                Text(
                    text = displayedGreeting,
                    style = currentStyle,
                    textAlign = TextAlign.Right,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Clip,
                    color = palette.onCanvas,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            alpha = greetingAlpha.value
                        }
                        .testTag("home_arabic_greeting")
                )
            }
        }
    }
}

@Composable
internal fun AppUpdateDialog(
    update: AppUpdateInfo,
    isPreparing: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    downloadStatusText: String?,
    needsInstallPermission: Boolean,
    pendingInstallReady: Boolean,
    errorMessage: String?,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit,
    onCancelDownload: () -> Unit
) {
    val palette = AppThemeColors.palette
    val busy = isPreparing || isDownloading
    val canDismiss = !update.isForced && !busy
    AlertDialog(
        onDismissRequest = {
            if (canDismiss) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = canDismiss,
            dismissOnClickOutside = canDismiss
        ),
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
            .testTag("app_update_dialog"),
        title = {
            Text(
                text = "Version ${update.versionName} is available",
                style = MaterialTheme.typography.titleLarge,
                color = palette.onCard,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("update_dialog_title")
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (update.changelog.isNotBlank()) {
                    Text(
                        text = update.changelog,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCard,
                        modifier = Modifier.testTag("update_dialog_changelog")
                    )
                }
                if (update.isForced) {
                    Text(
                        text = "This update contains a critical data-safety or bug fix and is required to continue.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCardSubtext,
                        modifier = Modifier.testTag("update_dialog_forced_notice")
                    )
                }
                if (isPreparing || isDownloading) {
                    if (downloadProgress != null && isDownloading) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            color = palette.accent,
                            trackColor = palette.track,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_download_progress_bar")
                        )
                    } else {
                        LinearProgressIndicator(
                            color = palette.accent,
                            trackColor = palette.track,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_download_progress_bar")
                        )
                    }
                    Text(
                        text = downloadStatusText ?: if (isPreparing) {
                            "Syncing your counts to cloud before updating..."
                        } else {
                            "Downloading update..."
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onCardSubtext,
                        modifier = Modifier.testTag("update_download_status_text")
                    )
                }
                if (needsInstallPermission) {
                    Text(
                        text = "Download complete. Allow 'Install unknown apps' for 1 Crore once in Settings so Android can install this update.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.onCard,
                        modifier = Modifier.testTag("update_install_permission_notice")
                    )
                }
                if (errorMessage != null) {
                    Surface(
                        color = palette.subtleSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.error, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onCard,
                            modifier = Modifier
                                .padding(10.dp)
                                .testTag("update_error_banner")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!update.isForced) {
                    if (isDownloading) {
                        TextButton(
                            onClick = onCancelDownload,
                            modifier = Modifier.testTag("update_cancel_download_button")
                        ) {
                            Text("Cancel", color = palette.onCardSubtext)
                        }
                    } else {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !isPreparing,
                            modifier = Modifier.testTag("update_later_button")
                        ) {
                            Text("Later", color = palette.onCardSubtext)
                        }
                    }
                }
                val actionLabel = when {
                    isPreparing -> "Syncing..."
                    isDownloading -> "Downloading..."
                    needsInstallPermission -> "Allow & Install"
                    pendingInstallReady -> "Install now"
                    else -> "Update now"
                }
                BlackPillButton(
                    text = actionLabel,
                    enabled = !busy,
                    onClick = onUpdateNow,
                    modifier = Modifier.testTag("update_now_button")
                )
            }
        }
    )
}

@Composable
private fun HomeCustomCounterRow(
    counter: CustomCounterEntity,
    onClick: () -> Unit
) {
    val palette = AppThemeColors.palette
    val ratio = (counter.currentCount.toDouble() / counter.targetCount.coerceAtLeast(1L).toDouble())
        .toFloat()
        .coerceIn(0f, 1f)
    val pct = String.format(Locale.US, "%.1f%%", ratio * 100f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("home_custom_counter_${counter.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = counter.name,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onCard,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${formatNumber(counter.currentCount)} / ${formatNumber(counter.targetCount)} · $pct",
                style = MaterialTheme.typography.labelMedium,
                color = palette.onCardSubtext
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.track)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceAtLeast(if (counter.currentCount > 0L) 0.01f else 0f))
                    .fillMaxHeight()
                    .background(palette.accent, RoundedCornerShape(999.dp))
            )
        }
    }
}

@Composable
fun FloatingButtonHomeToggleRow(
    isEnabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    Surface(
        color = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, palette.hairline, RoundedCornerShape(16.dp))
            .clickable(role = Role.Switch, onClick = onToggle)
            .semantics {
                contentDescription = "Floating button"
                stateDescription = if (isEnabled) "On" else "Off"
            }
            .testTag("home_floating_button_row")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(palette.accent)
                ) {
                    SmallOneCroreLogoGlyph(modifier = Modifier.size(20.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Floating button",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.onCard,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Count Durood over WhatsApp or any app",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.onCardSubtext
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                color = if (isEnabled) palette.accent else palette.cardSurface,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        color = if (isEnabled) palette.accent else palette.hairline,
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onToggle)
                    .testTag("home_floating_button_toggle")
            ) {
                Text(
                    text = if (isEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isEnabled) palette.onAccent else palette.onCard,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeMetricCell(
    label: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeColors.palette
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.onCardSubtext
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = palette.onCard,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtext,
            style = MaterialTheme.typography.labelMedium,
            color = palette.onCardSubtext
        )
    }
}

@Composable
internal fun ShareChoiceDialog(
    onDismiss: () -> Unit,
    onSelectProgressCard: () -> Unit,
    onSelectSimpleProgress: () -> Unit
) {
    val palette = AppThemeColors.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .border(1.dp, palette.strongBorder, RoundedCornerShape(16.dp))
            .testTag("share_choice_dialog"),
        title = {
            Text(
                text = "Share",
                style = MaterialTheme.typography.titleMedium,
                color = palette.onCard,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("share_choice_dialog_title")
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BlackPillButton(
                    text = "Progress card",
                    onClick = onSelectProgressCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_choice_progress_card_button")
                )
                WhiteBorderPillButton(
                    text = "Simple progress",
                    onClick = onSelectSimpleProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("share_choice_simple_progress_button")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("share_choice_cancel_button")
            ) {
                Text("Cancel", color = palette.onCardSubtext)
            }
        }
    )
}


