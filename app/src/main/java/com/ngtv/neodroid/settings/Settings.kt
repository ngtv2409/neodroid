package com.ngtv.neodroid.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val SETTINGS_DATASTORE_NAME = "settings"

private val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

/**
 * Represents a single option
 */
sealed class Setting<T> {
    abstract val key: Preferences.Key<T>
    abstract val defaultValue: T
    abstract val title: String
    abstract val description: String?

    data class Toggle(
        override val key: Preferences.Key<Boolean>,
        override val defaultValue: Boolean,
        override val title: String,
        override val description: String? = null,
    ) : Setting<Boolean>()

    data class Text(
        override val key: Preferences.Key<String>,
        override val defaultValue: String,
        override val title: String,
        override val description: String? = null,
        val placeholder: String? = null,
        val maxLength: Int? = null,
    ) : Setting<String>()

    data class Choice(
        override val key: Preferences.Key<String>,
        override val defaultValue: String,
        override val title: String,
        override val description: String? = null,
        val options: List<SettingOption>,
    ) : Setting<String>()

    data class Number(
        override val key: Preferences.Key<Int>,
        override val defaultValue: Int,
        override val title: String,
        override val description: String? = null,
        val range: IntRange,
        val suffix: String = "",
    ) : Setting<Int>()
}

data class SettingOption(val value: String, val label: String)

data class SettingsSection(val title: String, val settings: List<Setting<*>>)

data class SettingsCategory(
    val id: String,
    val title: String,
    val summary: String,
    val sections: List<SettingsSection>,
)

/**
 * All app options.
 *
 * Keys should be stable
 */
object Settings {
    val themeMode = Setting.Choice(
        key = stringPreferencesKey("appearance.theme_mode"),
        defaultValue = "system",
        title = "Theme",
        description = "Use the system theme or always use light/dark.",
        options = listOf(
            SettingOption("system", "System"),
            SettingOption("light", "Light"),
            SettingOption("dark", "Dark"),
        ),
    )
}

object SettingsCatalog {
    val categories: List<SettingsCategory> = listOf(
        SettingsCategory(
            id = "general",
            title = "General",
            summary = "General app settings",
            sections = listOf(
                SettingsSection(
                    title = "Appearance",
                    settings = listOf(
                        Settings.themeMode,
                    ),
                ),
            ),
        ),
    )
}

/**
 * Retrieve:
 *
 * val repository = rememberSettingsRepository()
 * val fontSize by repository.get(Settings.editorFontSize)
 *     .collectAsStateWithLifecycle(initialValue = Settings.editorFontSize.defaultValue)
 */
class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    fun <T> getAsFlow(setting: Setting<T>): Flow<T> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences -> preferences[setting.key] ?: setting.defaultValue }

    suspend fun <T> setValue(setting: Setting<T>, value: T) {
        dataStore.edit { preferences ->
            preferences[setting.key] = value
        }
    }

    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

@Composable
fun rememberSettingsRepository(): SettingsRepository {
    val context = LocalContext.current
    return remember(context) { SettingsRepository(context) }
}
