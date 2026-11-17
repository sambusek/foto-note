package com.sambusek.fotonote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Pacchetto di un sopralluogo in corso (file ".pastashoot.zip"), per continuarlo su un altro
 * telefono: foto originali (con GPS), disegni, testi, campi, layout e nota generale.
 * Le versioni emesse non sono incluse: restano sul telefono di origine.
 */
object SessionPackage {
    private const val FORMAT = "pastashoot-sopralluogo"
    private const val MANIFEST = "pastashoot-sopralluogo.json"

    /** Vero se il file inizia come uno ZIP (per distinguerlo da un template JSON). */
    fun isZip(c: Context, uri: Uri): Boolean = runCatching {
        c.contentResolver.openInputStream(uri)?.use { val b = ByteArray(2); it.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
    }.getOrNull() == true

    /** Crea il pacchetto della sessione [s] nella cartella di condivisione. */
    fun export(c: Context, s: Session, onProgress: (Float) -> Unit): File {
        val photos = SessionStore.photos(c, s).filter { it.uri.toString() !in s.excluded }
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "${s.folder}.pastashoot.zip")

        val entries = JSONArray()
        val files = photos.mapIndexed { i, p ->
            val name = MediaHelper.displayName(c.contentResolver, p.uri) ?: "foto_${i + 1}.jpg"
            val file = "foto/" + (i + 1).toString().padStart(3, '0') + "_" + name
            entries.put(
                JSONObject().put("file", file).put("name", name)
                    .put("taken", MediaHelper.takenMillis(c.contentResolver, p.uri))
                    .put("annotation", AnnotationStore.rawJson(c, p.uri) ?: JSONObject.NULL)
            )
            p.uri to file
        }
        // solo ciò che serve a continuare: niente versioni, niente pause, foto già elencate sopra
        val session = s.copy(excluded = emptySet(), extra = emptyList(), removed = emptySet(), items = emptyList(), versions = emptyList(), pauses = emptyList())
        val manifest = JSONObject()
            .put("format", FORMAT).put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("originalStart", s.start)
            .put("session", session.toJson())
            .put("photos", entries)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
            zos.putNextEntry(ZipEntry(MANIFEST))
            zos.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.setLevel(0) // i JPEG sono già compressi
            files.forEachIndexed { i, (uri, file) ->
                MediaHelper.openOriginal(c, uri)?.use { input ->
                    zos.putNextEntry(ZipEntry(file))
                    input.copyTo(zos)
                    zos.closeEntry()
                }
                onProgress((i + 1f) / files.size)
            }
        }
        return out
    }

    /**
     * Importa un pacchetto: le foto vanno in Immagini › Pastashoot › Importati › CARTELLA e diventano
     * la nuova sessione in corso, con disegni e testi. Le foto scattate da ora si aggiungono.
     */
    fun import(c: Context, uri: Uri, onProgress: (Float) -> Unit): Session {
        check(SessionStore.active(c) == null) {
            "C'è già un sopralluogo in corso: consolidalo o eliminalo prima di importarne un altro"
        }
        val cr = c.contentResolver
        var manifest: JSONObject? = null
        val imported = HashMap<String, Uri>()
        try {
            cr.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        if (e.name == MANIFEST) {
                            manifest = JSONObject(zip.readLimited(20 * MB).decodeToString())
                            require(manifest!!.optString("format") == FORMAT) { "Il file non è un sopralluogo di Pastashoot" }
                            continue
                        }
                        val m = manifest ?: continue
                        val photos = m.getJSONArray("photos")
                        val info = (0 until photos.length()).map { photos.getJSONObject(it) }.firstOrNull { it.getString("file") == e.name }
                            ?: continue
                        val folder = FileNames.clean(m.getJSONObject("session").getString("folder")).ifEmpty { "Sopralluogo" }
                        val name = FileNames.clean(info.getString("name")).ifEmpty { "foto.jpg" }
                        imported[e.name] = insertPhoto(c, zip, name, info.optLong("taken"), "Pictures/Pastashoot/Importati/$folder")
                        onProgress(imported.size.toFloat() / photos.length())
                    }
                }
            } ?: error("File non leggibile")
        } catch (e: Throwable) {
            // importazione fallita a metà: si tolgono le foto già copiate
            imported.values.forEach { runCatching { cr.delete(it, null, null) } }
            throw e
        }

        val m = manifest ?: error("Il file non è un sopralluogo di Pastashoot")
        val photos = m.getJSONArray("photos")
        val uris = ArrayList<Uri>()
        for (i in 0 until photos.length()) {
            val info = photos.getJSONObject(i)
            val newUri = imported[info.getString("file")] ?: continue
            uris += newUri
            info.optJSONObject("annotation")?.let { AnnotationStore.writeRaw(c, newUri, it) }
        }
        val src = sessionFromJson(m.getJSONObject("session"))
        val now = System.currentTimeMillis()
        val session = src.copy(
            id = now.toString(),
            folder = SessionStore.uniqueFolder(c, src.folder),
            start = now, // da qui in poi le nuove foto di questo telefono entrano nel sopralluogo
            end = 0L,
            extra = uris.map { it.toString() },
        )
        SessionStore.save(c, session)
        return session
    }

    /** Copia una foto del pacchetto nella Galleria. */
    private fun insertPhoto(c: Context, input: ZipInputStream, name: String, taken: Long, relPath: String): Uri {
        val cr = c.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (name.lowercase().endsWith(".png")) "image/png" else "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            if (taken > 0) put(MediaStore.Images.Media.DATE_TAKEN, taken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: error("Impossibile salvare le foto")
        try {
            cr.openOutputStream(target, "w")?.use { input.copyTo(it) } ?: error("Impossibile salvare le foto")
        } catch (e: Throwable) {
            runCatching { cr.delete(target, null, null) }
            throw e
        }
        cr.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return target
    }
}

/** Esporta e importa i pacchetti con la finestra di avanzamento delle operazioni lunghe. */
fun SessionOps.exportPackage(context: Context, s: Session, onDone: (File?) -> Unit) {
    val c = context.applicationContext
    launchOp("Preparazione del pacchetto…", { SessionPackage.export(c, s) { progress = it } }, onDone)
}

fun SessionOps.importPackage(context: Context, uri: Uri, onDone: (Result<Session>) -> Unit) {
    val c = context.applicationContext
    launchOp("Importazione del sopralluogo…", {
        runCatching { SessionPackage.import(c, uri) { progress = it } }
    }) { onDone(it ?: Result.failure(IllegalStateException("Importazione non riuscita"))) }
}
