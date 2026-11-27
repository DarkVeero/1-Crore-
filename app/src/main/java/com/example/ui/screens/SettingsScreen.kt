package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.announcements.AnnouncementItem
import com.example.announcements.AnnouncementOwnerConfig
import com.example.data.DuroodRepository
import com.example.notifications.ReminderScheduler
import com.example.ui.MainUiState
import com.example.ui.auth.mapFirebaseAuthError
import com.example.ui.auth.signOutUser
import com.example.ui.components.BlackPillButton
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.ScheduleAnnouncementDialog
import com.example.ui.components.WhiteBorderPillButton
import com.example.ui.components.formatNumber
import com.example.ui.components.formatPendingSyncSummary
import com.example.ui.components.formatSyncTime
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AppThemeColors
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.appOutlinedTextFieldColors
import com.example.update.AppUpdateManager
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onUpdateDisplayName: (String) -> Unit,
    onUpdatePlan: (Int) -> Unit,
    onUpdateReminders: (Boolean, Int, Int, Int, Int) -> Unit,
    onUpdateThemeMode: (AppThemeMode) -> Unit = {},
    onSyncNow: () -> Unit,
    onClearStatusMessage: () -> Unit,
    onExportBackupJson: suspend () -> String,
    onRestoreBackupJson: (String) -> Unit,
    onDeleteAccount: (() -> Unit) -> Unit,
    onVerifyPasswordForDelete: (suspend (String) -> Result<Unit>)? = null,
    userEmail: String? = null,
    onScheduleAnnouncement: (AnnouncementItem) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var showNameDialog by remember { mutableStateOf(false) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var editingReminderSlot by remember { mutableStateOf<Int?>(null) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showScheduleAnnouncementDialog by remember { mutableStateOf(false) }
    var pendingRestoreJson by remember { mutableStateOf<String?>(null) }
    var localNotice by remember { mutableStateOf<String?>(null) }
    val effectiveEmail = remember(userEmail) {
        userEmail ?: runCatching { Firebase.auth.currentUser?.email }.getOrNull().orEmpty()
    }
    val isOwnerToolsVisible = remember(context, effectiveEmail) {
        AnnouncementOwnerConfig.isOwnerUnlocked(context, effectiveEmail)
    }
    var hasPasswordLinked by remember {
        mutableStateOf(
            Firebase.auth.currentUser?.providerData?.any {
                it.providerId == EmailAuthProvider.PROVIDER_ID
            } == true
        )
    }

    val profile = uiState.profile
    val remindersEnabled = profile?.remindersEnabled ?: true
    val r1Hour = profile?.reminder1Hour ?: 18
    val r1Min = profile?.reminder1Minute ?: 0
    val r2Hour = profile?.reminder2Hour ?: 21
    val r2Min = profile?.reminder2Minute ?: 0

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onUpdateReminders(true, r1Hour, r1Min, r2Hour, r2Min)
        } else {
            localNotice = "Notification permission denied — enable it in Android settings for reminders."
            onUpdateReminders(false, r1Hour, r1Min, r2Hour, r2Min)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = onExportBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                    localNotice = "Backup saved successfully."
                } catch (e: Exception) {
                    localNotice = "Backup export failed: ${e.localizedMessage}"
                }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader(Charsets.UTF_8).readText()
                    }
                    if (!content.isNullOrBlank()) {
                        pendingRestoreJson = content
                    } else {
                        localNotice = "Selected file is empty."
                    }
                } catch (e: Exception) {
                    localNotice = "Could not read file: ${e.localizedMessage}"
                }
            }
        }
    }

    val palette = AppThemeColors.palette

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = palette.onCanvas
            )

            val bannerText = localNotice ?: uiState.statusMessage
            LaunchedEffect(bannerText) {
                if (bannerText != null) {
                    delay(4000L)
                    localNotice = null
                    onClearStatusMessage()
                }
            }
            if (bannerText != null) {
                Surface(
                    color = palette.subtleSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.strongBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bannerText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.onCard,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp)
                        )
                        IconButton(
                            onClick = {
                                localNotice = null
                                onClearStatusMessage()
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("settings_banner_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Dismiss message",
                                tint = palette.onCard
                            )
                        }
                    }
                }
            }

            // Plan & Profile
            SettingsSectionCard(title = "PLAN & PROFILE") {
                SettingsActionRow(
                    title = "Display Name",
                    subtitle = uiState.displayName,
                    onClick = { showNameDialog = true }
                )
                HorizontalDivider(color = palette.hairline)
                val yrs = DuroodRepository.ONE_CRORE.toDouble() / (uiState.dailyTarget * 365.0)
                SettingsActionRow(
                    title = "Daily Plan",
                    subtitle = "${formatNumber(uiState.dailyTarget.toLong())} / day (≈ ${String.format(Locale.US, "%.1f", yrs)} years)",
                    onClick = { showPlanDialog = true },
                    testTag = "settings_change_plan"
                )
            }

            // Reminders
            SettingsSectionCard(title = "DAILY REMINDERS (MAX 2 / DAY)") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Incomplete Target Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.onCard
                        )
                        Text(
                            text = "Only fires if today's ${formatNumber(uiState.dailyTarget.toLong())} target is incomplete",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.onCardSubtext
                        )
                    }
                    Switch(
                        checked = remindersEnabled,
                        onCheckedChange = { enable ->
                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@Switch
                                }
                            }
                            onUpdateReminders(enable, r1Hour, r1Min, r2Hour, r2Min)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = palette.onAccent,
                            checkedTrackColor = palette.accent,
                            uncheckedThumbColor = palette.onCard,
                            uncheckedTrackColor = palette.subtleSurface,
                            uncheckedBorderColor = palette.hairline
                        )
                    )
                }
                HorizontalDivider(color = palette.hairline)
                if (remindersEnabled && !ReminderScheduler.areNotificationsAllowed(context)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    runCatching {
                                        val settingsIntent = Intent(
                                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                        ).apply {
                                            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                        context.startActivity(settingsIntent)
                                    }
                                }
                            }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("settings_notifications_permission_warning"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Notifications are blocked in Android — tap to enable reminders",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.onCard,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Allow",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider(color = palette.hairline)
                }
                SettingsActionRow(
                    title = "Reminder 1",
                    subtitle = formatTime12h(r1Hour, r1Min),
                    onClick = { editingReminderSlot = 1 }
                )
                HorizontalDivider(color = palette.hairline)
                SettingsActionRow(
                    title = "Reminder 2",
                    subtitle = formatTime12h(r2Hour, r2Min),
                    onClick = { editingReminderSlot = 2 }
                )
            }

            // Cloud Sync & Backup
            SettingsSectionCard(title = "DATA & BACKUP") {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cloud Sync",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.onCard
                            )
                            val syncStatusLine = buildString {
                                append("Last synced: ${formatSyncTime(uiState.profile?.lastSyncedAtMillis ?: 0L)}")
                                if (uiState.hasPendingSync) {
                                    append(
                                        " · ${
                                            formatPendingSyncSummary(
                                                pendingDuroodCount = uiState.pendingSyncDuroodCount,
                                                pendingDaysCount = uiState.pendingSyncDaysCount
                                            )
                                        }"
                                    )
                                }
                            }
                            Text(
                                text = syncStatusLine,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.onCardSubtext,
                                modifier = Modifier.testTag("settings_cloud_sync_status")
                            )
                        }
                        BlackPillButton(
                            text = if (uiState.isSyncing) "Syncing..." else "Sync Now",
                            enabled = !uiState.isSyncing,
                            onClick = onSyncNow,
                            modifier = Modifier.testTag("settings_sync_now_button")
                        )
                    }

                    HorizontalDivider(color = palette.hairline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        WhiteBorderPillButton(
                            text = "Export Backup",
                            onClick = {
                                exportLauncher.launch("1crore_backup_${uiState.currentDate}.json")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_export_backup_button")
                        )
                        WhiteBorderPillButton(
                            text = "Restore File",
                            onClick = {
                                restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_restore_backup_button")
                        )
                    }
                }
            }

            // Account
            SettingsSectionCard(title = "ACCOUNT") {
            Text(text = "Signed in as: $effectiveEmail", style = MaterialTheme.typography.bodyMedium, color = palette.onCardSubtext, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            if (!hasPasswordLinked) Text(text = "Google account — signed in with Google", style = MaterialTheme.typography.bodyMedium, color = palette.onCardSubtext, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                SettingsActionRow(
                    title = "Change Password",
                    subtitle = if (hasPasswordLinked) "Re-authenticate and update your password" else "Set a password for your account",
                    onClick = { showChangePasswordDialog = true },
                    testTag = "settings_change_password_row"
                )
                HorizontalDivider(color = palette.hairline)
                SettingsActionRow(
                    title = "Logout",
                    subtitle = "Sign out of this device",
                    onClick = { showLogoutDialog = true },
                    testTag = "settings_logout_row"
                )
                HorizontalDivider(color = palette.hairline)
                SettingsActionRow(
                    title = "Delete Account",
                    subtitle = "Permanently remove all cloud & phone data",
                    onClick = { showDeleteDialog = true },
                    testTag = "settings_delete_account_row"
                )
            }

            // Support & Feedback
            SettingsSectionCard(title = "SUPPORT & FEEDBACK") {
                SettingsActionRow(
                    title = "Email Support",
                    subtitle = "1croresupport@gmail.com",
                    actionLabel = "Open",
                    onClick = {
                        try {
                            val mailtoUri = Uri.parse(
                                "mailto:1croresupport@gmail.com?subject=" +
                                    Uri.encode("1 Crore App — Feedback")
                            )
                            val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                                putExtra(Intent.EXTRA_EMAIL, arrayOf("1croresupport@gmail.com"))
                                putExtra(Intent.EXTRA_SUBJECT, "1 Crore App — Feedback")
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            localNotice = "No email app found on this device."
                        }
                    },
                    testTag = "settings_email_support_row"
                )
                HorizontalDivider(color = palette.hairline)
                SettingsActionRow(
                    title = "Share this app",
                    subtitle = "1 Crore — Durood Counter & Daily Target",
                    actionLabel = "Share",
                    onClick = {
                        scope.launch {
                            AppUpdateManager.shareApp(context)
                        }
                    },
                    testTag = "settings_share_app_row"
                )
            }

            if (isOwnerToolsVisible) {
                SettingsSectionCard(title = "COMMUNITY ANNOUNCEMENTS (OWNER)") {
                    SettingsActionRow(
                        title = "Schedule / Send Announcement",
                        subtitle = "Create scheduled or targeted pop-ups & pushes",
                        actionLabel = "Create",
                        onClick = { showScheduleAnnouncementDialog = true },
                        testTag = "settings_schedule_announcement_row"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showNameDialog) {
        EditNameDialog(
            initialName = uiState.displayName,
            onDismiss = { showNameDialog = false },
            onSave = {
                onUpdateDisplayName(it)
                showNameDialog = false
            }
        )
    }

    if (showPlanDialog) {
        EditPlanDialog(
            initialDailyTarget = uiState.dailyTarget,
            onDismiss = { showPlanDialog = false },
            onSave = {
                onUpdatePlan(it)
                showPlanDialog = false
            }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showChangePasswordDialog = false },
            userEmail = effectiveEmail,
            isGoogleOnly = !hasPasswordLinked,
            onPasswordChanged = {
                hasPasswordLinked = true
                showChangePasswordDialog = false
                localNotice = "Password updated successfully."
            }
        )
    }

    val slot = editingReminderSlot
    if (slot != null) {
        ReminderTimePickerDialog(
            initialHour = if (slot == 1) r1Hour else r2Hour,
            initialMinute = if (slot == 1) r1Min else r2Min,
            onDismiss = { editingReminderSlot = null },
            onConfirm = { h, m ->
                if (slot == 1) {
                    onUpdateReminders(remindersEnabled, h, m, r2Hour, r2Min)
                } else {
                    onUpdateReminders(remindersEnabled, r1Hour, r1Min, h, m)
                }
                editingReminderSlot = null
            }
        )
    }

    if (pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = { pendingRestoreJson = null },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Restore Backup?", color = palette.onCard) },
            text = {
                Text(
                    "Backup counts will merge with your phone safely. Higher local counts are never overwritten.",
                    color = palette.onCardSubtext
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pendingRestoreJson = null }) {
                        Text("Cancel", color = palette.onCardSubtext)
                    }
                    BlackPillButton(
                        text = "Restore",
                        onClick = {
                            val json = pendingRestoreJson
                            pendingRestoreJson = null
                            if (json != null) onRestoreBackupJson(json)
                        }
                    )
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = palette.cardSurface,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Logout?", color = palette.onCard) },
            text = {
                Text(
                    "Make sure pending counts are synced first. Your local data stays safe on this phone.",
                    color = palette.onCardSubtext
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel", color = palette.onCardSubtext)
                    }
                    BlackPillButton(
                        text = "Logout",
                        onClick = {
                            showLogoutDialog = false
                            ReminderScheduler.cancelAll(context)
                            signOutUser(credentialManager, scope)
                        }
                    )
                }
            }
        )
    }

    if (showDeleteDialog) {
        DeleteAccountConfirmDialog(
            onDismiss = { showDeleteDialog = false },
        isGoogleOnly = !hasPasswordLinked,
            onVerifyPassword = onVerifyPasswordForDelete ?: { enteredPassword ->
                val user = Firebase.auth.currentUser
                val email = user?.email
                if (user == null || email.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Account session not found. Please sign in again."))
                } else {
                    try {
                        val credential = EmailAuthProvider.getCredential(email, enteredPassword)
                        user.reauthenticate(credential).await()
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(IllegalArgumentException(mapFirebaseAuthError(e)))
                    }
                }
            },
            onConfirmDelete = {
                showDeleteDialog = false
                scope.launch {
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (_: Exception) {
                    }
                    onDeleteAccount {
                        signOutUser(credentialManager, scope)
                    }
                }
            }
        )
    }

    if (showScheduleAnnouncementDialog) {
        ScheduleAnnouncementDialog(
            onDismiss = { showScheduleAnnouncementDialog = false },
            onSchedule = { item ->
                showScheduleAnnouncementDialog = false
                onScheduleAnnouncement(item)
            }
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onPasswordChanged: () -> Unit,
    userEmail: String? = null,
    isGoogleOnly: Boolean = false,
) {
    val palette = AppThemeColors.palette
    val scope = rememberCoroutineScope()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = if (isGoogleOnly) "Set Password" else "Change Password",
                style = MaterialTheme.typography.titleLarge,
                color = palette.onCard
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Signed in as: ${userEmail.orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = palette.onCardSubtext)
                if (!isGoogleOnly) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = {
                            currentPassword = it
                            dialogError = null
                        },
                        label = { Text("Current Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = appOutlinedTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_password_current_input")
                    )
                }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        dialogError = null
                    },
                    label = { Text("New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_password_new_input")
                )
                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = {
                        confirmNewPassword = it
                        dialogError = null
                    },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("change_password_confirm_input")
                )
                if (dialogError != null) {
                    Text(
                        text = dialogError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, enabled = !isSaving) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = if (isSaving) (if (isGoogleOnly) "Setting..." else "Updating...") else (if (isGoogleOnly) "Set Password" else "Update"),
                    enabled = !isSaving,
                    onClick = {
                        if (!isGoogleOnly && currentPassword.isEmpty()) {
                            dialogError = "Please enter your current password."
                            return@BlackPillButton
                        }
                        if (newPassword.length < 6) {
                            dialogError = "New password must be at least 6 characters."
                            return@BlackPillButton
                        }
                        if (newPassword != confirmNewPassword) {
                            dialogError = "New passwords do not match."
                            return@BlackPillButton
                        }
                        val user = Firebase.auth.currentUser
                        val email = user?.email
                        if (user == null || email.isNullOrBlank()) {
                            dialogError = "User session not found. Please login again."
                            return@BlackPillButton
                        }
                        isSaving = true
                        dialogError = null
                        scope.launch {
                            try {
                                if (isGoogleOnly) {
                                    val credential = EmailAuthProvider.getCredential(email, newPassword)
                                    user.linkWithCredential(credential).await()
                                    user.reload().await()
                                } else {
                                    val credential = EmailAuthProvider.getCredential(email, currentPassword)
                                    user.reauthenticate(credential).await()
                                    user.updatePassword(newPassword).await()
                                }
                                isSaving = false
                                onPasswordChanged()
                            } catch (e: Exception) {
                                isSaving = false
                                dialogError = mapFirebaseAuthError(e)
                            }
                        }
                    },
                    modifier = Modifier.testTag("change_password_submit_button")
                )
            }
        }
    )
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val palette = AppThemeColors.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
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
                content()
            }
        }
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String? = null,
    actionLabel: String = "Edit"
) {
    val palette = AppThemeColors.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.onCard
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = palette.onCardSubtext
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun EditNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val palette = AppThemeColors.palette
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Update Name", color = palette.onCard) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                label = { Text("Display Name") },
                singleLine = true,
                colors = appOutlinedTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = "Save",
                    enabled = name.isNotBlank(),
                    onClick = { onSave(name.trim()) }
                )
            }
        }
    )
}

