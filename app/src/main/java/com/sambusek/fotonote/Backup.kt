package com.sambusek.fotonote

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Backup dei sopralluoghi su OneDrive, nella cartella "Pastashoot" dell'utente:
 *
 *   Pastashoot/AAMMGG_NOME/pastashoot-sopralluogo.json   manifesto (sessione, campi, disegni, note)
 *   Pastashoot/AAMMGG_NOME/foto/<id>_<nome>.jpg         foto originali, con GPS
 *
 * È lo stesso contenuto del pacchetto "Esporta per un altro telefono", in file separati:
 * si caricano solo le foto nuove e il manifesto quando cambia. Il ripristino ricompone il
 * pacchetto e lo importa come un sopralluogo in pausa.
 */
object Backup {
    private fun sp(c: Context) = c.getSharedPreferences("fotonote", Context.MODE_PRIVATE)

    fun enabled(c: Context) = sp(c).getBoolean("backup_auto", true)
    fun setEnabled(c: Context, v: Boolean) { sp(c).edit().putBoolean("backup_auto", v).apply(); reschedule(c) }
    fun wifiOnly(c: Context) = sp(c).getBoolean("backup_wifi_only", true)
    fun setWifiOnly(c: Context, v: Boolean) { sp(c).edit().putBoolean("backup_wifi_only", v).apply(); reschedule(c) }
    fun lastOk(c: Context) = sp(c).getLong("backup_last_ok", 0L)
    fun lastError(c: Context): String? = sp(c).getString("backup_last_error", null)

    private const val WORK_SOON = "backup-soon"
    private const val WORK_DAILY = "backup-daily"

