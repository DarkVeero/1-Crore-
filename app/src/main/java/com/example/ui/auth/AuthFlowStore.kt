package com.example.ui.auth

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks whether an authenticated UID originated from:
 * - a brand-new email+password sign-up (must see SetupScreen once after email verification),
 * - an email+password sign-in (account already exists by definition; must NEVER see SetupScreen),
 * - or a Google sign-in (check Firestore/Room first; if existing -> Home, if brand-new -> LinkPassword -> Setup),
 * and whether a fresh login just occurred so the post-login animated logo loading screen plays smoothly.
 */
object AuthFlowStore {
    private const val PREFS_NAME = "one_crore_auth_flow_prefs"
    private const val KEY_NEW_SIGNUP_UIDS = "new_signup_uids"
    private const val KEY_EMAIL_SIGNIN_UIDS = "email_signin_uids"
    private const val KEY_SIGNUP_NAME_PREFIX = "signup_name_"

    private val memoryNewSignupUids = ConcurrentHashMap.newKeySet<String>()
    private val memoryEmailSignInUids = ConcurrentHashMap.newKeySet<String>()
    private val memorySignupNames = ConcurrentHashMap<String, String>()
    private val memoryFreshLoginUids = ConcurrentHashMap.newKeySet<String>()

    fun markNewEmailSignup(context: Context?, uid: String, displayName: String = "") {
        if (uid.isBlank()) return
        memoryNewSignupUids.add(uid)
        memoryEmailSignInUids.remove(uid)
        memoryFreshLoginUids.add(uid)
        if (displayName.isNotBlank()) {
            memorySignupNames[uid] = displayName.trim()
        }
        val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return
        val newSignups = (prefs.getStringSet(KEY_NEW_SIGNUP_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        newSignups.add(uid)
        val emailSignIns = (prefs.getStringSet(KEY_EMAIL_SIGNIN_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        emailSignIns.remove(uid)
        prefs.edit().apply {
            putStringSet(KEY_NEW_SIGNUP_UIDS, newSignups)
            putStringSet(KEY_EMAIL_SIGNIN_UIDS, emailSignIns)
            if (displayName.isNotBlank()) {
                putString(KEY_SIGNUP_NAME_PREFIX + uid, displayName.trim())
            }
            apply()
        }
    }

    fun markEmailSignIn(context: Context?, uid: String) {
        if (uid.isBlank()) return
        memoryNewSignupUids.remove(uid)
        memoryEmailSignInUids.add(uid)
        memoryFreshLoginUids.add(uid)
        val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return
        val newSignups = (prefs.getStringSet(KEY_NEW_SIGNUP_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        newSignups.remove(uid)
        val emailSignIns = (prefs.getStringSet(KEY_EMAIL_SIGNIN_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        emailSignIns.add(uid)
        prefs.edit()
            .putStringSet(KEY_NEW_SIGNUP_UIDS, newSignups)
            .putStringSet(KEY_EMAIL_SIGNIN_UIDS, emailSignIns)
            .remove(KEY_SIGNUP_NAME_PREFIX + uid)
            .apply()
    }

    fun markGoogleSignIn(context: Context?, uid: String) {
        if (uid.isBlank()) return
        memoryNewSignupUids.remove(uid)
        memoryEmailSignInUids.remove(uid)
        memoryFreshLoginUids.add(uid)
        val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return
        val newSignups = (prefs.getStringSet(KEY_NEW_SIGNUP_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        newSignups.remove(uid)
        val emailSignIns = (prefs.getStringSet(KEY_EMAIL_SIGNIN_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        emailSignIns.remove(uid)
        prefs.edit()
            .putStringSet(KEY_NEW_SIGNUP_UIDS, newSignups)
            .putStringSet(KEY_EMAIL_SIGNIN_UIDS, emailSignIns)
            .apply()
    }

    fun clearNewEmailSignup(context: Context?, uid: String) {
        if (uid.isBlank()) return
        memoryNewSignupUids.remove(uid)
        memorySignupNames.remove(uid)
        val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return
        val updated = (prefs.getStringSet(KEY_NEW_SIGNUP_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        updated.remove(uid)
        prefs.edit()
            .putStringSet(KEY_NEW_SIGNUP_UIDS, updated)
            .remove(KEY_SIGNUP_NAME_PREFIX + uid)
            .apply()
    }

    fun isNewEmailSignup(context: Context?, uid: String): Boolean {
        if (uid.isBlank()) return false
        if (memoryNewSignupUids.contains(uid)) return true
        val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return false
        val set = prefs.getStringSet(KEY_NEW_SIGNUP_UIDS, emptySet()) ?: emptySet()
        return set.contains(uid)
    }

    fun isExplicitEmailSignIn(context: Context?, uid: String): Boolean {
        if (uid.isBlank()) return false
        if (memoryEmailSignInUids.contains(uid)) return true
        val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return false
        val set = prefs.getStringSet(KEY_EMAIL_SIGNIN_UIDS, emptySet()) ?: emptySet()
        return set.contains(uid)
    }

    fun getSignupDisplayName(context: Context?, uid: String): String? {
        if (uid.isBlank()) return null
        memorySignupNames[uid]?.takeIf { it.isNotBlank() }?.let { return it }
        val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return null
        return prefs.getString(KEY_SIGNUP_NAME_PREFIX + uid, null)?.takeIf { it.isNotBlank() }
    }

    fun markFreshLogin(uid: String) {
        if (uid.isBlank()) return
        memoryFreshLoginUids.add(uid)
    }

    fun clearFreshLogin(uid: String?) {
        if (uid.isNullOrBlank()) {
            memoryFreshLoginUids.clear()
        } else {
            memoryFreshLoginUids.remove(uid)
        }
    }

    fun clearUser(context: Context?, uid: String) {
        if (uid.isBlank()) return
        memoryNewSignupUids.remove(uid)
        memoryEmailSignInUids.remove(uid)
        memorySignupNames.remove(uid)
        memoryFreshLoginUids.remove(uid)
        val prefs = context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: return
        val newSignups = (prefs.getStringSet(KEY_NEW_SIGNUP_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        newSignups.remove(uid)
        val emailSignIns = (prefs.getStringSet(KEY_EMAIL_SIGNIN_UIDS, emptySet()) ?: emptySet()).toMutableSet()
        emailSignIns.remove(uid)
        prefs.edit()
            .putStringSet(KEY_NEW_SIGNUP_UIDS, newSignups)
            .putStringSet(KEY_EMAIL_SIGNIN_UIDS, emailSignIns)
            .remove(KEY_SIGNUP_NAME_PREFIX + uid)
            .apply()
    }

    fun consumeFreshLoginFlag(uid: String): Boolean {
        if (uid.isBlank()) return false
        return memoryFreshLoginUids.remove(uid)
    }

    fun resetForTest(context: Context? = null) {
        memoryNewSignupUids.clear()
        memoryEmailSignInUids.clear()
        memorySignupNames.clear()
        memoryFreshLoginUids.clear()
        context?.applicationContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.clear()
            ?.commit()
    }
}
