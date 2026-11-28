package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.announcements.AnnouncementLaunchRequest
import com.example.announcements.AnnouncementNotificationHelper
import com.example.ui.AppNavigation
import com.example.ui.auth.extractVerificationOobCode
import com.example.update.UpdateDownloadCompleteReceiver
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.OneCroreTheme
import com.example.ui.theme.ThemePreferenceStore
import com.example.widget.OneCroreWidgetProvider

class MainActivity : ComponentActivity() {
    private var pendingVerificationCode by mutableStateOf<String?>(null)
    private var pendingWidgetAction by mutableStateOf<String?>(null)
    private var pendingAnnouncementRequest by mutableStateOf<AnnouncementLaunchRequest?>(null)
    private var pendingOpenUpdateInstall by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        super.onCreate(savedInstanceState)
        val initialTheme = ThemePreferenceStore.reloadFromDevicePrefs(this)
        pendingVerificationCode = extractVerificationOobCode(intent?.data)
        pendingWidgetAction = intent?.getStringExtra(OneCroreWidgetProvider.EXTRA_WIDGET_ACTION)
        pendingAnnouncementRequest = AnnouncementNotificationHelper.extractLaunchRequestFromIntent(intent)
        pendingOpenUpdateInstall = intent?.getBooleanExtra(
            UpdateDownloadCompleteReceiver.EXTRA_OPEN_UPDATE_INSTALL, false
        ) == true
        val initialBarStyle = if (initialTheme == AppThemeMode.RED) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(
            statusBarStyle = initialBarStyle,
            navigationBarStyle = initialBarStyle
        )
        setContent {
            val activeTheme by ThemePreferenceStore.themeModeFlow(this).collectAsStateWithLifecycle()
            LaunchedEffect(activeTheme) {
                val barStyle = if (activeTheme == AppThemeMode.RED) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle
                )
            }
            OneCroreTheme(themeMode = activeTheme) {
                AppNavigation(
                    pendingVerificationCode = pendingVerificationCode,
                    onConsumeVerificationCode = { pendingVerificationCode = null },
                    pendingWidgetAction = pendingWidgetAction,
                    onConsumeWidgetAction = { pendingWidgetAction = null },
                    pendingAnnouncementRequest = pendingAnnouncementRequest,
                    onConsumeAnnouncementRequest = { pendingAnnouncementRequest = null },
                    pendingOpenUpdateInstall = pendingOpenUpdateInstall,
                    onConsumeOpenUpdateInstall = { pendingOpenUpdateInstall = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingVerificationCode = extractVerificationOobCode(intent.data)
        intent.getStringExtra(OneCroreWidgetProvider.EXTRA_WIDGET_ACTION)?.let { action ->
            pendingWidgetAction = action
        }
        AnnouncementNotificationHelper.extractLaunchRequestFromIntent(intent)?.let { request ->
            pendingAnnouncementRequest = request
        }
        if (intent.getBooleanExtra(
                UpdateDownloadCompleteReceiver.EXTRA_OPEN_UPDATE_INSTALL, false
            )
        ) {
            pendingOpenUpdateInstall = true
        }
    }
}
