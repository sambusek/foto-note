package com.sambusek.fotonote

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una foto consolidata di una sessione chiusa. */
class SessionItem(val src: String, val out: String, val name: String)

/**
 * Sessione di sopralluogo. Finché è aperta ([end] = 0) le foto sono quelle scattate
 * da [start] in poi e le annotazioni restano bozze; alla chiusura vengono consolidate
 * nella cartella Immagini/Pastashoot/[folder] ed elencate in [items].
 */
data class Session(
    val id: String,
    val name: String,
    val folder: String,
    val start: Long,
    val end: Long = 0L,
    val excluded: Set<String> = emptySet(),
    /** Foto scattate prima della sessione e aggiunte a mano. */
    val extra: List<String> = emptyList(),
    /** Layout delle note: i campi da compilare su ogni foto e dove stamparli. */
    val fields: List<NoteField> = emptyList(),
    val items: List<SessionItem> = emptyList(),
) {
    val open: Boolean get() = end == 0L
    val relPath: String get() = "Pictures/Pastashoot/$folder"
}

object SessionStore {
    private fun dir(c: Context) = File(c.filesDir, "sessioni").apply { mkdirs() }
    private fun file(c: Context, id: String) = File(dir(c), "$id.json")

    private fun parse(f: File): Session? = try {
        val o = JSONObject(f.readText())
        val ex = o.optJSONArray("excluded") ?: JSONArray()
        val xt = o.optJSONArray("extra") ?: JSONArray()
        val fl = o.optJSONArray("fields") ?: JSONArray()
        val it = o.optJSONArray("items") ?: JSONArray()
        Session(
            id = o.getString("id"),
            name = o.getString("name"),
            folder = o.getString("folder"),
            start = o.getLong("start"),
            end = o.optLong("end", 0L),
            excluded = (0 until ex.length()).map { i -> ex.getString(i) }.toSet(),
            extra = (0 until xt.length()).map { i -> xt.getString(i) },
            fields = (0 until fl.length()).mapNotNull { i ->
                val x = fl.getJSONObject(i)
                val pos = FieldPos.entries.firstOrNull { p -> p.name == x.optString("pos") } ?: FieldPos.BOTTOM
                NoteField(
                    x.getString("id"), x.getString("label"), pos, x.optBoolean("showLabel", true),
                    FieldKind.entries.firstOrNull { k -> k.name == x.optString("kind") } ?: FieldKind.INPUT,
                    x.optString("text", ""),
                )
            },
            items = (0 until it.length()).map { i ->
                val x = it.getJSONObject(i)
                SessionItem(x.getString("src"), x.getString("out"), x.getString("name"))
            },
        )
    } catch (e: Exception) {
        null
    }

    fun save(c: Context, s: Session) {
        val items = JSONArray()
        for (i in s.items) items.put(JSONObject().put("src", i.src).put("out", i.out).put("name", i.name))
        val fields = JSONArray()
        for (f in s.fields) fields.put(JSONObject().put("id", f.id).put("label", f.label).put("pos", f.pos.name).put("showLabel", f.showLabel)
                .put("kind", f.kind.name).put("text", f.text))
        val o = JSONObject()
            .put("fields", fields)
            .put("id", s.id).put("name", s.name).put("folder", s.folder)
            .put("start", s.start).put("end", s.end)
            .put("excluded", JSONArray(s.excluded.toList()))
            .put("extra", JSONArray(s.extra))
            .put("items", items)
        file(c, s.id).writeText(o.toString())
    }

    /** Tutte le sessioni, la più recente per prima. */
    fun all(c: Context): List<Session> =
        dir(c).listFiles()?.mapNotNull { parse(it) }?.sortedByDescending { it.start } ?: emptyList()

    fun get(c: Context, id: String): Session? = file(c, id).takeIf { it.exists() }?.let { parse(it) }

    /** La sessione in corso, se c'è (una sola alla volta). */
    fun active(c: Context): Session? = all(c).firstOrNull { it.open }

    fun remove(c: Context, id: String) {
        file(c, id).delete()
    }

