package com.example

import com.example.data.ShakeSettings
import com.example.sensor.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeDetectorTest {

    @Test
    fun gentleDoubleShakeTriggersWithDefaultLightThreshold() {
        var fakeTimeMs = 1_000L
        val detector = ShakeDetector { fakeTimeMs }
        val thresholdG = ShakeSettings.sensitivityToThresholdG(75) // ~0.51G

        // Initial resting gravity sample
        assertEquals(
            ShakeDetector.Result.NONE,
            detector.onAccelerometerSample(0f, 0f, 9.80665f, 0L, thresholdG, 2)
        )

        // Peak 1: gentle wrist flick (~0.65G linear impulse)
        fakeTimeMs += 60L
        assertEquals(
            ShakeDetector.Result.PEAK_PROGRESS,
            detector.onAccelerometerSample(7.5f, 0f, 9.80665f, 60_000_000L, thresholdG, 2)
        )

        // Sustained high acceleration should NOT double-count as Peak 2 without hysteresis release
        fakeTimeMs += 60L
        assertEquals(
            ShakeDetector.Result.NONE,
            detector.onAccelerometerSample(7.4f, 0f, 9.80665f, 120_000_000L, thresholdG, 2)
        )

        // Return near zero to re-arm hysteresis
        fakeTimeMs += 80L
        detector.onAccelerometerSample(0.2f, 0f, 9.80665f, 200_000_000L, thresholdG, 2)
        fakeTimeMs += 80L
        detector.onAccelerometerSample(0.1f, 0f, 9.80665f, 280_000_000L, thresholdG, 2)

        // Peak 2: second gentle wrist flick within 1150ms window
        fakeTimeMs += 80L
        assertEquals(
            ShakeDetector.Result.TRIGGERED,
            detector.onAccelerometerSample(-7.8f, 0f, 9.80665f, 360_000_000L, thresholdG, 2)
        )
    }

    @Test
    fun expiredWindowResetsPartialShakeCount() {
        var fakeTimeMs = 1_000L
        val detector = ShakeDetector { fakeTimeMs }
        val thresholdG = ShakeSettings.sensitivityToThresholdG(75)

        detector.onAccelerometerSample(0f, 0f, 9.80665f, 0L, thresholdG, 2)

        // First shake
        fakeTimeMs += 60L
        assertEquals(
            ShakeDetector.Result.PEAK_PROGRESS,
            detector.onAccelerometerSample(8.0f, 0f, 9.80665f, 60_000_000L, thresholdG, 2)
        )

        // Long pause (> 1150ms) at rest
        fakeTimeMs += 1_500L
        detector.onAccelerometerSample(0f, 0f, 9.80665f, 1_560_000_000L, thresholdG, 2)

        // Next shake should start fresh at 1/2 instead of triggering
        fakeTimeMs += 60L
        assertEquals(
            ShakeDetector.Result.PEAK_PROGRESS,
            detector.onAccelerometerSample(8.0f, 0f, 9.80665f, 1_620_000_000L, thresholdG, 2)
        )
    }

    @Test
    fun cooldownPreventsImmediateRetriggerAfterTrigger() {
        var fakeTimeMs = 1_000L
        val detector = ShakeDetector { fakeTimeMs }
        val thresholdG = ShakeSettings.sensitivityToThresholdG(75)

        detector.onAccelerometerSample(0f, 0f, 9.80665f, 0L, thresholdG, 1)

        // Trigger with 1x shake
        fakeTimeMs += 60L
        assertEquals(
            ShakeDetector.Result.TRIGGERED,
            detector.onAccelerometerSample(8.5f, 0f, 9.80665f, 60_000_000L, thresholdG, 1)
        )

        // Rest & try again 400ms later (still inside 1200ms cooldown)
        fakeTimeMs += 200L
        detector.onAccelerometerSample(0f, 0f, 9.80665f, 260_000_000L, thresholdG, 1)
        fakeTimeMs += 200L
        assertEquals(
            ShakeDetector.Result.NONE,
            detector.onAccelerometerSample(9.0f, 0f, 9.80665f, 460_000_000L, thresholdG, 1)
        )
    }
}
