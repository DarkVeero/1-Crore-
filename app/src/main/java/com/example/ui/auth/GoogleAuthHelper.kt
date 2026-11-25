package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal const val GOOGLE_CONFIG_ERROR_MESSAGE =
    "Google Sign-In could not verify this build with Google Play / Firebase (this build's signing SHA-1 fingerprint may not be registered in Firebase Console). Please use the verified build or sign in with email and password."

internal const val GOOGLE_NO_CREDENTIAL_MESSAGE =
    "No Google account is available for sign-in on this device, or this build's SHA-1 certificate is not registered in Firebase Console. Add a Google account in device Settings or sign in with email and password."

internal sealed interface GoogleSignInAttemptResult {
    data class Success(val idToken: String) : GoogleSignInAttemptResult
    data object Cancelled : GoogleSignInAttemptResult
    data class Error(
        val message: String,
        val isConfigurationIssue: Boolean = false
    ) : GoogleSignInAttemptResult
}

internal data class ClassifiedGoogleSignInFailure(
    val isCancelled: Boolean,
    val message: String = "",
    val shouldAttemptFallback: Boolean = false,
    val isConfigurationIssue: Boolean = false
)

internal fun findHostActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

internal fun buildSignInWithGoogleRequest(serverClientId: String): GetCredentialRequest {
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId).build()
    return GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
}

internal fun buildGoogleIdRequest(
    serverClientId: String,
    filterByAuthorizedAccounts: Boolean,
    autoSelectEnabled: Boolean = false
): GetCredentialRequest {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
        .setServerClientId(serverClientId)
        .setAutoSelectEnabled(autoSelectEnabled)
        .build()
    return GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
}

internal fun isGoogleConfigErrorMessage(message: String?): Boolean {
    if (message.isNullOrBlank()) return false
    val lower = message.lowercase()
    return lower.contains("developer console") ||
        lower.contains("developer_error") ||
        lower.contains("28444") ||
        lower.contains("28434") ||
        lower.contains("10:") ||
        lower.contains("status code: 10") ||
        lower.contains("certificate") ||
        lower.contains("sha-1") ||
        lower.contains("sha1") ||
        lower.contains("fingerprint") ||
        lower.contains("unregistered") ||
        lower.contains("misconfigured") ||
        lower.contains("client_id") ||
        lower.contains("api_not_connected")
}

internal fun classifyGoogleSignInException(error: Throwable): ClassifiedGoogleSignInFailure {
    if (error is CancellationException) throw error
    val msg = error.message ?: error.localizedMessage

    return when (error) {
        is GetCredentialCancellationException -> {
            if (isGoogleConfigErrorMessage(msg)) {
                ClassifiedGoogleSignInFailure(
                    isCancelled = false,
                    message = GOOGLE_CONFIG_ERROR_MESSAGE,
                    shouldAttemptFallback = false,
                    isConfigurationIssue = true
                )
            } else {
                ClassifiedGoogleSignInFailure(isCancelled = true)
            }
        }
        is GetCredentialProviderConfigurationException -> ClassifiedGoogleSignInFailure(
            isCancelled = false,
            message = GOOGLE_CONFIG_ERROR_MESSAGE,
            shouldAttemptFallback = false,
            isConfigurationIssue = true
        )
        is GetCredentialUnsupportedException -> ClassifiedGoogleSignInFailure(
            isCancelled = false,
            message = "Google Sign-In is not supported on this device (Credential Manager unavailable). Please sign in with email and password.",
            shouldAttemptFallback = false,
            isConfigurationIssue = false
        )
        is GoogleIdTokenParsingException -> ClassifiedGoogleSignInFailure(
            isCancelled = false,
            message = GOOGLE_CONFIG_ERROR_MESSAGE,
            shouldAttemptFallback = false,
            isConfigurationIssue = true
        )
        is FirebaseNetworkException -> ClassifiedGoogleSignInFailure(
            isCancelled = false,
            message = "No internet connection. Please connect and try again.",
            shouldAttemptFallback = false,
            isConfigurationIssue = false
        )
        is FirebaseAuthException -> ClassifiedGoogleSignInFailure(
            isCancelled = false,
            message = mapFirebaseAuthError(error),
            shouldAttemptFallback = false,
            isConfigurationIssue = false
        )
        is NoCredentialException -> {
            if (isGoogleConfigErrorMessage(msg)) {
                ClassifiedGoogleSignInFailure(
                    isCancelled = false,
                    message = GOOGLE_CONFIG_ERROR_MESSAGE,
                    shouldAttemptFallback = false,
                    isConfigurationIssue = true
                )
            } else {
                ClassifiedGoogleSignInFailure(
                    isCancelled = false,
                    message = GOOGLE_NO_CREDENTIAL_MESSAGE,
                    shouldAttemptFallback = true,
                    isConfigurationIssue = false
                )
            }
        }
        is GetCredentialException -> {
            val isConfig = isGoogleConfigErrorMessage(msg)
            ClassifiedGoogleSignInFailure(
                isCancelled = false,
                message = if (isConfig) {
                    GOOGLE_CONFIG_ERROR_MESSAGE
                } else {
                    msg?.takeIf { it.isNotBlank() }
                        ?: "Google Sign-In failed. Please check your internet connection and try again."
                },
                shouldAttemptFallback = false,
                isConfigurationIssue = isConfig
            )
        }
        else -> {
            val isConfig = isGoogleConfigErrorMessage(msg)
            ClassifiedGoogleSignInFailure(
                isCancelled = false,
                message = if (isConfig) {
                    GOOGLE_CONFIG_ERROR_MESSAGE
                } else {
                    error.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "Google Sign-In failed. Please check your internet connection."
                },
                shouldAttemptFallback = false,
                isConfigurationIssue = isConfig
            )
        }
    }
}