    private fun constraints(c: Context) = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly(c)) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    private fun active(c: Context) = enabled(c) && OneDrive.configured(c) && OneDrive.signedIn(c)

    /** Qualcosa è cambiato: backup fra qualche minuto (le modifiche ravvicinate si accorpano). */
    fun changed(c: Context) {
        if (!active(c)) return
        runCatching {
            WorkManager.getInstance(c).enqueueUniqueWork(
                WORK_SOON, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BackupWorker>()
                    .setInitialDelay(10, TimeUnit.MINUTES)
                    .setConstraints(constraints(c))
                    .build(),
            )
        }
    }

    /** Backup giornaliero di sicurezza; si aggiorna quando cambiano le impostazioni. */
    fun reschedule(c: Context) {
        runCatching {
            val wm = WorkManager.getInstance(c)
            if (!active(c)) {
                wm.cancelUniqueWork(WORK_DAILY); wm.cancelUniqueWork(WORK_SOON); return
            }
            wm.enqueueUniquePeriodicWork(
                WORK_DAILY, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS).setConstraints(constraints(c)).build(),
            )
        }
    }

    // ------------------------------------------------------------------ stato locale

    /** Cosa è già su OneDrive, per sessione: impronta del manifesto e foto caricate. */
    private fun stateFile(c: Context) = File(c.filesDir, "backup_state.json")

    private fun loadState(c: Context): JSONObject =
        runCatching { JSONObject(stateFile(c).readText()) }.getOrElse { JSONObject() }

    private fun saveState(c: Context, o: JSONObject) = stateFile(c).writeAtomic(o.toString())

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Nome stabile di una foto nel backup: non cambia se se ne aggiungono o tolgono altre. */
    private fun backupName(p: Photo, name: String) = "foto/${p.id}_" + FileNames.clean(name).ifEmpty { "foto.jpg" }

    // ------------------------------------------------------------------ backup

    /** Esegue il backup di tutti i sopralluoghi. Da chiamare fuori dal thread principale. */
    @Synchronized
    fun runAll(c: Context, onProgress: (Float) -> Unit = {}): Int {
        val sessions = SessionStore.all(c)
        val state = loadState(c)
        var sent = 0
        try {
            sessions.forEachIndexed { si, s ->
                val (manifest, files) = SessionPackage.manifest(c, s) { _, p, name -> backupName(p, name) }
                // stato di chiusura, per mostrarlo nel ripristino
                manifest.put("closed", !s.open).put("backupAt", System.currentTimeMillis())
                val st = state.optJSONObject(s.id) ?: JSONObject().also { state.put(s.id, it) }
                val done = st.optJSONArray("files")?.let { a -> (0 until a.length()).map { a.getString(it) }.toMutableSet() }
                    ?: mutableSetOf()
                // la cartella su OneDrive è quella della sessione; se la sessione cambia nome si riparte
                if (st.optString("folder") != s.folder) { done.clear(); st.put("folder", s.folder) }
                val tmp = File(c.cacheDir, "backup-upload").apply { mkdirs() }
                files.forEachIndexed { fi, (uri, file) ->
                    if (file !in done) {
                        val local = File(tmp, "foto.bin")
                        try {
                            MediaHelper.openOriginal(c, uri)?.use { input -> FileOutputStream(local).use { input.copyTo(it) } }
                                ?: return@forEachIndexed // foto non più leggibile: la si salta
                            val path = "${s.folder}/$file"
                            // già su OneDrive con la stessa dimensione (es. dopo una reinstallazione): non si ricarica
                            if (OneDrive.size(c, path) != local.length()) OneDrive.upload(c, path, local)
                            done += file; sent++
                            st.put("files", JSONArray(done.toList()))
                            saveState(c, state)
                        } finally {
                            local.delete()
                        }
                    }
                    onProgress((si + (fi + 1f) / (files.size + 1)) / sessions.size)
                }
                // il manifesto per ultimo: rimanda solo a foto già caricate
                val forHash = JSONObject(manifest.toString()).apply { remove("exportedAt"); remove("backupAt") }.toString()
                val hash = sha1(forHash)
                if (st.optString("manifest") != hash) {
                    OneDrive.putSmall(c, "${s.folder}/${SessionPackage.MANIFEST_NAME}", manifest.toString().toByteArray())
                    st.put("manifest", hash)
                    saveState(c, state)
                }
                onProgress((si + 1f) / sessions.size)
            }
            sp(c).edit().putLong("backup_last_ok", System.currentTimeMillis()).remove("backup_last_error").apply()
        } catch (e: Exception) {
            sp(c).edit().putString("backup_last_error", e.message ?: e.javaClass.simpleName).apply()
            throw e
        }
        return sent
    }

    // ------------------------------------------------------------------ ripristino

    /** Un sopralluogo trovato su OneDrive. */
    data class Remote(val folder: String, val name: String, val photos: Int, val closed: Boolean, val backupAt: Long)

    /** Sopralluoghi presenti su OneDrive, dal più recente. */
    fun listRemote(c: Context): List<Remote> =
        OneDrive.children(c, "").filter { it.has("folder") }.mapNotNull { f ->
            val folder = f.getString("name")
            runCatching {
                val m = JSONObject(OneDrive.readText(c, "$folder/${SessionPackage.MANIFEST_NAME}"))
                Remote(
                    folder, m.getJSONObject("session").optString("name", folder),
                    m.optJSONArray("photos")?.length() ?: 0, m.optBoolean("closed"),
                    m.optLong("backupAt", m.optLong("exportedAt")),
                )
            }.getOrNull()
        }.sortedByDescending { it.backupAt }

    /** Scarica un sopralluogo e lo importa (in pausa). Da chiamare fuori dal thread principale. */
    fun restore(c: Context, folder: String, onProgress: (Float) -> Unit): Session {
        val manifestText = OneDrive.readText(c, "$folder/${SessionPackage.MANIFEST_NAME}")
        val manifest = JSONObject(manifestText)
        val photos = manifest.getJSONArray("photos")
        val zip = File(c.cacheDir, "restore.pastashoot.zip")
        val running = SessionStore.running(c)
        try {
            // si ricompone il pacchetto e si usa l'importazione che già esiste
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { zos ->
                zos.putNextEntry(ZipEntry(SessionPackage.MANIFEST_NAME))
                zos.write(manifestText.toByteArray())
                zos.closeEntry()
                zos.setLevel(0)
                for (i in 0 until photos.length()) {
                    val file = photos.getJSONObject(i).getString("file")
                    zos.putNextEntry(ZipEntry(file))
                    OneDrive.download(c, "$folder/$file", zos)
                    zos.closeEntry()
                    onProgress(0.7f * (i + 1) / photos.length())
                }
            }
            val s = SessionPackage.import(c, Uri.fromFile(zip)) { onProgress(0.7f + 0.3f * it) }
            // ripristinato in pausa: non deve raccogliere le foto che si scattano ora;
            // il sopralluogo che era in corso torna in corso
            val restored = SessionStore.togglePause(c, s)
            running?.let { r -> SessionStore.get(c, r.id)?.takeIf { it.open && it.paused }?.let { SessionStore.togglePause(c, it) } }
            return SessionStore.get(c, restored.id) ?: restored
        } finally {
            zip.delete()
        }
    }
}

/** Esegue il backup in background, quando la rete richiesta è disponibile. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!OneDrive.configured(c) || !OneDrive.signedIn(c) || !Backup.enabled(c)) return Result.success()
        return try {
            Backup.runAll(c)
            Result.success()
        } catch (e: OneDrive.NotSignedIn) {
            Result.failure() // serve ricollegare l'account: lo si vede nelle Impostazioni
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}

/** Operazioni manuali con la finestra di avanzamento. */
fun SessionOps.backupNow(context: Context, onDone: (Result<Int>) -> Unit) {
    val c = context.applicationContext
    launchOp("Backup su OneDrive…", { runCatching { Backup.runAll(c) { progress = it } } }) {
        onDone(it ?: Result.failure(IllegalStateException("Backup non riuscito")))
    }
}

fun SessionOps.restoreFromCloud(context: Context, folder: String, onDone: (Result<Session>) -> Unit) {
    val c = context.applicationContext
    launchOp("Ripristino da OneDrive…", { runCatching { Backup.restore(c, folder) { progress = it } } }) {
        onDone(it ?: Result.failure(IllegalStateException("Ripristino non riuscito")))
    }
}
