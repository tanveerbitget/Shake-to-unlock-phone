package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.ShakeSettings
import com.example.service.LiveTelemetry
import com.example.ui.SystemStatusState
import com.example.ui.components.LiveShakeTesterCard
import com.example.ui.components.PermissionSetupRow
import com.example.ui.components.ShakeHeroStatusCard
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.ShakeLockTheme

@Composable
fun ControlScreen(
    settings: ShakeSettings,
    status: SystemStatusState,
    liveTelemetry: LiveTelemetry,
    testModeActive: Boolean,
    lockCount: Int,
    unlockCount: Int,
    onToggleMonitoring: (Boolean) -> Unit,
    onToggleTestMode: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onLockNowClick: () -> Unit,
    onOpenDiagnoseTab: () -> Unit,
    onRequestCloseApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ShakeHeroStatusCard(
            settings = settings,
            status = status,
            onToggleMonitoring = onToggleMonitoring
        )

        // Primary Quick Actions: Lock Now, Diagnose, Complete Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = onLockNowClick,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("quick_lock_now_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Lock screen now",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Lock Now", maxLines = 1)
            }

            FilledTonalButton(
                onClick = onOpenDiagnoseTab,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("quick_diagnose_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = "Open diagnostics",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Diagnose", maxLines = 1)
            }

            OutlinedButton(
                onClick = onRequestCloseApp,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralDanger),
                border = BorderStroke(1.dp, CoralDanger.copy(alpha = 0.55f)),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("close_app_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.PowerSettingsNew,
                    contentDescription = "Close application",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Close App", maxLines = 1)
            }
        }

        LiveShakeTesterCard(
            telemetry = liveTelemetry,
            testModeActive = testModeActive,
            onToggleTestMode = onToggleTestMode
        )

        // Permissions & System Auto-Close Protection
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System & Anti-Kill Setup",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Required on LG G8 / Android 10 so the system never stops shake detection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                PermissionSetupRow(
                    title = "Accessibility Service",
                    subtitle = if (status.accessibilityServiceConnected) {
                        "Active — Screen Lock & Wake ready"
                    } else {
                        "Turn ON 'ShakeLock' in Downloaded Services"
                    },
                    isGranted = status.accessibilityServiceConnected,
                    buttonLabel = "Enable",
                    testTag = "enable_accessibility_button",
                    onActionClick = onOpenAccessibilitySettings
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                PermissionSetupRow(
                    title = "Battery Optimization Guard",
                    subtitle = if (status.batteryOptimizationIgnored) {
                        "Unrestricted — Protected from Doze auto-close"
                    } else {
                        "Allow unrestricted background use on Android 10"
                    },
                    isGranted = status.batteryOptimizationIgnored,
                    buttonLabel = "Allow",
                    testTag = "battery_optimization_button",
                    onActionClick = onRequestBatteryOptimization
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PermissionSetupRow(
                        title = "Guard Notification Permission",
                        subtitle = if (status.notificationPermissionGranted) {
                            "Granted — Foreground keep-alive indicator active"
                        } else {
                            "Needed on Android 13+ for anti-kill guard notification"
                        },
                        isGranted = status.notificationPermissionGranted,
                        buttonLabel = "Grant",
                        testTag = "notification_permission_button",
                        onActionClick = onRequestNotificationPermission
                    )
                }
            }
        }

        // Compact Telemetry Summary
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Activity & Resource Profile",
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(
                        icon = Icons.Outlined.Lock,
                        label = "Locked",
                        value = lockCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Outlined.LockOpen,
                        label = "Woken",
                        value = unlockCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Outlined.RestartAlt,
                        label = "Boot Start",
                        value = if (settings.autoStartOnBoot) "ON" else "OFF",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        icon = Icons.Outlined.BatteryChargingFull,
                        label = "Sensor Rate",
                        value = "~16Hz",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ControlScreenPreview() {
    ShakeLockTheme(darkTheme = true) {
        ControlScreen(
            settings = ShakeSettings(),
            status = SystemStatusState(
                accessibilityEnabledInSystem = true,
                accessibilityServiceConnected = true,
                batteryOptimizationIgnored = true,
                sensorListening = true,
                foregroundGuardActive = true
            ),
            liveTelemetry = LiveTelemetry(netG = 0.64f, thresholdG = 0.51f, shakeCount = 1, targetShakes = 2),
            testModeActive = true,
            lockCount = 18,
            unlockCount = 24,
            onToggleMonitoring = {},
            onToggleTestMode = {},
            onOpenAccessibilitySettings = {},
            onRequestBatteryOptimization = {},
            onRequestNotificationPermission = {},
            onLockNowClick = {},
            onOpenDiagnoseTab = {},
            onRequestCloseApp = {}
        )
    }
}