@Composable
private fun EditPlanDialog(
    initialDailyTarget: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    val palette = AppThemeColors.palette
    var dailyInput by remember { mutableStateOf(initialDailyTarget.toString()) }
    var yearsInput by remember {
        val yrs = DuroodRepository.ONE_CRORE.toDouble() / (initialDailyTarget.coerceAtLeast(1) * 365.0)
        mutableStateOf(String.format(Locale.US, "%.1f", yrs))
    }
    val parsedDaily = dailyInput.toIntOrNull()?.coerceIn(1, DuroodRepository.ONE_CRORE.toInt()) ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Change 1 Crore Plan", color = palette.onCard) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = yearsInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            yearsInput = filtered
                            val yrs = filtered.toDoubleOrNull()
                            if (yrs != null && yrs > 0.0) {
                                val computedDaily = ceil(
                                    DuroodRepository.ONE_CRORE.toDouble() / (yrs * 365.0)
                                ).toLong().coerceIn(1L, DuroodRepository.ONE_CRORE)
                                dailyInput = computedDaily.toString()
                            }
                        }
                    },
                    label = { Text("Years to finish") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dailyInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() }.take(8)
                        dailyInput = filtered
                        val daily = filtered.toLongOrNull()
                        if (daily != null && daily > 0L) {
                            val computedYears = DuroodRepository.ONE_CRORE.toDouble() / (daily * 365.0)
                            yearsInput = String.format(Locale.US, "%.1f", computedYears)
                        }
                    },
                    label = { Text("Daily Target") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = "Save",
                    enabled = parsedDaily > 0,
                    onClick = { onSave(parsedDaily) }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val palette = AppThemeColors.palette
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Select Reminder Time", color = palette.onCard) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = palette.subtleSurface,
                        selectorColor = palette.accent,
                        containerColor = palette.cardSurface,
                        periodSelectorBorderColor = palette.hairline,
                        periodSelectorSelectedContainerColor = palette.accent,
                        periodSelectorSelectedContentColor = palette.onAccent,
                        periodSelectorUnselectedContainerColor = palette.cardSurface,
                        periodSelectorUnselectedContentColor = palette.onCard,
                        timeSelectorSelectedContainerColor = palette.accent,
                        timeSelectorSelectedContentColor = palette.onAccent,
                        timeSelectorUnselectedContainerColor = palette.subtleSurface,
                        timeSelectorUnselectedContentColor = palette.onCard,
                        clockDialSelectedContentColor = palette.onAccent,
                        clockDialUnselectedContentColor = palette.onCard
                    )
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = "Save",
                    onClick = { onConfirm(state.hour, state.minute) }
                )
            }
        }
    )
}