    /** Nome cartella: YYMMDD_NOME, senza caratteri non ammessi nei nomi di file. */
    fun folderName(start: Long, name: String): String {
        val date = SimpleDateFormat("yyMMdd", Locale.ITALY).format(Date(start))
        val clean = name.trim()
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[\\\\/:*?\"<>|.]"), "")
            .take(40)
            .ifEmpty { "SOPRALLUOGO" }
        return "${date}_$clean"
    }

    fun create(c: Context, name: String): Session {
        val start = System.currentTimeMillis()
        val previous = all(c)
        val used = previous.map { it.folder }.toSet()
        val base = folderName(start, name)
        var folder = base
        var n = 2
        while (folder in used) folder = "${base}_${n++}"
        // il nuovo sopralluogo parte dal layout delle note dell'ultimo
        val s = Session(
            id = start.toString(), name = name.trim(), folder = folder, start = start,
            fields = previous.firstOrNull()?.fields ?: emptyList(),
        )
        save(c, s)
        return s
    }

    /**
     * Foto della sessione: prima quelle aggiunte a mano, poi quelle scattate
     * durante la sessione in ordine di scatto (comprese le escluse).
     */
    fun photos(c: Context, s: Session): List<Photo> {
        if (!MediaHelper.hasPhotoPermission(c)) return emptyList()
        val shot = MediaHelper.cameraPhotosBetween(
            c.contentResolver, s.start / 1000, if (s.open) null else s.end / 1000
        )
        val shotKeys = shot.map { it.uri.toString() }.toSet()
        val added = s.extra.distinct().filter { it !in shotKeys }.mapNotNull { u ->
            val uri = Uri.parse(u)
            val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return@mapNotNull null
            Photo(id, uri, 0L)
        }
        return added + shot
    }

    /** La sessione in corso, se [uri] è una foto scattata durante quella sessione. */
    fun sessionFor(c: Context, uri: Uri): Session? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val s = active(c) ?: return null
        if (uri.toString() in s.extra) return s
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        val p = MediaHelper.cameraPhotoById(c.contentResolver, id) ?: return null
        return if (p.dateAddedSec >= s.start / 1000) s else null
    }
}

/**
 * Operazioni lunghe sulle sessioni. Girano in un ambito proprio, così non si
 * interrompono se si esce dalla schermata; [busy] e [progress] ne mostrano lo stato.
 */
object SessionOps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var busy by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    /** Chiude la sessione: consolida ogni foto non esclusa nella cartella della sessione. */
    fun close(context: Context, id: String, onDone: (saved: Int, errors: Int) -> Unit) {
        if (busy != null) return
        val c = context.applicationContext
        busy = "Consolidamento delle foto…"; progress = 0f
        scope.launch {
            var errors = 0
            val items = ArrayList<SessionItem>()
            try {
                val s = SessionStore.get(c, id)
                if (s != null && s.open) {
                    val end = System.currentTimeMillis()
                    val photos = SessionStore.photos(c, s).filter { it.uri.toString() !in s.excluded }
                    photos.forEachIndexed { i, p ->
                        try {
                            val a = AnnotationStore.load(c, p.uri)
                            val (out, name) = AnnotationIO.exportTo(
                                c, p.uri, a?.strokes ?: emptyList(), a?.note ?: "", s.relPath,
                                s.fields,
                                AutoFields.resolve(
                                    s.fields, a?.values ?: emptyMap(), s.name,
                                    MediaHelper.takenMillis(c.contentResolver, p.uri),
                                ),
                            )
                            items += SessionItem(p.uri.toString(), out.toString(), name)
                        } catch (e: Throwable) {
                            errors++
                        }
                        progress = (i + 1f) / photos.size
                    }
                    if (photos.isEmpty()) SessionStore.remove(c, id)
                    else SessionStore.save(c, s.copy(end = end, items = items))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    busy = null
                    onDone(items.size, errors)
                }
            }
        }
    }

    /** Genera lo ZIP o il PDF di una sessione chiusa nella cache, pronto da condividere. */
    fun export(context: Context, id: String, pdf: Boolean, onDone: (File?) -> Unit) {
        if (busy != null) return
        val c = context.applicationContext
        busy = if (pdf) "Creazione del report PDF…" else "Creazione dello ZIP…"; progress = 0f
        scope.launch {
            var result: File? = null
            try {
                val s = SessionStore.get(c, id)
                if (s != null && s.items.isNotEmpty()) {
                    val dir = File(c.cacheDir, "share").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val f = File(dir, s.folder + if (pdf) ".pdf" else ".zip")
                    if (pdf) PdfReport.write(c, s, f) { progress = it }
                    else Exporter.zip(c, s, f) { progress = it }
                    result = f
                }
            } catch (e: Throwable) {
                result = null
            } finally {
                withContext(Dispatchers.Main) {
                    busy = null
                    onDone(result)
                }
            }
        }
    }

    /**
     * Abbandona una sessione in corso senza salvare nulla: elimina la sessione e le bozze
     * delle sue foto. Le foto originali restano dove sono.
     */
    fun discard(context: Context, s: Session) {
        val c = context.applicationContext
        for (p in SessionStore.photos(c, s)) {
            // le annotazioni già salvate fuori sessione (con una copia in Galleria) restano
            if (AnnotationStore.load(c, p.uri)?.outUri == null) AnnotationStore.remove(c, p.uri)
        }
        SessionStore.remove(c, s.id)
    }

    /** Elimina la sessione e le sue foto consolidate (gli originali della fotocamera restano). */
    fun delete(context: Context, s: Session) {
        val c = context.applicationContext
        for (i in s.items) runCatching { c.contentResolver.delete(Uri.parse(i.out), null, null) }
        SessionStore.remove(c, s.id)
    }
}
