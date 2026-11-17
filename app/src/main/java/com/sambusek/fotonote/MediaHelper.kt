package com.sambusek.fotonote

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class Photo(val id: Long, val uri: Uri, val dateAddedSec: Long)

object MediaHelper {
    /** Cartella in cui la Fotocamera Samsung salva gli scatti. */
    /**
     * Foto della fotocamera: qualsiasi cartella sotto DCIM (Camera, OpenCamera, ecc.),
     * tranne gli screenshot che alcuni telefoni salvano in DCIM/Screenshots.
     */
    private val CAMERA_SEL =
        "(${MediaStore.Images.Media.RELATIVE_PATH} LIKE 'DCIM/%' AND ${MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE '%Screenshot%')"

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun hasPhotoPermission(c: Context): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    }

    fun latestCameraPhoto(cr: ContentResolver, sinceSec: Long = 0): Photo? = query(
        cr,
        "$CAMERA_SEL AND ${MediaStore.Images.Media.DATE_ADDED} >= ?",
        arrayOf(sinceSec.toString())
    )

    fun cameraPhotoById(cr: ContentResolver, id: Long): Photo? = query(
        cr,
        "${MediaStore.Images.Media._ID} = ? AND $CAMERA_SEL",
        arrayOf(id.toString())
    )

    private fun query(cr: ContentResolver, selection: String, args: Array<String>): Photo? {
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        return try {
            cr.query(
                collection, projection, selection, args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                if (!c.moveToFirst()) return null
                val id = c.getLong(0)
                Photo(id, ContentUris.withAppendedId(collection, id), c.getLong(1))
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Foto della fotocamera aggiunte in un intervallo di tempo (secondi), in ordine di scatto. */
    fun cameraPhotosBetween(cr: ContentResolver, startSec: Long, endSec: Long?): List<Photo> {
        val out = ArrayList<Photo>()
        var sel = "$CAMERA_SEL AND ${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val args = arrayListOf(startSec.toString())
        if (endSec != null) {
            sel += " AND ${MediaStore.Images.Media.DATE_ADDED} <= ?"
            args += endSec.toString()
        }
        try {
            cr.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED),
                sel, args.toTypedArray(),
                "${MediaStore.Images.Media.DATE_ADDED} ASC, ${MediaStore.Images.Media._ID} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += Photo(id, ContentUris.withAppendedId(collection, id), c.getLong(1))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Tutte le foto (o solo quelle della fotocamera), più recenti prima. */
    fun allPhotos(cr: ContentResolver, onlyCamera: Boolean): List<Uri> {
        val out = ArrayList<Uri>()
        val sel = if (onlyCamera) CAMERA_SEL else null
        val args: Array<String>? = null
        try {
            cr.query(
                collection, arrayOf(MediaStore.Images.Media._ID), sel, args,
                "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext()) out += ContentUris.withAppendedId(collection, c.getLong(0))
            }
        } catch (_: Exception) {
        }
        return out
    }

    /** Momento dello scatto (ms): data della foto, altrimenti data di aggiunta, altrimenti adesso. */
    fun takenMillis(cr: ContentResolver, uri: Uri): Long = try {
        cr.query(
            uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED), null, null, null
        )?.use { c ->
            if (!c.moveToFirst()) null
            else c.getLong(0).takeIf { it > 0 } ?: c.getLong(1).takeIf { it > 0 }?.times(1000)
        } ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    fun displayName(cr: ContentResolver, uri: Uri): String? = try {
        cr.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
