package com.example.floating

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.TextPaint
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.AppDatabase
import com.example.ui.components.formatNumber
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.BlackAndWhiteAppPalette
import com.example.ui.theme.RedAppPalette
import com.example.ui.theme.ThemePreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class FloatingCounterService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                FloatingCounterManager.stopFloatingButton(applicationContext)
                FloatingOverlayController.removeOverlayViews()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (!FloatingCounterManager.canDrawOverlays(applicationContext)) {
                    FloatingCounterManager.stopFloatingButton(applicationContext)
                    FloatingOverlayController.removeOverlayViews()
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!FloatingCounterManager.state.value.isRunning) {
                    FloatingCounterManager.restorePersistedFloatingMode(applicationContext)
                } else {
                    FloatingOverlayController.showOverlayViewsIfNeeded(applicationContext)
                }
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        if (!FloatingCounterManager.state.value.isRunning) {
            FloatingOverlayController.removeOverlayViews()
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "com.example.floating.ACTION_SHOW"
        const val ACTION_HIDE = "com.example.floating.ACTION_HIDE"
    }
}

/**
 * Process-wide main-thread controller that attaches and removes the floating counter bubble
 * (either Main Durood square or active Main Durood group pill) and bottom-center close target
 * directly on [WindowManager].
 */
internal object FloatingOverlayController {
    private val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var bubbleView: OneCroreFloatingBubbleView? = null
    private var closeTargetView: OneCroreBottomCloseTargetView? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var flashClearRunnable: Runnable? = null

    fun isOverlayShowing(): Boolean = bubbleView != null

