package com.sambusek.fotonote

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifications {
    private const val CH_SERVICE = "servizio"
    private const val CH_PHOTO = "nuove_foto"
    const val SERVICE_ID = 1

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Monitoraggio fotocamera", NotificationManager.IMPORTANCE_MIN)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PHOTO, "Nuove foto da annotare", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun serviceNotification(c: Context): Notification {
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(c, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_pen)
            .setContentTitle("Foto Note attivo")
            .setContentText("Dopo ogni scatto potrai annotare la foto")
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun notifyNewPhoto(c: Context, photo: Photo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val pi = PendingIntent.getActivity(
            c, photo.id.toInt(), EditorActivity.intent(c, photo.uri),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val thumb = try {
            c.contentResolver.loadThumbnail(photo.uri, Size(256, 256), null)
        } catch (e: Exception) {
            null
        }
        val b = NotificationCompat.Builder(c, CH_PHOTO)
            .setSmallIcon(R.drawable.ic_stat_pen)
            .setContentTitle("Nuova foto")
            .setContentText("Tocca per annotarla con l'S Pen")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup("foto_da_annotare")
        if (thumb != null) {
            b.setLargeIcon(thumb)
            b.setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumb).bigLargeIcon(null as android.graphics.Bitmap?))
        }
        NotificationManagerCompat.from(c).notify(1000 + (photo.id % 1_000_000).toInt(), b.build())
    }
}
