package com.sambusek.fotonote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Una revisione di una tavola: una pagina di un PDF caricato in una certa data. */
data class PlanRev(
    val id: String, val file: String, val page: Int, val time: Long, val source: String,
    /** La pagina ha annotazioni da disegnare (null = non ancora verificato, revisioni precedenti). */
    val annots: Boolean? = null,
)

/** Una tavola (planimetria) del cantiere; l'ultima revisione è quella attuale. */
data class Plan(val id: String, val name: String, val revisions: List<PlanRev>) {
    val current: PlanRev get() = revisions.last()
    fun rev(id: String?): PlanRev = revisions.firstOrNull { it.id == id } ?: current
}

/**
 * Cantiere: il contenitore stabile dei sopralluoghi, con le sue tavole e lo schema di campi
 * da cui partono i nuovi sopralluoghi.
 */
data class Site(
    val id: String,
    val name: String,
    val client: String = "",
    val fields: List<NoteField> = emptyList(),
    val plans: List<Plan> = emptyList(),
    val created: Long = System.currentTimeMillis(),
)

/**
 * Pin di una foto su una tavola: posizione in proporzione alla pagina (0–1), quindi valida a
 * qualsiasi zoom; [dir] è la direzione dello scatto in gradi (null = non indicata).
 */
data class Pin(val plan: String, val rev: String, val x: Float, val y: Float, val dir: Float? = null)

internal fun Pin.toJson(): JSONObject = JSONObject().put("plan", plan).put("rev", rev).put("x", x.toDouble()).put("y", y.toDouble())
    .apply { dir?.let { put("dir", it.toDouble()) } }

internal fun pinFromJson(o: JSONObject) = Pin(
    o.getString("plan"), o.getString("rev"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
    if (o.has("dir")) o.getDouble("dir").toFloat() else null,
)

/** Archivio dei cantieri: un JSON ciascuno e una cartella con i PDF delle tavole. */
object SiteStore {
    const val GENERIC = "generico"

    private fun root(c: Context) = File(c.filesDir, "cantieri").apply { mkdirs() }
    private fun file(c: Context, id: String) = File(root(c), "$id.json")
    fun planDir(c: Context, siteId: String) = File(root(c), siteId).apply { mkdirs() }
    fun planFile(c: Context, siteId: String, rev: PlanRev) = File(planDir(c, siteId), rev.file)

    private fun Site.toJson() = JSONObject()
        .put("id", id).put("name", name).put("client", client).put("created", created)
        .put("fields", JSONArray().also { a -> fields.forEach { a.put(it.toJson()) } })
        .put("plans", JSONArray().also { a ->
            plans.forEach { p ->
                a.put(JSONObject().put("id", p.id).put("name", p.name).put("revisions", JSONArray().also { r ->
                    p.revisions.forEach { v ->
                        r.put(JSONObject().put("id", v.id).put("file", v.file).put("page", v.page).put("time", v.time).put("source", v.source)
                            .apply { v.annots?.let { put("annots", it) } })
                    }
                }))
            }
        })

    private fun fromJson(o: JSONObject): Site {
        val fl = o.optJSONArray("fields") ?: JSONArray()
        val pl = o.optJSONArray("plans") ?: JSONArray()
        return Site(
            o.getString("id"), o.getString("name"), o.optString("client"),
            migrateLayout((0 until fl.length()).map { fieldFromJson(fl.getJSONObject(it)) }),
            (0 until pl.length()).map { i ->
                val p = pl.getJSONObject(i)
                val rv = p.getJSONArray("revisions")
                Plan(p.getString("id"), p.getString("name"), (0 until rv.length()).map { j ->
                    val v = rv.getJSONObject(j)
                    PlanRev(
                        v.getString("id"), v.getString("file"), v.optInt("page"), v.optLong("time"), v.optString("source"),
                        if (v.has("annots")) v.getBoolean("annots") else null,
                    )
                })
            }.filter { it.revisions.isNotEmpty() },
            o.optLong("created", System.currentTimeMillis()),
        )
    }

    fun all(c: Context): List<Site> {
        generic(c)
        return root(c).listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { runCatching { fromJson(JSONObject(it.readText())) }.getOrNull() }
            ?.sortedWith(compareBy({ it.id != GENERIC }, { it.name.lowercase() })).orEmpty()
    }

    fun get(c: Context, id: String?): Site? =
        id?.let { runCatching { fromJson(JSONObject(file(c, it).readText())) }.getOrNull() }

    /** Il cantiere di un sopralluogo; quelli senza cantiere stanno in "Generico". */
    fun of(c: Context, s: Session): Site = get(c, s.siteId) ?: generic(c)

    /** Cantiere "Generico": per i sopralluoghi veloci e per quelli creati prima dei cantieri. */
    fun generic(c: Context): Site =
        get(c, GENERIC) ?: Site(GENERIC, "Generico", created = 0L).also { save(c, it) }

    fun save(c: Context, s: Site) = file(c, s.id).writeAtomic(s.toJson().toString())

    fun create(c: Context, name: String, fields: List<NoteField> = emptyList(), client: String = ""): Site =
        Site(System.currentTimeMillis().toString(), name.trim(), client.trim(), fields).also { save(c, it) }

    /** Elimina il cantiere e le sue tavole; i suoi sopralluoghi passano a "Generico". */
    fun delete(c: Context, id: String) {
        if (id == GENERIC) return
        SessionStore.all(c).filter { it.siteId == id }.forEach { SessionStore.save(c, it.copy(siteId = null, pins = emptyMap())) }
        file(c, id).delete()
        planDir(c, id).deleteRecursively()
    }

    /** Copia un PDF nella cartella del cantiere; restituisce nome del file e numero di pagine. */
    private fun copyPdf(c: Context, siteId: String, uri: Uri): Pair<String, Int> {
        val name = "${System.nanoTime()}.pdf"
        val dest = File(planDir(c, siteId), name)
        try {
            c.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { i.copyTo(it) } }
                ?: error("File non leggibile")
            val pages = ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
            require(pages > 0) { "Il PDF non ha pagine" }
            return name to pages
        } catch (e: Exception) {
            dest.delete()
            throw if (e is SecurityException) IllegalStateException("PDF protetto da password: non si può aprire") else e
        }
    }

    /** Aggiunge le pagine scelte di un PDF come nuove tavole. Da chiamare fuori dal thread principale. */
    fun addPlans(c: Context, siteId: String, uri: Uri): Site {
        val site = get(c, siteId) ?: error("Cantiere non trovato")
        val source = MediaHelper.displayName(c.contentResolver, uri) ?: "planimetria.pdf"
        val (file, pages) = copyPdf(c, siteId, uri)
        val base = source.substringBeforeLast('.')
        val now = System.currentTimeMillis()
        val annots = PlanRender.annotatedPages(c, File(planDir(c, siteId), file))
        val added = (0 until pages).map { p ->
            Plan(
                "${now}_$p", if (pages == 1) base else "$base · p. ${p + 1}",
                listOf(PlanRev("${now}_${p}_r", file, p, now, source, p in annots)),
            )
        }
        return site.copy(plans = site.plans + added).also { save(c, it) }
    }

    /** Nuova revisione di una tavola, da una pagina di un PDF. Da chiamare fuori dal thread principale. */
    fun addRevision(c: Context, siteId: String, planId: String, uri: Uri, page: Int): Site {
        val site = get(c, siteId) ?: error("Cantiere non trovato")
        val source = MediaHelper.displayName(c.contentResolver, uri) ?: "planimetria.pdf"
        val (file, pages) = copyPdf(c, siteId, uri)
        val now = System.currentTimeMillis()
        val pg = page.coerceIn(0, pages - 1)
        val rev = PlanRev("${now}_r", file, pg, now, source, pg in PlanRender.annotatedPages(c, File(planDir(c, siteId), file)))
        return site.copy(plans = site.plans.map { if (it.id == planId) it.copy(revisions = it.revisions + rev) else it })
            .also { save(c, it) }
    }

    /** Numero di pagine di un PDF (per scegliere la pagina di una revisione). */
    fun pageCount(c: Context, uri: Uri): Int =
        c.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> PdfRenderer(pfd).use { it.pageCount } } ?: 0

    fun renamePlan(c: Context, siteId: String, planId: String, name: String) {
        val site = get(c, siteId) ?: return
        save(c, site.copy(plans = site.plans.map { if (it.id == planId) it.copy(name = name.trim().ifEmpty { it.name }) else it }))
    }

    /** Toglie una tavola dal cantiere (i file restano finché qualche sopralluogo li usa). */
    fun removePlan(c: Context, siteId: String, planId: String) {
        val site = get(c, siteId) ?: return
        save(c, site.copy(plans = site.plans.filter { it.id != planId }))
    }
}

