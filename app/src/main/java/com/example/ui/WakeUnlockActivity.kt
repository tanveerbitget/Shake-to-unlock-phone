package com.example.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.example.diagnostics.DiagnosticLogger

/**
 * Translucent zero-history helper Activity used only when the device has no secure lock
 * (Swipe / None) and "Auto-Dismiss Swipe Lock" is enabled. Wakes the display and dismisses
 * the non-secure keyguard, then immediately finishes itself.
 */
class WakeUnlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km != null && !km.isKeyguardSecure) {
                km.requestDismissKeyguard(
                    this,
                    object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            DiagnosticLogger.info("WakeUnlock", "Non-secure swipe keyguard dismissed")
                            finishSafely()
                        }

                        override fun onDismissCancelled() {
                            finishSafely()
                        }

                        override fun onDismissError() {
                            DiagnosticLogger.warn("WakeUnlock", "Keyguard dismiss error")
                            finishSafely()
                        }
                    }
                )
            } else {
                finishSafely()
            }
        } else {
            finishSafely()
        }
    }

    private fun finishSafely() {
        if (!isFinishing) {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
