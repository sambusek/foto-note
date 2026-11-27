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

/** Un'emissione: le foto consolidate in una cartella in un certo momento, con la nota generale di allora. */
class Release(val label: String, val folder: String, val time: Long, val items: List<SessionItem>, val note: String = "") {
    val relPath: String get() = "Pictures/Pastashoot/$folder"
    /** Numero di una versione intermedia (dal nome della cartella, …_v03 → 3); null per la definitiva. */
    val number: Int? get() = Regex("_v(\\d+)$").find(folder)?.groupValues?.get(1)?.toIntOrNull()
}

/**
 * Precompilazione dei campi per i nuovi scatti, valida da [time] fino alla successiva
 * ([values] vuoto = precompilazione disattivata). Es. dalle 10:12 "Piano" = 3, "Affaccio" = Gonzaga.
 */
data class Preset(val time: Long, val values: Map<String, String>)

/**
 * Ordine delle foto nei report PDF e negli ZIP: "taken" = di scatto, "fields" = per i valori dei campi
 * [fields] (in priorità), "manual" = l'ordine [manual] (foto originali).
 */
data class PhotoOrder(val mode: String = "taken", val fields: List<String> = emptyList(), val manual: List<String> = emptyList())

/** Confronto "naturale": P2 prima di P10, i vuoti in fondo. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        if (a.isBlank() && b.isBlank()) return 0
        if (a.isBlank()) return 1
        if (b.isBlank()) return -1
        val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 } ?: p.trimStart('0').compareTo(q.trimStart('0'))
            } else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }
}

/** Le foto di [r] nell'ordine scelto. */
fun PhotoOrder.apply(items: List<SessionItem>): List<SessionItem> = when (mode) {
    "fields" -> if (fields.isEmpty()) items else items.withIndex().sortedWith { a, b ->
        for (f in fields) {
            val c = NaturalOrder.compare(a.value.values[f].orEmpty(), b.value.values[f].orEmpty())
            if (c != 0) return@sortedWith c
        }
        a.index.compareTo(b.index)
    }.map { it.value }
    "manual" -> {
        val pos = manual.withIndex().associate { it.value to it.index }
        items.withIndex().sortedWith(compareBy({ pos[it.value.src] ?: Int.MAX_VALUE }, { it.index })).map { it.value }
    }
    else -> items
}

