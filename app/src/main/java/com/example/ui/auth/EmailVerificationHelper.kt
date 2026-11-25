package com.example.ui.auth

import android.content.Context
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

object EmailVerificationConfig {
    const val AUTH_DOMAIN = "gen-lang-client-0318050933.firebaseapp.com"
    const val CONTINUE_URL = "https://$AUTH_DOMAIN/verify-email"
    const val AUTO_SEND_FAILURE_MESSAGE =
        "Couldn't send the verification email — tap Resend to try again"
}

fun buildVerificationActionCodeSettings(context: Context): ActionCodeSettings {
    return ActionCodeSettings.newBuilder()
        .setUrl(EmailVerificationConfig.CONTINUE_URL)
        .setHandleCodeInApp(true)
        .setAndroidPackageName(
            context.packageName,
            true,
            null
        )
        .build()
}

suspend fun sendVerificationEmail(context: Context, user: FirebaseUser) {
    user.sendEmailVerification(buildVerificationActionCodeSettings(context)).await()
}

/**
 * Executes an automatic email verification send with one automatic retry if the first attempt fails.
 */
suspend fun executeAutoSendWithSingleRetry(
    retryDelayMillis: Long = 600L,
    onBeforeRetry: suspend () -> Unit = {},
    sendBlock: suspend () -> Unit
): Result<Unit> {
    return try {
        sendBlock()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (retryDelayMillis > 0L) {
            delay(retryDelayMillis)
        }
        try {
            onBeforeRetry()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        try {
            sendBlock()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (retryError: Exception) {
            Result.failure(retryError)
        }
    }
}

/**
 * Coordinates automatic verification email sending after email/password signup.
 *
 * Root cause of previous bug:
 * Calling `createUserWithEmailAndPassword` in `AuthScreen` immediately triggered
 * `FirebaseAuth.AuthStateListener` in `AppNavigation`, which unmounted `AuthScreen`
 * and cancelled `AuthScreen`'s `rememberCoroutineScope()` while `user.updateProfile()`
 * was still running—before `sendVerificationEmail` could execute (or cancelling it mid-flight),
 * and any error in `AuthScreen` was caught and swallowed silently.
 *
 * Fix:
 * `AuthScreen` completes profile initialization in `NonCancellable` and signals readiness here,
 * and `VerifyEmailScreen` (which remains mounted while waiting) triggers `ensureAutoSent`
 * once the user is confirmed signed in and ready, retrying once automatically on failure
 * and surfacing a clear message if both attempts fail.
 */
object EmailVerificationAutoSender {
    private val mutex = Mutex()
    private val profileReadySignals = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private val autoSentUids = ConcurrentHashMap.newKeySet<String>()

    private const val AUTO_SENT_PREFS_NAME = "one_crore_email_verification_prefs"
    private const val KEY_AUTO_SENT_UIDS = "auto_sent_uids"

    private fun hasAutoSentPersisted(context: Context, uid: String): Boolean {
        val prefs = context.applicationContext
            .getSharedPreferences(AUTO_SENT_PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getStringSet(KEY_AUTO_SENT_UIDS, emptySet()) ?: emptySet()).contains(uid)
    }

    private fun persistAutoSentUid(context: Context, uid: String) {
        val prefs = context.applicationContext
            .getSharedPreferences(AUTO_SENT_PREFS_NAME, Context.MODE_PRIVATE)
        val sent = (prefs.getStringSet(KEY_AUTO_SENT_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        if (!sent.add(uid)) return
        prefs.edit().putStringSet(KEY_AUTO_SENT_UIDS, sent).apply()
    }

    fun onSignupStarted(uid: String) {
        if (uid.isBlank()) return
        autoSentUids.remove(uid)
        profileReadySignals[uid] = CompletableDeferred()
    }

    fun onSignupProfileReady(uid: String) {
        if (uid.isBlank()) return
        profileReadySignals.getOrPut(uid) { CompletableDeferred() }.complete(Unit)
    }

    fun clearForUser(uid: String?) {
        if (uid.isNullOrBlank()) {
            profileReadySignals.clear()
            autoSentUids.clear()
        } else {
            profileReadySignals.remove(uid)
            autoSentUids.remove(uid)
        }
    }

    suspend fun ensureAutoSent(
        context: Context
    ): Result<Boolean> = mutex.withLock {
        val initialUser = Firebase.auth.currentUser
            ?: return@withLock Result.failure(IllegalStateException("User not signed in"))
        val uid = initialUser.uid
        if (initialUser.isEmailVerified || autoSentUids.contains(uid) || hasAutoSentPersisted(context, uid)) {
            return@withLock Result.success(false)
        }

        // If signup is finishing updateProfile(), wait briefly so user state is settled and ready
        profileReadySignals[uid]?.let { deferred ->
            withTimeoutOrNull(2_500L) {
                deferred.await()
            }
        }

        val readyUser = Firebase.auth.currentUser
            ?: return@withLock Result.failure(IllegalStateException("User not signed in"))
        if (readyUser.isEmailVerified) {
            autoSentUids.add(uid)
        persistAutoSentUid(context, uid)
            return@withLock Result.success(false)
        }

        // Ensure the newly created user's auth token is fresh and ready before sending
        try {
            readyUser.getIdToken(false).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }

        val sendResult = executeAutoSendWithSingleRetry(
            retryDelayMillis = 700L,
            onBeforeRetry = {
                val u = Firebase.auth.currentUser ?: readyUser
                try {
                    u.reload().await()
                } catch (_: Exception) {
                }
                try {
                    u.getIdToken(true).await()
                } catch (_: Exception) {
                }
            },
            sendBlock = {
                val targetUser = Firebase.auth.currentUser ?: readyUser
                sendVerificationEmail(context.applicationContext, targetUser)
            }
        )

        return@withLock sendResult.map {
            autoSentUids.add(uid)
        persistAutoSentUid(context, uid)
            true
        }
    }
}

fun extractVerificationOobCode(uri: Uri?): String? {
    if (uri == null) return null

    val directMode = uri.getQueryParameter("mode")
    val directCode = uri.getQueryParameter("oobCode")?.takeIf { it.isNotBlank() }
    if (directCode != null && (directMode.isNullOrBlank() || directMode == "verifyEmail")) {
        return directCode
    }

    val nestedLink = uri.getQueryParameter("link") ?: uri.getQueryParameter("continueUrl")
    if (!nestedLink.isNullOrBlank()) {
        val nestedUri = try {
            Uri.parse(nestedLink)
        } catch (_: Exception) {
            null
        }
        val nestedMode = nestedUri?.getQueryParameter("mode")
        val nestedCode = nestedUri?.getQueryParameter("oobCode")?.takeIf { it.isNotBlank() }
        if (nestedCode != null && (nestedMode.isNullOrBlank() || nestedMode == "verifyEmail")) {
            return nestedCode
        }
    }

    return null
}
