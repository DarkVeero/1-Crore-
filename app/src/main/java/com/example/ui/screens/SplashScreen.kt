package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.BlackPillButton
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.isLowEndOrLowRamDevice
import com.example.ui.components.rememberReduceMotion
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.PureBlack
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

enum class SplashIntroStage {
    TILE_READY,
    DOT_DROP,
    ONE_REVEAL,
    BODY_TASBEEH_REVEAL,
    FOOTER_CREDIT,
    RING_SPIN,
    WAVE_WIPE,
    HOME_SETTLE,
    COMPLETE
}

data class SplashFrameValues(
    val dotT: Float,
    val oneT: Float,
    val bodyT: Float,
    val creditT: Float,
    val ringT: Float,
    val waveProgress: Float
)

object SplashIntroSpec {
    const val CREDIT_NAME: String = "Rza Mudasir Attari"
    const val DOT_DROP_DURATION_MS: Int = 0
    const val ONE_REVEAL_DURATION_MS: Int = 0
    const val BODY_TASBEEH_DURATION_MS: Int = 0
    const val FOOTER_CREDIT_DURATION_MS: Int = 0
    const val START_HOLD_MS: Int = 40
    const val RING_SPIN_DURATION_MS: Int = 560
    const val WAVE_WIPE_DURATION_MS: Int = 300
    const val HOME_SETTLE_DURATION_MS: Int = 0
    const val REDUCE_MOTION_HOLD_MS: Long = 180L
    const val LOGO_TILE_SIZE_DP: Int = 144
    const val MAX_RING_SWEEP_DEGREES: Float = 270f

    // Cumulative stage boundaries (900 ms total, strictly <= 1000 ms from tap to Home)
    const val DOT_END_MS: Int = DOT_DROP_DURATION_MS // 0
    const val ONE_END_MS: Int = DOT_END_MS + ONE_REVEAL_DURATION_MS // 0
    const val BODY_END_MS: Int = ONE_END_MS + BODY_TASBEEH_DURATION_MS // 0
    const val CREDIT_END_MS: Int = BODY_END_MS + FOOTER_CREDIT_DURATION_MS // 0
    const val RING_START_MS: Int = CREDIT_END_MS + START_HOLD_MS // 40
    const val RING_END_MS: Int = RING_START_MS + RING_SPIN_DURATION_MS // 600
    const val WAVE_END_MS: Int = RING_END_MS + WAVE_WIPE_DURATION_MS // 900
    const val TOTAL_INTRO_DURATION_MS: Int = WAVE_END_MS + HOME_SETTLE_DURATION_MS // 900

    private val ringEaseOut = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    val ORDERED_STAGES: List<SplashIntroStage> = listOf(
        SplashIntroStage.TILE_READY,
        SplashIntroStage.RING_SPIN,
        SplashIntroStage.WAVE_WIPE,
        SplashIntroStage.COMPLETE
    )

    fun stageForElapsedMs(elapsedMs: Float): SplashIntroStage = when {
        elapsedMs <= START_HOLD_MS -> SplashIntroStage.TILE_READY
        elapsedMs < RING_END_MS -> SplashIntroStage.RING_SPIN
        elapsedMs < WAVE_END_MS -> SplashIntroStage.WAVE_WIPE
        else -> SplashIntroStage.COMPLETE
    }

    fun ringSweepDegrees(ringT: Float): Float =
        (ringT.coerceIn(0f, 1f) * MAX_RING_SWEEP_DEGREES).coerceIn(0f, MAX_RING_SWEEP_DEGREES)

