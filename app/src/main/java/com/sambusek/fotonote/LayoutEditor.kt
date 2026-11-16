package com.sambusek.fotonote

import androidx.activity.compose.BackHandler
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
                        "La nota libera resta sempre nella banda inferiore.",
                    Modifier.padding(top = 10.dp),
                )
                GroupBox("Anteprima") { LayoutPreview(fields) }
                GroupBox("Campi") {
                    if (fields.isEmpty()) {
                        PixelCaption("Nessun campo: sulle foto ci sarà solo la nota, nella banda inferiore.")
                        Spacer(Modifier.height(8.dp))
                    }
                    fields.forEachIndexed { i, f ->
                        key(f.id) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RetroTextField(
                                    f.label,
                                    { fields[i] = NoteField(f.id, it.replace("\n", " "), f.pos) },
                                    "es. Piano",
                                    Modifier.weight(1f),
                                    minHeight = 40.dp, maxHeight = 40.dp,
                                )
                                PosButton(f.pos) { fields[i] = NoteField(f.id, f.label, it) }
                                BevelBox(null, Modifier.size(44.dp), onClick = { fields.removeAt(i) }) {
                                    BevelValue("✕", size = 16)
                                }
                            }
                        }
                    }
                    PushButton("Aggiungi campo", {
                        fields.add(NoteField(System.nanoTime().toString(), "", FieldPos.BOTTOM))
                    })
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PushButton("Annulla", onCancel)
                PushButton("Salva layout", {
                    onDone(fields.filter { it.label.isNotBlank() }.map { NoteField(it.id, it.label.trim(), it.pos) })
                }, default = true)
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
            modifier = Modifier.background(P.Bg).border(1.dp, P.Ink),
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

/** Schema della foto con le bande e gli angoli, e dentro i nomi dei campi. */
@Composable
private fun LayoutPreview(fields: List<NoteField>) {
    fun names(pos: FieldPos) = fields.filter { it.pos == pos && it.label.isNotBlank() }.map { it.label.trim() }

    @Composable
    fun label(text: String, modifier: Modifier = Modifier) {
        Text(
            text, fontFamily = Pixel, fontSize = 11.sp, lineHeight = 14.sp, color = Color.White,
            modifier = modifier.background(Color.Black).padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }

    Column(Modifier.fillMaxWidth().border(1.dp, P.Ink)) {
        val top = names(FieldPos.TOP)
        if (top.isNotEmpty()) label(top.joinToString(" · "), Modifier.fillMaxWidth())
        Box(Modifier.fillMaxWidth().height(130.dp).background(Color(0xFF8A8A8A))) {
            Text(
                "FOTO", fontFamily = Pixel, fontSize = 13.sp, color = Color(0xFFCFCFCF),
                modifier = Modifier.align(Alignment.Center),
            )
            val corners = listOf(
                FieldPos.TL to Alignment.TopStart, FieldPos.TR to Alignment.TopEnd,
                FieldPos.BL to Alignment.BottomStart, FieldPos.BR to Alignment.BottomEnd,
            )
            for ((pos, align) in corners) {
                val n = names(pos)
                if (n.isNotEmpty()) label(n.joinToString("\n"), Modifier.align(align))
            }
        }
        label((names(FieldPos.BOTTOM) + "Nota").joinToString(" · "), Modifier.fillMaxWidth())
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    PushButton("Annulla", onDismiss)
                    PushButton("Applica", { onConfirm(target, text.trim()) }, default = true)
                }
            }
        }
    }
}