    private fun resolveOverlayContext(context: Context): Context {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                val display = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                if (display != null) {
                    return appContext
                        .createDisplayContext(display)
                        .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                }
            }
        }
        return appContext
    }

    private fun currentScreenSizePx(wm: WindowManager, context: Context): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val bounds = wm.currentWindowMetrics.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    return bounds.width() to bounds.height()
                }
            }
        }
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        runCatching { wm.defaultDisplay?.getRealMetrics(metrics) }
        val w = if (metrics.widthPixels > 0) metrics.widthPixels else context.resources.displayMetrics.widthPixels
        val h = if (metrics.heightPixels > 0) metrics.heightPixels else context.resources.displayMetrics.heightPixels
        return w to h
    }

    fun syncOverlayPresentation(context: Context) {
        val appContext = context.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { syncOverlayPresentation(appContext) }
            return
        }
        val bubble = bubbleView ?: return
        val params = bubbleLayoutParams ?: return
        val wm = windowManager ?: return
        val currentState = FloatingCounterManager.state.value
        if (!currentState.isRunning) {
            removeOverlayViews()
            return
        }
        if (currentState.mode == FloatingTargetMode.GROUP) {
            flashClearRunnable?.let { mainHandler.removeCallbacks(it) }
            flashClearRunnable = null
        }
        bubble.updateFromState(currentState)
        val (desiredW, desiredH) = bubble.desiredWindowSizePx(currentState)
        if (params.width != desiredW || params.height != desiredH) {
            params.width = desiredW
            params.height = desiredH
            val (screenW, screenH) = currentScreenSizePx(wm, bubble.context)
            params.x = params.x.coerceIn(0, (screenW - desiredW).coerceAtLeast(0))
            params.y = params.y.coerceIn(0, (screenH - desiredH).coerceAtLeast(0))
            runCatching { wm.updateViewLayout(bubble, params) }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showOverlayViewsIfNeeded(context: Context): Boolean {
        val appContext = context.applicationContext
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showOverlayViewsIfNeeded(appContext) }
            return true
        }
        if (bubbleView != null) {
            syncOverlayPresentation(appContext)
            return true
        }
        if (!FloatingCounterManager.canDrawOverlays(appContext)) return false

        val overlayContext = resolveOverlayContext(appContext)
        val wm = (overlayContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?: (appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?: return false
        windowManager = wm

        val density = overlayContext.resources.displayMetrics.density
        val targetWindowSizePx = (84f * density).toInt()
        val bottomMarginPx = (FloatingCounterManager.CLOSE_TARGET_BOTTOM_MARGIN_DP * density).toInt()
        val hitRadiusPx = FloatingCounterManager.CLOSE_TARGET_HIT_RADIUS_DP * density
        val touchSlop = ViewConfiguration.get(overlayContext).scaledTouchSlop

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val closeTarget = OneCroreBottomCloseTargetView(overlayContext).apply {
            visibility = View.GONE
            alpha = 0f
        }
        val closeTargetParams = WindowManager.LayoutParams(
            targetWindowSizePx,
            targetWindowSizePx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = bottomMarginPx
        }

        val initialState = FloatingCounterManager.state.value
        val bubble = OneCroreFloatingBubbleView(overlayContext).apply {
            updateFromState(initialState)
        }
        val (initialBubbleW, initialBubbleH) = bubble.desiredWindowSizePx(initialState)
        val bubbleParams = WindowManager.LayoutParams(
            initialBubbleW,
            initialBubbleH,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16f * density).toInt()
            y = (220f * density).toInt()
        }

        fun resolveCloseTargetCenter(): Pair<Float, Float> {
            if (closeTarget.visibility == View.VISIBLE && closeTarget.width > 0 && closeTarget.height > 0) {
                val loc = IntArray(2)
                closeTarget.getLocationOnScreen(loc)
                val cx = loc[0] + closeTarget.width / 2f
                val cy = loc[1] + closeTarget.height / 2f
                if (cx > 0f && cy > 0f) {
                    return cx to cy
                }
            }
            val (screenW, screenH) = currentScreenSizePx(wm, overlayContext)
            val fallbackCx = screenW / 2f
            val fallbackCy = screenH - bottomMarginPx - targetWindowSizePx / 2f
            return fallbackCx to fallbackCy
        }

        var downRawX = 0f
        var downRawY = 0f
        var startWinX = 0
        var startWinY = 0
        var wasDragged = false
        var wasHoveringClose = false

        bubble.setOnTouchListener { _, event ->
            val currentBubbleW = bubble.width.takeIf { it > 0 } ?: bubbleParams.width
            val currentBubbleH = bubble.height.takeIf { it > 0 } ?: bubbleParams.height
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startWinX = bubbleParams.x
                    startWinY = bubbleParams.y
                    wasDragged = false
                    wasHoveringClose = false
                    bubble.setPressedScale(true)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!wasDragged && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        wasDragged = true
                        bubble.setPressedScale(false)
                        FloatingCounterManager.onDragStarted()
                        closeTarget.setHovered(hovered = false, animate = false)
                        closeTarget.alpha = 0f
                        closeTarget.visibility = View.VISIBLE
                        closeTarget.animate().alpha(1f).setDuration(120L).start()
                    }
                    if (wasDragged) {
                        val (screenW, screenH) = currentScreenSizePx(wm, overlayContext)
                        val maxX = (screenW - currentBubbleW).coerceAtLeast(0)
                        val maxY = (screenH - currentBubbleH).coerceAtLeast(0)
                        bubbleParams.x = (startWinX + dx).toInt().coerceIn(0, maxX)
                        bubbleParams.y = (startWinY + dy).toInt().coerceIn(0, maxY)
                        runCatching { wm.updateViewLayout(bubble, bubbleParams) }

                        val bubbleCenterX = bubbleParams.x + currentBubbleW / 2f
                        val bubbleCenterY = bubbleParams.y + currentBubbleH / 2f
                        val (targetCx, targetCy) = resolveCloseTargetCenter()
                        val hoveringNow = FloatingCounterManager.isBubbleOverCloseTarget(
                            bubbleCenterX = bubbleCenterX,
                            bubbleCenterY = bubbleCenterY,
                            targetCenterX = targetCx,
                            targetCenterY = targetCy,
                            hitRadiusPx = hitRadiusPx
                        )
                        if (hoveringNow != wasHoveringClose) {
                            wasHoveringClose = hoveringNow
                            FloatingCounterManager.onDragTargetHoverChanged(hoveringNow)
                            closeTarget.setHovered(hovered = hoveringNow, animate = true)
                            if (hoveringNow) {
                                vibrateTick(appContext, strong = true)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    bubble.setPressedScale(false)
                    if (!wasDragged) {
                        handleBubbleTap(appContext, bubble)
                    } else {
                        val bubbleCenterX = bubbleParams.x + currentBubbleW / 2f
                        val bubbleCenterY = bubbleParams.y + currentBubbleH / 2f
                        val (targetCx, targetCy) = resolveCloseTargetCenter()
                        val droppedOnClose = FloatingCounterManager.isBubbleOverCloseTarget(
                            bubbleCenterX = bubbleCenterX,
                            bubbleCenterY = bubbleCenterY,
                            targetCenterX = targetCx,
                            targetCenterY = targetCy,
                            hitRadiusPx = hitRadiusPx
                        )
                        if (droppedOnClose) {
                            vibrateTick(appContext, strong = true)
                            FloatingCounterManager.onDragEnded(
                                context = appContext,
                                droppedOnClose = true
                            )
                            removeOverlayViews()
                        } else {
                            closeTarget.animate().cancel()
                            closeTarget.setHovered(hovered = false, animate = false)
                            closeTarget.visibility = View.GONE
                            FloatingCounterManager.onDragEnded(
                                context = appContext,
                                droppedOnClose = false
                            )
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    bubble.setPressedScale(false)
                    if (wasDragged) {
                        closeTarget.animate().cancel()
                        closeTarget.setHovered(hovered = false, animate = false)
                        closeTarget.visibility = View.GONE
                        FloatingCounterManager.onDragEnded(
                            context = appContext,
                            droppedOnClose = false
                        )
                    }
                    true
                }

                else -> false
            }
        }

        return try {
            wm.addView(closeTarget, closeTargetParams)
            wm.addView(bubble, bubbleParams)
            closeTargetView = closeTarget
            bubbleView = bubble
            bubbleLayoutParams = bubbleParams
            true
        } catch (_: Exception) {
            runCatching { wm.removeView(closeTarget) }
            runCatching { wm.removeView(bubble) }
            closeTargetView = null
            bubbleView = null
            bubbleLayoutParams = null
            false
        }
    }

    private fun handleBubbleTap(appContext: Context, bubble: OneCroreFloatingBubbleView) {
        vibrateTick(appContext, strong = false)
        flashClearRunnable?.let { mainHandler.removeCallbacks(it) }

        overlayScope.launch {
            val (resultValue, _) = FloatingCounterManager.onFloatingButtonTapped(appContext)
            val latestState = FloatingCounterManager.state.value
            if (!latestState.isRunning) {
                removeOverlayViews()
                return@launch
            }
            if (latestState.mode == FloatingTargetMode.GROUP) {
                bubble.updateFromState(latestState)
                bubble.triggerTapPulse()
                syncOverlayPresentation(appContext)
            } else {
                bubble.showFlashingNumber(resultValue)
                val clearRunnable = Runnable {
                    FloatingCounterManager.onFlashFinished(resultValue)
                    bubble.fadeBackToLogo()
                }
                flashClearRunnable = clearRunnable
                mainHandler.postDelayed(clearRunnable, FloatingCounterManager.FLASH_DURATION_MILLIS)
            }
        }
    }

    fun removeOverlayViews() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { removeOverlayViews() }
            return
        }
        flashClearRunnable?.let { mainHandler.removeCallbacks(it) }
        flashClearRunnable = null
        val bubble = bubbleView
        val target = closeTargetView
        bubbleView = null
        closeTargetView = null
        bubbleLayoutParams = null
        if (bubble != null) {
            runCatching { windowManager?.removeView(bubble) }
        }
        if (target != null) {
            runCatching { windowManager?.removeView(target) }
        }
    }

    private fun vibrateTick(context: Context, strong: Boolean) {
        try {
            val durationMs = if (strong) 40L else 16L
            val amplitude = if (strong) 170 else 65
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, amplitude)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
        }
    }
}

