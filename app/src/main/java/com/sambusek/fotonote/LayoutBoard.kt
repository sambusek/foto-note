package com.sambusek.fotonote

import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Operazioni sul layout. Un elemento del layout è un campo posizionato oppure una catena
 * (campo [FieldKind.COMBINED]) che stampa in una riga i campi che unisce. I campi fuori dal
 * layout ([NoteField.placed] falso) si compilano ma non si stampano.
 */
object LayoutOps {
    private fun chainOf(fs: List<NoteField>, id: String) = fs.firstOrNull { it.kind == FieldKind.COMBINED && id in it.parts }

    private fun nextSlot(fs: List<NoteField>, pos: FieldPos) =
        (fs.filter { it.placed && it.pos == pos && it.id !in grouped(fs) }.maxOfOrNull { it.slot } ?: -1) + 1

    private fun update(fs: List<NoteField>, id: String, f: (NoteField) -> NoteField) = fs.map { if (it.id == id) f(it) else it }

    /** Catene con meno di due campi: si sciolgono e il campo rimasto prende il posto della catena. */
    fun normalize(fs: List<NoteField>): List<NoteField> {
        var l = fs
        for (c in fs.filter { it.kind == FieldKind.COMBINED }) {
            val parts = c.parts.filter { id -> l.any { it.id == id } }
            if (parts.size >= 2) { if (parts != c.parts) l = update(l, c.id) { it.copy(parts = parts) }; continue }
            l = l.filter { it.id != c.id }
            parts.firstOrNull()?.let { m -> l = update(l, m) { it.copy(placed = true, pos = c.pos, slot = c.slot) } }
        }
        return l
    }

    private fun detach(fs: List<NoteField>, id: String): List<NoteField> =
        chainOf(fs, id)?.let { c -> update(fs, c.id) { it.copy(parts = it.parts - id) } } ?: fs

    /** Porta un campo o una catena in fondo alla casella [pos]. */
    fun moveTo(fs: List<NoteField>, id: String, pos: FieldPos): List<NoteField> {
        var l = detach(fs, id)
        l = update(l, id) { it.copy(placed = true, pos = pos, slot = nextSlot(l.filter { x -> x.id != id }, pos)) }
        return normalize(l)
    }

    /** Scioglie una catena: i suoi campi restano nella stessa casella, uno per riga, al suo posto. */
    fun dissolve(fs: List<NoteField>, chainId: String): List<NoteField> {
        val c = fs.firstOrNull { it.id == chainId } ?: return fs
        val cell = fs.filter { it.placed && it.pos == c.pos && it.id !in grouped(fs) }.sortedBy { it.slot }
        val order = cell.flatMap { if (it.id == chainId) c.parts else listOf(it.id) }
        return fs.filter { it.id != chainId }.map { f ->
            val i = order.indexOf(f.id)
            if (i >= 0) f.copy(placed = true, pos = c.pos, slot = i) else f
        }
    }

    /** Sposta un elemento di un posto nella sua banda, o un campo di un posto nella sua catena. */
    fun shift(fs: List<NoteField>, id: String, d: Int): List<NoteField> {
        chainOf(fs, id)?.let { c ->
            val i = c.parts.indexOf(id); val j = i + d
            if (j !in c.parts.indices) return fs
            return update(fs, c.id) { it.copy(parts = it.parts.toMutableList().apply { add(j, removeAt(i)) }) }
        }
        val f = fs.firstOrNull { it.id == id } ?: return fs
        val band = fs.filter { it.placed && it.pos == f.pos && it.id !in grouped(fs) }.sortedBy { it.slot }.toMutableList()
        val i = band.indexOfFirst { it.id == id }; val j = i + d
        if (i < 0 || j !in band.indices) return fs
        band.add(j, band.removeAt(i))
        val slots = band.withIndex().associate { it.value.id to it.index }
        return fs.map { x -> slots[x.id]?.let { x.copy(slot = it) } ?: x }
    }

    /** Toglie dal layout: il campo resta da compilare. Una catena si scioglie e i suoi campi escono. */
    fun unplace(fs: List<NoteField>, id: String): List<NoteField> {
        val f = fs.firstOrNull { it.id == id } ?: return fs
        if (f.kind == FieldKind.COMBINED) {
            return fs.filter { it.id != id }.map { if (it.id in f.parts) it.copy(placed = false) else it }
        }
        return normalize(update(detach(fs, id), id) { it.copy(placed = false) })
    }

