package com.sambusek.fotonote

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlin.math.roundToInt
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Annotazione salvata (per poterla riaprire e modificare). */
class SavedAnnotation(
    val note: String,
    val strokes: List<Stroke>,
    val outUri: Uri?,
    /** Valori dei campi del layout di sessione (id del campo → testo). */
    val values: Map<String, String> = emptyMap(),
    /** Spazio bianco aggiunto attorno alla foto (modalità tela). */
    val pad: Pad = Pad(),
)

object AnnotationStore {
    private fun key(src: Uri): String =
        MessageDigest.getInstance("SHA-1").digest(src.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun file(c: Context, src: Uri) =
        File(File(c.filesDir, "annotazioni").apply { mkdirs() }, key(src) + ".json")

    fun load(c: Context, src: Uri): SavedAnnotation? = try {
        val f = file(c, src)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            val arr = o.optJSONArray("strokes") ?: JSONArray()
            val strokes = (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                val p = s.getJSONArray("p")
                val pts = (0 until p.length() / 3).map { j ->
                    Pt(p.getDouble(j * 3).toFloat(), p.getDouble(j * 3 + 1).toFloat(), p.getDouble(j * 3 + 2).toFloat())
                }
                Stroke(s.getInt("c"), s.getDouble("w").toFloat(), pts)
            }
            SavedAnnotation(
                o.optString("note", ""),
                strokes,
                o.optString("out", "").takeIf { it.isNotEmpty() }?.let(Uri::parse),
                o.optJSONObject("values")?.let { v ->
                    v.keys().asSequence().associateWith { k -> v.optString(k, "") }
                } ?: emptyMap(),
                o.optJSONArray("pad")?.let { a ->
                    Pad(a.optDouble(0).toFloat(), a.optDouble(1).toFloat(), a.optDouble(2).toFloat(), a.optDouble(3).toFloat())
                } ?: Pad(),
            )
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Bozza: salva tratti e testi sulla foto senza produrre alcun file (staging di sessione).
     * Con [values] o [pad] nulli restano quelli già salvati.
     */
    fun saveDraft(
        c: Context, src: Uri, note: String, strokes: List<Stroke>,
        values: Map<String, String>? = null, pad: Pad? = null,
    ) = save(c, src, note, strokes, load(c, src)?.outUri, values, pad)

    /** Vero se sulla foto ci sono tratti o testi. */
    fun hasContent(c: Context, src: Uri): Boolean {
        val a = load(c, src) ?: return false
        return a.strokes.isNotEmpty() || a.note.isNotBlank() || a.values.values.any { it.isNotBlank() } || !a.pad.isZero
    }

    /** Annotazione così com'è salvata (per il pacchetto del sopralluogo). */
    fun rawJson(c: Context, src: Uri): JSONObject? =
        file(c, src).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }

    /** Scrive un'annotazione importata, legandola alla foto [src] di questo telefono. */
    fun writeRaw(c: Context, src: Uri, o: JSONObject) {
        o.put("source", src.toString()).put("out", "")
        file(c, src).writeText(o.toString())
    }

    fun remove(c: Context, src: Uri) {
        file(c, src).delete()
    }

    fun save(
        c: Context, src: Uri, note: String, strokes: List<Stroke>, out: Uri?,
        values: Map<String, String>? = null,
        pad: Pad? = null,
    ) {
        val old = if (values == null || pad == null) load(c, src) else null
        val vals = (values ?: old?.values ?: emptyMap()).filterValues { it.isNotBlank() }
        val pd = pad ?: old?.pad ?: Pad()
        val arr = JSONArray()
        for (s in strokes) {
            val p = JSONArray()
            for (pt in s.points) { p.put(pt.x.toDouble()); p.put(pt.y.toDouble()); p.put(pt.p.toDouble()) }
            arr.put(JSONObject().put("c", s.color).put("w", s.width.toDouble()).put("p", p))
        }
        val o = JSONObject()
            .put("source", src.toString())
            .put("out", out?.toString() ?: "")
            .put("note", note)
            .put("values", JSONObject(vals as Map<*, *>))
            .put("pad", JSONArray(listOf(pd.l.toDouble(), pd.t.toDouble(), pd.r.toDouble(), pd.b.toDouble())))
            .put("updated", System.currentTimeMillis())
            .put("strokes", arr)
        file(c, src).writeText(o.toString())
    }
}

object AnnotationIO {
    private const val MAX_SIDE = 4096

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
    )

