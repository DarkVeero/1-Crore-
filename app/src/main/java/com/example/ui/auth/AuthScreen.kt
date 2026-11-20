package com.example.ui.auth

import android.util.Patterns
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sync.NetworkMonitor
import com.example.ui.components.BlackPillButton
import com.example.ui.components.FaintGridBackground
import com.example.ui.components.WhiteBorderPillButton
import com.example.ui.components.formatNumber
import com.example.ui.components.rememberReduceMotion
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HairlineGrey
import com.example.ui.theme.OffWhite
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SubtextGrey
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToLong
import kotlin.math.sin

private enum class AuthMode {
    LOGIN,
    SIGNUP
}

internal const val GOAL_TARGET_COUNT = 10_000_000L
internal const val GOAL_COUNT_UP_DURATION_MS = 4_000

internal fun computeEasedGoalCount(rawFraction: Float): Long {
    val clamped = rawFraction.coerceIn(0f, 1f)
    return if (clamped >= 1f) {
        GOAL_TARGET_COUNT
    } else {
        (clamped.toDouble() * GOAL_TARGET_COUNT.toDouble())
            .roundToLong()
            .coerceIn(0L, GOAL_TARGET_COUNT)
    }
}

internal fun formatGoalCount(count: Long): String = formatNumber(count.coerceIn(0L, GOAL_TARGET_COUNT))

internal data class AuthFieldErrors(
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null
) {
    val hasAny: Boolean
        get() = nameError != null || emailError != null || passwordError != null || confirmPasswordError != null
}

private val FALLBACK_EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

internal fun isValidAuthEmail(email: String): Boolean {
    val trimmed = email.trim()
    if (trimmed.isEmpty()) return false
    val pattern = Patterns.EMAIL_ADDRESS
    return if (pattern != null) {
        pattern.matcher(trimmed).matches()
    } else {
        FALLBACK_EMAIL_REGEX.matches(trimmed)
    }
}

internal fun validateAuthFullName(fullName: String): String? =
    if (fullName.trim().isEmpty()) "Enter your full name" else null

internal fun validateAuthEmail(email: String): String? =
    if (!isValidAuthEmail(email)) "Enter a valid email address" else null

internal fun validateAuthPassword(password: String): String? =
    if (password.length < 6) "Password must be at least 6 characters" else null

internal fun validateAuthConfirmPassword(password: String, confirmPassword: String): String? =
    if (password != confirmPassword) "Passwords don't match" else null

internal fun validateAuthSubmission(
    isSignup: Boolean,
    fullName: String,
    email: String,
    password: String,
    confirmPassword: String
): AuthFieldErrors {
    return AuthFieldErrors(
        nameError = if (isSignup) validateAuthFullName(fullName) else null,
        emailError = validateAuthEmail(email),
        passwordError = validateAuthPassword(password),
        confirmPasswordError = if (isSignup) validateAuthConfirmPassword(password, confirmPassword) else null
    )
}

internal fun firstAuthBannerError(errors: AuthFieldErrors): String? {
    return when {
        errors.nameError != null -> "Please enter your full name."
        errors.emailError != null -> "Please enter a valid email address."
        errors.passwordError != null -> "Password must be at least 6 characters."
        errors.confirmPasswordError != null -> "Passwords do not match."
        else -> null
    }
}

