package com.sambusek.fotonote

import androidx.compose.ui.draw.rotate
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Editor del layout delle note di una sessione: campi con nome, tipo e posizione. */
@Composable
fun LayoutScreen(
    initial: List<NoteField>,
    sources: List<FieldSource>,
    onDone: (List<NoteField>) -> Unit,
    onCancel: () -> Unit,
    title: String = "Layout delle note",
    onSaveAsTemplate: ((String, List<NoteField>) -> Unit)? = null,
) {
    val fields = remember { mutableStateListOf<NoteField>().apply { addAll(initial) } }
    var askTemplate by remember { mutableStateOf(false) }
    BackHandler { onCancel() }

    fun cleaned() = fields.filter { it.auto || it.label.isNotBlank() }
        .map { it.copy(label = it.label.trim(), text = it.text.trim(), choices = it.options.joinToString(", ")) }

    if (askTemplate && onSaveAsTemplate != null) PromptDialog(
        title = "Salva come template",
        message = "I campi di questo layout diventano un template riutilizzabile nei prossimi sopralluoghi.",
        placeholder = "Nome del template",
        confirm = "Salva",
        onConfirm = { askTemplate = false; onSaveAsTemplate(it, cleaned()) },
        onDismiss = { askTemplate = false },
    )

    AppScreen(
        maxWidth = ReadableWidth,
        title = title,
        subtitle = if (title != "Layout delle note") "Campi del template" else null,
        onBack = onCancel,
        actions = {
            if (onSaveAsTemplate != null) OverflowMenu(listOf("Salva come template…" to { askTemplate = true }))
            BarAction("Salva", { onDone(cleaned()) }, primary = true)
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
        ) {
            Caption(
                "I testi stanno in due strisce aggiunte sopra e sotto la foto, mai sopra la foto. " +
                    "I campi automatici (*) compaiono da soli su tutte le foto."
            )
            if (sources.isNotEmpty()) CopyFromButton(sources) { src ->
                fields.clear()
                fields += src.fields
            }
            Section("Schema") { LayoutPreview(fields) }
            Section("Campi") {
                if (fields.isEmpty()) Caption(
                    "Nessun campo: sulle foto ci sarà solo la nota, nella banda inferiore. " +
                        "Aggiungi un campo o copia lo schema di un template o di un sopralluogo precedente."
                )
                fields.forEachIndexed { i, f ->
                    key(f.id) {
                        if (i > 0) ListDivider()
                        FieldEditor(
                            f, onChange = { fields[i] = it }, onRemove = { fields.removeAt(i) },
                            onUp = if (i > 0) ({ fields.add(i - 1, fields.removeAt(i)) }) else null,
                            onDown = if (i < fields.lastIndex) ({ fields.add(i + 1, fields.removeAt(i)) }) else null,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                AddFieldButton { kind ->
                    fields += NoteField(
                        System.nanoTime().toString(), kind.defaultLabel, kind.defaultPos,
                        showLabel = !kind.auto, kind = kind,
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldEditor(
    f: NoteField,
    onChange: (NoteField) -> Unit,
    onRemove: () -> Unit,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // tipo del campo, sempre visibile e modificabile
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindPicker(f.kind, Modifier.weight(1f)) { k ->
                onChange(f.copy(kind = k, label = f.label.ifBlank { k.defaultLabel }, showLabel = !k.auto))
            }
            // ordine dei campi: è anche l'ordine nel pannello dell'editor e sulla fascia
            ToolIcon(AppIcons.Prev, "Sposta su", { onUp?.invoke() }, enabled = onUp != null, modifier = Modifier.rotate(90f))
            ToolIcon(AppIcons.Next, "Sposta giù", { onDown?.invoke() }, enabled = onDown != null, modifier = Modifier.rotate(90f))
            ToolIcon(AppIcons.Delete, "Elimina campo", onRemove)
        }
        Caption(f.kind.description)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CompactField(
                f.label, { onChange(f.copy(label = it)) },
                if (f.auto) "Etichetta (facoltativa)" else "Nome del campo, es. Piano", Modifier.weight(1f),
            )
            PositionPicker(f.pos) { onChange(f.copy(pos = it)) }
        }
        when (f.kind) {
            FieldKind.CHOICE -> ChoiceEditor(f, onChange)
            FieldKind.FIXED -> CompactField(f.text, { onChange(f.copy(text = it)) }, "Testo da stampare su ogni foto")
            else -> {}
        }
        // con l'etichetta: "Piano: 3"; senza: solo "3"
        SwitchRow("Mostra l'etichetta prima del testo", f.showLabel, { onChange(f.copy(showLabel = !f.showLabel)) })
    }
}

/** Voci di un elenco di scelte: scritte a mano o importate da un file di testo / CSV. */
@Composable
private fun ChoiceEditor(f: NoteField, onChange: (NoteField) -> Unit) {
    val ctx = LocalContext.current
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ChoiceImport.read(ctx, uri) }
            .onSuccess { items ->
                if (items.isEmpty()) {
                    Toast.makeText(ctx, "Nessuna voce trovata nel file", Toast.LENGTH_SHORT).show()
                } else {
                    val merged = (f.options + items).distinct()
                    onChange(f.copy(choices = merged.joinToString(", ")))
                    Toast.makeText(ctx, "${merged.size - f.options.size} voci aggiunte", Toast.LENGTH_SHORT).show()
                }
            }
            .onFailure { Toast.makeText(ctx, "File non leggibile: ${it.message}", Toast.LENGTH_LONG).show() }
    }
    CompactField(f.choices, { onChange(f.copy(choices = it)) }, "Voci separate da virgola, es. P3, P4, P5", singleLine = false, maxHeight = 96.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Caption(if (f.options.isEmpty()) "Nessuna voce" else "${f.options.size} voci", Modifier.weight(1f))
        if (f.options.isNotEmpty()) BarAction("Svuota", { onChange(f.copy(choices = "")) })
        BarAction("Importa da file…", { importer.launch(ChoiceImport.MIME_TYPES) })
    }
}

/** Lettura delle voci da un file .txt o .csv. */
object ChoiceImport {
    val MIME_TYPES = arrayOf(
        "text/plain", "text/csv", "text/comma-separated-values", "application/csv",
        "text/tab-separated-values", "application/vnd.ms-excel", "application/octet-stream",
    )

    /**
     * Una voce per riga (di un CSV si prende la prima colonna); se il file ha una sola riga,
     * le voci sono separate da virgola, punto e virgola o tabulazione. Righe vuote e doppioni
     * vengono scartati; le virgole dentro una voce diventano spazi.
     */
    fun read(c: Context, uri: Uri): List<String> {
        val text = c.contentResolver.openInputStream(uri)?.use { it.readLimited(2 * MB).decodeToString() }
            ?: error("impossibile aprirlo")
        val lines = text.removePrefix("\uFEFF").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val sep = Regex("[;,\t]")
        val raw = if (lines.size == 1) lines[0].split(sep) else lines.map { it.split(sep).first() }
        return raw.map { it.trim().trim('"').trim().replace(",", " ") }.filter { it.isNotEmpty() }.distinct()
    }
}

/** Voci di menu per i tipi di campo: nome e spiegazione. */
@Composable
private fun KindMenuItems(onPick: (FieldKind) -> Unit) {
    for (k in FieldKind.entries) DropdownMenuItem(
        text = {
            Column {
                Text(k.label)
                Caption(k.description)
            }
        },
        onClick = { onPick(k) },
    )
}

/** Il tipo del campo; toccandolo se ne sceglie un altro. */
@Composable
private fun KindPicker(kind: FieldKind, modifier: Modifier, onPick: (FieldKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        // etichetta del tipo: fondo d'accento e testo a contrasto
        val cs = MaterialTheme.colorScheme
        Row(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(cs.primary)
                .clickable { open = true }
                .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tipo: ${kind.label}", style = MaterialTheme.typography.titleSmall, color = cs.onPrimary)
            Icon(AppIcons.DropDown, "Cambia tipo", tint = cs.onPrimary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            KindMenuItems { open = false; onPick(it) }
        }
    }
}

/** "Aggiungi campo…" con la scelta del tipo. */
@Composable
private fun AddFieldButton(onPick: (FieldKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton("Aggiungi campo…", { open = true }, primary = true)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            KindMenuItems { open = false; onPick(it) }
        }
    }
}

/** Uno schema di campi da cui copiare: un template o un sopralluogo. */
class FieldSource(val title: String, val fields: List<NoteField>)

/** Copia lo schema dei campi da un template o da un sopralluogo (sostituisce quello attuale). */
@Composable
private fun CopyFromButton(sources: List<FieldSource>, onPick: (FieldSource) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 8.dp)) {
        AppButton("Copia i campi da…", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (src in sources) DropdownMenuItem(
                text = {
                    Column {
                        Text(src.title)
                        Caption(src.fields.summary())
                    }
                },
                onClick = { open = false; onPick(src) },
            )
        }
    }
}

@Composable
private fun PositionPicker(pos: FieldPos, onPick: (FieldPos) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton(pos.short, { open = true }, Modifier.width(118.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (p in FieldPos.entries) DropdownMenuItem(text = { Text(p.label) }, onClick = { open = false; onPick(p) })
        }
    }
}

/**
 * Schema dello spazio per le note: striscia aggiunta sopra e sotto la foto, ciascuna divisa
 * in angolo sx (20%), banda centrale (60%) e angolo dx (20%), con dentro i nomi dei campi.
 */
@Composable
private fun LayoutPreview(fields: List<NoteField>) {
    fun names(pos: FieldPos) = fields.filter { it.pos == pos && (it.auto || it.label.isNotBlank()) }.map {
        if (it.auto) it.label.trim().ifEmpty { it.kind.defaultLabel } + "*" else it.label.trim()
    }
    val grey = Color(0xFF777777)

    @Composable
    fun RowScope.cell(weight: Float, empty: String, items: List<String>, align: TextAlign) = Box(
        Modifier.weight(weight).fillMaxHeight().border(0.5.dp, grey).padding(horizontal = 4.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (items.isEmpty()) empty else items.joinToString("\n"),
            fontSize = 10.sp, lineHeight = 13.sp, textAlign = align, modifier = Modifier.fillMaxWidth(),
            color = if (items.isEmpty()) grey else Color.White,
        )
    }

    @Composable
    fun strip(left: FieldPos, center: FieldPos, right: FieldPos, centerName: String, extra: List<String>) =
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Color.Black)) {
            cell(Strip.SIDE, "sx", names(left), TextAlign.Start)
            cell(1f - 2 * Strip.SIDE, centerName, names(center) + extra, TextAlign.Center)
            cell(Strip.SIDE, "dx", names(right), TextAlign.End)
        }

    Column(Modifier.fillMaxWidth()) {
        Caption("↓ spazio aggiunto")
        strip(FieldPos.TL, FieldPos.TOP, FieldPos.TR, "banda superiore", emptyList())
        Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF8A8A8A)), contentAlignment = Alignment.Center) {
            Text("FOTO\nrisoluzione nativa", fontSize = 12.sp, color = Color(0xFFE0E0E0), textAlign = TextAlign.Center)
        }
        // la nota libera sta sempre al centro della banda inferiore
        strip(FieldPos.BL, FieldPos.BOTTOM, FieldPos.BR, "banda inferiore", listOf("Nota"))
        Caption("↑ spazio aggiunto · angoli 20%, banda centrale 60%")
    }
}

