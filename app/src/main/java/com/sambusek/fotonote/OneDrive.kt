package com.sambusek.fotonote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Accesso a OneDrive (Microsoft 365) con Microsoft Graph.
 *
 * Accesso: OAuth 2.0 con PKCE nel browser (app "pubblica", nessun segreto nell'app).
 * L'app va registrata una volta in Microsoft Entra ID: vedi README, «Backup su OneDrive».
 * I token restano nelle preferenze private escluse dai backup di Android.
 */
object OneDrive {
    /** ID dell'app registrata in Microsoft Entra (si può cambiare dalle Impostazioni). */
    const val DEFAULT_CLIENT_ID = ""
    /** Directory (tenant): "organizations" = qualsiasi account Microsoft 365 aziendale. */
    const val DEFAULT_TENANT = "organizations"
    const val REDIRECT = "com.sambusek.fotonote://auth"
    private const val SCOPES = "offline_access User.Read Files.ReadWrite"
    private const val GRAPH = "https://graph.microsoft.com/v1.0"
    /** Cartella dei backup nella radice del OneDrive dell'utente. */
    const val ROOT = "Pastashoot"

    class NotSignedIn : IOException("Account Microsoft non collegato")

    private fun secret(c: Context) = c.getSharedPreferences("fotonote_secret", Context.MODE_PRIVATE)
    private fun sp(c: Context) = c.getSharedPreferences("fotonote", Context.MODE_PRIVATE)

    fun clientId(c: Context): String = sp(c).getString("ms_client_id", "").orEmpty().ifBlank { DEFAULT_CLIENT_ID }
    fun tenant(c: Context): String = sp(c).getString("ms_tenant", "").orEmpty().ifBlank { DEFAULT_TENANT }
    fun setClientId(c: Context, v: String) = sp(c).edit().putString("ms_client_id", v.trim()).apply()
    fun setTenant(c: Context, v: String) = sp(c).edit().putString("ms_tenant", v.trim()).apply()
    fun configured(c: Context) = clientId(c).isNotBlank()

    /** Account collegato (indirizzo), osservabile dall'interfaccia. */
    var account by mutableStateOf<String?>(null)
        private set

    fun load(c: Context) {
        account = secret(c).getString("ms_account", null)?.takeIf { secret(c).contains("ms_refresh") }
    }

    fun signedIn(c: Context): Boolean = secret(c).contains("ms_refresh")

    private fun authBase(c: Context) = "https://login.microsoftonline.com/${tenant(c)}/oauth2/v2.0"

    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    /** Apre il browser per l'accesso; la risposta torna ad [AuthActivity]. */
    fun signIn(activity: Activity) {
        val c = activity.applicationContext
        val rnd = SecureRandom()
        val verifier = b64url(ByteArray(48).also { rnd.nextBytes(it) })
        val state = b64url(ByteArray(16).also { rnd.nextBytes(it) })
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        secret(c).edit().putString("ms_verifier", verifier).putString("ms_state", state).apply()
        val url = Uri.parse("${authBase(c)}/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId(c))
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("prompt", "select_account")
            .build()
        activity.startActivity(Intent(Intent.ACTION_VIEW, url))
    }

    /** Completa l'accesso con il codice ricevuto dal browser. Da chiamare fuori dal thread principale. */
    fun completeSignIn(c: Context, redirect: Uri) {
        redirect.getQueryParameter("error")?.let { err ->
            error(redirect.getQueryParameter("error_description")?.substringBefore(" Trace ID") ?: err)
        }
        val code = redirect.getQueryParameter("code") ?: error("Risposta di accesso non valida")
        val st = redirect.getQueryParameter("state")
        require(st != null && st == secret(c).getString("ms_state", null)) { "Risposta di accesso non attesa: riprova" }
        val verifier = secret(c).getString("ms_verifier", null) ?: error("Accesso scaduto: riprova")
        token(c, mapOf(
            "grant_type" to "authorization_code", "code" to code,
            "redirect_uri" to REDIRECT, "code_verifier" to verifier,
        ))
        secret(c).edit().remove("ms_verifier").remove("ms_state").apply()
        val me = JSONObject(graphText(c, "GET", "$GRAPH/me?\$select=userPrincipalName,mail,displayName"))
        val who = me.optString("mail").ifBlank { me.optString("userPrincipalName") }
        secret(c).edit().putString("ms_account", who).apply()
        account = who
    }

    fun signOut(c: Context) {
        secret(c).edit().remove("ms_refresh").remove("ms_access").remove("ms_expiry").remove("ms_account").apply()
        account = null
    }

    /** Richiesta al servizio dei token; salva token di accesso e di rinnovo. */
    private fun token(c: Context, params: Map<String, String>) {
        val body = (params + mapOf("client_id" to clientId(c), "scope" to SCOPES))
            .entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val conn = (URL("${authBase(c)}/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 20_000; readTimeout = 30_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readLimited(1 * MB).decodeToString() }.orEmpty()
            val o = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                // rinnovo rifiutato (password cambiata, accesso revocato…): bisogna ricollegarsi
                if (o.optString("error") == "invalid_grant") signOut(c)
                error(o.optString("error_description").substringBefore(" Trace ID").ifBlank { "Accesso non riuscito ($code)" })
            }
            secret(c).edit()
                .putString("ms_access", o.getString("access_token"))
                .putLong("ms_expiry", System.currentTimeMillis() + (o.optLong("expires_in", 3600) - 120) * 1000)
                .apply {
                    o.optString("refresh_token").takeIf { it.isNotBlank() }?.let { putString("ms_refresh", it) }
                }
                .apply()
        } finally {
            conn.disconnect()
        }
    }

    /** Token di accesso valido, rinnovato se scaduto. */
    @Synchronized
    private fun accessToken(c: Context): String {
        val s = secret(c)
        val refresh = s.getString("ms_refresh", null) ?: throw NotSignedIn()
        if (System.currentTimeMillis() < s.getLong("ms_expiry", 0)) s.getString("ms_access", null)?.let { return it }
        token(c, mapOf("grant_type" to "refresh_token", "refresh_token" to refresh))
        return s.getString("ms_access", null) ?: throw NotSignedIn()
    }

    // ------------------------------------------------------------------ Microsoft Graph

    /** Percorso di un elemento sotto la cartella dei backup, con ogni parte codificata. */
    fun itemUrl(path: String): String =
        "$GRAPH/me/drive/root:/" + (listOf(ROOT) + path.split('/').filter { it.isNotEmpty() })
            .joinToString("/") { Uri.encode(it) }

    private fun open(c: Context, method: String, url: String, auth: Boolean = true): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 20_000; readTimeout = 60_000
            if (auth) setRequestProperty("Authorization", "Bearer " + accessToken(c))
        }

    private fun fail(conn: HttpURLConnection, code: Int): Nothing {
        val text = conn.errorStream?.use { it.readLimited(256 * KB).decodeToString() }.orEmpty()
        val msg = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull()
        conn.disconnect()
        if (code == 401) throw NotSignedIn()
        throw IOException(msg?.ifBlank { null } ?: "Errore di OneDrive ($code)")
    }

    private fun graphText(c: Context, method: String, url: String, body: ByteArray? = null, type: String? = null): String {
        val conn = open(c, method, url)
        try {
            if (body != null) {
                conn.doOutput = true
                type?.let { conn.setRequestProperty("Content-Type", it) }
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code !in 200..299) fail(conn, code)
            return conn.inputStream.use { it.readLimited(20 * MB).decodeToString() }
        } finally {
            conn.disconnect()
        }
    }

    /** Scrive un file piccolo (fino a qualche MB), sostituendo quello esistente. */
    fun putSmall(c: Context, path: String, bytes: ByteArray, type: String = "application/json") {
        graphText(c, "PUT", itemUrl(path) + ":/content", bytes, type)
    }

    /** Dimensione di un file su OneDrive; null se non esiste. */
    fun size(c: Context, path: String): Long? {
        val conn = open(c, "GET", itemUrl(path) + "?\$select=size")
        try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code !in 200..299) fail(conn, code)
            return JSONObject(conn.inputStream.use { it.readLimited(64 * KB).decodeToString() }).optLong("size")
        } finally {
            conn.disconnect()
        }
    }

    /** Carica un file grande a pezzi (sessione di caricamento, riprende dagli errori di rete del singolo pezzo). */
    fun upload(c: Context, path: String, file: File) {
        val session = JSONObject(
            graphText(
                c, "POST", itemUrl(path) + ":/createUploadSession",
                JSONObject().put("item", JSONObject().put("@microsoft.graph.conflictBehavior", "replace"))
                    .toString().toByteArray(),
                "application/json",
            )
        )
        val uploadUrl = session.getString("uploadUrl")
        val total = file.length()
        val chunk = 10 * 320 * 1024 // multiplo di 320 KiB, come richiesto da OneDrive
        RandomAccessFile(file, "r").use { raf ->
            var pos = 0L
            val buf = ByteArray(chunk)
            while (pos < total) {
                val n = minOf(chunk.toLong(), total - pos).toInt()
                raf.seek(pos); raf.readFully(buf, 0, n)
                var attempt = 0
                while (true) {
                    // l'indirizzo di caricamento è già autorizzato: niente token
                    val conn = open(c, "PUT", uploadUrl, auth = false)
                    try {
                        conn.doOutput = true
                        conn.setFixedLengthStreamingMode(n)
                        conn.setRequestProperty("Content-Range", "bytes $pos-${pos + n - 1}/$total")
                        conn.outputStream.use { it.write(buf, 0, n) }
                        val code = conn.responseCode
                        if (code in 200..299) break
                        if (code in 500..599 && attempt < 3) { attempt++; Thread.sleep(2000L * attempt); continue }
                        fail(conn, code)
                    } catch (e: IOException) {
                        if (e is NotSignedIn || attempt >= 3) throw e
                        attempt++; Thread.sleep(2000L * attempt)
                    } finally {
                        conn.disconnect()
                    }
                }
                pos += n
            }
        }
    }

    /** Scarica un file in [out]. */
    fun download(c: Context, path: String, out: OutputStream) {
        val conn = open(c, "GET", itemUrl(path) + ":/content")
        try {
            val code = conn.responseCode
            if (code !in 200..299) fail(conn, code)
            conn.inputStream.use { it.copyTo(out) }
        } finally {
            conn.disconnect()
        }
    }

    /** Legge un file di testo (es. un manifesto JSON). */
    fun readText(c: Context, path: String): String = graphText(c, "GET", itemUrl(path) + ":/content")

    /** Elementi in una cartella sotto la cartella dei backup ("" = la cartella stessa); vuoto se non esiste. */
    fun children(c: Context, path: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var url: String? = (if (path.isEmpty()) "$GRAPH/me/drive/root:/$ROOT" else itemUrl(path)) +
            ":/children?\$select=name,folder,file,size,lastModifiedDateTime&\$top=200"
        while (url != null) {
            val conn = open(c, "GET", url)
            try {
                val code = conn.responseCode
                if (code == 404) return out
                if (code !in 200..299) fail(conn, code)
                val o = JSONObject(conn.inputStream.use { it.readLimited(20 * MB).decodeToString() })
                val arr = o.getJSONArray("value")
                for (i in 0 until arr.length()) out += arr.getJSONObject(i)
                url = o.optString("@odata.nextLink").ifBlank { null }
            } finally {
                conn.disconnect()
            }
        }
        return out
    }
}
