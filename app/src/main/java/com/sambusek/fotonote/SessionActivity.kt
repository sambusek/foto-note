package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Una sessione di sopralluogo. In corso: l'area di staging con le foto da annotare.
 * Chiusa: le foto consolidate, con ZIP e report PDF.
 */
class SessionActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    companion object {
        fun intent(c: Context, id: String): Intent = Intent(c, SessionActivity::class.java).putExtra("id", id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getStringExtra("id") ?: return finish()
        setContent { AppTheme { SessionScreen(id, tick, onClose = { finish() }) } }
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
    val session = remember(tick.intValue) { SessionStore.get(ctx, id) }
    SessionOps.busy?.let { ProgressDialog(it, SessionOps.progress) }
    when {
        session == null -> AppScreen("Sopralluogo", onBack = onClose) {
            CenterMessage("Questa sessione non esiste più.", Modifier.fillMaxWidth())
        }
        session.open -> StagingScreen(session, tick, onClose)
        else -> ClosedScreen(session, onClose)
    }
}

private fun openCamera(ctx: Context) {
    runCatching { ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        .onFailure { Toast.makeText(ctx, "Apri la fotocamera dal telefono", Toast.LENGTH_SHORT).show() }
}

// ------------------------------------------------------------------ sessione in corso

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StagingScreen(s: Session, tick: MutableIntState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = tick.intValue
    fun refresh() { tick.intValue++ }

    val photos = remember(t) { SessionStore.photos(ctx, s) }
    val annotated by produceState(emptySet<String>(), photos, t) {
        value = withContext(Dispatchers.IO) {
            photos.filter { AnnotationStore.hasContent(ctx, it.uri) }.map { it.uri.toString() }.toSet()
        }
    }
    val included = photos.count { it.uri.toString() !in s.excluded }

    var picking by remember { mutableStateOf(false) }      // aggiunta di foto già scattate
    var layoutEdit by remember { mutableStateOf(false) }   // editor del layout delle note
    var selecting by remember { mutableStateOf(false) }    // selezione per il testo multiplo
    val selected = remember { mutableStateListOf<String>() }
    var multiOpen by remember { mutableStateOf(false) }
    var askClose by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }

    fun endSelection() { selecting = false; selected.clear() }

    if (layoutEdit) {
        LayoutScreen(
            initial = s.fields,
            onDone = { SessionStore.save(ctx, s.copy(fields = it)); layoutEdit = false; refresh() },
            onCancel = { layoutEdit = false },
        )
        return
    }
    if (picking) {
        PhotoGrid(
            preselected = s.extra.map(Uri::parse),
            onDone = { sel ->
                val keys = sel.map { it.toString() }
                SessionStore.save(ctx, s.copy(extra = keys, excluded = s.excluded - keys.toSet()))
                picking = false
                refresh()
            },
            onCancel = { picking = false },
        )
        return
    }
    BackHandler(enabled = selecting) { endSelection() }

    if (askDiscard) ConfirmDialog(
        title = "Eliminare la sessione senza salvare?",
        text = "Il sopralluogo \"${s.name}\" e le annotazioni fatte finora andranno persi. " +
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

    if (askClose) ConfirmDialog(
        title = if (included == 0) "Chiudere il sopralluogo senza foto?" else "Chiudere il sopralluogo?",
        text = if (included == 0) "Non ci sono foto da salvare: la sessione verrà eliminata."
        else "Le $included foto verranno salvate con le annotazioni in Immagini › Pastashoot › ${s.folder}. " +
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
                if (saved == 0 && errors == 0) onClose() else refresh()
            }
        },
        dismiss = "Annulla",
        onDismiss = { askClose = false },
    )

    if (multiOpen) MultiTextDialog(
        count = selected.size,
        fields = s.fields.filter { !it.auto },
        // se le foto scelte hanno già tutte lo stesso testo, si parte da quello
        initialFor = { fieldId ->
            selected.map { k ->
                val a = AnnotationStore.load(ctx, Uri.parse(k))
                if (fieldId == null) a?.note.orEmpty() else a?.values?.get(fieldId).orEmpty()
            }.distinct().singleOrNull().orEmpty()
        },
        onConfirm = { fieldId, text ->
            multiOpen = false
            val targets = selected.toList()
            scope.launch {
                withContext(Dispatchers.IO) {
                    for (k in targets) {
                        val uri = Uri.parse(k)
                        val a = AnnotationStore.load(ctx, uri)
                        val strokes = a?.strokes.orEmpty()
                        if (fieldId == null) AnnotationStore.saveDraft(ctx, uri, text, strokes)
                        else AnnotationStore.saveDraft(
                            ctx, uri, a?.note.orEmpty(), strokes, a?.values.orEmpty() + (fieldId to text)
                        )
                    }
                }
                Toast.makeText(ctx, "Testo aggiornato su ${targets.size} foto", Toast.LENGTH_SHORT).show()
                endSelection()
                refresh()
            }
        },
        onDismiss = { multiOpen = false },
    )

