package com.example.ui

import android.app.Application
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.announcements.AnnouncementLaunchRequest
import com.example.data.DuroodRepository
import com.example.floating.FloatingCounterManager
import com.example.notifications.ReminderScheduler
import com.example.ui.auth.AuthFlowStore
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.LinkPasswordScreen
import com.example.ui.auth.VerifyEmailScreen
import com.example.ui.auth.reauthenticateWithGoogle
import com.example.ui.auth.signOutUser
import com.example.ui.components.rememberReduceMotion
import com.example.ui.screens.AnimatedLogoLoadingScreen
import com.example.ui.screens.CounterScreen
import com.example.ui.screens.CustomCounterDetailScreen
import com.example.ui.screens.CustomCountersScreen
import com.example.ui.screens.GroupDetailScreen
import com.example.ui.screens.GroupsHubScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MessagesHistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SplashSessionGuard
import com.example.ui.screens.StatsScreen
import com.example.ui.theme.AppThemeColors
import com.example.widget.OneCroreWidgetProvider
import com.example.widget.WidgetAndFloatingBridge
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await

private const val ROUTE_CUSTOM_COUNTERS = "custom_counters"
private const val ROUTE_CUSTOM_COUNTER_DETAIL = "custom_counter_detail/{counterId}"
private const val ROUTE_MESSAGES_HISTORY = "messages_history"
private const val ROUTE_GROUPS = "groups"
private const val ROUTE_GROUP_DETAIL = "group_detail/{groupId}"

internal val NESTED_TAB_SUB_ROUTES = listOf(
    ROUTE_MESSAGES_HISTORY,
    ROUTE_CUSTOM_COUNTER_DETAIL,
    ROUTE_CUSTOM_COUNTERS,
    ROUTE_GROUP_DETAIL,
    ROUTE_GROUPS
)

internal data class TabNavigationSpec(
    val route: String,
    val saveStateOnLeave: Boolean = false,
    val restoreStateOnEnter: Boolean = false,
    val launchSingleTop: Boolean = true
)

internal fun resolveRootTabNavigationSpec(targetRoute: String): TabNavigationSpec {
    return TabNavigationSpec(
        route = targetRoute,
        saveStateOnLeave = false,
        restoreStateOnEnter = false,
        launchSingleTop = true
    )
}

