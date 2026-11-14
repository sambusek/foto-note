package com.sambusek.fotonote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Riavvia il monitoraggio dopo il riavvio del telefono o un aggiornamento dell'app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ok = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (ok && Prefs.isMonitoring(context) && MediaHelper.hasPhotoPermission(context)) {
            try {
                ServiceControl.start(context)
            } catch (_: Exception) {
            }
        }
    }
}
