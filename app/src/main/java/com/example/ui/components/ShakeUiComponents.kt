package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ShakeSettings
import com.example.diagnostics.CheckState
import com.example.service.LiveTelemetry
import com.example.ui.SystemStatusState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.EmeraldStatus
import com.example.ui.theme.JetBrainsMono
import java.util.Locale

@Composable
fun ShakeHeroStatusCard(
    settings: ShakeSettings,
    status: SystemStatusState,
    onToggleMonitoring: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isReady = status.accessibilityServiceConnected && settings.isMonitoringEnabled && !settings.manuallyClosed
    val isPaused = status.accessibilityServiceConnected && !settings.isMonitoringEnabled

    val ringColor by animateColorAsState(
        targetValue = when {
            isReady -> MaterialTheme.colorScheme.primary
            isPaused -> AmberWarning
            else -> CoralDanger
        },
        label = "HeroRingColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = ringColor.copy(alpha = 0.35f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ringColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(120f, 120f),
                        radius = 480f
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Animated Hardware Sensor Ring
                Box(
                    modifier = Modifier.size(84.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(84.dp)) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseRadius = size.minDimension * 0.42f
                        drawCircle(
                            color = ringColor.copy(alpha = if (isReady) 0.16f else 0.08f),
                            radius = baseRadius * (if (isReady) pulseScale else 1f),
                            center = center
                        )
                        drawCircle(
                            color = ringColor.copy(alpha = 0.25f),
                            radius = baseRadius * 0.85f,
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = if (isReady) 275f else if (isPaused) 140f else 60f,
                            useCenter = false,
                            topLeft = Offset(
                                center.x - baseRadius * 0.85f,
                                center.y - baseRadius * 0.85f
                            ),
                            size = Size(baseRadius * 1.7f, baseRadius * 1.7f),
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.Vibration,
                        contentDescription = "Shake sensor status",
                        tint = ringColor,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isReady -> "Shake Guard Active"
                            isPaused -> "Monitoring Paused"
                            status.accessibilityEnabledInSystem -> "Reconnecting Service"
                            else -> "Accessibility Setup Needed"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            isReady -> "${settings.shakesToLock}x lock • ${settings.shakesToUnlock}x unlock • ${settings.sensitivity}% light"
                            isPaused -> "Shake actions paused to save motion triggers"
                            else -> "Enable ShakeLock in Accessibility to lock & wake by shaking"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusChip(
                            label = if (status.sensorListening) "Sensor Live" else "Sensor Idle",
                            color = if (status.sensorListening) EmeraldStatus else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusChip(
                            label = if (status.foregroundGuardActive) "Anti-Kill On" else "Guard Off",
                            color = if (status.foregroundGuardActive) MaterialTheme.colorScheme.primary else AmberWarning
                        )
                    }
                }

                // Master Pause/Resume button
                FilledTonalButton(
                    onClick = { onToggleMonitoring(!settings.isMonitoringEnabled) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_monitoring_button"),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (settings.isMonitoringEnabled) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (settings.isMonitoringEnabled) "Pause monitoring" else "Resume monitoring"
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LiveShakeTesterCard(
    telemetry: LiveTelemetry,
    testModeActive: Boolean,
    onToggleTestMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val recentlyTriggered = testModeActive &&
        telemetry.lastTestTriggeredAtMs > 0L &&
        (now - telemetry.lastTestTriggeredAtMs) < 1400L

    val maxMeterG = 2.0f
    val animatedFill by animateFloatAsState(
        targetValue = (telemetry.netG / maxMeterG).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 600f),
        label = "GForceFill"
    )
    val thresholdRatio = (telemetry.thresholdG / maxMeterG).coerceIn(0.05f, 0.95f)
    val activeColor = when {
        recentlyTriggered -> EmeraldStatus
        telemetry.netG >= telemetry.thresholdG -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (testModeActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Sensors,
                        contentDescription = "Live G-force tester",
                        tint = if (testModeActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = "Live Shake Tester",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (testModeActive) {
                                "Gently flick phone — screen locks paused while testing"
                            } else {
                                "Test gentle wrist shakes without locking your screen"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { onToggleTestMode(!testModeActive) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("test_shake_mode_button")
                ) {
                    Text(if (testModeActive) "Stop" else "Test")
                }
            }

            if (testModeActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Motion: %.2f G  |  Trigger: %.2f G",
                            telemetry.netG,
                            telemetry.thresholdG
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = if (recentlyTriggered) EmeraldStatus else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (recentlyTriggered) {
                            "TRIGGERED!"
                        } else {
                            "Peaks: ${telemetry.shakeCount}/${telemetry.targetShakes}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (recentlyTriggered) EmeraldStatus else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                val markerColor = AmberWarning
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                ) {
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                    if (animatedFill > 0.01f) {
                        drawRoundRect(
                            color = activeColor,
                            size = Size(size.width * animatedFill, size.height),
                            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                        )
                    }
                    val markerX = size.width * thresholdRatio
                    drawLine(
                        color = markerColor,
                        start = Offset(markerX, 0f),
                        end = Offset(markerX, size.height),
                        strokeWidth = 3.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionSetupRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    buttonLabel: String,
    testTag: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (isGranted) "Enabled" else "Action needed",
            tint = if (isGranted) EmeraldStatus else AmberWarning,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isGranted) {
            OutlinedButton(
                onClick = onActionClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Text("Check")
            }
        } else {
            Button(
                onClick = onActionClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Text(buttonLabel)
            }
        }
    }
}

@Composable
fun ShakeCountSelector(
    label: String,
    selectedCount: Int,
    enabled: Boolean,
    testTagPrefix: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (ShakeSettings.MIN_SHAKES..ShakeSettings.MAX_SHAKES).forEach { count ->
                val isSelected = selectedCount == count
                val bg = when {
                    !enabled -> MaterialTheme.colorScheme.surfaceContainer
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
                val fg = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled) { onSelect(count) }
                        .testTag("${testTagPrefix}_$count"),
                    shape = RoundedCornerShape(12.dp),
                    color = bg,
                    border = if (isSelected && enabled) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    }
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (count == 1) "1x" else "${count}x",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun CheckStateBadge(state: CheckState) {
    val (icon, color) = when (state) {
        CheckState.PASS -> Icons.Filled.CheckCircle to EmeraldStatus
        CheckState.WARN -> Icons.Filled.Warning to AmberWarning
        CheckState.FAIL -> Icons.Filled.Error to CoralDanger
    }
    Icon(
        imageVector = icon,
        contentDescription = state.name,
        tint = color,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
fun CloseApplicationDialog(
    onConfirmClose: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = "Close application",
                tint = CoralDanger
            )
        },
        title = { Text("Close ShakeLock Completely?") },
        text = {
            Text(
                "This stops accelerometer monitoring, releases wake locks, removes the anti-kill notification, disables the active accessibility guard, and closes the app task completely."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralDanger,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_close_app_button")
            ) {
                Text("Close Completely")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
