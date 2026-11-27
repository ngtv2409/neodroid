package com.ngtv.neodroid.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val SETTINGS_DATASTORE_NAME = "settings"

private val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

sealed interface SettingsEntry {
    val title: String
    val description: String?
}

/**
 * Represents a single persisted option.
 */
sealed class Setting<T> : SettingsEntry {
    abstract val key: Preferences.Key<T>
    abstract val defaultValue: T
    abstract override val title: String
    abstract override val description: String?

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

/** A non-persisted row that performs an action. */
data class ActionSetting(
    val id: String,
    override val title: String,
    override val description: String? = null,
    val action: SettingsAction,
) : SettingsEntry

/** A non-persisted informational row. */
data class NoteSetting(
    val id: String,
    override val title: String,
    override val description: String? = null,
) : SettingsEntry

sealed interface SettingsAction {
    data class OpenUrl(val url: String) : SettingsAction

    data class ShowText(val assetPath: String, val title: String) : SettingsAction

    data object ResetSettings : SettingsAction
}

data class SettingOption(val value: String, val label: String)

data class SettingsSection(val title: String, val entries: List<SettingsEntry>)

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

    val dynamicColor = Setting.Toggle(
        key = booleanPreferencesKey("appearance.dynamic_color"),
        defaultValue = false,
        title = "Dynamic color",
        description = "Use wallpaper-based colors on Android 12 and newer.",
    )

    val editorFontFamily = Setting.Choice(
        key = stringPreferencesKey("editor.font_family"),
        defaultValue = EditorFont.JetBrainsMono.key,
        title = "Font",
        description = "Nerd Font used to render the Neovim editor.",
        options = EditorFont.entries.map { font ->
            SettingOption(font.key, font.label)
        },
    )

    val editorFontSize = Setting.Number(
        key = intPreferencesKey("editor.font_size"),
        defaultValue = 14,
        title = "Font size",
        description = "Text size in the Neovim editor.",
        range = 8..32,
        suffix = " sp",
    )

    val editorLineHeight = Setting.Number(
        key = intPreferencesKey("editor.line_height"),
        defaultValue = 130,
        title = "Line height",
        description = "Line height as a percentage of the font size.",
        range = 100..200,
        suffix = "%",
    )

    val allowRemoteClipboard = Setting.Toggle(
        key = booleanPreferencesKey("integration.remote_clipboard"),
        defaultValue = false,
        title = "Allow remote clipboard",
        description = "Let the remote process read and write your clipboard at will. " +
            "This allows remote Neovim to read anything currently on your clipboard " +
            "at any time. Only enable this for hosts you trust.",
    )

    val showHomeOnline = Setting.Toggle(
        key = booleanPreferencesKey("home.show_online"),
        defaultValue = true,
        title = "Online connections",
        description = "Show active SSH connections on the home screen.",
    )

    val showHomeRecent = Setting.Toggle(
        key = booleanPreferencesKey("home.show_recent"),
        defaultValue = true,
        title = "Recent hosts",
        description = "Show recently connected hosts on the home screen.",
    )

    val showHomeHosts = Setting.Toggle(
        key = booleanPreferencesKey("home.show_hosts"),
        defaultValue = true,
        title = "Saved hosts",
        description = "Show all saved hosts on the home screen.",
    )

    val keepScreenOn = Setting.Toggle(
        key = booleanPreferencesKey("general.keep_screen_on"),
        defaultValue = true,
        title = "Keep screen on",
        description = "Prevent the phone to close when idle.",
    )
}

