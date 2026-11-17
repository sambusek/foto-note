package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Una build pubblicata su GitHub (release "latest" del repository). */
class UpdateInfo(val build: Int, val title: String, val assetApiUrl: String, val downloadUrl: String, val size: Long)

/**
 * Aggiornamenti da GitHub: legge la release "latest" pubblicata dalla build automatica,
 * scarica l'APK e avvia l'installazione. Per un repository privato serve un token di sola lettura.
 */
object Updater {
    private const val REPO = "sambusek/foto-note"
    private const val ASSET = "pastashoot.apk"
    private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L

    /** Ultima build disponibile più recente di quella installata (null se non c'è o non ancora controllato). */
    var available by mutableStateOf<UpdateInfo?>(null)

    fun currentBuild(c: Context): Int =
        c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode.toInt()

    private fun open(url: String, token: String?, accept: String, follow: Boolean = true): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = follow
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "Pastashoot")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer ${token.trim()}")
        }

    /** Legge la release "latest" (da chiamare in background). */
    fun latest(c: Context): UpdateInfo {
        val conn = open("https://api.github.com/repos/$REPO/releases/tags/latest", Prefs.githubToken(c), "application/vnd.github+json")
        when (conn.responseCode) {
            200 -> {}
            401 -> error("token non valido o scaduto")
            403 -> error("accesso negato (token senza permesso di lettura, o troppe richieste)")
            404 -> error(
                if (Prefs.githubToken(c).isBlank()) "il repository è privato: inserisci un token di sola lettura"
                else "release non trovata, o token senza accesso al repository"
            )
            else -> error("risposta di GitHub ${conn.responseCode}")
        }
        val o = JSONObject(conn.inputStream.use { it.readLimited(2 * MB).decodeToString() })
        conn.disconnect()
        val title = o.optString("name")
        val build = Regex("build (\\d+)").find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val assets = o.getJSONArray("assets")
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == ASSET }
            ?: error("nella release manca $ASSET")
        Prefs.setLastUpdateCheck(c, System.currentTimeMillis())
        return UpdateInfo(build, title, asset.getString("url"), asset.getString("browser_download_url"), asset.optLong("size"))
    }

    /** Controlla e aggiorna [available]; restituisce l'esito (da chiamare in background). */
    fun check(c: Context): Result<UpdateInfo> = runCatching { latest(c) }.onSuccess {
        available = if (it.build > currentBuild(c)) it else null
    }

    /** Vero se è il momento del controllo automatico all'apertura. */
    fun dueForAutoCheck(c: Context) =
        Prefs.autoUpdateCheck(c) && System.currentTimeMillis() - Prefs.lastUpdateCheck(c) > CHECK_EVERY_MS

    /** Scarica l'APK nella cache (da chiamare in background). */
    fun download(c: Context, u: UpdateInfo, onProgress: (Float) -> Unit): File {
        val token = Prefs.githubToken(c)
        val conn = if (token.isBlank()) open(u.downloadUrl, null, "application/octet-stream")
        else {
            // repository privato: l'API risponde con un indirizzo temporaneo, da aprire senza token
            val api = open(u.assetApiUrl, token, "application/octet-stream", follow = false)
            if (api.responseCode in 300..399) {
                val location = api.getHeaderField("Location")
                api.disconnect()
                require(location != null && location.startsWith("https://")) { "indirizzo di download non valido" }
                open(location, null, "application/octet-stream")
            } else api
        }
        check(conn.responseCode == 200) { "download non riuscito (${conn.responseCode})" }
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: u.size
        val dir = File(c.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, ASSET)
        conn.inputStream.use { input ->
            FileOutputStream(f).use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        conn.disconnect()
        // un APK è un archivio ZIP: se non inizia con "PK" il download non è valido
        val ok = f.length() > 0 && f.inputStream().use { val b = ByteArray(2); it.read(b) == 2 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte() }
        if (!ok) { f.delete(); error("il file scaricato non è un APK valido") }
        return f
    }

    /** Avvia l'installazione; la prima volta Android chiede di consentire le installazioni da Pastashoot. */
    fun install(c: Context, apk: File) {
        if (!c.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(c, "Consenti a Pastashoot di installare app, poi tocca di nuovo Aggiorna", Toast.LENGTH_LONG).show()
            c.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", apk)
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Scarica e installa, con la finestra di avanzamento. */
    fun downloadAndInstall(context: Context, u: UpdateInfo) {
        val c = context.applicationContext
        SessionOps.launchOp("Download della build ${u.build}…", {
            runCatching { download(c, u) { SessionOps.progress = it } }
        }) { r ->
            r?.onSuccess { install(context, it) }
                ?.onFailure { Toast.makeText(c, "Aggiornamento non riuscito: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
}
