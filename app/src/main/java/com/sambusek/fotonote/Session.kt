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

/**
 * Una foto consolidata: file in Galleria ([out]), nome, momento dello scatto e valori
 * dei campi (compresi quelli automatici) com'erano al momento del consolidamento.
 */
class SessionItem(
    val src: String,
    val out: String,
    val name: String,
    val taken: Long = 0L,
    val values: Map<String, String> = emptyMap(),
)

/** Un'emissione: le foto consolidate in una cartella in un certo momento. */
class Release(val label: String, val folder: String, val time: Long, val items: List<SessionItem>) {
    val relPath: String get() = "Pictures/Pastashoot/$folder"
}

/**
 * Sessione di sopralluogo. Finché è aperta ([end] = 0) le foto sono quelle scattate da [start]
 * in poi e le annotazioni restano bozze modificabili. Si possono emettere [versions] intermedie
 * (cartelle YYMMDD_NOME_vNN); il consolidamento definitivo salva in [folder] e chiude la sessione.
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
    /** Foto del consolidamento definitivo. */
    val items: List<SessionItem> = emptyList(),
    val versions: List<Release> = emptyList(),
    /** Ultimo schema usato per i nomi dei file nello ZIP. */
    val zipPattern: String = "",
) {
    val open: Boolean get() = end == 0L
    val finalRelease: Release? get() = if (open) null else Release("Definitiva", folder, end, items)
    /** Tutte le emissioni, la più recente per prima. */
    val releases: List<Release> get() = listOfNotNull(finalRelease) + versions.reversed()
}

// ------------------------------------------------------------------ JSON

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }

private fun JSONObject.stringMap(): Map<String, String> = keys().asSequence().associateWith { optString(it) }

private fun SessionItem.toJson() = JSONObject()
    .put("src", src).put("out", out).put("name", name).put("taken", taken)
    .put("values", JSONObject(values as Map<*, *>))

private fun itemFromJson(x: JSONObject) = SessionItem(
    x.getString("src"), x.getString("out"), x.getString("name"),
    x.optLong("taken", 0L), x.optJSONObject("values")?.stringMap().orEmpty(),
)

