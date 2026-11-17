package com.sambusek.fotonote

import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.ServiceCompat

/**
 * Servizio sempre attivo che osserva la galleria: quando la Fotocamera
 * salva una nuova foto (in DCIM), propone di annotarla.
 */
class PhotoWatcherService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var startSec = 0L
    private val seen = HashSet<Long>()

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // piccolo ritardo: la fotocamera finisce di scrivere il file
            handler.postDelayed({ check(uri) }, 800)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startSec = System.currentTimeMillis() / 1000 - 2
        Notifications.createChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(
            this, Notifications.SERVICE_ID, Notifications.serviceNotification(this), type
        )
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun check(uri: Uri?) {
        if (!MediaHelper.hasPhotoPermission(this)) return
        val id = uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }?.takeIf { it >= 0 }
        val photo = if (id != null) MediaHelper.cameraPhotoById(contentResolver, id)
        else MediaHelper.latestCameraPhoto(contentResolver, startSec)
        if (photo == null || photo.dateAddedSec < startSec) return
        if (!seen.add(photo.id)) return
        onNewPhoto(photo)
    }

    private fun onNewPhoto(photo: Photo) {
        Notifications.notifyNewPhoto(this, photo)
        if (Prefs.autoOpen(this) && Settings.canDrawOverlays(this)) {
            try {
                startActivity(
                    EditorActivity.intent(this, photo.uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // se il sistema blocca l'apertura resta comunque la notifica
            }
        }
    }
}
