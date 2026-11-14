package com.audiops.app

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import java.io.IOException

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Use wallpaper-based colours on Android 12+. */
    val dynamicColor: Boolean = true,
    /** Use true black surfaces in the dark theme (saves power on OLED screens). */
    val pureBlack: Boolean = false,
    /** Keep the screen on while a recording is being processed. */
    val keepScreenOn: Boolean = true,
    /** Spoken language preselected for new transcriptions. */
    val defaultLanguage: SpeechLanguage = SpeechLanguage.AUTO,
)

/**
 * Creates the settings DataStore at [path]. Create it once per process and share it:
 * DataStore allows only one instance per file. On Android the path matches the file the
 * earlier `preferencesDataStore("settings")` delegate used, so existing settings are kept.
 */
fun createSettingsStore(path: String): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

/** Persists user preferences with Jetpack DataStore (multiplatform). */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<UserSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UserSettings(
                themeMode = prefs[THEME_MODE]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[DYNAMIC_COLOR] ?: true,
                pureBlack = prefs[PURE_BLACK] ?: false,
                keepScreenOn = prefs[KEEP_SCREEN_ON] ?: true,
                defaultLanguage = prefs[DEFAULT_LANGUAGE]
                    ?.let { name -> SpeechLanguage.entries.firstOrNull { it.name == name } }
                    ?.takeIf { it in transcriptionLanguages }
                    ?: SpeechLanguage.AUTO,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        store.edit { it[PURE_BLACK] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        store.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setDefaultLanguage(language: SpeechLanguage) {
        store.edit { it[DEFAULT_LANGUAGE] = language.name }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val DEFAULT_LANGUAGE = stringPreferencesKey("default_language")
    }
}