    /** Crea o aggiorna una catena; i campi tolti tornano da soli in fondo alla banda della catena. */
    fun saveChain(
        fs: List<NoteField>, chainId: String?, name: String, parts: List<String>, separator: String, band: FieldPos,
        highlight: Boolean = false,
    ): List<NoteField> {
        val old = chainId?.let { id -> fs.firstOrNull { it.id == id } }
        var l = fs
        // un campo può stare in una sola catena
        l = l.map { c -> if (c.kind == FieldKind.COMBINED && c.id != chainId && c.parts.any { it in parts }) c.copy(parts = c.parts - parts.toSet()) else c }
        if (old == null) {
            val chain = NoteField(
                System.nanoTime().toString(), name.trim().ifEmpty { "Codice" }, band, showLabel = false,
                kind = FieldKind.COMBINED, parts = parts, separator = separator, placed = true, slot = nextSlot(l, band),
                highlight = highlight,
            )
            l = l + chain
        } else {
            val moved = old.pos != band
            l = update(l, old.id) {
                it.copy(label = name.trim().ifEmpty { "Codice" }, parts = parts, separator = separator, pos = band,
                    placed = true, slot = if (moved) nextSlot(l, band) else it.slot, highlight = highlight)
            }
            var next = nextSlot(l, band)
            for (gone in old.parts - parts.toSet()) l = update(l, gone) { it.copy(placed = true, pos = band, slot = next++) }
        }
        return normalize(l)
    }
}

private val SEPARATORS = listOf("_", "-", ".", " ")