    fun frameValuesForElapsedMs(
        elapsedMs: Float,
        showStaticFinal: Boolean = false
    ): SplashFrameValues {
        if (showStaticFinal) {
            return SplashFrameValues(
                dotT = 1f,
                oneT = 1f,
                bodyT = 1f,
                creditT = 1f,
                ringT = 1f,
                waveProgress = 0f
            )
        }
        val tMs = elapsedMs.coerceAtLeast(0f)
        val rawRingT = if (RING_SPIN_DURATION_MS <= 0) {
            1f
        } else {
            ((tMs - RING_START_MS) / RING_SPIN_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        }
        val rawWaveT = if (WAVE_WIPE_DURATION_MS <= 0) {
            1f
        } else {
            ((tMs - RING_END_MS) / WAVE_WIPE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        }
        return SplashFrameValues(
            dotT = 1f,
            oneT = 1f,
            bodyT = 1f,
            creditT = 1f,
            ringT = ringEaseOut.transform(rawRingT),
            waveProgress = FastOutSlowInEasing.transform(rawWaveT)
        )
    }

    fun shouldUseStaticSplashLogo(reduceMotion: Boolean, isLowRam: Boolean): Boolean =
        reduceMotion || isLowRam

    fun isStaticDisplayImmediate(
        reduceMotion: Boolean,
        isLowRam: Boolean,
        alreadyPlayed: Boolean
    ): Boolean = reduceMotion || isLowRam || alreadyPlayed

    @Volatile
    var lastSplashStageHistoryForTest: List<SplashIntroStage> = emptyList()
        private set

    @Volatile
    var lastSplashWasStaticForTest: Boolean = false
        private set

    @Volatile
    var lastPostSignupWasStaticForTest: Boolean = false
        private set

    @Volatile
    var postSignupIntroPlayCountForTest: Int = 0
        private set

    internal fun recordSplashHistory(stages: List<SplashIntroStage>, wasStatic: Boolean) {
        lastSplashStageHistoryForTest = stages
        lastSplashWasStaticForTest = wasStatic
    }

    internal fun recordPostSignupIntro(wasStatic: Boolean) {
        lastPostSignupWasStaticForTest = wasStatic
        postSignupIntroPlayCountForTest += 1
    }

    fun resetTestObservability() {
        lastSplashStageHistoryForTest = emptyList()
        lastSplashWasStaticForTest = false
        lastPostSignupWasStaticForTest = false
        postSignupIntroPlayCountForTest = 0
    }
}

/**
 * Process-level guard ensuring the cold-start splash animation plays at most ONCE
 * per cold start and never restarts on recomposition or navigation changes.
 */
object SplashSessionGuard {
    @Volatile
    var hasPlayedColdStartSplash: Boolean = false
        private set

    fun markPlayed() {
        hasPlayedColdStartSplash = true
    }

    fun resetForTest() {
        hasPlayedColdStartSplash = false
    }
}

@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val isLowRam = remember(context) { isLowEndOrLowRamDevice(context) }
    val alreadyPlayed = remember { SplashSessionGuard.hasPlayedColdStartSplash }
    val showStaticImmediately = SplashIntroSpec.isStaticDisplayImmediate(
        reduceMotion = reduceMotion,
        isLowRam = isLowRam,
        alreadyPlayed = alreadyPlayed
    )

    val elapsedMs = remember {
        Animatable(if (showStaticImmediately) SplashIntroSpec.RING_END_MS.toFloat() else 0f)
    }

    LaunchedEffect(Unit) {
        if (alreadyPlayed) {
            SplashIntroSpec.recordSplashHistory(listOf(SplashIntroStage.COMPLETE), wasStatic = true)
            onFinished()
            return@LaunchedEffect
        }
        if (SplashIntroSpec.shouldUseStaticSplashLogo(reduceMotion, isLowRam)) {
            SplashIntroSpec.recordSplashHistory(listOf(SplashIntroStage.COMPLETE), wasStatic = true)
            delay(SplashIntroSpec.REDUCE_MOTION_HOLD_MS)
            SplashSessionGuard.markPlayed()
            onFinished()
            return@LaunchedEffect
        }

        try {
            elapsedMs.animateTo(
                targetValue = SplashIntroSpec.TOTAL_INTRO_DURATION_MS.toFloat(),
                animationSpec = tween(
                    durationMillis = SplashIntroSpec.TOTAL_INTRO_DURATION_MS,
                    easing = LinearEasing
                )
            )
            SplashIntroSpec.recordSplashHistory(SplashIntroSpec.ORDERED_STAGES, wasStatic = false)
        } finally {
            if (SplashIntroSpec.lastSplashStageHistoryForTest.isEmpty()) {
                SplashIntroSpec.recordSplashHistory(SplashIntroSpec.ORDERED_STAGES, wasStatic = false)
            }
            SplashSessionGuard.markPlayed()
            onFinished()
        }
    }

    V11SplashWaveStage(
        elapsedMsProvider = { elapsedMs.value },
        showStaticFinal = showStaticImmediately,
        reduceMotion = reduceMotion,
        modifier = Modifier
            .fillMaxSize()
            .testTag("cold_start_splash_screen")
            .semantics { contentDescription = "1 Crore" }
    )
}

/**
 * Post-login / post-signup overlay or loading screen.
 * - For returning logins (`playPostSignupIntro == false`): displays the static completed v11 logo
 *   + footer credit on the faint grid with zero re-animation and zero added delay.
 * - After signup (`playPostSignupIntro == true`): plays the ~0.9s v11 animated intro +
 *   footer credit + bottom-to-top black wave wipe uncovering the live destination behind it.
 */
@Composable
fun AnimatedLogoLoadingScreen(
    offlineErrorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onSwitchAccount: (() -> Unit)? = null,
    playPostSignupIntro: Boolean = false,
    onPostSignupIntroFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val isLowRam = remember(context) { isLowEndOrLowRamDevice(context) }
    val staticFallback = SplashIntroSpec.shouldUseStaticSplashLogo(reduceMotion, isLowRam)
    // FIX: never replay the intro after login if the cold-start SplashScreen already played it
    // this process — one seamless splash under 1s, not two.
    val shouldAnimateIntro = playPostSignupIntro && offlineErrorMessage == null && !staticFallback && !SplashSessionGuard.hasPlayedColdStartSplash

    val elapsedMs = remember(playPostSignupIntro, staticFallback) {
        Animatable(if (shouldAnimateIntro) 0f else SplashIntroSpec.RING_END_MS.toFloat())
    }
    var introComplete by remember(playPostSignupIntro, staticFallback) {
        mutableStateOf(!shouldAnimateIntro)
    }

    LaunchedEffect(playPostSignupIntro, staticFallback, offlineErrorMessage) {
        if (!playPostSignupIntro) return@LaunchedEffect
        if (offlineErrorMessage != null) return@LaunchedEffect
        if (SplashSessionGuard.hasPlayedColdStartSplash) {
            // FIX: the cold-start SplashScreen already played this intro once this
            // process — never replay it after login. Show the completed logo and
            // finish immediately so the splash stays under 1s.
            SplashIntroSpec.recordPostSignupIntro(wasStatic = true)
            introComplete = true
            onPostSignupIntroFinished()
            return@LaunchedEffect
        }
        if (staticFallback) {
            SplashIntroSpec.recordPostSignupIntro(wasStatic = true)
            introComplete = true
            delay(SplashIntroSpec.REDUCE_MOTION_HOLD_MS)
            onPostSignupIntroFinished()
            return@LaunchedEffect
        }
        try {
            elapsedMs.animateTo(
                targetValue = SplashIntroSpec.TOTAL_INTRO_DURATION_MS.toFloat(),
                animationSpec = tween(
                    durationMillis = SplashIntroSpec.TOTAL_INTRO_DURATION_MS,
                    easing = LinearEasing
                )
            )
            introComplete = true
        } finally {
            SplashIntroSpec.recordPostSignupIntro(wasStatic = false)
            introComplete = true
            onPostSignupIntroFinished()
        }
    }

    if (offlineErrorMessage == null) {
        V11SplashWaveStage(
            elapsedMsProvider = { elapsedMs.value },
            showStaticFinal = !shouldAnimateIntro || introComplete,
            reduceMotion = reduceMotion,
            modifier = Modifier
                .fillMaxSize()
                .testTag("post_login_logo_loading_screen")
                .semantics { contentDescription = "1 Crore Loading" }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("post_login_logo_loading_screen")
                .semantics { contentDescription = "1 Crore Loading" }
        ) {
            FaintGridBackground(reduceMotion = reduceMotion)

            val palette = AppThemeColors.palette
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 28.dp)
                    .widthIn(max = 420.dp)
                    .align(Alignment.Center)
            ) {
                V11AnimatedLogoTile(
                    showStaticFinal = true,
                    ringT = 1f
                )

                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = offlineErrorMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.onCanvas,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_load_error_message")
                )
                if (onRetry != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    BlackPillButton(
                        text = "Retry",
                        onClick = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 260.dp)
                            .testTag("offline_load_retry_button")
                    )
                }
                if (onSwitchAccount != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onSwitchAccount,
                        modifier = Modifier.testTag("offline_load_switch_account_button")
                    ) {
                        Text(
                            text = "Use a different account",
                            color = palette.onCanvasSubtext
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun V11SplashWaveStage(
    elapsedMsProvider: () -> Float,
    showStaticFinal: Boolean,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val frame = SplashIntroSpec.frameValuesForElapsedMs(
        elapsedMs = elapsedMsProvider(),
        showStaticFinal = showStaticFinal
    )
    val waveProgress = frame.waveProgress
    val creditT = frame.creditT
    val ringT = frame.ringT

    val splashCleared = !showStaticFinal && waveProgress >= 1f
    val clipPath = remember { Path() }
    val waveBandPath = remember { Path() }
    val palette = AppThemeColors.palette

    Box(modifier = modifier) {
        if (!splashCleared) {
            // Splash layer clipped above the rising bottom-to-top curved wave crest
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (waveProgress <= 0f) {
                            drawContent()
                        } else {
                            val h = size.height
                            val w = size.width
                            val bandH = 60.dp.toPx()
                            val amp = 16.dp.toPx() * sin(waveProgress * PI).toFloat()
                            val travel = waveProgress * (h + bandH * 1.45f)
                            val leadCenterY = h - travel

                            clipPath.reset()
                            clipPath.moveTo(0f, 0f)
                            clipPath.lineTo(w, 0f)
                            val steps = 20
                            for (i in steps downTo 0) {
                                val fx = i.toFloat() / steps
                                val x = w * fx
                                val waveY = leadCenterY + amp * sin(fx * 2f * PI + waveProgress * 2.4f).toFloat()
                                clipPath.lineTo(x, waveY)
                            }
                            clipPath.close()

                            clipPath(clipPath) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
            ) {
                FaintGridBackground(reduceMotion = reduceMotion)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Exact full-screen center spot and 144.dp size matching Frame-0 launch window tile
                    V11AnimatedLogoTile(
                        showStaticFinal = showStaticFinal,
                        ringT = ringT,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Bottom footer signature ("Rza Mudasir Attari" clearly separated at footer)
                    if (creditT > 0f) {
                        Text(
                            text = SplashIntroSpec.CREDIT_NAME,
                            fontFamily = InterFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            letterSpacing = 0.4.sp,
                            color = palette.onCanvasSubtext,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .safeDrawingPadding()
                                .padding(bottom = 26.dp)
                                .testTag("splash_credit_name")
                        )
                    }
                }
            }

            // Bottom-to-top curved wave crest wiping the splash away & unveiling Home beneath
            if (waveProgress > 0f && waveProgress < 1f) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("splash_wave_wipe")
                ) {
                    val h = size.height
                    val w = size.width
                    val bandH = 60.dp.toPx()
                    val amp = 16.dp.toPx() * sin(waveProgress * PI).toFloat()
                    val travel = waveProgress * (h + bandH * 1.45f)
                    val leadCenterY = h - travel
                    val trailCenterY = leadCenterY + bandH

                    waveBandPath.reset()
                    val steps = 20
                    for (i in 0..steps) {
                        val fx = i.toFloat() / steps
                        val x = w * fx
                        val yTop = leadCenterY + amp * sin(fx * 2f * PI + waveProgress * 2.4f).toFloat()
                        if (i == 0) {
                            waveBandPath.moveTo(x, yTop)
                        } else {
                            waveBandPath.lineTo(x, yTop)
                        }
                    }
                    for (i in steps downTo 0) {
                        val fx = i.toFloat() / steps
                        val x = w * fx
                        val yBottom = trailCenterY + amp * 0.7f * sin(fx * 2f * PI + waveProgress * 2.4f + 0.6f).toFloat()
                        waveBandPath.lineTo(x, yBottom)
                    }
                    waveBandPath.close()
                    drawPath(path = waveBandPath, color = palette.splashWave)
                }
            }
        }
    }
}

@Composable
private fun V11AnimatedLogoTile(
    showStaticFinal: Boolean,
    ringT: Float,
    modifier: Modifier = Modifier
) {
    val tileShape = RoundedCornerShape(30.dp)
    val ringWedgePath = remember { Path() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(SplashIntroSpec.LOGO_TILE_SIZE_DP.dp)
            .clip(tileShape)
            .background(PureBlack)
            .testTag("one_crore_logo_canvas")
    ) {
        if (showStaticFinal) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_v11_full),
                contentDescription = "1 Crore v11 logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("splash_logo_static_final")
            )
        } else {
            // Complete logo body (tile + dot + white '1' + body/tasbeeh) drawn 100% statically from Frame 0:
            // never fades, slides, or clips in — zero empty black patch at any frame.
            Image(
                painter = painterResource(id = R.drawable.ic_logo_v11_tile),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("splash_logo_tile")
            )
            Image(
                painter = painterResource(id = R.drawable.ic_logo_v11_dot),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("splash_logo_dot")
            )
            Image(
                painter = painterResource(id = R.drawable.ic_logo_v11_one),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("splash_logo_one")
            )
            Image(
                painter = painterResource(id = R.drawable.ic_logo_v11_body_tasbeeh),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("splash_logo_body_tasbeeh")
            )

            // ONLY the white arc/ring draws clockwise from 12 o'clock to 270 degrees and stops (gap at top-left)
            val sweepDeg = SplashIntroSpec.ringSweepDegrees(ringT)
            Image(
                painter = painterResource(id = R.drawable.ic_logo_v11_ring),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (sweepDeg > 0f) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val r = hypot(cx, cy) + 2f
                            ringWedgePath.reset()
                            ringWedgePath.moveTo(cx, cy)
                            ringWedgePath.arcTo(
                                rect = Rect(
                                    left = cx - r,
                                    top = cy - r,
                                    right = cx + r,
                                    bottom = cy + r
                                ),
                                startAngleDegrees = -90f,
                                sweepAngleDegrees = sweepDeg,
                                forceMoveTo = false
                            )
                            ringWedgePath.close()
                            clipPath(ringWedgePath) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }
                    .testTag("splash_logo_ring")
            )
        }
    }
}