@Composable
fun AuthScreen(
    onAuthChanged: () -> Unit,
    onGoogleSignInOverride: ((onSuccess: () -> Unit, onError: (String) -> Unit, onCancelled: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val isOnline by NetworkMonitor.observeOnline(context).collectAsStateWithLifecycle(initialValue = true)

    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val reduceMotion = rememberReduceMotion()
    val isAnyAuthLoading = isLoading || isGoogleLoading

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground(
            reduceMotion = reduceMotion,
            driftDurationMs = 12_000,
            pulseDurationMs = 6_000,
            minSquareAlpha = 0.016f,
            maxSquareAlphaDelta = 0.044f
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AuthHeader(reduceMotion = reduceMotion)

            Surface(
                color = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HairlineGrey, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (mode == AuthMode.LOGIN) "Login" else "Create Account",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureBlack,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (mode == AuthMode.LOGIN) {
                            "Sign in with your Google account or email and password."
                        } else {
                            "Create your account — email verification is required."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubtextGrey
                    )

                    if (!isOnline) {
                        Surface(
                            color = OffWhite,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, HairlineGrey, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "You are offline. Internet is required to sign in or create an account. Once signed in, the app works fully offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureBlack,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            color = OffWhite,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PureBlack, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureBlack,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (infoMessage != null) {
                        Surface(
                            color = OffWhite,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, HairlineGrey, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = infoMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureBlack,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (isAnyAuthLoading) return@OutlinedButton
                            errorMessage = null
                            infoMessage = null
                            isGoogleLoading = true
                            val onSuccess: () -> Unit = {
                                isGoogleLoading = false
                                onAuthChanged()
                            }
                            val onError: (String) -> Unit = { msg ->
                                isGoogleLoading = false
                                errorMessage = msg
                            }
                            val onCancelled: () -> Unit = {
                                isGoogleLoading = false
                            }
                            if (onGoogleSignInOverride != null) {
                                onGoogleSignInOverride(onSuccess, onError, onCancelled)
                            } else {
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = onSuccess,
                                    onAuthError = onError,
                                    scope = scope,
                                    onAuthCancelled = onCancelled
                                )
                            }
                        },
                        enabled = !isAnyAuthLoading,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, if (!isAnyAuthLoading) PureBlack else HairlineGrey),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PureWhite,
                            contentColor = PureBlack,
                            disabledContainerColor = PureWhite,
                            disabledContentColor = if (isGoogleLoading) PureBlack else SubtextGrey
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isGoogleLoading) {
                            CircularProgressIndicator(
                                color = PureBlack,
                                strokeWidth = 2.dp,
                                modifier = Modifier
                                    .size(18.dp)
                                    .testTag("google_sign_in_spinner")
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Signing in...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = PureBlack
                            )
                        } else {
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = HairlineGrey, modifier = Modifier.weight(1f))
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.labelSmall,
                            color = SubtextGrey
                        )
                        HorizontalDivider(color = HairlineGrey, modifier = Modifier.weight(1f))
                    }

                    if (mode == AuthMode.SIGNUP) {
                        AuthTextField(
                            value = fullName,
                            onValueChange = { updated ->
                                val clamped = if (updated.length <= 60) updated else fullName
                                fullName = clamped
                                errorMessage = null
                                if (nameError != null) {
                                    nameError = validateAuthFullName(clamped)
                                }
                            },
                            label = "Full Name",
                            errorText = nameError,
                            inputTestTag = "signup_name_input",
                            errorTestTag = "signup_name_error",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    AuthTextField(
                        value = email,
                        onValueChange = { updated ->
                            email = updated
                            errorMessage = null
                            if (emailError != null) {
                                emailError = validateAuthEmail(updated)
                            }
                        },
                        label = "Email",
                        errorText = emailError,
                        inputTestTag = "auth_email_input",
                        errorTestTag = "auth_email_error",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    AuthTextField(
                        value = password,
                        onValueChange = { updated ->
                            password = updated
                            errorMessage = null
                            if (passwordError != null) {
                                passwordError = validateAuthPassword(updated)
                            }
                            if (mode == AuthMode.SIGNUP && confirmPasswordError != null) {
                                confirmPasswordError = validateAuthConfirmPassword(updated, confirmPassword)
                            }
                        },
                        label = "Password",
                        errorText = passwordError,
                        inputTestTag = "auth_password_input",
                        errorTestTag = "auth_password_error",
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = SubtextGrey
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (mode == AuthMode.LOGIN) ImeAction.Done else ImeAction.Next
                        )
                    )

                    if (mode == AuthMode.SIGNUP) {
                        AuthTextField(
                            value = confirmPassword,
                            onValueChange = { updated ->
                                confirmPassword = updated
                                errorMessage = null
                                if (confirmPasswordError != null) {
                                    confirmPasswordError = validateAuthConfirmPassword(password, updated)
                                }
                            },
                            label = "Confirm Password",
                            errorText = confirmPasswordError,
                            inputTestTag = "signup_confirm_password_input",
                            errorTestTag = "signup_confirm_password_error",
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            )
                        )
                    }

                    if (mode == AuthMode.LOGIN) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Forgot password?",
                                style = MaterialTheme.typography.labelLarge,
                                color = PureBlack,
                                modifier = Modifier
                                    .clickable {
                                        errorMessage = null
                                        showForgotPasswordDialog = true
                                    }
                                    .padding(vertical = 4.dp)
                                    .testTag("forgot_password_link")
                            )
                        }
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = PureBlack,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        BlackPillButton(
                            text = if (mode == AuthMode.LOGIN) "Login" else "Create Account",
                            enabled = !isGoogleLoading,
                            onClick = {
                                errorMessage = null
                                infoMessage = null
                                val cleanEmail = email.trim()
                                val cleanName = fullName.trim()
                                val validation = validateAuthSubmission(
                                    isSignup = (mode == AuthMode.SIGNUP),
                                    fullName = fullName,
                                    email = email,
                                    password = password,
                                    confirmPassword = confirmPassword
                                )
                                nameError = validation.nameError
                                emailError = validation.emailError
                                passwordError = validation.passwordError
                                confirmPasswordError = validation.confirmPasswordError

                                if (validation.hasAny) {
                                    errorMessage = firstAuthBannerError(validation)
                                    return@BlackPillButton
                                }

                                isLoading = true
                                scope.launch {
                                    try {
                                        val auth = Firebase.auth
                                        if (mode == AuthMode.LOGIN) {
                                            auth.signInWithEmailAndPassword(cleanEmail, password).await()
                                            auth.currentUser?.reload()?.await()
                                            auth.currentUser?.uid?.let { signedInUid ->
                                                AuthFlowStore.markEmailSignIn(context, signedInUid)
                                            }
                                            isLoading = false
                                            onAuthChanged()
                                        } else {
                                            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                                            val user = result.user
                                            if (user != null) {
                                                val uid = user.uid
                                                AuthFlowStore.markNewEmailSignup(context, uid, cleanName)
                                                EmailVerificationAutoSender.onSignupStarted(uid)
                                                withContext(NonCancellable) {
                                                    try {
                                                        val profileUpdate = UserProfileChangeRequest.Builder()
                                                            .setDisplayName(cleanName)
                                                            .build()
                                                        user.updateProfile(profileUpdate).await()
                                                    } catch (_: Exception) {
                                                    } finally {
                                                        EmailVerificationAutoSender.onSignupProfileReady(uid)
                                                    }
                                                }
                                            }
                                            isLoading = false
                                            onAuthChanged()
                                        }
                                    } catch (e: Exception) {
                                        isLoading = false
                                        errorMessage = mapFirebaseAuthError(e)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_submit_button")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (mode == AuthMode.LOGIN) "Don't have an account? " else "Already have an account? ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SubtextGrey
                        )
                        Text(
                            text = if (mode == AuthMode.LOGIN) "Sign up" else "Login",
                            style = MaterialTheme.typography.labelLarge,
                            color = PureBlack,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable {
                                    errorMessage = null
                                    infoMessage = null
                                    nameError = null
                                    emailError = null
                                    passwordError = null
                                    confirmPasswordError = null
                                    mode = if (mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .testTag("auth_switch_mode")
                        )
                    }
                }
            }

            Text(
                text = "Offline-first · Cloud synced · Zero distraction",
                style = MaterialTheme.typography.labelMedium,
                color = SubtextGrey
            )
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            initialEmail = email.trim(),
            onDismiss = { showForgotPasswordDialog = false },
            onSent = { sentEmail ->
                showForgotPasswordDialog = false
                infoMessage = "Password reset link sent to $sentEmail. Check your inbox and spam folder, then login with your new password."
            }
        )
    }
}