object SettingsCatalog {
    val categories: List<SettingsCategory> = listOf(
        SettingsCategory(
            id = "general",
            title = "General",
            summary = "Manage general things.",
            sections = listOf(
                SettingsSection(
                    title = "General",
                    entries = listOf(
                        Settings.keepScreenOn,
                    ),
                ),
                SettingsSection(
                    title = "Settings",
                    entries = listOf(
                        ActionSetting(
                            id = "reset_settings",
                            title = "Reset all settings",
                            description = "Restore every app setting to its default value.",
                            action = SettingsAction.ResetSettings,
                        ),
                    ),
                ),
            ),
        ),
        SettingsCategory(
            id = "appearance",
            title = "Appearance",
            summary = "Theme and colors for the app.",
            sections = listOf(
                SettingsSection(
                    title = "Theme",
                    entries = listOf(
                        Settings.themeMode,
                        Settings.dynamicColor,
                    ),
                ),
            ),
        ),
        SettingsCategory(
            id = "editor",
            title = "Editor",
            summary = "Fonts and sizing for the Neovim editor.",
            sections = listOf(
                SettingsSection(
                    title = "Font",
                    entries = listOf(
                        Settings.editorFontFamily,
                        Settings.editorFontSize,
                        Settings.editorLineHeight,
                    ),
                ),
            ),
        ),
        SettingsCategory(
            id = "integration",
            title = "Integration",
            summary = "Control what remote Neovim sessions can access.",
            sections = listOf(
                SettingsSection(
                    title = "Clipboard",
                    entries = listOf(
                        Settings.allowRemoteClipboard,
                    ),
                ),
            ),
        ),
        SettingsCategory(
            id = "home",
            title = "Home screen",
            summary = "Choose which sections appear on the home screen.",
            sections = listOf(
                SettingsSection(
                    title = "Sections",
                    entries = listOf(
                        Settings.showHomeOnline,
                        Settings.showHomeRecent,
                        Settings.showHomeHosts,
                    ),
                ),
            ),
        ),
        SettingsCategory(
            id = "about",
            title = "About",
            summary = "Licenses, attribution, and project links.",
            sections = listOf(
                SettingsSection(
                    title = "Neodroid",
                    entries = listOf(
                        NoteSetting(
                            id = "license_note",
                            title = "Apache License 2.0",
                            description = "Neodroid is distributed under the Apache License " +
                                "2.0. Third-party components and assets keep their own " +
                                "licenses; see Third-party notices.",
                        ),
                        ActionSetting(
                            id = "view_license",
                            title = "View license",
                            description = "Read the bundled Apache License 2.0 text.",
                            action = SettingsAction.ShowText(
                                assetPath = "licenses/APACHE-2.0.txt",
                                title = "Apache License 2.0",
                            ),
                        ),
                        ActionSetting(
                            id = "font_licenses",
                            title = "Font licenses",
                            description = "License texts for the bundled Nerd Fonts.",
                            action = SettingsAction.ShowText(
                                assetPath = "licenses/FONT-LICENSES.txt",
                                title = "Font licenses",
                            ),
                        ),
                        ActionSetting(
                            id = "source_code",
                            title = "Source code",
                            description = "github.com/ngtv2409/neodroid",
                            action = SettingsAction.OpenUrl(
                                "https://github.com/ngtv2409/neodroid",
                            ),
                        ),
                    ),
                ),
                SettingsSection(
                    title = "Third-party software",
                    entries = listOf(
                        ActionSetting(
                            id = "third_party_notices",
                            title = "Third-party notices",
                            description = "Licenses and attributions for libraries and " +
                                "assets used by Neodroid.",
                            action = SettingsAction.ShowText(
                                assetPath = "licenses/THIRD-PARTY-NOTICES.txt",
                                title = "Third-party notices",
                            ),
                        ),
                        ActionSetting(
                            id = "bouncy_castle_license",
                            title = "Bouncy Castle",
                            description = "Cryptography provider, licensed under the " +
                                "Bouncy Castle Licence.",
                            action = SettingsAction.ShowText(
                                assetPath = "licenses/BOUNCY-CASTLE.txt",
                                title = "Bouncy Castle Licence",
                            ),
                        ),
                    ),
                ),
                SettingsSection(
                    title = "Attribution",
                    entries = listOf(
                        ActionSetting(
                            id = "material_symbols",
                            title = "Material Symbols",
                            description = "Icon set by Google, licensed under the " +
                                "Apache License 2.0.",
                            action = SettingsAction.OpenUrl(
                                "https://github.com/google/material-design-icons",
                            ),
                        ),
                        ActionSetting(
                            id = "nerd_fonts",
                            title = "Nerd Fonts",
                            description = "Patched fonts from the Nerd Fonts project. " +
                                "Each bundled font keeps its upstream license.",
                            action = SettingsAction.OpenUrl(
                                "https://github.com/ryanoasis/nerd-fonts",
                            ),
                        ),
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