    /** Carica la foto già ruotata correttamente, ridotta se oltre 4096 px. */
    fun loadBitmap(cr: ContentResolver, uri: Uri, maxSide: Int = MAX_SIDE): Bitmap {
        val src = ImageDecoder.createSource(cr, uri)
        return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val max = maxOf(w, h)
            if (max > maxSide) {
                val s = maxSide.toFloat() / max
                decoder.setTargetSize((w * s).toInt(), (h * s).toInt())
            }
        }
    }

    fun render(
        base: Bitmap, strokes: List<Stroke>, note: String = "",
        fields: List<NoteField> = emptyList(), values: Map<String, String> = emptyMap(),
        pad: Pad = Pad(),
    ): Bitmap {
        // tela: la foto più lo spazio bianco aggiunto attorno
        val (l, t) = pad.l.roundToInt() to pad.t.roundToInt()
        val cw = base.width + l + pad.r.roundToInt()
        val ch = base.height + t + pad.b.roundToInt()
        val overlay = Overlay.create(note, fields, values, cw, ch)
        val out = Bitmap.createBitmap(cw, overlay.topH + ch + overlay.bottomH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        // origine nell'angolo in alto a sinistra della tela
        canvas.translate(0f, overlay.topH.toFloat())
        if (!pad.isZero) canvas.drawRect(0f, 0f, cw.toFloat(), ch.toFloat(), Paint().apply { color = Color.WHITE })
        overlay.draw(canvas, ch.toFloat())
        // da qui in poi l'origine è l'angolo in alto a sinistra della foto
        canvas.translate(l.toFloat(), t.toFloat())
        canvas.drawBitmap(base, 0f, 0f, null)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (s in strokes) {
            paint.color = s.color
            dot.color = s.color
            val pts = s.points
            if (pts.size == 1) {
                canvas.drawCircle(pts[0].x, pts[0].y, pressureWidth(s.width, pts[0].p) / 2f, dot)
                continue
            }
            for (i in 1 until pts.size) {
                val a = pts[i - 1]
                val b = pts[i]
                paint.strokeWidth = pressureWidth(s.width, (a.p + b.p) / 2f)
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            }
        }
        return out
    }

    /**
     * Salva la copia annotata in Immagini/Pastashoot (l'originale non viene toccato),
     * scrive la nota nei metadati e memorizza tratti e nota per modifiche future.
     */
    fun save(c: Context, src: Uri, base: Bitmap, strokes: List<Stroke>, note: String, pad: Pad = Pad()): Uri {
        val cr = c.contentResolver
        val tmp = File(c.cacheDir, "annotata.jpg")
        val rendered = render(base, strokes, note, pad = pad)
        FileOutputStream(tmp).use { rendered.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        rendered.recycle()
        writeExif(c, src, tmp, note)

        val previous = AnnotationStore.load(c, src)?.outUri
        var target: Uri? = null
        if (previous != null) {
            target = try {
                cr.openOutputStream(previous, "wt")?.use { os -> tmp.inputStream().use { it.copyTo(os) } }
                previous
            } catch (e: Exception) {
                null // la copia precedente è stata cancellata: ne creo una nuova
            }
        }
        if (target == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outName(cr, src))
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Pastashoot")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            target = cr.insert(collection, values) ?: error("Impossibile creare il file")
            cr.openOutputStream(target, "w")?.use { os -> tmp.inputStream().use { it.copyTo(os) } }
        }
        val result: Uri = target!!
        val done = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
            put(MediaStore.Images.ImageColumns.DESCRIPTION, note)
        }
        try { cr.update(result, done, null, null) } catch (_: Exception) {}
        tmp.delete()
        AnnotationStore.save(c, src, note, strokes, result, pad = pad)
        return result
    }

    /**
     * Consolidamento di sessione: scrive in [relPath] la foto con tratti e nota.
     * Se non c'è nessuna annotazione copia l'originale così com'è (stessa qualità e metadati).
     * Restituisce l'URI del nuovo file e il suo nome.
     */
    fun exportTo(
        c: Context, src: Uri, strokes: List<Stroke>, note: String, relPath: String,
        fields: List<NoteField> = emptyList(), values: Map<String, String> = emptyMap(),
        pad: Pad = Pad(),
    ): Pair<Uri, String> {
        val desc = Overlay.describe(note, fields, values)
        val cr = c.contentResolver
        val orig = MediaHelper.displayName(cr, src)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(Date())
        val tmp = File(c.cacheDir, "export.tmp")
        val mime: String
        val name: String
        if (strokes.isEmpty() && desc.isEmpty() && pad.isZero) {
            mime = cr.getType(src) ?: "image/jpeg"
            name = orig ?: "$stamp.jpg"
            val hasLocation = ContextCompat.checkSelfPermission(
                c, Manifest.permission.ACCESS_MEDIA_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val input = (if (hasLocation && src.authority == MediaStore.AUTHORITY) {
                runCatching { cr.openInputStream(MediaStore.setRequireOriginal(src)) }.getOrNull()
            } else null) ?: cr.openInputStream(src) ?: error("Foto non leggibile")
            input.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
        } else {
            mime = "image/jpeg"
            name = (orig?.substringBeforeLast('.') ?: stamp) + ".jpg"
            val base = loadBitmap(cr, src)
            val rendered = render(base, strokes, note, fields, values, pad)
            base.recycle()
            FileOutputStream(tmp).use { rendered.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            rendered.recycle()
            writeExif(c, src, tmp, desc)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val target = cr.insert(collection, values) ?: error("Impossibile creare il file")
        try {
            cr.openOutputStream(target, "w")?.use { os -> tmp.inputStream().use { it.copyTo(os) } }
                ?: error("Impossibile scrivere il file")
        } catch (e: Exception) {
            runCatching { cr.delete(target, null, null) }
            throw e
        } finally {
            tmp.delete()
        }
        val done = ContentValues().apply {
            put(MediaStore.MediaColumns.IS_PENDING, 0)
            if (desc.isNotEmpty()) put(MediaStore.Images.ImageColumns.DESCRIPTION, desc)
        }
        try { cr.update(target, done, null, null) } catch (_: Exception) {}
        return target to (MediaHelper.displayName(cr, target) ?: name)
    }

    private fun outName(cr: ContentResolver, src: Uri): String {
        val orig = MediaHelper.displayName(cr, src)?.substringBeforeLast('.')
        val base = orig ?: SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(Date())
        return "${base}_nota.jpg"
    }

    private fun writeExif(c: Context, src: Uri, file: File, note: String) {
        val dst = ExifInterface(file)
        // copia data, modello e posizione GPS dall'originale
        val hasLocation = ContextCompat.checkSelfPermission(
            c, Manifest.permission.ACCESS_MEDIA_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val candidates = buildList {
            if (hasLocation && src.authority == MediaStore.AUTHORITY) {
                add(runCatching { MediaStore.setRequireOriginal(src) }.getOrNull())
            }
            add(src)
        }.filterNotNull()
        for (u in candidates) {
            val ok = runCatching {
                c.contentResolver.openInputStream(u)?.use { ins ->
                    val s = ExifInterface(ins)
                    for (tag in EXIF_TAGS) s.getAttribute(tag)?.let { dst.setAttribute(tag, it) }
                }
            }.isSuccess
            if (ok) break
        }
        dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        dst.setAttribute(ExifInterface.TAG_SOFTWARE, "Pastashoot")
        if (note.isNotBlank()) {
            val ascii = toAscii(note)
            dst.setAttribute(ExifInterface.TAG_IMAGE_DESCRIPTION, ascii)
            dst.setAttribute(ExifInterface.TAG_USER_COMMENT, ascii)
        }
        dst.saveAttributes()
    }

    /** I campi EXIF accettano solo ASCII: "è" diventa "e". */
    private fun toAscii(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .map { if (it.code in 32..126 || it == '\n') it else '?' }
            .joinToString("")
}
