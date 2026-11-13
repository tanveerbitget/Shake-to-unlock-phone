package com.example.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.SettingsRepository
import com.example.data.ShakeSettings
import com.example.diagnostics.DiagnosticLogger
import com.example.sensor.ShakeDetector
import com.example.ui.WakeUnlockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

data class LiveTelemetry(
    val netG: Float = 0f,
    val thresholdG: Float = 0.51f,
    val shakeCount: Int = 0,
    val targetShakes: Int = 2,
    val lastTestTriggeredAtMs: Long = 0L
)

class ShakeAccessibilityService : AccessibilityService(), SensorEventListener {

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager

    private var accelerometer: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var partialWakeLock: PowerManager.WakeLock? = null

    private val shakeDetector = ShakeDetector()
    private var serviceScope: CoroutineScope? = null

    private var isAccelRegistered = false
    private var isProximityRegistered = false
    private var isScreenInteractive = true
    private var isPocketNear = false
    private var lastUiTelemetryUpdateMs = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    isPocketNear = false
                    shakeDetector.reset()
                    DiagnosticLogger.info("Screen", "Screen ON detected")
                    applyRuntimePolicy()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    shakeDetector.reset()
                    DiagnosticLogger.info("Screen", "Screen OFF detected")
                    applyRuntimePolicy()
                }
            }
        }
    }

    private var screenReceiverRegistered = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        DiagnosticLogger.init(applicationContext)
        settingsRepo = SettingsRepository.getInstance(applicationContext)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        isScreenInteractive = powerManager.isInteractive

        // Clear manual close flag when the user explicitly binds/enables the accessibility service
        if (settingsRepo.current().manuallyClosed) {
            settingsRepo.setManuallyClosed(false)
        }

        activeInstance = this
        _isConnected.value = true

        registerScreenReceiver()
        createNotificationChannel()

        serviceScope?.cancel()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                settingsRepo.settings.collect {
                    applyRuntimePolicy()
                }
            }
            scope.launch {
                _testModeActive.collect {
                    shakeDetector.reset()
                    applyRuntimePolicy()
                }
            }
        }

        DiagnosticLogger.info(
            "Service",
            "AccessibilityService connected (accel=${accelerometer?.name ?: "NONE"}, prox=${proximitySensor?.name ?: "NONE"})"
        )
        applyRuntimePolicy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally minimal: we do not inspect or log screen UI content.
    }

    override fun onInterrupt() {
        DiagnosticLogger.warn("Service", "AccessibilityService interrupted by system")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        DiagnosticLogger.warn("Service", "AccessibilityService unbound")
        cleanupResources()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cleanupResources()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val nearThreshold = min(maxRange, 5f)
            isPocketNear = event.values[0] < nearThreshold
            return
        }

        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val s = settingsRepo.current()
        if (s.manuallyClosed || !s.isMonitoringEnabled) return

        val inTestMode = _testModeActive.value
        val screenOn = isScreenInteractive

        val targetShakes = when {
            inTestMode -> s.shakesToLock
            screenOn -> {
                if (!s.shakeToLockEnabled) return
                s.shakesToLock
            }
            else -> {
                if (!s.shakeToUnlockEnabled) return
                s.shakesToUnlock
            }
        }

        val result = shakeDetector.onAccelerometerSample(
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            timestampNs = event.timestamp,
            thresholdG = s.thresholdG,
            targetShakes = targetShakes
        )

        if (inTestMode) {
            val now = System.currentTimeMillis()
            if (result != ShakeDetector.Result.NONE || (now - lastUiTelemetryUpdateMs) >= 40L) {
                lastUiTelemetryUpdateMs = now
                val prevTriggeredAt = _liveTelemetry.value.lastTestTriggeredAtMs
                _liveTelemetry.value = LiveTelemetry(
                    netG = shakeDetector.currentNetG,
                    thresholdG = s.thresholdG,
                    shakeCount = shakeDetector.currentShakeCount,
                    targetShakes = targetShakes,
                    lastTestTriggeredAtMs = if (result == ShakeDetector.Result.TRIGGERED) now else prevTriggeredAt
                )
            }
        }

        if (result == ShakeDetector.Result.TRIGGERED) {
            if (inTestMode) {
                triggerHaptic(s, durationMs = 28L)
                DiagnosticLogger.info("Calibration", "Test shake matched (${targetShakes}x @ ${String.format("%.2f", s.thresholdG)}G)")
            } else if (powerManager.isInteractive) {
                performLockInternal(s)
            } else {
                performWakeUnlockInternal(s)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun applyRuntimePolicy() {
        if (!::settingsRepo.isInitialized) return
        val s = settingsRepo.current()
        val active = s.isMonitoringEnabled && !s.manuallyClosed
        val inTestMode = _testModeActive.value
        val screenOn = powerManager.isInteractive
        isScreenInteractive = screenOn

        val shouldListenAccel = active && (
            inTestMode ||
                (screenOn && s.shakeToLockEnabled) ||
                (!screenOn && s.shakeToUnlockEnabled)
            )

        updateAccelerometerRegistration(shouldListenAccel)

        val shouldListenProx = active && !screenOn && s.shakeToUnlockEnabled && s.pocketGuardEnabled
        updateProximityRegistration(shouldListenProx)

        val shouldHoldWakeLock = active && !screenOn && s.shakeToUnlockEnabled && s.screenOffWakeLockEnabled
        updatePartialWakeLock(shouldHoldWakeLock)

        val shouldRunForeground = active && s.foregroundGuardEnabled
        updateForegroundGuard(shouldRunForeground, s)
    }

    private fun updateAccelerometerRegistration(enable: Boolean) {
        val sensor = accelerometer
        if (enable && sensor != null) {
            if (!isAccelRegistered) {
                // SENSOR_DELAY_UI (~60ms / 16-20Hz) catches light wrist flicks with ultra-low battery draw
                val ok = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
                isAccelRegistered = ok
                _isSensorListening.value = ok
                if (ok) {
                    DiagnosticLogger.info("Sensor", "Accelerometer listener started (SENSOR_DELAY_UI)")
                } else {
                    DiagnosticLogger.error("Sensor", "Failed to register accelerometer listener")
                }
            }
        } else {
            if (isAccelRegistered) {
                sensorManager.unregisterListener(this, sensor)
                isAccelRegistered = false
                _isSensorListening.value = false
                shakeDetector.reset()
                DiagnosticLogger.info("Sensor", "Accelerometer listener paused to save battery")
            }
        }
    }

    private fun updateProximityRegistration(enable: Boolean) {
        val sensor = proximitySensor ?: return
        if (enable) {
            if (!isProximityRegistered) {
                isProximityRegistered = sensorManager.registerListener(
                    this,
                    sensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        } else if (isProximityRegistered) {
            sensorManager.unregisterListener(this, sensor)
            isProximityRegistered = false
            isPocketNear = false
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun updatePartialWakeLock(enable: Boolean) {
        if (enable) {
            if (partialWakeLock == null) {
                partialWakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "ShakeLock::ScreenOffSensorGuard"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (partialWakeLock?.isHeld == false) {
                try {
                    partialWakeLock?.acquire()
                    _isPartialWakeLockHeld.value = true
                } catch (e: Exception) {
                    DiagnosticLogger.error("WakeLock", "Failed to acquire partial WakeLock", e)
                }
            }
        } else {
            if (partialWakeLock?.isHeld == true) {
                try {
                    partialWakeLock?.release()
                } catch (e: Exception) {
                    DiagnosticLogger.warn("WakeLock", "Error releasing partial WakeLock: ${e.message}")
                }
            }
            _isPartialWakeLockHeld.value = false
        }
    }

    private fun updateForegroundGuard(enable: Boolean, s: ShakeSettings) {
        if (enable) {
            val notification = buildGuardNotification(s)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        GUARD_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(GUARD_NOTIFICATION_ID, notification)
                }
                _isForegroundGuardActive.value = true
            } catch (e: Exception) {
                _isForegroundGuardActive.value = false
                DiagnosticLogger.error("Guard", "Unable to start foreground guard notification", e)
            }
        } else {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
                // Ignore if not running in foreground
            }
            _isForegroundGuardActive.value = false
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildGuardNotification(s: ShakeSettings): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val lockText = if (s.shakeToLockEnabled) "${s.shakesToLock}x lock" else "lock off"
        val unlockText = if (s.shakeToUnlockEnabled) "${s.shakesToUnlock}x unlock" else "unlock off"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_shake)
            .setContentTitle(getString(R.string.notification_guard_title))
            .setContentText("Active: $lockText • $unlockText • ${s.sensitivity}% sensitivity")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingOpen)
            .build()
    }

    fun triggerLockNowFromUi(): Boolean {
        val s = if (::settingsRepo.isInitialized) settingsRepo.current() else ShakeSettings()
        return performLockInternal(s)
    }

    private fun performLockInternal(s: ShakeSettings): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            DiagnosticLogger.error("ShakeAction", "Screen lock requires Android 9+ (API 28)")
            return false
        }
        val success = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        if (success) {
            triggerHaptic(s, durationMs = 28L)
            if (::settingsRepo.isInitialized) {
                settingsRepo.incrementLockCount()
            }
            DiagnosticLogger.info("ShakeAction", "Screen locked via Accessibility Global Action")
        } else {
            DiagnosticLogger.warn("ShakeAction", "GLOBAL_ACTION_LOCK_SCREEN returned false")
        }
        return success
    }

    @Suppress("DEPRECATION")
    private fun performWakeUnlockInternal(s: ShakeSettings) {
        if (s.pocketGuardEnabled && proximitySensor != null && isPocketNear) {
            DiagnosticLogger.info("PocketGuard", "Suppressed shake-to-unlock while proximity sensor covered")
            return
        }

        try {
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "ShakeLock::ScreenWakePulse"
            )
            wakeLock.acquire(2500L)
            triggerHaptic(s, durationMs = 35L)
            if (::settingsRepo.isInitialized) {
                settingsRepo.incrementUnlockCount()
            }
            DiagnosticLogger.info("ShakeAction", "Screen woken via Shake-to-Unlock")

            if (s.autoDismissSwipeLock) {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (km != null && !km.isKeyguardSecure) {
                    val intent = Intent(this, WakeUnlockActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.error("ShakeAction", "Failed to wake screen on shake", e)
        }
    }

    private fun triggerHaptic(s: ShakeSettings, durationMs: Long) {
        if (!s.hapticFeedbackEnabled) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.warn("Haptic", "Vibration failed: ${e.message}")
        }
    }

    fun performCompleteShutdown() {
        DiagnosticLogger.info("Lifecycle", "Shutting down ShakeAccessibilityService completely")
        cleanupResources()
        try {
            disableSelf()
        } catch (e: Exception) {
            DiagnosticLogger.error("Lifecycle", "disableSelf() threw exception", e)
        }
    }

    private fun cleanupResources() {
        serviceScope?.cancel()
        serviceScope = null
        if (::sensorManager.isInitialized) {
            try {
                sensorManager.unregisterListener(this)
            } catch (_: Exception) {
            }
        }
        isAccelRegistered = false
        isProximityRegistered = false
        _isSensorListening.value = false

        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (_: Exception) {
            }
            screenReceiverRegistered = false
        }

        updatePartialWakeLock(false)

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        _isForegroundGuardActive.value = false
        _isConnected.value = false
        if (activeInstance === this) {
            activeInstance = null
        }
    }

    companion object {
        private const val CHANNEL_ID = "shakelock_guard_channel"
        private const val GUARD_NOTIFICATION_ID = 4018

        @Volatile
        var activeInstance: ShakeAccessibilityService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _isSensorListening = MutableStateFlow(false)
        val isSensorListening: StateFlow<Boolean> = _isSensorListening.asStateFlow()

        private val _isPartialWakeLockHeld = MutableStateFlow(false)
        val isPartialWakeLockHeld: StateFlow<Boolean> = _isPartialWakeLockHeld.asStateFlow()

        private val _isForegroundGuardActive = MutableStateFlow(false)
        val isForegroundGuardActive: StateFlow<Boolean> = _isForegroundGuardActive.asStateFlow()

        private val _testModeActive = MutableStateFlow(false)
        val testModeActive: StateFlow<Boolean> = _testModeActive.asStateFlow()

        private val _liveTelemetry = MutableStateFlow(LiveTelemetry())
        val liveTelemetry: StateFlow<LiveTelemetry> = _liveTelemetry.asStateFlow()

        fun setTestMode(enabled: Boolean) {
            _testModeActive.value = enabled
            if (!enabled) {
                _liveTelemetry.value = LiveTelemetry()
            }
        }

        fun requestCompleteClose(context: Context) {
            val repo = SettingsRepository.getInstance(context)
            repo.setManuallyClosed(true)
            activeInstance?.performCompleteShutdown()
        }
    }
}
