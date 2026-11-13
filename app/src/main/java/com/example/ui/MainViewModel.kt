package com.example.ui

import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SettingsRepository
import com.example.data.ShakeSettings
import com.example.diagnostics.DiagnosticLogEntry
import com.example.diagnostics.DiagnosticLogger
import com.example.diagnostics.DiagnosticReport
import com.example.sensor.ShakeDetector
import com.example.service.LiveTelemetry
import com.example.service.ShakeAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SystemStatusState(
    val accessibilityEnabledInSystem: Boolean = false,
    val accessibilityServiceConnected: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val sensorListening: Boolean = false,
    val foregroundGuardActive: Boolean = false,
    val partialWakeLockHeld: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val repo = SettingsRepository.getInstance(application)
    private val sensorManager: SensorManager? =
        application.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val fallbackDetector = ShakeDetector()
    private var isLocalFallbackSensorActive = false
    private var lastFallbackUpdateMs = 0L

    val settings: StateFlow<ShakeSettings> = repo.settings
    val lockCount: StateFlow<Int> = repo.lockCount
    val unlockCount: StateFlow<Int> = repo.unlockCount
    val logs: StateFlow<List<DiagnosticLogEntry>> = DiagnosticLogger.logs

    private val _systemPermissions = MutableStateFlow(readSystemPermissions())
    val systemStatus: StateFlow<SystemStatusState> = combine(
        _systemPermissions,
        ShakeAccessibilityService.isConnected,
        ShakeAccessibilityService.isSensorListening,
        ShakeAccessibilityService.isForegroundGuardActive,
        ShakeAccessibilityService.isPartialWakeLockHeld
    ) { perms, connected, listening, guard, wakeLock ->
        SystemStatusState(
            accessibilityEnabledInSystem = perms.first,
            accessibilityServiceConnected = connected,
            batteryOptimizationIgnored = perms.second,
            notificationPermissionGranted = perms.third,
            sensorListening = listening,
            foregroundGuardActive = guard,
            partialWakeLockHeld = wakeLock
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SystemStatusState()
    )

    private val _localTestTelemetry = MutableStateFlow(LiveTelemetry())
    val testModeActive: StateFlow<Boolean> = ShakeAccessibilityService.testModeActive

    val liveTelemetry: StateFlow<LiveTelemetry> = combine(
        ShakeAccessibilityService.isConnected,
        ShakeAccessibilityService.liveTelemetry,
        _localTestTelemetry
    ) { serviceConnected, serviceTelemetry, localTelemetry ->
        if (serviceConnected) serviceTelemetry else localTelemetry
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LiveTelemetry()
    )

    private val _diagnosticReport = MutableStateFlow<DiagnosticReport?>(null)
    val diagnosticReport: StateFlow<DiagnosticReport?> = _diagnosticReport.asStateFlow()

    init {
        runDiagnostics()
    }

    fun refreshSystemStatus() {
        _systemPermissions.value = readSystemPermissions()
        if (_diagnosticReport.value == null) {
            runDiagnostics()
        }
    }

    private fun readSystemPermissions(): Triple<Boolean, Boolean, Boolean> {
        val ctx = getApplication<Application>()
        return Triple(
            DiagnosticLogger.isAccessibilityEnabledInSystem(ctx),
            DiagnosticLogger.isBatteryOptimizationIgnored(ctx),
            DiagnosticLogger.hasNotificationPermission(ctx)
        )
    }

    fun updateSettings(transform: (ShakeSettings) -> ShakeSettings) {
        repo.update(transform)
    }

    fun setTestMode(enabled: Boolean) {
        ShakeAccessibilityService.setTestMode(enabled)
        if (enabled && !ShakeAccessibilityService.isConnected.value) {
            startLocalFallbackSensor()
        } else {
            stopLocalFallbackSensor()
        }
    }

    fun runDiagnostics(): DiagnosticReport {
        _systemPermissions.value = readSystemPermissions()
        val report = DiagnosticLogger.runHealthScan(getApplication(), repo.current())
        _diagnosticReport.value = report
        return report
    }

    fun clearLogs() {
        DiagnosticLogger.clear()
        runDiagnostics()
    }

    fun lockNow(): Boolean {
        val service = ShakeAccessibilityService.activeInstance
        return if (service != null) {
            service.triggerLockNowFromUi()
        } else {
            DiagnosticLogger.warn("UI", "Lock Now pressed while Accessibility Service is disconnected")
            false
        }
    }

    fun closeApplicationCompletely() {
        setTestMode(false)
        ShakeAccessibilityService.requestCompleteClose(getApplication())
    }

    private fun startLocalFallbackSensor() {
        val sm = sensorManager ?: return
        val sensor = accelerometer ?: return
        if (!isLocalFallbackSensorActive) {
            fallbackDetector.reset()
            isLocalFallbackSensorActive = sm.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    private fun stopLocalFallbackSensor() {
        if (isLocalFallbackSensorActive) {
            sensorManager?.unregisterListener(this)
            isLocalFallbackSensorActive = false
            fallbackDetector.reset()
            _localTestTelemetry.value = LiveTelemetry(thresholdG = repo.current().thresholdG)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        if (ShakeAccessibilityService.isConnected.value || !ShakeAccessibilityService.testModeActive.value) {
            stopLocalFallbackSensor()
            return
        }
        val s = repo.current()
        val result = fallbackDetector.onAccelerometerSample(
            x = event.values[0],
            y = event.values[1],
            z = event.values[2],
            timestampNs = event.timestamp,
            thresholdG = s.thresholdG,
            targetShakes = s.shakesToLock
        )
        val now = System.currentTimeMillis()
        if (result != ShakeDetector.Result.NONE || (now - lastFallbackUpdateMs) >= 40L) {
            lastFallbackUpdateMs = now
            val prevTriggeredAt = _localTestTelemetry.value.lastTestTriggeredAtMs
            _localTestTelemetry.value = LiveTelemetry(
                netG = fallbackDetector.currentNetG,
                thresholdG = s.thresholdG,
                shakeCount = fallbackDetector.currentShakeCount,
                targetShakes = s.shakesToLock,
                lastTestTriggeredAtMs = if (result == ShakeDetector.Result.TRIGGERED) now else prevTriggeredAt
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        setTestMode(false)
        stopLocalFallbackSensor()
        super.onCleared()
    }
}
