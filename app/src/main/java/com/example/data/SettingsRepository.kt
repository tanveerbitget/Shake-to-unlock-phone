package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<ShakeSettings> = _settings.asStateFlow()

    private val _lockCount = MutableStateFlow(prefs.getInt(KEY_LOCK_COUNT, 0))
    val lockCount: StateFlow<Int> = _lockCount.asStateFlow()

    private val _unlockCount = MutableStateFlow(prefs.getInt(KEY_UNLOCK_COUNT, 0))
    val unlockCount: StateFlow<Int> = _unlockCount.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        _settings.value = readSettings()
        _lockCount.value = prefs.getInt(KEY_LOCK_COUNT, 0)
        _unlockCount.value = prefs.getInt(KEY_UNLOCK_COUNT, 0)
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun current(): ShakeSettings = _settings.value

    fun update(transform: (ShakeSettings) -> ShakeSettings) {
        val updated = transform(readSettings())
        writeSettings(updated)
        _settings.value = updated
    }

    fun incrementLockCount() {
        val next = prefs.getInt(KEY_LOCK_COUNT, 0) + 1
        prefs.edit().putInt(KEY_LOCK_COUNT, next).apply()
        _lockCount.value = next
    }

    fun incrementUnlockCount() {
        val next = prefs.getInt(KEY_UNLOCK_COUNT, 0) + 1
        prefs.edit().putInt(KEY_UNLOCK_COUNT, next).apply()
        _unlockCount.value = next
    }

    fun setManuallyClosed(closed: Boolean) {
        prefs.edit().putBoolean(KEY_MANUALLY_CLOSED, closed).apply()
        _settings.value = readSettings()
    }

    private fun readSettings(): ShakeSettings {
        return ShakeSettings(
            isMonitoringEnabled = prefs.getBoolean(KEY_MONITORING_ENABLED, true),
            sensitivity = prefs.getInt(KEY_SENSITIVITY, 75).coerceIn(0, 100),
            shakeToLockEnabled = prefs.getBoolean(KEY_SHAKE_TO_LOCK, true),
            shakesToLock = prefs.getInt(KEY_SHAKES_TO_LOCK, 2)
                .coerceIn(ShakeSettings.MIN_SHAKES, ShakeSettings.MAX_SHAKES),
            shakeToUnlockEnabled = prefs.getBoolean(KEY_SHAKE_TO_UNLOCK, true),
            shakesToUnlock = prefs.getInt(KEY_SHAKES_TO_UNLOCK, 2)
                .coerceIn(ShakeSettings.MIN_SHAKES, ShakeSettings.MAX_SHAKES),
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_BOOT, true),
            foregroundGuardEnabled = prefs.getBoolean(KEY_FOREGROUND_GUARD, true),
            screenOffWakeLockEnabled = prefs.getBoolean(KEY_SCREEN_OFF_WAKELOCK, true),
            pocketGuardEnabled = prefs.getBoolean(KEY_POCKET_GUARD, true),
            hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
            autoDismissSwipeLock = prefs.getBoolean(KEY_AUTO_DISMISS_KEYGUARD, true),
            useDarkTheme = prefs.getBoolean(KEY_USE_DARK_THEME, true),
            manuallyClosed = prefs.getBoolean(KEY_MANUALLY_CLOSED, false)
        )
    }

    private fun writeSettings(s: ShakeSettings) {
        prefs.edit()
            .putBoolean(KEY_MONITORING_ENABLED, s.isMonitoringEnabled)
            .putInt(KEY_SENSITIVITY, s.sensitivity.coerceIn(0, 100))
            .putBoolean(KEY_SHAKE_TO_LOCK, s.shakeToLockEnabled)
            .putInt(
                KEY_SHAKES_TO_LOCK,
                s.shakesToLock.coerceIn(ShakeSettings.MIN_SHAKES, ShakeSettings.MAX_SHAKES)
            )
            .putBoolean(KEY_SHAKE_TO_UNLOCK, s.shakeToUnlockEnabled)
            .putInt(
                KEY_SHAKES_TO_UNLOCK,
                s.shakesToUnlock.coerceIn(ShakeSettings.MIN_SHAKES, ShakeSettings.MAX_SHAKES)
            )
            .putBoolean(KEY_AUTO_START_BOOT, s.autoStartOnBoot)
            .putBoolean(KEY_FOREGROUND_GUARD, s.foregroundGuardEnabled)
            .putBoolean(KEY_SCREEN_OFF_WAKELOCK, s.screenOffWakeLockEnabled)
            .putBoolean(KEY_POCKET_GUARD, s.pocketGuardEnabled)
            .putBoolean(KEY_HAPTIC_FEEDBACK, s.hapticFeedbackEnabled)
            .putBoolean(KEY_AUTO_DISMISS_KEYGUARD, s.autoDismissSwipeLock)
            .putBoolean(KEY_USE_DARK_THEME, s.useDarkTheme)
            .putBoolean(KEY_MANUALLY_CLOSED, s.manuallyClosed)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "shakelock_prefs"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_SHAKE_TO_LOCK = "shake_to_lock"
        private const val KEY_SHAKES_TO_LOCK = "shakes_to_lock"
        private const val KEY_SHAKE_TO_UNLOCK = "shake_to_unlock"
        private const val KEY_SHAKES_TO_UNLOCK = "shakes_to_unlock"
        private const val KEY_AUTO_START_BOOT = "auto_start_boot"
        private const val KEY_FOREGROUND_GUARD = "foreground_guard"
        private const val KEY_SCREEN_OFF_WAKELOCK = "screen_off_wakelock"
        private const val KEY_POCKET_GUARD = "pocket_guard"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        private const val KEY_AUTO_DISMISS_KEYGUARD = "auto_dismiss_keyguard"
        private const val KEY_USE_DARK_THEME = "use_dark_theme"
        private const val KEY_MANUALLY_CLOSED = "manually_closed"
        private const val KEY_LOCK_COUNT = "lock_count"
        private const val KEY_UNLOCK_COUNT = "unlock_count"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
