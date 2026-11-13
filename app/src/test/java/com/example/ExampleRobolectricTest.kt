package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsRepository
import com.example.data.ShakeSettings
import com.example.diagnostics.DiagnosticLogger
import com.example.ui.theme.ShakeLockTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appNameMatchesShakeLock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ShakeLock", appName)
    }

    @Test
    fun settingsRepositoryPersistsCustomShakesAndBootAutoStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository.getInstance(context)

        repo.update {
            it.copy(
                sensitivity = 82,
                shakesToLock = 3,
                shakesToUnlock = 2,
                autoStartOnBoot = true
            )
        }

        val current = repo.current()
        assertEquals(82, current.sensitivity)
        assertEquals(3, current.shakesToLock)
        assertEquals(2, current.shakesToUnlock)
        assertTrue(current.autoStartOnBoot)
    }

    @Test
    fun diagnosticLoggerBuildsCopyableReport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DiagnosticLogger.init(context)
        DiagnosticLogger.info("UnitTest", "Sample diagnostic event")
        val report = DiagnosticLogger.runHealthScan(context, ShakeSettings())
        assertTrue(report.copyableText.contains("ShakeLock Diagnostic Report"))
        assertTrue(report.copyableText.contains("Sample diagnostic event"))
    }

    @Test
    fun navigationTabsAndCloseDialogRenderCorrectly() {
        composeTestRule.setContent {
            ShakeLockTheme(darkTheme = true) {
                ShakeLockRootScreen(
                    settings = ShakeSettings(),
                    status = com.example.ui.SystemStatusState(),
                    liveTelemetry = com.example.service.LiveTelemetry(),
                    testModeActive = false,
                    lockCount = 2,
                    unlockCount = 3,
                    logs = emptyList(),
                    report = null,
                    snackbarHostState = androidx.compose.material3.SnackbarHostState(),
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
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ShakeLock").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tune").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Shake Calibration").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_diagnose").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("copy_diagnostics_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("topbar_close_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_close_app_button").assertIsDisplayed()
    }

    @Test
    fun mainActivityLaunchesWithoutViewModelCrash() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertTrue(!activity.isFinishing)
        controller.pause().stop().destroy()
    }
}