@Composable
internal fun AuthHeader(
    reduceMotion: Boolean
) {
    var hasPlayedGoalCountUp by rememberSaveable { mutableStateOf(false) }
    val goalProgress = remember {
        Animatable(if (reduceMotion || hasPlayedGoalCountUp) 1f else 0f)
    }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            hasPlayedGoalCountUp = true
            goalProgress.snapTo(1f)
        } else if (!hasPlayedGoalCountUp) {
            hasPlayedGoalCountUp = true
            goalProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GOAL_COUNT_UP_DURATION_MS,
                    easing = EaseOutCubic
                )
            )
        }
    }

    val displayedGoal = formatGoalCount(computeEasedGoalCount(goalProgress.value))

    Column {
        Text(
            text = "DUROOD PAK COUNTER",
            style = MaterialTheme.typography.labelMedium,
            color = SubtextGrey
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "1 Crore",
            style = MaterialTheme.typography.displayLarge,
            color = PureBlack
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = displayedGoal,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFeatureSettings = "tnum"
            ),
            color = SubtextGrey,
            modifier = Modifier.testTag("auth_goal_count_text")
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    errorText: String?,
    inputTestTag: String,
    errorTestTag: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            isError = errorText != null,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            colors = monochromeTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(inputTestTag)
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.labelMedium,
                color = ErrorRed,
                modifier = Modifier
                    .padding(start = 12.dp, top = 4.dp)
                    .testTag(errorTestTag)
            )
        }
    }
}