private fun itemsFromJson(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { itemFromJson(a.getJSONObject(it)) }

private fun List<SessionItem>.toJson() = JSONArray().also { a -> forEach { a.put(it.toJson()) } }

internal fun NoteField.toJson() = JSONObject()
    .put("id", id).put("label", label).put("pos", pos.name).put("showLabel", showLabel)
    .put("kind", kind.name).put("text", text).put("choices", choices)

internal fun fieldFromJson(x: JSONObject): NoteField {
    val choices = x.optString("choices", "")
    var kind = FieldKind.entries.firstOrNull { it.name == x.optString("kind") } ?: FieldKind.INPUT
    if (kind == FieldKind.INPUT && choices.isNotBlank()) kind = FieldKind.CHOICE // versioni precedenti dell'app
    return NoteField(
        x.getString("id"), x.getString("label"),
        FieldPos.entries.firstOrNull { it.name == x.optString("pos") } ?: FieldPos.BOTTOM,
        x.optBoolean("showLabel", true), kind, x.optString("text", ""), choices,
    )
}

private fun Session.toJson(): JSONObject {
    val fl = JSONArray().also { a -> fields.forEach { a.put(it.toJson()) } }
    val vs = JSONArray().also { a ->
        versions.forEach {
            a.put(JSONObject().put("label", it.label).put("folder", it.folder).put("time", it.time).put("items", it.items.toJson()))
        }
    }
    return JSONObject()
        .put("id", id).put("name", name).put("folder", folder)
        .put("start", start).put("end", end)
        .put("excluded", JSONArray(excluded.toList()))
        .put("extra", JSONArray(extra))
        .put("fields", fl)
        .put("items", items.toJson())
        .put("versions", vs)
        .put("zipPattern", zipPattern)
}

private fun sessionFromJson(o: JSONObject): Session {
    val fl = o.optJSONArray("fields")
    val vs = o.optJSONArray("versions")
    return Session(
        id = o.getString("id"),
        name = o.getString("name"),
        folder = o.getString("folder"),
        start = o.getLong("start"),
        end = o.optLong("end", 0L),
        excluded = o.optJSONArray("excluded").strings().toSet(),
        extra = o.optJSONArray("extra").strings(),
        fields = if (fl == null) emptyList() else (0 until fl.length()).map { fieldFromJson(fl.getJSONObject(it)) },
        items = itemsFromJson(o.optJSONArray("items")),
        versions = if (vs == null) emptyList() else (0 until vs.length()).map {
            val x = vs.getJSONObject(it)
            Release(x.getString("label"), x.getString("folder"), x.getLong("time"), itemsFromJson(x.optJSONArray("items")))
        },
        zipPattern = o.optString("zipPattern", ""),
    )
}

// ------------------------------------------------------------------ archivio

object SessionStore {
    private fun dir(c: Context) = File(c.filesDir, "sessioni").apply { mkdirs() }
    private fun file(c: Context, id: String) = File(dir(c), "$id.json")

    private fun parse(f: File): Session? = runCatching { sessionFromJson(JSONObject(f.readText())) }.getOrNull()

    fun save(c: Context, s: Session) = file(c, s.id).writeText(s.toJson().toString())

    /** Tutte le sessioni, la più recente per prima. */
    fun all(c: Context): List<Session> = dir(c).listFiles()?.mapNotNull(::parse)?.sortedByDescending { it.start }.orEmpty()

    fun get(c: Context, id: String): Session? = file(c, id).takeIf { it.exists() }?.let(::parse)

    /** La sessione in corso, se c'è (una sola alla volta). */
    fun active(c: Context): Session? = all(c).firstOrNull { it.open }

    fun remove(c: Context, id: String) {
        file(c, id).delete()
    }

    /** Nome cartella: YYMMDD_NOME, senza caratteri non ammessi nei nomi di file. */
    private fun folderName(start: Long, name: String): String {
        val date = SimpleDateFormat("yyMMdd", Locale.ITALY).format(Date(start))
        return "${date}_${FileNames.clean(name).take(40).ifEmpty { "SOPRALLUOGO" }}"
    }

    /** Nuova sessione, con i campi di un template oppure senza campi. */
    fun create(c: Context, name: String, fields: List<NoteField> = emptyList()): Session {
        val start = System.currentTimeMillis()
        val used = all(c).map { it.folder }.toSet()
        val base = folderName(start, name)
        var folder = base
        var n = 2
        while (folder in used) folder = "${base}_${n++}"
        return Session(id = start.toString(), name = name.trim(), folder = folder, start = start, fields = fields)
            .also { save(c, it) }
    }

    /**
     * Foto della sessione: prima quelle aggiunte a mano, poi quelle scattate
     * durante la sessione in ordine di scatto (comprese le escluse).
     */
    fun photos(c: Context, s: Session): List<Photo> {
        if (!MediaHelper.hasPhotoPermission(c)) return emptyList()
        val shot = MediaHelper.cameraPhotosBetween(c.contentResolver, s.start / 1000, if (s.open) null else s.end / 1000)
        val shotKeys = shot.map { it.uri.toString() }.toSet()
        val added = s.extra.distinct().filter { it !in shotKeys }.mapNotNull { u ->
            val uri = Uri.parse(u)
            runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { Photo(it, uri, 0L) }
        }
        return added + shot
    }

    /** La sessione in corso, se [uri] è una foto di quella sessione. */
    fun sessionFor(c: Context, uri: Uri): Session? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val s = active(c) ?: return null
        if (uri.toString() in s.extra) return s
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        val p = MediaHelper.cameraPhotoById(c.contentResolver, id) ?: return null
        return if (p.dateAddedSec >= s.start / 1000) s else null
    }
}