    AppScreen(
        title = if (selecting) "${selected.size} selezionate" else s.name,
        subtitle = if (selecting) "Tocca le foto a cui dare lo stesso testo"
        else "In corso · $included foto · ${annotated.size} annotate",
        onBack = { if (selecting) endSelection() else onClose() },
        actions = {
            if (selecting) {
                BarAction("Tutte", {
                    selected.clear()
                    selected.addAll(photos.map { it.uri.toString() }.filter { it !in s.excluded })
                })
                BarAction("Testo…", { multiOpen = true }, primary = true, enabled = selected.isNotEmpty())
            } else {
                ToolIcon(AppIcons.Camera, "Fotocamera", { openCamera(ctx) })
                ToolIcon(AppIcons.TextFields, "Testo su più foto", { selecting = true }, enabled = included > 0)
                OverflowMenu(
                    listOf(
                        "Aggiungi foto già scattate…" to { picking = true },
                        "Layout delle note…" to { layoutEdit = true },
                        "Elimina sessione…" to { askDiscard = true },
                    )
                )
            }
        },
        bottomBar = {
            if (!selecting) Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Caption("Tocca una foto per annotarla, tienila premuta per escluderla.")
                AppButton(
                    "Chiudi sopralluogo e salva", { askClose = true }, primary = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        },
    ) {
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CenterMessage("Ancora nessuna foto.\nScatta con la fotocamera: le foto compaiono qui.")
            }
            return@AppScreen
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(92.dp),
            contentPadding = PaddingValues(GridGap),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            verticalArrangement = Arrangement.spacedBy(GridGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(photos, key = { it.id }) { p ->
                val key = p.uri.toString()
                val excluded = key in s.excluded
                val isSelected = key in selected
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .combinedClickable(
                            onClick = {
                                when {
                                    excluded -> Toast.makeText(
                                        ctx, "Foto esclusa: tienila premuta per riammetterla", Toast.LENGTH_SHORT
                                    ).show()
                                    selecting -> if (isSelected) selected.remove(key) else selected.add(key)
                                    else -> ctx.startActivity(EditorActivity.intent(ctx, p.uri))
                                }
                            },
                            onLongClick = {
                                if (selecting) return@combinedClickable
                                // una foto aggiunta a mano si toglie; una scattata in sessione si esclude o riammette
                                SessionStore.save(
                                    ctx, when {
                                        key in s.extra -> s.copy(extra = s.extra - key)
                                        excluded -> s.copy(excluded = s.excluded - key)
                                        else -> s.copy(excluded = s.excluded + key)
                                    }
                                )
                                refresh()
                            },
                        )
                ) {
                    PhotoThumb(p.uri, Modifier.fillMaxSize())
                    when {
                        excluded -> Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("Esclusa", color = Color.White, style = MaterialTheme.typography.labelMedium) }
                        isSelected -> {
                            Box(Modifier.matchParentSize().selectedOverlay(true))
                            ThumbBadge(AppIcons.Check, Modifier.align(Alignment.TopEnd))
                        }
                        key in annotated -> ThumbBadge(AppIcons.Pen, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ sessione chiusa

@Composable
private fun ClosedScreen(s: Session, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var askDelete by remember { mutableStateOf(false) }

    if (askDelete) ConfirmDialog(
        title = "Eliminare questo sopralluogo?",
        text = "Verranno eliminate le ${s.items.size} foto della cartella ${s.folder}. " +
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

    fun export(pdf: Boolean) = SessionOps.export(ctx, s.id, pdf) { f ->
        if (f == null) Toast.makeText(ctx, "Non sono riuscito a creare il file", Toast.LENGTH_LONG).show()
        else Exporter.share(
            ctx, f, if (pdf) "application/pdf" else "application/zip",
            if (pdf) "Condividi il report PDF" else "Condividi lo ZIP",
        )
    }

    fun view(uri: Uri) = runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }.onFailure { Toast.makeText(ctx, "Nessuna app per aprire la foto", Toast.LENGTH_SHORT).show() }

    AppScreen(
        title = s.folder,
        subtitle = "${s.items.size} foto · chiuso il ${closedFmt.format(Date(s.end))}",
        onBack = onClose,
        actions = { OverflowMenu(listOf("Elimina sopralluogo…" to { askDelete = true })) },
        bottomBar = {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Caption("Immagini › Pastashoot › ${s.folder}")
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("ZIP", { export(false) }, Modifier.weight(1f))
                    AppButton("Report PDF", { export(true) }, Modifier.weight(1f), primary = true)
                }
            }
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(92.dp),
            contentPadding = PaddingValues(GridGap),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            verticalArrangement = Arrangement.spacedBy(GridGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(s.items, key = { it.out }) { item ->
                val uri = Uri.parse(item.out)
                PhotoThumb(uri, Modifier.aspectRatio(1f).clickable { view(uri) })
            }
        }
    }
}