@Composable
fun DeleteAccountConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    onVerifyPassword: suspend (String) -> Result<Unit> = { Result.success(Unit) },
    isGoogleOnly: Boolean = false
) {
    val palette = AppThemeColors.palette
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isVerifying) onDismiss() },
        containerColor = palette.cardSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Delete Account Permanently?",
                color = palette.onCard,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "This permanently deletes all your Durood Pak counts and profile from this phone and Firestore. Export a backup first if you want to keep your records. " + if (isGoogleOnly) "Tap Delete and confirm with your Google account." else "Enter your password below to confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onCardSubtext
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        passwordError = null
                    },
                    label = { Text("Enter Password") },
                    singleLine = true,
                    isError = passwordError != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = appOutlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_account_password_input")
                )
                if (passwordError != null) {
                    Text(
                        text = passwordError.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.error,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("delete_account_password_error")
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isVerifying,
                    modifier = Modifier.testTag("delete_account_cancel_button")
                ) {
                    Text("Cancel", color = palette.onCardSubtext)
                }
                BlackPillButton(
                    text = if (isVerifying) "Verifying..." else "Delete",
                    enabled = !isVerifying,
                    onClick = {
                        if (!isGoogleOnly && password.isBlank()) {
                            passwordError = "Enter your password to confirm account deletion"
                            return@BlackPillButton
                        }
                        if (!isGoogleOnly && password.length < 6) {
                            passwordError = "Password must be at least 6 characters"
                            return@BlackPillButton
                        }
                        isVerifying = true
                        passwordError = null
                        scope.launch {
                            val result = onVerifyPassword(password)
                            isVerifying = false
                            result.fold(
                                onSuccess = {
                                    onConfirmDelete()
                                },
                                onFailure = { err ->
                                    passwordError = err.message ?: "Incorrect password. Please try again."
                                }
                            )
                        }
                    },
                    modifier = Modifier.testTag("delete_account_confirm_button")
                )
            }
        }
    )
}

private fun formatTime12h(hour: Int, minute: Int): String {
    val amPm = if (hour >= 12) "PM" else "AM"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%d:%02d %s", h12, minute, amPm)
}