/** Disegno delle tavole: immagini ricavate dai PDF, tenute in memoria. */
object PlanRender {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Annotazioni che non si vedono sulla pianta (collegamenti, finestre delle note). */
    private val INVISIBLE = setOf("Link", "Popup")

    /**
     * Pagine con annotazioni visibili (disegni a mano libera, forme, testi, evidenziazioni, timbri):
     * il lettore di Android non le disegna, quindi quelle pagine passano dal motore completo.
     */
    fun annotatedPages(c: Context, f: File): Set<Int> = runCatching {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c.applicationContext)
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(f).use { doc ->
            (0 until doc.numberOfPages).filter { i ->
                doc.getPage(i).annotations.any { it.subtype !in INVISIBLE && !it.isHidden && !it.isNoView }
            }.toSet()
        }
    }.getOrDefault(emptySet())

    /** Motore completo (con le annotazioni): più lento, usato solo per le pagine che ne hanno. */
    private fun renderFull(c: Context, f: File, page: Int, longSide: Int): Bitmap {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(c.applicationContext)
        return com.tom_roush.pdfbox.pdmodel.PDDocument.load(f).use { doc ->
            val p = doc.getPage(page.coerceIn(0, doc.numberOfPages - 1))
            val box = p.cropBox
            val rotated = p.rotation % 180 != 0
            val w = if (rotated) box.height else box.width
            val h = if (rotated) box.width else box.height
            val scale = longSide / maxOf(w, h)
            com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
                .renderImage(doc.pages.indexOf(p), scale, com.tom_roush.pdfbox.rendering.ImageType.RGB)
        }
    }

    /** Immagine della pagina, lato lungo [longSide] pixel, su fondo bianco. Da chiamare fuori dal thread principale. */
    @Synchronized
    fun bitmap(c: Context, siteId: String, rev: PlanRev, longSide: Int = 2400): Bitmap {
        val key = "${rev.id}@$longSide"
        cache.get(key)?.let { return it }
        val f = SiteStore.planFile(c, siteId, rev)
        // revisioni caricate prima di questa verifica: si controlla ora
        val annots = rev.annots ?: (rev.page in annotatedPages(c, f))
        if (annots) runCatching { renderFull(c, f, rev.page, longSide) }.getOrNull()?.let { cache.put(key, it); return it }
        val bmp = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { r ->
                r.openPage(rev.page.coerceIn(0, r.pageCount - 1)).use { page ->
                    val k = longSide.toFloat() / maxOf(page.width, page.height)
                    val b = Bitmap.createBitmap((page.width * k).toInt().coerceAtLeast(1), (page.height * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    b
                }
            }
        }
        cache.put(key, bmp)
        return bmp
    }
}
