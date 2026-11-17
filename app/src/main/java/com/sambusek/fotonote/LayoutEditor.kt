package com.sambusek.fotonote

import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Editor del layout delle note di una sessione: campi con nome e posizione. */
@Composable
fun LayoutScreen(initial: List<NoteField>, onDone: (List<NoteField>) -> Unit, onCancel: () -> Unit) {
    val fields = remember { mutableStateListOf<NoteField>().apply { addAll(initial) } }
    BackHandler { onCancel() }

    DeskScreen {
        RetroWindow(
            title = "Layout delle note",
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 9.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                PixelCaption(
                    "Definisci i campi da compilare su ogni foto di questo sopralluogo e dove stamparli. " +
                        "I testi stanno sempre in due strisce aggiunte sopra e sotto la foto, mai sopra la foto.",
                    Modifier.padding(top = 10.dp),
                )
                GroupBox("Schema") { LayoutPreview(fields) }
                GroupBox("Campi") {
                    if (fields.isEmpty()) {
                        PixelCaption("Nessun campo: sulle foto ci sarà solo la nota, nella banda inferiore.")
                        Spacer(Modifier.height(8.dp))
                    }
                    fields.forEachIndexed { i, f ->
                        key(f.id) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                                PixelCaption(
                                    if (f.auto) "${f.kind.label} · automatico, su tutte le foto"
                                    else f.kind.label
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    RetroTextField(
                                        f.label,
                                        { fields[i] = f.copy(label = it.replace("\n", " ")) },
                                        if (f.auto) "Etichetta" else "es. Piano",
                                        Modifier.weight(1f),
                                        minHeight = 40.dp, maxHeight = 40.dp,
                                    )
                                    PosButton(f.pos) { fields[i] = f.copy(pos = it) }
                                    BevelBox(null, Modifier.size(44.dp), onClick = { fields.removeAt(i) }) {
                                        BevelValue("✕", size = 16)
                                    }
                                }
                                if (f.kind == FieldKind.FIXED) {
                                    Spacer(Modifier.height(6.dp))
                                    RetroTextField(
                                        f.text,
                                        { fields[i] = f.copy(text = it.replace("\n", " ")) },
                                        "Testo stampato su ogni foto",
                                        minHeight = 40.dp, maxHeight = 40.dp,
                                    )
                                }
                                // con l'etichetta: "Piano: 3"; senza: solo "3"
                                RetroCheckbox(
                                    f.showLabel, "Mostra l'etichetta prima del testo",
                                    { fields[i] = f.copy(showLabel = !f.showLabel) },
                                )
                            }
                        }
                    }
                    AddFieldButton { kind ->
                        fields.add(
                            NoteField(
                                System.nanoTime().toString(), kind.defaultLabel, kind.defaultPos,
                                showLabel = kind == FieldKind.INPUT, kind = kind,
                            )
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PushButton("Annulla", onCancel)
                PushButton("Salva layout", {
                    onDone(
                        fields.filter { it.auto || it.label.isNotBlank() }
                            .map { it.copy(label = it.label.trim(), text = it.text.trim()) }
                    )
                }, default = true)
            }
        }
    }
}

/** "Aggiungi campo…" con la scelta del tipo: da compilare o automatico. */
@Composable
private fun AddFieldButton(onPick: (FieldKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PushButton("Aggiungi campo…", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.menuFrame()) {
            for (k in FieldKind.entries) {
                Text(
                    tx(k.label),
                    fontFamily = Sys,
                    fontSize = 15.sp,
                    color = P.Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open = false; onPick(k) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun PosButton(pos: FieldPos, onPick: (FieldPos) -> Unit) {
    var open by remember { mutableStateOf(false) }
    BevelBox(null, Modifier.width(124.dp).height(44.dp), onClick = { open = true }) {
        BevelValue(pos.short, size = 13)
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.menuFrame(),
        ) {
            for (p in FieldPos.entries) {
                Text(
                    tx(p.label),
                    fontFamily = Sys,
                    fontWeight = if (p == pos) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 15.sp,
                    color = P.Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open = false; onPick(p) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
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

    @Composable
    fun RowScope.cell(weight: Float, empty: String, items: List<String>, align: TextAlign) {
        Box(
            Modifier
                .weight(weight)
                .fillMaxHeight()
                .border(0.5.dp, Color(0xFF777777))
                .padding(horizontal = 4.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (items.isEmpty()) empty else items.joinToString("\n"),
                fontFamily = Pixel, fontSize = 10.sp, lineHeight = 13.sp,
                color = if (items.isEmpty()) Color(0xFF777777) else Color.White,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    fun strip(left: FieldPos, center: FieldPos, right: FieldPos, centerName: String, extra: List<String>) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(Color.Black)) {
            cell(Strip.SIDE, "sx", names(left), TextAlign.Start)
            cell(1f - 2 * Strip.SIDE, centerName, names(center) + extra, TextAlign.Center)
            cell(Strip.SIDE, "dx", names(right), TextAlign.End)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        PixelCaption("↓ spazio aggiunto")
        Column(Modifier.fillMaxWidth().border(1.dp, P.Ink)) {
            strip(FieldPos.TL, FieldPos.TOP, FieldPos.TR, "banda superiore", emptyList())
            Box(Modifier.fillMaxWidth().height(110.dp).background(Color(0xFF8A8A8A)), contentAlignment = Alignment.Center) {
                Text(
                    "FOTO\nrisoluzione nativa", fontFamily = Pixel, fontSize = 12.sp, lineHeight = 16.sp,
                    color = Color(0xFFE0E0E0), textAlign = TextAlign.Center,
                )
            }
            // la nota libera sta sempre al centro della banda inferiore
            strip(FieldPos.BL, FieldPos.BOTTOM, FieldPos.BR, "banda inferiore", listOf("Nota (predefinita)"))
        }
        PixelCaption("↑ spazio aggiunto · angoli 20%, banda centrale 60% · * = automatico")
    }
}

/**
 * Dialogo per dare lo stesso testo a più foto: si sceglie se scrivere la nota libera
 * o uno dei campi del layout.
 */
@Composable
fun MultiTextDialog(
    count: Int,
    fields: List<NoteField>,
    initialFor: (String?) -> String,
    onConfirm: (fieldId: String?, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sk = LocalSkin.current
    var target by remember { mutableStateOf<String?>(null) } // null = nota libera
    var text by remember(target) { mutableStateOf(initialFor(target)) }
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalSkin provides sk, LocalCellInk provides sk.ink) {
            Column(Modifier.fillMaxWidth().dialogFrame()) {
                Text(
                    tx("Testo per $count foto"), fontFamily = sk.titleFont, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = sk.ink,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sostituisce il testo scelto sulle foto selezionate; il resto e i disegni restano. " +
                        "Lascia vuoto per toglierlo.",
                    fontFamily = sk.bodyFont, fontSize = 14.sp, color = sk.ink,
                )
                if (fields.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for ((id, name) in listOf<Pair<String?, String>>(null to "Nota") + fields.map { it.id to it.label }) {
                            BevelBox(null, Modifier.height(38.dp), on = target == id, onClick = { target = id }) {
                                Box(Modifier.padding(horizontal = 12.dp)) { BevelValue(name, size = 13) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                RetroTextField(text, { text = it }, "Scrivi qui", minHeight = 80.dp, maxHeight = 180.dp)
                Spacer(Modifier.height(16.dp))
                DialogButtons("Annulla", onDismiss, "Applica", { onConfirm(target, text.trim()) })
            }
        }
    }
}