/** Dialogo per dare lo stesso testo a più foto: nota libera o uno dei campi del layout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiTextDialog(
    count: Int,
    fields: List<NoteField>,
    initialFor: (String?) -> String,
    onConfirm: (fieldId: String?, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf<String?>(null) } // null = nota libera
    var text by remember(target) { mutableStateOf(initialFor(target)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text("Testo per $count foto") },
        text = {
            Column {
                Text(
                    "Sostituisce il testo scelto sulle foto selezionate; il resto e i disegni restano. " +
                        "Lascia vuoto per toglierlo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (fields.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for ((id, name) in listOf<Pair<String?, String>>(null to "Nota") + fields.map { it.id to it.label }) {
                        FilterChip(selected = target == id, onClick = { target = id }, label = { Text(name) })
                    }
                }
                val options = fields.firstOrNull { it.id == target }?.options.orEmpty()
                if (options.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (o in options) FilterChip(selected = text == o, onClick = { text = o }, label = { Text(o) })
                    FilterChip(selected = text.isEmpty(), onClick = { text = "" }, label = { Text("Nessuna") })
                }
                if (options.isEmpty()) OutlinedTextField(
                    value = text, onValueChange = { text = it }, placeholder = { Text("Scrivi qui") },
                    minLines = 2, shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton({ onConfirm(target, text.trim()) }) { Text("Applica") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}
