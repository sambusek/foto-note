package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Una sessione di sopralluogo. Se è in corso mostra l'area di staging (le foto scattate
 * finora, da annotare); se è chiusa mostra le foto consolidate, con ZIP e report PDF.
 */
class SessionActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    companion object {
        fun intent(c: Context, id: String): Intent =
            Intent(c, SessionActivity::class.java).putExtra("id", id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platinumEdgeToEdge()
        val id = intent.getStringExtra("id")
        if (id == null) { finish(); return }
        setContent { PlatinumTheme { SessionScreen(id, tick, onClose = { finish() }) } }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

private val closedFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)

@Composable
private fun SessionScreen(id: String, tick: MutableIntState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t = tick.intValue
    val session = remember(t) { SessionStore.get(ctx, id) }

    SessionOps.busy?.let { BusyDialog(it, SessionOps.progress) }

    if (session == null) {
        DeskScreen {
            RetroWindow("Sopralluogo", Modifier.fillMaxWidth().padding(12.dp)) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CenterNote("Questa sessione non esiste più.")
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                    PushButton("Indietro", onClose, default = true)
                }
            }
        }
        return
    }
    if (session.open) StagingScreen(session, tick, onClose) else ClosedScreen(session, tick, onClose)
}

// ------------------------------------------------------------------ sessione in corso

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun StagingScreen(s: Session, tick: MutableIntState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = tick.intValue
    val photos = remember(t) { SessionStore.photos(ctx, s) }
    val annotated by produceState(emptySet<String>(), photos, t) {
        value = withContext(Dispatchers.IO) {
            photos.filter { AnnotationStore.hasContent(ctx, it.uri) }.map { it.uri.toString() }.toSet()
        }
    }
    val included = photos.count { it.uri.toString() !in s.excluded }
    var askClose by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }      // aggiunta di foto già scattate
    var selecting by remember { mutableStateOf(false) }    // selezione multipla per la nota
    val selected = remember { mutableStateListOf<String>() }
    var multiOpen by remember { mutableStateOf(false) }    // dialogo del testo per più foto
    var layoutEdit by remember { mutableStateOf(false) }   // editor del layout delle note
    var askDiscard by remember { mutableStateOf(false) }

    if (layoutEdit) {
        LayoutScreen(
            initial = s.fields,
            onDone = { f ->
                SessionStore.save(ctx, s.copy(fields = f))
                layoutEdit = false
                tick.intValue++
            },
            onCancel = { layoutEdit = false },
        )
        return
    }

    if (askDiscard) {
        RetroAlert(
            message = "Eliminare la sessione senza salvare?",
            detail = "Il sopralluogo \"${s.name}\" e tutte le annotazioni fatte finora andranno persi. " +
                "Le foto originali restano nella Galleria.",
            confirm = "Elimina",
            onConfirm = {
                askDiscard = false
                SessionOps.discard(ctx, s)
                Toast.makeText(ctx, "Sessione eliminata", Toast.LENGTH_SHORT).show()
                onClose()
            },
            dismiss = "Annulla",
            onDismiss = { askDiscard = false },
        )
    }

    if (picking) {
        PhotoGrid(
            preselected = s.extra.map(Uri::parse),
            onDone = { sel ->
                val keys = sel.map { it.toString() }
                SessionStore.save(ctx, s.copy(extra = keys, excluded = s.excluded - keys.toSet()))
                picking = false
                tick.intValue++
            },
            onCancel = { picking = false },
        )
        return
    }

    BackHandler(enabled = selecting) { selecting = false; selected.clear() }

    if (multiOpen) {
        MultiTextDialog(
            count = selected.size,
            fields = s.fields.filter { !it.auto },
            // se le foto scelte hanno già tutte lo stesso testo, si parte da quello
            initialFor = { fieldId ->
                val texts = selected.map { k ->
                    val a = AnnotationStore.load(ctx, Uri.parse(k))
                    if (fieldId == null) a?.note ?: "" else a?.values?.get(fieldId) ?: ""
                }.distinct()
                if (texts.size == 1) texts[0] else ""
            },
            onConfirm = { fieldId, text ->
                multiOpen = false
                val targets = selected.toList()
                scope.launch {
                    withContext(Dispatchers.IO) {
                        for (k in targets) {
                            val uri = Uri.parse(k)
                            val a = AnnotationStore.load(ctx, uri)
                            val strokes = a?.strokes ?: emptyList()
                            if (fieldId == null) AnnotationStore.saveDraft(ctx, uri, text, strokes)
                            else AnnotationStore.saveDraft(
                                ctx, uri, a?.note ?: "", strokes, (a?.values ?: emptyMap()) + (fieldId to text),
                            )
                        }
                    }
                    Toast.makeText(ctx, "Testo aggiornato su ${targets.size} foto", Toast.LENGTH_SHORT).show()
                    selecting = false
                    selected.clear()
                    tick.intValue++
                }
            },
            onDismiss = { multiOpen = false },
        )
    }

    if (askClose) {
        RetroAlert(
            message = if (included == 0) "Chiudere il sopralluogo senza foto?" else "Chiudere il sopralluogo?",
            detail = if (included == 0) "Non ci sono foto da salvare: la sessione verrà eliminata."
            else "Le $included foto verranno salvate con le loro annotazioni in Immagini › Pastashoot › ${s.folder}. " +
                "Dopo la chiusura non si potranno più modificare.",
            confirm = "Chiudi",
            onConfirm = {
                askClose = false
                SessionOps.close(ctx, s.id) { saved, errors ->
                    val msg = when {
                        saved == 0 && errors == 0 -> "Sessione vuota eliminata"
                        errors == 0 -> "Sopralluogo chiuso: $saved foto salvate"
                        else -> "Sopralluogo chiuso: $saved foto salvate, $errors non riuscite"
                    }
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    if (saved == 0 && errors == 0) onClose() else tick.intValue++
                }
            },
            dismiss = "Annulla",
            onDismiss = { askClose = false },
        )
    }

    DeskScreen {
        RetroWindow(
            title = s.folder,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 9.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusLight(true)
                PixelCaption(
                    if (selecting) "Selezione · ${selected.size} foto"
                    else "In corso · $included foto · ${annotated.size} annotate",
                    Modifier.padding(start = 8.dp),
                )
            }
            Box(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).well(),
                contentAlignment = Alignment.Center,
            ) {
                if (photos.isEmpty()) {
                    Box(Modifier.padding(24.dp)) {
                        CenterNote("Ancora nessuna foto.\nScatta con la fotocamera: le foto compaiono qui.")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(92.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(photos, key = { _, p -> p.id }) { _, p ->
                            val key = p.uri.toString()
                            val excluded = key in s.excluded
                            val isExtra = key in s.extra
                            val selIdx = selected.indexOf(key)
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .combinedClickable(
                                        onClick = {
                                            when {
                                                excluded -> Toast.makeText(
                                                    ctx, "Foto esclusa: tieni premuto per riammetterla", Toast.LENGTH_SHORT
                                                ).show()
                                                selecting -> if (selIdx >= 0) selected.remove(key) else selected.add(key)
                                                else -> ctx.startActivity(EditorActivity.intent(ctx, p.uri))
                                            }
                                        },
                                        onLongClick = {
                                            if (!selecting) {
                                                // una foto aggiunta a mano si toglie; una scattata in sessione si esclude
                                                val next = when {
                                                    isExtra -> s.copy(extra = s.extra - key)
                                                    excluded -> s.copy(excluded = s.excluded - key)
                                                    else -> s.copy(excluded = s.excluded + key)
                                                }
                                                SessionStore.save(ctx, next)
                                                tick.intValue++
                                            }
                                        },
                                    )
                            ) {
                                PhotoThumb(p.uri, Modifier.fillMaxSize())
                                if (excluded) {
                                    Box(
                                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("ESCLUSA", fontFamily = Pixel, fontSize = 11.sp, color = Color.White)
                                    }
                                } else {
                                    if (key in annotated) {
                                        Box(
                                            Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(P.Accent)
                                                .border(1.dp, P.Ink),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("✎", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    if (selIdx >= 0) {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .background(P.Accent.copy(alpha = 0.35f))
                                                .border(3.dp, P.Accent)
                                        )
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomStart)
                                                .size(24.dp)
                                                .background(P.Ink),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            PixelCaption(
                if (selecting) "Tocca le foto a cui dare lo stesso testo."
                else "Tocca una foto per annotarla. Tieni premuto per escluderla (o per togliere una foto aggiunta).",
                Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
            )
            if (selecting) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PushButton("Annulla", { selecting = false; selected.clear() })
                    PushButton("Tutte", {
                        selected.clear()
                        selected.addAll(photos.map { it.uri.toString() }.filter { it !in s.excluded })
                    })
                    PushButton("Testo a ${selected.size}…", { multiOpen = true }, default = true, enabled = selected.isNotEmpty())
                }
            } else {
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PushButton("Fotocamera", {
                        try {
                            ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Apri la fotocamera dal telefono", Toast.LENGTH_SHORT).show()
                        }
                    })
                    PushButton("Aggiungi foto…", { picking = true })
                    PushButton("Layout note…", { layoutEdit = true })
                    PushButton("Testo multiplo", { selecting = true }, enabled = included > 0)
                }
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PushButton("Indietro", onClose)
                    PushButton("Elimina…", { askDiscard = true })
                    PushButton("Chiudi sessione…", { askClose = true }, default = true)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ sessione chiusa

@Composable
private fun ClosedScreen(s: Session, tick: MutableIntState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var askDelete by remember { mutableStateOf(false) }

    if (askDelete) {
        RetroAlert(
            message = "Eliminare questo sopralluogo?",
            detail = "Verranno eliminate le ${s.items.size} foto della cartella ${s.folder}. " +
                "Le foto originali della fotocamera restano nella Galleria.",
            confirm = "Elimina",
            onConfirm = {
                askDelete = false
                SessionOps.delete(ctx, s)
                Toast.makeText(ctx, "Sopralluogo eliminato", Toast.LENGTH_SHORT).show()
                onClose()
            },
            dismiss = "Annulla",
            onDismiss = { askDelete = false },
        )
    }

    fun export(pdf: Boolean) {
        SessionOps.export(ctx, s.id, pdf) { f ->
            if (f == null) {
                Toast.makeText(ctx, "Non sono riuscito a creare il file", Toast.LENGTH_LONG).show()
            } else {
                Exporter.share(
                    ctx, f, if (pdf) "application/pdf" else "application/zip",
                    if (pdf) "Condividi il report PDF" else "Condividi lo ZIP",
                )
            }
        }
    }

    DeskScreen {
        RetroWindow(
            title = s.folder,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 9.dp, top = 10.dp, bottom = 12.dp),
        ) {
            PixelCaption(
                "${s.items.size} foto · chiuso il ${closedFmt.format(Date(s.end))}\n" +
                    "Immagini › Pastashoot › ${s.folder}",
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(92.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).well(),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(s.items, key = { _, i -> i.out }) { _, item ->
                    val uri = Uri.parse(item.out)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .combinedClickableSimple {
                                try {
                                    ctx.startActivity(
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(uri, "image/*")
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(ctx, "Nessuna app per aprire la foto", Toast.LENGTH_SHORT).show()
                                }
                            }
                    ) {
                        PhotoThumb(uri, Modifier.fillMaxSize())
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PushButton("Indietro", onClose)
                PushButton("Elimina…", { askDelete = true })
                PushButton("ZIP", { export(false) })
                PushButton("PDF", { export(true) }, default = true)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.combinedClickableSimple(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)