/**
 * Lavagna del layout: le due bande sopra e sotto la foto e il vassoio dei campi non stampati.
 * Un tocco su un'etichetta apre le sue azioni (dove stamparla, prima o dopo); le catene si
 * creano e si modificano in una finestra dedicata.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutBoard(fields: SnapshotStateList<NoteField>) {
    val cs = MaterialTheme.colorScheme
    var chainEdit by remember { mutableStateOf<String?>(null) } // id della catena, oppure "" per una nuova

    fun apply(l: List<NoteField>) { fields.clear(); fields.addAll(l) }
    fun name(f: NoteField) = f.label.trim().ifEmpty { f.kind.defaultLabel.ifEmpty { f.kind.label } }
    fun byId(id: String) = fields.firstOrNull { it.id == id }
    val inChains = grouped(fields)
    fun chainText(c: NoteField) = c.parts.mapNotNull { byId(it) }.joinToString(c.separator) { name(it) }
    val free = fields.filter { it.kind != FieldKind.COMBINED && it.id !in inChains && (it.auto || it.label.isNotBlank()) }

    @Composable
    fun Pill(f: NoteField) {
        var menu by remember { mutableStateOf(false) }
        val chain = f.kind == FieldKind.COMBINED
        Box {
            Text(
                if (chain) "⛓ ${name(f)}: ${chainText(f)}" else name(f),
                style = MaterialTheme.typography.labelLarge,
                color = if (chain) cs.onPrimary else if (f.auto) cs.onSecondaryContainer else cs.onPrimaryContainer,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (chain) cs.primary else if (f.auto) cs.secondaryContainer else cs.primaryContainer)
                    .then(if (f.highlight) Modifier.border(2.dp, Color(0xFFFFB81C), RoundedCornerShape(50)) else Modifier)
                    .clickable { menu = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
            DropdownMenu(menu, { menu = false }) {
                fun go(l: List<NoteField>) { menu = false; apply(l) }
                if (chain) DropdownMenuItem(text = { Text("Modifica la catena…") }, onClick = { menu = false; chainEdit = f.id })
                if (!f.placed || f.pos.band != FieldPos.TOP) DropdownMenuItem(
                    text = { Text("Stampa nella banda superiore") }, onClick = { go(LayoutOps.moveTo(fields, f.id, FieldPos.TOP)) },
                )
                if (!f.placed || f.pos.band != FieldPos.BOTTOM) DropdownMenuItem(
                    text = { Text("Stampa nella banda inferiore") }, onClick = { go(LayoutOps.moveTo(fields, f.id, FieldPos.BOTTOM)) },
                )
                if (f.placed) {
                    DropdownMenuItem(text = { Text("◀  Prima") }, onClick = { go(LayoutOps.shift(fields, f.id, -1)) })
                    DropdownMenuItem(text = { Text("Dopo  ▶") }, onClick = { go(LayoutOps.shift(fields, f.id, 1)) })
                    DropdownMenuItem(
                        text = { Text(if (chain) "Sciogli la catena" else "Non stampare") },
                        onClick = { go(if (chain) LayoutOps.dissolve(fields, f.id) else LayoutOps.unplace(fields, f.id)) },
                    )
                }
            }
        }
    }

    @Composable
    fun Band(pos: FieldPos, title: String, extra: String? = null) {
        val items = fields.filter { it.placed && it.pos.band == pos && it.id !in inChains && (it.auto || it.label.isNotBlank() || it.kind == FieldKind.COMBINED) }
            .sortedWith(compareBy({ it.pos.ordinal }, { it.slot }))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2B2B2B))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (f in items) Pill(f)
                if (items.isEmpty()) Text("vuota", style = MaterialTheme.typography.labelMedium, color = Color(0xFF888888))
            }
            extra?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFCCCCCC)) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Caption("Tocca un'etichetta per scegliere dove stamparla e in che ordine.")
        Band(FieldPos.TOP, "Banda superiore")
        Box(
            Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF8A8A8A)),
            contentAlignment = Alignment.Center,
        ) { Text("FOTO", color = Color(0xFFE6E6E6), style = MaterialTheme.typography.labelLarge) }
        Band(FieldPos.BOTTOM, "Banda inferiore", extra = "+ nota della foto, a capo")
        Caption("Sulla foto le etichette di una banda si stampano in fila, separate da un punto.")
        if (free.size >= 2) AppButton(
            "Nuova catena…", { chainEdit = "" }, Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        // vassoio: campi che si compilano ma non si stampano
        val tray = fields.filter { !it.placed && it.id !in inChains && it.kind != FieldKind.COMBINED && (it.auto || it.label.isNotBlank()) }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, cs.outlineVariant, RoundedCornerShape(10.dp))
                .padding(8.dp),
        ) {
            Text("Non stampati", style = MaterialTheme.typography.titleSmall)
            Caption("Si compilano e valgono per i nomi dello ZIP e per l'ordine, ma non vanno sulla foto.")
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tray.isEmpty()) Caption("Vuoto")
                for (f in tray) Pill(f)
            }
        }
    }

    chainEdit?.let { id ->
        val chain = id.takeIf { it.isNotEmpty() }?.let { byId(it) }
        // campi disponibili: quelli liberi e quelli di questa catena
        val candidates = fields.filter { f ->
            f.kind != FieldKind.COMBINED && (f.auto || f.label.isNotBlank()) && (f.id !in inChains || chain != null && f.id in chain.parts)
        }
        ChainDialog(
            chain, candidates, ::name,
            onSave = { label, parts, sep, band, hl -> apply(LayoutOps.saveChain(fields, chain?.id, label, parts, sep, band, hl)); chainEdit = null },
            onDissolve = chain?.let { c -> { apply(LayoutOps.dissolve(fields, c.id)); chainEdit = null } },
            onDismiss = { chainEdit = null },
        )
    }
}

/** Crea o modifica una catena: campi nell'ordine in cui si toccano, frecce per riordinare, separatore, banda. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChainDialog(
    chain: NoteField?,
    candidates: List<NoteField>,
    name: (NoteField) -> String,
    onSave: (label: String, parts: List<String>, separator: String, band: FieldPos, highlight: Boolean) -> Unit,
    onDissolve: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var label by remember { mutableStateOf(chain?.label ?: "Codice") }
    var sep by remember { mutableStateOf(chain?.separator ?: "_") }
    var band by remember { mutableStateOf(chain?.pos?.band ?: FieldPos.BOTTOM) }
    var highlight by remember { mutableStateOf(chain?.highlight ?: false) }
    val order = remember { mutableStateListOf<String>().apply { addAll(chain?.parts.orEmpty()) } }
    fun f(id: String) = candidates.firstOrNull { it.id == id }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text(if (chain == null) "Nuova catena" else "Catena") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Caption("Tocca i campi nell'ordine in cui vanno uniti.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in candidates) {
                        val n = order.indexOf(c.id)
                        FilterChip(
                            selected = n >= 0,
                            onClick = { if (n >= 0) order.remove(c.id) else order.add(c.id) },
                            label = { Text(if (n >= 0) "${n + 1}  ${name(c)}" else name(c)) },
                        )
                    }
                }
                if (order.isNotEmpty()) {
                    Text("Ordine", style = MaterialTheme.typography.titleSmall)
                    order.forEachIndexed { i, id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.  ${f(id)?.let(name).orEmpty()}", Modifier.weight(1f))
                            ToolIcon(AppIcons.Prev, "Su", { order.add(i - 1, order.removeAt(i)) }, enabled = i > 0, modifier = Modifier.rotate(90f))
                            ToolIcon(AppIcons.Next, "Giù", { order.add(i + 1, order.removeAt(i)) }, enabled = i < order.lastIndex, modifier = Modifier.rotate(90f))
                        }
                    }
                }
                Text("Separatore", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in SEPARATORS) FilterChip(selected = sep == s, onClick = { sep = s }, label = { Text(if (s == " ") "spazio" else s) })
                }
                Text("Banda", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = band == FieldPos.TOP, onClick = { band = FieldPos.TOP }, label = { Text("Superiore") })
                    FilterChip(selected = band == FieldPos.BOTTOM, onClick = { band = FieldPos.BOTTOM }, label = { Text("Inferiore") })
                }
                CompactField(label, { label = it }, "Nome della catena (per lo ZIP), es. Codice")
                SwitchRow("In risalto: più grande, grassetto, giallo", highlight, { highlight = !highlight })
                if (order.size >= 2) Text(
                    "Sulla foto: " + order.mapNotNull { f(it) }.joinToString(sep) { name(it) },
                    style = MaterialTheme.typography.bodyMedium, color = cs.primary,
                ) else Caption("Scegli almeno due campi.")
            }
        },
        confirmButton = {
            TextButton({ onSave(label, order.toList(), sep, band, highlight) }, enabled = order.size >= 2) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (onDissolve != null) TextButton(onDissolve) { Text("Sciogli") }
                TextButton(onDismiss) { Text("Annulla") }
            }
        },
    )
}