@Composable
fun VerifyEmailScreen(
    email: String,
    pendingActionCode: String?,
    onConsumeActionCode: () -> Unit,
    onVerified: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val reduceMotion = rememberReduceMotion()

    var isResending by remember { mutableStateOf(false) }
    var isApplyingLink by remember { mutableStateOf(false) }
    var cooldownSeconds by rememberSaveable { mutableIntStateOf(0) }
    var sentConfirmation by rememberSaveable { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    suspend fun checkVerificationSilently() {
        try {
            val user = Firebase.auth.currentUser ?: return
            user.reload().await()
            if (Firebase.auth.currentUser?.isEmailVerified == true) {
                onVerified()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Silent retry on next 3-second tick or foreground resume
        }
    }

    // Automatically send verification email once user is confirmed signed in and ready,
    // retrying once automatically on failure and showing a clear message if both attempts fail.
    LaunchedEffect(email) {
        val result = EmailVerificationAutoSender.ensureAutoSent(context)
        if (result.isFailure && Firebase.auth.currentUser?.isEmailVerified != true) {
            statusMessage = EmailVerificationConfig.AUTO_SEND_FAILURE_MESSAGE
        }
    }

    // Apply incoming App Link verification action code automatically
    LaunchedEffect(pendingActionCode) {
        val code = pendingActionCode ?: return@LaunchedEffect
        isApplyingLink = true
        statusMessage = null
        try {
            Firebase.auth.applyActionCode(code).await()
            Firebase.auth.currentUser?.reload()?.await()
            onConsumeActionCode()
            isApplyingLink = false
            if (Firebase.auth.currentUser?.isEmailVerified == true) {
                onVerified()
            } else {
                statusMessage = "Email verified. Continuing..."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthActionCodeException) {
            onConsumeActionCode()
            try {
                Firebase.auth.currentUser?.reload()?.await()
            } catch (_: Exception) {
            }
            isApplyingLink = false
            if (Firebase.auth.currentUser?.isEmailVerified == true) {
                onVerified()
            } else {
                statusMessage = when (e.errorCode) {
                    "ERROR_EXPIRED_ACTION_CODE" ->
                        "This verification link has expired. Tap 'Resend Verification Email' below for a new link."
                    "ERROR_INVALID_ACTION_CODE" ->
                        "This verification link has already been used or is invalid. Tap 'Resend Verification Email' below if you still need a new link."
                    else ->
                        "Could not verify with this link. Please tap 'Resend Verification Email' below."
                }
            }
        } catch (e: Exception) {
            onConsumeActionCode()
            try {
                Firebase.auth.currentUser?.reload()?.await()
            } catch (_: Exception) {
            }
            isApplyingLink = false
            if (Firebase.auth.currentUser?.isEmailVerified == true) {
                onVerified()
            } else {
                statusMessage = mapFirebaseAuthError(e)
            }
        }
    }

    // Backup auto-check every 3 seconds while the waiting screen is open
    LaunchedEffect(email) {
        while (true) {
            delay(3000L)
            checkVerificationSilently()
        }
    }

    // Backup auto-check whenever the app comes back to the front
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch { checkVerificationSilently() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 60-second cooldown ticker after each Resend tap
    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000L)
            cooldownSeconds--
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HairlineGrey, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "VERIFY YOUR EMAIL",
                        style = MaterialTheme.typography.labelMedium,
                        color = SubtextGrey
                    )
                    Text(
                        text = "Check your email",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureBlack,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Check your email — we sent a verification link to $email\n\nAlso check your spam folder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubtextGrey
                    )

                    WaitingDotsRow(
                        label = if (isApplyingLink) "Verifying link..." else "Waiting for verification...",
                        reduceMotion = reduceMotion
                    )

                    if (statusMessage != null) {
                        Surface(
                            color = OffWhite,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PureBlack, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = statusMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureBlack,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    WhiteBorderPillButton(
                        text = when {
                            isResending -> "Sending..."
                            cooldownSeconds > 0 -> "Resend Verification Email (${cooldownSeconds}s)"
                            else -> "Resend Verification Email"
                        },
                        enabled = !isResending && cooldownSeconds == 0,
                        onClick = {
                            statusMessage = null
                            sentConfirmation = false
                            isResending = true
                            scope.launch {
                                try {
                                    val currentUser = Firebase.auth.currentUser
                                    if (currentUser == null) {
                                        isResending = false
                                        onSignOut()
                                        return@launch
                                    }
                                    sendVerificationEmail(context, currentUser)
                                    isResending = false
                                    sentConfirmation = true
                                    cooldownSeconds = 60
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    isResending = false
                                    statusMessage = mapFirebaseAuthError(e)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_email_resend_button")
                    )

                    if (sentConfirmation) {
                        Text(
                            text = "Sent",
                            style = MaterialTheme.typography.labelMedium,
                            color = PureBlack,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag("verify_email_sent_confirmation")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("verify_email_use_different_account")
                        ) {
                            Text(
                                text = "Use a different account",
                                color = SubtextGrey
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingDotsRow(
    label: String,
    reduceMotion: Boolean
) {
    val phase = if (reduceMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "verify_waiting_dots")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_phase"
        )
        animated
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) { index ->
                val dotAlpha = if (reduceMotion) {
                    0.7f
                } else {
                    val wave = (sin(phase - index * 0.9f) + 1f) * 0.5f
                    0.22f + wave * 0.78f
                }
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .alpha(dotAlpha)
                        .background(PureBlack, CircleShape)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = SubtextGrey
        )
    }
}

@Composable
fun LinkPasswordScreen(
    email: String,
    onLinked: () -> Unit,
    onSkip: () -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        FaintGridBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .widthIn(max = 520.dp)
                .align(Alignment.Center),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = PureWhite,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, HairlineGrey, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "ACCOUNT RECOVERY",
                        style = MaterialTheme.typography.labelMedium,
                        color = SubtextGrey
                    )
                    Text(
                        text = "Set a backup password",
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureBlack,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "You signed in with Google ($email). Set a permanent password linked to this same account so you can recover everything using email and password if you ever lose access to your Google account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubtextGrey
                    )

                    if (errorMessage != null) {
                        Surface(
                            color = OffWhite,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PureBlack, RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PureBlack,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = SubtextGrey
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next
                        ),
                        colors = monochromeTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_password_input")
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            errorMessage = null
                        },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        colors = monochromeTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_password_confirm_input")
                    )

                    BlackPillButton(
                        text = if (isSaving) "Saving..." else "Save Backup Password",
                        enabled = !isSaving,
                        onClick = {
                            errorMessage = null
                            if (email.isBlank()) {
                                errorMessage = "No email address associated with this account."
                                return@BlackPillButton
                            }
                            if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters."
                                return@BlackPillButton
                            }
                            if (password != confirmPassword) {
                                errorMessage = "Passwords do not match."
                                return@BlackPillButton
                            }
                            val user = Firebase.auth.currentUser
                            if (user == null) {
                                onSignOut()
                                return@BlackPillButton
                            }
                            isSaving = true
                            scope.launch {
                                try {
                                    val credential = EmailAuthProvider.getCredential(email, password)
                                    user.linkWithCredential(credential).await()
                                    user.reload().await()
                                    isSaving = false
                                    onLinked()
                                } catch (e: Exception) {
                                    isSaving = false
                                    errorMessage = mapFirebaseAuthError(e)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_password_submit_button")
                    )

                    WhiteBorderPillButton(
                        text = "Skip for now",
                        enabled = !isSaving,
                        onClick = onSkip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_password_skip_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSent: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var resetEmail by remember { mutableStateOf(initialEmail) }
    var isSending by remember { mutableStateOf(false) }
    var dialogError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PureWhite,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = "Reset Password",
                style = MaterialTheme.typography.titleLarge,
                color = PureBlack
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter your account email and we will send a password reset link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtextGrey
                )
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = {
                        resetEmail = it
                        dialogError = null
                    },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = monochromeTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forgot_password_email_input")
                )
                if (dialogError != null) {
                    Text(
                        text = dialogError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureBlack,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss, enabled = !isSending) {
                    Text("Cancel", color = SubtextGrey)
                }
                BlackPillButton(
                    text = if (isSending) "Sending..." else "Send Link",
                    enabled = !isSending,
                    onClick = {
                        val clean = resetEmail.trim()
                        if (clean.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(clean).matches()) {
                            dialogError = "Please enter a valid email address."
                            return@BlackPillButton
                        }
                        isSending = true
                        dialogError = null
                        scope.launch {
                            try {
                                Firebase.auth.sendPasswordResetEmail(clean).await()
                                isSending = false
                                onSent(clean)
                            } catch (e: Exception) {
                                isSending = false
                                dialogError = mapFirebaseAuthError(e)
                            }
                        }
                    },
                    modifier = Modifier.testTag("forgot_password_send_button")
                )
            }
        }
    )
}

@Composable
private fun monochromeTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PureBlack,
    unfocusedBorderColor = HairlineGrey,
    focusedLabelColor = PureBlack,
    unfocusedLabelColor = SubtextGrey,
    cursorColor = PureBlack,
    errorBorderColor = ErrorRed,
    errorLabelColor = ErrorRed,
    errorSupportingTextColor = ErrorRed,
    errorCursorColor = PureBlack,
    errorTrailingIconColor = SubtextGrey
)

fun mapFirebaseAuthError(error: Exception): String {
    return when (error) {
        is FirebaseNetworkException -> "No internet connection. Please connect and try again."
        is FirebaseAuthInvalidUserException -> "No account found for this email address."
        is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password. Please check and try again."
        is FirebaseAuthUserCollisionException -> "This email or credential is already linked to an account."
        is FirebaseAuthWeakPasswordException -> "Password is too weak. Please use at least 6 characters."
        is FirebaseAuthRecentLoginRequiredException ->
            "For security, please sign out and sign back in before completing this action."
        else -> {
            val msg = error.localizedMessage.orEmpty()
            when {
                msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ->
                    "Incorrect email or password. Please check and try again."
                msg.contains("TOO_MANY_ATTEMPTS_TRY_LATER", ignoreCase = true) ->
                    "Too many attempts. Please wait a moment and try again."
                msg.contains("PROVIDER_ALREADY_LINKED", ignoreCase = true) ->
                    "A password is already linked to this account."
                msg.isNotBlank() -> msg
                else -> "Authentication failed. Please try again."
            }
        }
    }
}