internal suspend fun executeInteractiveGoogleCredentialFlow(
    serverClientId: String,
    fetchCredential: suspend (GetCredentialRequest) -> Credential,
    extractIdToken: (Credential) -> String? = { credential ->
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    },
    signInWithIdToken: suspend (String) -> Unit
): GoogleSignInAttemptResult {
    if (serverClientId.isBlank()) {
        return GoogleSignInAttemptResult.Error(
            message = "Google Sign-In configuration missing: Web Client ID is empty.",
            isConfigurationIssue = true
        )
    }

    suspend fun completeWithRequest(request: GetCredentialRequest): GoogleSignInAttemptResult {
        val credential = fetchCredential(request)
        val googleIdToken = extractIdToken(credential)
            ?: return GoogleSignInAttemptResult.Error("Unexpected credential type returned.")
        signInWithIdToken(googleIdToken)
        return GoogleSignInAttemptResult.Success(googleIdToken)
    }

    val primaryFailure = try {
        return completeWithRequest(buildSignInWithGoogleRequest(serverClientId))
    } catch (e: Throwable) {
        if (e is CancellationException) throw e
        classifyGoogleSignInException(e)
    }

    if (primaryFailure.isCancelled) {
        return GoogleSignInAttemptResult.Cancelled
    }
    if (!primaryFailure.shouldAttemptFallback) {
        return GoogleSignInAttemptResult.Error(
            message = primaryFailure.message,
            isConfigurationIssue = primaryFailure.isConfigurationIssue
        )
    }

    return try {
        completeWithRequest(
            buildGoogleIdRequest(
                serverClientId = serverClientId,
                filterByAuthorizedAccounts = false,
                autoSelectEnabled = false
            )
        )
    } catch (fallbackError: Throwable) {
        if (fallbackError is CancellationException) throw fallbackError
        val secondaryFailure = classifyGoogleSignInException(fallbackError)
        if (secondaryFailure.isCancelled) {
            GoogleSignInAttemptResult.Cancelled
        } else if (primaryFailure.isConfigurationIssue && !secondaryFailure.isConfigurationIssue) {
            GoogleSignInAttemptResult.Error(
                message = primaryFailure.message,
                isConfigurationIssue = true
            )
        } else {
            GoogleSignInAttemptResult.Error(
                message = secondaryFailure.message,
                isConfigurationIssue = secondaryFailure.isConfigurationIssue || primaryFailure.isConfigurationIssue
            )
        }
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }

    val request = buildGoogleIdRequest(
        serverClientId = clientId,
        filterByAuthorizedAccounts = true,
        autoSelectEnabled = true
    )

    scope.launch {
        try {
            val hostContext = findHostActivity(context) ?: context
            val result = credentialManager.getCredential(hostContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                Firebase.auth.currentUser?.uid?.let { uid ->
                    AuthFlowStore.markGoogleSignIn(context, uid)
                }
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

// Re-authenticates the currently signed-in Firebase user with a fresh Google
// ID-token credential. Used when deleting the account of a Google-only user,
// who has no password to re-authenticate with. Returns failure when there is
// no signed-in user, no Google credential is available, or re-authentication fails.
internal suspend fun reauthenticateWithGoogle(
    context: Context,
    credentialManager: CredentialManager
): Result<Unit> {
    val user = Firebase.auth.currentUser
        ?: return Result.failure(IllegalStateException("Account session not found. Please sign in again."))
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        return Result.failure(IllegalStateException(GOOGLE_CONFIG_ERROR_MESSAGE))
    }
    return try {
        val request = buildGoogleIdRequest(
            serverClientId = clientId,
            filterByAuthorizedAccounts = true,
            autoSelectEnabled = true
        )
        val hostContext = findHostActivity(context) ?: context
        val result = credentialManager.getCredential(hostContext, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            user.reauthenticate(authCredential).await()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(GOOGLE_NO_CREDENTIAL_MESSAGE))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
        return
    }

    val hostContext = findHostActivity(context) ?: context

    scope.launch {
        try {
            when (
                val outcome = executeInteractiveGoogleCredentialFlow(
                    serverClientId = clientId,
                    fetchCredential = { request ->
                        credentialManager.getCredential(hostContext, request).credential
                    },
                    signInWithIdToken = { googleIdToken ->
                        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        Firebase.auth.signInWithCredential(authCredential).await()
                        Firebase.auth.currentUser?.uid?.let { uid ->
                            AuthFlowStore.markGoogleSignIn(context, uid)
                        }
                    }
                )
            ) {
                is GoogleSignInAttemptResult.Success -> onAuthSuccess()
                is GoogleSignInAttemptResult.Cancelled -> {
                    Log.w("Auth", "Google Sign-In cancelled or dismissed by user.")
                    onAuthCancelled()
                }
                is GoogleSignInAttemptResult.Error -> {
                    Log.e("Auth", "Google Sign-In failed: ${outcome.message}")
                    onAuthError(outcome.message)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In unexpected error", e)
            onAuthError(classifyGoogleSignInException(e).message.ifBlank {
                "Google Sign-In failed. Please check your internet connection."
            })
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                val signingOutUid = Firebase.auth.currentUser?.uid
                AuthFlowStore.clearFreshLogin(signingOutUid)
                EmailVerificationAutoSender.clearForUser(signingOutUid)
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
