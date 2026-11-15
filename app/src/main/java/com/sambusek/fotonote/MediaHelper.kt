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
    private const val CAMERA_PATH = "DCIM/Camera/"

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun hasPhotoPermission(c: Context): Boolean {
        val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
    }

    fun latestCameraPhoto(cr: ContentResolver, sinceSec: Long = 0): Photo? = query(
        cr,
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.DATE_ADDED} >= ?",
        arrayOf("$CAMERA_PATH%", sinceSec.toString())
    )

    fun cameraPhotoById(cr: ContentResolver, id: Long): Photo? = query(
        cr,
        "${MediaStore.Images.Media._ID} = ? AND ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
        arrayOf(id.toString(), "$CAMERA_PATH%")
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

    /** Tutte le foto (o solo quelle della fotocamera), più recenti prima. */
    fun allPhotos(cr: ContentResolver, onlyCamera: Boolean): List<Uri> {
        val out = ArrayList<Uri>()
        val sel = if (onlyCamera) "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" else null
        val args = if (onlyCamera) arrayOf("$CAMERA_PATH%") else null
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

    fun displayName(cr: ContentResolver, uri: Uri): String? = try {
        cr.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }
}
