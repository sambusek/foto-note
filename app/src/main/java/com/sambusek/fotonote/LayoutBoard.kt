package com.sambusek.fotonote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

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

    /** Concatena [dragId] (campo o catena) con [targetId]: dopo di lui nella sua catena, o in una catena nuova. */
    fun join(fs: List<NoteField>, dragId: String, targetId: String): List<NoteField> {
        if (dragId == targetId) return fs
        val drag = fs.firstOrNull { it.id == dragId } ?: return fs
        val target = fs.firstOrNull { it.id == targetId } ?: return fs
        val targetChain = if (target.kind == FieldKind.COMBINED) target else chainOf(fs, targetId)
        if (targetChain != null && (targetChain.id == dragId || dragId in targetChain.parts && target.kind == FieldKind.COMBINED)) return fs
        val moving = if (drag.kind == FieldKind.COMBINED) drag.parts else listOf(dragId)
        var l = if (drag.kind == FieldKind.COMBINED) fs.filter { it.id != dragId } else detach(fs, dragId)
        if (targetChain != null) {
            l = update(l, targetChain.id) { c ->
                val base = c.parts - moving.toSet()
                val at = if (target.kind == FieldKind.COMBINED) base.size else base.indexOf(targetId) + 1
                c.copy(parts = base.toMutableList().apply { addAll(at.coerceIn(0, base.size), moving) })
            }
        } else {
            val n = l.count { it.kind == FieldKind.COMBINED } + 1
            val chain = NoteField(
                System.nanoTime().toString(), "Codice" + if (n > 1) " $n" else "", target.pos,
                showLabel = false, kind = FieldKind.COMBINED, parts = listOf(targetId) + moving,
                placed = true, slot = target.slot,
            )
            // la catena prende il posto del campo su cui si è lasciato l'altro
            val i = l.indexOfFirst { it.id == targetId }
            l = l.toMutableList().apply { add(i.coerceAtLeast(0), chain) }
        }
        return normalize(l.map { if (it.id in moving) it.copy(placed = true) else it })
    }
}

private val SEPARATORS = listOf("_", "-", ".", " ")

