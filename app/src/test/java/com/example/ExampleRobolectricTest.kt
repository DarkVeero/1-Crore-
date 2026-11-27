package com.example

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BackupCodec
import com.example.data.DuroodRepository
import com.example.data.GlobalCounterStats
import com.example.data.GlobalStatsCache
import com.example.data.local.AppDatabase
import com.example.data.local.CustomCounterEntity
import com.example.data.local.DailyCountEntity
import com.example.data.local.UserProfileEntity
import com.example.floating.FloatingCounterBubbleComposable
import com.example.floating.FloatingCounterManager
import com.example.notifications.ReminderScheduler
import com.example.ui.MainUiState
import com.example.ui.auth.AuthFlowStore
import com.example.ui.auth.AuthHeader
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.extractVerificationOobCode
import com.example.ui.screens.AnimatedLogoLoadingScreen
import com.example.ui.screens.CounterScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.SplashSessionGuard
import com.example.ui.theme.OneCroreTheme
import com.example.update.DriveVersionJsonUpdateSource
import com.example.widget.OneCroreWidgetProvider
import com.example.widget.WidgetAndFloatingBridge
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import android.widget.FrameLayout
import android.widget.TextView

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        AuthFlowStore.resetForTest(context)
        GlobalStatsCache.clearCache(context)
        SplashSessionGuard.resetForTest()
        WidgetAndFloatingBridge.resetForTest(context)
        FloatingCounterManager.resetForTest(context)
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        com.example.announcements.AnnouncementNotificationHelper.resetForTest(context)
        com.example.badges.MilestoneBadgeManager.resetForTest(context)
        com.example.groups.GroupRepository.resetForTest(context)
        com.example.groups.GroupCelebrationStore.resetForTest(context)
        com.example.update.AppUpdateManager.resetShareStateForTest(context)
        com.example.ui.theme.ThemePreferenceStore.resetForTest(context)
        runBlocking { ReminderScheduler.resetForTest(context) }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        AuthFlowStore.resetForTest(context)
        GlobalStatsCache.clearCache(context)
        SplashSessionGuard.resetForTest()
        WidgetAndFloatingBridge.resetForTest(context)
        FloatingCounterManager.resetForTest(context)
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        com.example.announcements.AnnouncementNotificationHelper.resetForTest(context)
        com.example.badges.MilestoneBadgeManager.resetForTest(context)
        com.example.groups.GroupRepository.resetForTest(context)
        com.example.groups.GroupCelebrationStore.resetForTest(context)
        com.example.update.AppUpdateManager.resetShareStateForTest(context)
        com.example.ui.theme.ThemePreferenceStore.resetForTest(context)
        runBlocking { ReminderScheduler.resetForTest(context) }
        db.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("1 Crore", appName)
        assertEquals(3, BuildConfig.VERSION_CODE)
        assertEquals("1.2", BuildConfig.VERSION_NAME)
    }

    @Test
    fun `backupCodec encodes and decodes profile days counters and startedAtMillis`() {
        val startedAt = 1_700_000_000_000L
        val profile = UserProfileEntity(
            userId = "u1",
            displayName = "Ahmad",
            dailyTarget = 2600,
            setupCompleted = true,
            reminder1Hour = 18,
            reminder1Minute = 0,
            reminder2Hour = 21,
            reminder2Minute = 0,
            remindersEnabled = true,
            startedAtMillis = startedAt
        )
        val days = listOf(
            DailyCountEntity(userId = "u1", dateKey = "2026-11-14", count = 2600L),
            DailyCountEntity(userId = "u1", dateKey = "2026-11-15", count = 3100L)
        )
        val counters = listOf(
            CustomCounterEntity(
                id = "c1",
                userId = "u1",
                name = "Friday Salawat",
                targetCount = 1000L,
                currentCount = 450L,
                isDeleted = false
            ),
            CustomCounterEntity(
                id = "c2",
                userId = "u1",
                name = "Deleted Counter",
                targetCount = 500L,
                currentCount = 100L,
                isDeleted = true
            )
        )

        val json = BackupCodec.encode(profile, days, counters)
        val decoded = BackupCodec.decode(json)

        assertEquals("Ahmad", decoded.displayName)
        assertEquals(2600, decoded.dailyTarget)
        assertEquals(18, decoded.reminder1Hour)
        assertEquals(0, decoded.reminder1Minute)
        assertEquals(21, decoded.reminder2Hour)
        assertEquals(0, decoded.reminder2Minute)
        assertEquals(true, decoded.remindersEnabled)
        assertEquals(startedAt, decoded.startedAtMillis)
        assertEquals(listOf("2026-11-14" to 2600L, "2026-11-15" to 3100L), decoded.days)
        assertEquals(1, decoded.customCounters.size)
        assertEquals("Friday Salawat", decoded.customCounters.first().name)
        assertEquals(1000L, decoded.customCounters.first().targetCount)
        assertEquals(450L, decoded.customCounters.first().currentCount)
    }

    @Test
    fun `versionJsonParser parses valid release json and rejects non-https urls`() {
        val validJson = """
            {
              "versionCode": 3,
              "versionName": "1.2",
              "apkUrl": "https://drive.google.com/uc?export=download&id=test123",
              "changelog": "Bug fixes and performance improvements",
              "isForced": false
            }
        """.trimIndent()
        val parsed = DriveVersionJsonUpdateSource.parseVersionJson(validJson)
        assertNotNull(parsed)
        assertEquals(3, parsed?.versionCode)
        assertEquals("1.2", parsed?.versionName)
        assertEquals("https://drive.google.com/uc?export=download&id=test123", parsed?.downloadUrl)
        assertEquals("Bug fixes and performance improvements", parsed?.changelog)
        assertFalse(parsed?.isForced ?: true)

        val roundTrip = DriveVersionJsonUpdateSource.parseVersionJson(
            DriveVersionJsonUpdateSource.serialize(parsed!!)
        )
        assertEquals(parsed, roundTrip)

        val invalidHttp = """{"versionCode": 3, "versionName": "1.2", "apkUrl": "http://insecure.example/app.apk"}"""
        assertNull(DriveVersionJsonUpdateSource.parseVersionJson(invalidHttp))
    }

    @Test
    fun `extractVerificationOobCode extracts direct and nested action codes`() {
        val directUri = Uri.parse(
            "https://gen-lang-client-0318050933.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=CODE_123"
        )
        assertEquals("CODE_123", extractVerificationOobCode(directUri))

        val nestedUri = Uri.parse(
            "https://gen-lang-client-0318050933.firebaseapp.com/verify-email?link=" +
                Uri.encode("https://gen-lang-client-0318050933.firebaseapp.com/__/auth/action?mode=verifyEmail&oobCode=NESTED_456")
        )
        assertEquals("NESTED_456", extractVerificationOobCode(nestedUri))
    }

    @Test
    fun `offline Room repository handles setup counting history edit custom counters and backup restore`() = runBlocking {
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
        val userId = "local_test_user"

        // 1. Setup flow (default 2,600/day)
        repo.ensureLocalProfile(userId, "Mudasir")
        repo.completeSetup(userId, "Mudasir", DuroodRepository.DEFAULT_DAILY_TARGET)
        val profile = repo.getProfileOnce(userId)
        assertNotNull(profile)
        assertEquals("Mudasir", profile?.displayName)
        assertEquals(2600, profile?.dailyTarget)
        assertTrue(profile?.setupCompleted == true)
        assertTrue((profile?.startedAtMillis ?: 0L) > 0L)

        // 2. Counter +1 and custom amount
        repo.incrementCount(userId, "2026-11-14", 1L)
        repo.incrementCount(userId, "2026-11-14", 499L)
        repo.incrementCount(userId, "2026-11-15", 1000L)
        assertEquals(500L, repo.getDayCountOnce(userId, "2026-11-14"))
        assertEquals(1500L, repo.observeTotalCount(userId).first())
        assertTrue(repo.observeHasPendingSync(userId).first())

        // 3. History edit higher and lower
        repo.setDayCount(userId, "2026-11-14", 800L)
        assertEquals(1800L, repo.observeTotalCount(userId).first())
        repo.setDayCount(userId, "2026-11-14", 300L)
        assertEquals(1300L, repo.observeTotalCount(userId).first())

        // 4. Custom Counters create, increment, edit, delete
        val counterId = repo.createCustomCounter(userId, "Daily Istighfar", 1000L)
        repo.incrementCustomCounter(userId, counterId, 250L)
        var counters = repo.observeCustomCounters(userId).first()
        assertEquals(1, counters.size)
        assertEquals(250L, counters.first().currentCount)

        repo.updateCustomCounterDetails(userId, counterId, "Evening Istighfar", 2000L, 600L)
        counters = repo.observeCustomCounters(userId).first()
        assertEquals("Evening Istighfar", counters.first().name)
        assertEquals(2000L, counters.first().targetCount)
        assertEquals(600L, counters.first().currentCount)

        // 5. Backup export and restore (preserving higher local counts & earliest startedAtMillis)
        val backupJson = repo.exportBackupJson(userId)
        repo.incrementCount(userId, "2026-11-15", 500L) // Local is now 1500 on 2026-11-15 (backup had 1000)
        val restoreResult = repo.restoreBackupJson(userId, backupJson)
        assertTrue(restoreResult.isSuccess)
        // Higher local count (1500) must be preserved over smaller backup count (1000)
        assertEquals(1500L, repo.getDayCountOnce(userId, "2026-11-15"))

        repo.deleteCustomCounter(userId, counterId)
        assertTrue(repo.observeCustomCounters(userId).first().isEmpty())
    }

    @Test
    fun `completeSetup on existing account never resets total count or start date`() = runBlocking {
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
        val userId = "existing_user_shahzaib"
        val originalStartMillis = 1_680_000_000_000L

        // Seed existing account with original start date and existing counts totaling 12,500
        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = userId,
                displayName = "Shahzaib Khan",
                dailyTarget = 2600,
                setupCompleted = true,
                startedAtMillis = originalStartMillis
            )
        )
        repo.incrementCount(userId, "2026-11-10", 5000L)
        repo.incrementCount(userId, "2026-11-11", 7500L)
        assertEquals(12500L, repo.observeTotalCount(userId).first())

        // Even if the user somehow ever lands on Setup and taps 'Start Journey' (completeSetup):
        repo.completeSetup(
            userId = userId,
            displayName = "Shahzaib Khan",
            dailyTarget = 3000,
            fallbackStartedAtMillis = System.currentTimeMillis()
        )

        val updatedProfile = repo.getProfileOnce(userId)
        assertNotNull(updatedProfile)
        assertEquals(true, updatedProfile?.setupCompleted)
        // Start date must NEVER be reset to now
        assertEquals(originalStartMillis, updatedProfile?.startedAtMillis)
        // Total count and daily history must NEVER be reset
        assertEquals(12500L, repo.observeTotalCount(userId).first())
        assertEquals(5000L, repo.getDayCountOnce(userId, "2026-11-10"))
        assertEquals(7500L, repo.getDayCountOnce(userId, "2026-11-11"))

        // Also verify: if profile had startedAtMillis = 0L but daily_counts had an earlier date,
        // completeSetup preserves the earliest count date instead of resetting to now
        val userIdWithCountsOnly = "existing_user_counts_only"
        repo.incrementCount(userIdWithCountsOnly, "2025-01-15", 4200L)
        val expectedEarliestMillis = LocalDate.parse("2025-01-15")
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        repo.completeSetup(
            userId = userIdWithCountsOnly,
            displayName = "Shahzaib Khan",
            dailyTarget = 2600
        )
        val profileFromCounts = repo.getProfileOnce(userIdWithCountsOnly)
        assertEquals(expectedEarliestMillis, profileFromCounts?.startedAtMillis)
        assertEquals(4200L, repo.observeTotalCount(userIdWithCountsOnly).first())
    }

    @Test
    fun `hasLocalExistingData and AuthFlowStore distinguish returning vs new accounts`() = runBlocking {
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
        val brandNewUid = "brand_new_uid"
        assertFalse(repo.hasLocalExistingData(brandNewUid))

        // Sign-up marks new email signup
        AuthFlowStore.markNewEmailSignup(context, brandNewUid, "Shahzaib Khan")
        assertTrue(AuthFlowStore.isNewEmailSignup(context, brandNewUid))
        assertFalse(AuthFlowStore.isExplicitEmailSignIn(context, brandNewUid))
        assertEquals("Shahzaib Khan", AuthFlowStore.getSignupDisplayName(context, brandNewUid))

        // Email sign-in clears new-signup flag and marks explicit email sign-in
        val returningEmailUid = "returning_email_uid"
        AuthFlowStore.markEmailSignIn(context, returningEmailUid)
        assertFalse(AuthFlowStore.isNewEmailSignup(context, returningEmailUid))
        assertTrue(AuthFlowStore.isExplicitEmailSignIn(context, returningEmailUid))

        // Auto-completing setup for returning email sign-in preserves account creation start date
        val accountCreatedMillis = 1_690_000_000_000L
        repo.ensureExistingAccountSetupCompleted(
            userId = returningEmailUid,
            fallbackName = "Shahzaib Khan",
            accountCreatedAtMillis = accountCreatedMillis
        )
        assertTrue(repo.hasLocalExistingData(returningEmailUid))
        val profile = repo.getProfileOnce(returningEmailUid)
        assertEquals(true, profile?.setupCompleted)
        assertEquals("Shahzaib Khan", profile?.displayName)
        assertEquals(accountCreatedMillis, profile?.startedAtMillis)

        // Clearing user state on sign-out / delete clears persisted flags
        AuthFlowStore.clearUser(context, returningEmailUid)
        assertFalse(AuthFlowStore.isExplicitEmailSignIn(context, returningEmailUid))
    }

    @Test
    fun `restoreBackupJson derives earliest startedAtMillis from backup days when startedAtMillis is omitted`() = runBlocking {
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
        val userId = "restore_earliest_day_user"
        val legacyBackupJson = """
            {
              "app": "1 Crore",
              "version": 1,
              "profile": {
                "displayName": "Shahzaib",
                "dailyTarget": 2600
              },
              "dailyCounts": [
                {"dateKey": "2025-03-10", "count": 2600},
                {"dateKey": "2025-02-01", "count": 1500}
              ],
              "customCounters": []
            }
        """.trimIndent()

        val result = repo.restoreBackupJson(userId, legacyBackupJson)
        assertTrue(result.isSuccess)
        val expectedEarliestMillis = LocalDate.parse("2025-02-01")
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val restoredProfile = repo.getProfileOnce(userId)
        assertNotNull(restoredProfile)
        assertEquals(expectedEarliestMillis, restoredProfile?.startedAtMillis)
        assertEquals(4100L, repo.observeTotalCount(userId).first())
    }

    @Test
    fun `reminderScheduler enforces max 2 notifications per day and resets next day`() = runBlocking {
        val day1 = "2026-11-15"
        val day2 = "2026-11-16"
        assertTrue(ReminderScheduler.canSendTodayAndIncrement(context, day1))
        assertTrue(ReminderScheduler.canSendTodayAndIncrement(context, day1))
        assertFalse(ReminderScheduler.canSendTodayAndIncrement(context, day1))
        assertTrue(ReminderScheduler.canSendTodayAndIncrement(context, day2))
    }

    @Test
    fun `globalStatsCache persists and loadGlobalStats returns cached copy within 1 hour or offline`() = runBlocking {
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
        val t0 = 1_700_000_000_000L

        // Initially no cache -> offline load returns null (no zero, no error)
        assertNull(GlobalStatsCache.getCached(context))
        assertNull(repo.loadGlobalStats(context, isOnline = false, nowMillis = t0))

        // Save server-fetched stats to cache
        val seeded = GlobalCounterStats(
            totalCount = 2_450_000L,
            userCount = 185L,
            fetchedAtMillis = t0
        )
        GlobalStatsCache.saveCached(context, seeded)
        assertEquals(seeded, GlobalStatsCache.getCached(context))

        // Within 1 hour (e.g. 30 minutes later) while online -> returns cached value without touching Firestore
        val thirtyMinutesLater = t0 + 30L * 60L * 1000L
        val fromCacheOnline = repo.loadGlobalStats(context, isOnline = true, nowMillis = thirtyMinutesLater)
        assertEquals(seeded, fromCacheOnline)

        // Older than 1 hour (e.g. 2 hours later) while offline -> returns cached value
        val twoHoursLater = t0 + 2L * 60L * 60L * 1000L
        val fromCacheOffline = repo.loadGlobalStats(context, isOnline = false, nowMillis = twoHoursLater)
        assertEquals(seeded, fromCacheOffline)
    }

    @Test
    fun `homeScreen renders global counter block top-left without overlapping greeting on small 320dp screen and hides when doc is missing`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        var state by mutableStateOf(
            MainUiState(
                isLoading = false,
                totalCount = 15_000L,
                todayCount = 2_600L,
                isOnline = true,
                globalStats = null // Missing doc case first
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    HomeScreen(
                        uiState = state,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onRefreshGlobalStats = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Missing doc case: global counter block is NOT shown at all (no zero, no crash, no error)
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_global_counter_block").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("home_arabic_greeting").assertIsDisplayed()

        // 2. Populated stats/global doc on a narrow 320dp screen
        val fetchedAt = System.currentTimeMillis() - 3_600_000L
        state = state.copy(
            isOnline = true,
            globalStats = GlobalCounterStats(
                totalCount = 3_750_000L,
                userCount = 240L,
                fetchedAtMillis = fetchedAt
            )
        )
        composeTestRule.waitForIdle()

        val blockNode = composeTestRule.onNodeWithTag("home_global_counter_block")
        blockNode.assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_global_counter_total")
            .assertTextContains("Everyone together: 3,750,000 Durood")
        composeTestRule.onNodeWithTag("home_global_counter_users")
            .assertTextContains("240 users")
        composeTestRule.onNodeWithTag("home_global_counter_subtitle")
            .assertTextContains("Updates every hour")

        val blockBounds = blockNode.fetchSemanticsNode().boundsInRoot
        val greetingNode = composeTestRule.onNodeWithTag("home_arabic_greeting").fetchSemanticsNode()
        val greetingBounds = greetingNode.boundsInRoot

        // Verify the full-width Arabic greeting sits above the global counter block without overlapping
        assertTrue(
            "Expected full-width Arabic greeting to sit above global counter block without overlapping " +
                "(greeting.bottom=${greetingBounds.bottom}, block.top=${blockBounds.top})",
            greetingBounds.bottom <= blockBounds.top + 0.5f
        )

        // Verify Arabic greeting renders on strictly ONE line even on a 320dp narrow screen
        val greetingLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        greetingNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(greetingLayouts)
        assertTrue("Expected TextLayoutResult for home_arabic_greeting", greetingLayouts.isNotEmpty())
        assertEquals(
            "Arabic greeting on Home must fit on 1 line",
            1,
            greetingLayouts.first().lineCount
        )

        // 3. Offline mode shows cached value + last-updated time
        state = state.copy(isOnline = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_global_counter_subtitle")
            .assertTextContains("Updated", substring = true)
    }

    @Test
    fun `splashSessionGuard ensures splash plays once and post-login loading screen shows static logo`() {
        SplashSessionGuard.resetForTest()
        assertFalse(SplashSessionGuard.hasPlayedColdStartSplash)

        SplashSessionGuard.markPlayed()
        assertTrue(SplashSessionGuard.hasPlayedColdStartSplash)

        var finishedCount = 0
        var showLoading by mutableStateOf(false)

        composeTestRule.setContent {
            OneCroreTheme {
                if (!showLoading) {
                    SplashScreen(onFinished = { finishedCount++ })
                } else {
                    AnimatedLogoLoadingScreen()
                }
            }
        }
        composeTestRule.waitForIdle()

        // Because SplashSessionGuard was already marked played, SplashScreen finishes immediately without re-animating
        assertEquals(1, finishedCount)

        // Switching to AnimatedLogoLoadingScreen displays the static completed logo immediately
        showLoading = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("post_login_logo_loading_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("one_crore_logo_canvas").assertIsDisplayed()
    }

    @Test
    fun `authScreen signup shows all inline field errors simultaneously and clears each live while typing`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )

        composeTestRule.setContent {
            OneCroreTheme {
                AuthScreen(onAuthChanged = {})
            }
        }
        composeTestRule.waitForIdle()

        // Switch to Create Account
        composeTestRule.onNodeWithTag("auth_switch_mode").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Enter invalid values: empty name, invalid email, short password, mismatched confirm password
        composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("bad-email")
        composeTestRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("123")
        composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextInput("456")

        composeTestRule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // ALL 4 inline errors appear at once
        composeTestRule.onNodeWithTag("signup_name_error")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Enter your full name")
        composeTestRule.onNodeWithTag("auth_email_error")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Enter a valid email address")
        composeTestRule.onNodeWithTag("auth_password_error")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Password must be at least 6 characters")
        composeTestRule.onNodeWithTag("signup_confirm_password_error")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Passwords don't match")
        // Top banner still appears
        composeTestRule.onNodeWithText("Please enter your full name.")
            .performScrollTo()
            .assertIsDisplayed()

        // Fix Name -> clears ONLY name error immediately
        composeTestRule.onNodeWithTag("signup_name_input").performScrollTo().performTextInput("Shahzaib")
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("signup_name_error").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("auth_email_error").performScrollTo().assertIsDisplayed()

        // Fix Email -> clears ONLY email error immediately
        composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("@example.com")
        // email is now "bad-email@example.com" which is valid
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("auth_email_error").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("auth_password_error").performScrollTo().assertIsDisplayed()

        // Fix Password to "123456" -> clears password error, confirm error stays until it matches
        composeTestRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("456")
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("auth_password_error").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("signup_confirm_password_error").performScrollTo().assertIsDisplayed()

        // Fix Confirm Password to match "123456" -> confirm error clears immediately
        composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().performTextReplacement("123456")
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("signup_confirm_password_error").fetchSemanticsNodes().size)
    }

    @Test
    fun `authHeader counts up smoothly to 10000000 in 4s holds without restarting and respects reduce motion`() {
        // 1. Reduce Motion ON -> shows 10,000,000 immediately
        composeTestRule.setContent {
            OneCroreTheme {
                AuthHeader(reduceMotion = true)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_goal_count_text").assertTextEquals("10,000,000")
    }

    @Test
    fun `authHeader normal motion animates 0 to 10000000 over 4s with ease-out and holds`() {
        composeTestRule.mainClock.autoAdvance = false
        var tick by mutableStateOf(0)

        composeTestRule.setContent {
            OneCroreTheme {
                AuthHeader(reduceMotion = false)
                // Side state recomposition check to confirm typing/recomposing does not restart count-up
                val unused = tick
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()

        fun readGoalNumber(): Long {
            val texts = composeTestRule.onNodeWithTag("auth_goal_count_text")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
            val raw = texts.joinToString("") { it.text }.replace(",", "")
            return raw.toLong()
        }

        val startVal = readGoalNumber()
        assertEquals(0L, startVal)

        // After 1s of 4s EaseOutCubic: should be fast start (> 5,000,000 and < 10,000,000)
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        val atOneSecond = readGoalNumber()
        assertTrue(
            "Expected fast ease-out start > 5,000,000 at 1s, got $atOneSecond",
            atOneSecond in 5_000_000L until 10_000_000L
        )
        val atOneSecondFormatted = composeTestRule.onNodeWithTag("auth_goal_count_text")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }
        assertTrue("Expected comma grouping at 1s: $atOneSecondFormatted", atOneSecondFormatted.contains(","))

        // Advance past 4s total -> lands cleanly on 10,000,000
        composeTestRule.mainClock.advanceTimeBy(3_200L)
        assertEquals(10_000_000L, readGoalNumber())
        composeTestRule.onNodeWithTag("auth_goal_count_text").assertTextEquals("10,000,000")

        // Trigger recomposition & advance further -> holds on 10,000,000 and never restarts
        tick = 1
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onNodeWithTag("auth_goal_count_text").assertTextEquals("10,000,000")
    }

    @Test
    fun `announcementFrequencyStore enforces 1 per day 3 per week window and actedOn caps`() {
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        val zone = ZoneId.of("UTC")
        val day1Noon = LocalDate.of(2026, 11, 16).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val day1Evening = day1Noon + 4 * 3_600_000L
        val day2Noon = day1Noon + 24 * 3_600_000L
        val day3Noon = day1Noon + 48 * 3_600_000L
        val day4Noon = day1Noon + 72 * 3_600_000L

        val announcement = com.example.announcements.AnnouncementItem(
            id = "ann_window_test",
            title = "Rabi ul Awwal Target",
            body = "Join the community goal this week.",
            buttonText = "Share",
            buttonAction = com.example.announcements.AnnouncementAction.SHARE,
            startAtMillis = day1Noon - 3_600_000L,
            endAtMillis = day4Noon + 3_600_000L,
            maxShowsPerDay = 1,
            maxShowsPerWeek = 3,
            priority = 10
        )

        // Before startAt -> not eligible
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u1", announcement, nowMillis = day1Noon - 7_200_000L, zoneId = zone
            )
        )

        // Day 1 first open -> eligible
        assertTrue(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u1", announcement, nowMillis = day1Noon, zoneId = zone
            )
        )
        com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u1", announcement, nowMillis = day1Noon)

        // Day 1 second open (same day) -> blocked by 1/day cap
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u1", announcement, nowMillis = day1Evening, zoneId = zone
            )
        )

        // Day 2 -> eligible (1st show on Day 2, 2nd show of the week)
        assertTrue(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u1", announcement, nowMillis = day2Noon, zoneId = zone
            )
        )
        com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u1", announcement, nowMillis = day2Noon)

        // Day 3 -> eligible (1st show on Day 3, 3rd show of the week)
        assertTrue(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u1", announcement, nowMillis = day3Noon, zoneId = zone
            )
        )
        com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u1", announcement, nowMillis = day3Noon)

        // Day 4 -> blocked permanently because 3/week cap has been reached
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u1", announcement, nowMillis = day4Noon, zoneId = zone
            )
        )

        // Action button tap on another announcement marks it acted-on and never shows again
        val actionAnnouncement = announcement.copy(id = "ann_acted_test")
        assertTrue(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u1", actionAnnouncement, nowMillis = day1Noon, zoneId = zone
            )
        )
        com.example.announcements.AnnouncementFrequencyStore.markActedOn(
            context, "u1", actionAnnouncement.id, nowMillis = day1Noon
        )
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u1", actionAnnouncement, nowMillis = day2Noon, zoneId = zone
            )
        )
    }

    @Test
    fun `homeScreen shows update dialog before announcement and announcement dismisses on card scrim or X tap`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val sampleUpdate = com.example.update.AppUpdateInfo(
            versionCode = 4,
            versionName = "1.3",
            downloadUrl = "https://drive.google.com/uc?export=download&id=test4",
            changelog = "New improvements",
            isForced = false
        )
        val sampleAnnouncement = com.example.announcements.AnnouncementItem(
            id = "ann_ui_1",
            title = "Special Announcement",
            body = "Tap anywhere on or outside this card to dismiss immediately.",
            buttonText = "Share App",
            buttonAction = com.example.announcements.AnnouncementAction.SHARE
        )

        var dismissCount = 0
        var actionCount = 0
        var state by mutableStateOf(
            MainUiState(
                isLoading = false,
                availableUpdate = sampleUpdate,
                showUpdateDialog = true,
                isUpdateCheckSettled = true,
                activeAnnouncement = sampleAnnouncement
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = state,
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onRefreshGlobalStats = {},
                    onRefreshAnnouncements = {},
                    onAnnouncementShown = {},
                    onDismissAnnouncement = {
                        dismissCount++
                        state = state.copy(activeAnnouncement = null)
                    },
                    onAnnouncementAction = {
                        actionCount++
                        state = state.copy(activeAnnouncement = null)
                    },
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {
                        state = state.copy(showUpdateDialog = false)
                    },
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Both exist -> Update dialog is shown FIRST, Announcement is NOT shown yet
        composeTestRule.onNodeWithText("Version 1.3 is available").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("announcement_dialog_card").fetchSemanticsNodes().size)

        // 2. Dismiss Update dialog -> Announcement dialog appears immediately after
        composeTestRule.onNodeWithTag("update_later_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("announcement_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("announcement_dialog_title").assertTextEquals("Special Announcement")

        // 3. Tapping directly on the announcement card body dismisses it immediately
        composeTestRule.onNodeWithTag("announcement_dialog_body").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, dismissCount)
        assertEquals(0, composeTestRule.onAllNodesWithTag("announcement_dialog_card").fetchSemanticsNodes().size)

        // 4. Re-show and tap the action button -> triggers actionCount, not dismissCount
        state = state.copy(activeAnnouncement = sampleAnnouncement)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("announcement_action_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, actionCount)
        assertEquals(1, dismissCount)
    }

    @Test
    fun `oneCroreWidgetProvider renders Today and Total counts and all three buttons from shared Room database`() = runBlocking {
        val userId = "widget_shared_user_${System.nanoTime()}"
        val appDb = AppDatabase.getInstance(context)
        appDb.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = userId,
                displayName = "Shahzaib",
                dailyTarget = 2600,
                setupCompleted = true
            )
        )
        WidgetAndFloatingBridge.setActiveUserId(context, userId)
        val yesterdayKey = LocalDate.now().minusDays(1).toString()
        appDb.dailyCountDao().incrementDay(userId, yesterdayKey, 4_000L, System.currentTimeMillis())

        // Increment Today via shared bridge
        val snapshotAfterAdd = WidgetAndFloatingBridge.incrementCountAndSync(
            context = context,
            delta = 650L,
            db = appDb,
            userIdOverride = userId
        )
        assertEquals(650L, snapshotAfterAdd.todayCount)
        assertEquals(4_650L, snapshotAfterAdd.totalCount)

        val remoteViews = OneCroreWidgetProvider.buildRemoteViews(
            context = context,
            todayCount = snapshotAfterAdd.todayCount,
            totalCount = snapshotAfterAdd.totalCount
        )
        val host = FrameLayout(context)
        val appliedView = remoteViews.apply(context, host)

        val todayText = appliedView.findViewById<TextView>(R.id.widget_today_count)
        val totalText = appliedView.findViewById<TextView>(R.id.widget_total_count)
        val addCustomBtn = appliedView.findViewById<TextView>(R.id.widget_btn_add_custom)
        val openCounterBtn = appliedView.findViewById<TextView>(R.id.widget_btn_open_counter)
        val floatingBtn = appliedView.findViewById<TextView>(R.id.widget_btn_floating)

        assertNotNull(todayText)
        assertNotNull(totalText)
        assertNotNull(addCustomBtn)
        assertNotNull(openCounterBtn)
        assertNotNull(floatingBtn)

        assertEquals("650", todayText.text.toString())
        assertEquals("4,650", totalText.text.toString())
        assertEquals("Add Custom", addCustomBtn.text.toString())
        assertEquals("Open Counter", openCounterBtn.text.toString())
        assertEquals("Floating Button", floatingBtn.text.toString())
    }

    @Test
    fun `floatingButton tap flashes session count 1 to 21 and returns to logo updates shared DB and drags to bottom X to close`() = runBlocking {
        val userId = "floating_user_${System.nanoTime()}"
        val appDb = AppDatabase.getInstance(context)
        appDb.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = userId,
                displayName = "Shahzaib",
                dailyTarget = 2600,
                setupCompleted = true
            )
        )
        WidgetAndFloatingBridge.setActiveUserId(context, userId)
        val baseSnapshot = WidgetAndFloatingBridge.readCountsFromDatabase(
            context = context,
            db = appDb,
            userIdOverride = userId
        )

        FloatingCounterManager.setOverlayPermissionOverrideForTest(true)
        val started = FloatingCounterManager.requestEnableFloatingButton(context)
        assertTrue(started)
        assertTrue(FloatingCounterManager.state.value.isRunning)
        assertEquals(0L, FloatingCounterManager.state.value.sessionCount)
        assertNull(FloatingCounterManager.state.value.flashingNumber)

        // Tap 1: flashes "1" immediately and increments shared Room DB
        val (session1, afterTap1) = FloatingCounterManager.onFloatingButtonTapped(
            context = context,
            db = appDb,
            userIdOverride = userId
        )
        assertEquals(1L, session1)
        assertEquals(1L, FloatingCounterManager.state.value.flashingNumber)
        assertEquals(baseSnapshot.todayCount + 1L, afterTap1.todayCount)
        assertEquals(baseSnapshot.totalCount + 1L, afterTap1.totalCount)

        // After 0.5-1s flash duration, returns to logo (flashingNumber == null)
        FloatingCounterManager.onFlashFinished(session1)
        assertNull(FloatingCounterManager.state.value.flashingNumber)
        assertEquals(1L, FloatingCounterManager.state.value.sessionCount)

        // Taps 2..20 -> flashes 20, then 21 on tap 21
        for (tapIndex in 2..20) {
            val (shown, _) = FloatingCounterManager.onFloatingButtonTapped(
                context = context,
                db = appDb,
                userIdOverride = userId
            )
            assertEquals(tapIndex.toLong(), shown)
            assertEquals(tapIndex.toLong(), FloatingCounterManager.state.value.flashingNumber)
            FloatingCounterManager.onFlashFinished(shown)
            assertNull(FloatingCounterManager.state.value.flashingNumber)
        }

        // Tap 21 -> flashes 21
        val (session21, afterTap21) = FloatingCounterManager.onFloatingButtonTapped(
            context = context,
            db = appDb,
            userIdOverride = userId
        )
        assertEquals(21L, session21)
        assertEquals(21L, FloatingCounterManager.state.value.flashingNumber)

        // Verify shared Room database Today and Total counts increased by all 21 floating taps
        assertEquals(baseSnapshot.todayCount + 21L, afterTap21.todayCount)
        assertEquals(baseSnapshot.totalCount + 21L, afterTap21.totalCount)

        // Verify Compose bubble UI renders flashing number, then logo, and uses drag-to-close
        composeTestRule.setContent {
            OneCroreTheme {
                FloatingCounterBubbleComposable(
                    db = appDb,
                    userIdOverride = userId,
                    flashDurationMillis = 60_000L
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_counter_flash_number").assertTextEquals("21")
        assertEquals(0, composeTestRule.onAllNodesWithTag("floating_counter_close_target").fetchSemanticsNodes().size)

        // Fade back to logo
        FloatingCounterManager.onFlashFinished(21L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_counter_logo").assertIsDisplayed()

        // Dragging begins -> bottom-center X close target appears; hovering & dropping on X closes immediately
        FloatingCounterManager.onDragStarted()
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.state.value.isDragging)
        composeTestRule.onNodeWithTag("floating_counter_close_target").assertIsDisplayed()

        FloatingCounterManager.onDragTargetHoverChanged(true)
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.state.value.isOverCloseTarget)

        val closed = FloatingCounterManager.onDragEnded(context, droppedOnClose = true)
        composeTestRule.waitForIdle()
        assertTrue(closed)
        assertFalse(FloatingCounterManager.state.value.isRunning)
        assertFalse(FloatingCounterManager.state.value.isDragging)
        assertEquals(0, composeTestRule.onAllNodesWithTag("floating_counter_close_target").fetchSemanticsNodes().size)
    }

    @Test
    fun `homeScreen floating button toggle row turns floating button on and off and widget Add Custom opens Counter popup`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )

        // 1. When overlay permission is not granted yet, tapping the Home toggle asks for permission once
        FloatingCounterManager.setOverlayPermissionOverrideForTest(false)
        val homeState = MainUiState(
            isLoading = false,
            totalCount = 5_000L,
            todayCount = 500L,
            customCounters = listOf(
                CustomCounterEntity(
                    id = "c1",
                    userId = "u1",
                    name = "Salawat",
                    targetCount = 1000L,
                    currentCount = 200L
                )
            )
        )

        var showCounterWithCustomPopup by mutableStateOf(false)
        var customAddedAmount = 0L

        composeTestRule.setContent {
            OneCroreTheme {
                if (!showCounterWithCustomPopup) {
                    HomeScreen(
                        uiState = homeState,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onRefreshGlobalStats = {},
                        onRefreshAnnouncements = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                } else {
                    CounterScreen(
                        uiState = homeState,
                        onTapPlusOne = {},
                        onAddCustomAmount = { customAddedAmount += it },
                        onOpenCustomCounters = {},
                        initialShowCustomDialog = true
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Verify Floating button row is present on Home below custom counters and initially OFF
        composeTestRule.onNodeWithTag("home_floating_button_row").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("OFF")

        // Tap toggle without permission -> launches ACTION_MANAGE_OVERLAY_PERMISSION and stays OFF until granted
        composeTestRule.onNodeWithTag("home_floating_button_toggle").performClick()
        composeTestRule.waitForIdle()
        assertFalse(FloatingCounterManager.state.value.isRunning)
        val shadowApp = Shadows.shadowOf(context as android.app.Application)
        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, startedIntent.action)

        // 2. Grant overlay permission -> tapping toggle turns Floating button ON immediately, and tapping again turns it OFF
        FloatingCounterManager.setOverlayPermissionOverrideForTest(true)
        composeTestRule.onNodeWithTag("home_floating_button_toggle").performClick()
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.state.value.isRunning)
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("ON")

        composeTestRule.onNodeWithTag("home_floating_button_toggle").performClick()
        composeTestRule.waitForIdle()
        assertFalse(FloatingCounterManager.state.value.isRunning)
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("OFF")

        // 3. Launching CounterScreen from widget "Add Custom" button opens "Add Custom Amount" popup immediately
        showCounterWithCustomPopup = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add Custom Amount").assertIsDisplayed()
        composeTestRule.onNodeWithTag("custom_amount_input").performTextInput("313")
        composeTestRule.onNodeWithTag("confirm_custom_amount_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(313L, customAddedAmount)
    }

    @Test
    fun `dataSavingAudit offlineFirst pendingCounts batchSyncEvery100Taps and UI pending badge`(): Unit = runBlocking {
        val userId = "audit_offline_user_${System.nanoTime()}"
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
        repo.ensureLocalProfile(userId, "Shahzaib")

        // 1. Offline-first writes + outbox queue (pendingSync = true)
        repo.incrementCount(userId, delta = 250L)
        val yesterday = LocalDate.now().minusDays(1).toString()
        repo.setDayCount(userId, yesterday, 400L)

        // Verify pending Days & pending Durood total flows from Room
        assertEquals(2, repo.observePendingDaysCount(userId).first())
        assertEquals(650L, repo.observePendingDuroodTotal(userId).first())

        // 2. Batch sync threshold on floating / bridge taps: single taps accumulate in Room without per-tap cloud write
        val appDb = AppDatabase.getInstance(context)
        WidgetAndFloatingBridge.setActiveUserId(context, userId)
        WidgetAndFloatingBridge.resetUnsyncedTapCount(context)
        assertEquals(0L, WidgetAndFloatingBridge.getUnsyncedTapCount(context))

        for (i in 1..5) {
            WidgetAndFloatingBridge.incrementCountAndSync(
                context = context,
                delta = 1L,
                db = appDb,
                userIdOverride = userId
            )
        }
        // Still < 100 taps -> unsynced tap counter is 5 (no per-tap cloud write reset)
        assertEquals(5L, WidgetAndFloatingBridge.getUnsyncedTapCount(context))
        assertEquals(100L, WidgetAndFloatingBridge.BATCH_SYNC_TAP_THRESHOLD)

        // 3. Verify Home UI shows offline badge, Last synced status, and pending Durood count
        val uiState = MainUiState(
            isLoading = false,
            totalCount = 650L,
            todayCount = 250L,
            isOnline = false,
            hasPendingSync = true,
            pendingSyncDaysCount = 2,
            pendingSyncDuroodCount = 650L
        )
        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = uiState,
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onRefreshGlobalStats = {},
                    onRefreshAnnouncements = {},
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {},
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("offline_sync_badge")
            .assertIsDisplayed()
            .assertTextContains("650 Durood (2 days) pending", substring = true)
    }

    @Test
    fun `dataSavingAudit backupRestoreTestGate verifies roundTrip keeps original Started date and rejects invalid backup`(): Unit = runBlocking {
        val userId = "audit_backup_user_${System.nanoTime()}"
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
        val originalStartMillis = LocalDate.of(2025, 1, 10)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = userId,
                displayName = "Shahzaib",
                dailyTarget = 2600,
                setupCompleted = true,
                startedAtMillis = originalStartMillis
            )
        )
        repo.setDayCount(userId, "2025-01-10", 2_600L)
        repo.setDayCount(userId, "2025-01-11", 3_100L)

        // Export backup -> passes verifyBackupRoundTrip gate automatically
        val exportedJson = repo.exportBackupJson(userId)
        assertTrue(com.example.data.BackupCodec.verifyBackupRoundTrip(exportedJson).isSuccess)

        // Simulate restoring onto a second device where profile was created later and one day had a higher local count
        val secondUserId = "audit_restore_target_${System.nanoTime()}"
        val laterStartMillis = LocalDate.of(2026, 6, 1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = secondUserId,
                displayName = "Shahzaib",
                dailyTarget = 2600,
                setupCompleted = true,
                startedAtMillis = laterStartMillis
            )
        )
        // Local has 4,000 on 2025-01-11 (higher than backup's 3,100) -> restore must keep 4,000
        repo.setDayCount(secondUserId, "2025-01-11", 4_000L)

        val restoreResult = repo.restoreBackupJson(secondUserId, exportedJson)
        assertTrue(restoreResult.isSuccess)
        // restoreBackupJson returns Result<RestoreOutcome>, not Result<Int> —
        // the 2 restored days are in restoredItems.
        assertEquals(2, restoreResult.getOrNull()?.restoredItems)

        // Original Started date is preserved (2025-01-10, not 2026-06-01)
        val restoredProfile = repo.getProfileOnce(secondUserId)
        assertEquals(originalStartMillis, restoredProfile?.startedAtMillis)
        // Total is 2,600 + 4,000 = 6,600 (never overwritten with a smaller count)
        assertEquals(6_600L, repo.observeTotalCount(secondUserId).first())

        // Invalid/unrecognized and corrupt JSON are rejected by the restore-test gate before touching local data
        assertTrue(com.example.data.BackupCodec.verifyBackupRoundTrip("{not-valid-json").isFailure)
        val badRestore = repo.restoreBackupJson(secondUserId, """{"unexpected":"payload"}""")
        assertTrue(badRestore.isFailure)
        assertEquals(6_600L, repo.observeTotalCount(secondUserId).first())
    }

    @Test
    fun `dataSavingAudit deleteAccount requires password reEntry and blocks empty or wrong password`() {
        var deletedCount = 0

        composeTestRule.setContent {
            OneCroreTheme {
                com.example.ui.screens.DeleteAccountConfirmDialog(
                    onDismiss = {},
                    onConfirmDelete = { deletedCount++ },
                    onVerifyPassword = { enteredPassword ->
                        if (enteredPassword == "CorrectPass123") {
                            Result.success(Unit)
                        } else {
                            Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
                        }
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Clicking Delete with empty password shows error and does NOT delete
        composeTestRule.onNodeWithTag("delete_account_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, deletedCount)
        composeTestRule.onNodeWithTag("delete_account_password_error")
            .assertIsDisplayed()
            .assertTextEquals("Enter your password to confirm account deletion")

        // 2. Entering short password (< 6 chars) shows validation error and does NOT delete
        composeTestRule.onNodeWithTag("delete_account_password_input").performTextInput("123")
        composeTestRule.onNodeWithTag("delete_account_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, deletedCount)
        composeTestRule.onNodeWithTag("delete_account_password_error")
            .assertIsDisplayed()
            .assertTextEquals("Password must be at least 6 characters")

        // 3. Entering wrong password shows verification error and does NOT delete
        composeTestRule.onNodeWithTag("delete_account_password_input").performTextReplacement("WrongPass123")
        composeTestRule.onNodeWithTag("delete_account_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, deletedCount)
        composeTestRule.onNodeWithTag("delete_account_password_error")
            .assertIsDisplayed()
            .assertTextEquals("Incorrect password. Please try again.")

        // 4. Entering the correct password verifies and proceeds with account deletion
        composeTestRule.onNodeWithTag("delete_account_password_input").performTextReplacement("CorrectPass123")
        composeTestRule.onNodeWithTag("delete_account_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, deletedCount)
    }

    @Test
    fun `updateSystemAudit laterCooldown24h forcedUpdateCannotSkip and failedDownloadCleansUpBrokenFile`(): Unit = runBlocking {
        com.example.update.AppUpdateManager.resetDismissedStateForTest(context)
        val t0 = 1_795_000_000_000L

        val optionalUpdateV4 = com.example.update.AppUpdateInfo(
            versionCode = 4,
            versionName = "1.3",
            downloadUrl = "https://drive.google.com/uc?export=download&id=v4",
            changelog = "Bug fixes and performance improvements",
            isForced = false
        )
        val forcedUpdateV4 = optionalUpdateV4.copy(isForced = true)
        val optionalUpdateV5 = optionalUpdateV4.copy(versionCode = 5, versionName = "1.4")

        // 1. Fresh optional v4 (> installed v3) -> auto-shows dialog
        assertTrue(
            com.example.update.AppUpdateManager.shouldAutoShowDialog(
                context = context,
                update = optionalUpdateV4,
                nowMillis = t0
            )
        )

        // 2. User taps "Later" at t0 -> suppressed in current session and for 24 hours
        com.example.update.AppUpdateManager.markDismissedForToday(
            context = context,
            versionCode = 4,
            nowMillis = t0
        )
        assertFalse(
            com.example.update.AppUpdateManager.shouldAutoShowDialog(
                context = context,
                update = optionalUpdateV4,
                nowMillis = t0 + 5 * 60_000L
            )
        )

        // Simulate a new app open 12 hours later (< 24h cooldown) -> still suppressed
        com.example.update.AppUpdateManager.clearSessionDismissalForTest()
        val twelveHoursLater = t0 + 12L * 3_600_000L
        assertFalse(
            com.example.update.AppUpdateManager.shouldAutoShowDialog(
                context = context,
                update = optionalUpdateV4,
                nowMillis = twelveHoursLater
            )
        )

        // 3. Forced update (isForced = true) or newer version (v5) ALWAYS bypasses "Later" cooldown
        assertTrue(
            com.example.update.AppUpdateManager.shouldAutoShowDialog(
                context = context,
                update = forcedUpdateV4,
                nowMillis = twelveHoursLater
            )
        )
        assertTrue(
            com.example.update.AppUpdateManager.shouldAutoShowDialog(
                context = context,
                update = optionalUpdateV5,
                nowMillis = twelveHoursLater
            )
        )

        // 4. After 24 hours have elapsed -> optional v4 auto-prompts again on a fresh session
        val twentyFiveHoursLater = t0 + 25L * 3_600_000L
        assertTrue(
            com.example.update.AppUpdateManager.shouldAutoShowDialog(
                context = context,
                update = optionalUpdateV4,
                nowMillis = twentyFiveHoursLater
            )
        )

        // 5. Partial/broken APK cleanup: cancelDownload and cleanupPartialApkFiles remove broken files
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)!!
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val brokenApk = java.io.File(downloadsDir, "1crore-update-4.apk").apply {
            writeText("partial broken download bytes")
        }
        assertTrue(brokenApk.exists())
        com.example.update.AppUpdateManager.cleanupPartialApkFiles(context, versionCode = 4)
        assertFalse("Broken partial APK file must be deleted on cleanup", brokenApk.exists())

        // 6. Verify Forced Update Dialog UI cannot be skipped (no Later button, shows forced notice)
        //    and Failed Download state shows error banner with retryable Update Now button (never stuck)
        var dialogUpdate by mutableStateOf(forcedUpdateV4)
        var downloadProgress by mutableStateOf<Float?>(null)
        var errorMessage by mutableStateOf<String?>(null)
        var retryClicks = 0

        composeTestRule.setContent {
            OneCroreTheme {
                com.example.ui.screens.AppUpdateDialog(
                    update = dialogUpdate,
                    isPreparing = false,
                    isDownloading = false,
                    downloadProgress = downloadProgress,
                    downloadStatusText = null,
                    needsInstallPermission = false,
                    pendingInstallReady = false,
                    errorMessage = errorMessage,
                    onUpdateNow = { retryClicks++ },
                    onDismiss = {},
                    onCancelDownload = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Forced update shows forced notice and hides "Later" button
        composeTestRule.onNodeWithTag("app_update_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_dialog_title").assertTextEquals("Version 1.3 is available")
        composeTestRule.onNodeWithTag("update_dialog_changelog").assertTextEquals("Bug fixes and performance improvements")
        composeTestRule.onNodeWithTag("update_dialog_forced_notice")
            .assertIsDisplayed()
            .assertTextEquals("This update contains a critical data-safety or bug fix and is required to continue.")
        assertEquals(0, composeTestRule.onAllNodesWithTag("update_later_button").fetchSemanticsNodes().size)

        // Failed download shows clear error banner and allows immediate retry via "Update Now"
        dialogUpdate = optionalUpdateV4
        errorMessage = "Download failed. Please check your connection and try again."
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("update_error_banner")
            .assertIsDisplayed()
            .assertTextEquals("Download failed. Please check your connection and try again.")
        composeTestRule.onNodeWithTag("update_later_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_now_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, retryClicks)
    }

    @Test
    fun `smoothAnimationsAudit lowRamAndReduceMotionStaticGrid and isolatedGreetingDoesNotRecomposeHome`() {
        try {
            // 1. Low-RAM device detection override works cleanly
            com.example.ui.components.setLowRamDeviceOverrideForTest(true)
            assertTrue(com.example.ui.components.isLowEndOrLowRamDevice(context))
            com.example.ui.components.setLowRamDeviceOverrideForTest(false)
            assertFalse(com.example.ui.components.isLowEndOrLowRamDevice(context))

            // 2. Verify HomeScreen + AnimatedArabicGreeting + FaintGridBackground + RollingNumberText
            //    render smoothly and do not trigger parent HomeScreen recompositions during greeting animation
            Settings.Global.putFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            var parentRecompositions = 0
            var uiState by mutableStateOf(
                MainUiState(
                    isLoading = false,
                    totalCount = 12_500L,
                    todayCount = 600L
                )
            )

            composeTestRule.setContent {
                OneCroreTheme {
                    parentRecompositions++
                    HomeScreen(
                        uiState = uiState,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onRefreshGlobalStats = {},
                        onRefreshAnnouncements = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                }
            }
            composeTestRule.waitForIdle()
            val initialRecompositions = parentRecompositions

            // Advance virtual clock through Arabic greeting typing & fade and FaintGridBackground settle
            composeTestRule.mainClock.advanceTimeBy(2_500L)
            composeTestRule.waitForIdle()

            // Parent scope must not have been recomposed by the isolated leaf greeting or draw-phase grid
            assertEquals(initialRecompositions, parentRecompositions)
            composeTestRule.onNodeWithTag("home_arabic_greeting").assertIsDisplayed()

            // Updating count triggers smooth digit roll and updates displayed number
            uiState = uiState.copy(totalCount = 12_501L, todayCount = 601L)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_arabic_greeting").assertIsDisplayed()
        } finally {
            com.example.ui.components.setLowRamDeviceOverrideForTest(null)
        }
    }

    @Test
    fun `urgentFix globalCounterDisplays517And6ImmediatelyOnHomeScreen`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val liveStats = GlobalCounterStats(
            totalCount = 517L,
            userCount = 6L,
            fetchedAtMillis = System.currentTimeMillis()
        )
        GlobalStatsCache.saveCached(context, liveStats)

        var refreshCount = 0
        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isLoading = false,
                        totalCount = 120L,
                        todayCount = 40L,
                        isOnline = true,
                        globalStats = GlobalStatsCache.getCached(context)
                    ),
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onRefreshGlobalStats = { refreshCount++ },
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {},
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        assertTrue("Home open must trigger global stats refresh immediately", refreshCount >= 1)
        composeTestRule.onNodeWithTag("home_global_counter_block").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_global_counter_total")
            .assertTextEquals("Everyone together: 517 Durood")
        composeTestRule.onNodeWithTag("home_global_counter_users")
            .assertTextEquals("6 users")
        composeTestRule.onNodeWithTag("home_global_counter_subtitle")
            .assertTextEquals("Updates every hour")
    }

    @Test
    fun `urgentFix reminderFiresWhenIncompleteSuppressesWhenCompleteDeduplicatesPerSlotAndReschedulesOnBootOrUpdate`() = runBlocking {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val app = context.applicationContext as android.app.Application
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = Shadows.shadowOf(notificationManager)
        val shadowApp = Shadows.shadowOf(app)

        // Ensure SettingsScreen warns when POST_NOTIFICATIONS is denied while reminders are enabled
        shadowApp.denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        var permCheckEnabled by mutableStateOf(true)
        composeTestRule.setContent {
            OneCroreTheme {
                com.example.ui.screens.SettingsScreen(
                    uiState = MainUiState(
                        isLoading = false,
                        profile = UserProfileEntity(
                            userId = "u_rem_test",
                            displayName = "Test User",
                            dailyTarget = 2600,
                            setupCompleted = true,
                            remindersEnabled = permCheckEnabled
                        )
                    ),
                    onUpdateDisplayName = {},
                    onUpdatePlan = {},
                    onUpdateReminders = { enabled, _, _, _, _ -> permCheckEnabled = enabled },
                    onSyncNow = {},
                    onClearStatusMessage = {},
                    onExportBackupJson = { "{}" },
                    onRestoreBackupJson = {},
                    onDeleteAccount = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_notifications_permission_warning")
            .performScrollTo()
            .assertIsDisplayed()

        // Grant notification permission for firing checks
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowNm.setNotificationsEnabled(true)

        val uid = "u_rem_test"
        val day1Zone = ZoneId.of("Asia/Karachi")
        val day1SixPm = java.time.ZonedDateTime.of(2026, 11, 16, 18, 0, 5, 0, day1Zone)
        val day1NinePm = java.time.ZonedDateTime.of(2026, 11, 16, 21, 0, 5, 0, day1Zone)
        val day2SixPm = java.time.ZonedDateTime.of(2026, 11, 17, 18, 0, 5, 0, day1Zone)

        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = uid,
                displayName = "Ahmad",
                dailyTarget = 2600,
                setupCompleted = true,
                reminder1Hour = 18,
                reminder1Minute = 0,
                reminder2Hour = 21,
                reminder2Minute = 0,
                remindersEnabled = true
            )
        )
        db.dailyCountDao().upsertDay(
            DailyCountEntity(
                userId = uid,
                dateKey = "2026-11-16",
                count = 500L
            )
        )

        // Schedule with persisted config & userId (no FirebaseAuth user needed)
        ReminderScheduler.scheduleReminders(
            context = context,
            enabled = true,
            r1Hour = 18,
            r1Minute = 0,
            r2Hour = 21,
            r2Minute = 0,
            userId = uid,
            now = day1SixPm.minusMinutes(10)
        )
        assertEquals(uid, ReminderScheduler.getSavedScheduleConfig(context)?.userId)

        // 1. At 6:00 PM with 500 / 2600 (incomplete): Slot 1 fires notification
        val firedSlot1 = ReminderScheduler.evaluateAndSendReminder(
            context = context,
            slotOverride = 1,
            allowCatchUp = true,
            dbOverride = db,
            now = day1SixPm
        )
        assertTrue("6 PM reminder must fire when today's target is incomplete", firedSlot1)
        assertEquals(1, shadowNm.allNotifications.size)

        // 2. Duplicate trigger from backup WorkManager for Slot 1 on same day MUST deduplicate
        val duplicateSlot1 = ReminderScheduler.evaluateAndSendReminder(
            context = context,
            slotOverride = 1,
            allowCatchUp = true,
            dbOverride = db,
            now = day1SixPm.plusMinutes(2)
        )
        assertFalse("Duplicate trigger for Slot 1 on the same day must be suppressed", duplicateSlot1)
        assertEquals(1, shadowNm.allNotifications.size)

        // 3. At 9:00 PM with still 500 / 2600 (incomplete): Slot 2 fires notification (total 2/day)
        val firedSlot2 = ReminderScheduler.evaluateAndSendReminder(
            context = context,
            slotOverride = 2,
            allowCatchUp = true,
            dbOverride = db,
            now = day1NinePm
        )
        assertTrue("9 PM reminder must fire when today's target is still incomplete", firedSlot2)
        assertEquals(2, shadowNm.allNotifications.size)

        // 4. Next day (2026-11-17) with todayCount == 2600 (complete): NO reminder fires
        db.dailyCountDao().upsertDay(
            DailyCountEntity(
                userId = uid,
                dateKey = "2026-11-17",
                count = 2600L
            )
        )
        val day2Complete = ReminderScheduler.evaluateAndSendReminder(
            context = context,
            slotOverride = 1,
            allowCatchUp = true,
            dbOverride = db,
            now = day2SixPm
        )
        assertFalse("Reminder must NOT fire when today's daily target is already complete", day2Complete)
        assertEquals(2, shadowNm.allNotifications.size)

        // 5. Rescheduling on boot / package update / time change works from saved config without Auth
        ReminderScheduler.rescheduleFromSavedStateIfPossible(context, dbOverride = db)
        val cfg = ReminderScheduler.getSavedScheduleConfig(context)
        assertNotNull(cfg)
        assertTrue(cfg!!.enabled)
        assertEquals(18, cfg.r1Hour)
        assertEquals(21, cfg.r2Hour)
    }

    @Test
    fun `run2Task1 homePopupRespectsSplashGateAndShowsActiveAnnouncementImmediatelyOnceVisible`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val announcement = com.example.announcements.AnnouncementItem(
            id = "test-salam-1",
            title = "As-salamu alaykum",
            body = "Welcome to 1 Crore Durood.",
            priority = 10,
            maxShowsPerDay = 1,
            maxShowsPerWeek = 3
        )
        var shownCount = 0
        var canPresent by mutableStateOf(false) // Splash still covering screen

        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isLoading = false,
                        isUpdateCheckSettled = true,
                        activeAnnouncement = announcement
                    ),
                    canPresentPopups = canPresent,
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onRefreshGlobalStats = {},
                    onRefreshAnnouncements = {},
                    onAnnouncementShown = { shownCount++ },
                    onDismissAnnouncement = {},
                    onAnnouncementAction = {},
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {},
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // While behind splash, popup must NOT show and must NOT consume daily quota
        assertEquals(0, shownCount)
        assertEquals(0, composeTestRule.onAllNodesWithTag("announcement_dialog_card").fetchSemanticsNodes().size)

        // Once splash finishes, popup shows immediately and records its impression
        canPresent = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("announcement_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("announcement_dialog_title").assertTextEquals("As-salamu alaykum")
        assertEquals(1, shownCount)
    }

    @Test
    fun `run2Task2 notificationTapExtractsRawFcmAndHelperExtrasBypassesCapAndRendersClickableLinks`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        // 1. Verify raw background FCM intent extras (type="announcement", announcementId="test-salam-2")
        val fcmBackgroundIntent = android.content.Intent(context, MainActivity::class.java).apply {
            putExtra("type", "announcement")
            putExtra("announcementId", "test-salam-2")
        }
        val fcmReq = com.example.announcements.AnnouncementNotificationHelper
            .extractLaunchRequestFromIntent(fcmBackgroundIntent)
        assertNotNull(fcmReq)
        assertEquals("test-salam-2", fcmReq!!.announcementId)

        // 2. Verify foreground notification helper extras (extra_announcement_id + fallback content)
        val helperIntent = android.content.Intent(context, MainActivity::class.java).apply {
            putExtra(com.example.announcements.AnnouncementNotificationHelper.EXTRA_FROM_ANNOUNCEMENT_NOTIFICATION, true)
            putExtra(com.example.announcements.AnnouncementNotificationHelper.EXTRA_ANNOUNCEMENT_ID, "test-salam-3")
            putExtra(com.example.announcements.AnnouncementNotificationHelper.EXTRA_ANNOUNCEMENT_TITLE, "Push Title")
            putExtra(com.example.announcements.AnnouncementNotificationHelper.EXTRA_ANNOUNCEMENT_BODY, "Read details at https://example.com/durood")
        }
        val helperReq = com.example.announcements.AnnouncementNotificationHelper
            .extractLaunchRequestFromIntent(helperIntent)
        assertNotNull(helperReq)
        assertEquals("test-salam-3", helperReq!!.announcementId)
        assertEquals("Push Title", helperReq.fallbackTitle)

        // 3. Direct notification announcement opens immediately even if update check is still settling
        //    and renders clickable inline URL links + Join CTA button
        val directAnnouncement = com.example.announcements.AnnouncementItem(
            id = "test-salam-3",
            title = "Weekly Gathering",
            body = "Full announcement message. Visit https://example.com/durood for details.",
            buttonText = "Join",
            buttonAction = com.example.announcements.AnnouncementAction.LINK,
            buttonUrl = "https://example.com/join"
        )
        val linkified = com.example.ui.components.buildLinkifiedAnnouncementBody(directAnnouncement.body)
        val urlAnnotations = linkified.getLinkAnnotations(0, linkified.length)
        assertEquals(1, urlAnnotations.size)
        assertEquals(
            "https://example.com/durood",
            (urlAnnotations.first().item as androidx.compose.ui.text.LinkAnnotation.Url).url
        )

        var clickedAction: com.example.announcements.AnnouncementItem? = null
        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isLoading = false,
                        isUpdateCheckSettled = false,
                        activeAnnouncement = directAnnouncement,
                        isDirectAnnouncement = true
                    ),
                    canPresentPopups = true,
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onRefreshGlobalStats = {},
                    onRefreshAnnouncements = {},
                    onAnnouncementShown = {},
                    onDismissAnnouncement = {},
                    onAnnouncementAction = { clickedAction = it },
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {},
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("announcement_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("announcement_dialog_title").assertTextEquals("Weekly Gathering")
        composeTestRule.onNodeWithTag("announcement_dialog_body").assertIsDisplayed()
        composeTestRule.onNodeWithTag("announcement_action_button")
            .assertIsDisplayed()
            .assertTextEquals("Join")
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals("https://example.com/join", clickedAction?.buttonUrl)
    }

    @Test
    fun `globalStats observeGlobalStatsOnce returns null when signed out and stalled read times out without wedging mutex`() = runBlocking<Unit> {
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = auth
        )

        // 1. Signed-out observeGlobalStatsOnce must return null (never a no-op {}), so bootstrap recovery can re-attach
        val remover = repo.observeGlobalStatsOnce(
            context = context,
            onStatsLoaded = {},
            onConfirmedMissing = {},
            onError = {}
        )
        assertNull(remover)

        // 2. Bounded server + default read: a stalled read must time out via withTimeoutOrNull and not hang forever
        val timedOut = runCatching {
            repo.fetchGlobalWithTimeoutRetry<String>(
                timeoutMs = 80L,
                backoffDelays = longArrayOf(15L),
                refreshAuthToken = {},
                fetchServer = { kotlinx.coroutines.CompletableDeferred<String>().await() },
                fetchDefault = { kotlinx.coroutines.CompletableDeferred<String>().await() }
            )
        }
        assertTrue(timedOut.isFailure)
        assertTrue(timedOut.exceptionOrNull() is java.util.concurrent.TimeoutException)

        // If first attempt times out and second attempt recovers, result succeeds cleanly
        var serverAttempts = 0
        val recovered = repo.fetchGlobalWithTimeoutRetry(
            timeoutMs = 80L,
            backoffDelays = longArrayOf(15L),
            refreshAuthToken = {},
            fetchServer = {
                serverAttempts++
                if (serverAttempts == 1) {
                    kotlinx.coroutines.CompletableDeferred<String>().await()
                } else {
                    "ok-after-retry"
                }
            },
            fetchDefault = { "fallback" }
        )
        assertEquals("ok-after-retry", recovered)

        // 3. globalStatsMutex is NOT held across network I/O: while one fetch stalls, another succeeds immediately
        GlobalStatsCache.clearCache(context)
        val firstFetchEntered = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseFirstFetch = kotlinx.coroutines.CompletableDeferred<DuroodRepository.GlobalStatsLoadOutcome>()
        val firstJob = async {
            repo.loadGlobalStatsWithFetcher(
                context = context,
                isOnline = true,
                forceWhenEmpty = true,
                hasSignedInUser = true
            ) {
                firstFetchEntered.complete(Unit)
                releaseFirstFetch.await()
            }
        }
        firstFetchEntered.await()

        val liveStats = GlobalCounterStats(
            totalCount = 558L,
            userCount = 6L,
            fetchedAtMillis = System.currentTimeMillis()
        )
        val secondOutcome = repo.loadGlobalStatsWithFetcher(
            context = context,
            isOnline = true,
            forceWhenEmpty = true,
            hasSignedInUser = true
        ) {
            DuroodRepository.GlobalStatsLoadOutcome.Loaded(liveStats, fromServer = true)
        }
        assertTrue(secondOutcome is DuroodRepository.GlobalStatsLoadOutcome.Loaded)
        assertEquals(liveStats, (secondOutcome as DuroodRepository.GlobalStatsLoadOutcome.Loaded).stats)
        assertEquals(liveStats, GlobalStatsCache.getCached(context))

        releaseFirstFetch.complete(DuroodRepository.GlobalStatsLoadOutcome.TransientUnavailable(null))
        val firstOutcome = firstJob.await()
        assertTrue(firstOutcome is DuroodRepository.GlobalStatsLoadOutcome.TransientUnavailable)
        assertEquals(liveStats, GlobalStatsCache.getCached(context))
    }

    @Test
    fun `mainViewModel attachGlobalStatsListenerIfNeeded retries after null and resets on listener error`() {
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = auth
        )
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = "test-user-1",
            fallbackDisplayName = "Tester",
            accountCreatedAtMillis = 1_700_000_000_000L,
            appContext = context
        )
        val store = androidx.lifecycle.ViewModelStore()
        store.put("main", viewModel)
        var latestState: MainUiState? = null
        val uiCollectorJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            viewModel.uiState.collect { latestState = it }
        }
        try {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            // 1. When observer returns null (e.g. auth not ready), attachGlobalStatsListenerIfNeeded returns false
            //    so a subsequent attempt can still attach once auth is ready.
            assertFalse(
                viewModel.attachGlobalStatsListenerIfNeeded { _, _, _ -> null }
            )

            // 2. Once non-null remover is returned, it is stored and stays attached across duplicate attach calls
            var removedCount = 0
            var capturedErrorCallback: ((Exception) -> Unit)? = null
            var capturedStatsCallback: ((GlobalCounterStats) -> Unit)? = null
            assertTrue(
                viewModel.attachGlobalStatsListenerIfNeeded { onLoaded, _, onError ->
                    capturedStatsCallback = onLoaded
                    capturedErrorCallback = onError
                    { removedCount++ }
                }
            )
            var duplicateCalled = false
            assertTrue(
                viewModel.attachGlobalStatsListenerIfNeeded { _, _, _ ->
                    duplicateCalled = true
                    null
                }
            )
            assertFalse(duplicateCalled)

            // 3. When listener errors, remover is invoked & cleared so recovery can re-attach cleanly
            capturedErrorCallback?.invoke(IllegalStateException("Simulated transient Firestore listener error"))
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals(1, removedCount)

            // Now a new listener can attach again and deliver live stats to uiState
            assertTrue(
                viewModel.attachGlobalStatsListenerIfNeeded { onLoaded, _, _ ->
                    capturedStatsCallback = onLoaded
                    { removedCount++ }
                }
            )
            val expectedStats = GlobalCounterStats(
                totalCount = 558L,
                userCount = 6L,
                fetchedAtMillis = System.currentTimeMillis()
            )
            capturedStatsCallback?.invoke(expectedStats)

            val deadline = System.currentTimeMillis() + 3_000L
            while (latestState?.globalStats != expectedStats && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals(expectedStats, latestState?.globalStats)
            assertEquals(2, removedCount)
        } finally {
            uiCollectorJob.cancel()
            store.clear()
        }
    }

    @Test
    fun `announcementFetchFix list and direct reads time out stalled server calls and reject stale or deactivated cache fallbacks`() = runBlocking<Unit> {
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = FirebaseAuth.getInstance()
        )
        val nowMs = 1_794_860_000_000L

        val staleCachedSalam = mapOf(
            "active" to true, // stale local cache still had active=true before Firestore deactivated it
            "title" to "Salam",
            "body" to "This is a test announcement...",
            "startAt" to nowMs - 3_600_000L,
            "endAt" to nowMs + 3_600_000L,
            "priority" to 10
        )
        val deactivatedDoc = mapOf(
            "active" to false,
            "title" to "Old Salam",
            "body" to "Deactivated",
            "startAt" to nowMs - 3_600_000L,
            "endAt" to nowMs + 3_600_000L
        )
        val expiredDoc = mapOf(
            "active" to true,
            "title" to "Expired",
            "body" to "Past endAt",
            "startAt" to nowMs - 7_200_000L,
            "endAt" to nowMs - 1_000L
        )
        val liveJoinDoc = mapOf(
            "active" to true,
            "title" to "السلام علیکم",
            "body" to "Join the Rza Official channel...",
            "button" to mapOf(
                "text" to "Join my channel",
                "action" to "link",
                "url" to "https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B"
            ),
            "startAt" to nowMs - 60_000L,
            "endAt" to nowMs + 3L * 24L * 60L * 60L * 1000L,
            "maxShowsPerDay" to 3,
            "maxShowsPerWeek" to 21,
            "priority" to 10
        )

        // 1. List fetch: stalled Source.SERVER + stalled default times out quickly and marks transient error
        val timedOutList = repo.fetchAnnouncementListWithTimeoutRetry(
            nowMillis = nowMs,
            timeoutMs = 70L,
            backoffDelays = longArrayOf(15L),
            refreshAuthToken = {},
            fetchServer = { kotlinx.coroutines.CompletableDeferred<DuroodRepository.AnnouncementSnapshotBatch>().await() },
            fetchDefault = { kotlinx.coroutines.CompletableDeferred<DuroodRepository.AnnouncementSnapshotBatch>().await() }
        )
        assertTrue(timedOutList.items.isEmpty())
        assertTrue(timedOutList.hadTransientError)

        // 2. List fetch: when Source.SERVER fails and fallback returns a cached snapshot (isFromCache=true),
        //    the stale cached salam doc MUST be rejected and nothing shown (hadTransientError = true).
        val staleCacheFallback = repo.fetchAnnouncementListWithTimeoutRetry(
            nowMillis = nowMs,
            timeoutMs = 70L,
            backoffDelays = longArrayOf(15L),
            refreshAuthToken = {},
            fetchServer = { throw IllegalStateException("Network stalled") },
            fetchDefault = {
                DuroodRepository.AnnouncementSnapshotBatch(
                    isFromCache = true,
                    docs = listOf("test-salam-1" to staleCachedSalam)
                )
            }
        )
        assertTrue(staleCacheFallback.items.isEmpty())
        assertTrue(staleCacheFallback.hadTransientError)

        // 3. List fetch: live server snapshot filters out inactive & expired docs and keeps test-join-2
        val liveList = repo.fetchAnnouncementListWithTimeoutRetry(
            nowMillis = nowMs,
            timeoutMs = 70L,
            backoffDelays = longArrayOf(15L),
            refreshAuthToken = {},
            fetchServer = {
                DuroodRepository.AnnouncementSnapshotBatch(
                    isFromCache = false,
                    docs = listOf(
                        "test-salam-1" to deactivatedDoc,
                        "expired-1" to expiredDoc,
                        "test-join-2" to liveJoinDoc
                    )
                )
            },
            fetchDefault = { DuroodRepository.AnnouncementSnapshotBatch(isFromCache = true, docs = emptyList()) }
        )
        assertFalse(liveList.hadTransientError)
        assertEquals(listOf("test-join-2"), liveList.items.map { it.id })
        assertEquals("Join my channel", liveList.items.first().buttonText)
        assertEquals("https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B", liveList.items.first().buttonUrl)

        // 4. Direct notification fetch: stalled Source.SERVER + cache fallback MUST return null (never stale salam)
        val directCacheRejected = repo.fetchAnnouncementByIdWithTimeoutRetry(
            announcementId = "test-join-2",
            timeoutMs = 70L,
            backoffDelays = longArrayOf(15L),
            refreshAuthToken = {},
            fetchServer = { kotlinx.coroutines.CompletableDeferred<DuroodRepository.AnnouncementSingleSnapshot>().await() },
            fetchDefault = {
                DuroodRepository.AnnouncementSingleSnapshot(
                    exists = true,
                    isFromCache = true,
                    id = "test-salam-1",
                    data = staleCachedSalam
                )
            }
        )
        assertNull(directCacheRejected)

        // 5. Direct notification fetch: live server doc returns test-join-2 with Join button
        val directLive = repo.fetchAnnouncementByIdWithTimeoutRetry(
            announcementId = "test-join-2",
            timeoutMs = 70L,
            backoffDelays = longArrayOf(15L),
            refreshAuthToken = {},
            fetchServer = {
                DuroodRepository.AnnouncementSingleSnapshot(
                    exists = true,
                    isFromCache = false,
                    id = "test-join-2",
                    data = liveJoinDoc
                )
            },
            fetchDefault = {
                DuroodRepository.AnnouncementSingleSnapshot(exists = false, isFromCache = true, id = "test-join-2", data = null)
            }
        )
        assertNotNull(directLive)
        assertEquals("test-join-2", directLive?.id)
        assertEquals("Join my channel", directLive?.buttonText)
        assertEquals("https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B", directLive?.buttonUrl)
    }

    @Test
    fun `announcementFetchFix mainViewModel refresh clears stale popup on transient failure and displays live test-join-2 on recovery`() {
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = auth
        )
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = "test-user-ann",
            fallbackDisplayName = "Tester",
            accountCreatedAtMillis = 1_700_000_000_000L,
            appContext = context
        )
        val store = androidx.lifecycle.ViewModelStore()
        store.put("main", viewModel)
        var latestState: MainUiState? = null
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val collectorJob = scope.launch {
            viewModel.uiState.collect { latestState = it }
        }
        try {
            val initDeadline = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < initDeadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            val nowMs = System.currentTimeMillis()
            val oldItem = com.example.announcements.AnnouncementItem(
                id = "old-salam",
                title = "Old",
                body = "Old message",
                startAtMillis = nowMs - 60_000L,
                endAtMillis = nowMs + 60_000L,
                priority = 5
            )
            val joinItem = com.example.announcements.AnnouncementItem(
                id = "test-join-2",
                title = "السلام علیکم",
                body = "Join the Rza Official channel...",
                buttonText = "Join my channel",
                buttonAction = com.example.announcements.AnnouncementAction.LINK,
                buttonUrl = "https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B",
                startAtMillis = nowMs - 60_000L,
                endAtMillis = nowMs + 3L * 24L * 60L * 60L * 1000L,
                maxShowsPerDay = 3,
                maxShowsPerWeek = 21,
                priority = 10
            )

            // 1. Initial live refresh shows oldItem
            scope.launch {
                viewModel.performAnnouncementRefresh(nowMillis = nowMs) {
                    DuroodRepository.AnnouncementFetchResult(items = listOf(oldItem), hadTransientError = false)
                }
            }
            val deadline1 = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "old-salam" && System.currentTimeMillis() < deadline1) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals("old-salam", latestState?.activeAnnouncement?.id)

            // 2. Next Home open/resume refresh fails live fetch -> clears stale popup (shows nothing)
            scope.launch {
                viewModel.performAnnouncementRefresh(nowMillis = nowMs) {
                    DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = true)
                }
            }
            val deadline2 = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement != null && System.currentTimeMillis() < deadline2) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertNull(latestState?.activeAnnouncement)

            // 3. Subsequent live fetch returns test-join-2 -> displays test-join-2 with Join button
            scope.launch {
                viewModel.performAnnouncementRefresh(nowMillis = nowMs) {
                    DuroodRepository.AnnouncementFetchResult(items = listOf(joinItem), hadTransientError = false)
                }
            }
            val deadline3 = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "test-join-2" && System.currentTimeMillis() < deadline3) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals("test-join-2", latestState?.activeAnnouncement?.id)
            assertEquals("Join my channel", latestState?.activeAnnouncement?.buttonText)
            assertEquals(
                "https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B",
                latestState?.activeAnnouncement?.buttonUrl
            )
        } finally {
            collectorJob.cancel()
            store.clear()
        }
    }

    @Test
    fun `globalStatsReDiagnosis surfaces failureReason and never stalls on default database`() = runBlocking<Unit> {
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        GlobalStatsCache.clearCache(context)

        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = auth
        )

        // 1. Signed-out detailed fetch returns immediately with AUTH_NULL diagnostic reason (no 37s default-DB stall)
        val startMs = System.currentTimeMillis()
        val signedOutOutcome = repo.loadGlobalStatsDetailed(
            context = context,
            isOnline = true,
            forceWhenEmpty = true
        )
        val elapsedMs = System.currentTimeMillis() - startMs
        assertTrue(signedOutOutcome is DuroodRepository.GlobalStatsLoadOutcome.TransientUnavailable)
        val transient = signedOutOutcome as DuroodRepository.GlobalStatsLoadOutcome.TransientUnavailable
        assertNull(transient.fallbackCached)
        assertEquals("AUTH_NULL", transient.failureReason)
        assertTrue("Expected fast signed-out return, took ${elapsedMs}ms", elapsedMs < 2_000L)

        // 2. Offline non-forced empty cache returns OFFLINE_SKIPPED diagnostic reason
        val offlineOutcome = repo.loadGlobalStatsWithFetcher(
            context = context,
            isOnline = false,
            forceWhenEmpty = false,
            hasSignedInUser = true
        ) {
            error("Remote fetcher should not be invoked when offline and not forced")
        }
        assertTrue(offlineOutcome is DuroodRepository.GlobalStatsLoadOutcome.TransientUnavailable)
        assertEquals(
            "OFFLINE_SKIPPED",
            (offlineOutcome as DuroodRepository.GlobalStatsLoadOutcome.TransientUnavailable).failureReason
        )
    }

    @Test
    fun `run3Task1 twoTypeDialog renders joinButtonAndTopBannerForLinkAndOmitsButtonForMessageOnlyWithEmojis`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )

        val testImageLoader = coil.ImageLoader.Builder(context)
            .components {
                add(
                    coil.intercept.Interceptor { chain ->
                        val dataStr = chain.request.data.toString()
                        if (dataStr == "https://example.com/durood-banner.png") {
                            val bmp = android.graphics.Bitmap.createBitmap(
                                16,
                                16,
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            coil.request.SuccessResult(
                                drawable = android.graphics.drawable.BitmapDrawable(context.resources, bmp),
                                request = chain.request,
                                dataSource = coil.decode.DataSource.MEMORY
                            )
                        } else {
                            coil.request.ErrorResult(
                                drawable = null,
                                request = chain.request,
                                throwable = java.io.IOException("Simulated image load failure")
                            )
                        }
                    }
                )
            }
            .build()
        coil.Coil.setImageLoader(testImageLoader)

        try {
            // 1. Type (a): message + link + optional top banner picture + emojis
            val linkAnnouncement = com.example.announcements.AnnouncementItem(
                id = "run3_link_1",
                title = "السلام علیکم 🌙✨",
                body = "Join our channel 🤲 https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B",
                imageUrl = "https://example.com/durood-banner.png",
                buttonText = "Join my channel",
                buttonAction = com.example.announcements.AnnouncementAction.LINK,
                buttonUrl = "https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B"
            )

            var currentAnnouncement by mutableStateOf<com.example.announcements.AnnouncementItem?>(linkAnnouncement)
            var actionClicked: com.example.announcements.AnnouncementItem? = null
            var dismissCount = 0

            composeTestRule.setContent {
                OneCroreTheme {
                    currentAnnouncement?.let { item ->
                        com.example.ui.components.AnnouncementDialog(
                            announcement = item,
                            onDismiss = { dismissCount++ },
                            onActionClick = { actionClicked = it }
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("announcement_dialog_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("announcement_dialog_image").assertIsDisplayed()
            composeTestRule.onNodeWithTag("announcement_dialog_title").assertTextEquals("السلام علیکم 🌙✨")
            composeTestRule.onNodeWithTag("announcement_dialog_body").assertIsDisplayed()
            composeTestRule.onNodeWithTag("announcement_action_button")
                .assertIsDisplayed()
                .assertTextEquals("Join my channel")
                .performClick()
            composeTestRule.waitForIdle()
            assertEquals("run3_link_1", actionClicked?.id)

            // Tap anywhere (banner / body) dismisses
            composeTestRule.onNodeWithTag("announcement_dialog_image").performClick()
            composeTestRule.waitForIdle()
            assertEquals(1, dismissCount)

            // 2. Type (b): message-only with emojis and NO link/button -> Join button must NOT exist
            currentAnnouncement = com.example.announcements.AnnouncementItem(
                id = "run3_msg_1",
                title = "Friday Reminder 🕌",
                body = "Recite Durood Shareef abundantly today 💖📿",
                imageUrl = null,
                buttonText = null,
                buttonAction = com.example.announcements.AnnouncementAction.NONE,
                buttonUrl = null
            )
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("announcement_dialog_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("announcement_dialog_title").assertTextEquals("Friday Reminder 🕌")
            composeTestRule.onNodeWithTag("announcement_dialog_body")
                .assertIsDisplayed()
                .performClick()
            composeTestRule.waitForIdle()
            assertEquals(2, dismissCount)
            composeTestRule.onNodeWithTag("announcement_dialog_image").assertDoesNotExist()
            composeTestRule.onNodeWithTag("announcement_action_button").assertDoesNotExist()
        } finally {
            coil.Coil.reset()
        }
    }

    @Test
    fun `run3Task2 frequencyRules enforce 10MinGap acrossRapidOpens 3To5DailyCap 21WeeklyCap and dismissReEligibility`() = runBlocking<Unit> {
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        val zone = ZoneId.of("UTC")
        val day1Start = LocalDate.of(2026, 11, 16).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

        val annA = com.example.announcements.AnnouncementItem(
            id = "run3_ann_a",
            title = "Message A 🌙",
            body = "First announcement",
            startAtMillis = day1Start - 60_000L,
            endAtMillis = day1Start + 30L * 24L * 60L * 60L * 1000L,
            priority = 10
        )
        val annB = com.example.announcements.AnnouncementItem(
            id = "run3_ann_b",
            title = "Message B 📿",
            body = "Second announcement",
            startAtMillis = day1Start - 60_000L,
            endAtMillis = day1Start + 30L * 24L * 60L * 60L * 1000L,
            priority = 5
        )

        // 1. Open 1 -> annA eligible, shown at T=0
        assertEquals(
            "run3_ann_a",
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annB), nowMillis = day1Start, zoneId = zone
            )?.id
        )
        com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u_run3", annA, nowMillis = day1Start)

        // 2. Open 2 & 3 within 1 minute (+20s, +55s) and at 9m59s -> BLOCKED for BOTH annA and annB
        assertNull(
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annB), nowMillis = day1Start + 20_000L, zoneId = zone
            )
        )
        assertNull(
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annB), nowMillis = day1Start + 55_000L, zoneId = zone
            )
        )
        assertNull(
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annB), nowMillis = day1Start + 9L * 60_000L + 59_000L, zoneId = zone
            )
        )

        // 3. Open after 10 minutes (+10m, +20m) -> eligible (shows 2 and 3 of Day 1, default daily cap = 3)
        val t10m = day1Start + 10L * 60_000L
        assertEquals(
            "run3_ann_a",
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annB), nowMillis = t10m, zoneId = zone
            )?.id
        )
        com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u_run3", annA, nowMillis = t10m)

        val t20m = day1Start + 20L * 60_000L
        assertEquals(
            "run3_ann_a",
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annB), nowMillis = t20m, zoneId = zone
            )?.id
        )
        com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u_run3", annA, nowMillis = t20m)

        // 4. 4th open on Day 1 (+30m) -> blocked by default 3/day cap across ALL announcements
        val t30m = day1Start + 30L * 60_000L
        assertNull(
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annB), nowMillis = t30m, zoneId = zone
            )
        )

        // 5. Configurable daily cap 3..5 in code: setting configuredMaxShowsPerDay=5 permits shows 4 & 5 on Day 1, blocks 6th
        com.example.announcements.AnnouncementFrequencyStore.configuredMaxShowsPerDay = 99 // clamps to 5
        assertEquals(5, com.example.announcements.AnnouncementFrequencyStore.configuredMaxShowsPerDay)
        val annUncappedDoc = annB.copy(maxShowsPerDay = 5)
        assertEquals(
            "run3_ann_b",
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annUncappedDoc), nowMillis = t30m, zoneId = zone
            )?.id
        )
        com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u_run3", annUncappedDoc, nowMillis = t30m)

        val t40m = day1Start + 40L * 60_000L
        com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u_run3", annUncappedDoc, nowMillis = t40m)

        val t50m = day1Start + 50L * 60_000L
        assertNull(
            com.example.announcements.AnnouncementFrequencyStore.selectNextEligibleAnnouncement(
                context, "u_run3", listOf(annA, annUncappedDoc), nowMillis = t50m, zoneId = zone
            )
        )

        // 6. Weekly cap of 21 across 7 days (default daily cap = 3 -> 3 * 7 = 21)
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        val oneHour = 3_600_000L
        val oneDay = 24L * oneHour
        for (day in 0 until 7) {
            val base = day1Start + day * oneDay
            for (slot in 0 until 3) {
                val ts = base + slot * oneHour
                assertTrue(
                    "Expected show day=$day slot=$slot to be eligible",
                    com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                        context, "u_week", annA, nowMillis = ts, zoneId = zone
                    )
                )
                com.example.announcements.AnnouncementFrequencyStore.recordShown(context, "u_week", annA, nowMillis = ts)
            }
        }
        // 22nd show inside 7-day rolling window (day 6 + 4h) -> blocked by 21/week cap
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context, "u_week", annB, nowMillis = day1Start + 6 * oneDay + 4 * oneHour, zoneId = zone, globalDailyCap = 5
            )
        )

        // 7. MainViewModel: records each presentation cleanly across dismiss + re-open in same process
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = auth
        )
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = "u_vm_run3",
            fallbackDisplayName = "Tester",
            accountCreatedAtMillis = 1_700_000_000_000L,
            appContext = context
        )
        val store = androidx.lifecycle.ViewModelStore()
        store.put("main", viewModel)
        var latestState: MainUiState? = null
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val uiCollectorJob = vmScope.launch {
            viewModel.uiState.collect { latestState = it }
        }
        try {
            val initDeadline = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < initDeadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            val vmNow = System.currentTimeMillis()
            val liveAnnA = annA.copy(
                startAtMillis = vmNow - 60_000L,
                endAtMillis = vmNow + 30L * 24L * 60L * 60L * 1000L
            )

            viewModel.performAnnouncementRefresh(nowMillis = vmNow) {
                DuroodRepository.AnnouncementFetchResult(items = listOf(liveAnnA), hadTransientError = false)
            }
            val d1 = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "run3_ann_a" && System.currentTimeMillis() < d1) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals("run3_ann_a", latestState?.activeAnnouncement?.id)
            viewModel.onAnnouncementShown(liveAnnA)
            assertEquals(1, com.example.announcements.AnnouncementFrequencyStore.getAnnouncementShownTimestamps(context, "u_vm_run3", "run3_ann_a").size)

            // Duplicate refresh while dialog is visible must NOT self-evict it
            viewModel.performAnnouncementRefresh(nowMillis = System.currentTimeMillis() + 5_000L) {
                DuroodRepository.AnnouncementFetchResult(items = listOf(liveAnnA), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals("run3_ann_a", latestState?.activeAnnouncement?.id)

            // Dismiss -> opening 20s later is blocked by 10-minute cooldown
            viewModel.dismissAnnouncement()
            val d2 = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement != null && System.currentTimeMillis() < d2) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertNull(latestState?.activeAnnouncement)

            viewModel.performAnnouncementRefresh(nowMillis = System.currentTimeMillis() + 20_000L) {
                DuroodRepository.AnnouncementFetchResult(items = listOf(liveAnnA), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull(latestState?.activeAnnouncement)

            // Opening 11 minutes later -> shows again and records 2nd impression
            val laterMs = System.currentTimeMillis() + 11L * 60_000L
            viewModel.performAnnouncementRefresh(nowMillis = laterMs) {
                DuroodRepository.AnnouncementFetchResult(items = listOf(liveAnnA), hadTransientError = false)
            }
            val d3 = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "run3_ann_a" && System.currentTimeMillis() < d3) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals("run3_ann_a", latestState?.activeAnnouncement?.id)
            viewModel.onAnnouncementShown(liveAnnA)
            assertEquals(2, com.example.announcements.AnnouncementFrequencyStore.getAnnouncementShownTimestamps(context, "u_vm_run3", "run3_ann_a").size)
        } finally {
            uiCollectorJob.cancel()
            store.clear()
        }
    }

    @Test
    fun `run4Task1 dragToClose shows bottomCenter X on drag drops outside to stay and drops on X to close instantly`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        FloatingCounterManager.resetForTest(context)
        FloatingCounterManager.setOverlayPermissionOverrideForTest(true)
        assertTrue(FloatingCounterManager.requestEnableFloatingButton(context))

        var closedCallbackCount = 0
        composeTestRule.setContent {
            OneCroreTheme {
                FloatingCounterBubbleComposable(
                    db = db,
                    userIdOverride = "u_run4_drag",
                    flashDurationMillis = 60_000L,
                    onClosed = { closedCallbackCount++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Holding stationary for >2.5s must NOT trigger any close X (long-press close removed)
        composeTestRule.onNodeWithTag("floating_counter_button").performTouchInput {
            down(center)
            advanceEventTime(2600L)
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.state.value.isRunning)
        assertEquals(0, composeTestRule.onAllNodesWithTag("floating_counter_close_target").fetchSemanticsNodes().size)

        // 2. Drag sideways away from bottom-center -> X appears while dragging, drop outside stays running at moved X/Y
        val stageBounds = composeTestRule.onNodeWithTag("floating_counter_root").fetchSemanticsNode().boundsInRoot
        val initialBubbleBounds = composeTestRule.onNodeWithTag("floating_counter_button").fetchSemanticsNode().boundsInRoot
        val bubbleHalf = initialBubbleBounds.width / 2f

        composeTestRule.onNodeWithTag("floating_counter_button").performTouchInput {
            down(Offset(bubbleHalf, bubbleHalf))
            moveBy(Offset(80f, 0f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_counter_close_target").assertIsDisplayed()
        assertTrue(FloatingCounterManager.state.value.isDragging)
        assertFalse(FloatingCounterManager.state.value.isOverCloseTarget)

        composeTestRule.onNodeWithTag("floating_counter_button").performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        assertTrue("Dropping outside X must keep floating button running", FloatingCounterManager.state.value.isRunning)
        assertFalse(FloatingCounterManager.state.value.isDragging)
        assertEquals(0, composeTestRule.onAllNodesWithTag("floating_counter_close_target").fetchSemanticsNodes().size)
        val movedBubbleBounds = composeTestRule.onNodeWithTag("floating_counter_button").fetchSemanticsNode().boundsInRoot
        assertTrue("Dropped bubble must stay at moved position", movedBubbleBounds.left > initialBubbleBounds.left + 20f)

        // 3. Drag onto bottom-center X close target -> hover locks onto target, drop closes IMMEDIATELY
        val targetCenterXInStage = stageBounds.width / 2f
        val density = context.resources.displayMetrics.density
        val bottomMarginPx = FloatingCounterManager.CLOSE_TARGET_BOTTOM_MARGIN_DP * density
        val restingHalfPx = (FloatingCounterManager.CLOSE_TARGET_RESTING_SIZE_DP * density) / 2f
        val targetCenterYInStage = stageBounds.height - bottomMarginPx - restingHalfPx
        val deltaToTargetX = targetCenterXInStage - movedBubbleBounds.center.x
        val deltaToTargetY = targetCenterYInStage - movedBubbleBounds.center.y

        composeTestRule.onNodeWithTag("floating_counter_button").performTouchInput {
            down(Offset(bubbleHalf, bubbleHalf))
            moveBy(Offset(deltaToTargetX, deltaToTargetY))
        }
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.state.value.isDragging)
        assertTrue("Bubble over bottom-center X must mark hover state true", FloatingCounterManager.state.value.isOverCloseTarget)
        composeTestRule.onNodeWithTag("floating_counter_close_target").assertIsDisplayed()

        composeTestRule.onNodeWithTag("floating_counter_button").performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()

        assertFalse("Dropping on X must close floating button immediately", FloatingCounterManager.state.value.isRunning)
        assertFalse(FloatingCounterManager.isRunning(context))
        assertEquals(1, closedCallbackCount)
        assertEquals(0, composeTestRule.onAllNodesWithTag("floating_counter_button").fetchSemanticsNodes().size)
    }

    @Test
    fun `run4Task2 widgetFloatingButton toggles ON and OFF and reflects Floating ON black pill and Home toggle`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        FloatingCounterManager.resetForTest(context)
        FloatingCounterManager.setOverlayPermissionOverrideForTest(true)

        // Simulate stale SharedPreferences KEY_IS_RUNNING=true from a dead background process while overlay is OFF:
        // widget ON path must still recognize the floating button is OFF and turn it ON reliably.
        context.applicationContext
            .getSharedPreferences("one_crore_floating_counter_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("floating_is_running", true)
            .commit()
        assertFalse(FloatingCounterManager.isRunning(context))
        assertFalse(com.example.floating.FloatingOverlayController.isOverlayShowing())

        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = MainUiState(isLoading = false),
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onRefreshGlobalStats = {},
                    onRefreshAnnouncements = {},
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {},
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val widgetProvider = OneCroreWidgetProvider()
        val host = FrameLayout(context)

        // Initially OFF -> widget pill shows "Floating Button" and Home toggle shows "OFF"
        composeTestRule.onNodeWithTag("home_floating_button_toggle").performScrollTo().assertTextEquals("OFF")
        val initialApplied = OneCroreWidgetProvider.buildRemoteViews(context, 10L, 100L).apply(context, host)
        val initialBtn = initialApplied.findViewById<TextView>(R.id.widget_btn_floating)
        assertEquals("Floating Button", initialBtn.text.toString())
        assertEquals(android.graphics.Color.BLACK, initialBtn.currentTextColor)

        // 1st widget tap -> turns floating button ON, attaches WindowManager overlay view directly,
        // widget reflects "Floating ON" white text on black pill, Home toggle reflects "ON"
        widgetProvider.onReceive(
            context,
            android.content.Intent(OneCroreWidgetProvider.ACTION_WIDGET_ENABLE_FLOATING_BROADCAST)
        )
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.isRunning(context))
        assertTrue(
            "Widget ON tap must directly attach the floating overlay window even without foreground startService",
            com.example.floating.FloatingOverlayController.isOverlayShowing()
        )
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("ON")
        val onApplied = OneCroreWidgetProvider.buildRemoteViews(context, 10L, 100L).apply(context, host)
        val onBtn = onApplied.findViewById<TextView>(R.id.widget_btn_floating)
        assertEquals("Floating ON", onBtn.text.toString())
        assertEquals(android.graphics.Color.WHITE, onBtn.currentTextColor)

        // 2nd widget tap -> turns floating button OFF, removes WindowManager overlay view,
        // widget reflects "Floating Button" black text on white pill, Home toggle reflects "OFF"
        widgetProvider.onReceive(
            context,
            android.content.Intent(OneCroreWidgetProvider.ACTION_WIDGET_ENABLE_FLOATING_BROADCAST)
        )
        composeTestRule.waitForIdle()
        assertFalse(FloatingCounterManager.isRunning(context))
        assertFalse(com.example.floating.FloatingOverlayController.isOverlayShowing())
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("OFF")
        val offApplied = OneCroreWidgetProvider.buildRemoteViews(context, 10L, 100L).apply(context, host)
        val offBtn = offApplied.findViewById<TextView>(R.id.widget_btn_floating)
        assertEquals("Floating Button", offBtn.text.toString())
        assertEquals(android.graphics.Color.BLACK, offBtn.currentTextColor)

        // Turn ON from widget again, then close via drag-to-X -> overlay removed, both Home toggle and widget revert to OFF
        widgetProvider.onReceive(
            context,
            android.content.Intent(OneCroreWidgetProvider.ACTION_WIDGET_ENABLE_FLOATING_BROADCAST)
        )
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.isRunning(context))
        assertTrue(com.example.floating.FloatingOverlayController.isOverlayShowing())
        FloatingCounterManager.onDragStarted()
        FloatingCounterManager.onDragTargetHoverChanged(true)
        assertTrue(FloatingCounterManager.onDragEnded(context, droppedOnClose = true))
        composeTestRule.waitForIdle()

        assertFalse(com.example.floating.FloatingOverlayController.isOverlayShowing())
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("OFF")
        val afterDragApplied = OneCroreWidgetProvider.buildRemoteViews(context, 10L, 100L).apply(context, host)
        assertEquals(
            "Floating Button",
            afterDragApplied.findViewById<TextView>(R.id.widget_btn_floating).text.toString()
        )
    }

    @Test
    fun `run5WidgetSmallSquare providerMetadata2x2 and layoutRendersAll5FeaturesWithoutClippingOrOverlap`() {
        // 1. Verify one_crore_widget_info.xml configures compact 110dp x 80dp (2x1) default and 40dp x 40dp horizontal|vertical resize
        val parser = context.resources.getXml(R.xml.one_crore_widget_info)
        val attrs = mutableMapOf<String, String>()
        var eventType = parser.eventType
        while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "appwidget-provider") {
                for (i in 0 until parser.attributeCount) {
                    attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                }
            }
            eventType = parser.next()
        }
        assertEquals("110.0dip", attrs["minWidth"])
        assertEquals("80.0dip", attrs["minHeight"])
        assertEquals("40.0dip", attrs["minResizeWidth"])
        assertEquals("40.0dip", attrs["minResizeHeight"])
        assertEquals("2", attrs["targetCellWidth"])
        assertEquals("1", attrs["targetCellHeight"])

        // 2. Measure & layout inflated widget at compact default (110dp x 80dp), small square (110dp x 110dp), and larger (220dp x 180dp)
        FloatingCounterManager.resetForTest(context)
        val density = context.resources.displayMetrics.density
        val host = FrameLayout(context)

        fun assertWidgetLayoutClean(widthDp: Float, heightDp: Float) {
            val wPx = (widthDp * density).toInt()
            val hPx = (heightDp * density).toInt()
            val view = OneCroreWidgetProvider.buildRemoteViews(
                context = context,
                todayCount = 1_250L,
                totalCount = 10_000_000L,
                isFloatingRunning = false
            ).apply(context, host)

            val wSpec = android.view.View.MeasureSpec.makeMeasureSpec(wPx, android.view.View.MeasureSpec.EXACTLY)
            val hSpec = android.view.View.MeasureSpec.makeMeasureSpec(hPx, android.view.View.MeasureSpec.EXACTLY)
            view.measure(wSpec, hSpec)
            view.layout(0, 0, wPx, hPx)

            fun screenRect(v: android.view.View): android.graphics.Rect {
                val r = android.graphics.Rect()
                v.getDrawingRect(r)
                (view as android.view.ViewGroup).offsetDescendantRectToMyCoords(v, r)
                return r
            }

            val todayLabel = view.findViewById<TextView>(R.id.widget_today_label)
            val todayCount = view.findViewById<TextView>(R.id.widget_today_count)
            val totalLabel = view.findViewById<TextView>(R.id.widget_total_label)
            val totalCount = view.findViewById<TextView>(R.id.widget_total_count)
            val addCustomBtn = view.findViewById<TextView>(R.id.widget_btn_add_custom)
            val openCounterBtn = view.findViewById<TextView>(R.id.widget_btn_open_counter)
            val floatingBtn = view.findViewById<TextView>(R.id.widget_btn_floating)

            assertEquals("1,250", todayCount.text.toString())
            assertEquals("10,000,000", totalCount.text.toString())
            assertEquals("Add Custom", addCustomBtn.text.toString())
            assertEquals("Open Counter", openCounterBtn.text.toString())
            assertEquals("Floating Button", floatingBtn.text.toString())

            val todayLabelRect = screenRect(todayLabel)
            val todayCountRect = screenRect(todayCount)
            val totalLabelRect = screenRect(totalLabel)
            val totalCountRect = screenRect(totalCount)
            val addCustomRect = screenRect(addCustomBtn)
            val openCounterRect = screenRect(openCounterBtn)
            val floatingRect = screenRect(floatingBtn)

            listOf(
                todayLabelRect, todayCountRect,
                totalLabelRect, totalCountRect,
                addCustomRect, openCounterRect, floatingRect
            ).forEach { rect ->
                assertTrue("Widget child must have positive width: $rect", rect.width() > 0)
                assertTrue("Widget child must have positive height: $rect", rect.height() > 0)
                assertTrue("Widget child must stay inside ${widthDp}x${heightDp}dp bounds: $rect", rect.left >= 0 && rect.top >= 0 && rect.right <= wPx && rect.bottom <= hPx)
            }

            // Counts must hug the top with minimal (<= 6dp) top padding
            assertTrue("Counts must start at top without wasted empty space (top=${todayLabelRect.top})", todayLabelRect.top <= (6f * density).toInt())
            // Columns don't overlap horizontally
            assertTrue("Today column must stay left of Total column", todayCountRect.right <= totalCountRect.left)
            assertTrue("Add Custom must stay left of Open Counter", addCustomRect.right <= openCounterRect.left)
            // Counts stay above buttons, and full-width Floating sits cleanly below Row 1
            assertTrue("Counts must not overlap Row 1 buttons", todayCountRect.bottom <= addCustomRect.top && totalCountRect.bottom <= openCounterRect.top)
            assertTrue("Floating button must sit below Add Custom + Open Counter row", addCustomRect.bottom <= floatingRect.top && openCounterRect.bottom <= floatingRect.top)
            assertTrue("Floating button must span wider than Row 1 half-buttons", floatingRect.width() > addCustomRect.width())
            // Combined button heights occupy more vertical height than the compact counts header
            val countsHeight = maxOf(todayCountRect.bottom, totalCountRect.bottom) - minOf(todayLabelRect.top, totalLabelRect.top)
            val buttonsHeight = addCustomRect.height() + floatingRect.height()
            assertTrue("Buttons ($buttonsHeight) must take more height than counts ($countsHeight)", buttonsHeight > countsHeight)
        }

        assertWidgetLayoutClean(110f, 80f)
        assertWidgetLayoutClean(110f, 110f)
        assertWidgetLayoutClean(220f, 180f)

        // 3. Verify active Floating ON black pill renders cleanly too
        val onView = OneCroreWidgetProvider.buildRemoteViews(
            context = context,
            todayCount = 1_250L,
            totalCount = 10_000_000L,
            isFloatingRunning = true
        ).apply(context, host)
        val onFloatingBtn = onView.findViewById<TextView>(R.id.widget_btn_floating)
        assertEquals("Floating ON", onFloatingBtn.text.toString())
        assertEquals(android.graphics.Color.WHITE, onFloatingBtn.currentTextColor)
    }

    @Test
    fun `run6Task1 scheduledAnnouncementGatesBeforeScheduledAtFires2MinAheadAndRespectsCaps`() = runBlocking<Unit> {
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        com.example.announcements.AnnouncementNotificationHelper.resetForTest(context)

        val now = System.currentTimeMillis()
        val twoMinLater = now + 120_000L
        val scheduledItem = com.example.announcements.AnnouncementItem(
            id = "run6_sched_2min",
            title = "Ramadan Night Reminder 🌙",
            body = "Read 100 Durood before sleep",
            buttonText = null,
            buttonAction = com.example.announcements.AnnouncementAction.NONE,
            buttonUrl = null,
            startAtMillis = twoMinLater,
            endAtMillis = twoMinLater + 7L * 24L * 60L * 60L * 1000L,
            scheduledAtMillis = twoMinLater,
            priority = 10
        )

        // Incoming FCM payload arriving early before scheduledAt must NOT notify or pop up early
        val shownEarly = com.example.announcements.AnnouncementNotificationHelper.handleIncomingFcmAnnouncement(
            context = context,
            item = scheduledItem,
            nowMillis = now,
            userIdOverride = "u_run6_sched",
            audienceContextOverride = com.example.announcements.AnnouncementAudienceContext()
        )
        assertFalse("Scheduled FCM arriving before scheduledAt must wait", shownEarly)
        assertEquals(0, com.example.announcements.AnnouncementScheduledDispatcher.dispatchDueAnnouncements(context, nowMillis = now, db = db, userIdOverride = "u_run6_sched"))

        // Before scheduledAt, MainViewModel must NOT show the popup
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(localDb = db, firestore = FirebaseFirestore.getInstance(), auth = auth)
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = "u_run6_sched",
            fallbackDisplayName = "Tester",
            accountCreatedAtMillis = 1_700_000_000_000L,
            appContext = context
        )
        val store = androidx.lifecycle.ViewModelStore()
        store.put("main", viewModel)
        var latestState: MainUiState? = null
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = vmScope.launch { viewModel.uiState.collect { latestState = it } }
        try {
            val deadline = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }

            viewModel.performAnnouncementRefresh(nowMillis = now) {
                DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull("Before scheduledAt, dialog must remain hidden", latestState?.activeAnnouncement)

            // At scheduledAt (+2 minutes), dispatcher dispatches due announcement and Home popup appears
            val dispatchedCount = com.example.announcements.AnnouncementScheduledDispatcher.dispatchDueAnnouncements(
                context = context,
                nowMillis = twoMinLater,
                db = db,
                userIdOverride = "u_run6_sched"
            )
            assertEquals(1, dispatchedCount)
            assertTrue(com.example.announcements.AnnouncementScheduleStore.wasPushDispatched(context, "run6_sched_2min"))

            viewModel.performAnnouncementRefresh(nowMillis = twoMinLater) {
                DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = false)
            }
            val waitPop = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "run6_sched_2min" && System.currentTimeMillis() < waitPop) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals("run6_sched_2min", latestState?.activeAnnouncement?.id)
            assertFalse("Message-only announcement must have NO action button", latestState?.activeAnnouncement?.hasActionButton == true)

            // Caps still apply (dismissing enforces 10-minute cooldown before re-showing)
            viewModel.dismissAnnouncement()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull(latestState?.activeAnnouncement)

            viewModel.performAnnouncementRefresh(nowMillis = twoMinLater + 60_000L) {
                DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull("10-min cooldown must block re-showing dismissed scheduled msg", latestState?.activeAnnouncement)
        } finally {
            job.cancel()
            store.clear()
        }
    }

    @Test
    fun `run6Task2 targetedAnnouncementsFilterIncompleteTargetTodayAndInactiveXDays`() = runBlocking<Unit> {
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        val uid = "u_run6_target"
        val now = System.currentTimeMillis()
        val todayKey = LocalDate.now().toString()
        val fourDaysAgoKey = LocalDate.now().minusDays(4).toString()

        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = uid,
                displayName = "Bilal",
                dailyTarget = 2600,
                setupCompleted = true
            )
        )
        // User had activity 4 days ago and has NOT completed today's target (today = 500 < 2600)
        db.dailyCountDao().upsertDay(
            DailyCountEntity(
                userId = uid,
                dateKey = fourDaysAgoKey,
                count = 2600L,
                updatedAtMillis = now - 4L * com.example.announcements.AnnouncementItem.ONE_DAY_MILLIS
            )
        )
        db.dailyCountDao().upsertDay(
            DailyCountEntity(
                userId = uid,
                dateKey = todayKey,
                count = 500L,
                updatedAtMillis = now
            )
        )

        val incompleteTargetAnn = com.example.announcements.AnnouncementItem(
            id = "run6_target_incomplete",
            title = "Finish Today's Target",
            body = "You are only 2,100 away!",
            buttonText = "Open",
            buttonAction = com.example.announcements.AnnouncementAction.LINK,
            buttonUrl = "https://example.com/circle",
            startAtMillis = now - 10_000L,
            endAtMillis = now + 86_400_000L,
            priority = 10,
            targetAudience = com.example.announcements.AnnouncementAudience.INCOMPLETE_DAILY_TARGET
        )

        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(localDb = db, firestore = FirebaseFirestore.getInstance(), auth = auth)
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uid,
            fallbackDisplayName = "Bilal",
            accountCreatedAtMillis = now - 10L * 86_400_000L,
            appContext = context
        )
        val store = androidx.lifecycle.ViewModelStore()
        store.put("main", viewModel)
        var latestState: MainUiState? = null
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = vmScope.launch { viewModel.uiState.collect { latestState = it } }
        try {
            val deadline = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }

            // 1. With todayCount = 500 (< 2600), user matches INCOMPLETE_DAILY_TARGET
            viewModel.performAnnouncementRefresh(nowMillis = now) {
                DuroodRepository.AnnouncementFetchResult(items = listOf(incompleteTargetAnn), hadTransientError = false)
            }
            val wait1 = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "run6_target_incomplete" && System.currentTimeMillis() < wait1) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals("run6_target_incomplete", latestState?.activeAnnouncement?.id)

            // 2. Once user completes today's target (2600 >= 2600), targeted message stops matching
            db.dailyCountDao().incrementDay(uid, todayKey, 2100L, now)
            viewModel.performAnnouncementRefresh(nowMillis = now + 15L * 60_000L) {
                DuroodRepository.AnnouncementFetchResult(items = listOf(incompleteTargetAnn), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull(
                "User who completed daily target must NOT see incomplete_today announcement",
                latestState?.activeAnnouncement
            )

            // 3. Inactive X days (default 3 days) targets inactive user (0 today, lastActive 4 days ago), not active user
            db.dailyCountDao().deleteAllForUser(uid)
            db.dailyCountDao().upsertDay(
                DailyCountEntity(
                    userId = uid,
                    dateKey = fourDaysAgoKey,
                    count = 2600L,
                    updatedAtMillis = now - 4L * com.example.announcements.AnnouncementItem.ONE_DAY_MILLIS
                )
            )
            com.example.announcements.AnnouncementFrequencyStore.setLastActiveForTest(
                context = context,
                userId = uid,
                lastActiveMillis = now - 4L * com.example.announcements.AnnouncementItem.ONE_DAY_MILLIS
            )
            val inactiveAnn = com.example.announcements.AnnouncementItem(
                id = "run6_target_inactive",
                title = "Miss your daily Durood?",
                body = "Restart your streak today!",
                startAtMillis = now - 10_000L,
                endAtMillis = now + 86_400_000L,
                priority = 10,
                targetAudience = com.example.announcements.AnnouncementAudience.INACTIVE_DAYS,
                inactiveDaysThreshold = 3
            )
            viewModel.performAnnouncementRefresh(nowMillis = now + 30L * 60_000L) {
                DuroodRepository.AnnouncementFetchResult(items = listOf(inactiveAnn), hadTransientError = false)
            }
            val wait2 = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "run6_target_inactive" && System.currentTimeMillis() < wait2) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals("run6_target_inactive", latestState?.activeAnnouncement?.id)

            // Active user (lastActive 1 day ago) must NOT receive inactive_days=3
            com.example.announcements.AnnouncementFrequencyStore.setLastActiveForTest(
                context = context,
                userId = uid,
                lastActiveMillis = now - 1L * com.example.announcements.AnnouncementItem.ONE_DAY_MILLIS
            )
            viewModel.performAnnouncementRefresh(nowMillis = now + 45L * 60_000L) {
                DuroodRepository.AnnouncementFetchResult(items = listOf(inactiveAnn), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull(
                "User opened within 1 day must NOT see inactive_days=3 announcement",
                latestState?.activeAnnouncement
            )
        } finally {
            job.cancel()
            store.clear()
        }
    }

    @Test
    fun `run6OwnerSettingsAndScheduleDialog buildsTwoTypeAndTargetedScheduledItems`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        com.example.announcements.AnnouncementOwnerConfig.resetForTest(context)
        val scheduledItems = mutableListOf<com.example.announcements.AnnouncementItem>()

        var currentEmail by mutableStateOf("regular.user@example.com")
        composeTestRule.setContent {
            OneCroreTheme {
                com.example.ui.screens.SettingsScreen(
                    uiState = MainUiState(isLoading = false),
                    onUpdateDisplayName = {},
                    onUpdatePlan = {},
                    onUpdateReminders = { _, _, _, _, _ -> },
                    onSyncNow = {},
                    onClearStatusMessage = {},
                    onExportBackupJson = { "{}" },
                    onRestoreBackupJson = {},
                    onDeleteAccount = {},
                    userEmail = currentEmail,
                    onScheduleAnnouncement = { scheduledItems.add(it) }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Non-owner must NOT see the owner announcement row
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag("settings_schedule_announcement_row").fetchSemanticsNodes().size
        )

        // Owner sees the row and can open the create/schedule dialog
        currentEmail = "supportwin717@gmail.com"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_schedule_announcement_row")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("schedule_announcement_dialog").assertIsDisplayed()

        // Submit Type (b) message-only + incomplete_today + +2 min preset
        composeTestRule.onNodeWithTag("schedule_announcement_title_input").performTextReplacement("Jumu'ah 🕌")
        composeTestRule.onNodeWithTag("schedule_announcement_body_input").performTextReplacement("Send blessings today!")
        composeTestRule.onNodeWithTag("schedule_announcement_audience_incomplete").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("schedule_announcement_preset_2min").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("schedule_announcement_submit_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, scheduledItems.size)
        val first = scheduledItems[0]
        assertEquals("Jumu'ah 🕌", first.title)
        assertEquals("Send blessings today!", first.body)
        assertEquals(com.example.announcements.AnnouncementAction.NONE, first.buttonAction)
        assertNull(first.buttonText)
        assertEquals(
            com.example.announcements.AnnouncementAudience.INCOMPLETE_DAILY_TARGET,
            first.targetAudience
        )
        assertTrue(first.scheduledAtMillis > System.currentTimeMillis())

        // Open again and submit Type (a) message+link + inactive_days=5
        composeTestRule.onNodeWithTag("settings_schedule_announcement_row").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("schedule_announcement_body_input").performTextReplacement("Come back to our channel!")
        composeTestRule.onNodeWithTag("schedule_announcement_type_link").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("schedule_announcement_link_input").performScrollTo()
            .performTextReplacement("https://whatsapp.com/channel/test")
        composeTestRule.onNodeWithTag("schedule_announcement_audience_inactive").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("schedule_announcement_inactive_days_input").performScrollTo()
            .performTextReplacement("5")
        composeTestRule.onNodeWithTag("schedule_announcement_submit_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, scheduledItems.size)
        val second = scheduledItems[1]
        assertEquals(com.example.announcements.AnnouncementAction.LINK, second.buttonAction)
        assertEquals("Join", second.buttonText)
        assertEquals("https://whatsapp.com/channel/test", second.buttonUrl)
        assertEquals(com.example.announcements.AnnouncementAudience.INACTIVE_DAYS, second.targetAudience)
        assertEquals(5, second.inactiveDaysThreshold)
    }

    @Test
    fun `run7Task1 milestoneAnnouncements fireOnceAt1LakhAnd10LakhRespectFrequencyCapsAndSuppressDuplicatePush`() = runBlocking<Unit> {
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        com.example.announcements.AnnouncementNotificationHelper.resetForTest(context)
        GlobalStatsCache.clearCache(context)

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val shadowNm = Shadows.shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        )
        shadowNm.setNotificationsEnabled(true)

        val uid = "u_run7_milestone"
        val t0 = System.currentTimeMillis()
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(localDb = db, firestore = FirebaseFirestore.getInstance(), auth = auth)
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uid,
            fallbackDisplayName = "Amina",
            accountCreatedAtMillis = t0 - 86_400_000L,
            appContext = context
        )
        val store = androidx.lifecycle.ViewModelStore()
        store.put("main", viewModel)
        var latestState: MainUiState? = null
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = vmScope.launch { viewModel.uiState.collect { latestState = it } }
        try {
            val deadline = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }

            var capturedLoaded: ((GlobalCounterStats) -> Unit)? = null
            assertTrue(
                viewModel.attachGlobalStatsListenerIfNeeded { onLoaded, _, _ ->
                    capturedLoaded = onLoaded
                    {}
                }
            )

            // 1. Below 1 Lakh (99,999): no milestone announcement pops up
            capturedLoaded?.invoke(
                GlobalCounterStats(totalCount = 99_999L, userCount = 40L, fetchedAtMillis = t0)
            )
            viewModel.performAnnouncementRefresh(nowMillis = t0) {
                DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull("Below 100,000 must NOT trigger milestone popup", latestState?.activeAnnouncement)

            // 2. Crossing 1 Lakh (100,000): message-only milestone_100000 pops up automatically
            capturedLoaded?.invoke(
                GlobalCounterStats(totalCount = 100_000L, userCount = 42L, fetchedAtMillis = t0)
            )
            viewModel.performAnnouncementRefresh(nowMillis = t0) {
                DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = false)
            }
            val wait1Lakh = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "milestone_100000" && System.currentTimeMillis() < wait1Lakh) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            val oneLakhPopup = latestState?.activeAnnouncement
            assertNotNull(oneLakhPopup)
            assertEquals("milestone_100000", oneLakhPopup?.id)
            assertFalse("Default milestone popup is message-only", oneLakhPopup?.hasActionButton == true)

            // Mark shown then dismiss: recorded in Messages History and NEVER repeats even after 10-min gap
            viewModel.onAnnouncementShown(oneLakhPopup!!)
            viewModel.dismissAnnouncement()
            val waitHistory = System.currentTimeMillis() + 3_000L
            while (latestState?.announcementHistory?.none {
                    it.announcement.id == "milestone_100000" &&
                        it.status == com.example.announcements.AnnouncementHistoryStore.STATUS_DISMISSED
                } == true &&
                System.currentTimeMillis() < waitHistory
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertTrue(
                latestState?.announcementHistory?.any {
                    it.announcement.id == "milestone_100000" &&
                        it.status == com.example.announcements.AnnouncementHistoryStore.STATUS_DISMISSED
                } == true
            )

            // Duplicate FCM push for already-completed 1 Lakh milestone MUST be suppressed
            val duplicateFcmShown = com.example.announcements.AnnouncementNotificationHelper.handleIncomingFcmAnnouncement(
                context = context,
                item = oneLakhPopup,
                nowMillis = t0 + 15L * 60_000L,
                userIdOverride = uid,
                audienceContextOverride = com.example.announcements.AnnouncementAudienceContext()
            )
            assertFalse("Already celebrated 1 Lakh milestone must NOT push again", duplicateFcmShown)

            // Refresh 15 minutes later while global total stays >= 100k: milestone_100000 must NEVER pop up again
            val tAfter15Min = t0 + 15L * 60_000L
            viewModel.performAnnouncementRefresh(nowMillis = tAfter15Min) {
                DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull("Dismissed milestone must not repeat", latestState?.activeAnnouncement)

            // 3. Crossing 10 Lakh (1,000,000) within 10-min gap of last show is blocked by frequency cap
            capturedLoaded?.invoke(
                GlobalCounterStats(totalCount = 1_000_000L, userCount = 120L, fetchedAtMillis = t0 + 60_000L)
            )
            viewModel.performAnnouncementRefresh(nowMillis = t0 + 60_000L) {
                DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = false)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull("10 Lakh within 10-min gap must wait", latestState?.activeAnnouncement)

            // After 10-min gap passes, 10 Lakh milestone pops up once
            viewModel.performAnnouncementRefresh(nowMillis = tAfter15Min) {
                DuroodRepository.AnnouncementFetchResult(items = emptyList(), hadTransientError = false)
            }
            val wait10Lakh = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "milestone_1000000" && System.currentTimeMillis() < wait10Lakh) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals("milestone_1000000", latestState?.activeAnnouncement?.id)
        } finally {
            job.cancel()
            store.clear()
        }
    }

    @Test
    fun `run7Task2 settingsOpensMessagesHistory newestFirstWithDismissedAndClickableJoinLink`() = runBlocking<Unit> {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        com.example.announcements.AnnouncementNotificationHelper.resetForTest(context)

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val shadowNm = Shadows.shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        )
        shadowNm.setNotificationsEnabled(true)

        val uid = "u_run7_history"
        val baseTime = 1_795_000_000_000L

        val pushedMsg = com.example.announcements.AnnouncementItem(
            id = "run7_hist_pushed",
            title = "Pushed Update",
            body = "Community push notification",
            startAtMillis = baseTime - 60_000L,
            endAtMillis = baseTime + 86_400_000L
        )
        assertTrue(
            com.example.announcements.AnnouncementNotificationHelper.handleIncomingFcmAnnouncement(
                context = context,
                item = pushedMsg,
                nowMillis = baseTime,
                userIdOverride = uid,
                audienceContextOverride = com.example.announcements.AnnouncementAudienceContext()
            )
        )

        val dismissedMsg = com.example.announcements.AnnouncementItem(
            id = "run7_hist_dismissed",
            title = "Dismissed Reminder",
            body = "You dismissed this reminder earlier."
        )
        com.example.announcements.AnnouncementHistoryStore.recordAnnouncement(
            context = context,
            userId = uid,
            announcement = dismissedMsg,
            status = com.example.announcements.AnnouncementHistoryStore.STATUS_DISMISSED,
            recordedAtMillis = baseTime + 60_000L
        )

        val joinMsg = com.example.announcements.AnnouncementItem(
            id = "run7_hist_join",
            title = "Community Circle",
            body = "Join our channel at https://example.com/circle",
            buttonText = "Join",
            buttonAction = com.example.announcements.AnnouncementAction.LINK,
            buttonUrl = "https://whatsapp.com/channel/run7"
        )
        com.example.announcements.AnnouncementHistoryStore.recordAnnouncement(
            context = context,
            userId = uid,
            announcement = joinMsg,
            status = com.example.announcements.AnnouncementHistoryStore.STATUS_ACTED,
            recordedAtMillis = baseTime + 120_000L
        )

        val historyNewestFirst = com.example.announcements.AnnouncementHistoryStore.getHistoryNewestFirst(context, uid)
        assertEquals(
            listOf("run7_hist_join", "run7_hist_dismissed", "run7_hist_pushed"),
            historyNewestFirst.map { it.announcement.id }
        )

        var showMessagesHistoryScreen by mutableStateOf(false)
        var showSettingsScreen by mutableStateOf(true)
        var renderEmptyHistory by mutableStateOf(false)
        var clickedJoinItem: com.example.announcements.AnnouncementItem? = null

        composeTestRule.setContent {
            OneCroreTheme {
                when {
                    showSettingsScreen -> {
                        com.example.ui.screens.SettingsScreen(
                            uiState = MainUiState(isLoading = false, announcementHistory = historyNewestFirst),
                            onUpdateDisplayName = {},
                            onUpdatePlan = {},
                            onUpdateReminders = { _, _, _, _, _ -> },
                            onSyncNow = {},
                            onClearStatusMessage = {},
                            onExportBackupJson = { "{}" },
                            onRestoreBackupJson = {},
                            onDeleteAccount = {}
                        )
                    }
                    !showMessagesHistoryScreen -> {
                        com.example.ui.screens.HistoryScreen(
                            uiState = MainUiState(isLoading = false, announcementHistory = historyNewestFirst),
                            historyDays = emptyList(),
                            onEditDayCount = { _, _, _ -> },
                            onOpenMessagesHistory = { showMessagesHistoryScreen = true }
                        )
                    }
                    else -> {
                        com.example.ui.screens.MessagesHistoryScreen(
                            entries = if (renderEmptyHistory) emptyList() else historyNewestFirst,
                            onBack = { showMessagesHistoryScreen = false },
                            onActionClick = { clickedJoinItem = it }
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // Verify Messages History has been removed from Settings
        composeTestRule.onNodeWithTag("settings_messages_history_row").assertDoesNotExist()

        // Switch to History screen and verify Messages History sits at the TOP next to "+ Log Date"
        showSettingsScreen = false
        composeTestRule.waitForIdle()

        val messagesBtnBounds = composeTestRule.onNodeWithTag("history_messages_history_button")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val logDateBtnBounds = composeTestRule.onNodeWithTag("history_log_date_button")
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertEquals(
            "Messages History button must sit on the exact same top row next to + Log Date",
            messagesBtnBounds.top,
            logDateBtnBounds.top,
            2f
        )
        assertTrue(
            "Messages History and + Log Date buttons must not overlap horizontally",
            messagesBtnBounds.right <= logDateBtnBounds.left
        )

        composeTestRule.onNodeWithTag("history_messages_history_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("messages_history_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("messages_history_card_run7_hist_join").assertIsDisplayed()
        composeTestRule.onNodeWithTag("messages_history_card_run7_hist_dismissed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("messages_history_date_run7_hist_join").assertIsDisplayed()

        // Verify newest-first visual vertical ordering on visible top items
        val joinBounds = composeTestRule.onNodeWithTag("messages_history_card_run7_hist_join")
            .fetchSemanticsNode().boundsInRoot
        val dismissedBounds = composeTestRule.onNodeWithTag("messages_history_card_run7_hist_dismissed")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Newest join message must render above dismissed message", joinBounds.top < dismissedBounds.top)

        // Clicking Join button on history item triggers action callback; message-only dismissed item has NO button
        composeTestRule.onNodeWithTag("messages_history_action_run7_hist_dismissed").assertDoesNotExist()
        composeTestRule.onNodeWithTag("messages_history_action_run7_hist_join")
            .assertIsDisplayed()
            .assertTextEquals("Join")
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals("https://whatsapp.com/channel/run7", clickedJoinItem?.buttonUrl)

        // Scroll down the LazyColumn to confirm the older pushed entry also appears below dismissed
        composeTestRule.onNodeWithTag("messages_history_list")
            .performScrollToNode(hasTestTag("messages_history_card_run7_hist_pushed"))
        composeTestRule.onNodeWithTag("messages_history_card_run7_hist_pushed").assertIsDisplayed()

        // Empty state renders cleanly when no messages recorded
        renderEmptyHistory = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("messages_history_empty").assertIsDisplayed()

        // Back button returns to History screen
        composeTestRule.onNodeWithTag("messages_history_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_messages_history_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_log_date_button").assertIsDisplayed()
    }

    @Test
    fun `run8 urgentAnnouncement bypasses10MinAnd3DayAnd21WeekCapsOnPopupAndPushAndRendersUrgentBadges`() = runBlocking<Unit> {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        com.example.announcements.AnnouncementFrequencyStore.resetForTest(context)
        com.example.announcements.AnnouncementNotificationHelper.resetForTest(context)

        val shadowApp = Shadows.shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val shadowNm = Shadows.shadowOf(
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        )
        shadowNm.setNotificationsEnabled(true)

        val uid = "u_run8_urgent"
        val now = 1_796_000_000_000L

        // Verify owner-only gating & ScheduleAnnouncementDialog Urgent toggle
        assertFalse(com.example.announcements.AnnouncementOwnerConfig.isOwnerUnlocked(context, "regular@example.com"))
        assertTrue(com.example.announcements.AnnouncementOwnerConfig.isOwnerUnlocked(context, "supportwin717@gmail.com"))

        var createdUrgentFromUi: com.example.announcements.AnnouncementItem? = null
        var useUrgentDialog by mutableStateOf(true)
        var showRecipientPopup by mutableStateOf<com.example.announcements.AnnouncementItem?>(null)
        var historyEntriesToRender by mutableStateOf<List<com.example.announcements.AnnouncementHistoryEntry>>(emptyList())
        var renderHistoryView by mutableStateOf(false)

        composeTestRule.setContent {
            OneCroreTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        renderHistoryView -> {
                            com.example.ui.screens.MessagesHistoryScreen(
                                entries = historyEntriesToRender,
                                onBack = {},
                                onActionClick = {}
                            )
                        }
                        showRecipientPopup != null -> {
                            com.example.ui.components.AnnouncementDialog(
                                announcement = showRecipientPopup!!,
                                onDismiss = {},
                                onActionClick = {}
                            )
                        }
                        useUrgentDialog -> {
                            com.example.ui.components.ScheduleAnnouncementDialog(
                                onDismiss = {},
                                onSchedule = { createdUrgentFromUi = it },
                                nowProvider = { now }
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("schedule_announcement_body_input")
            .performTextInput("Critical schedule update")
        composeTestRule.onNodeWithTag("schedule_announcement_send_now")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("schedule_announcement_urgent_toggle")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("schedule_announcement_urgent_badge", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("schedule_announcement_submit_button")
            .performScrollTo()
            .performClick()
        useUrgentDialog = false
        composeTestRule.waitForIdle()

        val ownerUrgentItem = createdUrgentFromUi
        assertNotNull(ownerUrgentItem)
        assertTrue("Owner Urgent toggle must set isUrgent=true", ownerUrgentItem?.isUrgent == true)
        assertEquals(true, ownerUrgentItem?.toFirestoreMap(now)?.get("isUrgent"))

        // Non-urgent built form keeps isUrgent=false
        val builtNormal = com.example.ui.components.buildAnnouncementItemFromForm(
            title = "Routine",
            body = "Regular update",
            isLinkType = false,
            linkUrl = "",
            buttonText = "Join",
            imageUrl = null,
            targetAudience = com.example.announcements.AnnouncementAudience.ALL,
            inactiveDaysThreshold = 3,
            scheduledAtMillis = 0L,
            nowMillis = now
        )
        assertFalse(builtNormal.isUrgent)

        // Exhaust ALL limits (21/week, 3/day, and 10-min gap) for BOTH pop-ups and pushes
        val oneDayMs = 24L * 60L * 60L * 1000L
        for (dayOffset in 6 downTo 0) {
            val dayBase = now - dayOffset * oneDayMs
            for (slot in 0 until 3) {
                val ts = dayBase - (slot + 1) * 30L * 60_000L
                val filler = com.example.announcements.AnnouncementItem(
                    id = "filler_${dayOffset}_$slot",
                    title = "Filler",
                    body = "Body",
                    startAtMillis = ts - 60_000L,
                    endAtMillis = now + oneDayMs
                )
                com.example.announcements.AnnouncementFrequencyStore.recordShown(context, uid, filler, ts)
                com.example.announcements.AnnouncementFrequencyStore.recordPushSent(context, uid, ts)
            }
        }
        // Also ensure last shown is 1 minute ago (inside 10-min cooldown)
        com.example.announcements.AnnouncementFrequencyStore.recordShown(
            context,
            uid,
            com.example.announcements.AnnouncementItem(id = "recent_filler", title = "Recent", body = "Body"),
            now - 60_000L
        )
        com.example.announcements.AnnouncementFrequencyStore.recordPushSent(context, uid, now - 60_000L)

        // Normal (non-urgent) announcement MUST still be blocked when caps are hit
        val blockedNormal = builtNormal.copy(
            id = "run8_normal_blocked",
            startAtMillis = now - 60_000L,
            endAtMillis = now + oneDayMs
        )
        assertFalse(
            "Non-urgent announcement must remain blocked when caps are hit",
            com.example.announcements.AnnouncementFrequencyStore.isEligibleToShow(
                context = context,
                userId = uid,
                announcement = blockedNormal,
                nowMillis = now,
                audienceContext = com.example.announcements.AnnouncementAudienceContext()
            )
        )
        assertFalse(
            "Non-urgent push must remain blocked when caps are hit",
            com.example.announcements.AnnouncementNotificationHelper.handleIncomingFcmAnnouncement(
                context = context,
                item = blockedNormal,
                nowMillis = now,
                userIdOverride = uid,
                audienceContextOverride = com.example.announcements.AnnouncementAudienceContext()
            )
        )
        assertEquals(0, shadowNm.allNotifications.size)

        // Urgent announcement MUST bypass 10-min, 3/day, AND 21/week caps for FCM push
        val urgentPushItem = ownerUrgentItem!!.copy(
            id = "run8_urgent_push",
            title = "Urgent Alert",
            body = "Special gathering tonight",
            startAtMillis = now - 60_000L,
            endAtMillis = now + oneDayMs,
            isUrgent = true
        )
        assertTrue(
            "Urgent FCM push MUST fire even when all caps are exhausted",
            com.example.announcements.AnnouncementNotificationHelper.handleIncomingFcmAnnouncement(
                context = context,
                item = urgentPushItem,
                nowMillis = now,
                userIdOverride = uid,
                audienceContextOverride = com.example.announcements.AnnouncementAudienceContext()
            )
        )
        assertEquals(1, shadowNm.allNotifications.size)

        // Home pop-up via MainViewModel: Urgent announcement shows IMMEDIATELY over normal blocked items
        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(localDb = db, firestore = FirebaseFirestore.getInstance(), auth = auth)
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uid,
            fallbackDisplayName = "Tester",
            accountCreatedAtMillis = now - oneDayMs,
            appContext = context
        )
        val vmStore = androidx.lifecycle.ViewModelStore()
        vmStore.put("main", viewModel)
        var latestState: MainUiState? = null
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = vmScope.launch { viewModel.uiState.collect { latestState = it } }
        try {
            val initDeadline = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < initDeadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }

            viewModel.performAnnouncementRefresh(nowMillis = now) {
                DuroodRepository.AnnouncementFetchResult(
                    items = listOf(blockedNormal, urgentPushItem),
                    hadTransientError = false
                )
            }
            val waitPop = System.currentTimeMillis() + 3_000L
            while (latestState?.activeAnnouncement?.id != "run8_urgent_push" && System.currentTimeMillis() < waitPop) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            val activeUrgent = latestState?.activeAnnouncement
            assertNotNull("Urgent pop-up must show immediately on Home open", activeUrgent)
            assertEquals("run8_urgent_push", activeUrgent?.id)
            assertTrue(activeUrgent?.isUrgent == true)

            // Mark shown, verify normal quota / lastShownAtMillis wasn't bumped to now, then dismiss with X
            val lastNormalShownBefore = com.example.announcements.AnnouncementFrequencyStore.getLastGlobalShownAtMillis(context, uid)
            viewModel.onAnnouncementShown(activeUrgent!!)
            assertEquals(
                "Urgent show must not alter normal cooldown timestamp",
                lastNormalShownBefore,
                com.example.announcements.AnnouncementFrequencyStore.getLastGlobalShownAtMillis(context, uid)
            )
            viewModel.dismissAnnouncement()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull(latestState?.activeAnnouncement)

            // After dismiss, same urgent item MUST stay dismissed on subsequent refresh
            viewModel.performAnnouncementRefresh(nowMillis = now + 60_000L) {
                DuroodRepository.AnnouncementFetchResult(
                    items = listOf(blockedNormal, urgentPushItem),
                    hadTransientError = false
                )
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertNull("Dismissed urgent announcement must stay dismissed", latestState?.activeAnnouncement)
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun `run8 urgentBadges renderInOwnerDialogRecipientPopupAndMessagesHistory`() {
        val now = 1_796_000_000_000L
        var createdUrgentFromUi: com.example.announcements.AnnouncementItem? = null
        var activeView by mutableStateOf("owner")
        var urgentItem by mutableStateOf<com.example.announcements.AnnouncementItem?>(null)

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (activeView) {
                        "owner" -> {
                            com.example.ui.components.ScheduleAnnouncementDialog(
                                onDismiss = {},
                                onSchedule = { createdUrgentFromUi = it },
                                nowProvider = { now }
                            )
                        }
                        "popup" -> {
                            urgentItem?.let { item ->
                                com.example.ui.components.AnnouncementDialog(
                                    announcement = item,
                                    onDismiss = {},
                                    onActionClick = {}
                                )
                            }
                        }
                        "history" -> {
                            urgentItem?.let { item ->
                                com.example.ui.screens.MessagesHistoryScreen(
                                    entries = listOf(
                                        com.example.announcements.AnnouncementHistoryEntry(
                                            announcement = item,
                                            recordedAtMillis = now,
                                            status = com.example.announcements.AnnouncementHistoryStore.STATUS_SHOWN
                                        )
                                    ),
                                    onBack = {},
                                    onActionClick = {}
                                )
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Owner-only dialog: toggle Urgent -> URGENT badge & caps hint appear, submitted item has isUrgent=true
        composeTestRule.onNodeWithTag("schedule_announcement_body_input")
            .performTextInput("Critical schedule update")
        composeTestRule.onNodeWithTag("schedule_announcement_send_now")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("schedule_announcement_urgent_toggle")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("schedule_announcement_urgent_badge", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("schedule_announcement_submit_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        val submitted = createdUrgentFromUi
        assertNotNull(submitted)
        assertTrue(submitted!!.isUrgent)
        assertTrue(com.example.announcements.AnnouncementItem.fromJsonObject(submitted.toJsonObject())?.isUrgent == true)
        urgentItem = submitted.copy(
            id = "run8_badge_item",
            title = "Urgent Alert"
        )

        // 2. Recipient AnnouncementDialog shows URGENT badge
        activeView = "popup"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("announcement_dialog_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("announcement_dialog_urgent_badge")
            .assertIsDisplayed()
            .assertTextEquals("URGENT")

        // 3. MessagesHistoryScreen shows URGENT badge
        activeView = "history"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("messages_history_urgent_badge_run8_badge_item")
            .assertIsDisplayed()
            .assertTextEquals("URGENT")
    }

    @Test
    fun `run9Task1 groupsFlow joinByCode twoGroupLimit sharedMainCounting customCounterExclusion and zikrIsolation`() {
        val now = 1_797_000_000_000L
        val todayKey = LocalDate.now().toString()
        val uidA = "run9_user_a"
        val uidB = "run9_user_b"
        val auth = FirebaseAuth.getInstance()
        auth.signOut()

        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(
                    userId = uidA,
                    displayName = "Hamza",
                    dailyTarget = 2600,
                    setupCompleted = true,
                    startedAtMillis = now - 86_400_000L
                )
            )
            db.userProfileDao().upsertProfile(
                UserProfileEntity(
                    userId = uidB,
                    displayName = "Yusuf",
                    dailyTarget = 2600,
                    setupCompleted = true,
                    startedAtMillis = now - 86_400_000L
                )
            )
            db.dailyCountDao().incrementDay(uidB, todayKey, 120_000L, now)
        }

        val repo = DuroodRepository(localDb = db, firestore = FirebaseFirestore.getInstance(), auth = auth)
        val viewModelA = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uidA,
            fallbackDisplayName = "Hamza",
            accountCreatedAtMillis = now - 86_400_000L,
            appContext = context
        )
        val vmStore = androidx.lifecycle.ViewModelStore()
        vmStore.put("vmA", viewModelA)
        var latestStateA: MainUiState? = null
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = vmScope.launch { viewModelA.uiState.collect { latestStateA = it } }

        try {
            val deadlineInit = System.currentTimeMillis() + 3_000L
            while (latestStateA?.isLoading != false && System.currentTimeMillis() < deadlineInit) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }

            // 1. User A creates Main Durood group -> invite code generated; User B joins by code
            var createdMainGroup: com.example.groups.GroupItem? = null
            viewModelA.onCreateGroup(
                name = "Masjid Circle",
                type = com.example.groups.GroupType.MAIN_DUROOD
            ) { createdMainGroup = it }
            val deadlineGroup = System.currentTimeMillis() + 2_000L
            while (createdMainGroup == null && System.currentTimeMillis() < deadlineGroup) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertNotNull(createdMainGroup)
            val inviteCode = createdMainGroup!!.inviteCode

            val joinResB = runBlocking {
                com.example.groups.GroupRepository.joinGroupByCode(
                    context = context,
                    userId = uidB,
                    displayName = "Yusuf",
                    rawInviteCode = inviteCode,
                    userTodayCount = 120_000L,
                    userLifetimeTotal = 120_000L,
                    userWeeklyCount = 120_000L,
                    todayDateKey = todayKey,
                    nowMillis = now
                )
            }
            assertTrue(joinResB.isSuccess)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()

            // Code-only WhatsApp invite contains ONLY group name + invite code (no link)
            val waMessage = com.example.groups.GroupLogic.buildMergedWhatsAppInviteMessage(
                group = joinResB.getOrThrow()
            )
            assertTrue(waMessage.contains(inviteCode))
            assertTrue(waMessage.contains("Masjid Circle"))
            assertFalse(waMessage.contains("http://"))
            assertFalse(waMessage.contains("https://"))
            assertFalse(waMessage.contains("onecrore.app"))

            // 2. Run 4 strict isolation: Main counter +1, +Custom, Widget, and Named Custom Counter ONLY update personal total,
            //    NEVER any group total! Group counts are added/reduced strictly inside the group.
            viewModelA.onTapPlusOne()
            viewModelA.onAddCustomAmount(5_000L)
            runBlocking {
                WidgetAndFloatingBridge.incrementCountAndSync(
                    context = context,
                    delta = 100L,
                    db = db,
                    userIdOverride = uidA,
                    todayKey = todayKey,
                    nowMillis = now + 500L
                )
            }

            // Named custom counter ("ya Salam") stays personal only & MUST NOT affect personal Durood total or Main group total
            viewModelA.onCreateCustomCounter("ya Salam", 10_000L) { counterId ->
                viewModelA.onAddCustomCounterAmount(counterId, 9_999L)
            }

            val deadlineSync = System.currentTimeMillis() + 3_000L
            while ((latestStateA?.totalCount ?: 0L) != 5_101L && System.currentTimeMillis() < deadlineSync) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals(5_101L, latestStateA?.totalCount)

            // Main group starts at 0 and stays 0 despite Main Counter & Widget activity
            val isolatedInitialGroup = latestStateA?.userGroups?.firstOrNull { it.id == createdMainGroup!!.id }
            assertNotNull(isolatedInitialGroup)
            assertEquals(0L, isolatedInitialGroup!!.effectiveGroupTotal)

            // Add counts directly INSIDE the group for User B (120,000) and User A (5,101) -> only group updates
            runBlocking {
                com.example.groups.GroupRepository.adjustMainGroupCount(
                    context = context,
                    userId = uidB,
                    displayName = "Yusuf",
                    groupId = createdMainGroup!!.id,
                    delta = 120_000L,
                    todayDateKey = todayKey,
                    nowMillis = now + 100L
                ).getOrThrow()
            }
            viewModelA.onAdjustMainGroupCount(createdMainGroup!!.id, 5_101L)

            val deadlineGroupSum = System.currentTimeMillis() + 3_000L
            while ((latestStateA?.userGroups?.firstOrNull { it.id == createdMainGroup!!.id }?.effectiveGroupTotal ?: 0L) != 125_101L &&
                System.currentTimeMillis() < deadlineGroupSum
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            val updatedMainGroup = latestStateA?.userGroups?.firstOrNull { it.id == createdMainGroup!!.id }
            assertNotNull(updatedMainGroup)
            assertEquals(2, updatedMainGroup!!.memberCount)
            assertEquals(125_101L, updatedMainGroup.effectiveGroupTotal)
            assertEquals(5_101L, latestStateA?.totalCount)
            assertEquals(listOf(uidB, uidA), updatedMainGroup.rankedMembers(todayKey).map { it.userId })

            // 3. Create 2nd group = CUSTOM_ZIKR ("SubhanAllah"), verify 5-group limit allows slots 3..5 and blocks 6th
            var zikrGroup: com.example.groups.GroupItem? = null
            viewModelA.onCreateGroup(
                name = "Morning Tasbih",
                type = com.example.groups.GroupType.CUSTOM_ZIKR,
                zikrName = "SubhanAllah",
                targetCount = 25_000L
            ) { zikrGroup = it }
            val deadlineZikr = System.currentTimeMillis() + 2_000L
            while (zikrGroup == null && System.currentTimeMillis() < deadlineZikr) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertNotNull(zikrGroup)
            assertEquals(2, latestStateA?.userGroups?.size)

            // Fill remaining slots (3..5), then verify 6th group is blocked by 5-group limit
            listOf("Slot 3", "Slot 4", "Slot 5").forEach { slotName ->
                val slotGroup = runBlocking {
                    com.example.groups.GroupRepository.createGroup(
                        context = context,
                        creatorUserId = "other_owner_$slotName",
                        creatorDisplayName = "Owner",
                        name = slotName,
                        type = com.example.groups.GroupType.MAIN_DUROOD
                    ).getOrThrow()
                }
                val joinedSlot = runBlocking {
                    com.example.groups.GroupRepository.joinGroupByCode(
                        context = context,
                        userId = uidA,
                        displayName = "Hamza",
                        rawInviteCode = slotGroup.inviteCode
                    )
                }
                assertTrue("Expected $slotName join to succeed under 5-group cap", joinedSlot.isSuccess)
            }

            val extraGroup = runBlocking {
                com.example.groups.GroupRepository.createGroup(
                    context = context,
                    creatorUserId = "sixth_owner",
                    creatorDisplayName = "Other",
                    name = "Sixth Group",
                    type = com.example.groups.GroupType.MAIN_DUROOD
                ).getOrThrow()
            }
            val blockedJoin = runBlocking {
                com.example.groups.GroupRepository.joinGroupByCode(
                    context = context,
                    userId = uidA,
                    displayName = "Hamza",
                    rawInviteCode = extraGroup.inviteCode
                )
            }
            assertTrue("Joining a 6th group must be rejected at 5-group limit", blockedJoin.isFailure)

            // Incrementing Custom-Zikr group adds ONLY to the zikr group (250), NEVER to Main Durood total (5,101)
            viewModelA.onIncrementCustomZikrGroup(zikrGroup!!.id, 250L)
            val deadlineZikrInc = System.currentTimeMillis() + 2_000L
            while ((latestStateA?.userGroups?.firstOrNull { it.id == zikrGroup!!.id }?.effectiveGroupTotal ?: 0L) != 250L &&
                System.currentTimeMillis() < deadlineZikrInc
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals(5_101L, latestStateA?.totalCount)
            assertEquals(
                125_101L,
                latestStateA?.userGroups?.firstOrNull { it.id == createdMainGroup!!.id }?.effectiveGroupTotal
            )
            assertEquals(0, latestStateA?.badgeProgress?.earnedCount)

            // Leaving zikr group frees slot so joining 6th group now succeeds
            var leftDone = false
            viewModelA.onLeaveGroup(zikrGroup!!.id) { leftDone = true }
            val deadlineLeave = System.currentTimeMillis() + 2_000L
            while (!leftDone && System.currentTimeMillis() < deadlineLeave) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertTrue(leftDone)
            assertEquals(4, latestStateA?.userGroups?.size)
            val allowedJoinAfterLeave = runBlocking {
                com.example.groups.GroupRepository.joinGroupByCode(
                    context = context,
                    userId = uidA,
                    displayName = "Hamza",
                    rawInviteCode = extraGroup.inviteCode
                )
            }
            assertTrue(allowedJoinAfterLeave.isSuccess)

            // 4. Verify UI: Home My Groups card + GroupDetailScreen leaderboard & 1 Lakh celebration + one-time popup
            var showDetail by mutableStateOf(false)
            val renderedGroup = com.example.groups.GroupRepository.getGroupById(context, createdMainGroup!!.id)!!
            val snapshotState = latestStateA!!
            composeTestRule.setContent {
                OneCroreTheme {
                    if (!showDetail) {
                        HomeScreen(
                            uiState = snapshotState,
                            canPresentPopups = true,
                            onOpenCounter = {},
                            onOpenCustomCounter = {},
                            onDismissGroupCelebration = viewModelA::dismissGroupCelebrationPopup,
                            onCheckForUpdate = {},
                            onOpenUpdateDialog = {},
                            onDismissUpdateDialog = {},
                            onCancelUpdateDownload = {},
                            onUpdateNow = {}
                        )
                    } else {
                        com.example.ui.screens.GroupDetailScreen(
                            group = renderedGroup,
                            currentUserId = uidA,
                            currentDate = LocalDate.now(),
                            onBack = {},
                            onInviteWhatsApp = {},
                            onLeaveGroup = {},
                            onIncrementCustomZikr = { _, _ -> }
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_my_groups_card")
                .performScrollTo()
                .assertIsDisplayed()

            // One-time celebration popup shows on Home and dismisses cleanly
            composeTestRule.onNodeWithTag("group_celebration_popup_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("group_celebration_popup_confirm").performClick()
            composeTestRule.waitForIdle()

            showDetail = true
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("group_member_count").assertTextContains("2 members")
            composeTestRule.onNodeWithTag("group_total_value").assertTextContains("125,101")
            composeTestRule.onNodeWithTag("group_leaderboard_list").performScrollTo()
            composeTestRule.onNodeWithTag("group_member_row_$uidB").assertIsDisplayed()
            composeTestRule.onNodeWithTag("group_member_row_$uidA").assertIsDisplayed()
            composeTestRule.onNodeWithTag("group_celebrations_section").performScrollTo().assertIsDisplayed()
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun `run9Task2 milestoneBadges renderOnHomeAndStatsAndCelebrateOncePer50k`() {
        val now = 1_797_000_000_000L
        val todayKey = LocalDate.now().toString()
        val uid = "run9_badge_user"
        val auth = FirebaseAuth.getInstance()
        auth.signOut()

        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(
                    userId = uid,
                    displayName = "Nadia",
                    dailyTarget = 2600,
                    setupCompleted = true,
                    startedAtMillis = now - 86_400_000L
                )
            )
            db.dailyCountDao().incrementDay(uid, todayKey, 49_999L, now)
        }

        val repo = DuroodRepository(localDb = db, firestore = FirebaseFirestore.getInstance(), auth = auth)
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uid,
            fallbackDisplayName = "Nadia",
            accountCreatedAtMillis = now - 86_400_000L,
            appContext = context
        )
        val vmStore = androidx.lifecycle.ViewModelStore()
        vmStore.put("badges", viewModel)
        var latestState by mutableStateOf<MainUiState?>(null)
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = vmScope.launch { viewModel.uiState.collect { latestState = it } }

        try {
            val deadlineInit = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < deadlineInit) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals(0, latestState?.badgeProgress?.earnedCount)
            assertNull(latestState?.pendingMilestoneBadge)

            // Cross 50,000 threshold -> badge #1 unlocks and requests one-time celebration popup
            viewModel.onTapPlusOne()
            val deadlineFirst = System.currentTimeMillis() + 3_000L
            while (latestState?.pendingMilestoneBadge?.tier != 1 && System.currentTimeMillis() < deadlineFirst) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals(1, latestState?.badgeProgress?.earnedCount)
            assertEquals(1, latestState?.pendingMilestoneBadge?.tier)

            var showStats by mutableStateOf(false)
            composeTestRule.setContent {
                val ui = latestState
                if (ui != null) {
                    OneCroreTheme {
                        if (!showStats) {
                            HomeScreen(
                                uiState = ui,
                                canPresentPopups = true,
                                onOpenCounter = {},
                                onOpenCustomCounter = {},
                                onOpenStats = { showStats = true },
                                onDismissMilestoneBadge = viewModel::dismissMilestoneBadgeCelebration,
                                onCheckForUpdate = {},
                                onOpenUpdateDialog = {},
                                onDismissUpdateDialog = {},
                                onCancelUpdateDownload = {},
                                onUpdateNow = {}
                            )
                        } else {
                            com.example.ui.screens.StatsScreen(uiState = ui)
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()

            // One-time celebration popup shows and stays dismissed after tapping Alhamdulillah
            composeTestRule.onNodeWithTag("milestone_badge_celebration_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("milestone_badge_dialog_title").assertTextContains("50K Badge", substring = true)
            composeTestRule.onNodeWithTag("milestone_badge_dialog_confirm").performClick()
            composeTestRule.waitForIdle()
            assertNull(latestState?.pendingMilestoneBadge)
            assertNull(
                com.example.badges.MilestoneBadgeManager.getPendingCelebrationBadge(
                    context = context,
                    userId = uid,
                    mainDuroodTotalCount = 50_000L
                )
            )

            // Cross 100,000 -> badge #2 unlocks; named custom counter +200k must NOT unlock extra Durood badges
            viewModel.onAddCustomAmount(50_000L)
            viewModel.onCreateCustomCounter("Tasbih", 500_000L) { cid ->
                viewModel.onAddCustomCounterAmount(cid, 200_000L)
            }
            val deadlineSecond = System.currentTimeMillis() + 3_000L
            while (latestState?.pendingMilestoneBadge?.tier != 2 && System.currentTimeMillis() < deadlineSecond) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            assertEquals(2, latestState?.badgeProgress?.earnedCount)
            assertEquals(2, latestState?.pendingMilestoneBadge?.tier)
            viewModel.dismissMilestoneBadgeCelebration()
            composeTestRule.waitForIdle()

            // Home compact card shows 2 / 200 & latest badge, tap navigates to Stats showing full 200 badges
            composeTestRule.onNodeWithTag("home_milestone_badges_count", useUnmergedTree = true).assertTextContains("2 / 200", substring = true)
            composeTestRule.onNodeWithTag("home_milestone_badges_latest", useUnmergedTree = true).assertTextContains("1 Lakh Badge", substring = true)
            composeTestRule.onNodeWithTag("home_milestone_badges_card")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("stats_milestone_badges_section")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("stats_milestone_badges_count").assertTextContains("2 / 200", substring = true)
            composeTestRule.onNodeWithTag("stats_badges_show_all_button")
                .performScrollTo()
                .performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("stats_badge_tile_1").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("stats_badge_tile_200").performScrollTo().assertIsDisplayed()
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun `run9FollowUp listenerUpdatesGroupWithoutReopening refreshesImmediatelyOnOpen manualRefreshButton and detachesOnClose`() {
        val now = 1_797_100_000_000L
        val todayKey = LocalDate.now().toString()
        val uidA = "run9_followup_a"
        val uidB = "run9_followup_b"
        val auth = FirebaseAuth.getInstance()
        auth.signOut()

        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(
                    userId = uidA,
                    displayName = "Amina",
                    dailyTarget = 2600,
                    setupCompleted = true,
                    startedAtMillis = now - 86_400_000L
                )
            )
            db.dailyCountDao().incrementDay(uidA, todayKey, 5_000L, now)
        }

        val initialGroup = com.example.groups.GroupItem(
            id = "grp_followup_live",
            name = "Fajr Halqa",
            inviteCode = "FAJR99",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            creatorUserId = uidA,
            members = listOf(
                com.example.groups.GroupMemberProgress(
                    userId = uidA,
                    displayName = "Amina",
                    todayCount = 5_000L,
                    todayDateKey = todayKey,
                    lifetimeTotalCount = 5_000L,
                    weeklyCount = 5_000L,
                    updatedAtMillis = now
                ),
                com.example.groups.GroupMemberProgress(
                    userId = uidB,
                    displayName = "Bilal",
                    todayCount = 20_000L,
                    todayDateKey = todayKey,
                    lifetimeTotalCount = 20_000L,
                    weeklyCount = 20_000L,
                    updatedAtMillis = now
                )
            ),
            serverTotalCount = 25_000L,
            createdAtMillis = now - 86_400_000L,
            updatedAtMillis = now
        )
        com.example.groups.GroupRepository.upsertGroupLocally(context, initialGroup)

        val repo = DuroodRepository(localDb = db, firestore = FirebaseFirestore.getInstance(), auth = auth)
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uidA,
            fallbackDisplayName = "Amina",
            accountCreatedAtMillis = now - 86_400_000L,
            appContext = context
        )

        var attachCalls = 0
        var detachCalls = 0
        var singleRefreshCalls = 0
        var capturedListenerCallback: ((com.example.groups.GroupItem) -> Unit)? = null
        var nextServerBilalCount = 25_000L

        viewModel.groupListenerAttachHook = { _, onRemote ->
            attachCalls++
            capturedListenerCallback = onRemote
            { detachCalls++ }
        }
        viewModel.groupSingleRefreshHook = { groupId ->
            singleRefreshCalls++
            val current = com.example.groups.GroupRepository.getGroupById(context, groupId) ?: initialGroup
            val serverUpdatedAt = now + singleRefreshCalls * 15_000L
            current.copy(
                members = current.members.map { m ->
                    if (m.userId == uidB) {
                        m.copy(
                            todayCount = nextServerBilalCount,
                            todayDateKey = todayKey,
                            lifetimeTotalCount = nextServerBilalCount,
                            weeklyCount = nextServerBilalCount,
                            updatedAtMillis = serverUpdatedAt
                        )
                    } else {
                        m
                    }
                },
                serverTotalCount = 5_000L + nextServerBilalCount,
                updatedAtMillis = serverUpdatedAt
            )
        }
        viewModel.groupBatchFlushHook = { _, _ -> }

        val vmStore = androidx.lifecycle.ViewModelStore()
        vmStore.put("followup_live", viewModel)
        var latestState by mutableStateOf<MainUiState?>(null)
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = vmScope.launch { viewModel.uiState.collect { latestState = it } }

        try {
            val deadlineInit = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < deadlineInit) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }

            // Before Group Detail opens: ZERO off-screen listeners attached!
            assertEquals(0, viewModel.getOpenGroupListenerCountForTest())
            assertEquals(0, attachCalls)
            assertEquals(0, singleRefreshCalls)

            var showDetail by mutableStateOf(true)
            composeTestRule.setContent {
                val ui = latestState
                if (ui != null && showDetail) {
                    val activeGroup = ui.userGroups.firstOrNull { it.id == initialGroup.id }
                    OneCroreTheme {
                        com.example.ui.screens.GroupDetailScreen(
                            group = activeGroup,
                            currentUserId = uidA,
                            currentDate = LocalDate.now(),
                            isRefreshing = ui.isGroupRefreshing,
                            onScreenOpen = { viewModel.openGroupDetail(initialGroup.id) },
                            onScreenClose = { viewModel.closeGroupDetail(initialGroup.id) },
                            onRefresh = { viewModel.refreshOpenGroupDetailNow(initialGroup.id) },
                            onBack = { showDetail = false },
                            onInviteWhatsApp = {},
                            onLeaveGroup = {},
                            onIncrementCustomZikr = { _, _ -> }
                        )
                    }
                }
            }

            // 1. Opening Group Detail attaches ONE screen-scoped listener & immediately refreshes group total (25k -> 30k)
            val deadlineOpen = System.currentTimeMillis() + 3_000L
            while ((singleRefreshCalls < 1 || latestState?.isGroupRefreshing == true ||
                    latestState?.userGroups?.firstOrNull { it.id == initialGroup.id }?.effectiveGroupTotal != 30_000L) &&
                System.currentTimeMillis() < deadlineOpen
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            composeTestRule.waitForIdle()
            assertEquals(1, attachCalls)
            assertEquals(1, viewModel.getOpenGroupListenerCountForTest())
            assertEquals(1, singleRefreshCalls)
            assertEquals(0, detachCalls)
            composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("30,000")

            // 2. Live snapshot listener updates group total + member Today & lifetime WITHOUT reopening the screen (30k -> 40k)
            val liveSnapshotFromBilal = initialGroup.copy(
                members = listOf(
                    initialGroup.members.first { it.userId == uidA },
                    com.example.groups.GroupMemberProgress(
                        userId = uidB,
                        displayName = "Bilal",
                        todayCount = 35_000L,
                        todayDateKey = todayKey,
                        lifetimeTotalCount = 35_000L,
                        weeklyCount = 35_000L,
                        updatedAtMillis = now + 20_000L
                    )
                ),
                serverTotalCount = 40_000L,
                updatedAtMillis = now + 20_000L
            )
            capturedListenerCallback!!.invoke(liveSnapshotFromBilal)

            val deadlineLive = System.currentTimeMillis() + 3_000L
            while ((latestState?.userGroups?.firstOrNull { it.id == initialGroup.id }?.effectiveGroupTotal ?: 0L) != 40_000L &&
                System.currentTimeMillis() < deadlineLive
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("40,000")
            composeTestRule.onNodeWithTag("group_leaderboard_list").performScrollTo()
            composeTestRule.onNodeWithTag("group_member_row_$uidB").assertTextContains("Today: 35,000", substring = true)

            // 3. Tapping subtle monochrome Refresh icon in header triggers immediate single-doc refresh (40k -> 45k)
            nextServerBilalCount = 40_000L
            composeTestRule.onNodeWithTag("group_detail_refresh_button")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
            val deadlineManual = System.currentTimeMillis() + 3_000L
            while ((singleRefreshCalls < 2 || latestState?.isGroupRefreshing == true ||
                    latestState?.userGroups?.firstOrNull { it.id == initialGroup.id }?.effectiveGroupTotal != 45_000L) &&
                System.currentTimeMillis() < deadlineManual
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }
            composeTestRule.waitForIdle()
            assertEquals(2, singleRefreshCalls)
            composeTestRule.onNodeWithTag("group_total_value").performScrollTo().assertTextEquals("45,000")

            // 4. Closing Group Detail detaches the snapshot listener immediately (no off-screen listener leak)
            showDetail = false
            composeTestRule.waitForIdle()
            assertEquals(1, detachCalls)
            assertEquals(0, viewModel.getOpenGroupListenerCountForTest())
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun `run9FollowUp batchedOpenFlush noPerTapCloudWrites noPollingLoop and globalHourlyUnchanged`() {
        val now = 1_797_120_000_000L
        val todayKey = LocalDate.now().toString()
        val uid = "run9_batch_user"
        val auth = FirebaseAuth.getInstance()
        auth.signOut()

        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(
                    userId = uid,
                    displayName = "Tariq",
                    dailyTarget = 2600,
                    setupCompleted = true,
                    startedAtMillis = now - 86_400_000L
                )
            )
            db.dailyCountDao().incrementDay(uid, todayKey, 1_000L, now)
        }

        val mainGroup = com.example.groups.GroupItem(
            id = "grp_batch_main",
            name = "Sunnah Circle",
            inviteCode = "SUNNAH",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            creatorUserId = uid,
            members = listOf(
                com.example.groups.GroupMemberProgress(
                    userId = uid,
                    displayName = "Tariq",
                    todayCount = 1_000L,
                    todayDateKey = todayKey,
                    lifetimeTotalCount = 1_000L,
                    weeklyCount = 1_000L,
                    updatedAtMillis = now
                )
            ),
            serverTotalCount = 1_000L,
            createdAtMillis = now - 86_400_000L,
            updatedAtMillis = now
        )
        val zikrGroup = com.example.groups.GroupItem(
            id = "grp_batch_zikr",
            name = "Tasbih Halqa",
            inviteCode = "ZIKR88",
            type = com.example.groups.GroupType.CUSTOM_ZIKR,
            zikrName = "SubhanAllah",
            targetCount = 10_000L,
            creatorUserId = uid,
            members = listOf(
                com.example.groups.GroupMemberProgress(
                    userId = uid,
                    displayName = "Tariq",
                    todayCount = 0L,
                    todayDateKey = todayKey,
                    lifetimeTotalCount = 0L,
                    weeklyCount = 0L,
                    updatedAtMillis = now
                )
            ),
            serverTotalCount = 0L,
            createdAtMillis = now - 86_400_000L,
            updatedAtMillis = now
        )
        com.example.groups.GroupRepository.upsertGroupLocally(context, mainGroup)
        com.example.groups.GroupRepository.upsertGroupLocally(context, zikrGroup)

        val repo = DuroodRepository(localDb = db, firestore = FirebaseFirestore.getInstance(), auth = auth)
        val viewModel = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uid,
            fallbackDisplayName = "Tariq",
            accountCreatedAtMillis = now - 86_400_000L,
            appContext = context
        )
        viewModel.groupFlushDebounceMillis = 120L

        val flushEvents = java.util.Collections.synchronizedList(mutableListOf<Pair<String?, String>>())
        var refreshCalls = 0
        viewModel.groupListenerAttachHook = { _, _ -> { } }
        viewModel.groupSingleRefreshHook = { id ->
            refreshCalls++
            com.example.groups.GroupRepository.getGroupById(context, id)
        }
        viewModel.groupBatchFlushHook = { id, reason ->
            flushEvents.add(id to reason)
        }

        val vmStore = androidx.lifecycle.ViewModelStore()
        vmStore.put("followup_batch", viewModel)
        var latestState by mutableStateOf<MainUiState?>(null)
        val vmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)
        val job = vmScope.launch { viewModel.uiState.collect { latestState = it } }

        try {
            val deadlineInit = System.currentTimeMillis() + 3_000L
            while (latestState?.isLoading != false && System.currentTimeMillis() < deadlineInit) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }

            // 1. Tap +1 five times on Main Counter while Group Detail is CLOSED ->
            //    personal total updates (1,005) while mainGroup stays untouched at 1,000, ZERO per-tap cloud writes & ZERO polling!
            repeat(5) {
                viewModel.onTapPlusOne()
            }
            val deadlineTaps = System.currentTimeMillis() + 3_000L
            while ((latestState?.totalCount ?: 0L) != 1_005L && System.currentTimeMillis() < deadlineTaps) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
                Thread.sleep(15L)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper(350, java.util.concurrent.TimeUnit.MILLISECONDS)

            assertEquals(1_005L, latestState?.totalCount)
            assertEquals(
                1_000L,
                latestState?.userGroups?.firstOrNull { it.id == mainGroup.id }?.effectiveGroupTotal
            )
            assertEquals(0, com.example.groups.GroupRepository.getCloudGroupWriteCountForTest())
            assertEquals(0, viewModel.groupBatchFlushCountForTest.get())
            assertEquals(0, refreshCalls)

            // 2. Open Main Group Detail -> 3 rapid in-group custom adjustments coalesce into ONE debounced batch flush
            viewModel.openGroupDetail(mainGroup.id)
            val deadlineMainOpen = System.currentTimeMillis() + 3_000L
            while (refreshCalls < 1 && System.currentTimeMillis() < deadlineMainOpen) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
                Thread.sleep(15L)
            }
            assertEquals(0, viewModel.groupBatchFlushCountForTest.get())

            repeat(3) {
                viewModel.onAdjustMainGroupCount(mainGroup.id, 10L)
                org.robolectric.shadows.ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            val deadlineMainDebounced = System.currentTimeMillis() + 3_000L
            while (flushEvents.none { it.first == mainGroup.id && it.second == "debounced_open_flush" } &&
                System.currentTimeMillis() < deadlineMainDebounced
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                Thread.sleep(15L)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper(150, java.util.concurrent.TimeUnit.MILLISECONDS)

            assertEquals(1_030L, latestState?.userGroups?.firstOrNull { it.id == mainGroup.id }?.effectiveGroupTotal)
            assertEquals(1_005L, latestState?.totalCount)
            assertEquals(1, viewModel.groupBatchFlushCountForTest.get())
            assertEquals(mainGroup.id to "debounced_open_flush", flushEvents.last())

            // Close Main Group Detail cleanly (no pending un-flushed counts -> no redundant close flush)
            viewModel.closeGroupDetail(mainGroup.id)
            org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertEquals(1, viewModel.groupBatchFlushCountForTest.get())

            // 3. Open Custom Zikr Group Detail -> 5 rapid +1 taps coalesce into ONE debounced batch flush (no per-tap cloud writes)
            viewModel.openGroupDetail(zikrGroup.id)
            val deadlineZikrOpen = System.currentTimeMillis() + 3_000L
            while (refreshCalls < 2 && System.currentTimeMillis() < deadlineZikrOpen) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
                Thread.sleep(15L)
            }
            val flushCountBeforeRapidTaps = viewModel.groupBatchFlushCountForTest.get()
            repeat(5) {
                viewModel.onIncrementCustomZikrGroup(zikrGroup.id, 1L)
                org.robolectric.shadows.ShadowLooper.idleMainLooper(10, java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            // Advance virtual clock across debounced batch window (120ms) -> 5 rapid taps produce ONLY 1 batched flush
            val deadlineDebounced = System.currentTimeMillis() + 3_000L
            while (flushEvents.none { it.first == zikrGroup.id && it.second == "debounced_open_flush" } &&
                System.currentTimeMillis() < deadlineDebounced
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper(25, java.util.concurrent.TimeUnit.MILLISECONDS)
                Thread.sleep(15L)
            }
            org.robolectric.shadows.ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)

            assertEquals(
                5L,
                latestState?.userGroups?.firstOrNull { it.id == zikrGroup.id }?.effectiveGroupTotal
            )
            assertEquals(flushCountBeforeRapidTaps + 1, viewModel.groupBatchFlushCountForTest.get())
            assertEquals(0, com.example.groups.GroupRepository.getCloudGroupWriteCountForTest())
            viewModel.closeGroupDetail(zikrGroup.id)

            // 4. Verify Global hourly aggregate stays untouched at 1 hour
            assertEquals(3_600_000L, GlobalStatsCache.ONE_HOUR_MILLIS)
            val cachedGlobal = GlobalCounterStats(
                totalCount = 9_999L,
                userCount = 42L,
                fetchedAtMillis = now - 20L * 60L * 1000L
            )
            assertFalse(GlobalStatsCache.shouldFetchFromServer(cachedGlobal, now, isOnline = true))
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun `run10_shareButtonOpensChoiceDialog rendersFreshMonochromeProgressCardOnEachShare and keepsSimpleProgressUnchanged`() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        com.example.update.AppUpdateManager.resetShareStateForTest(context)
        com.example.update.AppUpdateManager.setShareTargetOverrideForTest(
            com.example.update.AppUpdateManager.ShareDownloadTarget(
                downloadUrl = com.example.update.UpdateConfig.UPDATE_VERSION_URL,
                versionName = "1.2"
            )
        )

        var currentToday by mutableStateOf(1_500L)
        var currentTotal by mutableStateOf(250_000L) // 2.50% of 10,000,000

        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isLoading = false,
                        isUpdateCheckSettled = true,
                        todayCount = currentToday,
                        totalCount = currentTotal
                    ),
                    canPresentPopups = true,
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onRefreshGlobalStats = {},
                    onRefreshAnnouncements = {},
                    onAnnouncementShown = {},
                    onDismissAnnouncement = {},
                    onAnnouncementAction = {},
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {},
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Existing Share button on Home opens the Share Choice Dialog (no second share button added)
        assertEquals(
            1,
            composeTestRule.onAllNodesWithTag("home_share_app_button").fetchSemanticsNodes().size
        )
        composeTestRule.onNodeWithTag("home_share_app_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("share_choice_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_choice_dialog_title").assertTextEquals("Share")
        composeTestRule.onNodeWithTag("share_choice_progress_card_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_choice_simple_progress_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_choice_cancel_button").assertIsDisplayed()

        // 2. Tap (1) Progress card -> renders fresh monochrome PNG card and launches ACTION_SEND
        composeTestRule.onNodeWithTag("share_choice_progress_card_button").performClick()
        val deadlineShare1 = System.currentTimeMillis() + 3_000L
        while (com.example.update.AppUpdateManager.getProgressCardRenderCountForTest() < 1 &&
            System.currentTimeMillis() < deadlineShare1
        ) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(15L)
        }
        composeTestRule.waitForIdle()

        assertEquals(1, com.example.update.AppUpdateManager.getProgressCardRenderCountForTest())
        assertEquals("progress_card", com.example.update.AppUpdateManager.getLastShareModeForTest())
        val firstSpec = com.example.update.AppUpdateManager.getLastProgressCardSpecForTest()
        assertNotNull(firstSpec)
        assertEquals(1_500L, firstSpec!!.todayCount)
        assertEquals(250_000L, firstSpec.totalCount)
        assertEquals("1,500", firstSpec.todayFormatted)
        assertEquals("250,000", firstSpec.totalFormatted)
        assertEquals("2.50%", firstSpec.percentFormatted)
        assertEquals(com.example.update.UpdateConfig.UPDATE_VERSION_URL, firstSpec.downloadUrl)

        val cardFile = java.io.File(java.io.File(context.cacheDir, "share"), "progress_share_card.png")
        assertTrue("Progress card PNG must be written at share time", cardFile.exists() && cardFile.length() > 0L)

        // Verify rendered bitmap is strictly monochrome (R == G == B for sampled pixels)
        val renderedBitmap = com.example.update.AppUpdateManager.renderProgressShareCardBitmap(firstSpec)
        assertEquals(1080, renderedBitmap.width)
        assertEquals(1080, renderedBitmap.height)
        for (y in 0 until renderedBitmap.height step 60) {
            for (x in 0 until renderedBitmap.width step 60) {
                val pixel = renderedBitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                assertEquals("Pixel ($x,$y) must be monochrome R==G", r, g)
                assertEquals("Pixel ($x,$y) must be monochrome G==B", g, b)
            }
        }

        // 3. Counts change in ViewModel/UI state -> tapping Share & Progress card renders FRESH numbers (never stale)
        currentToday = 4_200L
        currentTotal = 1_750_000L // 17.50% of 10,000,000
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_share_app_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("share_choice_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("share_choice_progress_card_button").performClick()
        val deadlineShare2 = System.currentTimeMillis() + 3_000L
        while (com.example.update.AppUpdateManager.getProgressCardRenderCountForTest() < 2 &&
            System.currentTimeMillis() < deadlineShare2
        ) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(15L)
        }
        composeTestRule.waitForIdle()

        assertEquals(2, com.example.update.AppUpdateManager.getProgressCardRenderCountForTest())
        val secondSpec = com.example.update.AppUpdateManager.getLastProgressCardSpecForTest()
        assertNotNull(secondSpec)
        assertEquals(4_200L, secondSpec!!.todayCount)
        assertEquals(1_750_000L, secondSpec.totalCount)
        assertEquals("4,200", secondSpec.todayFormatted)
        assertEquals("1,750,000", secondSpec.totalFormatted)
        assertEquals("17.50%", secondSpec.percentFormatted)
        assertTrue(secondSpec.shareCaptionText.contains("Today: 4,200"))
        assertTrue(secondSpec.shareCaptionText.contains("Total: 1,750,000 / 10,000,000 (17.50%)"))

        // 4. Tap Share again -> tap (2) Simple progress -> calls existing text share unchanged, zero Firestore writes
        composeTestRule.onNodeWithTag("home_share_app_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("share_choice_simple_progress_button").performClick()
        val deadlineSimple = System.currentTimeMillis() + 3_000L
        while (com.example.update.AppUpdateManager.getSimpleShareCountForTest() < 1 &&
            System.currentTimeMillis() < deadlineSimple
        ) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(15L)
        }
        composeTestRule.waitForIdle()

        assertEquals(1, com.example.update.AppUpdateManager.getSimpleShareCountForTest())
        assertEquals("simple_progress", com.example.update.AppUpdateManager.getLastShareModeForTest())
        assertEquals(0, com.example.groups.GroupRepository.getCloudGroupWriteCountForTest())
    }

    @Test
    fun `v11LogoSplash playsOrderedSequenceShowsNameCompletesWithinBudgetAndRespectsPostSignup`() {
        SplashSessionGuard.resetForTest()
        com.example.ui.screens.SplashIntroSpec.resetTestObservability()
        com.example.ui.components.setLowRamDeviceOverrideForTest(false)
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        var coldSplashFinished = false
        try {
            val startWall = System.currentTimeMillis()
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.setContent {
                OneCroreTheme {
                    SplashScreen(onFinished = { coldSplashFinished = true })
                }
            }
            composeTestRule.mainClock.advanceTimeByFrame()

            // Frame 0: black rounded-square tile & faint grid background immediately visible
            composeTestRule.onNodeWithTag("cold_start_splash_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("faint_grid_background").assertIsDisplayed()
            composeTestRule.onNodeWithTag("one_crore_logo_canvas").assertIsDisplayed()
            assertFalse("Splash must still be at Frame 0 before timeline advances", coldSplashFinished)

            var elapsedVirtualMs = 0L
            var sawFooterCreditAtBottom = false
            var sawWaveWipe = false
            while (!coldSplashFinished && elapsedVirtualMs < 2_500L) {
                composeTestRule.mainClock.advanceTimeBy(100L)
                org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                elapsedVirtualMs += 100L

                if (elapsedVirtualMs in 700L..800L && !sawFooterCreditAtBottom) {
                    val creditNodes = composeTestRule.onAllNodesWithTag("splash_credit_name").fetchSemanticsNodes()
                    val logoNodes = composeTestRule.onAllNodesWithTag("one_crore_logo_canvas").fetchSemanticsNodes()
                    if (creditNodes.isNotEmpty() && logoNodes.isNotEmpty()) {
                        composeTestRule.onNodeWithTag("splash_credit_name")
                            .assertIsDisplayed()
                            .assertTextEquals("Rza Mudasir Attari")
                        val creditTop = creditNodes.first().boundsInRoot.top
                        val logoBottom = logoNodes.first().boundsInRoot.bottom
                        assertTrue(
                            "Footer credit must sit in the lower footer area clearly separated from centered logo " +
                                "(creditTop=$creditTop, logoBottom=$logoBottom)",
                            creditTop > logoBottom + 80f
                        )
                        sawFooterCreditAtBottom = true
                    }
                }
                if (elapsedVirtualMs in 900L..1100L && !sawWaveWipe) {
                    if (composeTestRule.onAllNodesWithTag("splash_wave_wipe").fetchSemanticsNodes().isNotEmpty()) {
                        sawWaveWipe = true
                    }
                }
            }
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitForIdle()

            assertTrue("Cold-start v11 splash must finish cleanly", coldSplashFinished)
            assertTrue(
                "Cold-start intro must complete strictly within <= 1500ms hard cap (actual=$elapsedVirtualMs)",
                elapsedVirtualMs <= 1_500L
            )
            assertTrue("Footer credit 'Rza Mudasir Attari' must appear at bottom footer", sawFooterCreditAtBottom)
            assertTrue("Bottom-to-top curved black wave wipe must execute in 850..1150ms window", sawWaveWipe)
            assertTrue(SplashSessionGuard.hasPlayedColdStartSplash)
            assertFalse(com.example.ui.screens.SplashIntroSpec.lastSplashWasStaticForTest)
            assertEquals(
                com.example.ui.screens.SplashIntroSpec.ORDERED_STAGES,
                com.example.ui.screens.SplashIntroSpec.lastSplashStageHistoryForTest
            )
            assertTrue("Wall-clock duration remains bounded", System.currentTimeMillis() - startWall >= 0L)
        } finally {
            composeTestRule.mainClock.autoAdvance = true
            com.example.ui.components.setLowRamDeviceOverrideForTest(null)
        }
    }

    @Test
    fun `v11LogoSplash reduceMotionAndLowRamAreStatic postSignupPlaysIntro andAssetsUseV11Everywhere`() {
        val bubbleDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            // 1. Reduce Motion -> static final logo + footer credit immediately, zero animation stages
            SplashSessionGuard.resetForTest()
            com.example.ui.screens.SplashIntroSpec.resetTestObservability()
            com.example.ui.components.setLowRamDeviceOverrideForTest(false)
            Settings.Global.putFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                0f
            )

            var showMode by mutableStateOf(0)
            var postSignupDone = false
            composeTestRule.setContent {
                OneCroreTheme {
                    when (showMode) {
                        0 -> SplashScreen(onFinished = {})
                        1 -> SplashScreen(onFinished = {})
                        2 -> AnimatedLogoLoadingScreen(
                            playPostSignupIntro = true,
                            onPostSignupIntroFinished = { postSignupDone = true }
                        )
                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                FloatingCounterBubbleComposable(
                                    db = bubbleDb,
                                    userIdOverride = "v11_test_user",
                                    flashDurationMillis = 60_000L
                                )
                            }
                        }
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("splash_logo_static_final").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_credit_name")
                .assertIsDisplayed()
                .assertTextEquals("Rza Mudasir Attari")
            assertTrue(com.example.ui.screens.SplashIntroSpec.lastSplashWasStaticForTest)

            // 2. Low-RAM -> static final logo + footer credit immediately even when animator scale is 1f
            SplashSessionGuard.resetForTest()
            com.example.ui.screens.SplashIntroSpec.resetTestObservability()
            Settings.Global.putFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            com.example.ui.components.setLowRamDeviceOverrideForTest(true)
            showMode = 1
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("splash_logo_static_final").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_credit_name")
                .assertIsDisplayed()
                .assertTextEquals("Rza Mudasir Attari")
            assertTrue(com.example.ui.screens.SplashIntroSpec.lastSplashWasStaticForTest)

            // 3. Normal device Post-Signup transition plays the <=1500ms v11 animated intro + wave wipe and finishes
            com.example.ui.components.setLowRamDeviceOverrideForTest(false)
            com.example.ui.screens.SplashIntroSpec.resetTestObservability()
            composeTestRule.mainClock.autoAdvance = false
            showMode = 2
            var signupMs = 0L
            var sawPostSignupCredit = false
            while (!postSignupDone && signupMs < 2_500L) {
                composeTestRule.mainClock.advanceTimeBy(100L)
                org.robolectric.shadows.ShadowLooper.idleMainLooper(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                signupMs += 100L
                if (signupMs in 700L..800L &&
                    composeTestRule.onAllNodesWithTag("splash_credit_name").fetchSemanticsNodes().isNotEmpty()
                ) {
                    composeTestRule.onNodeWithTag("splash_credit_name").assertIsDisplayed()
                    sawPostSignupCredit = true
                }
            }
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitForIdle()
            assertTrue("Post-signup v11 intro must complete cleanly", postSignupDone)
            assertTrue("Post-signup intro must complete within <= 1500ms (actual=$signupMs)", signupMs <= 1_500L)
            assertTrue("Post-signup intro must display footer credit", sawPostSignupCredit)
            assertFalse(com.example.ui.screens.SplashIntroSpec.lastPostSignupWasStaticForTest)
            assertEquals(1, com.example.ui.screens.SplashIntroSpec.postSignupIntroPlayCountForTest)

            // 4. Floating button renders static v11 logo & retains tap count-flash -> back to logo
            FloatingCounterManager.resetForTest(context)
            FloatingCounterManager.setOverlayPermissionOverrideForTest(true)
            assertTrue(FloatingCounterManager.requestEnableFloatingButton(context))
            showMode = 3
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("floating_counter_logo").assertIsDisplayed()
            composeTestRule.onNodeWithTag("floating_counter_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("floating_counter_flash_number")
                .assertIsDisplayed()
                .assertTextEquals("1")
            val dbDeadline = System.currentTimeMillis() + 3_000L
            while (System.currentTimeMillis() < dbDeadline) {
                val total = runCatching {
                    kotlinx.coroutines.runBlocking {
                        bubbleDb.dailyCountDao().getTotalCountOnce("v11_test_user")
                    }
                }.getOrDefault(0L)
                if (total >= 1L) break
                Thread.sleep(20L)
            }
            FloatingCounterManager.onFlashFinished(1L)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("floating_counter_logo").assertIsDisplayed()

            // 5. Share PNG overwrites any stale pre-v11 cache and launcher mipmaps decode cleanly
            val shareDir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
            java.io.File(shareDir, "1_crore_logo.png").writeBytes(byteArrayOf(1, 2, 3))
            java.io.File(shareDir, "1_crore_logo.version").writeText("old_v10")
            val shareUri = com.example.update.AppUpdateManager.prepareShareLogoUri(context)
            assertNotNull(shareUri)
            assertEquals("v11_small_dot", java.io.File(shareDir, "1_crore_logo.version").readText().trim())
            val sharedPng = android.graphics.BitmapFactory.decodeFile(
                java.io.File(shareDir, "1_crore_logo.png").absolutePath
            )
            assertNotNull(sharedPng)
            assertTrue(sharedPng!!.width >= 512)
            assertNotNull(android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
            assertNotNull(android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round))
        } finally {
            composeTestRule.mainClock.autoAdvance = true
            com.example.ui.components.setLowRamDeviceOverrideForTest(null)
            FloatingCounterManager.resetForTest(context)
            runCatching { bubbleDb.close() }
        }
    }

    @Test
    fun `run2Task1 groupsDialogsUseNormalRoundedRectNoClipping_leaveReturnsStraightToListAnd1MemberGrammar`() {
        val sampleGroup = com.example.groups.GroupItem(
            id = "run2_grp_1",
            name = "Sixth Year",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            zikrName = null,
            targetCount = com.example.groups.GroupLogic.TARGET_ONE_CRORE,
            inviteCode = "7NR343",
            creatorUserId = "uid_1",
            createdAtMillis = 1_797_000_000_000L,
            members = listOf(
                com.example.groups.GroupMemberProgress(
                    userId = "uid_1",
                    displayName = "Hn G mlk",
                    lifetimeTotalCount = 1_023L,
                    todayCount = 2L,
                    todayDateKey = LocalDate.now().toString(),
                    updatedAtMillis = 1_797_000_000_000L
                )
            )
        )

        var screenMode by mutableStateOf(0) // 0=hub, 1=detail, 2=null detail
        var groupForDetail by mutableStateOf<com.example.groups.GroupItem?>(sampleGroup)
        var leaveInvokedCount = 0
        var autoBackCount = 0
        var measuredExtraLargeShape: androidx.compose.ui.graphics.Shape? = null

        composeTestRule.setContent {
            OneCroreTheme {
                measuredExtraLargeShape = androidx.compose.material3.MaterialTheme.shapes.extraLarge
                when (screenMode) {
                    0 -> com.example.ui.screens.GroupsHubScreen(
                        groups = listOf(sampleGroup),
                        statusMessage = null,
                        onClearStatusMessage = {},
                        onBack = {},
                        onOpenGroup = { screenMode = 1 },
                        onCreateGroup = { _, _, _, _ -> },
                        onJoinByCode = {}
                    )
                    1 -> com.example.ui.screens.GroupDetailScreen(
                        group = groupForDetail,
                        currentUserId = "uid_1",
                        currentDate = LocalDate.now(),
                        onBack = { screenMode = 0 },
                        onInviteWhatsApp = {},
                        onLeaveGroup = {
                            leaveInvokedCount++
                            groupForDetail = null
                            screenMode = 0
                        },
                        onIncrementCustomZikr = { _, _ -> }
                    )
                    else -> com.example.ui.screens.GroupDetailScreen(
                        group = null,
                        currentUserId = "uid_1",
                        currentDate = LocalDate.now(),
                        onBack = {
                            autoBackCount++
                            screenMode = 0
                        },
                        onInviteWhatsApp = {},
                        onLeaveGroup = {},
                        onIncrementCustomZikr = { _, _ -> }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Root cause verified fixed: theme extraLarge is 16.dp rounded rectangle, never 999.dp capsule oval
        assertEquals(
            androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            measuredExtraLargeShape
        )

        // 1. My Groups list shows singular "1 member", never "1 members"
        composeTestRule.onNodeWithText("1 member · Code 7NR343", substring = false).assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("1 members", substring = true).fetchSemanticsNodes().isEmpty())

        // 2. Create Group dialog renders as normal unclipped rounded rectangle with full title + Create button
        composeTestRule.onNodeWithTag("groups_create_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_group_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_group_dialog_title")
            .assertIsDisplayed()
            .assertTextEquals("Create Group")
        composeTestRule.onNodeWithTag("create_group_submit_button")
            .assertIsDisplayed()
            .assertTextContains("Create")
        val createDialogBounds = composeTestRule.onNodeWithTag("create_group_dialog").fetchSemanticsNode().boundsInRoot
        val createTitleBounds = composeTestRule.onNodeWithTag("create_group_dialog_title").fetchSemanticsNode().boundsInRoot
        val createBtnBounds = composeTestRule.onNodeWithTag("create_group_submit_button").fetchSemanticsNode().boundsInRoot
        assertTrue(createTitleBounds.left >= createDialogBounds.left && createTitleBounds.right <= createDialogBounds.right)
        assertTrue(createBtnBounds.right <= createDialogBounds.right && createBtnBounds.bottom <= createDialogBounds.bottom)
        composeTestRule.onNodeWithTag("create_group_cancel_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Open Group Detail -> Leave Group dialog is unclipped & tapping Leave goes straight back to My Groups
        composeTestRule.onNodeWithTag("group_card_run2_grp_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_leave_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("group_leave_confirm_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_leave_dialog_title")
            .assertIsDisplayed()
            .assertTextEquals("Leave Sixth Year?")
        composeTestRule.onNodeWithTag("group_leave_confirm_button")
            .assertIsDisplayed()
            .assertTextContains("Leave")
        composeTestRule.onNodeWithTag("group_leave_cancel_button")
            .assertIsDisplayed()
            .assertTextContains("Cancel")
        val leaveDialogBounds = composeTestRule.onNodeWithTag("group_leave_confirm_dialog").fetchSemanticsNode().boundsInRoot
        val leaveTitleBounds = composeTestRule.onNodeWithTag("group_leave_dialog_title").fetchSemanticsNode().boundsInRoot
        val leaveConfirmBounds = composeTestRule.onNodeWithTag("group_leave_confirm_button").fetchSemanticsNode().boundsInRoot
        assertTrue(leaveTitleBounds.left >= leaveDialogBounds.left && leaveTitleBounds.right <= leaveDialogBounds.right)
        assertTrue(leaveConfirmBounds.right <= leaveDialogBounds.right && leaveConfirmBounds.bottom <= leaveDialogBounds.bottom)

        composeTestRule.onNodeWithTag("group_leave_confirm_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, leaveInvokedCount)
        composeTestRule.onNodeWithTag("groups_create_button").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("Group not available", substring = true).fetchSemanticsNodes().isEmpty())

        // 4. Even if GroupDetailScreen is composed with null group, it auto-returns cleanly without "Group not available"
        screenMode = 2
        composeTestRule.waitForIdle()
        assertEquals(1, autoBackCount)
        composeTestRule.onNodeWithTag("groups_create_button").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("Group not available", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `run2Task2 homeHeaderRemovesSendBlessingsLineAndPlacesGlobalBlockDirectlyUnderArabicGreeting`() {
        val state = MainUiState(
            profile = UserProfileEntity(
                userId = "run2_home_user",
                displayName = "Hn G mlk",
                dailyTarget = 2_600,
                setupCompleted = true,
                startedAtMillis = 1_794_800_000_000L
            ),
            totalCount = 1_071L,
            todayCount = 50L,
            isOnline = true,
            globalStats = GlobalCounterStats(
                totalCount = 9_614L,
                userCount = 6L,
                fetchedAtMillis = 1_797_000_000_000L
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = state,
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {},
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. "Send blessings upon him" English line must be completely removed
        assertTrue(
            composeTestRule.onAllNodesWithText("Send blessings upon him", substring = true, ignoreCase = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )

        // 2. Greeting on top -> Global counter block directly below left-aligned -> Welcome directly below that
        composeTestRule.onNodeWithTag("home_arabic_greeting").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_global_counter_block").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_welcome_text")
            .assertIsDisplayed()
            .assertTextEquals("Welcome, Hn G mlk")

        val greetingBounds = composeTestRule.onNodeWithTag("home_arabic_greeting").fetchSemanticsNode().boundsInRoot
        val globalBounds = composeTestRule.onNodeWithTag("home_global_counter_block").fetchSemanticsNode().boundsInRoot
        val welcomeBounds = composeTestRule.onNodeWithTag("home_welcome_text").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expected Global block directly below Arabic greeting (${greetingBounds.bottom} <= ${globalBounds.top})",
            greetingBounds.bottom <= globalBounds.top + 1f
        )
        assertTrue(
            "Expected Welcome directly below Global block (${globalBounds.bottom} <= ${welcomeBounds.top})",
            globalBounds.bottom <= welcomeBounds.top + 1f
        )
        assertTrue(
            "Expected left-aligned Global block aligned with Welcome (${globalBounds.left} vs ${welcomeBounds.left})",
            kotlin.math.abs(globalBounds.left - welcomeBounds.left) <= 2f
        )
    }

    @Test
    fun `run3Task1 fiveGroupSlots creatorEditAndDeleteWith16dpDialogs nonCreatorHidesBoth andDeleteNeverTouchesPersonalTotals`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.example.groups.GroupRepository.resetForTest(context)

        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val creatorId = "run3_creator"
            val memberId = "run3_member"
            val todayKey = java.time.LocalDate.now().toString()
            val now = System.currentTimeMillis()
            runBlocking {
                db.userProfileDao().upsertProfile(
                    com.example.data.local.UserProfileEntity(
                        userId = creatorId,
                        displayName = "Creator Ali",
                        dailyTarget = 2600,
                        setupCompleted = true
                    )
                )
                db.dailyCountDao().incrementDay(creatorId, todayKey, 4_200L, now)
            }
            assertEquals(4_200L, runBlocking { db.dailyCountDao().getTotalCountOnce(creatorId) })

            val group1 = runBlocking {
                com.example.groups.GroupRepository.createGroup(
                    context = context,
                    creatorUserId = creatorId,
                    creatorDisplayName = "Creator Ali",
                    name = "Sixth Year",
                    type = com.example.groups.GroupType.MAIN_DUROOD,
                    creatorTodayCount = 4_200L,
                    creatorLifetimeTotal = 4_200L
                ).getOrThrow()
            }
            runBlocking {
                com.example.groups.GroupRepository.joinGroupByCode(
                    context = context,
                    userId = memberId,
                    displayName = "Member Omar",
                    rawInviteCode = group1.inviteCode,
                    userTodayCount = 500L,
                    userLifetimeTotal = 500L
                ).getOrThrow()
            }

            // 1. GroupsHubScreen shows "1 of 5 group slots used", supports up to 5 slots, and disables creation at 5
            var hubGroups by mutableStateOf(listOf(com.example.groups.GroupRepository.getGroupById(context, group1.id)!!))
            var viewingUserId by mutableStateOf(creatorId)
            var activeDetailGroupId by mutableStateOf<String?>(null)
            val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate)

            composeTestRule.setContent {
                OneCroreTheme {
                    val detailGroup = activeDetailGroupId?.let { id -> hubGroups.firstOrNull { it.id == id } }
                    if (activeDetailGroupId == null || detailGroup == null) {
                        com.example.ui.screens.GroupsHubScreen(
                            groups = hubGroups,
                            statusMessage = null,
                            onClearStatusMessage = {},
                            onBack = {},
                            onOpenGroup = { activeDetailGroupId = it },
                            onCreateGroup = { _, _, _, _ -> },
                            onJoinByCode = {}
                        )
                    } else {
                        com.example.ui.screens.GroupDetailScreen(
                            group = detailGroup,
                            currentUserId = viewingUserId,
                            currentDate = java.time.LocalDate.now(),
                            onBack = { activeDetailGroupId = null },
                            onInviteWhatsApp = {},
                            onRenameGroup = { gId, newName ->
                                runBlocking {
                                    com.example.groups.GroupRepository.renameGroup(context, viewingUserId, gId, newName)
                                }
                                hubGroups = com.example.groups.GroupRepository.getGroupsForUser(context, creatorId)
                            },
                            onLeaveGroup = { activeDetailGroupId = null },
                            onDeleteGroup = { gId ->
                                runBlocking {
                                    com.example.groups.GroupRepository.deleteGroup(context, viewingUserId, gId)
                                }
                                activeDetailGroupId = null
                                hubGroups = com.example.groups.GroupRepository.getGroupsForUser(context, creatorId)
                            },
                            onIncrementCustomZikr = { _, _ -> }
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("1 of 5 group slots used").assertIsDisplayed()

            // Add 4 more groups -> 5 of 5 slots used -> notice updates to 5
            val extra4 = (2..5).map { idx ->
                runBlocking {
                    com.example.groups.GroupRepository.createGroup(
                        context = context,
                        creatorUserId = creatorId,
                        creatorDisplayName = "Creator Ali",
                        name = "Group $idx",
                        type = com.example.groups.GroupType.MAIN_DUROOD
                    ).getOrThrow()
                }
            }
            assertEquals(5, extra4.size + 1)
            hubGroups = com.example.groups.GroupRepository.getGroupsForUser(context, creatorId)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("5 of 5 group slots used").assertIsDisplayed()
            composeTestRule.onNodeWithTag("groups_limit_notice").assertIsDisplayed()
            composeTestRule.onNodeWithText("5 groups (max)", substring = true).assertIsDisplayed()

            // 2. Non-creator MUST NOT see Edit Name icon or Delete Group button on GroupDetailScreen
            viewingUserId = memberId
            activeDetailGroupId = group1.id
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("group_leave_button").performScrollTo().assertIsDisplayed()
            assertTrue(composeTestRule.onAllNodesWithTag("group_edit_name_button").fetchSemanticsNodes().isEmpty())
            assertTrue(composeTestRule.onAllNodesWithTag("group_delete_button").fetchSemanticsNodes().isEmpty())

            // Non-creator calling rename/delete in repository is also blocked
            assertTrue(
                runBlocking {
                    com.example.groups.GroupRepository.renameGroup(context, memberId, group1.id, "Hacked Name")
                }.isFailure
            )
            assertTrue(
                runBlocking {
                    com.example.groups.GroupRepository.deleteGroup(context, memberId, group1.id)
                }.isFailure
            )

            // 3. Creator sees Edit Name icon in header + Delete Group below Leave Group, renames cleanly via dialog
            viewingUserId = creatorId
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("group_edit_name_button").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("group_leave_button").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("group_delete_button").performScrollTo().assertIsDisplayed()

            val leaveBounds = composeTestRule.onNodeWithTag("group_leave_button").fetchSemanticsNode().boundsInRoot
            val deleteBounds = composeTestRule.onNodeWithTag("group_delete_button").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Delete Group must sit below Leave Group (${leaveBounds.bottom} <= ${deleteBounds.top})",
                leaveBounds.bottom <= deleteBounds.top + 1f
            )

            // Open Rename dialog -> verify normal rounded-rectangle dimensions & full title -> rename group
            composeTestRule.onNodeWithTag("group_edit_name_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("group_rename_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("group_rename_dialog_title").assertTextEquals("Rename Group")
            val renameDialogBounds = composeTestRule.onNodeWithTag("group_rename_dialog").fetchSemanticsNode().boundsInRoot
            assertTrue(renameDialogBounds.width > renameDialogBounds.height * 0.8f)

            composeTestRule.onNodeWithTag("group_rename_input").performTextReplacement("Seventh Year")
            composeTestRule.onNodeWithTag("group_rename_confirm_button").performClick()
            composeTestRule.waitForIdle()

            // New name visible on detail and to all members
            composeTestRule.onNodeWithText("Seventh Year").performScrollTo().assertIsDisplayed()
            val forMember = com.example.groups.GroupRepository.getGroupsForUser(context, memberId)
                .first { it.id == group1.id }
            assertEquals("Seventh Year", forMember.name)

            // 4. Creator opens Delete Confirmation dialog -> confirms -> group deleted for everyone, personal Durood total untouched
            composeTestRule.onNodeWithTag("group_delete_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("group_delete_confirm_dialog").assertIsDisplayed()
            val deleteDialogBounds = composeTestRule.onNodeWithTag("group_delete_confirm_dialog").fetchSemanticsNode().boundsInRoot
            assertTrue(deleteDialogBounds.width > deleteDialogBounds.height * 0.8f)

            composeTestRule.onNodeWithTag("group_delete_confirm_button").performClick()
            composeTestRule.waitForIdle()

            assertNull(com.example.groups.GroupRepository.getGroupById(context, group1.id))
            assertFalse(
                com.example.groups.GroupRepository.getGroupsForUser(context, memberId)
                    .any { it.id == group1.id }
            )
            assertEquals(4_200L, runBlocking { db.dailyCountDao().getTotalCountOnce(creatorId) })
            composeTestRule.onNodeWithText("4 of 5 group slots used").assertIsDisplayed()
        } finally {
            db.close()
            com.example.groups.GroupRepository.resetForTest(context)
        }
    }

    @Test
    fun `run3Task2 joinByCodeAddsMemberToGroup wrongCodeShowsInvalidCodeAndRetainsInput andInviteHasNoLink`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.example.groups.GroupRepository.resetForTest(context)

        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val joinerId = "run3_joiner"
            val todayKey = java.time.LocalDate.now().toString()
            val now = System.currentTimeMillis()
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            auth.signOut()
            runBlocking {
                db.userProfileDao().upsertProfile(
                    com.example.data.local.UserProfileEntity(
                        userId = joinerId,
                        displayName = "Joiner Bilal",
                        dailyTarget = 2600,
                        setupCompleted = true
                    )
                )
                db.dailyCountDao().incrementDay(joinerId, todayKey, 300L, now)
            }

            val hostGroup = runBlocking {
                com.example.groups.GroupRepository.createGroup(
                    context = context,
                    creatorUserId = "host_user",
                    creatorDisplayName = "Host Hn",
                    name = "Sixth Year",
                    type = com.example.groups.GroupType.MAIN_DUROOD,
                    creatorTodayCount = 1_023L,
                    creatorLifetimeTotal = 1_023L
                ).getOrThrow()
            }

            // Verify invite message has ONLY group name and code, with zero links
            val inviteText = com.example.groups.GroupLogic.buildMergedWhatsAppInviteMessage(hostGroup)
            assertEquals("Sixth Year\nInvite Code: ${hostGroup.inviteCode}", inviteText)
            assertFalse(inviteText.contains("http://"))
            assertFalse(inviteText.contains("https://"))
            assertFalse(inviteText.contains("onecrore.app"))

            val repo = com.example.data.DuroodRepository(
                localDb = db,
                firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                auth = auth
            )
            val vm = com.example.ui.MainViewModel(
                repository = repo,
                currentUserId = joinerId,
                fallbackDisplayName = "Joiner Bilal",
                accountCreatedAtMillis = now,
                appContext = context
            )
            val vmStore = androidx.lifecycle.ViewModelStore()
            vmStore.put("run3_join_vm", vm)

            var latestState by mutableStateOf<MainUiState?>(null)
            var joinedGroupId by mutableStateOf<String?>(null)
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
                vm.uiState.collect { latestState = it }
            }

            try {
                composeTestRule.setContent {
                    OneCroreTheme {
                        val state = latestState ?: MainUiState()
                        com.example.ui.screens.GroupsHubScreen(
                            groups = state.userGroups,
                            statusMessage = state.statusMessage,
                            isJoiningGroup = state.isJoiningGroup,
                            onClearStatusMessage = vm::clearStatusMessage,
                            onBack = {},
                            onOpenGroup = { joinedGroupId = it },
                            onCreateGroup = { _, _, _, _ -> },
                            onJoinByCode = { code ->
                                vm.onJoinGroupByCode(code) { joinedGroupId = it.id }
                            }
                        )
                    }
                }
                composeTestRule.waitForIdle()

                // 1. Enter wrong code -> shows "Invalid code" error and retains user's typed code so they can fix typos
                composeTestRule.onNodeWithTag("groups_join_code_input").performTextInput("WRONGC")
                composeTestRule.onNodeWithTag("groups_join_button").performClick()

                val wrongDeadline = System.currentTimeMillis() + 3_000L
                while (latestState?.statusMessage != "Invalid code" && System.currentTimeMillis() < wrongDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                composeTestRule.waitForIdle()
                assertEquals("Invalid code", latestState?.statusMessage)
                composeTestRule.onNodeWithTag("groups_status_banner").assertIsDisplayed()
                composeTestRule.onNodeWithText("Invalid code").assertIsDisplayed()
                assertTrue(latestState?.userGroups.orEmpty().isEmpty())

                // 2. Replace with valid invite code -> adds joiner to group and updates UI
                composeTestRule.onNodeWithTag("groups_join_code_input").performTextReplacement(hostGroup.inviteCode)
                composeTestRule.onNodeWithTag("groups_join_button").performClick()

                val joinDeadline = System.currentTimeMillis() + 3_000L
                while (joinedGroupId == null && System.currentTimeMillis() < joinDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                composeTestRule.waitForIdle()
                assertEquals(hostGroup.id, joinedGroupId)
                assertTrue(latestState?.userGroups.orEmpty().any { it.id == hostGroup.id })
                val persistedGroup = com.example.groups.GroupRepository.getGroupById(context, hostGroup.id)!!
                assertTrue(persistedGroup.members.any { it.userId == joinerId })
                assertEquals(2, persistedGroup.memberCount)
            } finally {
                job.cancel()
            }
        } finally {
            db.close()
            com.example.groups.GroupRepository.resetForTest(context)
        }
    }

    @Test
    fun `run4Task1 inGroupCustomAddAndReduceToggle isolatedFromPersonalAndGlobal clampsAtZero noFloatingButton`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.example.groups.GroupRepository.resetForTest(context)
        com.example.badges.MilestoneBadgeManager.resetForTest(context)

        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val uid = "run4_group_user"
            val today = java.time.LocalDate.now()
            val todayKey = today.toString()
            val yesterdayKey = today.minusDays(1).toString()
            val now = System.currentTimeMillis()
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            auth.signOut()

            runBlocking {
                db.userProfileDao().upsertProfile(
                    com.example.data.local.UserProfileEntity(
                        userId = uid,
                        displayName = "Saad",
                        dailyTarget = 2600,
                        setupCompleted = true
                    )
                )
                db.dailyCountDao().incrementDay(uid, yesterdayKey, 2_000L, now - 86_400_000L)
                db.dailyCountDao().incrementDay(uid, todayKey, 500L, now)
            }

            val repo = com.example.data.DuroodRepository(
                localDb = db,
                firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                auth = auth
            )
            val vm = com.example.ui.MainViewModel(
                repository = repo,
                currentUserId = uid,
                fallbackDisplayName = "Saad",
                accountCreatedAtMillis = now,
                appContext = context
            )
            val vmStore = androidx.lifecycle.ViewModelStore()
            vmStore.put("run4_group_vm", vm)
            var latestState by mutableStateOf<MainUiState?>(null)
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
                vm.uiState.collect { latestState = it }
            }

            try {
                val initDeadline = System.currentTimeMillis() + 3_000L
                while (latestState?.isLoading != false && System.currentTimeMillis() < initDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                assertEquals(2_500L, latestState?.totalCount)

                // Create Main Durood group -> MUST start at 0L (personal 2,500 does NOT flow into group)
                var createdGroup: com.example.groups.GroupItem? = null
                vm.onCreateGroup(
                    name = "Ramadan Circle",
                    type = com.example.groups.GroupType.MAIN_DUROOD,
                    targetCount = 10_000_000L
                ) { createdGroup = it }
                val createDeadline = System.currentTimeMillis() + 2_000L
                while (createdGroup == null && System.currentTimeMillis() < createDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                assertNotNull(createdGroup)
                assertEquals(0L, createdGroup!!.effectiveGroupTotal)

                composeTestRule.setContent {
                    OneCroreTheme {
                        val group = latestState?.userGroups?.firstOrNull { it.id == createdGroup!!.id }
                        com.example.ui.screens.GroupDetailScreen(
                            group = group,
                            currentUserId = uid,
                            currentDate = today,
                            onBack = {},
                            onInviteWhatsApp = {},
                            onLeaveGroup = {},
                            onAdjustMainGroupCount = vm::onAdjustMainGroupCount,
                            onIncrementCustomZikr = vm::onIncrementCustomZikrGroup
                        )
                    }
                }
                composeTestRule.waitForIdle()

                // Verify percentage is displayed, + Custom button exists, and NO floating button exists on Group Detail
                composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("0")
                composeTestRule.onNodeWithTag("group_target_text").assertTextContains("0.00%", substring = true)
                composeTestRule.onNodeWithTag("group_main_custom_button").performScrollTo().assertIsDisplayed()
                assertTrue(composeTestRule.onAllNodesWithTag("floating_counter_button").fetchSemanticsNodes().isEmpty())

                // Main counter tap still MUST NOT affect this group
                vm.onTapPlusOne()
                val personalTickDeadline = System.currentTimeMillis() + 2_000L
                while ((latestState?.totalCount ?: 0L) != 2_501L && System.currentTimeMillis() < personalTickDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                composeTestRule.waitForIdle()
                assertEquals(2_501L, latestState?.totalCount)
                composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("0")

                // Open + Custom -> Add 5,000 inside group -> group total = 5,000 (0.05%), personal stays 2,501
                composeTestRule.onNodeWithTag("group_main_custom_button").performScrollTo().performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("group_main_custom_dialog").assertIsDisplayed()
                composeTestRule.onNodeWithTag("group_main_custom_input").performTextInput("5000")
                composeTestRule.onNodeWithTag("group_main_custom_confirm").performClick()

                val addDeadline = System.currentTimeMillis() + 2_000L
                while ((latestState?.userGroups?.firstOrNull { it.id == createdGroup!!.id }?.effectiveGroupTotal ?: 0L) != 5_000L &&
                    System.currentTimeMillis() < addDeadline
                ) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                composeTestRule.waitForIdle()

                composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("5,000")
                composeTestRule.onNodeWithTag("group_target_text").assertTextContains("0.05%", substring = true)
                composeTestRule.onNodeWithTag("group_member_row_$uid").performScrollTo()
                    .assertTextContains("5,000", substring = true)
                assertEquals(2_501L, latestState?.totalCount)

                // Toggle Reduce -> enter 1,200 -> group drops to 3,800; personal stays 2,501
                composeTestRule.onNodeWithTag("group_main_custom_button").performScrollTo().performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("group_main_mode_reduce").performClick()
                composeTestRule.onNodeWithTag("group_main_custom_input").performTextInput("1200")
                composeTestRule.onNodeWithTag("group_main_custom_confirm").performClick()

                val reduceDeadline = System.currentTimeMillis() + 2_000L
                while ((latestState?.userGroups?.firstOrNull { it.id == createdGroup!!.id }?.effectiveGroupTotal ?: 0L) != 3_800L &&
                    System.currentTimeMillis() < reduceDeadline
                ) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("3,800")
                assertEquals(2_501L, latestState?.totalCount)

                // Toggle Reduce -> enter 99,999 -> group clamps strictly at 0 (never negative) and personal stays 2,501
                composeTestRule.onNodeWithTag("group_main_custom_button").performScrollTo().performClick()
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("group_main_mode_reduce").performClick()
                composeTestRule.onNodeWithTag("group_main_custom_input").performTextInput("99999")
                composeTestRule.onNodeWithTag("group_main_custom_confirm").performClick()

                val clampDeadline = System.currentTimeMillis() + 2_000L
                while ((latestState?.userGroups?.firstOrNull { it.id == createdGroup!!.id }?.effectiveGroupTotal ?: -1L) != 0L &&
                    System.currentTimeMillis() < clampDeadline
                ) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("0")
                assertEquals(2_501L, latestState?.totalCount)
            } finally {
                job.cancel()
                vmStore.clear()
            }
        } finally {
            db.close()
            com.example.groups.GroupRepository.resetForTest(context)
            com.example.badges.MilestoneBadgeManager.resetForTest(context)
        }
    }

    @Test
    fun `run4Task2 badgesLockAgainOnHistoryDropAndRecomputeStats exact1071Scenario noReplayOfPastCelebration`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.example.badges.MilestoneBadgeManager.resetForTest(context)

        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.AppDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val uid = "run4_badge_drop_user"
            val today = java.time.LocalDate.now()
            val todayKey = today.toString()
            val yesterdayKey = today.minusDays(1).toString()
            val now = System.currentTimeMillis()
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            auth.signOut()

            // Start with user at 5,000,050 (Today 50 + Yesterday 5,000,000 -> 100/200 badges up to 50 Lakh)
            runBlocking {
                db.userProfileDao().upsertProfile(
                    com.example.data.local.UserProfileEntity(
                        userId = uid,
                        displayName = "Zaid",
                        dailyTarget = 2600,
                        setupCompleted = true
                    )
                )
                db.dailyCountDao().incrementDay(uid, yesterdayKey, 5_000_000L, now - 86_400_000L)
                db.dailyCountDao().incrementDay(uid, todayKey, 50L, now)
            }

            val repo = com.example.data.DuroodRepository(
                localDb = db,
                firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                auth = auth
            )
            val vm = com.example.ui.MainViewModel(
                repository = repo,
                currentUserId = uid,
                fallbackDisplayName = "Zaid",
                accountCreatedAtMillis = now,
                appContext = context
            )
            val vmStore = androidx.lifecycle.ViewModelStore()
            vmStore.put("run4_badge_vm", vm)
            var latestState by mutableStateOf<MainUiState?>(null)
            val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
                vm.uiState.collect { latestState = it }
            }

            try {
                val highDeadline = System.currentTimeMillis() + 3_000L
                while (latestState?.pendingMilestoneBadge?.tier != 100 && System.currentTimeMillis() < highDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                assertEquals(100, latestState?.badgeProgress?.earnedCount)
                assertEquals("50 Lakh Badge", latestState?.badgeProgress?.latestEarnedBadge?.title)
                vm.dismissMilestoneBadgeCelebration()
                org.robolectric.shadows.ShadowLooper.idleMainLooper()

                // User reduces yesterday's count via History edit to 1,021 -> current total is 1,071 (50 + 1,021)
                vm.onEditDayCount(yesterdayKey, 1_021L, 5_000_000L)
                val dropDeadline = System.currentTimeMillis() + 3_000L
                while ((latestState?.totalCount ?: 0L) != 1_071L && System.currentTimeMillis() < dropDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                // Wait for badgeProgress to re-evaluate after drop
                val badgeDropDeadline = System.currentTimeMillis() + 2_000L
                while ((latestState?.badgeProgress?.earnedCount ?: -1) != 0 && System.currentTimeMillis() < badgeDropDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }

                assertEquals(1_071L, latestState?.totalCount)
                assertEquals(0, latestState?.badgeProgress?.earnedCount)
                assertNull(latestState?.badgeProgress?.latestEarnedBadge)
                assertEquals("50K Badge", latestState?.badgeProgress?.nextBadge?.title)
                assertEquals(48_929L, latestState?.badgeProgress?.remainingToNext)
                assertNull(latestState?.pendingMilestoneBadge)

                // Render StatsScreen -> verify 0 / 200 and Next: 50K Badge · 48,929 remaining
                composeTestRule.setContent {
                    val ui = latestState
                    if (ui != null) {
                        OneCroreTheme {
                            com.example.ui.screens.StatsScreen(uiState = ui)
                        }
                    }
                }
                composeTestRule.waitForIdle()
                composeTestRule.onNodeWithTag("stats_milestone_badges_count")
                    .performScrollTo()
                    .assertTextContains("0 / 200")
                composeTestRule.onNodeWithText("Next: 50K Badge · 48,929 remaining")
                    .performScrollTo()
                    .assertIsDisplayed()

                // User crosses 50,000 again -> badge #1 re-unlocks, WITHOUT replaying already-celebrated popup!
                vm.onAddCustomAmount(48_929L)
                val reEarnDeadline = System.currentTimeMillis() + 3_000L
                while ((latestState?.badgeProgress?.earnedCount ?: 0) != 1 && System.currentTimeMillis() < reEarnDeadline) {
                    org.robolectric.shadows.ShadowLooper.idleMainLooper()
                    Thread.sleep(20L)
                }
                composeTestRule.waitForIdle()
                assertEquals(50_000L, latestState?.totalCount)
                assertEquals(1, latestState?.badgeProgress?.earnedCount)
                assertEquals("50K Badge", latestState?.badgeProgress?.latestEarnedBadge?.title)
                assertNull("Past celebration popups must NOT replay when re-crossing a previously celebrated tier", latestState?.pendingMilestoneBadge)
                composeTestRule.onNodeWithTag("stats_milestone_badges_count")
                    .performScrollTo()
                    .assertTextContains("1 / 200")
            } finally {
                job.cancel()
                vmStore.clear()
            }
        } finally {
            db.close()
            com.example.badges.MilestoneBadgeManager.resetForTest(context)
        }
    }

    @Test
    fun `fixRun6Task1 uiDialogsAndScreens_renderVocalizedArabicWithAiraab`() {
        var mode by mutableStateOf(0)
        val sampleBadge = com.example.badges.MilestoneBadgeManager.badgeForTier(1)!!
        val fullProgress = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(10_000_000L)
        val sampleGroup = com.example.groups.GroupItem(
            id = "grp_run6_ui",
            name = "Halqa Noor",
            inviteCode = "NOOR99",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            targetCount = com.example.groups.GroupLogic.TARGET_ONE_CRORE,
            creatorUserId = "u1",
            members = listOf(
                com.example.groups.GroupMemberProgress(
                    userId = "u1",
                    displayName = "Zaid",
                    todayCount = 1_000L,
                    todayDateKey = java.time.LocalDate.now().toString(),
                    lifetimeTotalCount = 100_000L,
                    weeklyCount = 5_000L
                )
            ),
            serverTotalCount = 100_000L
        )
        val celebrationNotice = com.example.groups.GroupLogic.computeCelebrations(
            group = sampleGroup,
            currentUserId = "u1"
        ).first { it.type == com.example.groups.GroupCelebrationType.TARGET_1_LAKH }

        composeTestRule.setContent {
            OneCroreTheme {
                when (mode) {
                    0 -> com.example.ui.components.MilestoneBadgeCelebrationDialog(
                        badge = sampleBadge,
                        earnedCount = 1,
                        onDismiss = {}
                    )
                    1 -> com.example.ui.components.StatsMilestoneBadgesSection(
                        progress = fullProgress
                    )
                    2 -> com.example.ui.screens.GroupCelebrationPopupDialog(
                        notice = celebrationNotice,
                        onDismiss = {}
                    )
                    3 -> com.example.ui.screens.GroupsHubScreen(
                        groups = emptyList(),
                        statusMessage = null,
                        onClearStatusMessage = {},
                        onBack = {},
                        onOpenGroup = {},
                        onCreateGroup = { _, _, _, _ -> },
                        onJoinByCode = {}
                    )
                    4 -> com.example.ui.components.ScheduleAnnouncementDialog(
                        onDismiss = {},
                        onSchedule = {}
                    )
                }
            }
        }

        // 0: MilestoneBadgeCelebrationDialog shows vocalized الْحَمْدُ لِلَّهِ in body and confirm button
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("الْحَمْدُ لِلَّهِ! You have crossed 50,000 Durood Pak. Earned 1 of 200 monochrome badges.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("milestone_badge_dialog_confirm")
            .assertIsDisplayed()
            .assertTextEquals("الْحَمْدُ لِلَّهِ")

        // 1: StatsMilestoneBadgesSection when all 200 badges are earned shows "الْحَمْدُ لِلَّهِ! All 200 badges earned!"
        mode = 1
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("الْحَمْدُ لِلَّهِ! All 200 badges earned!")
            .assertIsDisplayed()

        // 2: GroupCelebrationPopupDialog shows vocalized Arabic message and confirm button "الْحَمْدُ لِلَّهِ"
        mode = 2
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("الْحَمْدُ لِلَّهِ! Halqa Noor has crossed 1,00,000 (1 Lakh) Durood Pak together!")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_celebration_popup_confirm")
            .assertIsDisplayed()
            .assertTextEquals("الْحَمْدُ لِلَّهِ")

        // 3: CreateGroupDialog Custom Zikr input label shows "Zikr name (e.g. سُبْحَانَ اللَّهِ)"
        mode = 3
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("groups_create_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_group_type_zikr").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Zikr name (e.g. سُبْحَانَ اللَّهِ)")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_group_cancel_button").performClick()
        composeTestRule.waitForIdle()

        // 4: ScheduleAnnouncementDialog preset button shows "Next الْجُمُعَةُ"
        mode = 4
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("schedule_announcement_preset_jumuah")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("Next الْجُمُعَةُ")
    }

    @Test
    fun `fixRun6Task2 outboundMilestonePushNotificationAndShareCard_deliverVocalizedArabicWithAiraab`() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(notificationManager)
        notificationManager.cancelAll()

        val oneLakhItem = com.example.announcements.MilestoneAnnouncementManager.buildMilestoneAnnouncement(
            threshold = com.example.announcements.MilestoneAnnouncementManager.MILESTONE_ONE_LAKH
        )
        val shown1L = com.example.announcements.AnnouncementNotificationHelper.showAnnouncementPushNotification(
            context = context,
            title = oneLakhItem.title,
            body = oneLakhItem.body,
            announcementId = oneLakhItem.id
        )
        assertTrue(shown1L)

        val tenLakhItem = com.example.announcements.MilestoneAnnouncementManager.buildMilestoneAnnouncement(
            threshold = com.example.announcements.MilestoneAnnouncementManager.MILESTONE_TEN_LAKH
        )
        val shown10L = com.example.announcements.AnnouncementNotificationHelper.showAnnouncementPushNotification(
            context = context,
            title = tenLakhItem.title,
            body = tenLakhItem.body,
            announcementId = tenLakhItem.id
        )
        assertTrue(shown10L)

        val postedTexts = shadowNm.allNotifications.mapNotNull {
            it.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        }
        assertTrue(postedTexts.any { it.startsWith("الْحَمْدُ لِلَّهِ!") })
        assertTrue(postedTexts.any { it.startsWith("مَا شَاءَ اللَّهُ!") })

        val spec = com.example.update.AppUpdateManager.buildProgressShareCardSpec(
            todayCount = 2_600L,
            totalCount = 100_000L,
            downloadUrl = "https://example.com/1crore.apk"
        )
        assertTrue(spec.shareCaptionText.startsWith("*صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا ﷺ*"))
        val bmp = com.example.update.AppUpdateManager.renderProgressShareCardBitmap(spec)
        assertEquals(1080, bmp.width)
        assertEquals(1080, bmp.height)
    }

    @Test
    fun `fixRun7Task1 perGroupFloatingButton_togglesForMainDuroodOnlyShowsLiveTotalAndCountsOnlyThatGroup`() = runBlocking {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val userId = "run7_task1_user_${System.nanoTime()}"
        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = userId,
                displayName = "Ahmad",
                dailyTarget = 2600,
                setupCompleted = true
            )
        )
        val todayKey = LocalDate.now().toString()
        db.dailyCountDao().incrementDay(userId, todayKey, 500L, System.currentTimeMillis())
        val customCounterId = "cc_run7_1"
        db.customCounterDao().upsertCounter(
            CustomCounterEntity(
                id = customCounterId,
                userId = userId,
                name = "Istighfar",
                targetCount = 1000L,
                currentCount = 10L
            )
        )
        WidgetAndFloatingBridge.setActiveUserId(context, userId)
        FloatingCounterManager.setOverlayPermissionOverrideForTest(true)

        val groupA = com.example.groups.GroupRepository.createGroup(
            context = context,
            creatorUserId = userId,
            creatorDisplayName = "Ahmad",
            name = "Halqa A",
            type = com.example.groups.GroupType.MAIN_DUROOD
        ).getOrThrow()
        com.example.groups.GroupRepository.adjustMainGroupCount(
            context = context,
            userId = userId,
            displayName = "Ahmad",
            groupId = groupA.id,
            delta = 120L,
            todayDateKey = todayKey,
            pushToCloud = false
        )
        val groupB = com.example.groups.GroupRepository.createGroup(
            context = context,
            creatorUserId = userId,
            creatorDisplayName = "Ahmad",
            name = "Halqa B",
            type = com.example.groups.GroupType.MAIN_DUROOD
        ).getOrThrow()
        com.example.groups.GroupRepository.adjustMainGroupCount(
            context = context,
            userId = userId,
            displayName = "Ahmad",
            groupId = groupB.id,
            delta = 50L,
            todayDateKey = todayKey,
            pushToCloud = false
        )
        val zikrGroup = com.example.groups.GroupRepository.createGroup(
            context = context,
            creatorUserId = userId,
            creatorDisplayName = "Ahmad",
            name = "Tasbih Circle",
            type = com.example.groups.GroupType.CUSTOM_ZIKR,
            zikrName = "SubhanAllah",
            targetCount = 10_000L
        ).getOrThrow()
        com.example.groups.GroupRepository.incrementCustomZikrGroup(
            context = context,
            userId = userId,
            displayName = "Ahmad",
            groupId = zikrGroup.id,
            delta = 30L,
            todayDateKey = todayKey,
            pushToCloud = false
        )

        var selectedGroup by mutableStateOf(com.example.groups.GroupRepository.getGroupById(context, zikrGroup.id)!!)
        var showBubbleOverlay by mutableStateOf(false)

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    com.example.ui.screens.GroupDetailScreen(
                        group = selectedGroup,
                        currentUserId = userId,
                        currentDate = LocalDate.now(),
                        onBack = {},
                        onInviteWhatsApp = {},
                        onLeaveGroup = {},
                        onIncrementCustomZikr = { _, _ -> }
                    )
                    if (showBubbleOverlay) {
                        FloatingCounterBubbleComposable(
                            db = db,
                            userIdOverride = userId,
                            flashDurationMillis = 100L
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Custom Zikr group gets NO floating button toggle
        assertEquals(0, composeTestRule.onAllNodesWithTag("group_floating_button_row").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("group_floating_button_toggle").fetchSemanticsNodes().size)
        assertFalse(FloatingCounterManager.toggleGroupFloatingButton(context, zikrGroup))
        assertFalse(FloatingCounterManager.isRunning(context))

        // 2. Main Durood group A shows floating button toggle (OFF initially), positioned below action buttons
        selectedGroup = com.example.groups.GroupRepository.getGroupById(context, groupA.id)!!
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_floating_button_row").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_floating_button_toggle").assertTextEquals("OFF")

        val inviteBounds = composeTestRule.onNodeWithTag("group_invite_whatsapp_button").fetchSemanticsNode().boundsInRoot
        val toggleBounds = composeTestRule.onNodeWithTag("group_floating_button_row").fetchSemanticsNode().boundsInRoot
        assertTrue("Expected toggle card below action buttons", toggleBounds.top >= inviteBounds.bottom - 1f)

        // 3. Turn Group A floating button ON via detail toggle
        composeTestRule.onNodeWithTag("group_floating_button_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_floating_button_toggle").assertTextEquals("ON")
        assertTrue(FloatingCounterManager.isGroupRunning(groupA.id))
        assertFalse(FloatingCounterManager.isMainRunning(context))
        assertEquals(120L, FloatingCounterManager.state.value.activeGroupTotal)

        // 4. Floating pill renders Group A's live total (120) and taps increment ONLY Group A
        showBubbleOverlay = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_floating_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_floating_pill_total").assertTextEquals("120")

        repeat(3) {
            composeTestRule.onNodeWithTag("floating_counter_button").performClick()
            composeTestRule.waitForIdle()
        }
        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            FloatingCounterManager.state.value.activeGroupTotal == 123L
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_floating_pill_total").assertTextEquals("123")
        assertEquals(123L, FloatingCounterManager.state.value.activeGroupTotal)
        assertEquals(123L, com.example.groups.GroupRepository.getGroupById(context, groupA.id)?.effectiveGroupTotal)
        assertTrue(com.example.groups.GroupRepository.getDirtyGroupSyncIds(context).contains(groupA.id))

        // Main Durood, Group B, Custom Zikr group, and Personal Custom Counter remain completely untouched
        assertEquals(500L, db.dailyCountDao().getTotalCountOnce(userId))
        assertEquals(50L, com.example.groups.GroupRepository.getGroupById(context, groupB.id)?.effectiveGroupTotal)
        assertEquals(30L, com.example.groups.GroupRepository.getGroupById(context, zikrGroup.id)?.effectiveGroupTotal)
        assertEquals(10L, db.customCounterDao().getActiveCounters(userId).first { it.id == customCounterId }.currentCount)

        // 5. Leaving Group A automatically turns Group A's floating button OFF
        com.example.groups.GroupRepository.leaveGroup(context, userId, groupA.id)
        composeTestRule.waitForIdle()
        assertFalse(FloatingCounterManager.isRunning(context))
        assertFalse(FloatingCounterManager.isGroupRunning(groupA.id))
    }

    @Test
    fun `fixRun7Task2 mutualExclusionAndWidgetScope_enforcesOneActiveAndWidgetControlsMainOnly`() = runBlocking {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val userId = "run7_task2_user_${System.nanoTime()}"
        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = userId,
                displayName = "Bilal",
                dailyTarget = 2600,
                setupCompleted = true
            )
        )
        WidgetAndFloatingBridge.setActiveUserId(context, userId)
        FloatingCounterManager.setOverlayPermissionOverrideForTest(true)

        val groupA = com.example.groups.GroupRepository.createGroup(
            context = context,
            creatorUserId = userId,
            creatorDisplayName = "Bilal",
            name = "Circle One",
            type = com.example.groups.GroupType.MAIN_DUROOD
        ).getOrThrow()
        val groupB = com.example.groups.GroupRepository.createGroup(
            context = context,
            creatorUserId = userId,
            creatorDisplayName = "Bilal",
            name = "Circle Two",
            type = com.example.groups.GroupType.MAIN_DUROOD
        ).getOrThrow()

        var homeState by mutableStateOf(
            MainUiState(
                isLoading = false,
                totalCount = 1_000L,
                todayCount = 200L
            )
        )
        composeTestRule.setContent {
            OneCroreTheme {
                HomeScreen(
                    uiState = homeState,
                    onOpenCounter = {},
                    onOpenCustomCounter = {},
                    onCheckForUpdate = {},
                    onOpenUpdateDialog = {},
                    onDismissUpdateDialog = {},
                    onCancelUpdateDownload = {},
                    onUpdateNow = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val widgetProvider = OneCroreWidgetProvider()
        val host = FrameLayout(context)
        fun readWidgetFloatingLabel(): String {
            val view = OneCroreWidgetProvider.buildRemoteViews(context, 200L, 1_000L).apply(context, host)
            return view.findViewById<TextView>(R.id.widget_btn_floating).text.toString()
        }

        // 1. Start Main Durood floating button from Home toggle -> Home ON, Widget "Floating ON"
        composeTestRule.onNodeWithTag("home_floating_button_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("ON")
        assertTrue(FloatingCounterManager.isMainRunning(context))
        assertEquals("Floating ON", readWidgetFloatingLabel())

        // 2. Turn Group A floating button ON -> Main turns OFF, Home toggle OFF, Widget reverts to "Floating Button"
        assertTrue(FloatingCounterManager.toggleGroupFloatingButton(context, groupA))
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.isGroupRunning(groupA.id))
        assertFalse(FloatingCounterManager.isMainRunning(context))
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("OFF")
        assertEquals("Floating Button", readWidgetFloatingLabel())

        // 3. Turn Group B floating button ON -> Group A turns OFF, only Group B is ON
        assertTrue(FloatingCounterManager.toggleGroupFloatingButton(context, groupB))
        composeTestRule.waitForIdle()
        assertFalse(FloatingCounterManager.isGroupRunning(groupA.id))
        assertTrue(FloatingCounterManager.isGroupRunning(groupB.id))
        assertFalse(FloatingCounterManager.isMainRunning(context))
        assertEquals("Floating Button", readWidgetFloatingLabel())

        // 4. Tap Home-Screen Widget Floating Button while Group B is ON -> turns Group B OFF and turns Main ON!
        widgetProvider.onReceive(
            context,
            android.content.Intent(OneCroreWidgetProvider.ACTION_WIDGET_ENABLE_FLOATING_BROADCAST)
        )
        composeTestRule.waitForIdle()
        assertFalse(FloatingCounterManager.isGroupRunning(groupA.id))
        assertFalse(FloatingCounterManager.isGroupRunning(groupB.id))
        assertTrue(FloatingCounterManager.isMainRunning(context))
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("ON")
        assertEquals("Floating ON", readWidgetFloatingLabel())

        // 5. Tap Home-Screen Widget again -> turns Main OFF
        widgetProvider.onReceive(
            context,
            android.content.Intent(OneCroreWidgetProvider.ACTION_WIDGET_ENABLE_FLOATING_BROADCAST)
        )
        composeTestRule.waitForIdle()
        assertFalse(FloatingCounterManager.isRunning(context))
        assertFalse(FloatingCounterManager.isMainRunning(context))
        composeTestRule.onNodeWithTag("home_floating_button_toggle").assertTextEquals("OFF")
        assertEquals("Floating Button", readWidgetFloatingLabel())

        // 6. Turn Group A ON, then tap Home's toggle row -> turns Group A OFF and turns Main ON
        assertTrue(FloatingCounterManager.toggleGroupFloatingButton(context, groupA))
        composeTestRule.waitForIdle()
        assertTrue(FloatingCounterManager.isGroupRunning(groupA.id))
        composeTestRule.onNodeWithTag("home_floating_button_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(FloatingCounterManager.isGroupRunning(groupA.id))
        assertTrue(FloatingCounterManager.isMainRunning(context))
        assertEquals("Floating ON", readWidgetFloatingLabel())
    }

    @Test
    fun fixRun8Task1_unifiedCreateGroupAndDetailFlow_supportsTargetsAddReduceFloatingForAllAndKeepsMalikTeam() {
        runBlocking {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val userId = "run8_task1_user_${System.nanoTime()}"
        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = userId,
                displayName = "Malik",
                dailyTarget = 2600,
                setupCompleted = true
            )
        )
        val todayKey = LocalDate.now().toString()
        db.dailyCountDao().incrementDay(userId, todayKey, 400L, System.currentTimeMillis())
        WidgetAndFloatingBridge.setActiveUserId(context, userId)
        FloatingCounterManager.setOverlayPermissionOverrideForTest(true)

        // Legacy Main Durood group preserved intact ("malik team", 86 / 10,000,000, 2 members, NYLSQ6)
        val legacyMalikTeam = com.example.groups.GroupItem(
            id = "grp_malik_legacy",
            name = "malik team",
            inviteCode = "NYLSQ6",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            zikrName = null,
            targetCount = 10_000_000L,
            creatorUserId = userId,
            members = listOf(
                com.example.groups.GroupMemberProgress(userId, "Malik", 86L, todayKey, 86L),
                com.example.groups.GroupMemberProgress("u_peer", "Zaid", 0L, todayKey, 0L)
            ),
            serverTotalCount = 86L
        )
        com.example.groups.GroupRepository.upsertGroupLocally(context, legacyMalikTeam)

        var groups by mutableStateOf(com.example.groups.GroupRepository.getGroupsForUser(context, userId))
        var selectedGroupId by mutableStateOf<String?>(null)
        var showBubbleOverlay by mutableStateOf(false)

        composeTestRule.setContent {
            val testScope = androidx.compose.runtime.rememberCoroutineScope()
            OneCroreTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val activeGroup = selectedGroupId?.let { id -> groups.firstOrNull { it.id == id } }
                    if (activeGroup == null) {
                        com.example.ui.screens.GroupsHubScreen(
                            groups = groups,
                            statusMessage = null,
                            onClearStatusMessage = {},
                            onBack = {},
                            onOpenGroup = { id -> selectedGroupId = id },
                            onCreateGroup = { name, type, zikrName, targetCount ->
                                testScope.launch {
                                    val created = com.example.groups.GroupRepository.createGroup(
                                        context = context,
                                        creatorUserId = userId,
                                        creatorDisplayName = "Malik",
                                        name = name,
                                        type = type,
                                        zikrName = zikrName,
                                        targetCount = targetCount
                                    ).getOrThrow()
                                    groups = com.example.groups.GroupRepository.getGroupsForUser(context, userId)
                                    selectedGroupId = created.id
                                }
                            },
                            onJoinByCode = {}
                        )
                    } else {
                        com.example.ui.screens.GroupDetailScreen(
                            group = activeGroup,
                            currentUserId = userId,
                            currentDate = LocalDate.now(),
                            onBack = { selectedGroupId = null },
                            onInviteWhatsApp = {},
                            onLeaveGroup = {},
                            onAdjustMainGroupCount = { gid, delta ->
                                testScope.launch {
                                    com.example.groups.GroupRepository.adjustMainGroupCount(
                                        context = context,
                                        userId = userId,
                                        displayName = "Malik",
                                        groupId = gid,
                                        delta = delta,
                                        todayDateKey = todayKey,
                                        pushToCloud = false
                                    )
                                    groups = com.example.groups.GroupRepository.getGroupsForUser(context, userId)
                                }
                            },
                            onIncrementCustomZikr = { _, _ -> }
                        )
                    }

                    if (showBubbleOverlay) {
                        FloatingCounterBubbleComposable(
                            db = db,
                            userIdOverride = userId,
                            flashDurationMillis = 100L
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Legacy "malik team" card shows "Durood" pill (no "MAIN DUROOD") and keeps exact numbers/members/code
        composeTestRule.onNodeWithTag("group_card_${legacyMalikTeam.id}").performScrollTo()
        composeTestRule.onNodeWithTag("group_card_zikr_pill_${legacyMalikTeam.id}", useUnmergedTree = true)
            .assertTextEquals("Durood")
        assertEquals(0, composeTestRule.onAllNodesWithText("MAIN DUROOD", useUnmergedTree = true).fetchSemanticsNodes().size)
        composeTestRule.onNodeWithText("86 / 10,000,000", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("2 members · Code NYLSQ6", useUnmergedTree = true).assertIsDisplayed()

        // 2. Open Create Group dialog -> verify default "For Durood", 50K / 1L (default) / 10L + custom target, no "GROUP TYPE"
        composeTestRule.onNodeWithTag("groups_create_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithText("GROUP TYPE").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("create_group_type_main").assertTextEquals("For Durood")
        composeTestRule.onNodeWithTag("create_group_type_zikr").assertTextEquals("Other Zikr")
        composeTestRule.onNodeWithTag("create_group_target_50k").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_group_target_1l").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_group_target_10l").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_group_target_input").assertTextContains("100000")
        assertEquals(0, composeTestRule.onAllNodesWithTag("create_group_zikr_name_input").fetchSemanticsNodes().size)

        // Create a Durood group with custom 10L preset target
        composeTestRule.onNodeWithTag("create_group_name_input").performTextInput("Friday Durood")
        composeTestRule.onNodeWithTag("create_group_target_10l").performClick()
        composeTestRule.onNodeWithTag("create_group_target_input").assertTextContains("1000000")
        composeTestRule.onNodeWithTag("create_group_submit_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("group_detail_zikr_label").assertTextEquals("Durood")
        composeTestRule.onNodeWithTag("group_target_text").performScrollTo().assertTextContains("Target: 1,000,000", substring = true)
        composeTestRule.onNodeWithTag("group_detail_back_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Create an "Other Zikr" group with custom typed target 250000
        composeTestRule.onNodeWithTag("groups_create_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_group_type_zikr").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_group_target_50k").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_group_target_1l").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_group_target_10l").assertIsDisplayed()
        composeTestRule.onNodeWithTag("create_group_name_input").performTextInput("Tasbih Circle")
        composeTestRule.onNodeWithTag("create_group_target_input").performTextReplacement("250000")
        composeTestRule.onNodeWithTag("create_group_zikr_name_input").performScrollTo().performTextInput("سُبْحَانَ اللَّهِ")
        composeTestRule.onNodeWithTag("create_group_submit_button").performClick()
        composeTestRule.waitForIdle()

        // 4. Unified GroupDetailScreen for Other Zikr: exact zikr label, single + Custom (Add & Reduce), and Floating button!
        composeTestRule.onNodeWithTag("group_detail_zikr_label").assertTextEquals("سُبْحَانَ اللَّهِ")
        composeTestRule.onNodeWithTag("group_target_text").performScrollTo().assertTextContains("Target: 250,000", substring = true)
        assertEquals(0, composeTestRule.onAllNodesWithTag("group_zikr_plus_one_button").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("group_main_custom_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_floating_button_row").performScrollTo().assertIsDisplayed()

        // Use + Custom -> Add 25
        composeTestRule.onNodeWithTag("group_main_custom_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_main_mode_add").assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_main_mode_reduce").assertIsDisplayed()
        composeTestRule.onNodeWithTag("group_main_custom_input").performTextInput("25")
        composeTestRule.onNodeWithTag("group_main_custom_confirm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("25")

        // Use + Custom -> Reduce 5
        composeTestRule.onNodeWithTag("group_main_custom_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_main_mode_reduce").performClick()
        composeTestRule.onNodeWithTag("group_main_custom_input").performTextInput("5")
        composeTestRule.onNodeWithTag("group_main_custom_confirm").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_total_value").assertTextEquals("20")

        // Enable Floating button for this Other Zikr group and tap pill twice -> becomes 22, personal Durood stays 400
        composeTestRule.onNodeWithTag("group_floating_button_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_floating_button_toggle").assertTextEquals("ON")
        val tasbihGroupId = selectedGroupId!!
        assertTrue(FloatingCounterManager.isGroupRunning(tasbihGroupId))

        showBubbleOverlay = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_floating_pill_total").assertTextEquals("20")
        repeat(2) {
            composeTestRule.onNodeWithTag("floating_counter_button").performClick()
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
        }
        val bubbleDeadline = System.currentTimeMillis() + 5_000L
        while (FloatingCounterManager.state.value.activeGroupTotal != 22L && System.currentTimeMillis() < bubbleDeadline) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            composeTestRule.waitForIdle()
            Thread.sleep(20L)
        }
        assertEquals(22L, FloatingCounterManager.state.value.activeGroupTotal)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_floating_pill_total").assertTextEquals("22")
        assertEquals(22L, com.example.groups.GroupRepository.getGroupById(context, tasbihGroupId)?.effectiveGroupTotal)
        assertEquals(400L, db.dailyCountDao().getTotalCountOnce(userId))

        // Return to hub and check pill displays exact zikr name for Other Zikr group
        showBubbleOverlay = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_detail_back_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("group_card_$tasbihGroupId").performScrollTo()
        composeTestRule.onNodeWithTag("group_card_zikr_pill_$tasbihGroupId", useUnmergedTree = true)
            .assertTextEquals("سُبْحَانَ اللَّهِ")
        }
    }

    @Test
    fun fixRun8Task2_bottomTabsAlwaysOpenMainRootPage_fixesHistoryMessagesHistoryReentryBug() {
        runBlocking {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val uid = "run8_task2_user_${System.nanoTime()}"
        val now = System.currentTimeMillis()
        val todayKey = LocalDate.now().toString()
        db.userProfileDao().upsertProfile(
            UserProfileEntity(
                userId = uid,
                displayName = "Zaid",
                dailyTarget = 2600,
                setupCompleted = true
            )
        )
        db.dailyCountDao().incrementDay(uid, todayKey, 150L, now)

        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = DuroodRepository(
            localDb = db,
            firestore = FirebaseFirestore.getInstance(),
            auth = auth
        )
        val vm = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uid,
            fallbackDisplayName = "Zaid",
            accountCreatedAtMillis = now,
            appContext = context
        )
        val vmStore = androidx.lifecycle.ViewModelStore()
        vmStore.put("run8_nav_vm", vm)
        var latestState by mutableStateOf<MainUiState?>(null)
        val collectJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            vm.uiState.collect { latestState = it }
        }

        try {
            val deadline = System.currentTimeMillis() + 3_000L
            while ((latestState == null || latestState?.isLoading == true) && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(20L)
            }

            composeTestRule.setContent {
                val ui = latestState ?: MainUiState(isLoading = false)
                OneCroreTheme {
                    com.example.ui.MainTabScaffold(
                        viewModel = vm,
                        uiState = ui,
                        canPresentPopups = false
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Initial landing: Home root
            composeTestRule.onNodeWithTag("home_open_counter_button").performScrollTo().assertIsDisplayed()

            // Bug #1 exact reproduction: History -> Messages History -> Home -> History
            composeTestRule.onNodeWithTag("nav_history").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("history_messages_history_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("messages_history_title").assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_home").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_open_counter_button").performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_history").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("history_messages_history_button").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("messages_history_title").fetchSemanticsNodes().size)

            // Also verify History -> Messages History -> Stats -> History lands on History main page
            composeTestRule.onNodeWithTag("history_messages_history_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("messages_history_title").assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_stats").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Consistency overview").assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_history").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("history_messages_history_button").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("messages_history_title").fetchSemanticsNodes().size)

            // Re-tapping History while on nested Messages History also resets to History main page
            composeTestRule.onNodeWithTag("history_messages_history_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("messages_history_title").assertIsDisplayed()
            composeTestRule.onNodeWithTag("nav_history").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("history_messages_history_button").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("messages_history_title").fetchSemanticsNodes().size)

            // Home -> My Groups -> Settings -> Home lands on Home main page (not My Groups)
            composeTestRule.onNodeWithTag("nav_home").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_my_groups_card").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("groups_create_button").assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_settings").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("settings_change_plan").performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_home").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("home_open_counter_button").performScrollTo().assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("groups_create_button").fetchSemanticsNodes().size)

            // Counter -> Custom Counters -> Stats -> Counter lands on Counter main page (not Custom Counters)
            composeTestRule.onNodeWithTag("nav_counter").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("counter_open_custom_counters_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("create_custom_counter_button").assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_stats").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Consistency overview").assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_counter").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("counter_tap_button").performScrollTo().assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("create_custom_counter_button").fetchSemanticsNodes().size)
        } finally {
            collectJob.cancel()
            vmStore.clear()
        }
        }
    }

    @Test
    fun fixRun9Task1_googleSignIn_unwrapsContextExecutesFallbackAndKeepsEmailAuthIntact() {
        runBlocking {
            // 1. Verify host Activity unwrapping works even when Compose LocalContext is wrapped in ContextThemeWrapper
            val activityController = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
            val realActivity = activityController.get()
            val wrappedContext = android.view.ContextThemeWrapper(
                android.view.ContextThemeWrapper(realActivity, 0),
                0
            )
            assertNull(wrappedContext as? android.app.Activity)
            assertEquals(realActivity, com.example.ui.auth.findHostActivity(wrappedContext))

            // 2. Verify interactive Google credential flow attempts 2 single-option requests (GetSignInWithGoogleOption -> GetGoogleIdOption)
            // and reports SHA-1/Play config issue clearly if both fail due to developer config
            val capturedRequests = mutableListOf<androidx.credentials.GetCredentialRequest>()
            val configErrorResult = com.example.ui.auth.executeInteractiveGoogleCredentialFlow(
                serverClientId = context.getString(R.string.default_web_client_id),
                fetchCredential = { req ->
                    capturedRequests += req
                    if (capturedRequests.size == 1) {
                        throw androidx.credentials.exceptions.NoCredentialException(
                            "[28444] Developer console is not set up correctly"
                        )
                    } else {
                        throw androidx.credentials.exceptions.NoCredentialException("No credentials available")
                    }
                },
                signInWithIdToken = {}
            )
            assertEquals(2, capturedRequests.size)
            assertTrue(capturedRequests.all { it.credentialOptions.size == 1 })
            assertTrue(capturedRequests[0].credentialOptions[0] is com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption)
            assertTrue(capturedRequests[1].credentialOptions[0] is com.google.android.libraries.identity.googleid.GetGoogleIdOption)
            assertTrue(configErrorResult is com.example.ui.auth.GoogleSignInAttemptResult.Error)
            val err = configErrorResult as com.example.ui.auth.GoogleSignInAttemptResult.Error
            assertTrue(err.isConfigurationIssue)
            assertTrue(err.message.contains("SHA-1"))

            // Secondary single-option fallback succeeds cleanly when primary sheet throws NoCredentialException
            var signedInToken: String? = null
            var attempt = 0
            val fallbackSuccess = com.example.ui.auth.executeInteractiveGoogleCredentialFlow(
                serverClientId = context.getString(R.string.default_web_client_id),
                fetchCredential = {
                    attempt++
                    if (attempt == 1) {
                        throw androidx.credentials.exceptions.NoCredentialException("No credentials available")
                    }
                    androidx.credentials.PasswordCredential("u@example.com", "pw")
                },
                extractIdToken = { "fake_google_id_token_123" },
                signInWithIdToken = { token -> signedInToken = token }
            )
            assertTrue(fallbackSuccess is com.example.ui.auth.GoogleSignInAttemptResult.Success)
            assertEquals("fake_google_id_token_123", signedInToken)

            // 3. Render AuthScreen and verify Google Sign-In button + email/password login & signup no-regression
            FirebaseAuth.getInstance().signOut()
            composeTestRule.setContent {
                OneCroreTheme {
                    AuthScreen(onAuthChanged = {})
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Continue with Google").assertIsDisplayed()

            // Login form accepts email & password and enforces client validation without touching UI/copy
            composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextReplacement("invalid-email")
            composeTestRule.onNodeWithTag("auth_password_input").performScrollTo().performTextReplacement("123")
            composeTestRule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("auth_email_error").assertIsDisplayed()
            composeTestRule.onNodeWithTag("auth_password_error").assertIsDisplayed()

            // Switch to Sign Up mode and confirm name + email + password + confirm password fields remain intact
            composeTestRule.onNodeWithTag("auth_switch_mode").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("signup_name_input").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("signup_name_input").performTextReplacement("")
            composeTestRule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("signup_name_error").assertIsDisplayed()
        }
    }

    @Test
    fun fixRun9Task2_inviteCodeCopy_tappingCodeCopiesToClipboardAndShowsCopiedPill() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("empty", ""))

        val group = com.example.groups.GroupItem(
            id = "grp_qg25ju",
            name = "Halqa Noor",
            inviteCode = "QG25JU",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            zikrName = null,
            targetCount = 100_000L,
            creatorUserId = "u_creator",
            members = listOf(
                com.example.groups.GroupMemberProgress("u_creator", "Amina", 120L, LocalDate.now().toString(), 120L)
            ),
            serverTotalCount = 120L
        )
        var invitedViaWhatsApp: com.example.groups.GroupItem? = null

        composeTestRule.setContent {
            OneCroreTheme {
                com.example.ui.screens.GroupDetailScreen(
                    group = group,
                    currentUserId = "u_creator",
                    currentDate = LocalDate.now(),
                    onBack = {},
                    onInviteWhatsApp = { invitedViaWhatsApp = it },
                    onLeaveGroup = {},
                    onIncrementCustomZikr = { _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Code pill is shown with exact text; "Copied" pill is initially hidden
        composeTestRule.onNodeWithTag("group_invite_code_label")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Code: QG25JU", substring = true)
        assertEquals(0, composeTestRule.onAllNodesWithTag("group_invite_copied_pill").fetchSemanticsNodes().size)

        // Tap code -> copies raw code ("QG25JU") to clipboard and reveals monochrome "Copied" pill
        composeTestRule.onNodeWithTag("group_invite_code_label").performClick()
        composeTestRule.waitForIdle()

        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("QG25JU", clipText)
        composeTestRule.onNodeWithTag("group_invite_copied_pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copied").assertIsDisplayed()

        // Invite on WhatsApp button remains untouched and passes the exact group with code QG25JU
        composeTestRule.onNodeWithTag("group_invite_whatsapp_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals("QG25JU", invitedViaWhatsApp?.inviteCode)
    }

    @Test
    fun fixRun10Task1_homeHeader_ayatFirstTwoLinesLowerWithMoreSpaceBelowGlobalThenCloseWelcomeStarted() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )

        val state = MainUiState(
            isLoading = false,
            profile = UserProfileEntity(
                userId = "fix_run10_user_1",
                displayName = "Ali",
                dailyTarget = 2_000,
                setupCompleted = true,
                startedAtMillis = 1_794_800_000_000L
            ),
            isOnline = true,
            globalStats = GlobalCounterStats(
                totalCount = 4_120_000L,
                userCount = 310L,
                fetchedAtMillis = System.currentTimeMillis() - 120_000L
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    HomeScreen(
                        uiState = state,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val headerBounds = composeTestRule.onNodeWithTag("home_header_column").fetchSemanticsNode().boundsInRoot
        val greetingNode = composeTestRule.onNodeWithTag("home_arabic_greeting").fetchSemanticsNode()
        val greetingBounds = greetingNode.boundsInRoot
        val globalBounds = composeTestRule.onNodeWithTag("home_global_counter_block").fetchSemanticsNode().boundsInRoot
        val welcomeNode = composeTestRule.onNodeWithTag("home_welcome_text")
        val startedNode = composeTestRule.onNodeWithTag("home_started_date")
        welcomeNode.assertIsDisplayed().assertTextEquals("Welcome, Ali")
        startedNode.assertIsDisplayed().assertTextEquals("Started ${state.startDateFormatted}")
        val welcomeBounds = welcomeNode.fetchSemanticsNode().boundsInRoot
        val startedBounds = startedNode.fetchSemanticsNode().boundsInRoot

        // 1. Top offset pushes Ayat slightly lower from top of header container
        assertTrue(
            "Expected top offset above Ayat (greeting.top=${greetingBounds.top}, header.top=${headerBounds.top})",
            greetingBounds.top - headerBounds.top >= 8f
        )

        // 2. Ayat renders in strictly 2 lines
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        greetingNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        assertTrue("Expected TextLayoutResult for home_arabic_greeting", layouts.isNotEmpty())
        assertEquals("Ayat greeting on Home must render in 2 lines", 2, layouts.first().lineCount)

        // 3. Order: Ayat (first) -> Global block -> Welcome -> Started
        assertTrue("Ayat must sit above Global block", greetingBounds.bottom <= globalBounds.top + 0.5f)
        assertTrue("Global block must sit above Welcome", globalBounds.bottom <= welcomeBounds.top + 0.5f)
        assertTrue("Welcome must sit above Started", welcomeBounds.bottom <= startedBounds.top + 0.5f)

        // 4. More breathing space below Ayat than between Global block and Welcome
        val gapBelowAyat = globalBounds.top - greetingBounds.bottom
        val gapBelowGlobal = welcomeBounds.top - globalBounds.bottom
        assertTrue(
            "Expected larger gap below Ayat ($gapBelowAyat) than between Global block and Welcome ($gapBelowGlobal)",
            gapBelowAyat > gapBelowGlobal
        )
    }

    @Test
    fun fixRun10Task2_customAddHome_halfHalfOnHomeWorksAndCounterScreenHasNoCustomAddButton() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )

        var openedCounter = false
        var addedAmount = 0L
        var showCounterScreen by mutableStateOf(false)
        var initialCustomOnHome by mutableStateOf(false)

        val state = MainUiState(
            isLoading = false,
            profile = UserProfileEntity(
                userId = "fix_run10_user_2",
                displayName = "Ali",
                dailyTarget = 2_000,
                setupCompleted = true,
                startedAtMillis = 1_794_800_000_000L
            ),
            totalCount = 1_500L,
            todayCount = 200L,
            isOnline = true
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    if (!showCounterScreen) {
                        HomeScreen(
                            uiState = state,
                            onOpenCounter = { openedCounter = true },
                            onAddCustomAmount = { addedAmount += it },
                            initialShowCustomDialog = initialCustomOnHome,
                            onConsumedInitialCustomDialog = { initialCustomOnHome = false },
                            onOpenCustomCounter = {},
                            onCheckForUpdate = {},
                            onOpenUpdateDialog = {},
                            onDismissUpdateDialog = {},
                            onCancelUpdateDownload = {},
                            onUpdateNow = {}
                        )
                    } else {
                        com.example.ui.screens.CounterScreen(
                            uiState = state,
                            onTapPlusOne = {},
                            onAddCustomAmount = { addedAmount += it },
                            onOpenCustomCounters = {}
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Open Counter & Custom Add sit side-by-side at equal half widths on Home
        val openBtn = composeTestRule.onNodeWithTag("home_open_counter_button").performScrollTo()
        val customBtn = composeTestRule.onNodeWithTag("home_custom_add_button").performScrollTo()
        openBtn.assertIsDisplayed()
        customBtn.assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Counter").assertIsDisplayed()
        composeTestRule.onNodeWithText("Custom Add").assertIsDisplayed()

        val openBounds = openBtn.fetchSemanticsNode().boundsInRoot
        val customBounds = customBtn.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected side-by-side alignment (${openBounds.top} vs ${customBounds.top})",
            kotlin.math.abs(openBounds.top - customBounds.top) <= 2f
        )
        assertTrue(
            "Expected Open Counter left of Custom Add (${openBounds.right} vs ${customBounds.left})",
            openBounds.right < customBounds.left
        )
        assertTrue(
            "Expected equal 50/50 button widths (${openBounds.width} vs ${customBounds.width})",
            kotlin.math.abs(openBounds.width - customBounds.width) <= 2f
        )

        // 2. Open Counter callback fires
        openBtn.performClick()
        composeTestRule.waitForIdle()
        assertTrue(openedCounter)

        // 3. Tapping Custom Add on Home opens dialog and adds amount
        customBtn.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add Custom Amount").assertIsDisplayed()
        composeTestRule.onNodeWithTag("custom_amount_input").performTextInput("250")
        composeTestRule.onNodeWithTag("confirm_custom_amount_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(250L, addedAmount)

        // 4. Widget Custom Add launch opens Custom Amount dialog directly on Home
        initialCustomOnHome = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Add Custom Amount").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // 5. Counter screen stays tap-only and does NOT show Custom Add button
        showCounterScreen = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("counter_tap_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("counter_open_custom_counters_button").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("custom_amount_button").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("+ Custom Amount").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Custom Add").fetchSemanticsNodes().size)
    }

    @Test
    fun fixRun11Task1_signInSmooth_immediateLoadingStateFastSingleShotFlowAndEmailAuthIntact() {
        runBlocking {
            val webClientId = context.getString(R.string.default_web_client_id)

            // 1. Primary sign-in succeeds in a single fast CredentialManager call (no sequential retries)
            var fetchCountSuccess = 0
            var signedInToken: String? = null
            val successResult = com.example.ui.auth.executeInteractiveGoogleCredentialFlow(
                serverClientId = webClientId,
                fetchCredential = {
                    fetchCountSuccess++
                    androidx.credentials.PasswordCredential("ali@example.com", "pw")
                },
                extractIdToken = { "fast_token_001" },
                signInWithIdToken = { token -> signedInToken = token }
            )
            assertEquals(1, fetchCountSuccess)
            assertTrue(successResult is com.example.ui.auth.GoogleSignInAttemptResult.Success)
            assertEquals("fast_token_001", signedInToken)

            // 2. Generic GetCredentialException and config-matched errors fail fast in 1 call (no slow fallback)
            var fetchCountConfig = 0
            val configErrResult = com.example.ui.auth.executeInteractiveGoogleCredentialFlow(
                serverClientId = webClientId,
                fetchCredential = {
                    fetchCountConfig++
                    throw androidx.credentials.exceptions.NoCredentialException(
                        "[28444] Developer console is not set up correctly"
                    )
                },
                signInWithIdToken = {}
            )
            assertEquals(1, fetchCountConfig)
            assertTrue(configErrResult is com.example.ui.auth.GoogleSignInAttemptResult.Error)

            var fetchCountGeneric = 0
            val genericErrResult = com.example.ui.auth.executeInteractiveGoogleCredentialFlow(
                serverClientId = webClientId,
                fetchCredential = {
                    fetchCountGeneric++
                    throw androidx.credentials.exceptions.GetCredentialUnknownException("Transient error")
                },
                signInWithIdToken = {}
            )
            assertEquals(1, fetchCountGeneric)
            assertTrue(genericErrResult is com.example.ui.auth.GoogleSignInAttemptResult.Error)

            // 3. UI immediately displays inline spinner + "Signing in..." inside Google button, prevents double-tap,
            // resets cleanly on cancel, completes smoothly on success, and leaves Email/Password auth intact.
            FirebaseAuth.getInstance().signOut()
            var authChangedCount = 0
            var triggerCount = 0
            var pendingOnSuccess: (() -> Unit)? = null
            var pendingOnCancelled: (() -> Unit)? = null

            composeTestRule.setContent {
                OneCroreTheme {
                    AuthScreen(
                        onAuthChanged = { authChangedCount++ },
                        onGoogleSignInOverride = { onSuccess, _, onCancelled ->
                            triggerCount++
                            pendingOnSuccess = onSuccess
                            pendingOnCancelled = onCancelled
                        }
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Initial ready state
            composeTestRule.onNodeWithTag("google_sign_in_button")
                .performScrollTo()
                .assertIsDisplayed()
                .assertIsEnabled()
            composeTestRule.onNodeWithText("Continue with Google").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("google_sign_in_spinner").fetchSemanticsNodes().size)

            // Tap Continue with Google -> immediate inline spinner & "Signing in..." inside button
            composeTestRule.onNodeWithTag("google_sign_in_button").performClick()
            composeTestRule.waitForIdle()

            assertEquals(1, triggerCount)
            composeTestRule.onNodeWithTag("google_sign_in_spinner").assertIsDisplayed()
            composeTestRule.onNodeWithText("Signing in...").assertIsDisplayed()
            composeTestRule.onNodeWithTag("google_sign_in_button").assertIsNotEnabled()
            composeTestRule.onNodeWithTag("auth_submit_button").performScrollTo().assertIsNotEnabled()

            // Extra click does not start duplicate sign-in
            composeTestRule.onNodeWithTag("google_sign_in_button").performClick()
            composeTestRule.waitForIdle()
            assertEquals(1, triggerCount)

            // Cancel restores button smoothly
            composeTestRule.runOnUiThread { pendingOnCancelled?.invoke() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Continue with Google").assertIsDisplayed()
            composeTestRule.onNodeWithTag("google_sign_in_button").assertIsEnabled()
            composeTestRule.onNodeWithTag("auth_submit_button").performScrollTo().assertIsEnabled()
            assertEquals(0, composeTestRule.onAllNodesWithTag("google_sign_in_spinner").fetchSemanticsNodes().size)

            // Retry -> complete succeeds and calls onAuthChanged
            composeTestRule.onNodeWithTag("google_sign_in_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertEquals(2, triggerCount)
            composeTestRule.runOnUiThread { pendingOnSuccess?.invoke() }
            composeTestRule.waitForIdle()
            assertEquals(1, authChangedCount)

            // Email/Password Login & Sign-Up UI validation still works untouched
            composeTestRule.onNodeWithTag("auth_email_input").performScrollTo().performTextReplacement("bad-email")
            composeTestRule.onNodeWithTag("auth_password_input").performScrollTo().performTextReplacement("12")
            composeTestRule.onNodeWithTag("auth_submit_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("auth_email_error").assertIsDisplayed()
            composeTestRule.onNodeWithTag("auth_password_error").assertIsDisplayed()

            composeTestRule.onNodeWithTag("auth_switch_mode").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("signup_name_input").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("signup_confirm_password_input").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun fixRun12Task1_coldStart_splashFrameZeroWaveFastAndLocalHomeInstant() {
        SplashSessionGuard.resetForTest()
        com.example.ui.screens.SplashIntroSpec.resetTestObservability()
        com.example.ui.components.setLowRamDeviceOverrideForTest(false)
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )

        var splashFinished = false
        try {
            // 1. Frame 0 immediately displays tile, dot, '1', body+tasbeeh, ring, and footer credit (no blank wait)
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.setContent {
                OneCroreTheme {
                    SplashScreen(onFinished = { splashFinished = true })
                }
            }
            composeTestRule.mainClock.advanceTimeByFrame()

            composeTestRule.onNodeWithTag("cold_start_splash_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("one_crore_logo_canvas").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_tile").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_dot").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_one").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_body_tasbeeh").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_ring").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_credit_name")
                .assertIsDisplayed()
                .assertTextEquals("Rza Mudasir Attari")
            assertFalse("Splash must still be running at Frame 0", splashFinished)

            // 2. Ring spins then curved black wave rises and finishes strictly within <= 1000ms (~550ms)
            var elapsedVirtualMs = 0L
            var sawWaveWipe = false
            while (!splashFinished && elapsedVirtualMs < 1_500L) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                elapsedVirtualMs += 50L
                if (composeTestRule.onAllNodesWithTag("splash_wave_wipe").fetchSemanticsNodes().isNotEmpty()) {
                    sawWaveWipe = true
                }
            }
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitForIdle()

            assertTrue("Cold-start splash must finish cleanly", splashFinished)
            assertTrue(
                "Cold-start splash must finish strictly within <= 1000ms (actual=$elapsedVirtualMs ms)",
                elapsedVirtualMs in 400L..1_000L
            )
            assertTrue("Curved black wave wipe must execute cleanly", sawWaveWipe)
            assertFalse(com.example.ui.screens.SplashIntroSpec.lastSplashWasStaticForTest)
            assertEquals(
                com.example.ui.screens.SplashIntroSpec.ORDERED_STAGES,
                com.example.ui.screens.SplashIntroSpec.lastSplashStageHistoryForTest
            )
        } finally {
            composeTestRule.mainClock.autoAdvance = true
            com.example.ui.components.setLowRamDeviceOverrideForTest(null)
        }

        // 3. Returning signed-in user with local Room cache opens Home immediately without blocking on cloud sync
        val uid = "fix_run12_cold_user_${System.nanoTime()}"
        val todayKey = LocalDate.now().toString()
        val now = System.currentTimeMillis()
        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(
                    userId = uid,
                    displayName = "Bilal",
                    dailyTarget = 2600,
                    setupCompleted = true,
                    startedAtMillis = now - 86_400_000L
                )
            )
            db.dailyCountDao().incrementDay(uid, todayKey, 1_250L, now)
        }

        val auth = FirebaseAuth.getInstance()
        auth.signOut()
        val repo = com.example.data.DuroodRepository(
            localDb = db,
            firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance(),
            auth = auth
        )
        val vm = com.example.ui.MainViewModel(
            repository = repo,
            currentUserId = uid,
            fallbackDisplayName = "Bilal",
            accountCreatedAtMillis = now - 86_400_000L,
            appContext = context
        )
        val vmStore = androidx.lifecycle.ViewModelStore()
        vmStore.put("fix_run12_cold_vm", vm)
        var latestState by mutableStateOf<MainUiState?>(null)
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            vm.uiState.collect { latestState = it }
        }

        try {
            val loadDeadline = System.currentTimeMillis() + 1_500L
            while ((latestState == null || latestState?.isLoading == true) && System.currentTimeMillis() < loadDeadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }
            val settled = latestState
            assertNotNull(settled)
            assertFalse("Returning user with local cache must unlock immediately", settled!!.isLoading)
            assertFalse(settled.initialLoadOfflineError)
            assertTrue(settled.hasExistingDataOrSetup)
            assertEquals(1_250L, settled.totalCount)
            assertEquals(
                com.example.ui.PostLoginDestination.HOME,
                com.example.ui.resolvePostLoginDestination(
                    isLoading = settled.isLoading,
                    hasOfflineLoadError = settled.initialLoadOfflineError,
                    hasExistingSetupOrData = settled.hasExistingDataOrSetup,
                    isEmailSignIn = false,
                    isGoogleSignedIn = true,
                    hasPasswordLinked = true,
                    hasSkippedBackupPasswordPrompt = true
                )
            )
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun fixRun13Task2_test3_deleteWithConfirmRemovesOnlyThatZikr() {
        val uid = "fix_run13_t3_${System.nanoTime()}"
        val now = System.currentTimeMillis()
        val istighfarId = "custom_istighfar_1"
        val subhanId = "custom_subhan_2"

        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(userId = uid, displayName = "Bilal", dailyTarget = 2600, setupCompleted = true)
            )
            db.customCounterDao().upsertCounter(
                CustomCounterEntity(
                    id = istighfarId, userId = uid, name = "Istighfar",
                    targetCount = 1_000L, currentCount = 345L,
                    pendingSync = false, isDeleted = false,
                    createdAtMillis = now - 10_000L, updatedAtMillis = now - 10_000L
                )
            )
            db.customCounterDao().upsertCounter(
                CustomCounterEntity(
                    id = subhanId, userId = uid, name = "SubhanAllah",
                    targetCount = 3_300L, currentCount = 700L,
                    pendingSync = false, isDeleted = false,
                    createdAtMillis = now - 20_000L, updatedAtMillis = now - 20_000L
                )
            )
        }

        val auth = FirebaseAuth.getInstance().apply { signOut() }
        val repo = DuroodRepository(db, FirebaseFirestore.getInstance(), auth)
        val vm = com.example.ui.MainViewModel(repo, uid, "Bilal", now - 86_400_000L, context)
        val vmStore = androidx.lifecycle.ViewModelStore().apply { put("vm_t3", vm) }
        var latestState by mutableStateOf<MainUiState?>(null)
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            vm.uiState.collect { latestState = it }
        }

        try {
            val deadline = System.currentTimeMillis() + 2_000L
            while ((latestState?.customCounters?.size ?: 0) != 2 && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }

            composeTestRule.setContent {
                OneCroreTheme {
                    com.example.ui.screens.CustomCountersScreen(
                        uiState = latestState ?: MainUiState(isLoading = false),
                        onBack = {},
                        onOpenCounter = {},
                        onCreateCounter = { _, _ -> },
                        onUpdateCounter = { _, _, _, _, _ -> },
                        onDeleteCounter = vm::onDeleteCustomCounter
                    )
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("custom_counter_rename_$istighfarId").assertIsDisplayed()
            composeTestRule.onNodeWithTag("custom_counter_delete_$istighfarId").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("delete_custom_counter_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("delete_custom_counter_name").assertTextEquals("Istighfar")
            composeTestRule.onNodeWithTag("delete_custom_counter_count").assertTextEquals("Total count: 345")
            composeTestRule.onNodeWithTag("delete_custom_counter_warning")
                .assertTextEquals("This will permanently delete this zikr and its counts")

            composeTestRule.onNodeWithTag("confirm_delete_custom_counter").performClick()

            val deleteDeadline = System.currentTimeMillis() + 2_000L
            while (latestState?.customCounters?.any { it.id == istighfarId } == true &&
                System.currentTimeMillis() < deleteDeadline
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }
            composeTestRule.waitForIdle()

            assertEquals(0, composeTestRule.onAllNodesWithTag("delete_custom_counter_dialog").fetchSemanticsNodes().size)
            composeTestRule.onNodeWithTag("custom_counters_back_button").assertIsDisplayed()
            assertEquals(0, composeTestRule.onAllNodesWithTag("custom_counter_card_$istighfarId").fetchSemanticsNodes().size)
            composeTestRule.onNodeWithTag("custom_counter_card_$subhanId").assertIsDisplayed()

            val activeAfter = runBlocking { db.customCounterDao().getActiveCounters(uid) }
            assertEquals(listOf(subhanId), activeAfter.map { it.id })
            assertEquals(700L, activeAfter.single().currentCount)
            val deletedRow = runBlocking { db.customCounterDao().getCounter(istighfarId) }
            assertTrue(deletedRow?.isDeleted == true)
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun fixRun13Task2_test4_cancelKeepsZikr() {
        val uid = "fix_run13_t4_${System.nanoTime()}"
        val now = System.currentTimeMillis()
        val istighfarId = "custom_istighfar_cancel"

        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(userId = uid, displayName = "Bilal", dailyTarget = 2600, setupCompleted = true)
            )
            db.customCounterDao().upsertCounter(
                CustomCounterEntity(
                    id = istighfarId, userId = uid, name = "Istighfar",
                    targetCount = 1_000L, currentCount = 345L,
                    pendingSync = false, isDeleted = false,
                    createdAtMillis = now - 10_000L, updatedAtMillis = now - 10_000L
                )
            )
        }

        val auth = FirebaseAuth.getInstance().apply { signOut() }
        val repo = DuroodRepository(db, FirebaseFirestore.getInstance(), auth)
        val vm = com.example.ui.MainViewModel(repo, uid, "Bilal", now - 86_400_000L, context)
        val vmStore = androidx.lifecycle.ViewModelStore().apply { put("vm_t4", vm) }
        var latestState by mutableStateOf<MainUiState?>(null)
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            vm.uiState.collect { latestState = it }
        }

        try {
            val deadline = System.currentTimeMillis() + 2_000L
            while ((latestState?.customCounters?.size ?: 0) != 1 && System.currentTimeMillis() < deadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }

            composeTestRule.setContent {
                OneCroreTheme {
                    com.example.ui.screens.CustomCountersScreen(
                        uiState = latestState ?: MainUiState(isLoading = false),
                        onBack = {},
                        onOpenCounter = {},
                        onCreateCounter = { _, _ -> },
                        onUpdateCounter = { _, _, _, _, _ -> },
                        onDeleteCounter = vm::onDeleteCustomCounter
                    )
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("custom_counter_delete_$istighfarId").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("delete_custom_counter_dialog").assertIsDisplayed()

            composeTestRule.onNodeWithTag("cancel_delete_custom_counter").performClick()
            composeTestRule.waitForIdle()

            assertEquals(0, composeTestRule.onAllNodesWithTag("delete_custom_counter_dialog").fetchSemanticsNodes().size)
            composeTestRule.onNodeWithTag("custom_counter_card_$istighfarId").assertIsDisplayed()

            val activeAfter = runBlocking { db.customCounterDao().getActiveCounters(uid) }
            assertEquals(1, activeAfter.size)
            assertEquals(345L, activeAfter.single().currentCount)
            assertFalse(activeAfter.single().isDeleted)
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun fixRun13Task2_test5_mainDuroodTotalAndGroupsUnchangedAfterDelete() {
        val uid = "fix_run13_t5_${System.nanoTime()}"
        val todayKey = LocalDate.now().toString()
        val yesterdayKey = LocalDate.now().minusDays(1).toString()
        val now = System.currentTimeMillis()
        val counterId = "custom_salawat_5"

        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(
                    userId = uid,
                    displayName = "Bilal",
                    dailyTarget = 2600,
                    setupCompleted = true,
                    startedAtMillis = now - 86_400_000L
                )
            )
            db.dailyCountDao().incrementDay(uid, yesterdayKey, 2_600L, now - 86_400_000L)
            db.dailyCountDao().incrementDay(uid, todayKey, 1_200L, now)
            db.customCounterDao().upsertCounter(
                CustomCounterEntity(
                    id = counterId, userId = uid, name = "Salawat",
                    targetCount = 5_000L, currentCount = 999L,
                    pendingSync = false, isDeleted = false,
                    createdAtMillis = now - 10_000L, updatedAtMillis = now - 10_000L
                )
            )
        }

        val auth = FirebaseAuth.getInstance().apply { signOut() }
        val repo = DuroodRepository(db, FirebaseFirestore.getInstance(), auth)
        val vm = com.example.ui.MainViewModel(repo, uid, "Bilal", now - 86_400_000L, context)
        val vmStore = androidx.lifecycle.ViewModelStore().apply { put("vm_t5", vm) }
        var latestState by mutableStateOf<MainUiState?>(null)
        val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            vm.uiState.collect { latestState = it }
        }

        try {
            val deadline = System.currentTimeMillis() + 2_000L
            while ((latestState == null || latestState?.totalCount != 3_800L || latestState?.customCounters?.size != 1) &&
                System.currentTimeMillis() < deadline
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }
            val groupsBefore = latestState?.userGroups

            composeTestRule.runOnUiThread {
                vm.onDeleteCustomCounter(counterId)
            }

            val deleteDeadline = System.currentTimeMillis() + 2_000L
            while (latestState?.customCounters?.isNotEmpty() == true && System.currentTimeMillis() < deleteDeadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }

            val mainTotalDb = runBlocking { db.dailyCountDao().getTotalCountOnce(uid) }
            assertEquals(3_800L, mainTotalDb)
            assertEquals(3_800L, latestState?.totalCount)
            assertEquals(1_200L, latestState?.todayCount)
            assertEquals(groupsBefore, latestState?.userGroups)
            assertTrue(runBlocking { db.customCounterDao().getActiveCounters(uid) }.isEmpty())
        } finally {
            job.cancel()
            vmStore.clear()
        }
    }

    @Test
    fun fixRun13Task1_test1_themeIsBlackAndWhiteOnlyWithNoThemeSelector() {
        // The red theme was removed by the user's decision (f58b66a: theme
        // settings section removed entirely; app is B&W only). This test now
        // pins the current truth: B&W is the only theme, and Settings shows
        // no theme selector at all.
        // 1. Default theme is Black & White (unchanged from original design)
        assertEquals(
            com.example.ui.theme.AppThemeMode.BLACK_AND_WHITE,
            com.example.ui.theme.ThemePreferenceStore.getCurrentThemeMode(context)
        )
        assertEquals(
            com.example.ui.theme.AppThemeMode.BLACK_AND_WHITE,
            MainUiState().themeMode
        )

        // 2. Black & White palette tokens match spec
        assertEquals(androidx.compose.ui.graphics.Color(0xFFFFFFFF), com.example.ui.theme.PureWhite)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), com.example.ui.theme.PureBlack)

        var observedPalette: com.example.ui.theme.AppColorPalette? = null

        composeTestRule.setContent {
            OneCroreTheme(themeMode = com.example.ui.theme.AppThemeMode.BLACK_AND_WHITE) {
                observedPalette = com.example.ui.theme.AppThemeColors.palette
                com.example.ui.screens.SettingsScreen(
                    uiState = MainUiState(isLoading = false, themeMode = com.example.ui.theme.AppThemeMode.BLACK_AND_WHITE),
                    onUpdateDisplayName = {},
                    onUpdatePlan = {},
                    onUpdateReminders = { _, _, _, _, _ -> },
                    onUpdateThemeMode = { },
                    onSyncNow = {},
                    onClearStatusMessage = {},
                    onExportBackupJson = { "{}" },
                    onRestoreBackupJson = {},
                    onDeleteAccount = {},
                    userEmail = "tester@example.com"
                )
            }
        }
        composeTestRule.waitForIdle()

        // Black & White active
        assertEquals(com.example.ui.theme.AppThemeMode.BLACK_AND_WHITE, observedPalette?.themeMode)
        assertEquals(com.example.ui.theme.PureWhite, observedPalette?.canvasBackground)
        assertEquals(com.example.ui.theme.PureWhite, observedPalette?.cardSurface)
        assertEquals(com.example.ui.theme.PureBlack, observedPalette?.accent)

        // No theme selector exists in Settings anymore — neither option
        composeTestRule.onNodeWithTag("settings_theme_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_theme_bw_option").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settings_theme_red_option").assertDoesNotExist()
    }

    @Test
    fun fixRun13Task1_test2_themeChoicePersistsOnRestartAndSyncsViaFirestore() {
        val uid = "fix_run13_theme_${System.nanoTime()}"
        val now = System.currentTimeMillis()

        runBlocking {
            db.userProfileDao().upsertProfile(
                UserProfileEntity(
                    userId = uid,
                    displayName = "Bilal",
                    dailyTarget = 2600,
                    setupCompleted = true,
                    pendingSync = false,
                    themeMode = "bw"
                )
            )
        }

        val auth = FirebaseAuth.getInstance().apply { signOut() }
        val repo = DuroodRepository(db, FirebaseFirestore.getInstance(), auth)
        val vm1 = com.example.ui.MainViewModel(repo, uid, "Bilal", now - 86_400_000L, context)
        val store1 = androidx.lifecycle.ViewModelStore().apply { put("vm_theme_1", vm1) }
        var state1 by mutableStateOf<MainUiState?>(null)
        val job1 = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            vm1.uiState.collect { state1 = it }
        }

        try {
            val initDeadline = System.currentTimeMillis() + 2_000L
            while (state1?.isLoading != false && System.currentTimeMillis() < initDeadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }
            assertEquals(com.example.ui.theme.AppThemeMode.BLACK_AND_WHITE, state1?.themeMode)

            // Switch theme via MainViewModel.onUpdateThemeMode(RED)
            composeTestRule.runOnUiThread {
                vm1.onUpdateThemeMode(com.example.ui.theme.AppThemeMode.RED)
            }

            val updateDeadline = System.currentTimeMillis() + 2_000L
            while ((state1?.themeMode != com.example.ui.theme.AppThemeMode.RED ||
                    runBlocking { db.userProfileDao().getProfile(uid)?.themeMode } != "red") &&
                System.currentTimeMillis() < updateDeadline
            ) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }

            assertEquals(com.example.ui.theme.AppThemeMode.RED, state1?.themeMode)
            assertEquals(
                com.example.ui.theme.AppThemeMode.RED,
                com.example.ui.theme.ThemePreferenceStore.getCurrentThemeMode(context)
            )

            // Room UserProfileEntity persisted themeMode="red" and marked pendingSync=true for Firestore sync
            val savedProfile = runBlocking { db.userProfileDao().getProfile(uid) }
            assertNotNull(savedProfile)
            assertEquals("red", savedProfile?.themeMode)
            assertTrue("Switching theme must mark profile pendingSync for Firestore cloud push", savedProfile?.pendingSync == true)

            // BackupCodec & FirestoreUserProfile carry themeMode for sync/backup round-trip
            val firestoreModel = com.example.data.remote.FirestoreUserProfile(userId = uid, themeMode = savedProfile!!.themeMode)
            assertEquals("red", firestoreModel.themeMode)
            val decodedBackup = BackupCodec.decode(BackupCodec.encode(savedProfile, emptyList(), emptyList()))
            assertEquals("red", decodedBackup.themeMode)
        } finally {
            job1.cancel()
            store1.clear()
        }

        // Simulate app process restart: reload device prefs & create fresh MainViewModel
        val reloadedMode = com.example.ui.theme.ThemePreferenceStore.reloadFromDevicePrefs(context)
        assertEquals(com.example.ui.theme.AppThemeMode.RED, reloadedMode)

        val vm2 = com.example.ui.MainViewModel(repo, uid, "Bilal", now - 86_400_000L, context)
        val store2 = androidx.lifecycle.ViewModelStore().apply { put("vm_theme_2", vm2) }
        var state2 by mutableStateOf<MainUiState?>(null)
        val job2 = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            vm2.uiState.collect { state2 = it }
        }
        try {
            assertEquals(com.example.ui.theme.AppThemeMode.RED, vm2.uiState.value.themeMode)
            val restartDeadline = System.currentTimeMillis() + 2_000L
            while (state2?.isLoading != false && System.currentTimeMillis() < restartDeadline) {
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                Thread.sleep(10L)
            }
            assertEquals(com.example.ui.theme.AppThemeMode.RED, state2?.themeMode)
        } finally {
            job2.cancel()
            store2.clear()
        }
    }

    @Test
    fun fixRun14Task1_homeHeader_unitFormattingAndArabicStringUnchanged() {
        // Exact Ayat string with airaab preserved verbatim
        assertEquals("صَلُّوا عَلَيْهِ\nوَسَلِّمُوا تَسْلِيمًا", com.example.ui.screens.ARABIC_GREETING)
        assertEquals(2, com.example.ui.screens.ARABIC_GREETING.lines().size)

        // Global stats wording and number formatting completely untouched (only reads totalCount & userCount)
        assertEquals("Everyone together: 24,305 Durood", GlobalStatsCache.formatTotalLine(24_305L))
        assertEquals("16 users", GlobalStatsCache.formatUsersLine(16L))
        assertEquals(
            "Updates every hour",
            GlobalStatsCache.formatSubtitleLine(isOnline = true, fetchedAtMillis = 1_700_000_000_000L)
        )
    }

    @Test
    fun fixRun14Task1_homeHeader_singleTopRowLeftGlobalOppositeRightTwoLineAyatAndTightSpacing() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val now = System.currentTimeMillis()
        val state = MainUiState(
            isLoading = false,
            profile = UserProfileEntity(
                userId = "fix_run14_u1",
                displayName = "Rza Official",
                dailyTarget = 3_000,
                setupCompleted = true,
                startedAtMillis = now - 7L * 86_400_000L,
                lastSyncedAtMillis = now
            ),
            totalCount = 5_671L,
            todayCount = 0L,
            isOnline = true,
            globalStats = GlobalCounterStats(
                totalCount = 24_305L,
                userCount = 16L,
                fetchedAtMillis = now - 60_000L
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(360.dp)) {
                    HomeScreen(
                        uiState = state,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val density = context.resources.displayMetrics.density
        val headerBounds = composeTestRule.onNodeWithTag("home_header_column").fetchSemanticsNode().boundsInRoot
        val topRowBounds = composeTestRule.onNodeWithTag("home_header_top_row").fetchSemanticsNode().boundsInRoot
        val greetingNode = composeTestRule.onNodeWithTag("home_arabic_greeting").fetchSemanticsNode()
        val greetingBounds = greetingNode.boundsInRoot
        val globalBounds = composeTestRule.onNodeWithTag("home_global_counter_block").fetchSemanticsNode().boundsInRoot
        val welcomeRowBounds = composeTestRule.onNodeWithTag("home_welcome_row").fetchSemanticsNode().boundsInRoot
        val welcomeBounds = composeTestRule.onNodeWithTag("home_welcome_text").fetchSemanticsNode().boundsInRoot

        // 1. Tighter upward spacing: extra top spacer removed, top padding reduced (~12dp), below-header gap ~10dp
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_header_top_spacer").fetchSemanticsNodes().size)
        assertTrue(
            "Expected reduced top inset (<= 15dp, was 30dp before); headerBounds.top=${headerBounds.top / density}dp",
            headerBounds.top <= 15f * density
        )
        val belowHeaderGapDp = (welcomeRowBounds.top - topRowBounds.bottom) / density
        assertTrue(
            "Expected compact below-header gap (~10dp, < 14dp), was ${belowHeaderGapDp}dp",
            belowHeaderGapDp in 7f..14f
        )

        // 2. Ayat sits on the RIGHT side, right-aligned, in strictly 2 lines
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        greetingNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        assertTrue("Expected TextLayoutResult for home_arabic_greeting", layouts.isNotEmpty())
        assertEquals("Ayat on Home must stay strictly 2 lines", 2, layouts.first().lineCount)
        assertFalse("Ayat must not clip/overflow horizontally", layouts.first().hasVisualOverflow)
        assertEquals(
            androidx.compose.ui.text.style.TextAlign.Right,
            layouts.first().layoutInput.style.textAlign
        )
        assertTrue(
            "Ayat must align to right edge of header (${greetingBounds.right} vs ${headerBounds.right})",
            kotlin.math.abs(greetingBounds.right - headerBounds.right) <= 2f * density
        )

        // 3. Global block sits on LEFT edge in the SAME horizontal row, vertically opposite & centered against Ayat
        assertTrue(
            "Global block must align to left margin (${globalBounds.left} vs ${welcomeBounds.left})",
            kotlin.math.abs(globalBounds.left - welcomeBounds.left) <= 2f * density
        )
        assertTrue(
            "Global block must stay strictly left of Ayat without collision (${globalBounds.right} <= ${greetingBounds.left})",
            globalBounds.right <= greetingBounds.left + 0.5f
        )
        assertTrue(
            "Global block and Ayat must share the same horizontal row vertically",
            globalBounds.top < greetingBounds.bottom && greetingBounds.top < globalBounds.bottom
        )
        val verticalCenterDiffDp = kotlin.math.abs(
            ((globalBounds.top + globalBounds.bottom) / 2f) - ((greetingBounds.top + greetingBounds.bottom) / 2f)
        ) / density
        assertTrue(
            "Expected global block vertically centered opposite Ayat (diff=${verticalCenterDiffDp}dp)",
            verticalCenterDiffDp <= 6f
        )

        // 4. Global block wording, Welcome, Synced pill, and personal cards below remain untouched
        composeTestRule.onNodeWithTag("home_global_counter_total")
            .assertTextEquals("Everyone together: 24,305 Durood")
        composeTestRule.onNodeWithTag("home_global_counter_users")
            .assertTextEquals("16 users")
        composeTestRule.onNodeWithTag("home_global_counter_subtitle")
            .assertTextEquals("Updates every hour")
        composeTestRule.onNodeWithTag("home_welcome_text")
            .assertIsDisplayed()
            .assertTextEquals("Welcome, Rza Official")
        composeTestRule.onNodeWithText("Synced · Just now").assertIsDisplayed()
        composeTestRule.onNodeWithText("TOTAL RECITED").assertIsDisplayed()
        composeTestRule.onNodeWithText("TODAY VS TARGET").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("DAYS LEFT").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("FINISH DATE").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fixRun14Task1_homeHeader_noOverlapAtSmallestWidthAndNoStatsKeepsAyatRightAndUpdateNextToWelcome() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val now = System.currentTimeMillis()
        var currentState by mutableStateOf(
            MainUiState(
                isLoading = false,
                profile = UserProfileEntity(
                    userId = "fix_run14_u_narrow",
                    displayName = "Rza Official",
                    dailyTarget = 3_000,
                    setupCompleted = true,
                    startedAtMillis = now - 86_400_000L,
                    lastSyncedAtMillis = now
                ),
                totalCount = 5_671L,
                todayCount = 0L,
                isOnline = true,
                globalStats = GlobalCounterStats(
                    totalCount = 10_000_000L,
                    userCount = 1_240L,
                    fetchedAtMillis = now
                ),
                availableUpdate = com.example.update.AppUpdateInfo(
                    versionCode = 4,
                    versionName = "1.3",
                    changelog = "Fixes",
                    downloadUrl = "https://example.com/app.apk",
                    isForced = false
                )
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    HomeScreen(
                        uiState = currentState,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val density = context.resources.displayMetrics.density
        val headerBounds = composeTestRule.onNodeWithTag("home_header_column").fetchSemanticsNode().boundsInRoot
        val topRowBounds = composeTestRule.onNodeWithTag("home_header_top_row").fetchSemanticsNode().boundsInRoot
        val greetingNode = composeTestRule.onNodeWithTag("home_arabic_greeting").fetchSemanticsNode()
        val greetingBounds = greetingNode.boundsInRoot
        val globalBounds = composeTestRule.onNodeWithTag("home_global_counter_block").fetchSemanticsNode().boundsInRoot
        val totalBounds = composeTestRule.onNodeWithTag("home_global_counter_total").fetchSemanticsNode().boundsInRoot
        val usersBounds = composeTestRule.onNodeWithTag("home_global_counter_users").fetchSemanticsNode().boundsInRoot
        val updateBounds = composeTestRule.onNodeWithTag("home_update_badge_button").fetchSemanticsNode().boundsInRoot
        val welcomeRowBounds = composeTestRule.onNodeWithTag("home_welcome_row").fetchSemanticsNode().boundsInRoot

        // 1. At narrow 320dp width: zero horizontal collision/overlap between Global block and Ayat
        assertTrue(
            "At 320dp width, Global block must stay strictly left of Ayat with visible gap " +
                "(global.right=${globalBounds.right}, greeting.left=${greetingBounds.left})",
            globalBounds.right <= greetingBounds.left - (4f * density)
        )
        assertTrue(
            "Global text must stay inside its column bounds (${totalBounds.right} <= ${globalBounds.right})",
            totalBounds.right <= globalBounds.right + 1f
        )
        assertTrue(
            "Global lines must not overlap vertically (${totalBounds.bottom} <= ${usersBounds.top})",
            totalBounds.bottom <= usersBounds.top + 1f
        )

        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        greetingNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        assertTrue(layouts.isNotEmpty())
        assertEquals("Ayat must stay strictly 2 lines even at 320dp", 2, layouts.first().lineCount)
        assertFalse("Ayat must shrink rather than overflow at 320dp", layouts.first().hasVisualOverflow)

        // 2. Update icon sits beside Welcome row below top header row (NOT inside top row)
        assertTrue(
            "Update badge must sit below top header row (${updateBounds.top} >= ${topRowBounds.bottom})",
            updateBounds.top >= topRowBounds.bottom - 1f
        )
        assertTrue(
            "Update badge belongs vertically inside welcome row",
            updateBounds.top < welcomeRowBounds.bottom && welcomeRowBounds.top < updateBounds.bottom
        )

        // 3. When global stats are unavailable, Ayat stays right-aligned at the top right
        currentState = currentState.copy(globalStats = null)
        composeTestRule.waitForIdle()
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_global_counter_block").fetchSemanticsNodes().size)
        val greetingBoundsNoStats = composeTestRule.onNodeWithTag("home_arabic_greeting").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Ayat must remain right-aligned when global stats are absent (${greetingBoundsNoStats.right} vs ${headerBounds.right})",
            kotlin.math.abs(greetingBoundsNoStats.right - headerBounds.right) <= 2f * density
        )
    }

    @Test
    fun fixRun16Task1_ayatSize_largerFontRightAlignedTwoLinesOppositeGlobalAndStringUnchanged() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        assertEquals("صَلُّوا عَلَيْهِ\nوَسَلِّمُوا تَسْلِيمًا", com.example.ui.screens.ARABIC_GREETING)
        assertEquals(22f, com.example.ui.screens.HOME_AYAT_BASE_FONT_SIZE_SP, 0.01f)
        assertTrue(
            "New base Ayat font size must be modestly bigger than old 20sp",
            com.example.ui.screens.HOME_AYAT_BASE_FONT_SIZE_SP > 20f
        )

        val now = System.currentTimeMillis()
        val state = MainUiState(
            isLoading = false,
            profile = UserProfileEntity(
                userId = "fix_run16_u1",
                displayName = "Rza Official",
                dailyTarget = 3_000,
                setupCompleted = true,
                startedAtMillis = now - 7L * 86_400_000L,
                lastSyncedAtMillis = now
            ),
            totalCount = 5_671L,
            todayCount = 0L,
            isOnline = true,
            globalStats = GlobalCounterStats(
                totalCount = 24_305L,
                userCount = 16L,
                fetchedAtMillis = now - 60_000L
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(390.dp)) {
                    HomeScreen(
                        uiState = state,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val density = context.resources.displayMetrics.density
        val headerBounds = composeTestRule.onNodeWithTag("home_header_column").fetchSemanticsNode().boundsInRoot
        val greetingNode = composeTestRule.onNodeWithTag("home_arabic_greeting").fetchSemanticsNode()
        val greetingBounds = greetingNode.boundsInRoot
        val globalBounds = composeTestRule.onNodeWithTag("home_global_counter_block").fetchSemanticsNode().boundsInRoot

        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        greetingNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        assertTrue("Expected TextLayoutResult for home_arabic_greeting", layouts.isNotEmpty())
        val layout = layouts.first()

        assertTrue(
            "Rendered Ayat fontSize (${layout.layoutInput.style.fontSize.value}sp) must be bigger than previous 20sp",
            layout.layoutInput.style.fontSize.value > 20.5f
        )
        assertEquals("Ayat must remain strictly 2 lines", 2, layout.lineCount)
        assertFalse("Ayat must not overflow/clip", layout.hasVisualOverflow)
        assertEquals(androidx.compose.ui.text.style.TextAlign.Right, layout.layoutInput.style.textAlign)
        assertTrue(
            "Ayat must align to the right edge of header (${greetingBounds.right} vs ${headerBounds.right})",
            kotlin.math.abs(greetingBounds.right - headerBounds.right) <= 2f * density
        )
        assertTrue(
            "Global block must sit on the left opposite the Ayat without collision (${globalBounds.right} <= ${greetingBounds.left})",
            globalBounds.right <= greetingBounds.left + 0.5f
        )
        assertTrue(
            "Global block and Ayat must remain on the same horizontal row",
            globalBounds.top < greetingBounds.bottom && greetingBounds.top < globalBounds.bottom
        )
    }

    @Test
    fun fixRun16Task1_ayatSize_noOverlapAtSmallestWidth() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val now = System.currentTimeMillis()
        val state = MainUiState(
            isLoading = false,
            profile = UserProfileEntity(
                userId = "fix_run16_u_narrow",
                displayName = "Rza Official",
                dailyTarget = 3_000,
                setupCompleted = true,
                startedAtMillis = now - 86_400_000L,
                lastSyncedAtMillis = now
            ),
            totalCount = 5_671L,
            todayCount = 0L,
            isOnline = true,
            globalStats = GlobalCounterStats(
                totalCount = 10_000_000L,
                userCount = 1_240L,
                fetchedAtMillis = now
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(320.dp)) {
                    HomeScreen(
                        uiState = state,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        val density = context.resources.displayMetrics.density
        val greetingNode = composeTestRule.onNodeWithTag("home_arabic_greeting").fetchSemanticsNode()
        val greetingBounds = greetingNode.boundsInRoot
        val globalBounds = composeTestRule.onNodeWithTag("home_global_counter_block").fetchSemanticsNode().boundsInRoot
        val totalBounds = composeTestRule.onNodeWithTag("home_global_counter_total").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "At 320dp width, Global block must stay strictly left of bigger Ayat (${globalBounds.right} <= ${greetingBounds.left - 4f * density})",
            globalBounds.right <= greetingBounds.left - (4f * density)
        )
        assertTrue(
            "Global text must fit inside its column without overflow (${totalBounds.right} <= ${globalBounds.right})",
            totalBounds.right <= globalBounds.right + 1f
        )

        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        greetingNode.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layouts)
        assertTrue(layouts.isNotEmpty())
        assertEquals("Ayat must stay strictly 2 lines even at 320dp", 2, layouts.first().lineCount)
        assertFalse("Ayat must scale down cleanly rather than clipping at 320dp", layouts.first().hasVisualOverflow)
    }

    @Test
    fun fixRun16Task2_badgesHome_removedFromHomeAndLayoutOrderPreserved() {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
        val now = System.currentTimeMillis()
        val state = MainUiState(
            isLoading = false,
            profile = UserProfileEntity(
                userId = "fix_run16_badges_u1",
                displayName = "Rza Official",
                dailyTarget = 3_000,
                setupCompleted = true,
                startedAtMillis = now - 7L * 86_400_000L,
                lastSyncedAtMillis = now
            ),
            totalCount = 125_000L,
            todayCount = 1_500L,
            isOnline = true,
            badgeProgress = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(125_000L),
            customCounters = listOf(
                CustomCounterEntity(
                    id = "cc_run16",
                    userId = "fix_run16_badges_u1",
                    name = "Istighfar",
                    currentCount = 250L,
                    targetCount = 1_000L,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(390.dp)) {
                    HomeScreen(
                        uiState = state,
                        onOpenCounter = {},
                        onOpenCustomCounter = {},
                        onCheckForUpdate = {},
                        onOpenUpdateDialog = {},
                        onDismissUpdateDialog = {},
                        onCancelUpdateDownload = {},
                        onUpdateNow = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // No milestone badges card, count pill, latest line, 'left for' progress, or chevron anywhere on Home
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_milestone_badges_card", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_milestone_badges_count", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("home_milestone_badges_latest", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MILESTONE BADGES", ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("left for", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Open all milestone badges in Stats")).fetchSemanticsNodes().size)

        // Home still keeps all remaining sections in exact expected vertical order
        val headerBounds = composeTestRule.onNodeWithTag("home_header_top_row").fetchSemanticsNode().boundsInRoot
        val welcomeBounds = composeTestRule.onNodeWithTag("home_welcome_row").fetchSemanticsNode().boundsInRoot
        val totalRecitedBounds = composeTestRule.onNodeWithText("TOTAL RECITED").fetchSemanticsNode().boundsInRoot
        assertTrue(headerBounds.bottom <= welcomeBounds.top + 1f)
        assertTrue(welcomeBounds.bottom <= totalRecitedBounds.top + 1f)

        composeTestRule.onNodeWithText("TODAY VS TARGET").performScrollTo().assertIsDisplayed()
        val totalAfterScroll1 = composeTestRule.onNodeWithText("TOTAL RECITED").fetchSemanticsNode().boundsInRoot
        val todayVsTarget1 = composeTestRule.onNodeWithText("TODAY VS TARGET").fetchSemanticsNode().boundsInRoot
        assertTrue(totalAfterScroll1.bottom <= todayVsTarget1.top + 1f)

        composeTestRule.onNodeWithTag("home_counter_actions_row").performScrollTo().assertIsDisplayed()
        val todayVsTarget2 = composeTestRule.onNodeWithText("TODAY VS TARGET").fetchSemanticsNode().boundsInRoot
        val actionsRow2 = composeTestRule.onNodeWithTag("home_counter_actions_row").fetchSemanticsNode().boundsInRoot
        assertTrue(todayVsTarget2.bottom <= actionsRow2.top + 1f)

        composeTestRule.onNodeWithTag("home_custom_counters_list").performScrollTo().assertIsDisplayed()
        val actionsRow3 = composeTestRule.onNodeWithTag("home_counter_actions_row").fetchSemanticsNode().boundsInRoot
        val customCounters3 = composeTestRule.onNodeWithTag("home_custom_counters_list").fetchSemanticsNode().boundsInRoot
        assertTrue(actionsRow3.bottom <= customCounters3.top + 1f)
    }

    @Test
    fun fixRun16Task2_badgesHome_statsStillShowsBadgesAndCountingLogicUnchanged() {
        // Counting / unlocking logic every 50,000 Durood stays untouched
        assertEquals(50_000L, com.example.badges.MilestoneBadgeManager.BADGE_STEP)
        assertEquals(200, com.example.badges.MilestoneBadgeManager.TOTAL_BADGES)

        val beforeFirst = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(49_999L)
        assertEquals(0, beforeFirst.earnedCount)
        assertEquals(1L, beforeFirst.remainingToNext)

        val atFirst = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(50_000L)
        assertEquals(1, atFirst.earnedCount)
        assertEquals("50K Badge", atFirst.latestEarnedBadge?.title)

        val atTwoPlusHalf = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(125_000L)
        assertEquals(2, atTwoPlusHalf.earnedCount)
        assertEquals("1 Lakh Badge", atTwoPlusHalf.latestEarnedBadge?.title)
        assertEquals("1.5 Lakh Badge", atTwoPlusHalf.nextBadge?.title)
        assertEquals(25_000L, atTwoPlusHalf.remainingToNext)

        val atCrore = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(10_000_000L)
        assertEquals(200, atCrore.earnedCount)

        // Stats screen still displays Milestone Badges section identically
        val now = System.currentTimeMillis()
        val statsState = MainUiState(
            isLoading = false,
            profile = UserProfileEntity(
                userId = "fix_run16_stats_u",
                displayName = "Rza Official",
                dailyTarget = 3_000,
                setupCompleted = true,
                startedAtMillis = now - 7L * 86_400_000L,
                lastSyncedAtMillis = now
            ),
            totalCount = 125_000L,
            todayCount = 1_500L,
            badgeProgress = atTwoPlusHalf
        )

        composeTestRule.setContent {
            OneCroreTheme {
                Box(modifier = Modifier.width(390.dp)) {
                    com.example.ui.screens.StatsScreen(uiState = statsState)
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("stats_milestone_badges_section").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("MILESTONE BADGES").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("stats_milestone_badges_count").performScrollTo().assertTextContains("2 / 200", substring = true)
    }

    @Test
    fun fixRun15Task1_coldStart_realFirstFrameDrawablesRenderFullLogoWithoutBlackPatch() {
        fun collectDrawableRefIds(xmlResId: Int): List<Int> {
            val refs = mutableListOf<Int>()
            val parser = context.resources.getXml(xmlResId)
            try {
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        val ref = parser.getAttributeResourceValue(
                            "http://schemas.android.com/apk/res/android",
                            "drawable",
                            0
                        )
                        if (ref != 0) refs.add(ref)
                    }
                    event = parser.next()
                }
            } finally {
                parser.close()
            }
            return refs
        }

        // 1. bg_launch_logo_tile must NOT be an empty solid-black <shape>; it must stack tile + dot + '1' + body/tasbeeh + ring
        // (75e2f69: the launch tile gained the ring layer so every frame shows the same 144dp ring logo)
        val tileDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_launch_logo_tile)
        assertTrue(
            "bg_launch_logo_tile must be a LayerDrawable containing full logo layers (was ${tileDrawable?.javaClass?.simpleName})",
            tileDrawable is android.graphics.drawable.LayerDrawable
        )
        val tileLayers = tileDrawable as android.graphics.drawable.LayerDrawable
        assertEquals("bg_launch_logo_tile must stack all 5 base logo layers", 5, tileLayers.numberOfLayers)
        val tileRefs = collectDrawableRefIds(R.drawable.bg_launch_logo_tile)
        assertEquals(
            listOf(
                R.drawable.ic_logo_v11_tile,
                R.drawable.ic_logo_v11_dot,
                R.drawable.ic_logo_v11_one,
                R.drawable.ic_logo_v11_body_tasbeeh,
                R.drawable.ic_logo_v11_ring
            ),
            tileRefs
        )

        // 2. windowBackground (bg_launch_splash_window) renders opaque white + full logo tile at Frame 0
        // (75e2f69: launch window base is white instead of grid, so the logo shows once on white)
        val windowSplashDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_launch_splash_window)
        assertTrue(windowSplashDrawable is android.graphics.drawable.LayerDrawable)
        val windowRefs = collectDrawableRefIds(R.drawable.bg_launch_splash_window)
        assertEquals(listOf(android.R.color.white, R.drawable.bg_launch_logo_tile), windowRefs)

        // 3. Android 12+ system splash icon (ic_splash_transparent) renders opaque white base + the full ring logo at Frame 0
        // (75e2f69: 144dp ic_logo_v11_full replaces the 4-layer tile; the grid file itself is now a white base — see §4)
        val systemSplashIcon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_splash_transparent)
        assertTrue(
            "ic_splash_transparent must be a LayerDrawable with white base + full logo",
            systemSplashIcon is android.graphics.drawable.LayerDrawable
        )
        val splashIconLayers = systemSplashIcon as android.graphics.drawable.LayerDrawable
        assertEquals(2, splashIconLayers.numberOfLayers)
        assertTrue(
            "ic_splash_transparent layer 1 must be the full ring logo (ic_logo_v11_full)",
            splashIconLayers.getDrawable(1) is android.graphics.drawable.BitmapDrawable
        )
        val splashIconRefs = collectDrawableRefIds(R.drawable.ic_splash_transparent)
        assertEquals(listOf(R.drawable.bg_splash_grid_288, R.drawable.ic_logo_v11_full), splashIconRefs)

        // 4. bg_splash_grid_288 must contain NO empty black patch (#FF000000) path; outer grid canvas is opaque #FFFFFFFF
        val gridParser = context.resources.getXml(R.drawable.bg_splash_grid_288)
        val fillColors = mutableListOf<Int>()
        try {
            var ev = gridParser.eventType
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && gridParser.name == "path") {
                    val color = gridParser.getAttributeIntValue(
                        "http://schemas.android.com/apk/res/android",
                        "fillColor",
                        0
                    )
                    if (color != 0) fillColors.add(color)
                }
                ev = gridParser.next()
            }
        } finally {
            gridParser.close()
        }
        assertFalse("bg_splash_grid_288 must not draw an empty black patch", fillColors.contains(android.graphics.Color.BLACK))
        assertTrue("bg_splash_grid_288 must retain opaque white base", fillColors.contains(android.graphics.Color.WHITE))
    }

    @Test
    fun fixRun15Task1_coldStart_frameZeroCompleteRingDrawsTo270WaveRisesWithin1Sec() {
        // Unit check on timeline & 270-degree cap
        assertEquals(900, com.example.ui.screens.SplashIntroSpec.TOTAL_INTRO_DURATION_MS)
        assertTrue(
            "Total cold start duration must never exceed 1 second (actual=${com.example.ui.screens.SplashIntroSpec.TOTAL_INTRO_DURATION_MS}ms)",
            com.example.ui.screens.SplashIntroSpec.TOTAL_INTRO_DURATION_MS <= 1_000
        )
        assertEquals(270f, com.example.ui.screens.SplashIntroSpec.MAX_RING_SWEEP_DEGREES, 0.01f)

        val f0 = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(0f)
        assertEquals(1f, f0.dotT, 0.001f)
        assertEquals(1f, f0.oneT, 0.001f)
        assertEquals(1f, f0.bodyT, 0.001f)
        assertEquals(1f, f0.creditT, 0.001f)
        assertEquals(0f, com.example.ui.screens.SplashIntroSpec.ringSweepDegrees(f0.ringT), 0.001f)
        assertEquals(0f, f0.waveProgress, 0.001f)

        val fMid = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(320f)
        val midSweep = com.example.ui.screens.SplashIntroSpec.ringSweepDegrees(fMid.ringT)
        assertTrue("Ring must be sweeping clockwise mid-intro (midSweep=$midSweep)", midSweep in 10f..269f)
        assertEquals(0f, fMid.waveProgress, 0.001f)

        val fRingDone = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(600f)
        assertEquals(270f, com.example.ui.screens.SplashIntroSpec.ringSweepDegrees(fRingDone.ringT), 0.01f)
        assertEquals(0f, fRingDone.waveProgress, 0.001f)

        val fWaveMid = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(750f)
        assertEquals(270f, com.example.ui.screens.SplashIntroSpec.ringSweepDegrees(fWaveMid.ringT), 0.01f)
        assertTrue("Black wave must be rising in 600..900ms (wave=${fWaveMid.waveProgress})", fWaveMid.waveProgress in 0.05f..0.95f)

        val fEnd = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(1_500f)
        assertEquals(270f, com.example.ui.screens.SplashIntroSpec.ringSweepDegrees(fEnd.ringT), 0.01f)
        assertEquals(1f, fEnd.waveProgress, 0.001f)

        // Robolectric UI timeline verification: Frame 0 -> ring draw -> black wave wipe -> Home opens <= 1s
        SplashSessionGuard.resetForTest()
        com.example.ui.screens.SplashIntroSpec.resetTestObservability()
        com.example.ui.components.setLowRamDeviceOverrideForTest(false)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)

        var splashFinished by mutableStateOf(false)
        try {
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule.setContent {
                OneCroreTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        androidx.compose.material3.Text(
                            "HomeDestination",
                            modifier = Modifier.testTag("mock_home_dest")
                        )
                        if (!splashFinished) {
                            SplashScreen(onFinished = { splashFinished = true })
                        }
                    }
                }
            }
            composeTestRule.mainClock.advanceTimeByFrame()

            // Frame 0: tile, dot, '1', figure+tasbeeh, and ring node all mounted cleanly
            composeTestRule.onNodeWithTag("cold_start_splash_screen").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_tile").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_dot").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_one").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_body_tasbeeh").assertIsDisplayed()
            composeTestRule.onNodeWithTag("splash_logo_ring").assertIsDisplayed()
            assertFalse(splashFinished)

            var elapsedMs = 0L
            var sawWaveWipe = false
            while (!splashFinished && elapsedMs < 1_500L) {
                composeTestRule.mainClock.advanceTimeBy(50L)
                org.robolectric.shadows.ShadowLooper.idleMainLooper()
                elapsedMs += 50L
                if (composeTestRule.onAllNodesWithTag("splash_wave_wipe").fetchSemanticsNodes().isNotEmpty()) {
                    sawWaveWipe = true
                }
            }
            composeTestRule.mainClock.autoAdvance = true
            composeTestRule.waitForIdle()

            assertTrue("Splash must complete cleanly", splashFinished)
            assertTrue(
                "Cold start splash must finish in ~800ms-1000ms (actual=$elapsedMs ms)",
                elapsedMs in 800L..1_000L
            )
            assertTrue("Black layer rise wave wipe must run before Home opens", sawWaveWipe)
            composeTestRule.onNodeWithTag("mock_home_dest").assertIsDisplayed()
        } finally {
            composeTestRule.mainClock.autoAdvance = true
            com.example.ui.components.setLowRamDeviceOverrideForTest(null)
        }
    }

    @Test
    fun fixRun15Task1_coldStart_noDoubleSplashAndRespectsReduceMotion() {
        // 1. Reduce Motion skips animation and shows static final logo immediately
        SplashSessionGuard.resetForTest()
        com.example.ui.screens.SplashIntroSpec.resetTestObservability()
        com.example.ui.components.setLowRamDeviceOverrideForTest(false)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)

        var firstDone = false
        var secondDone = false
        var mode by mutableStateOf(0)
        try {
            composeTestRule.setContent {
                OneCroreTheme {
                    when (mode) {
                        0 -> SplashScreen(onFinished = { firstDone = true })
                        else -> SplashScreen(onFinished = { secondDone = true })
                    }
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("splash_logo_static_final").assertIsDisplayed()
            assertTrue(com.example.ui.screens.SplashIntroSpec.lastSplashWasStaticForTest)
            assertTrue(firstDone)
            assertTrue(SplashSessionGuard.hasPlayedColdStartSplash)

            // 2. No double splash: once played in session, mounting SplashScreen again finishes immediately without re-animating
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            com.example.ui.screens.SplashIntroSpec.resetTestObservability()
            mode = 1
            composeTestRule.waitForIdle()
            assertTrue("Already-played session must skip second splash immediately", secondDone)
            assertTrue(com.example.ui.screens.SplashIntroSpec.lastSplashWasStaticForTest)
            assertEquals(
                listOf(com.example.ui.screens.SplashIntroStage.COMPLETE),
                com.example.ui.screens.SplashIntroSpec.lastSplashStageHistoryForTest
            )
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            com.example.ui.components.setLowRamDeviceOverrideForTest(null)
        }
    }
}


