package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.floating.FloatingCounterManager
import com.example.ui.components.formatNumber

class OneCroreWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val (cachedToday, cachedTotal) = WidgetAndFloatingBridge.getCachedCounts(context)
        for (appWidgetId in appWidgetIds) {
            val views = buildRemoteViews(context, cachedToday, cachedTotal)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        // Also refresh asynchronously from the local Room database to guarantee exact live values
        WidgetAndFloatingBridge.refreshWidgetsFromDatabaseAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_ENABLE_FLOATING_BROADCAST -> {
                handleFloatingButtonFromWidget(context)
            }
            ACTION_WIDGET_REFRESH -> {
                WidgetAndFloatingBridge.refreshWidgetsFromDatabaseAsync(context)
            }
        }
    }

    companion object {
        private var lastPushedToday: Long? = null
        private var lastPushedTotal: Long? = null
        private var lastPushedFloating: Boolean? = null
        const val EXTRA_WIDGET_ACTION = "extra_one_crore_widget_action"
        const val ACTION_WIDGET_ADD_CUSTOM = "widget_action_add_custom"
        const val ACTION_WIDGET_OPEN_COUNTER = "widget_action_open_counter"
        const val ACTION_WIDGET_ENABLE_FLOATING = "widget_action_enable_floating"

        const val ACTION_WIDGET_ENABLE_FLOATING_BROADCAST =
            "com.example.widget.ACTION_WIDGET_ENABLE_FLOATING_BROADCAST"
        const val ACTION_WIDGET_REFRESH =
            "com.example.widget.ACTION_WIDGET_REFRESH"

        private const val REQ_ADD_CUSTOM = 3101
        private const val REQ_OPEN_COUNTER = 3102
        private const val REQ_FLOATING = 3103
        private const val REQ_ROOT_OPEN = 3104

        fun handleFloatingButtonFromWidget(context: Context): Boolean {
            val appContext = context.applicationContext
            // R3-P2-16: the widget toggle is the MAIN floating button, but a
            // GROUP pill may be the one running. Toggling while any pill is
            // running must stop it — the old isMainRunning check missed the
            // group pill and startFloatingButton then replaced it with Main.
            if (FloatingCounterManager.isRunning(appContext)) {
                FloatingCounterManager.stopFloatingButton(appContext)
                return false
            }
            // R3-P2-27: never start the pill before any sign-in — taps made
            // under the fallback id were orphaned on sign-in. Open the app
            // instead so the user can sign in first.
            if (!WidgetAndFloatingBridge.hasRealUserId(appContext)) {
                val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                runCatching { appContext.startActivity(launchIntent) }
                updateAllWidgetsState(appContext)
                return false
            }
            return if (FloatingCounterManager.canDrawOverlays(appContext)) {
                FloatingCounterManager.startFloatingButton(appContext)
            } else {
                FloatingCounterManager.setPendingStartAfterPermission(appContext, true)
                val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_WIDGET_ACTION, ACTION_WIDGET_ENABLE_FLOATING)
                }
                val startedMain = runCatching {
                    appContext.startActivity(launchIntent)
                    true
                }.getOrDefault(false)
                if (!startedMain) {
                    FloatingCounterManager.openOverlayPermissionSettings(appContext)
                }
                updateAllWidgetsState(appContext)
                false
            }
        }

        fun buildRemoteViews(
            context: Context,
            todayCount: Long,
            totalCount: Long,
            // R3-P2-16: the widget shows the ON state whenever ANY pill is
            // running, not only Main — a running group pill used to read OFF.
            isFloatingRunning: Boolean = FloatingCounterManager.isRunning(context)
        ): RemoteViews {
            val appContext = context.applicationContext
            val views = RemoteViews(appContext.packageName, R.layout.widget_one_crore)

            views.setTextViewText(R.id.widget_today_count, formatNumber(todayCount.coerceAtLeast(0L)))
            views.setTextViewText(R.id.widget_total_count, formatNumber(totalCount.coerceAtLeast(0L)))

            // 1. "Add Custom" -> opens the Counter page with the Add Custom Amount dialog open
            val addCustomIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_WIDGET_ACTION, ACTION_WIDGET_ADD_CUSTOM)
            }
            val addCustomPendingIntent = PendingIntent.getActivity(
                appContext,
                REQ_ADD_CUSTOM,
                addCustomIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_add_custom, addCustomPendingIntent)

            // 2. "Open Counter" -> opens the Counter page directly
            val openCounterIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_WIDGET_ACTION, ACTION_WIDGET_OPEN_COUNTER)
            }
            val openCounterPendingIntent = PendingIntent.getActivity(
                appContext,
                REQ_OPEN_COUNTER,
                openCounterIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_open_counter, openCounterPendingIntent)

            // 3. "Floating Button" / "Floating ON" -> toggles the floating button ON/OFF
            if (isFloatingRunning) {
                views.setInt(
                    R.id.widget_btn_floating,
                    "setBackgroundResource",
                    R.drawable.bg_widget_black_pill
                )
                views.setTextColor(R.id.widget_btn_floating, Color.WHITE)
                views.setTextViewText(R.id.widget_btn_floating, "Floating ON")
            } else {
                views.setInt(
                    R.id.widget_btn_floating,
                    "setBackgroundResource",
                    R.drawable.bg_widget_white_pill
                )
                views.setTextColor(R.id.widget_btn_floating, Color.BLACK)
                views.setTextViewText(R.id.widget_btn_floating, "Floating Button")
            }
            val floatingBroadcastIntent = Intent(appContext, OneCroreWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_ENABLE_FLOATING_BROADCAST
            }
            val floatingPendingIntent = PendingIntent.getBroadcast(
                appContext,
                REQ_FLOATING,
                floatingBroadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_floating, floatingPendingIntent)

            // Tapping the counts header also opens the Counter screen
            views.setOnClickPendingIntent(R.id.widget_root, openCounterPendingIntent)

            return views
        }

        fun updateAllWidgetsState(context: Context) {
            val appContext = context.applicationContext
            val (cachedToday, cachedTotal) = WidgetAndFloatingBridge.getCachedCounts(appContext)
            updateAllWidgetsWithCounts(appContext, cachedToday, cachedTotal)
        }

        fun updateAllWidgetsWithCounts(
            context: Context,
            todayCount: Long,
            totalCount: Long
        ) {
            val appContext = context.applicationContext
            WidgetAndFloatingBridge.cacheLatestCounts(appContext, todayCount, totalCount)
            // R3-P2-16: same as buildRemoteViews — ON whenever any pill runs.
            val isFloatingRunning = FloatingCounterManager.isRunning(appContext)
        if (lastPushedToday == todayCount && lastPushedTotal == totalCount && lastPushedFloating == isFloatingRunning) return
        lastPushedToday = todayCount
        lastPushedTotal = totalCount
        lastPushedFloating = isFloatingRunning
            runCatching {
                val manager = AppWidgetManager.getInstance(appContext) ?: return
                val component = ComponentName(appContext, OneCroreWidgetProvider::class.java)
                val views = buildRemoteViews(
                    context = appContext,
                    todayCount = todayCount,
                    totalCount = totalCount,
                    isFloatingRunning = isFloatingRunning
                )
                val ids = manager.getAppWidgetIds(component)
                if (ids != null && ids.isNotEmpty()) {
                    for (id in ids) {
                        manager.updateAppWidget(id, views)
                    }
                } else {
                    manager.updateAppWidget(component, views)
                }
            }
        }
    }
}