/**
 * Custom View rendering either:
 * - Main Durood mode: compact 54dp 1 Crore app logo floating button flashing the session count.
 * - Group Durood mode: compact monochrome pill displaying the group's live total with a subtle tap pulse.
 */
internal class OneCroreFloatingBubbleView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val bgRect = RectF()
    private val logoDestRect = Rect()
    private val clipPath = Path()

    private val fillBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val borderWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1.5f,
            resources.displayMetrics
        )
    }
    private val logoBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val pillGroupNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(195, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            9.5f,
            resources.displayMetrics
        )
    }
    private val pillTotalPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            14f,
            resources.displayMetrics
        )
    }

    private val staticLogoBitmap: Bitmap? by lazy {
        runCatching {
            BitmapFactory.decodeResource(resources, R.drawable.ic_one_crore_foreground)
        }.getOrNull()
    }

    private var currentMode: FloatingTargetMode = FloatingTargetMode.MAIN
    private var activeGroupName: String = ""
    private var activeGroupTotal: Long = 0L
    private var displayedNumber: Long? = null
    private var logoAlphaFraction: Float = 1f
    private var numberAlphaFraction: Float = 0f
    private var fadeAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    fun updateFromState(state: FloatingBubbleUiState) {
        val modeChanged = currentMode != state.mode
        currentMode = state.mode
        activeGroupName = state.activeGroupName.orEmpty()
        activeGroupTotal = state.activeGroupTotal.coerceAtLeast(0L)
        if (currentMode == FloatingTargetMode.GROUP) {
            fadeAnimator?.cancel()
            displayedNumber = null
            logoAlphaFraction = 0f
            numberAlphaFraction = 0f
        } else if (modeChanged) {
            fadeAnimator?.cancel()
            displayedNumber = null
            logoAlphaFraction = 1f
            numberAlphaFraction = 0f
        }
        invalidate()
    }

    fun desiredWindowSizePx(state: FloatingBubbleUiState): Pair<Int, Int> {
        if (state.mode == FloatingTargetMode.MAIN) {
            val squarePx = (54f * density).roundToInt()
            return squarePx to squarePx
        }
        val totalText = formatNumber(state.activeGroupTotal.coerceAtLeast(0L))
        val nameText = state.activeGroupName.orEmpty().trim()
        val maxContentW = (116f * density)
        val totalW = pillTotalPaint.measureText(totalText)
        val nameW = if (nameText.isNotEmpty()) {
            minOf(pillGroupNamePaint.measureText(nameText), maxContentW - 24f * density)
        } else {
            0f
        }
        val minPillW = (72f * density).roundToInt()
        val maxPillW = (140f * density).roundToInt()
        val rawW = (max(totalW, nameW) + 28f * density).roundToInt()
        val pillW = rawW.coerceIn(minPillW, maxPillW)
        val pillH = (46f * density).roundToInt()
        return pillW to pillH
    }

    fun setPressedScale(pressed: Boolean) {
        val target = if (pressed) 0.93f else 1f
        animate().scaleX(target).scaleY(target).setDuration(90L).start()
    }

    fun triggerTapPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(1f, 0.93f, 1f).apply {
            duration = 160L
            addUpdateListener { anim ->
                val s = anim.animatedValue as Float
                scaleX = s
                scaleY = s
            }
            start()
        }
    }

    fun showFlashingNumber(number: Long) {
        fadeAnimator?.cancel()
        displayedNumber = number
        numberAlphaFraction = 1f
        logoAlphaFraction = 0f
        invalidate()
    }

    fun fadeBackToLogo() {
        fadeAnimator?.cancel()
        fadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            addUpdateListener { anim ->
                val f = anim.animatedValue as Float
                logoAlphaFraction = f
                numberAlphaFraction = 1f - f
                if (f >= 1f) {
                    displayedNumber = null
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val inset = borderWhitePaint.strokeWidth
        bgRect.set(inset, inset, w - inset, h - inset)

        if (currentMode == FloatingTargetMode.GROUP) {
            val pillCorner = (h - 2f * inset) / 2f
            canvas.drawRoundRect(bgRect, pillCorner, pillCorner, fillBlackPaint)
            canvas.drawRoundRect(bgRect, pillCorner, pillCorner, borderWhitePaint)

            val totalText = formatNumber(activeGroupTotal)
            val availableTextW = (w - 20f * density).coerceAtLeast(16f)
            val cleanGroupName = activeGroupName.trim()
            if (cleanGroupName.isNotEmpty()) {
                val ellipsizedName = TextUtils.ellipsize(
                    cleanGroupName,
                    pillGroupNamePaint,
                    availableTextW,
                    TextUtils.TruncateAt.END
                ).toString()
                val nameFm = pillGroupNamePaint.fontMetrics
                val totalFm = pillTotalPaint.fontMetrics
                val nameH = nameFm.descent - nameFm.ascent
                val totalH = totalFm.descent - totalFm.ascent
                val gap = 1f * density
                val blockTop = (h - (nameH + gap + totalH)) / 2f
                canvas.drawText(ellipsizedName, w / 2f, blockTop - nameFm.ascent, pillGroupNamePaint)
                canvas.drawText(totalText, w / 2f, blockTop + nameH + gap - totalFm.ascent, pillTotalPaint)
            } else {
                val fm = pillTotalPaint.fontMetrics
                val centerY = h / 2f - (fm.ascent + fm.descent) / 2f
                canvas.drawText(totalText, w / 2f, centerY, pillTotalPaint)
            }
            return
        }

        val corner = w * 0.28f

        canvas.drawRoundRect(bgRect, corner, corner, fillBlackPaint)

        val bmp = staticLogoBitmap
        if (bmp != null && logoAlphaFraction > 0f) {
            val alphaInt = (logoAlphaFraction * 255f).toInt().coerceIn(0, 255)
            logoBitmapPaint.alpha = alphaInt
            val pad = (inset + w * 0.055f).roundToInt()
            logoDestRect.set(pad, pad, (w - pad).roundToInt(), (h - pad).roundToInt())
            val saveCount = canvas.save()
            clipPath.reset()
            clipPath.addRoundRect(bgRect, corner, corner, Path.Direction.CW)
            canvas.clipPath(clipPath)
            canvas.drawBitmap(bmp, null, logoDestRect, logoBitmapPaint)
            canvas.restoreToCount(saveCount)
        }

        // Thin white outer border preserved around the static v11 tile
        canvas.drawRoundRect(bgRect, corner, corner, borderWhitePaint)

        val number = displayedNumber
        if (number != null && numberAlphaFraction > 0f) {
            val text = number.toString()
            numberPaint.alpha = (numberAlphaFraction * 255f).toInt().coerceIn(0, 255)
            val sizeFactor = when {
                text.length <= 2 -> 0.38f
                text.length == 3 -> 0.30f
                else -> 0.24f
            }
            numberPaint.textSize = w * sizeFactor
            val fm = numberPaint.fontMetrics
            val textCenterY = h / 2f - (fm.ascent + fm.descent) / 2f
            canvas.drawText(text, w / 2f, textCenterY, numberPaint)
        }
    }
}

/**
 * Bottom-center circular 'X' target shown while dragging the floating button.
 * Resting state: 56dp white circle with black border & black X.
 * Hovered state: enlarges smoothly to 68dp and inverts to solid black fill with white ring & white X.
 */
internal class OneCroreBottomCloseTargetView(context: Context) : View(context) {
    private val restingDiameterPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        FloatingCounterManager.CLOSE_TARGET_RESTING_SIZE_DP,
        resources.displayMetrics
    )
    private val hoveredDiameterPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        FloatingCounterManager.CLOSE_TARGET_HOVERED_SIZE_DP,
        resources.displayMetrics
    )

    private var hoverFraction: Float = 0f
    private var hoverAnimator: ValueAnimator? = null
    private var isHoveredState: Boolean = false

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2f,
            resources.displayMetrics
        )
    }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            2.4f,
            resources.displayMetrics
        )
    }

    fun setHovered(hovered: Boolean, animate: Boolean) {
        if (isHoveredState == hovered && animate) return
        isHoveredState = hovered
        val target = if (hovered) 1f else 0f
        hoverAnimator?.cancel()
        if (!animate) {
            hoverFraction = target
            invalidate()
            return
        }
        hoverAnimator = ValueAnimator.ofFloat(hoverFraction, target).apply {
            duration = 140L
            addUpdateListener { anim ->
                hoverFraction = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val currentDiameter = restingDiameterPx + (hoveredDiameterPx - restingDiameterPx) * hoverFraction
        val radius = ((currentDiameter / 2f) - borderPaint.strokeWidth).coerceAtLeast(0f)
        if (radius <= 0f) return

        val inverted = hoverFraction >= 0.5f
        bgPaint.color = if (inverted) Color.BLACK else Color.WHITE
        borderPaint.color = if (inverted) Color.WHITE else Color.BLACK
        xPaint.color = if (inverted) Color.WHITE else Color.BLACK

        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, borderPaint)

        val arm = radius * 0.36f
        canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, xPaint)
        canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, xPaint)
    }
}

