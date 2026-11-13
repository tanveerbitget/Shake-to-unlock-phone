package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.diagnostics.CheckState
import com.example.diagnostics.DiagnosticLogEntry
import com.example.diagnostics.DiagnosticReport
import com.example.diagnostics.HealthCheckItem
import com.example.diagnostics.LogSeverity
import com.example.ui.components.CheckStateBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralDanger
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.ShakeLockTheme

private enum class LogFilter(val label: String) {
    ALL("All"),
    ERRORS("Errors"),
    WARNINGS("Warnings+")
}

@Composable
fun DiagnoseScreen(
    report: DiagnosticReport?,
    logs: List<DiagnosticLogEntry>,
    onRunDiagnostics: () -> Unit,
    onCopyReport: (String) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(LogFilter.ALL) }

    val filteredLogs = remember(logs, selectedFilter) {
        val list = when (selectedFilter) {
            LogFilter.ALL -> logs
            LogFilter.ERRORS -> logs.filter { it.severity == LogSeverity.ERROR }
            LogFilter.WARNINGS -> logs.filter {
                it.severity == LogSeverity.ERROR || it.severity == LogSeverity.WARN
            }
        }
        list.asReversed()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Primary Diagnostic Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onRunDiagnostics,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("run_diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Run diagnostics scan",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Scan")
            }

            Button(
                onClick = {
                    val text = report?.copyableText
                        ?: "No diagnostic scan run yet. Tap Scan first."
                    onCopyReport(text)
                },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("copy_diagnostics_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy diagnostic report",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("Copy Full Report", maxLines = 1)
            }

            OutlinedButton(
                onClick = onClearLogs,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("clear_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Clear logs",
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // System Health Checks
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "System Health Checks",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (report == null) {
                    Text(
                        text = "Tap Scan to inspect permissions, sensors, and runtime health.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    report.checks.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CheckStateBadge(state = item.state)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = item.detail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (index < report.checks.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        // Device & Hardware Summary
        if (report != null) {
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
                        text = "Device & Sensor Telemetry",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = report.deviceSummary,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = report.memorySummary,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = report.sensorSummary,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Event & Error Log Console
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
                        text = "Event & Error Logs (${filteredLogs.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("log_filter_${filter.name.lowercase()}")
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Terminal,
                                    contentDescription = "No logs",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "No matching logs recorded yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filteredLogs.take(80).forEach { entry ->
                                val lineColor = when (entry.severity) {
                                    LogSeverity.ERROR -> CoralDanger
                                    LogSeverity.WARN -> AmberWarning
                                    LogSeverity.INFO -> MaterialTheme.colorScheme.onSurface
                                }
                                Text(
                                    text = entry.toLine(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMono,
                                    fontWeight = if (entry.severity == LogSeverity.ERROR) FontWeight.Bold else FontWeight.Normal,
                                    color = lineColor
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun DiagnoseScreenPreview() {
    ShakeLockTheme(darkTheme = true) {
        DiagnoseScreen(
            report = DiagnosticReport(
                generatedAtMs = 0L,
                checks = listOf(
                    HealthCheckItem("Accessibility Service", "Bound and active", CheckState.PASS),
                    HealthCheckItem("Battery Optimization", "Unrestricted on Android 10", CheckState.PASS),
                    HealthCheckItem("Accelerometer Hardware", "BMI160 Accelerometer (0.18 mA)", CheckState.PASS)
                ),
                deviceSummary = "LGE LM-G820 (alphaplus) • Android 10 (SDK 29) • App v1.0",
                sensorSummary = "Accel: BMI160 | Power: 0.18mA | MinDelay: 2500us",
                memorySummary = "Heap Used: 14.2 MB / 256 MB Max | Sensor Active: true",
                copyableText = "Sample Report"
            ),
            logs = listOf(
                DiagnosticLogEntry(1700000000000L, LogSeverity.INFO, "Service", "AccessibilityService connected"),
                DiagnosticLogEntry(1700000005000L, LogSeverity.INFO, "ShakeAction", "Screen locked via GLOBAL_ACTION_LOCK_SCREEN")
            ),
            onRunDiagnostics = {},
            onCopyReport = {},
            onClearLogs = {}
        )
    }
}
