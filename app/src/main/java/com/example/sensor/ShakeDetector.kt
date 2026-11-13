package com.example.sensor

import kotlin.math.sqrt

/**
 * Lightweight, zero-allocation accelerometer shake detector tuned for effortless wrist shakes.
 * Uses a time-delta-adaptive high-pass gravity filter and hysteresis peak counting so a single
 * bump never counts as multiple shakes while gentle double shakes trigger reliably.
 */
class ShakeDetector(
    private val timeProviderMs: () -> Long = { System.currentTimeMillis() }
) {
    enum class Result {
        NONE,
        PEAK_PROGRESS,
        TRIGGERED
    }

    private var gravityInitialized = false
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var lastTimestampNs = 0L

    private var peakArmed = true
    private var shakeCount = 0
    private var firstShakeMs = 0L
    private var lastPeakMs = 0L
    private var lastTriggerMs = 0L

    var currentNetG: Float = 0f
        private set

    var currentShakeCount: Int = 0
        private set

    fun reset() {
        gravityInitialized = false
        gx = 0f
        gy = 0f
        gz = 0f
        lastTimestampNs = 0L
        peakArmed = true
        shakeCount = 0
        firstShakeMs = 0L
        lastPeakMs = 0L
        currentNetG = 0f
        currentShakeCount = 0
    }

    fun onAccelerometerSample(
        x: Float,
        y: Float,
        z: Float,
        timestampNs: Long,
        thresholdG: Float,
        targetShakes: Int
    ): Result {
        val nowMs = timeProviderMs()

        if (!gravityInitialized) {
            gx = x
            gy = y
            gz = z
            lastTimestampNs = timestampNs
            gravityInitialized = true
            currentNetG = 0f
            return Result.NONE
        }

        // Dynamic high-pass alpha adapted to actual sensor sampling interval
        val dtSec = ((timestampNs - lastTimestampNs) / 1_000_000_000f).coerceIn(0.002f, 0.25f)
        lastTimestampNs = timestampNs
        val alpha = GRAVITY_TAU_SEC / (GRAVITY_TAU_SEC + dtSec)

        gx = alpha * gx + (1f - alpha) * x
        gy = alpha * gy + (1f - alpha) * y
        gz = alpha * gz + (1f - alpha) * z

        val lx = x - gx
        val ly = y - gy
        val lz = z - gz

        val linearAccel = sqrt((lx * lx + ly * ly + lz * lz).toDouble()).toFloat()
        val netG = linearAccel / GRAVITY_EARTH
        currentNetG = netG

        // Ignore shakes during post-trigger cooldown
        if (lastTriggerMs != 0L && (nowMs - lastTriggerMs) < COOLDOWN_MS) {
            currentShakeCount = 0
            return Result.NONE
        }

        val clampedTarget = targetShakes.coerceIn(1, 4)
        val windowMs = windowForShakes(clampedTarget)
        val releaseThresholdG = thresholdG * HYSTERESIS_RELEASE_RATIO

        // Reset stale partial sequence if window expired
        if (shakeCount > 0 && (nowMs - firstShakeMs) > windowMs) {
            shakeCount = 0
            currentShakeCount = 0
        }

        // Re-arm peak detector only after acceleration drops below hysteresis floor
        if (!peakArmed && netG <= releaseThresholdG) {
            peakArmed = true
        }

        if (peakArmed && netG >= thresholdG && (nowMs - lastPeakMs) >= MIN_PEAK_GAP_MS) {
            peakArmed = false
            lastPeakMs = nowMs

            if (shakeCount == 0 || (nowMs - firstShakeMs) > windowMs) {
                shakeCount = 1
                firstShakeMs = nowMs
            } else {
                shakeCount++
            }

            currentShakeCount = shakeCount

            if (shakeCount >= clampedTarget) {
                lastTriggerMs = nowMs
                shakeCount = 0
                currentShakeCount = 0
                return Result.TRIGGERED
            }
            return Result.PEAK_PROGRESS
        }

        return Result.NONE
    }

    companion object {
        private const val GRAVITY_EARTH = 9.80665f
        private const val GRAVITY_TAU_SEC = 0.18f
        private const val HYSTERESIS_RELEASE_RATIO = 0.52f
        private const val MIN_PEAK_GAP_MS = 125L
        private const val COOLDOWN_MS = 1200L

        fun windowForShakes(targetShakes: Int): Long = when (targetShakes.coerceIn(1, 4)) {
            1 -> 300L
            2 -> 1150L
            3 -> 1500L
            else -> 1850L
        }
    }
}
