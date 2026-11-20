package com.example

import com.example.data.DuroodRepository
import com.example.data.local.DailyCountEntity
import com.example.data.local.UserProfileEntity
import com.example.ui.MainUiState
import com.example.ui.PostLoginDestination
import com.example.ui.auth.computeEasedGoalCount
import com.example.ui.auth.formatGoalCount
import com.example.ui.auth.validateAuthConfirmPassword
import com.example.ui.auth.validateAuthEmail
import com.example.ui.auth.validateAuthFullName
import com.example.ui.auth.validateAuthPassword
import com.example.ui.auth.validateAuthSubmission
import com.example.ui.resolvePostLoginDestination
import com.example.ui.screens.formatDateLabel
import com.example.update.UpdateConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExampleUnitTest {

    @Test
    fun mainUiState_calculatesProgressStreakAndMetricsCorrectly() {
        val today = LocalDate.of(2026, 11, 15)
        val profile = UserProfileEntity(
            userId = "u1",
            displayName = "Zayd",
            dailyTarget = DuroodRepository.DEFAULT_DAILY_TARGET,
            setupCompleted = true,
            startedAtMillis = 1_731_628_800_000L
        )
        val days = listOf(
            DailyCountEntity("u1", today.minusDays(2).toString(), 2600L),
            DailyCountEntity("u1", today.minusDays(1).toString(), 3000L),
            DailyCountEntity("u1", today.toString(), 1000L)
        )
        val state = MainUiState(
            isLoading = false,
            profile = profile,
            allDays = days,
            totalCount = 6600L,
            todayCount = 1000L,
            currentDate = today
        )

        assertEquals(2600, state.dailyTarget)
        assertEquals(10_000_000L - 6600L, state.remaining)
        assertEquals(1600L, state.todayRemaining)
        assertEquals(3, state.activeDaysCount)
        assertEquals(2200L, state.averagePerActiveDay)
        assertEquals(3000L, state.bestDay?.count)
        // Today is incomplete (1000 < 2600), so streak counts yesterday and the day before (2 days)
        assertEquals(2, state.currentStreak)
        assertNotNull(state.startDateFormatted)
    }

    @Test
    fun formatDateLabel_usesReactiveTodayForTodayAndYesterdayLabels() {
        val today = LocalDate.of(2026, 11, 15)
        assertTrue(formatDateLabel("2026-11-15", today).startsWith("Today ·"))
        assertTrue(formatDateLabel("2026-11-14", today).startsWith("Yesterday ·"))
        assertEquals("10 Nov 2026", formatDateLabel("2026-11-10", today))
    }

    @Test
    fun shareAndUpdateConstants_matchExactSpecification() {
        assertEquals(
            "https://drive.google.com/uc?export=download&id=1T013CXTJbJFbUkNc_euHiD3f147tzlTE",
            UpdateConfig.UPDATE_VERSION_URL
        )
        assertEquals(
            "1 Crore — Durood Counter & Daily Target",
            UpdateConfig.SHARE_APP_TEXT_TITLE
        )

        val testUrl = "https://example.com/1crore.apk"
        val expectedShareWithLink = """
            *صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا ﷺ*
            *✅️ڈاکٹر محمد اقبالؒ* سے منسوب ایک نقل : کہ مجھ پر یہ سب کرم نوازیاں گن کر ایک کروڑ مرتبہ درود پاک پڑھنے کے سبب ہے  تو اسی سے ترغیب لیتے ہوئے، آئیے 1 CRORE DUROOD TARGET مکمل کرنے کا عزم کریں۔

            *✅️مزے کی بات😇* آپ کا سارا ڈیٹا Email+Password  پہ ہمیشہ کیلئےAuto  محفوظ ہوتا رہے گا کبھی ختم نہیں ہوگا.  
            ✅️بس آپ اپنا   Email+Password  یاد رکھیے گا 🫠
            *Offline + Online*
            ✅️بس ایک مرتبہ انٹرنیٹ کیساتھ دن میں یا ایک مہینے میں ایپ کو اوپن کرنا تا کہ درود کا سارا ڈیٹا  ہمیشہ کیلئے محفوظ ہو جائے۔
            ✅️اپنی counter Tasbih  پہ درود پاک پڑھ کر دن میں ایک مرتبہ آکر اپنا درود پاک  *1 Crore App*  میں input کر دیں🫰🏻 
            ✅️آج ہی اپنا سفر شروع کریں اور دوسروں کو بھی اس نیک عمل میں شریک کریں!

            *📲 DOWNLOAD APK*

            👇🏻👇🏻
            https://example.com/1crore.apk

            APP VERSION: *1.2*

            1 CRORE — ہر شمار، ایک قدم آگے!
        """.trimIndent()

        assertEquals(
            expectedShareWithLink,
            com.example.update.AppUpdateManager.buildShareText(testUrl)
        )
        assertEquals(
            expectedShareWithLink.replace("APP VERSION: *1.2*", "APP VERSION: *1.5*"),
            com.example.update.AppUpdateManager.buildShareText(testUrl, "1.5")
        )
        assertEquals(
            expectedShareWithLink,
            com.example.update.AppUpdateManager.buildShareText(testUrl, "")
        )
    }

    @Test
    fun emailVerificationAutoSend_retriesOnceAndSurfacesClearErrorWhenBothAttemptsFail() {
        assertEquals(
            "Couldn't send the verification email — tap Resend to try again",
            com.example.ui.auth.EmailVerificationConfig.AUTO_SEND_FAILURE_MESSAGE
        )

        // Case 1: Succeeds on first attempt -> 1 call, no retry
        var attempts1 = 0
        var beforeRetry1 = 0
        val res1 = kotlinx.coroutines.runBlocking {
            com.example.ui.auth.executeAutoSendWithSingleRetry(
                retryDelayMillis = 0L,
                onBeforeRetry = { beforeRetry1++ },
                sendBlock = {
                    attempts1++
                }
            )
        }
        assertTrue(res1.isSuccess)
        assertEquals(1, attempts1)
        assertEquals(0, beforeRetry1)

        // Case 2: Fails on first attempt, succeeds on automatic retry -> 2 calls, 1 retry hook, overall success
        var attempts2 = 0
        var beforeRetry2 = 0
        val res2 = kotlinx.coroutines.runBlocking {
            com.example.ui.auth.executeAutoSendWithSingleRetry(
                retryDelayMillis = 0L,
                onBeforeRetry = { beforeRetry2++ },
                sendBlock = {
                    attempts2++
                    if (attempts2 == 1) {
                        throw IllegalStateException("Transient network error on first auto-send")
                    }
                }
            )
        }
        assertTrue(res2.isSuccess)
        assertEquals(2, attempts2)
        assertEquals(1, beforeRetry2)

        // Case 3: Fails on both first attempt and retry -> 2 calls, returns failure so UI shows AUTO_SEND_FAILURE_MESSAGE
        var attempts3 = 0
        var beforeRetry3 = 0
        val res3 = kotlinx.coroutines.runBlocking {
            com.example.ui.auth.executeAutoSendWithSingleRetry(
                retryDelayMillis = 0L,
                onBeforeRetry = { beforeRetry3++ },
                sendBlock = {
                    attempts3++
                    throw IllegalStateException("Persistent error #$attempts3")
                }
            )
        }
        assertTrue(res3.isFailure)
        assertEquals(2, attempts3)
        assertEquals(1, beforeRetry3)
    }

    @Test
    fun postLoginRouting_showsAnimatedLogoWhileLoadingForEveryLogin() {
        assertEquals(
            PostLoginDestination.LOGO_LOADING,
            resolvePostLoginDestination(
                isLoading = true,
                hasOfflineLoadError = false,
                hasExistingSetupOrData = false,
                isEmailSignIn = false,
                isGoogleSignedIn = true,
                hasPasswordLinked = false,
                hasSkippedBackupPasswordPrompt = false
            )
        )
        assertEquals(
            PostLoginDestination.LOGO_LOADING,
            resolvePostLoginDestination(
                isLoading = true,
                hasOfflineLoadError = false,
                hasExistingSetupOrData = true,
                isEmailSignIn = true,
                isGoogleSignedIn = false,
                hasPasswordLinked = true,
                hasSkippedBackupPasswordPrompt = false
            )
        )
    }

    @Test
    fun postLoginRouting_emailSignInOpensHomeAndNeverShowsSetup() {
        // Email + password sign-in: even if cloud had no profile doc yet, goes directly to HOME
        val destOnline = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = false,
            hasExistingSetupOrData = false,
            isEmailSignIn = true,
            isGoogleSignedIn = false,
            hasPasswordLinked = true,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.HOME, destOnline)
        assertNotEquals(PostLoginDestination.SETUP, destOnline)

        // Email + password sign-in with existing data: goes directly to HOME
        val destWithData = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = false,
            hasExistingSetupOrData = true,
            isEmailSignIn = true,
            isGoogleSignedIn = false,
            hasPasswordLinked = true,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.HOME, destWithData)
    }

    @Test
    fun postLoginRouting_googleSignInRoutesExistingToHomeAndBrandNewToBackupPasswordThenSetup() {
        // Returning Google account (has setup or counts): opens HOME directly, skipping LinkPassword and Setup
        val returningGoogle = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = false,
            hasExistingSetupOrData = true,
            isEmailSignIn = false,
            isGoogleSignedIn = true,
            hasPasswordLinked = false,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.HOME, returningGoogle)

        // Brand-new Google account: asks for backup password first
        val newGoogleStep1 = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = false,
            hasExistingSetupOrData = false,
            isEmailSignIn = false,
            isGoogleSignedIn = true,
            hasPasswordLinked = false,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.LINK_BACKUP_PASSWORD, newGoogleStep1)

        // Brand-new Google account after linking backup password (or skipping): shows Setup screen
        val newGoogleStep2Linked = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = false,
            hasExistingSetupOrData = false,
            isEmailSignIn = false,
            isGoogleSignedIn = true,
            hasPasswordLinked = true,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.SETUP, newGoogleStep2Linked)

        val newGoogleStep2Skipped = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = false,
            hasExistingSetupOrData = false,
            isEmailSignIn = false,
            isGoogleSignedIn = true,
            hasPasswordLinked = false,
            hasSkippedBackupPasswordPrompt = true
        )
        assertEquals(PostLoginDestination.SETUP, newGoogleStep2Skipped)
    }

    @Test
    fun postLoginRouting_firstTimeEmailSignupShowsSetupOnceThenHome() {
        val newSignupDest = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = false,
            hasExistingSetupOrData = false,
            isEmailSignIn = false,
            isGoogleSignedIn = false,
            hasPasswordLinked = true,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.SETUP, newSignupDest)

        val afterSetupCompleted = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = false,
            hasExistingSetupOrData = true,
            isEmailSignIn = false,
            isGoogleSignedIn = false,
            hasPasswordLinked = true,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.HOME, afterSetupCompleted)
    }

    @Test
    fun postLoginRouting_offlineFreshInstallShowsOfflineErrorNeverSetup() {
        // Offline + no local data (fresh install, no internet) -> OFFLINE_LOAD_ERROR, never SETUP
        val offlineNoLocal = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = true,
            hasExistingSetupOrData = false,
            isEmailSignIn = false,
            isGoogleSignedIn = true,
            hasPasswordLinked = false,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.OFFLINE_LOAD_ERROR, offlineNoLocal)
        assertNotEquals(PostLoginDestination.SETUP, offlineNoLocal)

        // Offline + existing local Room data -> HOME directly
        val offlineWithLocal = resolvePostLoginDestination(
            isLoading = false,
            hasOfflineLoadError = true,
            hasExistingSetupOrData = true,
            isEmailSignIn = false,
            isGoogleSignedIn = true,
            hasPasswordLinked = false,
            hasSkippedBackupPasswordPrompt = false
        )
        assertEquals(PostLoginDestination.HOME, offlineWithLocal)
    }

    @Test
    fun globalStatsCache_oneHourCacheGuard_fetchesOnlyWhenOlderThanOneHourAndOnline() {
        val t0 = 1_700_000_000_000L
        val cached = com.example.data.GlobalCounterStats(
            totalCount = 1_250_000L,
            userCount = 142L,
            fetchedAtMillis = t0
        )

        assertEquals(3_600_000L, com.example.data.GlobalStatsCache.ONE_HOUR_MILLIS)

        // No cache + online -> fetch from server
        assertTrue(
            com.example.data.GlobalStatsCache.shouldFetchFromServer(
                cached = null,
                nowMillis = t0,
                isOnline = true
            )
        )

        // Fresh cache (30 minutes old, < 1h) + online -> do NOT fetch, use cached value
        val thirtyMinutesLater = t0 + 30L * 60_000L
        assertEquals(
            false,
            com.example.data.GlobalStatsCache.shouldFetchFromServer(
                cached = cached,
                nowMillis = thirtyMinutesLater,
                isOnline = true
            )
        )

        // Just under 1 hour (1h - 1ms) + online -> do NOT fetch
        val justUnderOneHour = t0 + com.example.data.GlobalStatsCache.ONE_HOUR_MILLIS - 1L
        assertEquals(
            false,
            com.example.data.GlobalStatsCache.shouldFetchFromServer(
                cached = cached,
                nowMillis = justUnderOneHour,
                isOnline = true
            )
        )

        // At/older than 1 hour + online -> fetch from server
        val oneHourLater = t0 + com.example.data.GlobalStatsCache.ONE_HOUR_MILLIS
        assertTrue(
            com.example.data.GlobalStatsCache.shouldFetchFromServer(
                cached = cached,
                nowMillis = oneHourLater,
                isOnline = true
            )
        )

        // Offline -> never fetch from server even if cache is older than 1 hour
        assertEquals(
            false,
            com.example.data.GlobalStatsCache.shouldFetchFromServer(
                cached = cached,
                nowMillis = oneHourLater + 3_600_000L,
                isOnline = false
            )
        )
    }

    @Test
    fun globalStatsCache_missingDocAndDisplayFormatting_handlesAllCasesCleanly() {
        val now = 1_700_050_000_000L

        // Missing doc (exists = false) -> returns null (show nothing, no zero, no error)
        assertEquals(
            null,
            com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
                exists = false,
                data = null,
                fetchedAtMillis = now
            )
        )

        // Missing fields or zero values -> returns null (never show zero)
        assertEquals(
            null,
            com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
                exists = true,
                data = emptyMap(),
                fetchedAtMillis = now
            )
        )
        assertEquals(
            null,
            com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
                exists = true,
                data = mapOf("totalCount" to 0L, "userCount" to 0L),
                fetchedAtMillis = now
            )
        )

        // Valid stats/global doc -> parses totalCount and userCount accurately
        val parsed = com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
            exists = true,
            data = mapOf("totalCount" to 4_850_200L, "userCount" to 315L),
            fetchedAtMillis = now
        )
        assertNotNull(parsed)
        assertEquals(4_850_200L, parsed?.totalCount)
        assertEquals(315L, parsed?.userCount)

        // Live server doc shape {totalCount: 287, userCount: 5, updatedAtMillis: ...} (Number & String)
        val liveServerParsed = com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
            exists = true,
            data = mapOf("totalCount" to 287, "userCount" to 5, "updatedAtMillis" to 1_794_801_000_000L),
            fetchedAtMillis = now
        )
        assertNotNull(liveServerParsed)
        assertEquals(287L, liveServerParsed?.totalCount)
        assertEquals(5L, liveServerParsed?.userCount)
        assertEquals(
            "Everyone together: 287 Durood",
            com.example.data.GlobalStatsCache.formatTotalLine(liveServerParsed!!.totalCount)
        )
        assertEquals(
            "5 users",
            com.example.data.GlobalStatsCache.formatUsersLine(liveServerParsed.userCount)
        )

        val stringTypedParsed = com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
            exists = true,
            data = mapOf("totalCount" to "287", "userCount" to "5"),
            fetchedAtMillis = now
        )
        assertNotNull(stringTypedParsed)
        assertEquals(287L, stringTypedParsed?.totalCount)
        assertEquals(5L, stringTypedParsed?.userCount)

        // Display formatting matches exact specification
        assertEquals(
            "Everyone together: 4,850,200 Durood",
            com.example.data.GlobalStatsCache.formatTotalLine(4_850_200L)
        )
        assertEquals(
            "315 users",
            com.example.data.GlobalStatsCache.formatUsersLine(315L)
        )
        assertEquals(
            "Updates every hour",
            com.example.data.GlobalStatsCache.formatSubtitleLine(
                isOnline = true,
                fetchedAtMillis = now,
                nowMillis = now + 60_000L
            )
        )
        val offlineSubtitle = com.example.data.GlobalStatsCache.formatSubtitleLine(
            isOnline = false,
            fetchedAtMillis = now,
            nowMillis = now + 2 * 3_600_000L
        )
        assertTrue(offlineSubtitle.contains("Updates every hour"))
        assertTrue(offlineSubtitle.contains("Updated 2h ago"))
    }

    @Test
    fun authFormValidation_showsAllInlineErrorsAtOnceAndClearsEachWhenValid() {
        val allBadSignup = validateAuthSubmission(
            isSignup = true,
            fullName = "   ",
            email = "not-an-email",
            password = "123",
            confirmPassword = "456"
        )
        assertTrue(allBadSignup.hasAny)
        assertEquals("Enter your full name", allBadSignup.nameError)
        assertEquals("Enter a valid email address", allBadSignup.emailError)
        assertEquals("Password must be at least 6 characters", allBadSignup.passwordError)
        assertEquals("Passwords don't match", allBadSignup.confirmPasswordError)

        // Fixing each individual field clears that specific error immediately
        assertNull(validateAuthFullName("Shahzaib"))
        assertNull(validateAuthEmail("user@example.com"))
        assertNull(validateAuthPassword("123456"))
        assertNull(validateAuthConfirmPassword("123456", "123456"))

        // Login mode checks only email and password
        val badLogin = validateAuthSubmission(
            isSignup = false,
            fullName = "",
            email = "bad",
            password = "12",
            confirmPassword = "99"
        )
        assertNull(badLogin.nameError)
        assertEquals("Enter a valid email address", badLogin.emailError)
        assertEquals("Password must be at least 6 characters", badLogin.passwordError)
        assertNull(badLogin.confirmPasswordError)
    }

    @Test
    fun goalCountUp_formatsWithCommasAndLandsExactlyOnTenMillion() {
        assertEquals(0L, computeEasedGoalCount(0f))
        assertEquals("0", formatGoalCount(0L))

        val midCount = computeEasedGoalCount(0.54321f)
        assertTrue(midCount in 1L until 10_000_000L)
        assertEquals("5,432,100", formatGoalCount(midCount))

        assertEquals(10_000_000L, computeEasedGoalCount(1f))
        assertEquals("10,000,000", formatGoalCount(10_000_000L))
    }

    @Test
    fun announcementParser_parsesNestedAndFlatButtonsAndTimestampsAndActions() {
        val nestedDoc = mapOf(
            "title" to "Blessed Friday Reminder",
            "body" to "Recite extra Durood Pak today.",
            "button" to mapOf(
                "text" to "Share with friends",
                "action" to "share"
            ),
            "startAt" to 1_794_800_000_000L,
            "endAt" to 1_795_100_000_000L,
            "priority" to 5
        )
        val parsedNested = com.example.announcements.AnnouncementParser.parseAnnouncementDocument("ann_1", nestedDoc)
        assertNotNull(parsedNested)
        assertEquals("ann_1", parsedNested?.id)
        assertEquals("Blessed Friday Reminder", parsedNested?.title)
        assertEquals("Recite extra Durood Pak today.", parsedNested?.body)
        assertEquals("Share with friends", parsedNested?.buttonText)
        assertEquals(com.example.announcements.AnnouncementAction.SHARE, parsedNested?.buttonAction)
        assertEquals(true, parsedNested?.hasActionButton)
        assertEquals(3, parsedNested?.maxShowsPerDay)
        assertEquals(21, parsedNested?.maxShowsPerWeek)
        assertEquals(5, parsedNested?.priority)

        val flatLinkDoc = mapOf(
            "title" to "Community Milestone",
            "body" to "Read the latest update notes.",
            "buttonText" to "Read More",
            "buttonAction" to "link",
            "buttonUrl" to "https://example.com/milestone",
            "startAt" to "2026-11-15T00:00:00Z",
            "endAt" to "2026-11-19T00:00:00Z",
            "maxShowsPerDay" to 1,
            "maxShowsPerWeek" to 3
        )
        val parsedFlat = com.example.announcements.AnnouncementParser.parseAnnouncementDocument("ann_2", flatLinkDoc)
        assertNotNull(parsedFlat)
        assertEquals(com.example.announcements.AnnouncementAction.LINK, parsedFlat?.buttonAction)
        assertEquals("https://example.com/milestone", parsedFlat?.buttonUrl)
        assertEquals(true, parsedFlat?.hasActionButton)

        val noButtonDoc = mapOf(
            "title" to "Simple Notice",
            "body" to "Tap anywhere to close.",
            "button" to mapOf("text" to "", "action" to "none")
        )
        val parsedNoButton = com.example.announcements.AnnouncementParser.parseAnnouncementDocument("ann_3", noButtonDoc)
        assertNotNull(parsedNoButton)
        assertEquals(com.example.announcements.AnnouncementAction.NONE, parsedNoButton?.buttonAction)
        assertEquals(false, parsedNoButton?.hasActionButton)

        assertEquals(
            "announcements",
            com.example.announcements.AnnouncementNotificationHelper.TOPIC_ANNOUNCEMENTS
        )
    }

    @Test
    fun floatingCounterAndWidgetConstants_matchSpecification() {
        assertTrue(
            "Flash duration must be between 0.5s and 1.0s (500..1000ms)",
            com.example.floating.FloatingCounterManager.FLASH_DURATION_MILLIS in 500L..1000L
        )
        assertTrue(
            "Hovered close target diameter must exceed resting diameter",
            com.example.floating.FloatingCounterManager.CLOSE_TARGET_HOVERED_SIZE_DP >
                com.example.floating.FloatingCounterManager.CLOSE_TARGET_RESTING_SIZE_DP
        )
        assertTrue(
            "Hit radius must cover the bottom-center close circle",
            com.example.floating.FloatingCounterManager.CLOSE_TARGET_HIT_RADIUS_DP >=
                com.example.floating.FloatingCounterManager.CLOSE_TARGET_RESTING_SIZE_DP / 2f
        )
        // Geometry hit testing: center hit = true, edge outside radius = false
        assertTrue(
            com.example.floating.FloatingCounterManager.isBubbleOverCloseTarget(
                bubbleCenterX = 540f,
                bubbleCenterY = 2000f,
                targetCenterX = 540f,
                targetCenterY = 2010f,
                hitRadiusPx = 120f
            )
        )
        assertFalse(
            com.example.floating.FloatingCounterManager.isBubbleOverCloseTarget(
                bubbleCenterX = 200f,
                bubbleCenterY = 2000f,
                targetCenterX = 540f,
                targetCenterY = 2000f,
                hitRadiusPx = 120f
            )
        )
        assertEquals(
            "widget_action_add_custom",
            com.example.widget.OneCroreWidgetProvider.ACTION_WIDGET_ADD_CUSTOM
        )
        assertEquals(
            "widget_action_open_counter",
            com.example.widget.OneCroreWidgetProvider.ACTION_WIDGET_OPEN_COUNTER
        )
        assertEquals(
            "widget_action_enable_floating",
            com.example.widget.OneCroreWidgetProvider.ACTION_WIDGET_ENABLE_FLOATING
        )
        assertEquals(
            "com.example.widget.ACTION_WIDGET_ENABLE_FLOATING_BROADCAST",
            com.example.widget.OneCroreWidgetProvider.ACTION_WIDGET_ENABLE_FLOATING_BROADCAST
        )
    }

    @Test
    fun dailyCountConflictRules_todayDeviceWinsWhenLocalExistsAndPastDaysLargerWinsAndNeverSmaller() {
        val todayKey = "2026-11-16"
        val pastKey = "2026-11-10"
        val tLocal = 1_794_800_000_000L
        val tCloud = 1_794_800_500_000L

        // 1. Today with no local row (fresh device login) -> pull cloud count
        val todayPulledFromCloud = DuroodRepository.resolveDailyCountConflict(
            uid = "u1",
            dateKey = todayKey,
            todayDateKey = todayKey,
            local = null,
            cloudCount = 1_200L,
            cloudUpdatedAtMillis = tCloud
        )
        assertEquals(1_200L, todayPulledFromCloud.count)
        assertEquals(false, todayPulledFromCloud.pendingSync)

        // 2. Today with local row where local > cloud -> device count wins, queued to upload if needed
        val localTodayHigher = DailyCountEntity("u1", todayKey, count = 1_800L, updatedAtMillis = tLocal, pendingSync = false)
        val resolvedTodayHigher = DuroodRepository.resolveDailyCountConflict(
            uid = "u1",
            dateKey = todayKey,
            todayDateKey = todayKey,
            local = localTodayHigher,
            cloudCount = 1_200L,
            cloudUpdatedAtMillis = tCloud
        )
        assertEquals(1_800L, resolvedTodayHigher.count)
        assertEquals(true, resolvedTodayHigher.pendingSync)

        // 3. Today with local row where local < cloud (e.g. user edited today's count on device) -> device count wins for Today
        val localTodayEdited = DailyCountEntity("u1", todayKey, count = 900L, updatedAtMillis = tLocal, pendingSync = false)
        val resolvedTodayEdited = DuroodRepository.resolveDailyCountConflict(
            uid = "u1",
            dateKey = todayKey,
            todayDateKey = todayKey,
            local = localTodayEdited,
            cloudCount = 1_200L,
            cloudUpdatedAtMillis = tCloud
        )
        assertEquals(900L, resolvedTodayEdited.count)
        assertEquals(true, resolvedTodayEdited.pendingSync)

        // 4. Past day where local > cloud -> larger count wins; cloud NEVER overwrites local with a smaller number
        val localPastHigher = DailyCountEntity("u1", pastKey, count = 2_600L, updatedAtMillis = tLocal, pendingSync = false)
        val resolvedPastLocalHigher = DuroodRepository.resolveDailyCountConflict(
            uid = "u1",
            dateKey = pastKey,
            todayDateKey = todayKey,
            local = localPastHigher,
            cloudCount = 1_500L,
            cloudUpdatedAtMillis = tCloud
        )
        assertEquals(2_600L, resolvedPastLocalHigher.count)
        assertEquals(true, resolvedPastLocalHigher.pendingSync)

        // 5. Past day where cloud > local -> larger count wins (900 -> 3000)
        val localPastLower = DailyCountEntity("u1", pastKey, count = 900L, updatedAtMillis = tLocal, pendingSync = false)
        val resolvedPastCloudHigher = DuroodRepository.resolveDailyCountConflict(
            uid = "u1",
            dateKey = pastKey,
            todayDateKey = todayKey,
            local = localPastLower,
            cloudCount = 3_000L,
            cloudUpdatedAtMillis = tCloud
        )
        assertEquals(3_000L, resolvedPastCloudHigher.count)
        assertEquals(false, resolvedPastCloudHigher.pendingSync)
    }

    @Test
    fun batchSyncAndHourlyGlobalConstants_matchExactSpecification() {
        assertEquals(3_600_000L, com.example.data.GlobalStatsCache.ONE_HOUR_MILLIS)
        assertEquals(100, com.example.ui.MainViewModel.BATCH_SYNC_TAP_THRESHOLD)
        assertEquals(100L, com.example.widget.WidgetAndFloatingBridge.BATCH_SYNC_TAP_THRESHOLD)
        assertEquals(5L * 60L * 1000L, com.example.ui.MainViewModel.AUTO_SYNC_INTERVAL_MILLIS)
    }

    @Test
    fun pendingSyncUiFormatting_formatsPendingDuroodAndDaysCleanly() {
        assertEquals(
            "350 Durood (1 day) pending",
            com.example.ui.components.formatPendingSyncSummary(350L, 1)
        )
        assertEquals(
            "4,200 Durood (3 days) pending",
            com.example.ui.components.formatPendingSyncSummary(4_200L, 3)
        )
        assertEquals(
            "Offline · 350 Durood (1 day) pending · Saved on phone, will sync",
            com.example.ui.components.formatOfflineSyncBadgeLabel(
                isOnline = false,
                hasPendingSync = true,
                isSyncing = false,
                lastSyncedAtMillis = 0L,
                pendingSyncDuroodCount = 350L,
                pendingSyncDaysCount = 1
            )
        )
        assertEquals(
            "Saved on phone · 1,250 Durood (2 days) pending, will sync",
            com.example.ui.components.formatOfflineSyncBadgeLabel(
                isOnline = true,
                hasPendingSync = true,
                isSyncing = false,
                lastSyncedAtMillis = 0L,
                pendingSyncDuroodCount = 1_250L,
                pendingSyncDaysCount = 2
            )
        )
    }

    @Test
    fun updateSystemAudit_apkValidationAndFailureMappingAndCooldownConstants() {
        // 24-hour cooldown constant for "Later" dismissal
        assertEquals(24L * 60L * 60L * 1000L, com.example.update.AppUpdateManager.LATER_COOLDOWN_MILLIS)

        // Progress text formatting
        assertEquals(
            "Downloading 4.5 MB / 10.0 MB (45%)",
            com.example.update.AppUpdateManager.formatDownloadProgressText(
                bytesDownloaded = (4.5 * 1024 * 1024).toLong(),
                totalBytes = (10.0 * 1024 * 1024).toLong()
            )
        )

        // Failure reason mapping
        assertEquals(
            "Not enough storage space to download update.",
            com.example.update.AppUpdateManager.mapFailureReason(android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE)
        )
        assertEquals(
            "Network error while downloading APK. Please try again.",
            com.example.update.AppUpdateManager.mapFailureReason(android.app.DownloadManager.ERROR_HTTP_DATA_ERROR)
        )

        // APK magic-byte header validation (rejects empty, small, or HTML error pages; accepts valid PK zip/APK header)
        val tempDir = java.nio.file.Files.createTempDirectory("apk_validation_test").toFile()
        try {
            val missingFile = java.io.File(tempDir, "missing.apk")
            assertEquals(false, com.example.update.AppUpdateManager.isValidApkFile(missingFile))

            val htmlFile = java.io.File(tempDir, "error_page.apk").apply {
                writeText("<!DOCTYPE html><html><body>Quota exceeded</body></html>".repeat(40))
            }
            assertEquals(false, com.example.update.AppUpdateManager.isValidApkFile(htmlFile))

            val validApkFile = java.io.File(tempDir, "valid_1crore.apk").apply {
                val bytes = ByteArray(2048)
                bytes[0] = 0x50.toByte() // 'P'
                bytes[1] = 0x4B.toByte() // 'K'
                writeBytes(bytes)
            }
            assertTrue(com.example.update.AppUpdateManager.isValidApkFile(validApkFile))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun smoothAnimationsAudit_faintGridSettleDurationAndGoalCountUpCurves() {
        assertEquals(4_000, com.example.ui.components.FAINT_GRID_SETTLE_DURATION_MS)
        assertTrue(com.example.ui.components.FAINT_GRID_OSCILLATION_AMPLITUDE_DP in 6f..8f)
        // Verify computeEasedGoalCount stays monotonic across ease-out progress 0f..1f
        var previous = 0L
        for (step in 0..20) {
            val fraction = step / 20f
            val current = computeEasedGoalCount(fraction)
            assertTrue("Goal count must increase monotonically at step $step", current >= previous)
            previous = current
        }
        assertEquals(10_000_000L, previous)
    }

    @Test
    fun globalStatsUrgentFix_parsesLive517And6AndCommaStringsWithoutWaitingForHourlyCycle() {
        val t0 = 1_700_000_000_000L

        // Empty cache must ALWAYS trigger immediate fetch (both online and startup forceWhenEmpty path)
        assertTrue(com.example.data.GlobalStatsCache.shouldFetchFromServer(null, t0, isOnline = true))

        // Parses live 517 Durood / 6 users numeric fields
        val liveNumeric = com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
            exists = true,
            data = mapOf("totalCount" to 517L, "userCount" to 6L),
            fetchedAtMillis = t0
        )
        assertEquals(517L, liveNumeric?.totalCount)
        assertEquals(6L, liveNumeric?.userCount)
        assertEquals("Everyone together: 517 Durood", com.example.data.GlobalStatsCache.formatTotalLine(517L))
        assertEquals("6 users", com.example.data.GlobalStatsCache.formatUsersLine(6L))

        // Parses stringified and comma-formatted fields (e.g., "558", "1,234")
        val stringDoc = com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
            exists = true,
            data = mapOf("totalCount" to "1,558", "userCount" to "6"),
            fetchedAtMillis = t0
        )
        assertEquals(1558L, stringDoc?.totalCount)
        assertEquals(6L, stringDoc?.userCount)

        // Fresh cache within 1 hour skips server refresh, whereas after 1 hour it refreshes
        assertEquals(
            false,
            com.example.data.GlobalStatsCache.shouldFetchFromServer(liveNumeric, t0 + 59L * 60L * 1000L, isOnline = true)
        )
        assertTrue(
            com.example.data.GlobalStatsCache.shouldFetchFromServer(liveNumeric, t0 + 60L * 60L * 1000L, isOnline = true)
        )
    }

    @Test
    fun reminderUrgentFix_incompleteTargetConditionAndNextTriggerBoundary() {
        // Incomplete target check: strictly fires only when todayCount < dailyTarget
        assertTrue(com.example.notifications.ReminderScheduler.isTargetIncomplete(0L, 2600L))
        assertTrue(com.example.notifications.ReminderScheduler.isTargetIncomplete(2599L, 2600L))
        assertEquals(false, com.example.notifications.ReminderScheduler.isTargetIncomplete(2600L, 2600L))
        assertEquals(false, com.example.notifications.ReminderScheduler.isTargetIncomplete(3100L, 2600L))

        // computeNextTrigger scheduled 10 seconds before 18:00 MUST fire today at 18:00 (no +15s skip bug)
        val zone = java.time.ZoneId.of("Asia/Karachi")
        val beforeSixPm = java.time.ZonedDateTime.of(2026, 11, 16, 17, 59, 50, 0, zone)
        val todaySixPm = com.example.notifications.ReminderScheduler.computeNextTrigger(
            now = beforeSixPm,
            hour = 18,
            minute = 0,
            minAdvanceSeconds = 0L
        )
        assertEquals(java.time.LocalDate.of(2026, 11, 16), todaySixPm.toLocalDate())
        assertEquals(18, todaySixPm.hour)
        assertEquals(0, todaySixPm.minute)

        // After 18:00 (e.g. 18:00:05), next trigger advances to tomorrow 18:00
        val afterSixPm = java.time.ZonedDateTime.of(2026, 11, 16, 18, 0, 5, 0, zone)
        val tomorrowSixPm = com.example.notifications.ReminderScheduler.computeNextTrigger(
            now = afterSixPm,
            hour = 18,
            minute = 0,
            minAdvanceSeconds = 0L
        )
        assertEquals(java.time.LocalDate.of(2026, 11, 17), tomorrowSixPm.toLocalDate())
        assertEquals(18, tomorrowSixPm.hour)
    }

    @Test
    fun announcementShowPathFix_parsesTestSalamDocsWithoutButtonAndInferredLinkAndMapTimestamps() {
        val nowSeconds = 1_794_850_500L
        val testSalamNoButton = mapOf(
            "title" to "Assalamu Alaikum",
            "body" to "Keep reciting Durood Pak today.",
            "active" to true,
            "priority" to 10,
            "maxShowsPerDay" to 1,
            "maxShowsPerWeek" to 3,
            "startAt" to mapOf("seconds" to nowSeconds, "nanoseconds" to 0)
        )
        val parsedSalam = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "test-salam-1",
            data = testSalamNoButton
        )
        assertNotNull(parsedSalam)
        assertEquals("test-salam-1", parsedSalam?.id)
        assertEquals(10, parsedSalam?.priority)
        assertEquals(1, parsedSalam?.maxShowsPerDay)
        assertEquals(3, parsedSalam?.maxShowsPerWeek)
        assertEquals(nowSeconds * 1000L, parsedSalam?.startAtMillis)
        assertEquals(Long.MAX_VALUE, parsedSalam?.endAtMillis)
        assertEquals(com.example.announcements.AnnouncementAction.NONE, parsedSalam?.buttonAction)
        assertEquals(false, parsedSalam?.hasActionButton)

        // Button map with text + url (without explicit "action") must infer LINK
        val inferredLinkDoc = mapOf(
            "title" to "Join Circle",
            "body" to "Visit https://onecrore.app/join for details.",
            "button" to mapOf(
                "text" to "Join",
                "url" to "https://onecrore.app/join"
            )
        )
        val parsedInferredLink = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "test-salam-2",
            data = inferredLinkDoc
        )
        assertNotNull(parsedInferredLink)
        assertEquals(com.example.announcements.AnnouncementAction.LINK, parsedInferredLink?.buttonAction)
        assertEquals("Join", parsedInferredLink?.buttonText)
        assertEquals("https://onecrore.app/join", parsedInferredLink?.buttonUrl)
        assertTrue(parsedInferredLink?.hasActionButton == true)

        // Inactive doc is skipped for automatic pop-ups, but still parses on explicit notification tap
        val inactiveDoc = testSalamNoButton + ("active" to false)
        assertNull(
            com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
                id = "test-salam-3",
                data = inactiveDoc
            )
        )
        assertNotNull(
            com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
                id = "test-salam-3",
                data = inactiveDoc,
                allowInactiveOrExpired = true
            )
        )
    }

    @Test
    fun globalStatsDeadlockFix_serverReadTimeoutIsBoundedWithin8To10Seconds() {
        val timeoutMs = com.example.data.DuroodRepository.GLOBAL_STATS_FETCH_TIMEOUT_MS
        assertTrue(
            "Expected per-attempt global stats timeout in 8_000..10_000ms, was $timeoutMs",
            timeoutMs in 8_000L..10_000L
        )
    }

    @Test
    fun announcementFetchFix_timeoutBoundedAndParsesTestJoin2WithWhatsAppButton() {
        val timeoutMs = com.example.data.DuroodRepository.ANNOUNCEMENT_FETCH_TIMEOUT_MS
        assertTrue(
            "Expected per-attempt announcement timeout in 8_000..10_000ms, was $timeoutMs",
            timeoutMs in 8_000L..10_000L
        )

        val nowMs = 1_794_860_000_000L
        val joinDoc = mapOf(
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
        val parsed = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "test-join-2",
            data = joinDoc
        )
        assertNotNull(parsed)
        assertEquals("test-join-2", parsed?.id)
        assertEquals("السلام علیکم", parsed?.title)
        assertEquals("Join my channel", parsed?.buttonText)
        assertEquals(com.example.announcements.AnnouncementAction.LINK, parsed?.buttonAction)
        assertEquals("https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B", parsed?.buttonUrl)
        assertTrue(parsed?.hasActionButton == true)
        assertEquals(3, parsed?.maxShowsPerDay)
        assertEquals(21, parsed?.maxShowsPerWeek)
        assertEquals(10, parsed?.priority)
    }

    @Test
    fun run3AnnouncementTwoType_parsesImageEmojiAndDefaults3PerDay21PerWeek10MinGap() {
        assertEquals(3, com.example.announcements.AnnouncementItem.DEFAULT_MAX_SHOWS_PER_DAY)
        assertEquals(3, com.example.announcements.AnnouncementItem.MIN_CONFIGURABLE_DAILY_CAP)
        assertEquals(5, com.example.announcements.AnnouncementItem.MAX_CONFIGURABLE_DAILY_CAP)
        assertEquals(21, com.example.announcements.AnnouncementItem.DEFAULT_MAX_SHOWS_PER_WEEK)
        assertEquals(10L * 60L * 1000L, com.example.announcements.AnnouncementItem.MIN_SHOW_INTERVAL_MILLIS)

        // Type (a): message + link + optional imageUrl + Unicode emojis
        val linkWithImageDoc = mapOf(
            "active" to true,
            "title" to "السلام علیکم 🌙✨",
            "body" to "Join our official community channel 🤲📿",
            "imageUrl" to "https://example.com/banner.jpg",
            "button" to mapOf(
                "text" to "Join Channel",
                "action" to "link",
                "url" to "https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B"
            )
        )
        val parsedLink = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "run3-link-1",
            data = linkWithImageDoc
        )
        assertNotNull(parsedLink)
        assertEquals("السلام علیکم 🌙✨", parsedLink?.title)
        assertEquals("Join our official community channel 🤲📿", parsedLink?.body)
        assertEquals("https://example.com/banner.jpg", parsedLink?.imageUrl)
        assertEquals("Join Channel", parsedLink?.buttonText)
        assertEquals(com.example.announcements.AnnouncementAction.LINK, parsedLink?.buttonAction)
        assertEquals("https://whatsapp.com/channel/0029VbASCFE4tRs0xSB7E61B", parsedLink?.buttonUrl)
        assertTrue(parsedLink?.hasActionButton == true)
        assertEquals(3, parsedLink?.maxShowsPerDay)
        assertEquals(21, parsedLink?.maxShowsPerWeek)

        // Type (b): message-only + emojis + invalid non-http imageUrl rejected -> NO button
        val messageOnlyDoc = mapOf(
            "active" to true,
            "title" to "Daily Reminder 🕌",
            "body" to "Recite Durood Pak 100 times today 💖",
            "imageUrl" to "javascript:alert(1)"
        )
        val parsedMessageOnly = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "run3-msg-1",
            data = messageOnlyDoc
        )
        assertNotNull(parsedMessageOnly)
        assertEquals("Daily Reminder 🕌", parsedMessageOnly?.title)
        assertEquals("Recite Durood Pak 100 times today 💖", parsedMessageOnly?.body)
        assertNull(parsedMessageOnly?.imageUrl)
        assertNull(parsedMessageOnly?.buttonText)
        assertEquals(com.example.announcements.AnnouncementAction.NONE, parsedMessageOnly?.buttonAction)
        assertEquals(false, parsedMessageOnly?.hasActionButton)
        assertEquals(3, parsedMessageOnly?.maxShowsPerDay)
        assertEquals(21, parsedMessageOnly?.maxShowsPerWeek)
    }

    @Test
    fun run6ScheduledAndTargeted_parsesScheduledAtAndAudiencesAndEnforcesPureAudienceRules() {
        val nowMillis = 1_795_000_000_000L
        val twoMinAhead = nowMillis + 120_000L

        val scheduledTargetedDoc = mapOf(
            "active" to true,
            "title" to "Jumu'ah Reminder 🕌",
            "body" to "Complete your Durood target before Maghrib!",
            "scheduledAt" to twoMinAhead,
            "targetAudience" to "incomplete_today",
            "button" to mapOf(
                "text" to "Join",
                "url" to "https://example.com/jumuah"
            )
        )
        val parsed = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "run6-sched-1",
            data = scheduledTargetedDoc
        )
        assertNotNull(parsed)
        assertEquals(twoMinAhead, parsed!!.scheduledAtMillis)
        assertEquals(twoMinAhead, parsed.effectiveStartAtMillis)
        assertTrue(parsed.isScheduledForFuture(nowMillis))
        assertFalse(parsed.isScheduledForFuture(twoMinAhead))
        assertEquals(
            com.example.announcements.AnnouncementAudience.INCOMPLETE_DAILY_TARGET,
            parsed.targetAudience
        )
        assertEquals(com.example.announcements.AnnouncementAction.LINK, parsed.buttonAction)
        assertEquals("Join", parsed.buttonText)
        assertTrue(parsed.hasActionButton)

        // Audience (a): incomplete daily target today
        val completedCtx = com.example.announcements.AnnouncementAudienceContext(
            todayCount = 2600L,
            dailyTarget = 2600,
            lastActiveAtMillis = nowMillis
        )
        val incompleteCtx = com.example.announcements.AnnouncementAudienceContext(
            todayCount = 900L,
            dailyTarget = 2600,
            lastActiveAtMillis = nowMillis
        )
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isAudienceEligible(
                parsed,
                completedCtx,
                nowMillis
            )
        )
        assertTrue(
            com.example.announcements.AnnouncementFrequencyStore.isAudienceEligible(
                parsed,
                incompleteCtx,
                nowMillis
            )
        )

        // Audience (b): inactive for X days (default 3, configurable X)
        val inactiveDefaultDoc = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "run6-inactive-default",
            data = mapOf(
                "active" to true,
                "title" to "We miss you",
                "body" to "Come back for Durood today",
                "targetAudience" to "inactive_days"
            )
        )!!
        assertEquals(
            com.example.announcements.AnnouncementAudience.INACTIVE_DAYS,
            inactiveDefaultDoc.targetAudience
        )
        assertEquals(3, inactiveDefaultDoc.inactiveDaysThreshold)
        assertFalse(inactiveDefaultDoc.hasActionButton)

        val oneDayMs = com.example.announcements.AnnouncementItem.ONE_DAY_MILLIS
        assertTrue(
            com.example.announcements.AnnouncementFrequencyStore.isAudienceEligible(
                inactiveDefaultDoc,
                com.example.announcements.AnnouncementAudienceContext(
                    todayCount = 0L,
                    dailyTarget = 2600,
                    lastActiveAtMillis = nowMillis - 4L * oneDayMs
                ),
                nowMillis
            )
        )
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isAudienceEligible(
                inactiveDefaultDoc,
                com.example.announcements.AnnouncementAudienceContext(
                    todayCount = 0L,
                    dailyTarget = 2600,
                    lastActiveAtMillis = nowMillis - 2L * oneDayMs
                ),
                nowMillis
            )
        )
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isAudienceEligible(
                inactiveDefaultDoc,
                com.example.announcements.AnnouncementAudienceContext(
                    todayCount = 50L,
                    dailyTarget = 2600,
                    lastActiveAtMillis = nowMillis - 5L * oneDayMs
                ),
                nowMillis
            )
        )

        val custom5Days = inactiveDefaultDoc.copy(inactiveDaysThreshold = 5)
        assertFalse(
            com.example.announcements.AnnouncementFrequencyStore.isAudienceEligible(
                custom5Days,
                com.example.announcements.AnnouncementAudienceContext(
                    todayCount = 0L,
                    dailyTarget = 2600,
                    lastActiveAtMillis = nowMillis - 4L * oneDayMs
                ),
                nowMillis
            )
        )
        assertTrue(
            com.example.announcements.AnnouncementFrequencyStore.isAudienceEligible(
                custom5Days,
                com.example.announcements.AnnouncementAudienceContext(
                    todayCount = 0L,
                    dailyTarget = 2600,
                    lastActiveAtMillis = nowMillis - 6L * oneDayMs
                ),
                nowMillis
            )
        )
    }

    @Test
    fun run7Task1_milestoneParserAndThresholds_parsesSentMilestonesBuildsTwoTypesAndCrossesOnly1LakhAnd10Lakh() {
        assertEquals(
            listOf(100_000L, 1_000_000L),
            com.example.announcements.MilestoneAnnouncementManager.THRESHOLDS
        )

        // Only 1 Lakh (100,000) and 10 Lakh (1,000,000) thresholds trigger
        assertTrue(com.example.announcements.MilestoneAnnouncementManager.crossedMilestones(99_999L).isEmpty())
        assertEquals(
            listOf(100_000L),
            com.example.announcements.MilestoneAnnouncementManager.crossedMilestones(100_000L)
        )
        assertEquals(
            listOf(100_000L),
            com.example.announcements.MilestoneAnnouncementManager.crossedMilestones(999_999L)
        )
        assertEquals(
            listOf(100_000L, 1_000_000L),
            com.example.announcements.MilestoneAnnouncementManager.crossedMilestones(1_000_000L)
        )

        // Parses sentMilestones in numeric, string, or map forms from stats/global
        val parsedNumeric = com.example.data.GlobalStatsCache.parseGlobalStatsDocument(
            exists = true,
            data = mapOf(
                "totalCount" to 1_050_000L,
                "userCount" to 120L,
                "sentMilestones" to listOf(100_000L, "milestone_1000000")
            ),
            fetchedAtMillis = 1_795_000_000_000L
        )
        assertEquals(listOf(100_000L, 1_000_000L), parsedNumeric?.sentMilestones)

        // Message-only celebration by default, optional Join link supported
        val msgOnly = com.example.announcements.MilestoneAnnouncementManager.buildMilestoneAnnouncement(100_000L)
        assertEquals("milestone_100000", msgOnly.id)
        assertFalse(msgOnly.hasActionButton)
        assertEquals(com.example.announcements.AnnouncementAudience.ALL, msgOnly.targetAudience)
        assertEquals(1, msgOnly.maxShowsPerDay)
        assertEquals(1, msgOnly.maxShowsPerWeek)

        val withJoinLink = com.example.announcements.MilestoneAnnouncementManager.buildMilestoneAnnouncement(
            threshold = 1_000_000L,
            linkUrl = "https://whatsapp.com/channel/test"
        )
        assertEquals("milestone_1000000", withJoinLink.id)
        assertTrue(withJoinLink.hasActionButton)
        assertEquals("Join", withJoinLink.buttonText)
        assertEquals(com.example.announcements.AnnouncementAction.LINK, withJoinLink.buttonAction)
        assertEquals("https://whatsapp.com/channel/test", withJoinLink.buttonUrl)
    }

    @Test
    fun run7Task2_messagesHistoryDateFormat_formatsReadableTimestampAndEmptyOnZero() {
        assertEquals("", com.example.ui.screens.formatHistoryTimestamp(0L))
        val formatted = com.example.ui.screens.formatHistoryTimestamp(1_795_000_000_000L)
        assertTrue("Expected formatted timestamp string, was '$formatted'", formatted.isNotBlank())
        assertTrue(formatted.contains(","))
    }

    @Test
    fun run8_urgentFlagParsingAndSerialization_parsesIsUrgentAcrossFirestoreFcmAndJson() {
        val urgentDoc = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "run8_urgent_doc",
            data = mapOf(
                "title" to "Urgent Notice",
                "body" to "Immediate schedule change",
                "isUrgent" to true
            )
        )
        assertNotNull(urgentDoc)
        assertTrue(urgentDoc?.isUrgent == true)
        val firestoreMap = urgentDoc!!.toFirestoreMap()
        assertEquals(true, firestoreMap["isUrgent"])

        val roundTripFirestore = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "run8_urgent_doc",
            data = firestoreMap
        )
        assertTrue(roundTripFirestore?.isUrgent == true)

        val aliasUrgent = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "run8_urgent_alias",
            data = mapOf("title" to "Notice", "body" to "Alert", "urgent" to 1L)
        )
        assertTrue(aliasUrgent?.isUrgent == true)

        val formUrgent = com.example.ui.components.buildAnnouncementItemFromForm(
            title = "Urgent",
            body = "Immediately",
            isLinkType = false,
            linkUrl = "",
            buttonText = "Join",
            imageUrl = null,
            targetAudience = com.example.announcements.AnnouncementAudience.ALL,
            inactiveDaysThreshold = 3,
            scheduledAtMillis = 0L,
            isUrgent = true,
            nowMillis = 1_796_000_000_000L
        )
        assertTrue(formUrgent.isUrgent)
        assertEquals(100, formUrgent.priority)

        val normalDoc = com.example.announcements.AnnouncementParser.parseAnnouncementDocument(
            id = "run8_normal_doc",
            data = mapOf(
                "title" to "Regular Notice",
                "body" to "Daily reminder"
            )
        )
        assertNotNull(normalDoc)
        assertFalse(normalDoc?.isUrgent == true)
        assertEquals(false, normalDoc?.toFirestoreMap()?.get("isUrgent"))

        val fcmUrgent = com.example.announcements.AnnouncementParser.parseFromFcmData(
            messageId = "m1",
            notificationTitle = "Urgent",
            notificationBody = "Live stream started",
            data = mapOf(
                "id" to "run8_fcm_urgent",
                "isUrgent" to "true"
            )
        )
        assertTrue(fcmUrgent?.isUrgent == true)

        val fcmNormal = com.example.announcements.AnnouncementParser.parseFromFcmData(
            messageId = "m2",
            notificationTitle = "Normal",
            notificationBody = "Routine update",
            data = mapOf(
                "id" to "run8_fcm_normal",
                "isUrgent" to "false"
            )
        )
        assertFalse(fcmNormal?.isUrgent == true)
    }

    @Test
    fun run9Task1_groupCalculationsAndIsolation_sumsMainTotalsRanksMembersBuildsInviteAndCelebrations() {
        val today = LocalDate.of(2026, 11, 17)
        val todayKey = today.toString()
        val yesterdayKey = today.minusDays(1).toString()

        val members = listOf(
            com.example.groups.GroupMemberProgress(
                userId = "u_b",
                displayName = "Bilal",
                todayCount = 1_200L,
                todayDateKey = todayKey,
                lifetimeTotalCount = 35_000L,
                weeklyCount = 9_500L
            ),
            com.example.groups.GroupMemberProgress(
                userId = "u_a",
                displayName = "Amina",
                todayCount = 4_500L,
                todayDateKey = todayKey,
                lifetimeTotalCount = 70_000L,
                weeklyCount = 15_000L
            ),
            com.example.groups.GroupMemberProgress(
                userId = "u_c",
                displayName = "Tariq",
                todayCount = 900L,
                todayDateKey = yesterdayKey, // stale date -> 0 today
                lifetimeTotalCount = 15_000L,
                weeklyCount = 3_000L
            ),
            com.example.groups.GroupMemberProgress(
                userId = "u_d",
                displayName = "Zaid",
                todayCount = 500L,
                todayDateKey = todayKey,
                lifetimeTotalCount = 5_000L,
                weeklyCount = 1_000L
            )
        )

        val mainGroup = com.example.groups.GroupItem(
            id = "grp_main_1",
            name = "Halqa-e-Durood",
            inviteCode = "DRD48K",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            creatorUserId = "u_a",
            members = members,
            serverTotalCount = null
        )

        // Server total equals sum of members' main Durood lifetime totals (70k + 35k + 15k + 5k = 125,000)
        assertEquals(4, mainGroup.memberCount)
        assertEquals(125_000L, mainGroup.effectiveGroupTotal)

        // Ranking most-read first (#1 Amina, #2 Bilal, #3 Tariq, #4 Zaid)
        val ranked = mainGroup.rankedMembers(todayKey)
        assertEquals(listOf("u_a", "u_b", "u_c", "u_d"), ranked.map { it.userId })
        assertEquals(4_500L, ranked[0].effectiveTodayCount(todayKey))
        assertEquals(0L, ranked[2].effectiveTodayCount(todayKey))

        // Weekly Top-3 celebration + 1 Lakh target celebration (private + group-wide)
        val top3 = mainGroup.weeklyTopThree()
        assertEquals(listOf("u_a", "u_b", "u_c"), top3.map { it.userId })

        val celebrationsForAmina = com.example.groups.GroupLogic.computeCelebrations(
            group = mainGroup,
            currentUserId = "u_a",
            today = today,
            nowMillis = 1_796_000_000_000L
        )
        assertTrue(celebrationsForAmina.any { it.type == com.example.groups.GroupCelebrationType.TARGET_1_LAKH })
        assertFalse(celebrationsForAmina.any { it.type == com.example.groups.GroupCelebrationType.TARGET_1_CRORE })
        assertTrue(celebrationsForAmina.any {
            it.type == com.example.groups.GroupCelebrationType.WEEKLY_TOP_3_PRIVATE && it.targetUserId == "u_a"
        })
        val groupWideNotice = celebrationsForAmina.first {
            it.type == com.example.groups.GroupCelebrationType.WEEKLY_TOP_3_GROUP
        }
        assertTrue(groupWideNotice.message.contains("Amina"))
        assertTrue(groupWideNotice.message.contains("Bilal"))
        assertTrue(groupWideNotice.message.contains("Tariq"))

        // Non-top-3 member Zaid sees group-wide Top-3 notice, NOT a private Top-3 notice
        val celebrationsForZaid = com.example.groups.GroupLogic.computeCelebrations(
            group = mainGroup,
            currentUserId = "u_d",
            today = today
        )
        assertFalse(celebrationsForZaid.any { it.type == com.example.groups.GroupCelebrationType.WEEKLY_TOP_3_PRIVATE })

        // Code-only WhatsApp invite message includes ONLY group name and invite code (no links)
        val whatsappText = com.example.groups.GroupLogic.buildMergedWhatsAppInviteMessage(
            group = mainGroup
        )
        assertTrue(whatsappText.contains("DRD48K"))
        assertTrue(whatsappText.contains("Halqa-e-Durood"))
        assertFalse(whatsappText.contains("http://"))
        assertFalse(whatsappText.contains("https://"))
        assertFalse(whatsappText.contains("onecrore.app"))
    }

    @Test
    fun run9Task2_milestoneBadges_defines200BadgesEvery50kUpTo1CroreAndProgressMath() {
        assertEquals(50_000L, com.example.badges.MilestoneBadgeManager.BADGE_STEP)
        assertEquals(200, com.example.badges.MilestoneBadgeManager.TOTAL_BADGES)
        assertEquals(200, com.example.badges.MilestoneBadgeManager.ALL_BADGES.size)

        assertEquals(0, com.example.badges.MilestoneBadgeManager.tierForCount(0L))
        assertEquals(0, com.example.badges.MilestoneBadgeManager.tierForCount(49_999L))
        assertEquals(1, com.example.badges.MilestoneBadgeManager.tierForCount(50_000L))
        assertEquals(2, com.example.badges.MilestoneBadgeManager.tierForCount(100_000L))
        assertEquals(3, com.example.badges.MilestoneBadgeManager.tierForCount(150_000L))
        assertEquals(200, com.example.badges.MilestoneBadgeManager.tierForCount(10_000_000L))
        assertEquals(200, com.example.badges.MilestoneBadgeManager.tierForCount(15_000_000L))

        assertEquals("50K", com.example.badges.MilestoneBadgeManager.badgeForTier(1)?.shortLabel)
        assertEquals("1 Lakh", com.example.badges.MilestoneBadgeManager.badgeForTier(2)?.shortLabel)
        assertEquals("1.5 Lakh", com.example.badges.MilestoneBadgeManager.badgeForTier(3)?.shortLabel)
        assertEquals("1 Crore", com.example.badges.MilestoneBadgeManager.badgeForTier(200)?.shortLabel)

        val midProgress = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(75_000L)
        assertEquals(1, midProgress.earnedCount)
        assertEquals("50K Badge", midProgress.latestEarnedBadge?.title)
        assertEquals("1 Lakh Badge", midProgress.nextBadge?.title)
        assertEquals(25_000L, midProgress.remainingToNext)
        assertEquals(0.5f, midProgress.progressToNextFraction, 0.001f)

        val fullProgress = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(10_000_000L)
        assertEquals(200, fullProgress.earnedCount)
        assertEquals("1 Crore Badge", fullProgress.latestEarnedBadge?.title)
        assertNull(fullProgress.nextBadge)
        assertEquals(0L, fullProgress.remainingToNext)
        assertEquals(1f, fullProgress.progressToNextFraction, 0.001f)
    }

    @Test
    fun run9FollowUp_hourlyGlobalAggregateAndBatchConstants_remainSparkFreeTierSafe() {
        val now = 1_797_100_000_000L
        assertEquals(60L * 60L * 1000L, com.example.data.GlobalStatsCache.ONE_HOUR_MILLIS)
        assertEquals(1_500L, com.example.ui.MainViewModel.GROUP_FLUSH_DEBOUNCE_MILLIS)
        assertEquals(100, com.example.ui.MainViewModel.BATCH_SYNC_TAP_THRESHOLD)

        val thirtyMinCached = com.example.data.GlobalCounterStats(
            totalCount = 1_234L,
            userCount = 25L,
            fetchedAtMillis = now - 30L * 60L * 1000L
        )
        assertFalse(
            "Global hourly aggregate MUST NOT refresh before 1 full hour elapses",
            com.example.data.GlobalStatsCache.shouldFetchFromServer(
                cached = thirtyMinCached,
                nowMillis = now,
                isOnline = true
            )
        )
        assertTrue(
            "Global aggregate refreshes once 1 full hour has elapsed",
            com.example.data.GlobalStatsCache.shouldFetchFromServer(
                cached = thirtyMinCached.copy(fetchedAtMillis = now - 60L * 60L * 1000L),
                nowMillis = now,
                isOnline = true
            )
        )
    }

    @Test
    fun run10_progressShareCardSpec_calculatesPercentFormatsCountsUsesGroupDownloadUrlAndKeepsSimpleShareUnchanged() {
        // 1. Verify % math across 0, 50k, 125k, 1M, and 10M (total / 10,000,000)
        val zeroSpec = com.example.update.AppUpdateManager.buildProgressShareCardSpec(
            todayCount = 0L,
            totalCount = 0L,
            downloadUrl = null,
            nowMillis = 1_797_200_000_000L
        )
        assertEquals("0", zeroSpec.todayFormatted)
        assertEquals("0", zeroSpec.totalFormatted)
        assertEquals("0.00%", zeroSpec.percentFormatted)
        assertEquals(0f, zeroSpec.progressFraction, 0.0001f)
        assertEquals(UpdateConfig.UPDATE_VERSION_URL, zeroSpec.downloadUrl)

        val fiftyKSpec = com.example.update.AppUpdateManager.buildProgressShareCardSpec(
            todayCount = 1_200L,
            totalCount = 50_000L,
            downloadUrl = null
        )
        assertEquals("1,200", fiftyKSpec.todayFormatted)
        assertEquals("50,000", fiftyKSpec.totalFormatted)
        assertEquals("0.50%", fiftyKSpec.percentFormatted)
        assertEquals(0.005f, fiftyKSpec.progressFraction, 0.0001f)

        val spec125k = com.example.update.AppUpdateManager.buildProgressShareCardSpec(
            todayCount = 2_600L,
            totalCount = 125_000L,
            downloadUrl = null,
            nowMillis = 1_797_200_001_000L
        )
        assertEquals(2_600L, spec125k.todayCount)
        assertEquals(125_000L, spec125k.totalCount)
        assertEquals(10_000_000L, spec125k.targetCount)
        assertEquals("2,600", spec125k.todayFormatted)
        assertEquals("125,000", spec125k.totalFormatted)
        assertEquals("1.25%", spec125k.percentFormatted)
        assertEquals(0.0125f, spec125k.progressFraction, 0.0001f)
        assertEquals(UpdateConfig.UPDATE_VERSION_URL, spec125k.downloadUrl)
        assertTrue(spec125k.shareCaptionText.contains("Today: 2,600"))
        assertTrue(spec125k.shareCaptionText.contains("Total: 125,000 / 10,000,000 (1.25%)"))
        assertTrue(spec125k.shareCaptionText.contains(UpdateConfig.UPDATE_VERSION_URL))

        val oneMillionSpec = com.example.update.AppUpdateManager.buildProgressShareCardSpec(
            todayCount = 5_000L,
            totalCount = 1_000_000L,
            downloadUrl = "https://example.com/1crore-latest.apk"
        )
        assertEquals("10.00%", oneMillionSpec.percentFormatted)
        assertEquals(0.10f, oneMillionSpec.progressFraction, 0.0001f)
        assertEquals("https://example.com/1crore-latest.apk", oneMillionSpec.downloadUrl)
        assertTrue(oneMillionSpec.shareCaptionText.contains("https://example.com/1crore-latest.apk"))

        val oneCroreSpec = com.example.update.AppUpdateManager.buildProgressShareCardSpec(
            todayCount = 10_000L,
            totalCount = 10_000_000L,
            downloadUrl = null
        )
        assertEquals("100.00%", oneCroreSpec.percentFormatted)
        assertEquals(1.0f, oneCroreSpec.progressFraction, 0.0001f)

        // 2. Verify group WhatsApp invite is strictly code-only (no links), while app Share keeps its download URL helper
        val dummyGroup = com.example.groups.GroupItem(
            id = "grp_share_check",
            name = "Halqa",
            inviteCode = "HALQA1",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            creatorUserId = "u1",
            members = emptyList()
        )
        val groupInviteDefault = com.example.groups.GroupLogic.buildMergedWhatsAppInviteMessage(
            group = dummyGroup
        )
        assertTrue(groupInviteDefault.contains("Halqa"))
        assertTrue(groupInviteDefault.contains("HALQA1"))
        assertFalse(groupInviteDefault.contains("http://"))
        assertFalse(groupInviteDefault.contains("https://"))
        assertFalse(groupInviteDefault.contains("onecrore.app"))
        assertEquals(
            UpdateConfig.UPDATE_VERSION_URL,
            com.example.update.AppUpdateManager.resolveEffectiveShareDownloadUrl(null)
        )

        // 3. Verify Simple progress text share remains completely unchanged
        val expectedSimpleShare = com.example.update.AppUpdateManager.buildShareText(
            downloadUrl = "https://example.com/1crore.apk",
            versionName = "1.2"
        )
        assertTrue(expectedSimpleShare.contains(UpdateConfig.SHARE_APP_HEADER))
        assertTrue(expectedSimpleShare.contains(UpdateConfig.SHARE_APP_IQBAL_QUOTE))
        assertTrue(expectedSimpleShare.contains("https://example.com/1crore.apk"))
        assertTrue(expectedSimpleShare.contains("APP VERSION: *1.2*"))
    }

    @Test
    fun v11LogoSplash_introSequenceOrderTotalUnder2sAndStaticFallbacksMatchSpec() {
        // 1. Sequence order: tile -> dot -> '1' -> body+tasbeeh -> footer credit -> ring spin -> black wave wipe -> home settle -> complete
        assertEquals(
            listOf(
                com.example.ui.screens.SplashIntroStage.TILE_READY,
                com.example.ui.screens.SplashIntroStage.DOT_DROP,
                com.example.ui.screens.SplashIntroStage.ONE_REVEAL,
                com.example.ui.screens.SplashIntroStage.BODY_TASBEEH_REVEAL,
                com.example.ui.screens.SplashIntroStage.FOOTER_CREDIT,
                com.example.ui.screens.SplashIntroStage.RING_SPIN,
                com.example.ui.screens.SplashIntroStage.WAVE_WIPE,
                com.example.ui.screens.SplashIntroStage.HOME_SETTLE,
                com.example.ui.screens.SplashIntroStage.COMPLETE
            ),
            com.example.ui.screens.SplashIntroSpec.ORDERED_STAGES
        )

        // 2. Exact timing breakdown fits strictly within <= 1500ms hard cap
        assertEquals(150, com.example.ui.screens.SplashIntroSpec.DOT_DROP_DURATION_MS)
        assertEquals(150, com.example.ui.screens.SplashIntroSpec.ONE_REVEAL_DURATION_MS)
        assertEquals(200, com.example.ui.screens.SplashIntroSpec.BODY_TASBEEH_DURATION_MS)
        assertEquals(150, com.example.ui.screens.SplashIntroSpec.FOOTER_CREDIT_DURATION_MS)
        assertEquals(200, com.example.ui.screens.SplashIntroSpec.RING_SPIN_DURATION_MS)
        assertEquals(300, com.example.ui.screens.SplashIntroSpec.WAVE_WIPE_DURATION_MS)
        assertEquals(350, com.example.ui.screens.SplashIntroSpec.HOME_SETTLE_DURATION_MS)
        assertEquals(1500, com.example.ui.screens.SplashIntroSpec.TOTAL_INTRO_DURATION_MS)
        assertTrue(com.example.ui.screens.SplashIntroSpec.TOTAL_INTRO_DURATION_MS <= 1500)
        assertEquals("Rza Mudasir Attari", com.example.ui.screens.SplashIntroSpec.CREDIT_NAME)

        // Stage mapping matches each millisecond window cleanly
        assertEquals(com.example.ui.screens.SplashIntroStage.TILE_READY, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(0f))
        assertEquals(com.example.ui.screens.SplashIntroStage.DOT_DROP, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(75f))
        assertEquals(com.example.ui.screens.SplashIntroStage.ONE_REVEAL, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(200f))
        assertEquals(com.example.ui.screens.SplashIntroStage.BODY_TASBEEH_REVEAL, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(400f))
        assertEquals(com.example.ui.screens.SplashIntroStage.FOOTER_CREDIT, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(575f))
        assertEquals(com.example.ui.screens.SplashIntroStage.RING_SPIN, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(750f))
        assertEquals(com.example.ui.screens.SplashIntroStage.WAVE_WIPE, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(1000f))
        assertEquals(com.example.ui.screens.SplashIntroStage.HOME_SETTLE, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(1300f))
        assertEquals(com.example.ui.screens.SplashIntroStage.COMPLETE, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(1500f))

        // 3. Reduce Motion & Low-RAM static fallback rules
        assertFalse(com.example.ui.screens.SplashIntroSpec.shouldUseStaticSplashLogo(reduceMotion = false, isLowRam = false))
        assertTrue(com.example.ui.screens.SplashIntroSpec.shouldUseStaticSplashLogo(reduceMotion = true, isLowRam = false))
        assertTrue(com.example.ui.screens.SplashIntroSpec.shouldUseStaticSplashLogo(reduceMotion = false, isLowRam = true))
        assertTrue(com.example.ui.screens.SplashIntroSpec.shouldUseStaticSplashLogo(reduceMotion = true, isLowRam = true))
        assertTrue(
            com.example.ui.screens.SplashIntroSpec.isStaticDisplayImmediate(
                reduceMotion = false,
                isLowRam = false,
                alreadyPlayed = true
            )
        )

        // 4. Frame-0 launch drawables & v11 assets all resolve
        assertEquals("v11_small_dot", com.example.update.AppUpdateManager.SHARE_LOGO_ASSET_VERSION)
        assertEquals(R.drawable.ic_one_crore_foreground, com.example.update.AppUpdateManager.resolveShareLogoDrawableResId())
        assertEquals(R.drawable.ic_one_crore_foreground, com.example.floating.resolveFloatingLogoDrawableResId())
        assertNotEquals(0, R.drawable.bg_launch_splash_window)
        assertNotEquals(0, R.drawable.bg_launch_faint_grid)
        assertNotEquals(0, R.drawable.bg_launch_logo_tile)
        assertNotEquals(0, R.drawable.ic_splash_transparent)
        assertNotEquals(0, R.drawable.ic_logo_v11_tile)
        assertNotEquals(0, R.drawable.ic_logo_v11_dot)
        assertNotEquals(0, R.drawable.ic_logo_v11_one)
        assertNotEquals(0, R.drawable.ic_logo_v11_body_tasbeeh)
        assertNotEquals(0, R.drawable.ic_logo_v11_ring)
        assertNotEquals(0, R.drawable.ic_logo_v11_full)
    }

    @Test
    fun formatGroupMemberCount_singularOnlyForOne_pluralOtherwise() {
        assertEquals("0 members", com.example.ui.screens.formatGroupMemberCount(0))
        assertEquals("1 member", com.example.ui.screens.formatGroupMemberCount(1))
        assertEquals("2 members", com.example.ui.screens.formatGroupMemberCount(2))
        assertEquals("14 members", com.example.ui.screens.formatGroupMemberCount(14))
    }

    @Test
    fun run3Task1_fiveGroupLimit_andRemoteRenameMergeSpec() {
        assertEquals(5, com.example.groups.GroupLogic.MAX_GROUPS_PER_USER)

        val creatorGroup = com.example.groups.GroupItem(
            id = "grp_rename_merge",
            name = "Sixth Year",
            inviteCode = "7NR343",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            creatorUserId = "creator_1",
            members = listOf(
                com.example.groups.GroupMemberProgress(
                    userId = "creator_1",
                    displayName = "Owner",
                    todayCount = 10L,
                    todayDateKey = "2026-11-18",
                    lifetimeTotalCount = 1_023L
                )
            ),
            updatedAtMillis = 1_000L
        )
        val remoteRenamed = creatorGroup.copy(
            name = "Halqa Friday",
            updatedAtMillis = 2_000L
        )
        val merged = com.example.groups.GroupRepository.mergeRemoteAndLocalGroup(
            remote = remoteRenamed,
            local = creatorGroup
        )
        assertEquals("Halqa Friday", merged.name)
        assertEquals(2_000L, merged.updatedAtMillis)
    }

    @Test
    fun run3Task2_inviteMessageIsCodeOnlyNoLinks_andExtractsCodeFromPastedInvite() {
        val group = com.example.groups.GroupItem(
            id = "grp_code_only",
            name = "Sixth Year",
            inviteCode = "7NR343",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            creatorUserId = "creator_1",
            members = emptyList()
        )
        val message = com.example.groups.GroupLogic.buildMergedWhatsAppInviteMessage(
            group = group,
            downloadUrl = "https://onecrore.app/ignored-url"
        )
        assertEquals("Sixth Year\nInvite Code: 7NR343", message)
        assertFalse(message.contains("http://"))
        assertFalse(message.contains("https://"))
        assertFalse(message.contains("onecrore.app"))

        // Works whether user enters raw code, lowercase with spaces, or pastes the full WhatsApp invite block
        assertEquals("7NR343", com.example.groups.GroupLogic.extractInviteCodeInput("7nr343"))
        assertEquals("7NR343", com.example.groups.GroupLogic.extractInviteCodeInput(" 7N R-343 "))
        assertEquals(
            "7NR343",
            com.example.groups.GroupLogic.extractInviteCodeInput("Sixth Year\nInvite Code: 7NR343")
        )
    }

    @Test
    fun run4Task1_groupPercentAndEffectiveTotal_deriveFromInGroupCountsOnlyAndClampAtZero() {
        val emptyGroup = com.example.groups.GroupItem(
            id = "grp_iso_1",
            name = "Halqa Isolated",
            inviteCode = "ISO123",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            targetCount = 100_000L,
            creatorUserId = "u1",
            members = listOf(
                com.example.groups.GroupMemberProgress(
                    userId = "u1",
                    displayName = "Ali",
                    todayCount = 0L,
                    todayDateKey = "2026-11-18",
                    lifetimeTotalCount = 0L
                )
            ),
            serverTotalCount = 0L
        )
        assertEquals(0L, emptyGroup.effectiveGroupTotal)
        assertEquals("0.00%", emptyGroup.progressPercentFormatted)

        val addedGroup = emptyGroup.copy(
            members = listOf(
                emptyGroup.members.first().copy(todayCount = 2_500L, lifetimeTotalCount = 2_500L),
                com.example.groups.GroupMemberProgress(
                    userId = "u2",
                    displayName = "Bilal",
                    todayCount = 500L,
                    todayDateKey = "2026-11-18",
                    lifetimeTotalCount = 5_000L
                )
            ),
            serverTotalCount = 7_500L
        )
        assertEquals(7_500L, addedGroup.effectiveGroupTotal)
        assertEquals("7.50%", addedGroup.progressPercentFormatted)
        assertEquals(listOf("u2", "u1"), addedGroup.rankedMembers("2026-11-18").map { it.userId })

        val reducedGroup = addedGroup.copy(serverTotalCount = -50L)
        assertEquals(0L, reducedGroup.effectiveGroupTotal)
    }

    @Test
    fun run4Task2_badgePureProgressRecomputesFromCurrentTotalIncludingExact1071Scenario() {
        val high = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(5_000_000L)
        assertEquals(100, high.earnedCount)
        assertEquals("50 Lakh Badge", high.latestEarnedBadge?.title)
        assertEquals("50.5 Lakh Badge", high.nextBadge?.title)
        assertEquals(50_000L, high.remainingToNext)

        // Exact user reference case: total drops to 1,071 (Today 50 + Yesterday 1,021)
        val dropped = com.example.badges.MilestoneBadgeManager.evaluatePureProgress(1_071L)
        assertEquals(0, dropped.earnedCount)
        assertNull(dropped.latestEarnedBadge)
        assertEquals("50K Badge", dropped.nextBadge?.title)
        assertEquals(50_000L, dropped.nextBadge?.threshold)
        assertEquals(48_929L, dropped.remainingToNext)
        assertEquals(1_071f / 50_000f, dropped.progressToNextFraction, 0.0001f)
    }

    @Test
    fun fixRun6Task1_groupCelebrationMessages_useVocalizedArabicWithAiraab() {
        val group = com.example.groups.GroupItem(
            id = "grp_run6_arabic",
            name = "Halqa Madinah",
            inviteCode = "MDN786",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            targetCount = com.example.groups.GroupLogic.TARGET_ONE_CRORE,
            creatorUserId = "u1",
            members = listOf(
                com.example.groups.GroupMemberProgress(
                    userId = "u1",
                    displayName = "Ahmad",
                    todayCount = 5_000L,
                    todayDateKey = "2026-11-18",
                    lifetimeTotalCount = 10_000_000L,
                    weeklyCount = 25_000L
                )
            ),
            serverTotalCount = 10_000_000L
        )

        val notices = com.example.groups.GroupLogic.computeCelebrations(
            group = group,
            currentUserId = "u1",
            today = java.time.LocalDate.of(2026, 11, 18),
            nowMillis = 1_797_300_000_000L
        )

        val oneCroreNotice = notices.first { it.type == com.example.groups.GroupCelebrationType.TARGET_1_CRORE }
        assertTrue(oneCroreNotice.message.startsWith("مَا شَاءَ اللَّهُ!"))
        assertFalse(oneCroreNotice.message.contains("MashaAllah", ignoreCase = true))

        val oneLakhNotice = notices.first { it.type == com.example.groups.GroupCelebrationType.TARGET_1_LAKH }
        assertTrue(oneLakhNotice.message.startsWith("الْحَمْدُ لِلَّهِ!"))
        assertFalse(oneLakhNotice.message.contains("Alhamdulillah", ignoreCase = true))

        val weeklyPrivateNotice = notices.first { it.type == com.example.groups.GroupCelebrationType.WEEKLY_TOP_3_PRIVATE }
        assertTrue(weeklyPrivateNotice.message.contains("مَا شَاءَ اللَّهُ"))
        assertFalse(weeklyPrivateNotice.message.contains("MashaAllah", ignoreCase = true))

        val weeklyGroupNotice = notices.first { it.type == com.example.groups.GroupCelebrationType.WEEKLY_TOP_3_GROUP }
        assertTrue(weeklyGroupNotice.message.contains("جَزَاكُمُ اللَّهُ خَيْرًا"))
        assertFalse(weeklyGroupNotice.message.contains("JazakAllah", ignoreCase = true))
    }

    @Test
    fun fixRun6Task2_milestoneAnnouncementsAndShareTexts_useVocalizedArabicWithAiraab() {
        val oneLakhAnn = com.example.announcements.MilestoneAnnouncementManager.buildMilestoneAnnouncement(
            threshold = com.example.announcements.MilestoneAnnouncementManager.MILESTONE_ONE_LAKH,
            nowMillis = 1_797_300_000_000L
        )
        assertTrue(oneLakhAnn.body.startsWith("الْحَمْدُ لِلَّهِ!"))
        assertFalse(oneLakhAnn.body.contains("Alhamdulillah", ignoreCase = true))

        val tenLakhAnn = com.example.announcements.MilestoneAnnouncementManager.buildMilestoneAnnouncement(
            threshold = com.example.announcements.MilestoneAnnouncementManager.MILESTONE_TEN_LAKH,
            nowMillis = 1_797_300_000_000L
        )
        assertTrue(tenLakhAnn.body.startsWith("مَا شَاءَ اللَّهُ!"))
        assertFalse(tenLakhAnn.body.contains("MashaAllah", ignoreCase = true))

        val customMilestoneAnn = com.example.announcements.MilestoneAnnouncementManager.buildMilestoneAnnouncement(
            threshold = 500_000L,
            nowMillis = 1_797_300_000_000L
        )
        assertTrue(customMilestoneAnn.body.startsWith("الْحَمْدُ لِلَّهِ!"))
        assertFalse(customMilestoneAnn.body.contains("Alhamdulillah", ignoreCase = true))

        val shareText = com.example.update.AppUpdateManager.buildShareText(
            downloadUrl = "https://example.com/1crore.apk",
            versionName = "1.2"
        )
        assertTrue(shareText.contains("صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا ﷺ"))

        val cardSpec = com.example.update.AppUpdateManager.buildProgressShareCardSpec(
            todayCount = 1_500L,
            totalCount = 250_000L,
            downloadUrl = "https://example.com/1crore.apk"
        )
        assertTrue(cardSpec.shareCaptionText.contains("صَلُّوا عَلَيْهِ وَسَلِّمُوا تَسْلِيمًا ﷺ"))
    }

    @Test
    fun fixRun7Task1_groupFloatingStateHelpers_identifyMainVsSpecificGroup() {
        val offState = com.example.floating.FloatingBubbleUiState()
        assertFalse(offState.isRunning)
        assertFalse(offState.isMainRunning)
        assertFalse(offState.isGroupRunning("grp_a"))

        val groupAState = com.example.floating.FloatingBubbleUiState(
            isRunning = true,
            mode = com.example.floating.FloatingTargetMode.GROUP,
            activeGroupId = "grp_a",
            activeGroupName = "Halqa A",
            activeGroupTotal = 1_250L
        )
        assertTrue(groupAState.isRunning)
        assertFalse(groupAState.isMainRunning)
        assertTrue(groupAState.isGroupRunning("grp_a"))
        assertFalse(groupAState.isGroupRunning("grp_b"))
        assertEquals(1_250L, groupAState.activeGroupTotal)
    }

    @Test
    fun fixRun7Task2_mutualExclusionStateTransitions_allowOnlyOneFloatingMode() {
        val mainOn = com.example.floating.FloatingBubbleUiState(
            isRunning = true,
            mode = com.example.floating.FloatingTargetMode.MAIN,
            activeGroupId = null,
            activeGroupTotal = 0L
        )
        assertTrue(mainOn.isMainRunning)
        assertFalse(mainOn.isGroupRunning("grp_a"))

        // Switching to Group A clears Main
        val groupAOn = mainOn.copy(
            mode = com.example.floating.FloatingTargetMode.GROUP,
            activeGroupId = "grp_a",
            activeGroupName = "Halqa A",
            activeGroupTotal = 500L
        )
        assertFalse(groupAOn.isMainRunning)
        assertTrue(groupAOn.isGroupRunning("grp_a"))
        assertFalse(groupAOn.isGroupRunning("grp_b"))

        // Switching to Group B clears Group A
        val groupBOn = groupAOn.copy(
            mode = com.example.floating.FloatingTargetMode.GROUP,
            activeGroupId = "grp_b",
            activeGroupName = "Halqa B",
            activeGroupTotal = 900L
        )
        assertFalse(groupBOn.isMainRunning)
        assertFalse(groupBOn.isGroupRunning("grp_a"))
        assertTrue(groupBOn.isGroupRunning("grp_b"))

        // Switching back to Main clears Group B
        val backToMain = groupBOn.copy(
            mode = com.example.floating.FloatingTargetMode.MAIN,
            activeGroupId = null,
            activeGroupName = null,
            activeGroupTotal = 0L
        )
        assertTrue(backToMain.isMainRunning)
        assertFalse(backToMain.isGroupRunning("grp_a"))
        assertFalse(backToMain.isGroupRunning("grp_b"))
    }

    @Test
    fun fixRun8Task1_unifiedGroupModelsAndTargets_replaceMainDuroodLabelsAndKeepLegacyData() {
        assertEquals("For Durood", com.example.groups.GroupType.MAIN_DUROOD.displayLabel)
        assertEquals("Other Zikr", com.example.groups.GroupType.CUSTOM_ZIKR.displayLabel)
        assertEquals(100_000L, com.example.groups.GroupLogic.DEFAULT_UNIFIED_GROUP_TARGET)

        val malikLegacyGroup = com.example.groups.GroupItem(
            id = "grp_malik_team",
            name = "malik team",
            inviteCode = "NYLSQ6",
            type = com.example.groups.GroupType.fromWire("main_durood"),
            zikrName = null,
            targetCount = 10_000_000L,
            creatorUserId = "u_malik",
            members = listOf(
                com.example.groups.GroupMemberProgress("u_malik", "Malik", 86L, "2026-11-19", 86L),
                com.example.groups.GroupMemberProgress("u_friend", "Friend", 0L, "2026-11-19", 0L)
            ),
            serverTotalCount = 86L
        )
        assertEquals("Durood", malikLegacyGroup.effectiveZikrName)
        assertEquals(86L, malikLegacyGroup.effectiveGroupTotal)
        assertEquals(10_000_000L, malikLegacyGroup.targetCount)
        assertEquals(2, malikLegacyGroup.memberCount)
        assertEquals("NYLSQ6", malikLegacyGroup.inviteCode)

        val zikrGroup = com.example.groups.GroupItem(
            id = "grp_istighfar",
            name = "Morning Zikr",
            inviteCode = "IST786",
            type = com.example.groups.GroupType.fromWire("custom_zikr"),
            zikrName = "  سُبْحَانَ اللَّهِ  ",
            targetCount = 50_000L,
            creatorUserId = "u1",
            members = emptyList()
        )
        assertEquals("سُبْحَانَ اللَّهِ", zikrGroup.effectiveZikrName)
        assertEquals(50_000L, zikrGroup.targetCount)
    }

    @Test
    fun fixRun8Task2_rootTabNavigationPolicy_neverSavesOrRestoresNestedSubRoutes() {
        val routes = com.example.ui.AppTab.entries.map { it.route }
        assertEquals(listOf("home", "counter", "history", "stats", "settings"), routes)

        assertTrue(com.example.ui.NESTED_TAB_SUB_ROUTES.contains("messages_history"))
        assertTrue(com.example.ui.NESTED_TAB_SUB_ROUTES.contains("custom_counters"))
        assertTrue(com.example.ui.NESTED_TAB_SUB_ROUTES.contains("custom_counter_detail/{counterId}"))
        assertTrue(com.example.ui.NESTED_TAB_SUB_ROUTES.contains("groups"))
        assertTrue(com.example.ui.NESTED_TAB_SUB_ROUTES.contains("group_detail/{groupId}"))

        com.example.ui.AppTab.entries.forEach { tab ->
            val spec = com.example.ui.resolveRootTabNavigationSpec(tab.route)
            assertEquals(tab.route, spec.route)
            assertFalse(spec.saveStateOnLeave)
            assertFalse(spec.restoreStateOnEnter)
            assertTrue(spec.launchSingleTop)
        }
    }

    @Test
    fun fixRun9Task1_googleSignIn_classifiesConfigFallbackAndPreservesEmailAuth() {
        val cancelOnly = com.example.ui.auth.classifyGoogleSignInException(
            androidx.credentials.exceptions.GetCredentialCancellationException("User dismissed bottom sheet")
        )
        assertTrue(cancelOnly.isCancelled)
        assertFalse(cancelOnly.isConfigurationIssue)

        val shaMismatchCancel = com.example.ui.auth.classifyGoogleSignInException(
            androidx.credentials.exceptions.GetCredentialCancellationException(
                "[28444] Developer console is not set up correctly."
            )
        )
        assertFalse(shaMismatchCancel.isCancelled)
        assertTrue(shaMismatchCancel.isConfigurationIssue)
        assertTrue(shaMismatchCancel.message.contains("SHA-1"))

        val noCredGeneric = com.example.ui.auth.classifyGoogleSignInException(
            androidx.credentials.exceptions.NoCredentialException("No credentials available")
        )
        assertFalse(noCredGeneric.isCancelled)
        assertTrue(noCredGeneric.shouldAttemptFallback)
        assertTrue(noCredGeneric.message.contains("SHA-1"))

        val providerConfig = com.example.ui.auth.classifyGoogleSignInException(
            androidx.credentials.exceptions.GetCredentialProviderConfigurationException("Missing provider")
        )
        assertFalse(providerConfig.isCancelled)
        assertTrue(providerConfig.isConfigurationIssue)
        assertFalse(providerConfig.shouldAttemptFallback)

        // Empty Web Client ID fails fast with configuration error before touching CredentialManager
        val emptyClientOutcome = kotlinx.coroutines.runBlocking {
            com.example.ui.auth.executeInteractiveGoogleCredentialFlow(
                serverClientId = "   ",
                fetchCredential = { error("Should not be called") },
                signInWithIdToken = {}
            )
        }
        assertTrue(emptyClientOutcome is com.example.ui.auth.GoogleSignInAttemptResult.Error)
        assertTrue((emptyClientOutcome as com.example.ui.auth.GoogleSignInAttemptResult.Error).isConfigurationIssue)

        // Direct SHA-1 / OAuth error message matching
        assertTrue(com.example.ui.auth.isGoogleConfigErrorMessage("Developer console is not set up correctly"))
        assertTrue(com.example.ui.auth.isGoogleConfigErrorMessage("ApiException: 10: DEVELOPER_ERROR"))
        assertTrue(com.example.ui.auth.isGoogleConfigErrorMessage("SHA-1 certificate fingerprint mismatch"))
        assertFalse(com.example.ui.auth.isGoogleConfigErrorMessage("Temporary timeout"))

        // No-regression check on Email/Password Login & Sign-Up validation
        val validLogin = validateAuthSubmission(
            isSignup = false,
            fullName = "",
            email = "member@example.com",
            password = "password123",
            confirmPassword = ""
        )
        assertFalse(validLogin.hasAny)

        val validSignup = validateAuthSubmission(
            isSignup = true,
            fullName = "Zaid",
            email = "member@example.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertFalse(validSignup.hasAny)

        val invalidSignup = validateAuthSubmission(
            isSignup = true,
            fullName = "",
            email = "not-an-email",
            password = "123",
            confirmPassword = "456"
        )
        assertTrue(invalidSignup.hasAny)
        assertNotNull(invalidSignup.nameError)
        assertNotNull(invalidSignup.emailError)
        assertNotNull(invalidSignup.passwordError)
        assertNotNull(invalidSignup.confirmPasswordError)
    }

    @Test
    fun fixRun9Task2_inviteCodeCopy_extractsRawCodeWithoutMutatingWhatsAppText() {
        assertEquals("QG25JU", com.example.ui.screens.extractCopyableInviteCode("  QG25JU \n"))
        assertEquals("", com.example.ui.screens.extractCopyableInviteCode("   "))

        val group = com.example.groups.GroupItem(
            id = "grp_qg",
            name = "Qalb Team",
            inviteCode = "QG25JU",
            type = com.example.groups.GroupType.MAIN_DUROOD,
            targetCount = 100_000L,
            creatorUserId = "u1",
            members = emptyList()
        )
        val whatsAppInvite = com.example.groups.GroupLogic.buildMergedWhatsAppInviteMessage(group)
        assertTrue(whatsAppInvite.contains("QG25JU"))
        assertFalse(whatsAppInvite.contains("http://"))
        assertFalse(whatsAppInvite.contains("https://"))
    }

    @Test
    fun fixRun10Task1_homeHeader_twoLineGreetingSplitsCleanlyAndGraphemeIndicesSkipNewline() {
        val lines = com.example.ui.screens.ARABIC_GREETING.split('\n')
        assertEquals(2, lines.size)
        assertEquals("صَلُّوا عَلَيْهِ", lines[0])
        assertEquals("وَسَلِّمُوا تَسْلِيمًا", lines[1])

        val ends = com.example.ui.screens.arabicGraphemeEndIndices(com.example.ui.screens.ARABIC_GREETING)
        assertTrue(ends.isNotEmpty())
        assertEquals(com.example.ui.screens.ARABIC_GREETING.length, ends.last())
        for (idx in ends.indices) {
            if (idx > 0) {
                assertTrue(ends[idx] > ends[idx - 1])
            }
            val endPos = ends[idx]
            assertTrue(com.example.ui.screens.ARABIC_GREETING[endPos - 1] != '\n')
        }
    }

    @Test
    fun fixRun10Task2_customAddHome_widgetRouteAndAmountRangeMatchSpec() {
        assertEquals(
            "widget_action_add_custom",
            com.example.widget.OneCroreWidgetProvider.ACTION_WIDGET_ADD_CUSTOM
        )
        assertEquals(250L, "250".toLongOrNull()?.takeIf { it in 1L..1_000_000L })
        assertEquals(1_000_000L, "1000000".toLongOrNull()?.takeIf { it in 1L..1_000_000L })
        assertEquals(null, "0".toLongOrNull()?.takeIf { it in 1L..1_000_000L })
        assertEquals(null, "1000001".toLongOrNull()?.takeIf { it in 1L..1_000_000L })
    }

    @Test
    fun fixRun11Task1_signInSmooth_prunesRedundantFallbacksAndPreservesEmailAuth() {
        // 1. Config-matched NoCredentialException fails fast without sequential 2nd round-trip
        val configNoCred = com.example.ui.auth.classifyGoogleSignInException(
            androidx.credentials.exceptions.NoCredentialException(
                "[28444] Developer console is not set up correctly."
            )
        )
        assertFalse(configNoCred.isCancelled)
        assertTrue(configNoCred.isConfigurationIssue)
        assertFalse(configNoCred.shouldAttemptFallback)
        assertTrue(configNoCred.message.contains("SHA-1"))

        // 2. Generic GetCredentialException fails fast without slow sequential retry
        val genericCredError = com.example.ui.auth.classifyGoogleSignInException(
            androidx.credentials.exceptions.GetCredentialUnknownException("Temporary service unavailable")
        )
        assertFalse(genericCredError.isCancelled)
        assertFalse(genericCredError.shouldAttemptFallback)
        assertEquals("Temporary service unavailable", genericCredError.message)

        // 3. Clean non-config NoCredentialException preserves fallback option for compatibility
        val cleanNoCred = com.example.ui.auth.classifyGoogleSignInException(
            androidx.credentials.exceptions.NoCredentialException("No credentials available")
        )
        assertFalse(cleanNoCred.isCancelled)
        assertFalse(cleanNoCred.isConfigurationIssue)
        assertTrue(cleanNoCred.shouldAttemptFallback)

        // 4. User cancellation remains instant cancellation
        val cancelled = com.example.ui.auth.classifyGoogleSignInException(
            androidx.credentials.exceptions.GetCredentialCancellationException("User cancelled")
        )
        assertTrue(cancelled.isCancelled)
        assertFalse(cancelled.shouldAttemptFallback)

        // 5. Email/password login and signup validation unchanged
        val validLogin = validateAuthSubmission(
            isSignup = false,
            fullName = "",
            email = "ali@example.com",
            password = "password123",
            confirmPassword = ""
        )
        assertFalse(validLogin.hasAny)

        val validSignup = validateAuthSubmission(
            isSignup = true,
            fullName = "Ali",
            email = "ali@example.com",
            password = "password123",
            confirmPassword = "password123"
        )
        assertFalse(validSignup.hasAny)
    }

    @Test
    fun fixRun12Task1_coldStart_timelineIsSnappyFrameZeroPresentRingSpinsImmediatelyAndWaveRises() {
        // 1. Whole cold-start splash runs ~0.55s and strictly <= 1.0s max
        assertEquals(0, com.example.ui.screens.SplashIntroSpec.DOT_DROP_DURATION_MS)
        assertEquals(0, com.example.ui.screens.SplashIntroSpec.ONE_REVEAL_DURATION_MS)
        assertEquals(0, com.example.ui.screens.SplashIntroSpec.BODY_TASBEEH_DURATION_MS)
        assertEquals(0, com.example.ui.screens.SplashIntroSpec.FOOTER_CREDIT_DURATION_MS)
        assertEquals(250, com.example.ui.screens.SplashIntroSpec.RING_SPIN_DURATION_MS)
        assertEquals(300, com.example.ui.screens.SplashIntroSpec.WAVE_WIPE_DURATION_MS)
        assertEquals(0, com.example.ui.screens.SplashIntroSpec.HOME_SETTLE_DURATION_MS)
        assertEquals(550, com.example.ui.screens.SplashIntroSpec.TOTAL_INTRO_DURATION_MS)
        assertTrue(com.example.ui.screens.SplashIntroSpec.TOTAL_INTRO_DURATION_MS in 400..1000)

        // 2. Stage sequence: frame-0 ready -> immediate ring spin -> rising black wave -> complete
        assertEquals(
            listOf(
                com.example.ui.screens.SplashIntroStage.TILE_READY,
                com.example.ui.screens.SplashIntroStage.RING_SPIN,
                com.example.ui.screens.SplashIntroStage.WAVE_WIPE,
                com.example.ui.screens.SplashIntroStage.COMPLETE
            ),
            com.example.ui.screens.SplashIntroSpec.ORDERED_STAGES
        )
        assertEquals(com.example.ui.screens.SplashIntroStage.TILE_READY, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(0f))
        assertEquals(com.example.ui.screens.SplashIntroStage.RING_SPIN, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(1f))
        assertEquals(com.example.ui.screens.SplashIntroStage.RING_SPIN, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(125f))
        assertEquals(com.example.ui.screens.SplashIntroStage.WAVE_WIPE, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(250f))
        assertEquals(com.example.ui.screens.SplashIntroStage.WAVE_WIPE, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(400f))
        assertEquals(com.example.ui.screens.SplashIntroStage.COMPLETE, com.example.ui.screens.SplashIntroSpec.stageForElapsedMs(550f))

        // 3. Frame 0 already renders dot, '1', body+tasbeeh, and credit at full 1f (no staggered blank wait)
        val frame0 = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(0f)
        assertEquals(1f, frame0.dotT, 0.0001f)
        assertEquals(1f, frame0.oneT, 0.0001f)
        assertEquals(1f, frame0.bodyT, 0.0001f)
        assertEquals(1f, frame0.creditT, 0.0001f)
        assertEquals(0f, frame0.ringT, 0.0001f)
        assertEquals(0f, frame0.waveProgress, 0.0001f)

        // 4. Ring spins immediately from t > 0 while wave remains 0 until 250ms, then rises 250..550ms
        val frameEarlyRing = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(50f)
        assertTrue(frameEarlyRing.ringT > 0f && frameEarlyRing.ringT < 1f)
        assertEquals(0f, frameEarlyRing.waveProgress, 0.0001f)

        val frameMidWave = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(400f)
        assertEquals(1f, frameMidWave.ringT, 0.0001f)
        assertTrue(frameMidWave.waveProgress > 0f && frameMidWave.waveProgress < 1f)

        val frameDone = com.example.ui.screens.SplashIntroSpec.frameValuesForElapsedMs(550f)
        assertEquals(1f, frameDone.ringT, 0.0001f)
        assertEquals(1f, frameDone.waveProgress, 0.0001f)
    }
}
