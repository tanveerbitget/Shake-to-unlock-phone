package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.SettingsRepository
import com.example.diagnostics.DiagnosticLogger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        DiagnosticLogger.init(context.applicationContext)
        val repo = SettingsRepository.getInstance(context.applicationContext)
        val settings = repo.current()

        if (settings.autoStartOnBoot) {
            if (settings.manuallyClosed) {
                repo.setManuallyClosed(false)
            }
            val accEnabled = DiagnosticLogger.isAccessibilityEnabledInSystem(context)
            DiagnosticLogger.info(
                "BootReceiver",
                "Boot event ($action) handled — Auto-Start ON, AccessibilityEnabled=$accEnabled"
            )
        } else {
            DiagnosticLogger.info(
                "BootReceiver",
                "Boot event ($action) ignored — Auto-Start on Boot is disabled by user"
            )
        }
    }
}
