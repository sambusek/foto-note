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

    fun userName(c: Context): String = sp(c).getString("user_name", "").orEmpty()
    fun setUserName(c: Context, v: String) = sp(c).edit().putString("user_name", v.trim()).apply()

    /** Il token sta in un file a parte, escluso dai backup (vedi res/xml/*_rules.xml). */
    private fun secret(c: Context) = c.getSharedPreferences("fotonote_secret", Context.MODE_PRIVATE)

    fun githubToken(c: Context): String {
        // spostamento dal file delle preferenze usato dalle versioni precedenti
        sp(c).getString("github_token", null)?.let { old ->
            secret(c).edit().putString("github_token", old).apply()
            sp(c).edit().remove("github_token").apply()
        }
        return secret(c).getString("github_token", "").orEmpty()
    }

    fun setGithubToken(c: Context, v: String) = secret(c).edit().putString("github_token", v.trim()).apply()

    fun autoUpdateCheck(c: Context) = sp(c).getBoolean("auto_update_check", true)
    fun setAutoUpdateCheck(c: Context, v: Boolean) = sp(c).edit().putBoolean("auto_update_check", v).apply()

    fun lastUpdateCheck(c: Context) = sp(c).getLong("last_update_check", 0L)
    fun setLastUpdateCheck(c: Context, v: Long) = sp(c).edit().putLong("last_update_check", v).apply()

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
