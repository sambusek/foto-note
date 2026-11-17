package com.sambusek.fotonote

import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.os.HandlerThread
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.ServiceCompat

/**
 * Servizio sempre attivo che osserva la galleria: quando la Fotocamera
 * salva una nuova foto (in DCIM), propone di annotarla.
 */
class PhotoWatcherService : Service() {

    // le interrogazioni alla Galleria girano su un thread di lavoro, non su quello dell'interfaccia
    private val worker = HandlerThread("pastashoot-watcher").apply { start() }
    private val handler = Handler(worker.looper)
    private var startSec = 0L
    private val seen = LinkedHashSet<Long>()

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // la fotocamera genera più eventi per foto: si aspetta che finisca di scrivere e si accorpano
            handler.removeCallbacksAndMessages(TOKEN)
            handler.postAtTime({ check(uri) }, TOKEN, SystemClock.uptimeMillis() + 800)
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
        worker.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        val TOKEN = Any()
    }

    private fun check(uri: Uri?) {
        if (!MediaHelper.hasPhotoPermission(this)) return
        val id = uri?.let { runCatching { ContentUris.parseId(it) }.getOrNull() }?.takeIf { it >= 0 }
        val photo = if (id != null) MediaHelper.cameraPhotoById(contentResolver, id)
        else MediaHelper.latestCameraPhoto(contentResolver, startSec)
        if (photo == null || photo.dateAddedSec < startSec) return
        if (!seen.add(photo.id)) return
        if (seen.size > 500) seen.remove(seen.first())
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
