package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StagingScreen(s: Session, tick: MutableIntState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val t = tick.intValue
    val photos = remember(t) { SessionStore.photos(ctx, s) }
    val annotated by produceState(emptySet<String>(), photos, t) {
        value = withContext(Dispatchers.IO) {
            photos.filter { AnnotationStore.hasContent(ctx, it.uri) }.map { it.uri.toString() }.toSet()
        }
    }
    val included = photos.count { it.uri.toString() !in s.excluded }
    var askClose by remember { mutableStateOf(false) }

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
                    "In corso · $included foto · ${annotated.size} annotate",
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
                            Box(
                                Modifier
                                    .aspectRatio(1f)
                                    .combinedClickable(
                                        onClick = {
                                            if (excluded) {
                                                Toast.makeText(ctx, "Foto esclusa: tieni premuto per riammetterla", Toast.LENGTH_SHORT).show()
                                            } else ctx.startActivity(EditorActivity.intent(ctx, p.uri))
                                        },
                                        onLongClick = {
                                            SessionStore.save(
                                                ctx,
                                                s.copy(excluded = if (excluded) s.excluded - key else s.excluded + key),
                                            )
                                            tick.intValue++
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
                                } else if (key in annotated) {
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
                            }
                        }
                    }
                }
            }
            PixelCaption(
                "Tocca una foto per annotarla. Tieni premuto per escluderla dal sopralluogo.",
                Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PushButton("Indietro", onClose)
                PushButton("Fotocamera", {
                    try {
                        ctx.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Apri la fotocamera dal telefono", Toast.LENGTH_SHORT).show()
                    }
                })
                PushButton("Chiudi sessione…", { askClose = true }, default = true)
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