/** Una pausa della sessione; [end] = 0 se è ancora in corso. */
data class Pause(val start: Long, val end: Long = 0L)

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
    /** Foto scattate durante la sessione ma tolte dal sopralluogo (restano nella Galleria). */
    val removed: Set<String> = emptySet(),
    /** Layout delle note: i campi da compilare su ogni foto e dove stamparli. */
    val fields: List<NoteField> = emptyList(),
    /** Foto del consolidamento definitivo. */
    val items: List<SessionItem> = emptyList(),
    val versions: List<Release> = emptyList(),
    /** Ultimo schema usato per i nomi dei file nello ZIP. */
    val zipPattern: String = "",
    /** Nota generale del sopralluogo: va nel report PDF e, come testo, nello ZIP. */
    val generalNote: String = "",
    /** Pause: le foto scattate durante una pausa non entrano nel sopralluogo. */
    val pauses: List<Pause> = emptyList(),
    /** Storia delle precompilazioni: ogni foto prende quella valida al momento dello scatto. */
    val presets: List<Preset> = emptyList(),
    /** Ordine delle foto scelto per report e ZIP. */
    val photoOrder: PhotoOrder = PhotoOrder(),
    /** Cantiere a cui appartiene (null = "Generico"). */
    val siteId: String? = null,
    /** Posizione delle foto sulle tavole del cantiere, per foto (uri). */
    val pins: Map<String, Pin> = emptyMap(),
    /** Revisione usata per ogni tavola (id tavola → id revisione); assente = la più recente. */
    val planRevs: Map<String, String> = emptyMap(),
) {
    /** Valori precompilati per una foto scattata a [ms]. */
    fun presetAt(ms: Long): Map<String, String> = presets.lastOrNull { it.time <= ms }?.values.orEmpty()
    /** Precompilazione attiva ora. */
    val currentPreset: Map<String, String> get() = presets.lastOrNull()?.values.orEmpty()

    val open: Boolean get() = end == 0L
    val paused: Boolean get() = open && pauses.lastOrNull()?.end == 0L
    fun inPause(ms: Long): Boolean = pauses.any { ms >= it.start && (it.end == 0L || ms <= it.end) }
    val finalRelease: Release? get() = if (open) null else Release("Definitiva", folder, end, items, generalNote)
    /** Numero della prossima versione: dopo l'ultima ancora presente, anche se ne sono state eliminate. */
    val nextVersion: Int get() = (versions.mapNotNull { it.number }.maxOrNull() ?: 0) + 1
    /** Etichetta dell'ultima versione emessa, es. "V02"; null se non ce ne sono. */
    val versionTag: String? get() = versions.mapNotNull { it.number }.maxOrNull()?.let { "V" + it.toString().padStart(2, '0') }

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
    .put("parts", JSONArray(parts)).put("separator", separator)
    .put("placed", placed).put("slot", slot).put("highlight", highlight)

/**
 * Layout delle versioni precedenti, con gli angoli: sinistra, centro e destra finiscono
 * in fila nella banda dello stesso lato, nello stesso ordine.
 */
internal fun migrateLayout(fields: List<NoteField>): List<NoteField> {
    if (fields.none { it.pos != it.pos.band }) return fields
    val cells = fields.groupBy { it.pos }.mapValues { (_, l) -> l.sortedBy { it.slot } }
    return fields.map { f ->
        val cell = when (f.pos) { FieldPos.TL, FieldPos.BL -> 0; FieldPos.TOP, FieldPos.BOTTOM -> 1; else -> 2 }
        f.copy(pos = f.pos.band, slot = cell * 1000 + cells.getValue(f.pos).indexOf(f))
    }
}

internal fun fieldFromJson(x: JSONObject): NoteField {
    val choices = x.optString("choices", "")
    var kind = FieldKind.entries.firstOrNull { it.name == x.optString("kind") } ?: FieldKind.INPUT
    if (kind == FieldKind.INPUT && choices.isNotBlank()) kind = FieldKind.CHOICE // versioni precedenti dell'app
    return NoteField(
        x.getString("id"), x.getString("label"),
        FieldPos.entries.firstOrNull { it.name == x.optString("pos") } ?: FieldPos.BOTTOM,
        x.optBoolean("showLabel", true), kind, x.optString("text", ""), choices,
        parts = x.optJSONArray("parts")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
        separator = x.optString("separator", "_"),
        placed = x.optBoolean("placed", true),
        slot = x.optInt("slot", 0),
        highlight = x.optBoolean("highlight", false),
    )
}

internal fun Session.toJson(): JSONObject {
    val fl = JSONArray().also { a -> fields.forEach { a.put(it.toJson()) } }
    val vs = JSONArray().also { a ->
        versions.forEach {
            a.put(
                JSONObject().put("label", it.label).put("folder", it.folder).put("time", it.time)
                    .put("items", it.items.toJson()).put("note", it.note)
            )
        }
    }
    return JSONObject()
        .put("id", id).put("name", name).put("folder", folder)
        .put("start", start).put("end", end)
        .put("excluded", JSONArray(excluded.toList()))
        .put("extra", JSONArray(extra))
        .put("removed", JSONArray(removed.toList()))
        .put("fields", fl)
        .put("items", items.toJson())
        .put("versions", vs)
        .put("zipPattern", zipPattern)
        .put("generalNote", generalNote)
        .put("pauses", JSONArray().also { a -> pauses.forEach { a.put(JSONArray().put(it.start).put(it.end)) } })
        .apply { siteId?.let { put("siteId", it) } }
        .put("pins", JSONObject().also { o -> pins.forEach { (k, v) -> o.put(k, v.toJson()) } })
        .put("planRevs", JSONObject(planRevs as Map<*, *>))
        .put("photoOrder", JSONObject().put("mode", photoOrder.mode)
            .put("fields", JSONArray(photoOrder.fields)).put("manual", JSONArray(photoOrder.manual)))
        .put("presets", JSONArray().also { a ->
            presets.forEach { a.put(JSONObject().put("time", it.time).put("values", JSONObject(it.values as Map<*, *>))) }
        })
}

internal fun sessionFromJson(o: JSONObject): Session {
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
        removed = o.optJSONArray("removed").strings().toSet(),
        fields = if (fl == null) emptyList() else migrateLayout((0 until fl.length()).map { fieldFromJson(fl.getJSONObject(it)) }),
        items = itemsFromJson(o.optJSONArray("items")),
        versions = if (vs == null) emptyList() else (0 until vs.length()).map {
            val x = vs.getJSONObject(it)
            Release(
                x.getString("label"), x.getString("folder"), x.getLong("time"),
                itemsFromJson(x.optJSONArray("items")), x.optString("note", ""),
            )
        },
        zipPattern = o.optString("zipPattern", ""),
        generalNote = o.optString("generalNote", ""),
        pauses = o.optJSONArray("pauses")?.let { a ->
            (0 until a.length()).map { val p = a.getJSONArray(it); Pause(p.getLong(0), p.optLong(1, 0L)) }
        }.orEmpty(),
        siteId = o.optString("siteId").ifBlank { null },
        pins = o.optJSONObject("pins")?.let { p ->
            p.keys().asSequence().mapNotNull { k -> runCatching { k to pinFromJson(p.getJSONObject(k)) }.getOrNull() }.toMap()
        }.orEmpty(),
        planRevs = o.optJSONObject("planRevs")?.stringMap().orEmpty(),
        photoOrder = o.optJSONObject("photoOrder")?.let { p ->
            PhotoOrder(
                p.optString("mode", "taken"),
                p.optJSONArray("fields").strings(),
                p.optJSONArray("manual").strings(),
            )
        } ?: PhotoOrder(),
        presets = o.optJSONArray("presets")?.let { a ->
            (0 until a.length()).map {
                val p = a.getJSONObject(it)
                Preset(p.getLong("time"), p.optJSONObject("values")?.stringMap().orEmpty())
            }
        }.orEmpty(),
    )
}

