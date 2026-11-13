package com.example.diagnostics

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.example.data.ShakeSettings
import com.example.service.ShakeAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogSeverity {
    INFO,
    WARN,
    ERROR
}

data class DiagnosticLogEntry(
    val timestampMs: Long,
    val severity: LogSeverity,
    val tag: String,
    val message: String
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(timestampMs))
    }

    fun toLine(): String = "${formattedTime()} [${severity.name}] $tag: $message"
}

enum class CheckState {
    PASS,
    WARN,
    FAIL
}

data class HealthCheckItem(
    val title: String,
    val detail: String,
    val state: CheckState
)

data class DiagnosticReport(
    val generatedAtMs: Long,
    val checks: List<HealthCheckItem>,
    val deviceSummary: String,
    val sensorSummary: String,
    val memorySummary: String,
    val copyableText: String
)

object DiagnosticLogger {

    private const val MAX_MEMORY_LOGS = 250
    private const val PERSISTED_PREFS = "shakelock_diag_prefs"
    private const val KEY_PERSISTED_ERRORS = "persisted_error_logs"
    private const val MAX_PERSISTED_ERRORS = 25

    private val lock = Any()
    private val buffer = ArrayDeque<DiagnosticLogEntry>(MAX_MEMORY_LOGS)
    private var appContext: Context? = null
    private var crashHandlerInstalled = false

    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        restorePersistedErrors()
        installCrashHandler()
    }

    fun info(tag: String, message: String) = append(LogSeverity.INFO, tag, message)

    fun warn(tag: String, message: String) = append(LogSeverity.WARN, tag, message)

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) {
            "$message | ${throwable.javaClass.simpleName}: ${throwable.message}\n${
                throwable.stackTraceToString().take(1200)
            }"
        } else {
            message
        }
        append(LogSeverity.ERROR, tag, fullMsg)
        persistErrorLine(fullMsg)
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _logs.value = emptyList()
        }
        appContext?.getSharedPreferences(PERSISTED_PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.remove(KEY_PERSISTED_ERRORS)
            ?.apply()
        info("Diagnostics", "Diagnostic logs cleared by user")
    }

    private fun append(severity: LogSeverity, tag: String, message: String) {
        val entry = DiagnosticLogEntry(
            timestampMs = System.currentTimeMillis(),
            severity = severity,
            tag = tag,
            message = message
        )
        synchronized(lock) {
            if (buffer.size >= MAX_MEMORY_LOGS) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
            _logs.value = buffer.toList()
        }
    }

    private fun persistErrorLine(message: String) {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PERSISTED_PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_PERSISTED_ERRORS, "") ?: ""
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val lines = existing.split("\n---\n").filter { it.isNotBlank() }.toMutableList()
        lines.add("[$stamp] $message")
        while (lines.size > MAX_PERSISTED_ERRORS) {
            lines.removeAt(0)
        }
        prefs.edit().putString(KEY_PERSISTED_ERRORS, lines.joinToString("\n---\n")).commit()
    }

    private fun restorePersistedErrors() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PERSISTED_PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PERSISTED_ERRORS, "").orEmpty()
        if (saved.isNotBlank()) {
            synchronized(lock) {
                if (buffer.isEmpty()) {
                    saved.split("\n---\n").filter { it.isNotBlank() }.forEach { line ->
                        buffer.addLast(
                            DiagnosticLogEntry(
                                timestampMs = System.currentTimeMillis(),
                                severity = LogSeverity.ERROR,
                                tag = "PreviousSession",
                                message = line
                            )
                        )
                    }
                    _logs.value = buffer.toList()
                }
            }
        }
    }

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                persistErrorLine(
                    "UNCAUGHT CRASH on thread '${thread.name}': ${throwable.stackTraceToString().take(1800)}"
                )
            } catch (_: Throwable) {
                // Never mask original crash
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun isAccessibilityEnabledInSystem(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val enabledServices =
            am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expectedPackage = context.packageName
        return enabledServices.any {
            it.resolveInfo?.serviceInfo?.packageName == expectedPackage &&
                it.resolveInfo?.serviceInfo?.name == ShakeAccessibilityService::class.java.name
        }
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun runHealthScan(context: Context, settings: ShakeSettings): DiagnosticReport {
        val checks = mutableListOf<HealthCheckItem>()

        val accEnabledSystem = isAccessibilityEnabledInSystem(context)
        val accConnected = ShakeAccessibilityService.isConnected.value
        checks += when {
            accEnabledSystem && accConnected -> HealthCheckItem(
                title = "Accessibility Service",
                detail = "Bound and active (Global Lock ready)",
                state = CheckState.PASS
            )
            accEnabledSystem -> HealthCheckItem(
                title = "Accessibility Service",
                detail = "Enabled in Settings, waiting for service bind (try toggling OFF/ON if stuck)",
                state = CheckState.WARN
            )
            else -> HealthCheckItem(
                title = "Accessibility Service",
                detail = "Disabled — required for screen lock & background shake engine",
                state = CheckState.FAIL
            )
        }

        val batteryIgnored = isBatteryOptimizationIgnored(context)
        checks += if (batteryIgnored) {
            HealthCheckItem(
                title = "Battery Optimization Guard",
                detail = "Unrestricted — system will not auto-close ShakeLock",
                state = CheckState.PASS
            )
        } else {
            HealthCheckItem(
                title = "Battery Optimization Guard",
                detail = "Optimized — Android 10 / OEM Doze may pause shakes while screen is off",
                state = CheckState.WARN
            )
        }

        val notifGranted = hasNotificationPermission(context)
        checks += if (notifGranted) {
            HealthCheckItem(
                title = "Foreground Guard Notification",
                detail = if (settings.foregroundGuardEnabled) {
                    "Permitted & enabled (prevents system kill)"
                } else {
                    "Permitted (currently disabled in Tune tab)"
                },
                state = if (settings.foregroundGuardEnabled) CheckState.PASS else CheckState.WARN
            )
        } else {
            HealthCheckItem(
                title = "Foreground Guard Notification",
                detail = "Notification permission denied — foreground keep-alive notification hidden",
                state = CheckState.WARN
            )
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val proximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        checks += if (accel != null) {
            HealthCheckItem(
                title = "Accelerometer Hardware",
                detail = "${accel.name} (${String.format(Locale.US, "%.2f", accel.power)} mA)",
                state = CheckState.PASS
            )
        } else {
            HealthCheckItem(
                title = "Accelerometer Hardware",
                detail = "No accelerometer sensor detected on this device",
                state = CheckState.FAIL
            )
        }

        checks += if (proximity != null) {
            HealthCheckItem(
                title = "Proximity Pocket Guard",
                detail = "${proximity.name} (maxRange=${proximity.maximumRange})",
                state = CheckState.PASS
            )
        } else {
            HealthCheckItem(
                title = "Proximity Pocket Guard",
                detail = "No proximity sensor found — Pocket Guard skipped automatically",
                state = CheckState.WARN
            )
        }

        val lockApiSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        checks += if (lockApiSupported) {
            HealthCheckItem(
                title = "OS Lock Screen API",
                detail = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) supports GLOBAL_ACTION_LOCK_SCREEN",
                state = CheckState.PASS
            )
        } else {
            HealthCheckItem(
                title = "OS Lock Screen API",
                detail = "API ${Build.VERSION.SDK_INT} < 28 — GLOBAL_ACTION_LOCK_SCREEN requires Android 9+",
                state = CheckState.FAIL
            )
        }

        val pkgInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
        val versionName = pkgInfo?.versionName ?: "1.0"
        val deviceSummary = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) • Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) • App v$versionName"

        val sensorSummary = buildString {
            if (accel != null) {
                append("Accel: ${accel.name} | Vendor: ${accel.vendor} | Power: ${accel.power}mA | MinDelay: ${accel.minDelay}us | MaxRange: ${accel.maximumRange}")
            } else {
                append("Accel: MISSING")
            }
            append("\n")
            if (proximity != null) {
                append("Proximity: ${proximity.name} | MaxRange: ${proximity.maximumRange}")
            } else {
                append("Proximity: None")
            }
        }

        val rt = Runtime.getRuntime()
        val usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
        val maxMb = rt.maxMemory() / (1024f * 1024f)
        val memorySummary = String.format(
            Locale.US,
            "Heap Used: %.1f MB / %.0f MB Max | Sensor Active: %s | WakeLock Held: %s",
            usedMb,
            maxMb,
            ShakeAccessibilityService.isSensorListening.value,
            ShakeAccessibilityService.isPartialWakeLockHeld.value
        )

        val currentLogs = _logs.value
        val reportText = buildString {
            appendLine("=== ShakeLock Diagnostic Report ===")
            appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Device: $deviceSummary")
            appendLine("Memory/Runtime: $memorySummary")
            appendLine("Sensors: $sensorSummary")
            appendLine()
            appendLine("--- Health Checks ---")
            checks.forEach {
                appendLine("[${it.state.name}] ${it.title}: ${it.detail}")
            }
            appendLine()
            appendLine("--- Active Settings ---")
            appendLine("MonitoringEnabled=${settings.isMonitoringEnabled}, ManuallyClosed=${settings.manuallyClosed}")
            appendLine("Sensitivity=${settings.sensitivity}% (Threshold=${String.format(Locale.US, "%.2f", settings.thresholdG)}G)")
            appendLine("ShakeToLock=${settings.shakeToLockEnabled} (${settings.shakesToLock}x), ShakeToUnlock=${settings.shakeToUnlockEnabled} (${settings.shakesToUnlock}x)")
            appendLine("BootAutoStart=${settings.autoStartOnBoot}, ForegroundGuard=${settings.foregroundGuardEnabled}, ScreenOffWakeLock=${settings.screenOffWakeLockEnabled}, PocketGuard=${settings.pocketGuardEnabled}, AutoDismissSwipe=${settings.autoDismissSwipeLock}, Haptic=${settings.hapticFeedbackEnabled}")
            appendLine()
            appendLine("--- Event & Error Logs (${currentLogs.size}) ---")
            if (currentLogs.isEmpty()) {
                appendLine("(No events logged yet)")
            } else {
                currentLogs.forEach { appendLine(it.toLine()) }
            }
        }

        info("Diagnostics", "Health scan completed (${checks.count { it.state == CheckState.FAIL }} failures, ${checks.count { it.state == CheckState.WARN }} warnings)")

        return DiagnosticReport(
            generatedAtMs = System.currentTimeMillis(),
            checks = checks,
            deviceSummary = deviceSummary,
            sensorSummary = sensorSummary,
            memorySummary = memorySummary,
            copyableText = reportText
        )
    }
}
