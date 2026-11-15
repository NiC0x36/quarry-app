package app.quarry.tanvir.info.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.quarry.tanvir.info.domain.model.StorageCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val displayName: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

class UserPreferencesRepository private constructor(context: Context) {

    private val supportedCategoryNames = StorageCategory.entries
        .filter { it != StorageCategory.APPS }.map { it.name }.toSet()

    private fun enabledFileCategories(stored: Set<String>?): Set<String> {
        val supported = stored.orEmpty().intersect(supportedCategoryNames)
        return if (supported.size >= 4) supported else supportedCategoryNames
    }

    // Index settings contain folder paths; keep them outside backup and device transfer.
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { File(context.noBackupFilesDir, "user_preferences.preferences_pb") }
    )

    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color_enabled")
    private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
    private val BIOMETRIC_AUTH_KEY = booleanPreferencesKey("biometric_auth_enabled")
    private val DELETE_COUNTDOWN_KEY = intPreferencesKey("delete_countdown_seconds")
    private val EXCLUDED_FOLDERS_KEY = stringSetPreferencesKey("excluded_folders")
    private val SCAN_HIDDEN_FILES_KEY = booleanPreferencesKey("scan_hidden_files")
    private val QUICK_INSIGHTS_ENABLED_KEY = booleanPreferencesKey("quick_insights_enabled")
    private val ENABLED_CATEGORIES_KEY = stringSetPreferencesKey("enabled_categories")
    private val HAPTICS_ENABLED_KEY = booleanPreferencesKey("haptics_enabled")
    private val HAPTIC_STRENGTH_KEY = intPreferencesKey("haptic_strength")
    private val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
    private val SELECTED_VOLUME_ID_KEY = stringPreferencesKey("selected_volume_id")

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        val raw = preferences[THEME_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(raw)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val isDynamicColorEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR_KEY] ?: false
    }

    val scanHiddenFiles: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SCAN_HIDDEN_FILES_KEY] ?: true
    }

    val isOnboardingCompleted: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[ONBOARDING_COMPLETED_KEY] ?: false
    }

    val isBiometricAuthEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BIOMETRIC_AUTH_KEY] ?: true
    }

    val deleteCountdownSeconds: Flow<Int> = dataStore.data.map { preferences ->
        preferences[DELETE_COUNTDOWN_KEY] ?: 5
    }

    val excludedFolders: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[EXCLUDED_FOLDERS_KEY] ?: emptySet()
    }

    val isQuickInsightsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[QUICK_INSIGHTS_ENABLED_KEY] ?: true
    }

    val enabledCategories: Flow<Set<String>> = dataStore.data.map { preferences ->
        enabledFileCategories(preferences[ENABLED_CATEGORIES_KEY])
    }

    val isHapticsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[HAPTICS_ENABLED_KEY] ?: true
    }

    val hapticStrength: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[HAPTIC_STRENGTH_KEY] ?: 60).coerceIn(1, 100)
    }

    val isKeepScreenOn: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[KEEP_SCREEN_ON_KEY] ?: false
    }

    val selectedVolumeId: Flow<String> = dataStore.data.map { preferences ->
        preferences[SELECTED_VOLUME_ID_KEY] ?: "internal_storage"
    }

    suspend fun setSelectedVolumeId(volumeId: String) {
        dataStore.edit { preferences ->
            preferences[SELECTED_VOLUME_ID_KEY] = volumeId
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode.name
        }
    }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    suspend fun setScanHiddenFiles(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SCAN_HIDDEN_FILES_KEY] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    suspend fun setBiometricAuthEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_AUTH_KEY] = enabled
        }
    }

    suspend fun setDeleteCountdownSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[DELETE_COUNTDOWN_KEY] = seconds.coerceIn(0, 10)
        }
    }

    suspend fun addExcludedFolder(path: String) {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { preferences ->
            val current = preferences[EXCLUDED_FOLDERS_KEY] ?: emptySet()
            preferences[EXCLUDED_FOLDERS_KEY] = current + trimmed
        }
    }

    suspend fun addExcludedFolders(paths: Collection<String>) {
        val cleaned = paths.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return
        dataStore.edit { preferences ->
            val current = preferences[EXCLUDED_FOLDERS_KEY] ?: emptySet()
            preferences[EXCLUDED_FOLDERS_KEY] = current + cleaned
        }
    }

    suspend fun setQuickInsightsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[QUICK_INSIGHTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setEnabledCategories(categories: Set<String>) {
        val cleaned = categories.intersect(supportedCategoryNames)
        if (cleaned.size < 4) return
        dataStore.edit { preferences ->
            preferences[ENABLED_CATEGORIES_KEY] = cleaned
        }
    }

    suspend fun toggleCategory(categoryName: String): Boolean {
        if (categoryName !in supportedCategoryNames) return false
        var applied = false
        dataStore.edit { preferences ->
            val current = enabledFileCategories(preferences[ENABLED_CATEGORIES_KEY])
            val next = if (current.contains(categoryName)) {
                if (current.size <= 4) current else current - categoryName
            } else {
                current + categoryName
            }
            applied = next != current
            if (applied) preferences[ENABLED_CATEGORIES_KEY] = next
        }
        return applied
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAPTICS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setHapticStrength(strength: Int) {
        dataStore.edit { preferences ->
            preferences[HAPTIC_STRENGTH_KEY] = strength.coerceIn(1, 100)
        }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON_KEY] = enabled
        }
    }

    suspend fun removeExcludedFolder(path: String) {
        dataStore.edit { preferences ->
            val current = preferences[EXCLUDED_FOLDERS_KEY] ?: emptySet()
            preferences[EXCLUDED_FOLDERS_KEY] = current - path
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let { return@synchronized it }
                val instance = UserPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