internal enum class AppTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    COUNTER("counter", "Counter", Icons.Filled.RadioButtonChecked, Icons.Outlined.RadioButtonChecked),
    HISTORY("history", "History", Icons.Filled.History, Icons.Outlined.History),
    STATS("stats", "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun AppNavigation(
    auth: FirebaseAuth = Firebase.auth,
    pendingVerificationCode: String? = null,
    onConsumeVerificationCode: () -> Unit = {},
    pendingWidgetAction: String? = null,
    onConsumeWidgetAction: () -> Unit = {},
    pendingAnnouncementRequest: AnnouncementLaunchRequest? = null,
    onConsumeAnnouncementRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showSplash by rememberSaveable {
        mutableStateOf(!SplashSessionGuard.hasPlayedColdStartSplash)
    }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var authRefreshTick by remember { mutableIntStateOf(0) }
    var skippedLinkPromptForUid by remember { mutableStateOf<String?>(null) }
    val reduceMotion = rememberReduceMotion()

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val updated = firebaseAuth.currentUser
            if (updated == null) {
                skippedLinkPromptForUid = null
            }
            currentUser = updated
            authRefreshTick++
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = remember(currentUser, authRefreshTick) { auth.currentUser }
    val hasPasswordLinked = remember(user, authRefreshTick) {
        user?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true
    }
    val isGoogleSignedIn = remember(user, authRefreshTick) {
        user?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true
    }

    // If a verification App Link arrives while signed out or already verified, apply and consume cleanly
    LaunchedEffect(pendingVerificationCode, user?.uid, user?.isEmailVerified, isGoogleSignedIn) {
        val code = pendingVerificationCode ?: return@LaunchedEffect
        val needsVerifyScreen = user != null && !user.isEmailVerified && !isGoogleSignedIn
        if (!needsVerifyScreen) {
            try {
                auth.applyActionCode(code).await()
                auth.currentUser?.reload()?.await()
                currentUser = auth.currentUser
                authRefreshTick++
            } catch (_: Exception) {
            } finally {
                onConsumeVerificationCode()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Compose the underlying destination immediately so if the user is already signed in at cold start,
        // MainViewModel loads Room/Firestore data in parallel during the single 1.35s splash animation.
        if (user == null) {
            if (!showSplash) {
                AuthScreen(
                    onAuthChanged = {
                        currentUser = auth.currentUser
                        authRefreshTick++
                    }
                )
            }
        } else if (!user.isEmailVerified && !isGoogleSignedIn) {
            if (!showSplash) {
                VerifyEmailScreen(
                    email = user.email.orEmpty(),
                    pendingActionCode = pendingVerificationCode,
                    onConsumeActionCode = onConsumeVerificationCode,
                    onVerified = {
                        AuthFlowStore.markFreshLogin(user.uid)
                        currentUser = auth.currentUser
                        authRefreshTick++
                    },
                    onSignOut = {
                        FloatingCounterManager.stopGroupFloatingButtonIfActive(context)
                        WidgetAndFloatingBridge.setActiveUserId(context, null)
                        ReminderScheduler.cancelAll(context)
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = {
                                currentUser = null
                                authRefreshTick++
                            }
                        )
                    }
                )
            }
        } else {
            val uid = user.uid
            val fallbackName = AuthFlowStore.getSignupDisplayName(context, uid)
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Friend"
            val accountCreatedAtMillis = user.metadata?.creationTimestamp ?: 0L
            key(uid) {
                AuthenticatedAppRoot(
                    currentUserId = uid,
                    userEmail = user.email.orEmpty(),
                    fallbackDisplayName = fallbackName,
                    accountCreatedAtMillis = accountCreatedAtMillis,
                    hasPasswordLinked = hasPasswordLinked,
                    isGoogleSignedIn = isGoogleSignedIn,
                    skippedLinkPromptForUid = skippedLinkPromptForUid,
                    canPresentPopups = !showSplash,
                    pendingWidgetAction = pendingWidgetAction,
                    onConsumeWidgetAction = onConsumeWidgetAction,
                    pendingAnnouncementRequest = pendingAnnouncementRequest,
                    onConsumeAnnouncementRequest = onConsumeAnnouncementRequest,
                    onPasswordLinked = {
                        currentUser = auth.currentUser
                        authRefreshTick++
                    },
                    onSkipLinkPassword = {
                        skippedLinkPromptForUid = uid
                    },
                    onSignOut = {
                        FloatingCounterManager.stopGroupFloatingButtonIfActive(context)
                        WidgetAndFloatingBridge.setActiveUserId(context, null)
                        ReminderScheduler.cancelAll(context)
                        signOutUser(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = {
                                currentUser = null
                                authRefreshTick++
                            }
                        )
                    }
                )
            }
        }

        if (showSplash) {
            SplashScreen(
                onFinished = {
                    SplashSessionGuard.markPlayed()
                    showSplash = false
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedAppRoot(
    currentUserId: String,
    userEmail: String,
    fallbackDisplayName: String,
    accountCreatedAtMillis: Long,
    hasPasswordLinked: Boolean,
    isGoogleSignedIn: Boolean,
    skippedLinkPromptForUid: String?,
    canPresentPopups: Boolean,
    pendingWidgetAction: String?,
    onConsumeWidgetAction: () -> Unit,
    pendingAnnouncementRequest: AnnouncementLaunchRequest?,
    onConsumeAnnouncementRequest: () -> Unit,
    onPasswordLinked: () -> Unit,
    onSkipLinkPassword: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                MainViewModel(
                    repository = DuroodRepository(app),
                    currentUserId = currentUserId,
                    fallbackDisplayName = fallbackDisplayName,
                    accountCreatedAtMillis = accountCreatedAtMillis,
                    appContext = app,
                    isEmailSignInProvider = hasPasswordLinked,
                    isGoogleSignedInProvider = isGoogleSignedIn
                )
            }
        }
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.refreshCurrentDate()
                    viewModel.ensureRemindersScheduled()
                    viewModel.onResumeInstallCheck()
                    viewModel.refreshGlobalStats()
                    viewModel.refreshAnnouncements()
                    viewModel.refreshAnnouncementHistory()
                    if (viewModel.isGroupDetailOpen()) {
                        viewModel.refreshOpenGroupDetailNow()
                    }
                    FloatingCounterManager.onHostResumeCheckPermission(context)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.onAppPaused()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val reduceMotion = rememberReduceMotion()
    val destination = resolvePostLoginDestination(
        isLoading = uiState.isLoading,
        hasOfflineLoadError = uiState.initialLoadOfflineError,
        hasExistingSetupOrData = uiState.hasExistingDataOrSetup,
        isEmailSignIn = viewModel.isEmailSignInSession(),
        isGoogleSignedIn = isGoogleSignedIn,
        hasPasswordLinked = hasPasswordLinked,
        hasSkippedBackupPasswordPrompt = skippedLinkPromptForUid == currentUserId
    )
    var postSignupIntroActive by rememberSaveable(currentUserId) {
        mutableStateOf(AuthFlowStore.isNewEmailSignup(context, currentUserId))
    }
    var postSignupIntroCompleted by rememberSaveable(currentUserId) {
        mutableStateOf(false)
    }

    LaunchedEffect(destination, postSignupIntroCompleted) {
        if (!postSignupIntroCompleted &&
            !postSignupIntroActive &&
            (destination == PostLoginDestination.SETUP || destination == PostLoginDestination.LINK_BACKUP_PASSWORD)
        ) {
            postSignupIntroActive = true
        }
    }

    val holdForPostSignupIntro = postSignupIntroActive &&
        !postSignupIntroCompleted &&
        destination != PostLoginDestination.OFFLINE_LOAD_ERROR
    val showLoadingOrPostSignupOverlay =
        destination != PostLoginDestination.OFFLINE_LOAD_ERROR &&
            (destination == PostLoginDestination.LOGO_LOADING || holdForPostSignupIntro)

    Box(modifier = Modifier.fillMaxSize()) {
        if (destination != PostLoginDestination.LOGO_LOADING) {
            Crossfade(
                targetState = destination,
                animationSpec = tween(durationMillis = if (reduceMotion) 0 else 220, easing = FastOutSlowInEasing),
                label = "post_login_destination_transition"
            ) { targetDestination ->
                when (targetDestination) {
                    PostLoginDestination.LOGO_LOADING -> Unit

                    PostLoginDestination.OFFLINE_LOAD_ERROR -> {
                        AnimatedLogoLoadingScreen(
                            offlineErrorMessage = "Couldn't load your data — check internet",
                            onRetry = { viewModel.retryInitialLoad() },
                            onSwitchAccount = onSignOut
                        )
                    }

                    PostLoginDestination.LINK_BACKUP_PASSWORD -> {
                        LinkPasswordScreen(
                            email = userEmail,
                            onLinked = onPasswordLinked,
                            onSkip = onSkipLinkPassword,
                            onSignOut = onSignOut
                        )
                    }

                    PostLoginDestination.SETUP -> {
                        val initialSetupName = uiState.profile?.displayName?.takeIf { it.isNotBlank() }
                            ?: AuthFlowStore.getSignupDisplayName(context, currentUserId)
                            ?: fallbackDisplayName
                        SetupScreen(
                            initialName = initialSetupName,
                            initialDailyTarget = uiState.dailyTarget,
                            onCompleteSetup = { name, dailyTarget ->
                                viewModel.onCompleteSetup(name, dailyTarget)
                            }
                        )
                    }

                    PostLoginDestination.HOME -> {
                        MainTabScaffold(
                            viewModel = viewModel,
                            uiState = uiState,
                            userEmail = userEmail,
                            canPresentPopups = canPresentPopups && !holdForPostSignupIntro,
                            pendingWidgetAction = pendingWidgetAction,
                            onConsumeWidgetAction = onConsumeWidgetAction,
                            pendingAnnouncementRequest = pendingAnnouncementRequest,
                            onConsumeAnnouncementRequest = onConsumeAnnouncementRequest
                        )
                    }
                }
            }
        }

        if (showLoadingOrPostSignupOverlay) {
            AnimatedLogoLoadingScreen(
                playPostSignupIntro = holdForPostSignupIntro,
                onPostSignupIntroFinished = {
                    postSignupIntroCompleted = true
                    postSignupIntroActive = false
                }
            )
        }
    }
}

@Composable
internal fun MainTabScaffold(
    viewModel: MainViewModel,
    uiState: MainUiState,
    userEmail: String = "",
    canPresentPopups: Boolean = true,
    pendingWidgetAction: String? = null,
    onConsumeWidgetAction: () -> Unit = {},
    pendingAnnouncementRequest: AnnouncementLaunchRequest? = null,
    onConsumeAnnouncementRequest: () -> Unit = {}
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppTab.HOME.route
    val reduceMotion = rememberReduceMotion()
    val animDuration = if (reduceMotion) 0 else 200
    var openCustomAmountOnHomeLaunch by rememberSaveable { mutableStateOf(false) }

    val isCustomCountersRoute = currentRoute == ROUTE_CUSTOM_COUNTERS ||
        currentRoute.startsWith("custom_counter_detail")
    val isGroupsRoute = currentRoute == ROUTE_GROUPS ||
        currentRoute.startsWith("group_detail")
    val isMessagesHistoryRoute = currentRoute == ROUTE_MESSAGES_HISTORY

    fun navigateToTab(route: String) {
        val spec = resolveRootTabNavigationSpec(route)
        NESTED_TAB_SUB_ROUTES.forEach { nestedRoute ->
            navController.clearBackStack(nestedRoute)
        }
        AppTab.entries.forEach { tab ->
            navController.clearBackStack(tab.route)
        }

        if (route == AppTab.HOME.route) {
            val popped = navController.popBackStack(AppTab.HOME.route, inclusive = false)
            if (!popped && currentRoute != AppTab.HOME.route) {
                navController.navigate(spec.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                        saveState = spec.saveStateOnLeave
                    }
                    launchSingleTop = spec.launchSingleTop
                    restoreState = spec.restoreStateOnEnter
                }
            }
            return
        }

        if (currentRoute == spec.route) return

        navController.navigate(spec.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = false
                saveState = spec.saveStateOnLeave
            }
            launchSingleTop = spec.launchSingleTop
            restoreState = spec.restoreStateOnEnter
        }
    }

    LaunchedEffect(pendingWidgetAction) {
        when (pendingWidgetAction) {
            OneCroreWidgetProvider.ACTION_WIDGET_OPEN_COUNTER -> {
                openCustomAmountOnHomeLaunch = false
                navigateToTab(AppTab.COUNTER.route)
                onConsumeWidgetAction()
            }

            OneCroreWidgetProvider.ACTION_WIDGET_ADD_CUSTOM -> {
                openCustomAmountOnHomeLaunch = true
                navigateToTab(AppTab.HOME.route)
                onConsumeWidgetAction()
            }

            OneCroreWidgetProvider.ACTION_WIDGET_ENABLE_FLOATING -> {
                FloatingCounterManager.requestEnableFloatingButton(context)
                onConsumeWidgetAction()
            }
        }
    }

    LaunchedEffect(pendingAnnouncementRequest) {
        val request = pendingAnnouncementRequest ?: return@LaunchedEffect
        navigateToTab(AppTab.HOME.route)
        viewModel.openAnnouncementFromNotification(request)
        onConsumeAnnouncementRequest()
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == AppTab.HOME.route) {
            viewModel.refreshAnnouncements()
        }
    }

    val palette = AppThemeColors.palette

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.canvasBackground)
    ) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = palette.canvasBackground,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    androidx.compose.foundation.layout.Column {
                        HorizontalDivider(color = palette.navDivider, thickness = 1.dp)
                        NavigationBar(
                            containerColor = palette.navContainer,
                            tonalElevation = 0.dp
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = currentRoute == tab.route ||
                                    (tab == AppTab.HOME && isGroupsRoute) ||
                                    (tab == AppTab.COUNTER && isCustomCountersRoute) ||
                                    (tab == AppTab.HISTORY && isMessagesHistoryRoute)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = palette.navSelectedIcon,
                                        selectedTextColor = palette.navSelectedText,
                                        indicatorColor = palette.navIndicator,
                                        unselectedIconColor = palette.navUnselected,
                                        unselectedTextColor = palette.navUnselected
                                    ),
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = palette.navContainer,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        AppTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route ||
                                (tab == AppTab.HOME && isGroupsRoute) ||
                                (tab == AppTab.COUNTER && isCustomCountersRoute) ||
                                (tab == AppTab.HISTORY && isMessagesHistoryRoute)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = palette.navSelectedIcon,
                                    selectedTextColor = palette.navSelectedText,
                                    indicatorColor = palette.navIndicator,
                                    unselectedIconColor = palette.navUnselected,
                                    unselectedTextColor = palette.navUnselected
                                ),
                                modifier = Modifier.testTag("nav_${tab.route}")
                            )
                        }
                    }
                    VerticalDivider(color = palette.navDivider, thickness = 1.dp)
                }

                NavHost(
                    navController = navController,
                    startDestination = AppTab.HOME.route,
                    enterTransition = { fadeIn(tween(animDuration, easing = FastOutSlowInEasing)) },
                    exitTransition = { fadeOut(tween(animDuration, easing = FastOutSlowInEasing)) },
                    popEnterTransition = { fadeIn(tween(animDuration, easing = FastOutSlowInEasing)) },
                    popExitTransition = { fadeOut(tween(animDuration, easing = FastOutSlowInEasing)) },
                    modifier = Modifier.weight(1f)
                ) {
                    composable(AppTab.HOME.route) {
                        HomeScreen(
                            uiState = uiState,
                            canPresentPopups = canPresentPopups,
                            onOpenCounter = { navigateToTab(AppTab.COUNTER.route) },
                            onAddCustomAmount = viewModel::onAddCustomAmount,
                            initialShowCustomDialog = openCustomAmountOnHomeLaunch,
                            onConsumedInitialCustomDialog = { openCustomAmountOnHomeLaunch = false },
                            onOpenCustomCounter = { counterId ->
                                navController.navigate("custom_counter_detail/$counterId")
                            },
                            onOpenStats = { navigateToTab(AppTab.STATS.route) },
                            onOpenGroups = {
                                viewModel.refreshGroupsHubOnce()
                                navController.navigate(ROUTE_GROUPS)
                            },
                            onDismissMilestoneBadge = viewModel::dismissMilestoneBadgeCelebration,
                            onDismissGroupCelebration = viewModel::dismissGroupCelebrationPopup,
                            onCheckForUpdate = viewModel::checkForAppUpdate,
                            onRefreshGlobalStats = viewModel::refreshGlobalStats,
                            onRefreshAnnouncements = viewModel::refreshAnnouncements,
                            onAnnouncementShown = viewModel::onAnnouncementShown,
                            onDismissAnnouncement = viewModel::dismissAnnouncement,
                            onAnnouncementAction = viewModel::onAnnouncementActionClicked,
                            onOpenUpdateDialog = viewModel::openUpdateDialog,
                            onDismissUpdateDialog = viewModel::dismissUpdateDialogForToday,
                            onCancelUpdateDownload = viewModel::cancelUpdateDownload,
                            onUpdateNow = viewModel::onUpdateNowClicked
                        )
                    }
                    composable(AppTab.COUNTER.route) {
                        CounterScreen(
                            uiState = uiState,
                            onTapPlusOne = viewModel::onTapPlusOne,
                            onAddCustomAmount = viewModel::onAddCustomAmount,
                            onOpenCustomCounters = { navController.navigate(ROUTE_CUSTOM_COUNTERS) }
                        )
                    }
                    composable(AppTab.HISTORY.route) {
                        val historyDays by viewModel.historyAllDaysFlow.collectAsStateWithLifecycle()
                        HistoryScreen(
                            uiState = uiState,
                            historyDays = historyDays,
                            onEditDayCount = viewModel::onEditDayCount,
                            onOpenMessagesHistory = {
                                viewModel.refreshAnnouncementHistory()
                                navController.navigate(ROUTE_MESSAGES_HISTORY)
                            }
                        )
                    }
                    composable(AppTab.STATS.route) {
                        val historyDays by viewModel.historyAllDaysFlow.collectAsStateWithLifecycle()
                        StatsScreen(uiState = uiState.copy(allDays = historyDays))
                    }
                    composable(AppTab.SETTINGS.route) {
                        SettingsScreen(
                            uiState = uiState,
                            onUpdateDisplayName = viewModel::onUpdateDisplayName,
                            onUpdatePlan = viewModel::onUpdatePlan,
                            onUpdateReminders = viewModel::onUpdateReminders,
                            onUpdateThemeMode = viewModel::onUpdateThemeMode,
                            onSyncNow = { viewModel.triggerSync(silent = false) },
                            onClearStatusMessage = viewModel::clearStatusMessage,
                            onExportBackupJson = viewModel::exportBackupJson,
                            onRestoreBackupJson = viewModel::restoreBackupJson,
                            onDeleteAccount = viewModel::deleteAccount,
                            userEmail = userEmail,
                            onScheduleAnnouncement = viewModel::scheduleOrPublishAnnouncement,
            onVerifyPasswordForDelete = if (hasPasswordLinked) null else { _ -> reauthenticateWithGoogle(context, credentialManager) },
                        )
                    }
                    composable(ROUTE_MESSAGES_HISTORY) {
                        MessagesHistoryScreen(
                            entries = uiState.announcementHistory,
                            onBack = { navController.popBackStack() },
                            onActionClick = viewModel::onHistoryAnnouncementActionClicked
                        )
                    }
                    composable(ROUTE_CUSTOM_COUNTERS) {
                        CustomCountersScreen(
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onOpenCounter = { counterId ->
                                navController.navigate("custom_counter_detail/$counterId")
                            },
                            onCreateCounter = { name, target ->
                                viewModel.onCreateCustomCounter(name, target) { newId ->
                                    navController.navigate("custom_counter_detail/$newId")
                                }
                            },
                            onUpdateCounter = viewModel::onUpdateCustomCounter,
                            onDeleteCounter = viewModel::onDeleteCustomCounter
                        )
                    }
                    composable(
                        route = ROUTE_CUSTOM_COUNTER_DETAIL,
                        arguments = listOf(navArgument("counterId") { type = NavType.StringType })
                    ) { entry ->
                        val counterId = entry.arguments?.getString("counterId").orEmpty()
                        CustomCounterDetailScreen(
                            counterId = counterId,
                            uiState = uiState,
                            onBack = { navController.popBackStack() },
                            onTapPlusOne = { viewModel.onTapCustomCounterPlusOne(counterId) },
                            onAddCustomAmount = { amount ->
                                viewModel.onAddCustomCounterAmount(counterId, amount)
                            },
                            onUpdateCounter = { name, target, count ->
                                viewModel.onUpdateCustomCounter(counterId, name, target, count)
                            }
                        )
                    }
                    composable(ROUTE_GROUPS) {
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            viewModel.refreshGroupsHubOnce()
                        }
                        GroupsHubScreen(
                            groups = uiState.userGroups,
                            statusMessage = uiState.statusMessage,
                            isJoiningGroup = uiState.isJoiningGroup,
                            onClearStatusMessage = viewModel::clearStatusMessage,
                            onBack = { navController.popBackStack() },
                            onOpenGroup = { groupId ->
                                navController.navigate("group_detail/$groupId")
                            },
                            onCreateGroup = { name, type, zikrName, target ->
                                viewModel.onCreateGroup(name, type, zikrName, target) { created ->
                                    navController.navigate("group_detail/${created.id}")
                                }
                            },
                            onJoinByCode = { code ->
                                viewModel.onJoinGroupByCode(code) { joined ->
                                    navController.navigate("group_detail/${joined.id}")
                                }
                            }
                        )
                    }
                    composable(
                        route = ROUTE_GROUP_DETAIL,
                        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
                    ) { entry ->
                        val groupId = entry.arguments?.getString("groupId").orEmpty()
                        val selectedGroup = uiState.userGroups.firstOrNull { it.id == groupId }
                        val uid = uiState.profile?.userId
                            ?: com.google.firebase.Firebase.auth.currentUser?.uid
                            ?: ""
                        GroupDetailScreen(
                            group = selectedGroup,
                            currentUserId = uid,
                            currentDate = uiState.currentDate,
                            isRefreshing = uiState.isGroupRefreshing,
                            onScreenOpen = { viewModel.openGroupDetail(groupId) },
                            onScreenClose = { viewModel.closeGroupDetail(groupId) },
                            onRefresh = { viewModel.refreshOpenGroupDetailNow(groupId) },
                            onBack = {
                                if (navController.currentBackStackEntry?.destination?.route == ROUTE_GROUP_DETAIL) {
                                    navController.popBackStack()
                                }
                            },
                            onInviteWhatsApp = viewModel::onInviteGroupWhatsApp,
                            onRenameGroup = viewModel::onRenameGroup,
                            onLeaveGroup = { id ->
                                if (navController.currentBackStackEntry?.destination?.route == ROUTE_GROUP_DETAIL) {
                                    navController.popBackStack()
                                }
                                viewModel.onLeaveGroup(id)
                            },
                            onDeleteGroup = { id ->
                                if (navController.currentBackStackEntry?.destination?.route == ROUTE_GROUP_DETAIL) {
                                    navController.popBackStack()
                                }
                                viewModel.onDeleteGroup(id)
                            },
                            onAdjustMainGroupCount = viewModel::onAdjustMainGroupCount,
                            onIncrementCustomZikr = viewModel::onIncrementCustomZikrGroup
                        )
                    }
                }
            }
        }
    }
}
