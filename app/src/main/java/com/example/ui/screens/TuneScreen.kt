package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.data.SensitivityPreset
import com.example.data.ShakeSettings
import com.example.service.LiveTelemetry
import com.example.ui.components.LiveShakeTesterCard
import com.example.ui.components.SettingSwitchRow
import com.example.ui.components.ShakeCountSelector
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.ShakeLockTheme
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TuneScreen(
    settings: ShakeSettings,
    liveTelemetry: LiveTelemetry,
    testModeActive: Boolean,
    onToggleTestMode: (Boolean) -> Unit,
    onUpdateSettings: ((ShakeSettings) -> ShakeSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sensitivity Tuning Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Shake Sensitivity",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "%d%% • %.2fG",
                            settings.sensitivity,
                            settings.thresholdG
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Higher sensitivity lets you lock or unlock with an effortless light wrist flick.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = settings.sensitivity.toFloat(),
                    onValueChange = { raw ->
                        val next = raw.roundToInt().coerceIn(0, 100)
                        onUpdateSettings { it.copy(sensitivity = next) }
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sensitivity_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Heavy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Light (Recommended)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Feather",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SensitivityPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = settings.sensitivity == preset.sensitivityValue,
                            onClick = {
                                onUpdateSettings { it.copy(sensitivity = preset.sensitivityValue) }
                            },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
                        )
                    }
                }
            }
        }

        LiveShakeTesterCard(
            telemetry = liveTelemetry,
            testModeActive = testModeActive,
            onToggleTestMode = onToggleTestMode
        )

        // Custom Shake Gestures Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Custom Shake Gestures",
                    style = MaterialTheme.typography.titleMedium
                )

                SettingSwitchRow(
                    title = "Shake to Lock Screen",
                    subtitle = "Uses Accessibility Global Lock (preserves fingerprint & face unlock)",
                    checked = settings.shakeToLockEnabled,
                    testTag = "switch_shake_to_lock",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(shakeToLockEnabled = enabled) }
                    }
                )

                ShakeCountSelector(
                    label = "Shakes required to lock (default 2x)",
                    selectedCount = settings.shakesToLock,
                    enabled = settings.shakeToLockEnabled,
                    testTagPrefix = "lock_shakes",
                    onSelect = { count ->
                        onUpdateSettings { it.copy(shakesToLock = count) }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SettingSwitchRow(
                    title = "Shake to Unlock / Wake Screen",
                    subtitle = "Wakes display when screen is off without pressing power button",
                    checked = settings.shakeToUnlockEnabled,
                    testTag = "switch_shake_to_unlock",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(shakeToUnlockEnabled = enabled) }
                    }
                )

                ShakeCountSelector(
                    label = "Shakes required to unlock (default 2x)",
                    selectedCount = settings.shakesToUnlock,
                    enabled = settings.shakeToUnlockEnabled,
                    testTagPrefix = "unlock_shakes",
                    onSelect = { count ->
                        onUpdateSettings { it.copy(shakesToUnlock = count) }
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                SettingSwitchRow(
                    title = "Auto-Dismiss Swipe Lock",
                    subtitle = "Opens straight to home when no PIN/pattern lock is set",
                    checked = settings.autoDismissSwipeLock,
                    testTag = "switch_auto_dismiss_swipe",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(autoDismissSwipeLock = enabled) }
                    }
                )

                SettingSwitchRow(
                    title = "Haptic Vibration Feedback",
                    subtitle = "Short tactile pulse when a shake triggers lock or unlock",
                    checked = settings.hapticFeedbackEnabled,
                    testTag = "switch_haptic",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(hapticFeedbackEnabled = enabled) }
                    }
                )
            }
        }

        // System Resilience & Boot Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Auto-Start & Anti-Kill Guard",
                    style = MaterialTheme.typography.titleMedium
                )

                SettingSwitchRow(
                    title = "Auto-Start on Boot",
                    subtitle = "Automatically resume ShakeLock protection after phone restart",
                    checked = settings.autoStartOnBoot,
                    testTag = "switch_auto_start_boot",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(autoStartOnBoot = enabled) }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingSwitchRow(
                    title = "Persistent Foreground Guard",
                    subtitle = "Keeps a silent notification so Android never auto-closes ShakeLock",
                    checked = settings.foregroundGuardEnabled,
                    testTag = "switch_foreground_guard",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(foregroundGuardEnabled = enabled) }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingSwitchRow(
                    title = "Screen-Off Sensor WakeLock",
                    subtitle = "Keeps accelerometer awake in Android 10 Doze for instant shake-to-unlock",
                    checked = settings.screenOffWakeLockEnabled,
                    enabled = settings.shakeToUnlockEnabled,
                    testTag = "switch_screen_off_wakelock",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(screenOffWakeLockEnabled = enabled) }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingSwitchRow(
                    title = "Pocket Guard (Proximity)",
                    subtitle = "Prevents accidental screen wake when phone is inside a pocket or bag",
                    checked = settings.pocketGuardEnabled,
                    enabled = settings.shakeToUnlockEnabled,
                    testTag = "switch_pocket_guard",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(pocketGuardEnabled = enabled) }
                    }
                )
            }
        }

        // Display Theme Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingSwitchRow(
                    title = "Hardware Dark Theme",
                    subtitle = "OLED-friendly midnight slate theme with electric cyan accents",
                    checked = settings.useDarkTheme,
                    testTag = "switch_dark_theme",
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(useDarkTheme = enabled) }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun TuneScreenPreview() {
    ShakeLockTheme(darkTheme = true) {
        TuneScreen(
            settings = ShakeSettings(),
            liveTelemetry = LiveTelemetry(),
            testModeActive = false,
            onToggleTestMode = {},
            onUpdateSettings = {}
        )
    }
}