/**
 * Lavagna del layout: le caselle sopra e sotto la foto e il vassoio dei campi non stampati.
 * Le etichette (pills) si trascinano tenendole premute, oppure si toccano e poi si tocca la destinazione:
 * su una casella si spostano, su un'altra etichetta si concatenano, sul vassoio escono dal layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutBoard(fields: SnapshotStateList<NoteField>) {
    val cs = MaterialTheme.colorScheme
    var selected by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf<Pair<String, Offset>?>(null) } // id e punto (coordinate radice)
    var origin by remember { mutableStateOf(Offset.Zero) }
    var editChain by remember { mutableStateOf<String?>(null) }
    val bounds = remember { HashMap<String, Rect>() }

    fun apply(l: List<NoteField>) { fields.clear(); fields.addAll(l) }
    fun name(f: NoteField) = f.label.trim().ifEmpty { f.kind.defaultLabel.ifEmpty { f.kind.label } }
    fun byId(id: String) = fields.firstOrNull { it.id == id }
    val inChains = grouped(fields)

    /** Azione su una destinazione: "pill:<id>", "cell:<POS>" oppure "tray". */
    fun act(id: String, target: String) {
        when {
            target.startsWith("pill:") -> {
                val t = target.removePrefix("pill:")
                // un'etichetta del vassoio non si concatena: lasciarci sopra vuol dire "non stampare"
                val inTray = byId(t)?.let { !it.placed && it.id !in grouped(fields) } == true
                apply(if (inTray) LayoutOps.unplace(fields, id) else LayoutOps.join(fields, id, t))
            }
            target.startsWith("cell:") -> apply(LayoutOps.moveTo(fields, id, FieldPos.valueOf(target.removePrefix("cell:"))))
            target == "tray" -> apply(LayoutOps.unplace(fields, id))
        }
        selected = null
    }

    fun drop(id: String, p: Offset) {
        val own = byId(id)?.takeIf { it.kind == FieldKind.COMBINED }?.parts.orEmpty() + id
        // prima le etichette (concatena), poi le caselle e il vassoio
        val hit = bounds.entries.filter { it.key.startsWith("pill:") && it.key.removePrefix("pill:") !in own && it.value.contains(p) }
            .minByOrNull { it.value.width }?.key
            ?: bounds.entries.firstOrNull { !it.key.startsWith("pill:") && it.value.contains(p) }?.key
        if (hit != null) act(id, hit)
    }

    @Composable
    fun Pill(id: String, text: String, auto: Boolean, chainHandle: Boolean = false) {
        val isSel = selected == id
        val dragging = drag?.first == id
        val tap by rememberUpdatedState {
            val s = selected
            when {
                s == null -> selected = id
                s == id -> selected = null
                else -> act(s, "pill:$id")
            }
        }
        val onDrop by rememberUpdatedState { i: String, p: Offset -> drop(i, p) }
        Text(
            if (chainHandle) "⛓ $text" else text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (chainHandle) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSel) cs.onPrimary else if (auto) cs.onSecondaryContainer else cs.onPrimaryContainer,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .alpha(if (dragging) 0.35f else 1f)
                .onGloballyPositioned { bounds["pill:$id"] = it.boundsInRoot() }
                .widthIn(max = 150.dp)
                .clip(RoundedCornerShape(50))
                .background(if (isSel) cs.primary else if (auto) cs.secondaryContainer else cs.primaryContainer)
                .pointerInput(id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { o ->
                            selected = null
                            drag = id to ((bounds["pill:$id"]?.topLeft ?: Offset.Zero) + o)
                        },
                        onDrag = { ch, d -> ch.consume(); drag = drag?.let { it.first to it.second + d } },
                        onDragEnd = { drag?.let { onDrop(it.first, it.second) }; drag = null },
                        onDragCancel = { drag = null },
                    )
                }
                .clickable { tap() }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }

    @Composable
    fun Item(f: NoteField) {
        if (f.kind != FieldKind.COMBINED) return Pill(f.id, name(f), f.auto)
        // catena: maniglia col nome, i campi con il separatore fra l'uno e l'altro, le impostazioni
        val members = f.parts.mapNotNull { byId(it) }
        FlowRow(
            Modifier.clip(RoundedCornerShape(18.dp)).border(1.5.dp, cs.primary, RoundedCornerShape(18.dp)).padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Pill(f.id, name(f), auto = false, chainHandle = true)
            members.forEachIndexed { i, m ->
                Text(
                    // tocco sul separatore: si passa al successivo
                    if (i == 0) ":" else if (f.separator == " ") "␣" else f.separator,
                    style = MaterialTheme.typography.titleMedium, color = Color.White,
                    modifier = Modifier.align(Alignment.CenterVertically)
                        .clickable(enabled = i > 0) {
                            val next = SEPARATORS[(SEPARATORS.indexOf(f.separator) + 1) % SEPARATORS.size]
                            apply(fields.map { if (it.id == f.id) it.copy(separator = next) else it })
                        }
                        .padding(horizontal = 5.dp),
                )
                Pill(m.id, name(m), m.auto)
            }
            ToolIcon(AppIcons.Tune, "Impostazioni della catena", { editChain = f.id }, modifier = Modifier.align(Alignment.CenterVertically))
        }
    }

    @Composable
    fun Band(pos: FieldPos, title: String, extra: String? = null) {
        val items = fields.filter { it.placed && it.pos.band == pos && it.id !in inChains && (it.auto || it.label.isNotBlank() || it.kind == FieldKind.COMBINED) }
            .sortedWith(compareBy({ it.pos.ordinal }, { it.slot }))
        val tap by rememberUpdatedState { selected?.let { act(it, "cell:${pos.name}") } }
        val active = selected != null || drag != null
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .onGloballyPositioned { bounds["cell:${pos.name}"] = it.boundsInRoot() }
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2B2B2B))
                .border(if (active) 2.dp else 1.dp, if (active) cs.primary else Color(0xFF555555), RoundedCornerShape(8.dp))
                .clickable(enabled = selected != null) { tap() }
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color(0xFFAAAAAA))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (f in items) Item(f)
                if (items.isEmpty()) Text("vuota", style = MaterialTheme.typography.labelMedium, color = Color(0xFF888888))
            }
            extra?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Color(0xFFCCCCCC)) }
        }
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val sel = selected?.let { byId(it) }
            if (sel != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(
                    "«${name(sel)}»: tocca una banda per spostarlo, un'altra etichetta per concatenarli, " +
                        "il vassoio per non stamparlo.",
                    Modifier.weight(1f),
                )
                // ordine nella banda (o nella catena)
                ToolIcon(AppIcons.Prev, "Prima", { apply(LayoutOps.shift(fields, sel.id, -1)) })
                ToolIcon(AppIcons.Next, "Dopo", { apply(LayoutOps.shift(fields, sel.id, 1)) })
                ToolIcon(AppIcons.Close, "Annulla", { selected = null })
            } else Caption(
                "Tieni premuta un'etichetta e trascinala, oppure toccala e poi tocca dove va. " +
                    "Lasciata su un'altra etichetta, la concatena."
            )
            Band(FieldPos.TOP, "Banda superiore")
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF8A8A8A)),
                contentAlignment = Alignment.Center,
            ) { Text("FOTO", color = Color(0xFFE6E6E6), style = MaterialTheme.typography.labelLarge) }
            Band(FieldPos.BOTTOM, "Banda inferiore", extra = "+ nota della foto, a capo")
            Caption("Sulla foto le etichette di una banda si stampano in fila, separate da un punto.")

            // vassoio: campi che si compilano ma non si stampano
            val tray = fields.filter { !it.placed && it.id !in inChains && it.kind != FieldKind.COMBINED && (it.auto || it.label.isNotBlank()) }
            val trayTap by rememberUpdatedState { selected?.let { act(it, "tray") } }
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .onGloballyPositioned { bounds["tray"] = it.boundsInRoot() }
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, if (selected != null || drag != null) cs.primary else cs.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable(enabled = selected != null) { trayTap() }
                    .padding(8.dp),
            ) {
                Text("Non stampati", style = MaterialTheme.typography.titleSmall)
                Caption("Si compilano e valgono per i nomi dello ZIP e per l'ordine, ma non vanno sulla foto.")
                FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (tray.isEmpty()) Caption("Vuoto")
                    for (f in tray) Pill(f.id, name(f), f.auto)
                }
            }
        }

        // etichetta che segue il dito durante il trascinamento
        drag?.let { (id, p) ->
            val f = byId(id)
            Text(
                f?.let { (if (it.kind == FieldKind.COMBINED) "⛓ " else "") + name(it) }.orEmpty(),
                style = MaterialTheme.typography.labelLarge, color = cs.onPrimary,
                modifier = Modifier
                    .zIndex(10f)
                    .offset { IntOffset((p.x - origin.x - 30.dp.toPx()).roundToInt(), (p.y - origin.y - 40.dp.toPx()).roundToInt()) }
                    .clip(RoundedCornerShape(50))
                    .background(cs.primary)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }

    editChain?.let { id ->
        val c = byId(id)
        if (c == null) { editChain = null; return@let }
        var label by remember(id) { mutableStateOf(c.label) }
        AlertDialog(
            onDismissRequest = { editChain = null },
            shape = RoundedCornerShape(12.dp),
            title = { Text("Catena") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Caption("Il nome serve nei nomi dello ZIP, es. {${label.ifBlank { "Codice" }}}.")
                    CompactField(label, { label = it }, "Nome, es. Codice")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (sep in SEPARATORS) FilterChip(
                            selected = c.separator == sep,
                            onClick = { apply(fields.map { if (it.id == id) it.copy(separator = sep) else it }) },
                            label = { Text(if (sep == " ") "spazio" else sep) },
                        )
                    }
                    Caption("Sulla foto: " + c.parts.mapNotNull { byId(it) }.joinToString(c.separator) { name(it) })
                }
            },
            confirmButton = {
                TextButton({
                    apply(fields.map { if (it.id == id) it.copy(label = label.trim().ifEmpty { "Codice" }) else it })
                    editChain = null
                }) { Text("OK") }
            },
            dismissButton = {
                // sciogliere: i campi restano nella stessa casella, uno per riga
                TextButton({
                    apply(LayoutOps.dissolve(fields, id)); editChain = null
                }) { Text("Sciogli") }
            },
        )
    }
}