/**
 * Compose representation of the Floating Counter Button for in-app preview and Robolectric tests.
 * Uses the exact same FloatingCounterManager state machine:
 * - MAIN mode: 54dp rounded-square logo button; tapping increments Main Durood +1 and flashes session count.
 * - GROUP mode: compact pill displaying the active Main Durood group's live total with subtle tap pulse;
 *   tapping adds +1 exclusively to that group.
 * - Dragging reveals the bottom-center X close target; dropping on X closes immediately.
 */
@Composable
fun FloatingCounterBubbleComposable(
    db: AppDatabase? = null,
    userIdOverride: String? = null,
    flashDurationMillis: Long = FloatingCounterManager.FLASH_DURATION_MILLIS,
    onClosed: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by FloatingCounterManager.state.collectAsState()
    val themeMode by ThemePreferenceStore.themeModeFlow(context).collectAsState()
    val palette = if (themeMode == AppThemeMode.RED) RedAppPalette else BlackAndWhiteAppPalette

    if (!state.isRunning) return

    val flashingNum = state.flashingNumber
    LaunchedEffect(flashingNum) {
        if (flashingNum != null) {
            delay(flashDurationMillis)
            FloatingCounterManager.onFlashFinished(flashingNum)
        }
    }

    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(state.tapPulseTick) {
        if (state.tapPulseTick > 0L && state.mode == FloatingTargetMode.GROUP) {
            pulseScale.snapTo(1f)
            pulseScale.animateTo(0.93f, tween(durationMillis = 70, easing = FastOutSlowInEasing))
            pulseScale.animateTo(1f, tween(durationMillis = 110, easing = FastOutSlowInEasing))
        }
    }

    val density = LocalDensity.current
    val defaultMainSizePx = with(density) { 54.dp.toPx() }
    val restingSizeDp = FloatingCounterManager.CLOSE_TARGET_RESTING_SIZE_DP.dp
    val hoveredSizeDp = FloatingCounterManager.CLOSE_TARGET_HOVERED_SIZE_DP.dp
    val bottomMarginDp = FloatingCounterManager.CLOSE_TARGET_BOTTOM_MARGIN_DP.dp
    val hitRadiusPx = with(density) { FloatingCounterManager.CLOSE_TARGET_HIT_RADIUS_DP.dp.toPx() }
    val initialX = with(density) { 16.dp.toPx() }
    val initialY = with(density) { 120.dp.toPx() }

    var bubbleOffsetX by remember { mutableFloatStateOf(initialX) }
    var bubbleOffsetY by remember { mutableFloatStateOf(initialY) }
    var measuredBubbleW by remember { mutableFloatStateOf(defaultMainSizePx) }
    var measuredBubbleH by remember { mutableFloatStateOf(defaultMainSizePx) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("floating_counter_root")
    ) {
        val stageWidthPx = with(density) { maxWidth.toPx() }
        val stageHeightPx = with(density) { maxHeight.toPx() }
        val bottomMarginPx = with(density) { bottomMarginDp.toPx() }
        val restingSizePx = with(density) { restingSizeDp.toPx() }

        val targetCenterX = stageWidthPx / 2f
        val targetCenterY = (stageHeightPx - bottomMarginPx - restingSizePx / 2f).coerceAtLeast(0f)

        val isGroupMode = state.mode == FloatingTargetMode.GROUP
        val bubbleShape = if (isGroupMode) CircleShape else RoundedCornerShape(15.dp)
        val sizeModifier = if (isGroupMode) {
            Modifier
                .defaultMinSize(minWidth = 72.dp)
                .widthIn(max = 148.dp)
                .height(46.dp)
        } else {
            Modifier.size(54.dp)
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()) }
                .graphicsLayer {
                    scaleX = pulseScale.value
                    scaleY = pulseScale.value
                }
                .then(sizeModifier)
                .onSizeChanged { size ->
                    if (size.width > 0) measuredBubbleW = size.width.toFloat()
                    if (size.height > 0) measuredBubbleH = size.height.toFloat()
                }
                .clip(bubbleShape)
                .background(palette.accent)
                .border(1.5.dp, palette.onAccent, bubbleShape)
                .pointerInput(stageWidthPx, stageHeightPx, state.mode, state.activeGroupId) {
                    detectTapGestures(
                        onTap = {
                            scope.launch {
                                FloatingCounterManager.onFloatingButtonTapped(
                                    context = context,
                                    db = db ?: AppDatabase.getInstance(context),
                                    userIdOverride = userIdOverride
                                )
                            }
                        }
                    )
                }
                .pointerInput(stageWidthPx, stageHeightPx, measuredBubbleW, measuredBubbleH) {
                    detectDragGestures(
                        onDragStart = {
                            FloatingCounterManager.onDragStarted()
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val maxBubbleX = (stageWidthPx - measuredBubbleW).coerceAtLeast(0f)
                            val maxBubbleY = (stageHeightPx - measuredBubbleH).coerceAtLeast(0f)
                            bubbleOffsetX = (bubbleOffsetX + dragAmount.x).coerceIn(0f, maxBubbleX)
                            bubbleOffsetY = (bubbleOffsetY + dragAmount.y).coerceIn(0f, maxBubbleY)

                            val hoveringNow = FloatingCounterManager.isBubbleOverCloseTarget(
                                bubbleCenterX = bubbleOffsetX + measuredBubbleW / 2f,
                                bubbleCenterY = bubbleOffsetY + measuredBubbleH / 2f,
                                targetCenterX = targetCenterX,
                                targetCenterY = targetCenterY,
                                hitRadiusPx = hitRadiusPx
                            )
                            FloatingCounterManager.onDragTargetHoverChanged(hoveringNow)
                        },
                        onDragEnd = {
                            val droppedOnClose = FloatingCounterManager.isBubbleOverCloseTarget(
                                bubbleCenterX = bubbleOffsetX + measuredBubbleW / 2f,
                                bubbleCenterY = bubbleOffsetY + measuredBubbleH / 2f,
                                targetCenterX = targetCenterX,
                                targetCenterY = targetCenterY,
                                hitRadiusPx = hitRadiusPx
                            )
                            if (FloatingCounterManager.onDragEnded(context, droppedOnClose)) {
                                onClosed()
                            }
                        },
                        onDragCancel = {
                            FloatingCounterManager.onDragEnded(context, droppedOnClose = false)
                        }
                    )
                }
                .semantics {
                    contentDescription = if (isGroupMode) {
                        val name = state.activeGroupName ?: "Group"
                        "$name floating button total ${formatNumber(state.activeGroupTotal)}"
                    } else {
                        "1 Crore floating button"
                    }
                }
                .testTag("floating_counter_button")
        ) {
            if (isGroupMode) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .testTag("group_floating_pill")
                ) {
                    val groupLabel = state.activeGroupName.orEmpty().trim()
                    if (groupLabel.isNotEmpty()) {
                        Text(
                            text = groupLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            ),
                            color = palette.onAccent.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("group_floating_pill_name")
                        )
                    }
                    Text(
                        text = formatNumber(state.activeGroupTotal),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = palette.onAccent,
                        maxLines = 1,
                        modifier = Modifier.testTag("group_floating_pill_total")
                    )
                }
            } else if (flashingNum != null) {
                Text(
                    text = flashingNum.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (flashingNum < 100L) 18.sp else 14.sp
                    ),
                    color = palette.onAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("floating_counter_flash_number")
                )
            } else {
                SmallOneCroreLogoGlyph(
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("floating_counter_logo")
                )
            }
        }

        if (state.isDragging) {
            val targetSize by animateDpAsState(
                targetValue = if (state.isOverCloseTarget) hoveredSizeDp else restingSizeDp,
                label = "close_target_size"
            )
            val fillColor = if (state.isOverCloseTarget) palette.accent else palette.cardSurface
            val borderColor = if (state.isOverCloseTarget) palette.onAccent else palette.onCard
            val iconTint = if (state.isOverCloseTarget) palette.onAccent else palette.onCard

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomMarginDp)
                    .size(targetSize)
                    .clip(CircleShape)
                    .background(fillColor)
                    .border(2.dp, borderColor, CircleShape)
                    .semantics {
                        contentDescription = if (state.isOverCloseTarget) {
                            "Release to close floating button"
                        } else {
                            "Drag here to close floating button"
                        }
                    }
                    .testTag("floating_counter_close_target")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

internal fun resolveFloatingLogoDrawableResId(): Int = R.drawable.ic_one_crore_foreground

@Composable
fun SmallOneCroreLogoGlyph(
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = resolveFloatingLogoDrawableResId()),
        contentDescription = "1 Crore v11 logo",
        contentScale = ContentScale.Fit,
        modifier = modifier.clip(RoundedCornerShape(22))
    )
}