// ------------------------------------------------------------------ archivio

object SessionStore {
    private fun dir(c: Context) = File(c.filesDir, "sessioni").apply { mkdirs() }
    private fun file(c: Context, id: String) = File(dir(c), "$id.json")

    /** Sessioni già lette, con la data di modifica del file: si rilegge solo ciò che è cambiato. */
    private val cache = HashMap<String, Pair<Long, Session>>()

    private fun parse(f: File): Session? {
        val id = f.nameWithoutExtension
        val mod = f.lastModified()
        synchronized(cache) { cache[id]?.let { (m, s) -> if (m == mod) return s } }
        val s = runCatching { sessionFromJson(JSONObject(f.readText())) }.getOrNull() ?: return null
        synchronized(cache) { cache[id] = mod to s }
        return s
    }

    fun save(c: Context, s: Session) {
        val f = file(c, s.id)
        f.writeAtomic(s.toJson().toString())
        synchronized(cache) { cache[s.id] = f.lastModified() to s }
    }

    /** Tutte le sessioni, la più recente per prima. */
    fun all(c: Context): List<Session> =
        dir(c).listFiles { f -> f.name.endsWith(".json") }?.mapNotNull(::parse)?.sortedByDescending { it.start }.orEmpty()

    fun get(c: Context, id: String): Session? = file(c, id).takeIf { it.exists() }?.let(::parse)

    /**
     * La sessione che sta raccogliendo le foto: aperta e non in pausa. Ce n'è al massimo una;
     * le altre aperte sono in pausa (es. cantiere A interrotto per un sopralluogo nel cantiere B).
     */
    fun running(c: Context): Session? = all(c).firstOrNull { it.open && !it.paused }

    /** Mette in pausa ogni sessione che sta raccogliendo foto, tranne [keepId]. */
    fun pauseOthers(c: Context, keepId: String? = null) {
        val now = System.currentTimeMillis()
        all(c).filter { it.open && !it.paused && it.id != keepId }.forEach { save(c, it.copy(pauses = it.pauses + Pause(now))) }
    }

    fun remove(c: Context, id: String) {
        file(c, id).delete()
        synchronized(cache) { cache.remove(id) }
    }

    /** Mette in pausa la sessione o la riprende. */
    fun togglePause(c: Context, s: Session): Session {
        // riprendendo questa, le altre in corso vanno in pausa: le foto finiscono in una sola sessione
        if (s.paused) pauseOthers(c, s.id)
        val now = System.currentTimeMillis()
        val pauses = if (s.paused) s.pauses.dropLast(1) + s.pauses.last().copy(end = now) else s.pauses + Pause(now)
        return s.copy(pauses = pauses).also { save(c, it) }
    }

