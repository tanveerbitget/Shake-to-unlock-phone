package com.example.data

enum class SensitivityPreset(
    val label: String,
    val sensitivityValue: Int,
    val description: String
) {
    FEATHER("Feather", 92, "Ultra-light wrist flick"),
    LIGHT("Light", 75, "Recommended for effortless daily use"),
    BALANCED("Balanced", 50, "Moderate shake, avoids pocket triggers"),
    FIRM("Firm", 25, "Deliberate strong shake only")
}

data class ShakeSettings(
    val isMonitoringEnabled: Boolean = true,
    val sensitivity: Int = 75,
    val shakeToLockEnabled: Boolean = true,
    val shakesToLock: Int = 2,
    val shakeToUnlockEnabled: Boolean = true,
    val shakesToUnlock: Int = 2,
    val autoStartOnBoot: Boolean = true,
    val foregroundGuardEnabled: Boolean = true,
    val screenOffWakeLockEnabled: Boolean = true,
    val pocketGuardEnabled: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,
    val autoDismissSwipeLock: Boolean = true,
    val useDarkTheme: Boolean = true,
    val manuallyClosed: Boolean = false
) {
    val thresholdG: Float
        get() = sensitivityToThresholdG(sensitivity)

    val matchingPreset: SensitivityPreset?
        get() = SensitivityPreset.entries.firstOrNull { it.sensitivityValue == sensitivity }

    companion object {
        const val MIN_SHAKES = 1
        const val MAX_SHAKES = 4

        fun sensitivityToThresholdG(sensitivity: Int): Float {
            val clamped = sensitivity.coerceIn(0, 100)
            val t = (100 - clamped) / 100f
            // 100 -> 0.32G (Featherlight), 75 -> 0.51G (Light default),
            // 50 -> 0.81G (Balanced), 25 -> 1.21G (Firm), 0 -> 1.72G (Heavy)
            return 0.32f + (t * t * 0.85f) + (t * 0.55f)
        }
    }
}
