package cz.darken.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cz.darken.app.timer.OverlayTimer
import cz.darken.app.model.DimLevel
import cz.darken.app.model.OverlayTint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "darken_prefs")

class PreferencesRepository(private val context: Context) {

    val defaultDimLevel: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_DIM]?.coerceIn(MIN_DIM, MAX_DIM) ?: FALLBACK_DEFAULT
    }

    val hasSavedDefaultDim: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs.contains(KEY_DEFAULT_DIM)
    }

    suspend fun setDefaultDimLevel(level: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_DIM] = level.coerceIn(MIN_DIM, MAX_DIM)
        }
    }

    suspend fun resolveDefaultDimLevel(): Int = defaultDimLevel.first()

    val highDimmingWarningHidden: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HIGH_DIMMING_WARNING_HIDDEN] ?: false
    }

    suspend fun setHighDimmingWarningHidden(hidden: Boolean) {
        context.dataStore.edit { it[KEY_HIGH_DIMMING_WARNING_HIDDEN] = hidden }
    }


    val colorPreviewIntensity: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLOR_PREVIEW_INTENSITY]?.coerceIn(MIN_DIM, MAX_SAVED_PREVIEW_DIM)
            ?: DEFAULT_PREVIEW_DIM
    }

    suspend fun setColorPreviewIntensity(level: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_COLOR_PREVIEW_INTENSITY] = level.coerceIn(MIN_DIM, MAX_SAVED_PREVIEW_DIM)
        }
    }

    val colorFilterExpanded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLOR_FILTER_EXPANDED] ?: false
    }

    suspend fun setColorFilterExpanded(expanded: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_COLOR_FILTER_EXPANDED] = expanded }
    }

    val overlayTintPreset: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_OVERLAY_TINT_PRESET].takeIf { it in OverlayTint.presetSlots } ?: OverlayTint.PRESET_GRAY
    }

    val customOverlayTintArgb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_OVERLAY_TINT] ?: OverlayTint.AmberArgb
    }

    val savedOverlayTintPresets: Flow<Map<String, Int>> = context.dataStore.data.map { prefs ->
        OVERLAY_TINT_SLOT_KEYS.mapNotNull { (preset, key) ->
            prefs[key]?.let { preset to it }
        }.toMap()
    }

    val resolvedOverlayTintArgb: Flow<Int> = context.dataStore.data.map(::resolveOverlayTintArgb)

    suspend fun saveOverlayTintPreset(preset: String, argb: Int) {
        val key = OVERLAY_TINT_SLOT_KEYS.getValue(preset)
        context.dataStore.edit { prefs ->
            prefs[key] = argb
        }
    }

    suspend fun resetOverlayTintPresets() {
        context.dataStore.edit { prefs ->
            OVERLAY_TINT_SLOT_KEYS.values.forEach { prefs.remove(it) }
            prefs.remove(KEY_CUSTOM_OVERLAY_TINT)
            prefs.remove(KEY_COLOR_PREVIEW_INTENSITY)
            prefs[KEY_OVERLAY_TINT_PRESET] = OverlayTint.PRESET_GRAY
        }
    }

    suspend fun setOverlayTintPreset(preset: String) {
        val normalized = when (preset) {
            OverlayTint.PRESET_GRAPHITE, OverlayTint.PRESET_BURGUNDY -> preset
            else -> OverlayTint.PRESET_GRAY
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_OVERLAY_TINT_PRESET] = normalized
        }
    }

    data class OverlaySettings(
        val defaultDimLevel: Int = FALLBACK_DEFAULT,
        val tintArgb: Int = OverlayTint.GrayArgb,
        val notificationMode: String = NOTIF_MINIMAL,
    )

    /** One consistent snapshot for the service, emitted only when relevant settings change. */
    val overlaySettings: Flow<OverlaySettings> = context.dataStore.data.map { prefs ->
        OverlaySettings(
            defaultDimLevel = prefs[KEY_DEFAULT_DIM]?.coerceIn(MIN_DIM, MAX_DIM) ?: FALLBACK_DEFAULT,
            tintArgb = resolveOverlayTintArgb(prefs),
            notificationMode = prefs[KEY_NOTIFICATION_MODE] ?: NOTIF_MINIMAL,
        )
    }.distinctUntilChanged()

    private fun resolveOverlayTintArgb(prefs: Preferences): Int {
        val preset = prefs[KEY_OVERLAY_TINT_PRESET].takeIf { it in OverlayTint.presetSlots } ?: OverlayTint.PRESET_GRAY
        val savedArgb = OVERLAY_TINT_SLOT_KEYS[preset]?.let { prefs[it] }
        return savedArgb ?: OverlayTint.resolveArgb(
            preset,
            prefs[KEY_CUSTOM_OVERLAY_TINT] ?: OverlayTint.AmberArgb,
        )
    }

    val appLanguage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE] ?: LANG_SYSTEM
    }

    suspend fun setAppLanguage(language: String) {
        val normalized = when (language) {
            LANG_CS, LANG_EN, LANG_ZH_CN -> language
            else -> LANG_SYSTEM
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_LANGUAGE] = normalized
        }
    }

    val notificationMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATION_MODE] ?: NOTIF_MINIMAL
    }

    suspend fun setNotificationMode(mode: String) {
        val normalized = when (mode) {
            NOTIF_INTERACTIVE -> NOTIF_INTERACTIVE
            else -> NOTIF_MINIMAL
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATION_MODE] = normalized
        }
    }


    val notificationWarningHidden: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATION_WARNING_HIDDEN] ?: false
    }

    suspend fun hideNotificationWarning() {
        context.dataStore.edit { prefs -> prefs[KEY_NOTIFICATION_WARNING_HIDDEN] = true }
    }

    val autoStartOnLaunch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_START] ?: false
    }

    suspend fun setAutoStartOnLaunch(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_START] = enabled
        }
    }

    suspend fun autoStartOnLaunchEnabled(): Boolean = autoStartOnLaunch.first()

    val privacyAcknowledged: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PRIVACY_ACK] ?: false
    }

    suspend fun setPrivacyAcknowledged(acknowledged: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRIVACY_ACK] = acknowledged
        }
    }

    val shutdownTimer: Flow<OverlayTimer> = context.dataStore.data.map { prefs ->
        OverlayTimer(
            enabled = prefs[KEY_TIMER_ENABLED] ?: false,
            hour = (prefs[KEY_TIMER_HOUR] ?: 7).coerceIn(0, 23),
            minute = (prefs[KEY_TIMER_MINUTE] ?: 0).coerceIn(0, 59),
            repeatDaily = prefs[KEY_TIMER_REPEAT] ?: false,
            nextTriggerAt = prefs[KEY_TIMER_NEXT] ?: 0,
            lastFiredDate = prefs[KEY_TIMER_LAST_DATE] ?: "",
        )
    }.distinctUntilChanged()

    suspend fun setShutdownTimer(timer: OverlayTimer) {
        context.dataStore.edit { prefs ->
            prefs[KEY_TIMER_ENABLED] = timer.enabled
            prefs[KEY_TIMER_HOUR] = timer.hour
            prefs[KEY_TIMER_MINUTE] = timer.minute
            prefs[KEY_TIMER_REPEAT] = timer.repeatDaily
            prefs[KEY_TIMER_NEXT] = timer.nextTriggerAt
            prefs[KEY_TIMER_LAST_DATE] = timer.lastFiredDate
        }
    }

    val startupTimer: Flow<OverlayTimer> = context.dataStore.data.map { prefs ->
        OverlayTimer(
            enabled = prefs[KEY_STARTUP_TIMER_ENABLED] ?: false,
            hour = (prefs[KEY_STARTUP_TIMER_HOUR] ?: 20).coerceIn(0, 23),
            minute = (prefs[KEY_STARTUP_TIMER_MINUTE] ?: 0).coerceIn(0, 59),
            repeatDaily = prefs[KEY_STARTUP_TIMER_REPEAT] ?: false,
            nextTriggerAt = prefs[KEY_STARTUP_TIMER_NEXT] ?: 0,
            lastFiredDate = prefs[KEY_STARTUP_TIMER_LAST_DATE] ?: "",
        )
    }.distinctUntilChanged()

    suspend fun setStartupTimer(timer: OverlayTimer) {
        context.dataStore.edit { prefs ->
            prefs[KEY_STARTUP_TIMER_ENABLED] = timer.enabled
            prefs[KEY_STARTUP_TIMER_HOUR] = timer.hour
            prefs[KEY_STARTUP_TIMER_MINUTE] = timer.minute
            prefs[KEY_STARTUP_TIMER_REPEAT] = timer.repeatDaily
            prefs[KEY_STARTUP_TIMER_NEXT] = timer.nextTriggerAt
            prefs[KEY_STARTUP_TIMER_LAST_DATE] = timer.lastFiredDate
        }
    }

    companion object {
        const val MIN_DIM = DimLevel.MIN
        const val MAX_DIM = DimLevel.MAX
        const val FALLBACK_DEFAULT = DimLevel.DEFAULT
        const val DEFAULT_PREVIEW_DIM = 20
        const val MAX_SAVED_PREVIEW_DIM = 50

        const val LANG_SYSTEM = "system"
        const val LANG_CS = "cs"
        const val LANG_EN = "en"
        const val LANG_ZH_CN = "zh-CN"

        const val NOTIF_MINIMAL = "minimal"
        const val NOTIF_INTERACTIVE = "interactive"

        private val KEY_COLOR_PREVIEW_INTENSITY = intPreferencesKey("color_preview_intensity")
        private val KEY_DEFAULT_DIM = intPreferencesKey("default_dim_level")
        private val KEY_HIGH_DIMMING_WARNING_HIDDEN = booleanPreferencesKey("high_dimming_warning_hidden")
        private val KEY_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_NOTIFICATION_MODE = stringPreferencesKey("notification_mode")
        private val KEY_NOTIFICATION_WARNING_HIDDEN = booleanPreferencesKey("notification_warning_hidden")
        private val KEY_TIMER_ENABLED = booleanPreferencesKey("shutdown_timer_enabled")
        private val KEY_TIMER_HOUR = intPreferencesKey("shutdown_timer_hour")
        private val KEY_TIMER_MINUTE = intPreferencesKey("shutdown_timer_minute")
        private val KEY_TIMER_REPEAT = booleanPreferencesKey("shutdown_timer_repeat")
        private val KEY_TIMER_NEXT = longPreferencesKey("shutdown_timer_next")
        private val KEY_TIMER_LAST_DATE = stringPreferencesKey("shutdown_timer_last_date")
        private val KEY_STARTUP_TIMER_ENABLED = booleanPreferencesKey("startup_timer_enabled")
        private val KEY_STARTUP_TIMER_HOUR = intPreferencesKey("startup_timer_hour")
        private val KEY_STARTUP_TIMER_MINUTE = intPreferencesKey("startup_timer_minute")
        private val KEY_STARTUP_TIMER_REPEAT = booleanPreferencesKey("startup_timer_repeat")
        private val KEY_STARTUP_TIMER_NEXT = longPreferencesKey("startup_timer_next")
        private val KEY_STARTUP_TIMER_LAST_DATE = stringPreferencesKey("startup_timer_last_date")
        private val KEY_AUTO_START = booleanPreferencesKey("auto_start_on_launch")
        private val KEY_COLOR_FILTER_EXPANDED = booleanPreferencesKey("color_filter_expanded")
        private val KEY_OVERLAY_TINT_PRESET = stringPreferencesKey("overlay_tint_preset")
        private val KEY_CUSTOM_OVERLAY_TINT = intPreferencesKey("custom_overlay_tint_argb")
        private val OVERLAY_TINT_SLOT_KEYS = OverlayTint.presetSlots.associateWith { preset ->
            intPreferencesKey("overlay_tint_slot_$preset")
        }
        private val KEY_PRIVACY_ACK = booleanPreferencesKey("privacy_acknowledged")
    }
}