    /** Nome cartella: YYMMDD_NOME, senza caratteri non ammessi nei nomi di file. */
    private fun folderName(start: Long, name: String): String {
        val date = SimpleDateFormat("yyMMdd", Locale.ITALY).format(Date(start))
        return "${date}_${FileNames.clean(name).take(40).ifEmpty { "SOPRALLUOGO" }}"
    }

    /** [base] o, se già usato da un altro sopralluogo, [base]_2, _3… */
    fun uniqueFolder(c: Context, base: String, exceptId: String? = null): String {
        val used = all(c).filter { it.id != exceptId }.map { it.folder }.toSet()
        var folder = base
        var n = 2
        while (folder in used) folder = "${base}_${n++}"
        return folder
    }

    /**
     * Rinomina un sopralluogo aperto: cambiano il nome e la cartella delle prossime versioni e del
     * consolidamento (stessa data). Le versioni già emesse restano nelle loro cartelle.
     */
    fun rename(c: Context, s: Session, name: String): Session {
        val n = name.trim().ifEmpty { return s }
        val folder = if (s.open) uniqueFolder(c, folderName(s.start, n), s.id) else s.folder
        return s.copy(name = n, folder = folder).also { save(c, it) }
    }

    /** Nuova sessione, con i campi di un template oppure senza campi. */
    fun create(c: Context, name: String, fields: List<NoteField> = emptyList(), siteId: String? = null): Session {
        pauseOthers(c)
        val start = System.currentTimeMillis()
        val folder = uniqueFolder(c, folderName(start, name))
        return Session(
            id = start.toString(), name = name.trim(), folder = folder, start = start, fields = fields,
            siteId = siteId?.takeIf { it != SiteStore.GENERIC },
        )
            .also { save(c, it) }
    }

    /**
     * Foto della sessione: prima quelle aggiunte a mano, poi quelle scattate
     * durante la sessione in ordine di scatto (comprese le escluse).
     */
    fun photos(c: Context, s: Session): List<Photo> {
        if (!MediaHelper.hasPhotoPermission(c)) return emptyList()
        val shot = MediaHelper.cameraPhotosBetween(c.contentResolver, s.start / 1000, if (s.open) null else s.end / 1000)
            .filter { !s.inPause(it.dateAddedSec * 1000) && it.uri.toString() !in s.removed }
        val shotKeys = shot.map { it.uri.toString() }.toSet()
        val wanted = s.extra.distinct().filter { it !in shotKeys }.mapNotNull { u ->
            val uri = Uri.parse(u)
            runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { Photo(it, uri, 0L) }
        }
        // si tengono solo le foto che esistono ancora nella Galleria
        val alive = MediaHelper.existingIds(c.contentResolver, wanted.map { it.id })
        val added = wanted.filter { it.id in alive }
        return added + shot
    }

