package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ShakeSettings
import com.example.diagnostics.DiagnosticLogger
import com.example.ui.MainViewModel
import com.example.ui.SystemStatusState
import com.example.ui.components.CloseApplicationDialog
import com.example.ui.screens.ControlScreen
import com.example.ui.screens.DiagnoseScreen
import com.example.ui.screens.TuneScreen
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.ShakeLockTheme
import kotlinx.coroutines.launch

enum class ShakeLockTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    CONTROL("Control", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_control"),
    TUNE("Tune", Icons.Filled.Tune, Icons.Outlined.Tune, "nav_tune"),
    DIAGNOSE("Diagnose", Icons.Filled.BugReport, Icons.Outlined.BugReport, "nav_diagnose")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val status by viewModel.systemStatus.collectAsStateWithLifecycle()
            val liveTelemetry by viewModel.liveTelemetry.collectAsStateWithLifecycle()
            val testModeActive by viewModel.testModeActive.collectAsStateWithLifecycle()
            val lockCount by viewModel.lockCount.collectAsStateWithLifecycle()
            val unlockCount by viewModel.unlockCount.collectAsStateWithLifecycle()
            val logs by viewModel.logs.collectAsStateWithLifecycle()
            val report by viewModel.diagnosticReport.collectAsStateWithLifecycle()

            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            @Suppress("DEPRECATION")
            val clipboardManager = LocalClipboardManager.current

            val notificationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                viewModel.refreshSystemStatus()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (granted) "Guard notifications enabled"
                        else "Notification permission denied — guard indicator hidden"
                    )
                }
            }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshSystemStatus()
                    } else if (event == Lifecycle.Event.ON_STOP) {
                        if (viewModel.testModeActive.value) {
                            viewModel.setTestMode(false)
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            ShakeLockTheme(darkTheme = settings.useDarkTheme) {
                ShakeLockRootScreen(
                    settings = settings,
                    status = status,
                    liveTelemetry = liveTelemetry,
                    testModeActive = testModeActive,
                    lockCount = lockCount,
                    unlockCount = unlockCount,
                    logs = logs,
                    report = report,
                    snackbarHostState = snackbarHostState,
                    onToggleMonitoring = { enabled ->
                        viewModel.updateSettings {
                            it.copy(isMonitoringEnabled = enabled, manuallyClosed = false)
                        }
                    },
                    onToggleTestMode = viewModel::setTestMode,
                    onUpdateSettings = viewModel::updateSettings,
                    onOpenAccessibilitySettings = {
                        openAccessibilitySettings()
                    },
                    onRequestBatteryOptimization = {
                        openBatteryOptimizationSettings()
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onLockNowClick = {
                        val locked = viewModel.lockNow()
                        if (!locked) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Enable ShakeLock Accessibility Service first")
                            }
                        }
                    },
                    onRunDiagnostics = {
                        viewModel.runDiagnostics()
                    },
                    onCopyReport = { reportText ->
                        clipboardManager.setText(AnnotatedString(reportText))
                        scope.launch {
                            snackbarHostState.showSnackbar("Diagnostic report copied to clipboard")
                        }
                    },
                    onClearLogs = {
                        viewModel.clearLogs()
                        scope.launch {
                            snackbarHostState.showSnackbar("Diagnostic logs cleared")
                        }
                    },
                    onConfirmCloseApp = {
                        viewModel.closeApplicationCompletely()
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            DiagnosticLogger.error("Navigation", "Could not open Accessibility Settings", e)
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            DiagnosticLogger.warn("Navigation", "Direct battery dialog unsupported, falling back to settings list: ${e.message}")
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (ex: Exception) {
                DiagnosticLogger.error("Navigation", "Could not open Battery Optimization settings", ex)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShakeLockRootScreen(
    settings: ShakeSettings,
    status: SystemStatusState,
    liveTelemetry: com.example.service.LiveTelemetry,
    testModeActive: Boolean,
    lockCount: Int,
    unlockCount: Int,
    logs: List<com.example.diagnostics.DiagnosticLogEntry>,
    report: com.example.diagnostics.DiagnosticReport?,
    snackbarHostState: SnackbarHostState,
    onToggleMonitoring: (Boolean) -> Unit,
    onToggleTestMode: (Boolean) -> Unit,
    onUpdateSettings: ((ShakeSettings) -> ShakeSettings) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onLockNowClick: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onCopyReport: (String) -> Unit,
    onClearLogs: () -> Unit,
    onConfirmCloseApp: () -> Unit
) {
    var activeTab by remember { mutableStateOf(ShakeLockTab.CONTROL) }
    var showCloseDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = activeTab != ShakeLockTab.CONTROL) {
        activeTab = ShakeLockTab.CONTROL
    }

    if (showCloseDialog) {
        CloseApplicationDialog(
            onConfirmClose = {
                showCloseDialog = false
                onConfirmCloseApp()
            },
            onDismiss = { showCloseDialog = false }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (activeTab) {
                                ShakeLockTab.CONTROL -> "ShakeLock"
                                ShakeLockTab.TUNE -> "Shake Calibration"
                                ShakeLockTab.DIAGNOSE -> "Diagnostics"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                onRunDiagnostics()
                                activeTab = ShakeLockTab.DIAGNOSE
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("topbar_diagnose_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = "Run diagnostics"
                            )
                        }
                        IconButton(
                            onClick = { showCloseDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("topbar_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PowerSettingsNew,
                                contentDescription = "Close application completely",
                                tint = CoralDanger
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        ShakeLockTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab == ShakeLockTab.DIAGNOSE) onRunDiagnostics()
                                    activeTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        ShakeLockTab.entries.forEach { tab ->
                            val selected = activeTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (tab == ShakeLockTab.DIAGNOSE) onRunDiagnostics()
                                    activeTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 680.dp)) {
                        when (activeTab) {
                            ShakeLockTab.CONTROL -> ControlScreen(
                                settings = settings,
                                status = status,
                                liveTelemetry = liveTelemetry,
                                testModeActive = testModeActive,
                                lockCount = lockCount,
                                unlockCount = unlockCount,
                                onToggleMonitoring = onToggleMonitoring,
                                onToggleTestMode = onToggleTestMode,
                                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                                onRequestBatteryOptimization = onRequestBatteryOptimization,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onLockNowClick = onLockNowClick,
                                onOpenDiagnoseTab = {
                                    onRunDiagnostics()
                                    activeTab = ShakeLockTab.DIAGNOSE
                                },
                                onRequestCloseApp = { showCloseDialog = true }
                            )
                            ShakeLockTab.TUNE -> TuneScreen(
                                settings = settings,
                                liveTelemetry = liveTelemetry,
                                testModeActive = testModeActive,
                                onToggleTestMode = onToggleTestMode,
                                onUpdateSettings = onUpdateSettings
                            )
                            ShakeLockTab.DIAGNOSE -> DiagnoseScreen(
                                report = report,
                                logs = logs,
                                onRunDiagnostics = onRunDiagnostics,
                                onCopyReport = onCopyReport,
                                onClearLogs = onClearLogs
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ShakeLockRootPreview() {
    ShakeLockTheme(darkTheme = true) {
        ShakeLockRootScreen(
            settings = ShakeSettings(),
            status = SystemStatusState(
                accessibilityEnabledInSystem = true,
                accessibilityServiceConnected = true,
                batteryOptimizationIgnored = false,
                sensorListening = true,
                foregroundGuardActive = true
            ),
            liveTelemetry = com.example.service.LiveTelemetry(),
            testModeActive = false,
            lockCount = 12,
            unlockCount = 19,
            logs = emptyList(),
            report = null,
            snackbarHostState = remember { SnackbarHostState() },
            onToggleMonitoring = {},
            onToggleTestMode = {},
            onUpdateSettings = {},
            onOpenAccessibilitySettings = {},
            onRequestBatteryOptimization = {},
            onRequestNotificationPermission = {},
            onLockNowClick = {},
            onRunDiagnostics = {},
            onCopyReport = {},
            onClearLogs = {},
            onConfirmCloseApp = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 520)
@Composable
fun ShakeLockTabletLightPreview() {
    ShakeLockTheme(darkTheme = false) {
        ShakeLockRootScreen(
            settings = ShakeSettings(useDarkTheme = false),
            status = SystemStatusState(
                accessibilityEnabledInSystem = true,
                accessibilityServiceConnected = true,
                batteryOptimizationIgnored = true,
                sensorListening = true,
                foregroundGuardActive = true
            ),
            liveTelemetry = com.example.service.LiveTelemetry(),
            testModeActive = false,
            lockCount = 4,
            unlockCount = 7,
            logs = emptyList(),
            report = null,
            snackbarHostState = remember { SnackbarHostState() },
            onToggleMonitoring = {},
            onToggleTestMode = {},
            onUpdateSettings = {},
            onOpenAccessibilitySettings = {},
            onRequestBatteryOptimization = {},
            onRequestNotificationPermission = {},
            onLockNowClick = {},
            onRunDiagnostics = {},
            onCopyReport = {},
            onClearLogs = {},
            onConfirmCloseApp = {}
        )
    }
}
