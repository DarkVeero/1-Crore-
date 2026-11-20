package com.example

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.announcements.AnnouncementLaunchRequest
import com.example.announcements.AnnouncementNotificationHelper
import com.example.ui.AppNavigation
import com.example.ui.auth.extractVerificationOobCode
import com.example.ui.theme.OneCroreTheme
import com.example.widget.OneCroreWidgetProvider

class MainActivity : ComponentActivity() {
    private var pendingVerificationCode by mutableStateOf<String?>(null)
    private var pendingWidgetAction by mutableStateOf<String?>(null)
    private var pendingAnnouncementRequest by mutableStateOf<AnnouncementLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.remove()
            }
        }
        super.onCreate(savedInstanceState)
        pendingVerificationCode = extractVerificationOobCode(intent?.data)
        pendingWidgetAction = intent?.getStringExtra(OneCroreWidgetProvider.EXTRA_WIDGET_ACTION)
        pendingAnnouncementRequest = AnnouncementNotificationHelper.extractLaunchRequestFromIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            OneCroreTheme {
                AppNavigation(
                    pendingVerificationCode = pendingVerificationCode,
                    onConsumeVerificationCode = { pendingVerificationCode = null },
                    pendingWidgetAction = pendingWidgetAction,
                    onConsumeWidgetAction = { pendingWidgetAction = null },
                    pendingAnnouncementRequest = pendingAnnouncementRequest,
                    onConsumeAnnouncementRequest = { pendingAnnouncementRequest = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractVerificationOobCode(intent.data)?.let { code ->
            pendingVerificationCode = code
        }
        intent.getStringExtra(OneCroreWidgetProvider.EXTRA_WIDGET_ACTION)?.let { action ->
            pendingWidgetAction = action
        }
        AnnouncementNotificationHelper.extractLaunchRequestFromIntent(intent)?.let { request ->
            pendingAnnouncementRequest = request
        }
    }
}