// ------------------------------------------------------------------ nomi dei file nello ZIP

/**
 * Nomi dei file composti da uno schema con segnaposto tra graffe, es. "{N}_{Piano}_{Stanza}".
 * Segnaposto: i campi del layout (per nome) e quelli di [BUILT_IN].
 */
object FileNames {
    val BUILT_IN = listOf("N", "Originale", "Sopralluogo", "Utente", "Data", "Ora")
    const val DEFAULT = "{Originale}"

    /** Toglie i caratteri non ammessi nei nomi di file e sostituisce gli spazi con "_". */
    fun clean(s: String) = s.trim().replace(Regex("\\s+"), "_").replace(Regex("[\\\\/:*?\"<>|]"), "")

    /** Segnaposto disponibili per una sessione. */
    fun tokens(s: Session): List<String> =
        BUILT_IN + s.fields.map { it.label.trim().ifEmpty { it.kind.defaultLabel } }.filter { it.isNotEmpty() }.distinct()

    fun build(pattern: String, s: Session, release: Release, user: String): List<String> {
        val digits = maxOf(2, release.items.size.toString().length)
        val dateFmt = SimpleDateFormat("yyMMdd", Locale.ITALY)
        val timeFmt = SimpleDateFormat("HHmm", Locale.ITALY)
        val byLabel = s.fields.associateBy { it.label.trim().ifEmpty { it.kind.defaultLabel }.lowercase() }
        return release.items.mapIndexed { i, item ->
            val ext = item.name.substringAfterLast('.', "jpg")
            val taken = Date(if (item.taken > 0) item.taken else release.time)
            val name = Regex("\\{([^}]*)\\}").replace(pattern.ifBlank { DEFAULT }) { m ->
                val key = m.groupValues[1].trim()
                when (key.lowercase()) {
                    "n" -> (i + 1).toString().padStart(digits, '0')
                    "originale" -> item.name.substringBeforeLast('.')
                    "sopralluogo" -> s.name
                    "utente" -> user
                    "data" -> dateFmt.format(taken)
                    "ora" -> timeFmt.format(taken)
                    else -> byLabel[key.lowercase()]?.let { item.values[it.id] }.orEmpty()
                }
            }
            val base = clean(name).replace(Regex("_+"), "_").trim('_', '-', ' ')
            "${base.ifEmpty { "foto_" + (i + 1).toString().padStart(digits, '0') }}.$ext"
        }
    }
}

// ------------------------------------------------------------------ operazioni

/**
 * Operazioni lunghe sulle sessioni. Girano in un ambito proprio, così non si interrompono
 * se si esce dalla schermata; [busy] e [progress] ne mostrano lo stato.
 */
object SessionOps {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var busy by mutableStateOf<String?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    /** Esegue [work] in background mostrando [message]; [done] gira sul thread principale. */
    private fun <T> launchOp(message: String, work: suspend () -> T, done: (T?) -> Unit) {
        if (busy != null) return
        busy = message; progress = 0f
        scope.launch {
            val r = runCatching { work() }.getOrNull()
            withContext(Dispatchers.Main) { busy = null; done(r) }
        }
    }

    /** Salva ogni foto non esclusa, con disegni e testi attuali, in [relPath]. */
    private fun consolidate(c: Context, s: Session, relPath: String): Pair<List<SessionItem>, Int> {
        val user = Prefs.userName(c)
        val photos = SessionStore.photos(c, s).filter { it.uri.toString() !in s.excluded }
        val items = ArrayList<SessionItem>()
        var errors = 0
        photos.forEachIndexed { i, p ->
            runCatching {
                val a = AnnotationStore.load(c, p.uri)
                val taken = MediaHelper.takenMillis(c.contentResolver, p.uri)
                val values = AutoFields.resolve(s.fields, a?.values.orEmpty(), s.name, taken, user)
                val (out, name) = AnnotationIO.exportTo(c, p.uri, a?.strokes.orEmpty(), a?.note.orEmpty(), relPath, s.fields, values)
                items += SessionItem(p.uri.toString(), out.toString(), name, taken, values)
            }.onFailure { errors++ }
            progress = (i + 1f) / photos.size
        }
        return items to errors
    }

