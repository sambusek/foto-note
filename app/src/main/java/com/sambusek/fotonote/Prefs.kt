package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("fotonote", Context.MODE_PRIVATE)

    fun isMonitoring(c: Context) = sp(c).getBoolean("monitoring", false)
    fun setMonitoring(c: Context, v: Boolean) = sp(c).edit().putBoolean("monitoring", v).apply()

    fun autoOpen(c: Context) = sp(c).getBoolean("auto_open", false)
    fun setAutoOpen(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_open", v).apply()

    fun stylusOnly(c: Context) = sp(c).getBoolean("stylus_only", true)
    fun setStylusOnly(c: Context, v: Boolean) = sp(c).edit().putBoolean("stylus_only", v).apply()
}

object ServiceControl {
    fun start(c: Context) {
        ContextCompat.startForegroundService(c, Intent(c, PhotoWatcherService::class.java))
    }

    fun stop(c: Context) {
        c.stopService(Intent(c, PhotoWatcherService::class.java))
    }
}
