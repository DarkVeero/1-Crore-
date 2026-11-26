package com.example.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppThemeMode(
    val storageKey: String,
    val displayLabel: String
) {
    BLACK_AND_WHITE(storageKey = "bw", displayLabel = "Black & White"),
    RED(storageKey = "red", displayLabel = "Red");

    companion object {
        fun fromStorageKey(raw: String?): AppThemeMode {
            val normalized = raw?.trim().orEmpty()
            return entries.firstOrNull {
                it != AppThemeMode.RED && it.storageKey.equals(normalized, ignoreCase = true)
            } ?: BLACK_AND_WHITE
        }
    }
}

private val Context.themePreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "one_crore_theme_datastore"
)

/**
 * Stores the user's selected [AppThemeMode] in DataStore for durable persistence alongside a
 * fast SharedPreferences mirror so Frame 0 on cold start never blocks or flashes the wrong theme.
 */
object ThemePreferenceStore {
    private const val FAST_PREFS_NAME = "one_crore_theme_fast_prefs"
    private const val KEY_THEME_MODE_STR = "theme_mode"
    private val KEY_THEME_MODE = stringPreferencesKey(KEY_THEME_MODE_STR)

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableThemeFlow = MutableStateFlow(AppThemeMode.BLACK_AND_WHITE)
    @Volatile
    private var loadedFromDisk = false

    fun themeModeFlow(context: Context): StateFlow<AppThemeMode> {
        ensureLoaded(context)
        return mutableThemeFlow.asStateFlow()
    }

    fun getCurrentThemeMode(context: Context?): AppThemeMode {
        if (context != null) {
            ensureLoaded(context)
        }
        return mutableThemeFlow.value
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        val appCtx = context.applicationContext
        loadedFromDisk = true
        mutableThemeFlow.value = mode
        appCtx.getSharedPreferences(FAST_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE_STR, mode.storageKey)
            .apply()
        storeScope.launch {
            runCatching {
                appCtx.themePreferencesDataStore.edit { prefs ->
                    prefs[KEY_THEME_MODE] = mode.storageKey
                }
            }
        }
    }

    /**
     * Reloads the theme choice from persistent device storage (used on cold-start initialization
     * or restart verification).
     */
    fun reloadFromDevicePrefs(context: Context): AppThemeMode {
        val appCtx = context.applicationContext
        val raw = appCtx.getSharedPreferences(FAST_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE_STR, null)
        val mode = AppThemeMode.fromStorageKey(raw)
        loadedFromDisk = true
        mutableThemeFlow.value = mode
        return mode
    }

    fun resetForTest(context: Context? = null) {
        loadedFromDisk = false
        mutableThemeFlow.value = AppThemeMode.BLACK_AND_WHITE
        context?.applicationContext
            ?.getSharedPreferences(FAST_PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.clear()
            ?.commit()
    }

    private fun ensureLoaded(context: Context) {
        if (!loadedFromDisk) {
            synchronized(this) {
                if (!loadedFromDisk) {
                    val appCtx = context.applicationContext
                    val raw = appCtx.getSharedPreferences(FAST_PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(KEY_THEME_MODE_STR, null)
                    mutableThemeFlow.value = AppThemeMode.fromStorageKey(raw)
                    loadedFromDisk = true
                }
            }
        }
    }
}