    /** Emette una versione intermedia: la sessione resta aperta e modificabile. */
    fun emitVersion(context: Context, id: String, onDone: (Release?, Int) -> Unit) {
        val c = context.applicationContext
        var errors = 0
        launchOp("Emissione della versione…", {
            val s = SessionStore.get(c, id)!!
            val n = s.versions.size + 1
            val folder = "${s.folder}_v" + n.toString().padStart(2, '0')
            val (items, e) = consolidate(c, s, "Pictures/Pastashoot/$folder")
            errors = e
            if (items.isEmpty()) null
            else Release("Versione $n", folder, System.currentTimeMillis(), items).also {
                // rilettura: nel frattempo la sessione potrebbe essere cambiata
                val now = SessionStore.get(c, id) ?: s
                SessionStore.save(c, now.copy(versions = now.versions + it))
            }
        }) { onDone(it, errors) }
    }

    /** Consolidamento definitivo: salva nella cartella della sessione e la chiude. */
    fun close(context: Context, id: String, onDone: (saved: Int, errors: Int) -> Unit) {
        val c = context.applicationContext
        launchOp("Consolidamento definitivo…", {
            val s = SessionStore.get(c, id)!!
            val (items, errors) = consolidate(c, s, "Pictures/Pastashoot/${s.folder}")
            if (items.isEmpty() && errors == 0 && s.versions.isEmpty()) SessionStore.remove(c, id)
            else SessionStore.save(c, s.copy(end = System.currentTimeMillis(), items = items))
            items.size to errors
        }) { r -> onDone(r?.first ?: 0, r?.second ?: 0) }
    }

    /** Genera lo ZIP (con i nomi da [pattern]) o il PDF di un'emissione, pronto da condividere. */
    fun export(context: Context, id: String, folder: String, pdf: Boolean, pattern: String, onDone: (File?) -> Unit) {
        val c = context.applicationContext
        launchOp(if (pdf) "Creazione del report PDF…" else "Creazione dello ZIP…", {
            val s = SessionStore.get(c, id)!!
            val r = s.releases.first { it.folder == folder }
            val dir = File(c.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, r.folder + if (pdf) ".pdf" else ".zip")
            val user = Prefs.userName(c)
            if (pdf) PdfReport.write(c, s, r, user, f) { progress = it }
            else {
                Exporter.zip(c, r, FileNames.build(pattern, s, r, user), f) { progress = it }
                SessionStore.save(c, s.copy(zipPattern = pattern))
            }
            f
        }, onDone)
    }

    private fun deleteFiles(c: Context, releases: List<Release>) {
        for (r in releases) for (i in r.items) runCatching { c.contentResolver.delete(Uri.parse(i.out), null, null) }
    }

    /**
     * Abbandona una sessione in corso: elimina la sessione, le versioni emesse e le bozze
     * delle sue foto. Le foto originali restano dove sono.
     */
    fun discard(context: Context, s: Session) {
        val c = context.applicationContext
        for (p in SessionStore.photos(c, s)) {
            // le annotazioni salvate fuori sessione (con una copia in Galleria) restano
            if (AnnotationStore.load(c, p.uri)?.outUri == null) AnnotationStore.remove(c, p.uri)
        }
        deleteFiles(c, s.versions)
        SessionStore.remove(c, s.id)
    }

    /** Elimina un sopralluogo chiuso con tutte le sue emissioni (gli originali restano). */
    fun delete(context: Context, s: Session) {
        val c = context.applicationContext
        deleteFiles(c, s.releases)
        SessionStore.remove(c, s.id)
    }
}
