package com.sambusek.fotonote

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Template di sopralluogo: un nome e uno schema di campi preimpostati. */
data class Template(val id: String, val name: String, val fields: List<NoteField>)

/** Riassunto dei campi per gli elenchi, es. "Piano, Stanza, Data". */
fun List<NoteField>.summary(): String =
    if (isEmpty()) "Nessun campo" else joinToString(", ") { it.label.ifBlank { it.kind.defaultLabel } }

/**
 * Archivio dei template (un file JSON ciascuno). Gli stessi file, con estensione
 * ".pastashoot.json", servono per esportarli e importarli su un altro telefono.
 */
object TemplateStore {
    private const val FORMAT = "pastashoot-template"

    private fun dir(c: Context) = File(c.filesDir, "template").apply { mkdirs() }
    private fun file(c: Context, id: String) = File(dir(c), "$id.json")

    private fun Template.toJson() = JSONObject()
        .put("format", FORMAT).put("version", 1)
        .put("id", id).put("name", name)
        .put("fields", JSONArray().also { a -> fields.forEach { a.put(it.toJson()) } })

    private fun fromJson(o: JSONObject): Template {
        require(o.optString("format") == FORMAT) { "Il file non è un template di Pastashoot" }
        val fl = o.optJSONArray("fields") ?: JSONArray()
        return Template(
            o.optString("id").ifBlank { System.currentTimeMillis().toString() },
            o.getString("name"),
            migrateLayout((0 until fl.length()).map { fieldFromJson(fl.getJSONObject(it)) }),
        )
    }

    fun all(c: Context): List<Template> = dir(c).listFiles()
        ?.filter { it.name.endsWith(".json") }?.mapNotNull { runCatching { fromJson(JSONObject(it.readText())) }.getOrNull() }
        ?.sortedBy { it.name.lowercase() }.orEmpty()

    fun save(c: Context, t: Template) = file(c, t.id).writeAtomic(t.toJson().toString())

    fun delete(c: Context, id: String) {
        file(c, id).delete()
    }

    fun create(c: Context, name: String, fields: List<NoteField> = emptyList()) =
        Template(System.currentTimeMillis().toString(), name.trim(), fields).also { save(c, it) }

    /** Scrive il template in un file da condividere. */
    fun exportFile(c: Context, t: Template): File {
        val dir = File(c.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, FileNames.clean(t.name).ifEmpty { "template" } + ".pastashoot.json")
        f.writeText(t.toJson().toString(2))
        return f
    }

    /**
     * Importa un template da un file. Se ce n'è già uno con lo stesso nome viene aggiornato,
     * altrimenti se ne crea uno nuovo. Restituisce il template e se ne ha sostituito uno.
     */
    fun import(c: Context, uri: Uri): Pair<Template, Boolean> {
        val text = c.contentResolver.openInputStream(uri)?.use { it.readLimited(1 * MB).decodeToString() }
            ?: error("File non leggibile")
        val t = fromJson(JSONObject(text))
        val existing = all(c).firstOrNull { it.name.equals(t.name, ignoreCase = true) }
        val saved = t.copy(id = existing?.id ?: System.currentTimeMillis().toString())
        save(c, saved)
        return saved to (existing != null)
    }

    /** Importa e avvisa con un messaggio. */
    fun importWithToast(c: Context, uri: Uri) {
        runCatching { import(c, uri) }
            .onSuccess { (t, replaced) ->
                Toast.makeText(c, "Template \"${t.name}\" ${if (replaced) "aggiornato" else "importato"}", Toast.LENGTH_LONG).show()
            }
            .onFailure { Toast.makeText(c, "Impossibile importare: ${it.message}", Toast.LENGTH_LONG).show() }
    }
}

/** Gestione dei template: elenco, nuovo, modifica, esporta, importa, elimina. */
@Composable
fun TemplatesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val templates = remember(tick) { TemplateStore.all(ctx) }
    var editing by remember { mutableStateOf<Template?>(null) }
    var askName by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf<Template?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { TemplateStore.importWithToast(ctx, uri); tick++ }
    }

    editing?.let { t ->
        LayoutScreen(
            title = t.name,
            initial = t.fields,
            sources = fieldSources(ctx, excludeTemplate = t.id),
            onDone = { TemplateStore.save(ctx, t.copy(fields = it)); editing = null; tick++ },
            onCancel = { editing = null },
        )
        return
    }
    BackHandler { onBack() }

    if (askName) PromptDialog(
        title = "Nuovo template",
        message = "Dai un nome al template, poi definisci i suoi campi.",
        placeholder = "es. Collaudo impianti",
        confirm = "Crea",
        onConfirm = { askName = false; editing = TemplateStore.create(ctx, it); tick++ },
        onDismiss = { askName = false },
    )
    askDelete?.let { t ->
        ConfirmDialog(
            title = "Eliminare \"${t.name}\"?",
            text = "I sopralluoghi già creati con questo template non cambiano.",
            confirm = "Elimina",
            onConfirm = { TemplateStore.delete(ctx, t.id); askDelete = null; tick++ },
            dismiss = "Annulla",
            onDismiss = { askDelete = null },
        )
    }

    AppScreen(
        "Template",
        subtitle = "Schemi di campi pronti per i sopralluoghi",
        maxWidth = ReadableWidth,
        onBack = onBack,
        actions = {
            ToolIcon(AppIcons.Add, "Nuovo template", { askName = true })
            OverflowMenu(listOf("Importa da file…" to { importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }))
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 4.dp, bottom = 16.dp)) {
            if (templates.isEmpty()) CenterMessage(
                "Nessun template. Creane uno con +, oppure importa un file .pastashoot.json ricevuto da un collega.",
                Modifier.fillMaxWidth(),
            )
            templates.forEachIndexed { i, t ->
                if (i > 0) ListDivider()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { editing = t },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Caption(t.fields.summary())
                    }
                    OverflowMenu(
                        listOf(
                            "Modifica campi…" to { editing = t },
                            "Esporta e condividi…" to {
                                runCatching { TemplateStore.exportFile(ctx, t) }
                                    .onSuccess { Exporter.share(ctx, it, "application/json", "Condividi il template") }
                            },
                            "Elimina…" to { askDelete = t },
                        )
                    )
                }
            }
            Caption(
                "Per passare un template a un altro telefono: Esporta e condividi, poi sull'altro telefono " +
                    "apri il file con Pastashoot oppure usa Importa da file.",
                Modifier.padding(top = 16.dp, end = 8.dp),
            )
        }
    }
}

/** Da dove copiare uno schema di campi: i template e i sopralluoghi che hanno dei campi. */
fun fieldSources(c: Context, excludeSession: String? = null, excludeTemplate: String? = null): List<FieldSource> =
    TemplateStore.all(c).filter { it.id != excludeTemplate && it.fields.isNotEmpty() }
        .map { FieldSource("Template · ${it.name}", it.fields) } +
        SessionStore.all(c).filter { it.id != excludeSession && it.fields.isNotEmpty() }
            .map { FieldSource(it.folder, it.fields) }