    /** La sessione in corso, se [uri] è una foto di quella sessione. */
    fun sessionFor(c: Context, uri: Uri): Session? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        val s = running(c) ?: return null
        if (uri.toString() in s.extra) return s
        if (uri.toString() in s.removed) return null
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return null
        val p = MediaHelper.cameraPhotoById(c.contentResolver, id) ?: return null
        return if (p.dateAddedSec >= s.start / 1000 && !s.inPause(p.dateAddedSec * 1000)) s else null
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
            fun fill(part: String) = Regex("\\{([^}]*)\\}").replace(part) { m ->
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
            // "/" nello schema crea sottocartelle, es. {Piano}/{Zona}/{N}
            val segments = pattern.ifBlank { DEFAULT }.split('/').map { seg ->
                clean(fill(seg)).replace(Regex("_+"), "_").trim('_', '-', ' ', '.')
            }
            val dirs = segments.dropLast(1).map { it.ifEmpty { "senza_valore" } }
            val base = segments.last().ifEmpty { "foto_" + (i + 1).toString().padStart(digits, '0') }
            (dirs + "$base.$ext").joinToString("/")
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

    /** Esegue [work] in background mostrando [message]; [done] gira sul thread principale. */
    fun <T> launchOp(message: String, work: suspend () -> T, done: (T?) -> Unit) {
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
                // i valori scritti sulla foto prevalgono sulla precompilazione del momento dello scatto
                val values = AutoFields.resolve(s.fields, s.presetAt(taken) + a?.values.orEmpty(), s.name, taken, user)
                val (out, name) = AnnotationIO.exportTo(
                    c, p.uri, a?.strokes.orEmpty(), a?.note.orEmpty(), relPath, s.fields, values, a?.pad ?: Pad(),
                )
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
            val n = s.nextVersion
            val folder = "${s.folder}_v" + n.toString().padStart(2, '0')
            val (items, e) = consolidate(c, s, "Pictures/Pastashoot/$folder")
            errors = e
            if (items.isEmpty()) null
            else Release("Versione $n", folder, System.currentTimeMillis(), items, s.generalNote).also {
                // rilettura: nel frattempo la sessione potrebbe essere cambiata
                val now = SessionStore.get(c, id) ?: s
                SessionStore.save(c, now.copy(versions = now.versions + it))
            }
        }) { onDone(it, errors) }
    }

    /** Elimina una versione intermedia: le sue foto nella Galleria e la voce nella sessione. */
    fun deleteVersion(context: Context, id: String, folder: String, onDone: (failed: Int) -> Unit) {
        val c = context.applicationContext
        launchOp("Eliminazione della versione…", {
            val s = SessionStore.get(c, id)!!
            val r = s.versions.first { it.folder == folder }
            var failed = 0
            r.items.forEachIndexed { i, item ->
                runCatching { c.contentResolver.delete(Uri.parse(item.out), null, null) }
                    .onFailure { failed++ }
                progress = (i + 1f) / r.items.size.coerceAtLeast(1)
            }
            val now = SessionStore.get(c, id) ?: s
            SessionStore.save(c, now.copy(versions = now.versions.filter { it.folder != folder }))
            failed
        }) { onDone(it ?: 0) }
    }

    /** Consolidamento definitivo: salva nella cartella della sessione e la chiude. */
    fun close(context: Context, id: String, onDone: (saved: Int, errors: Int) -> Unit) {
        val c = context.applicationContext
        launchOp("Consolidamento definitivo…", {
            val s = SessionStore.get(c, id)!!
            val (items, errors) = consolidate(c, s, "Pictures/Pastashoot/${s.folder}")
            if (items.isEmpty() && errors == 0 && s.versions.isEmpty()) SessionStore.remove(c, id)
            else {
                val now = System.currentTimeMillis()
                // una pausa ancora aperta si chiude con il sopralluogo
                val pauses = if (s.paused) s.pauses.dropLast(1) + s.pauses.last().copy(end = now) else s.pauses
                SessionStore.save(c, s.copy(end = now, items = items, pauses = pauses))
            }
            items.size to errors
        }) { r -> onDone(r?.first ?: 0, r?.second ?: 0) }
    }

    /** Genera lo ZIP (con i nomi da [pattern]) o il PDF di un'emissione, pronto da condividere. */
    fun export(context: Context, id: String, folder: String, pdf: Boolean, pattern: String, onDone: (File?) -> Unit) {
        val c = context.applicationContext
        launchOp(if (pdf) "Creazione del report PDF…" else "Creazione dello ZIP…", {
            val s = SessionStore.get(c, id)!!
            val r0 = s.releases.first { it.folder == folder }
            // foto nell'ordine scelto: vale per il report e per la numerazione {N} dello ZIP
            val r = Release(r0.label, r0.folder, r0.time, s.photoOrder.apply(r0.items), r0.note)
            val dir = File(c.cacheDir, "share").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val f = File(dir, r.folder + if (pdf) ".pdf" else ".zip")
            val user = Prefs.userName(c)
            if (pdf) PdfReport.write(c, s, r, user, f) { progress = it }
            else {
                Exporter.zip(c, r, FileNames.build(pattern, s, r, user), noteText(s, r, user), f) { progress = it }
                SessionStore.save(c, s.copy(zipPattern = pattern))
            }
            f
        }, onDone)
    }

    /** Testo della nota generale da mettere nello ZIP (null se non c'è nota). */
    private fun noteText(s: Session, r: Release, user: String): String? {
        if (r.note.isBlank()) return null
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
        val head = listOfNotNull(
            "Sopralluogo: ${s.name}",
            "Emissione: ${r.label} del ${fmt.format(Date(r.time))}",
            if (user.isNotBlank()) "Redatto da: $user" else null,
        )
        return (head + "" + "NOTA GENERALE" + "" + r.note.trim()).joinToString("\r\n") + "\r\n"
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
