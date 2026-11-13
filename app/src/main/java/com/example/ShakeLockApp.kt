package com.example

import android.app.Application
import com.example.data.SettingsRepository
import com.example.diagnostics.DiagnosticLogger

class ShakeLockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.init(this)
        SettingsRepository.getInstance(this)
        DiagnosticLogger.info("App", "ShakeLock initialized (process start)")
    }
}
