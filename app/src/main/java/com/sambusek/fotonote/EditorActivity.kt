package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorActivity : ComponentActivity() {

    companion object {
        fun intent(c: Context, uri: Uri): Intent =
            Intent(c, EditorActivity::class.java).setData(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platinumEdgeToEdge()
        val uri: Uri? = if (intent.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        } else intent.data
        if (uri == null) {
            finish(); return
        }
        setContent {
            PlatinumTheme { EditorScreen(uri, onClose = { finish() }) }
        }
    }
}

private val PALETTE = listOf(
    0xFFE53935.toInt(), // rosso
    0xFFFFD600.toInt(), // giallo
    0xFF6B6BE0.toInt(), // azzurro-viola
    0xFF1E88E5.toInt(), // blu
    0xFF43A047.toInt(), // verde
    0xFF000000.toInt(), // nero
    0xFFFFFFFF.toInt(), // bianco
)

private val WIDTHS = listOf("S" to 0.002f, "M" to 0.004f, "L" to 0.008f)

@Composable
private fun EditorScreen(uri: Uri, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ink = remember { InkState() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var savedNote by remember { mutableStateOf("") }
    var noteOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var stylusOnly by remember { mutableStateOf(Prefs.stylusOnly(ctx)) }
    var askExit by remember { mutableStateOf(false) }

    val unsaved = ink.dirty || note != savedNote

    LaunchedEffect(uri) {
        try {
            val (bmp, saved) = withContext(Dispatchers.IO) {
                AnnotationIO.loadBitmap(ctx.contentResolver, uri) to AnnotationStore.load(ctx, uri)
            }
            if (saved != null) {
                ink.restore(saved.strokes)
                note = saved.note; savedNote = saved.note
                if (saved.note.isNotBlank()) noteOpen = true
            }
            bitmap = bmp
        } catch (e: Exception) {
            error = "Impossibile aprire la foto: ${e.message}"
        }
    }

    fun save(share: Boolean) {
        val bmp = bitmap ?: return
        if (saving) return
        saving = true
        scope.launch {
            try {
                val strokes = ink.strokes
                val text = note
                val out = withContext(Dispatchers.IO) {
                    AnnotationIO.save(ctx, uri, bmp, strokes, text)
                }
                ink.dirty = false
                savedNote = text
                if (share) {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("image/jpeg")
                        .putExtra(Intent.EXTRA_STREAM, out)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (text.isNotBlank()) send.putExtra(Intent.EXTRA_TEXT, text)
                    ctx.startActivity(Intent.createChooser(send, "Condividi foto annotata"))
                } else {
                    Toast.makeText(ctx, "Salvata in Immagini › Pastashoot", Toast.LENGTH_SHORT).show()
                    onClose()
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "Errore nel salvataggio: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                saving = false
            }
        }
    }

    BackHandler(enabled = unsaved) { askExit = true }

    if (askExit) {
        RetroAlert(
            message = "Salvare le modifiche prima di chiudere?",
            detail = "Se non salvi, i tratti e la nota andranno persi.",
            confirm = "Salva",
            onConfirm = { askExit = false; save(false) },
            dismiss = "Non salvare",
            onDismiss = { askExit = false; onClose() },
        )
    }

    var colorMenu by remember { mutableStateOf(false) }
    val ready = bitmap != null && !saving
    val fileName by produceState("Senza titolo", uri) {
        value = withContext(Dispatchers.IO) { MediaHelper.displayName(ctx.contentResolver, uri) } ?: "Senza titolo"
    }

    Column(
        Modifier
            .fillMaxSize()
            .desktop()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        MenuBar("Pastashoot")

        RetroWindow(
            title = fileName,
            onClose = { if (unsaved) askExit = true else onClose() },
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 4.dp, end = 7.dp, top = 6.dp),
        ) {
            // barra dei comandi
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BevelBox(null, Modifier.size(44.dp), enabled = ink.canUndo, onClick = { ink.undo() }) {
                    BevelValue("↶", enabled = ink.canUndo, size = 20)
                }
                BevelBox(null, Modifier.size(44.dp), enabled = ink.canRedo, onClick = { ink.redo() }) {
                    BevelValue("↷", enabled = ink.canRedo, size = 20)
                }
                Spacer(Modifier.weight(1f))
                PushButton("Condividi", { save(true) }, enabled = ready)
                PushButton("Salva", { save(false) }, default = true, enabled = ready)
            }

            // foto e disegno
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .well(P.Canvas),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = bitmap
                when {
                    error != null -> CenterNote(error!!)
                    bmp == null -> CenterNote("Apertura in corso…")
                    else -> {
                        val image = remember(bmp) { bmp.asImageBitmap() }
                        InkCanvas(image = image, state = ink, stylusOnly = stylusOnly, note = note)
                    }
                }
                if (saving) {
                    Box(Modifier.width(220.dp).background(P.Bg).border(1.dp, P.Ink).padding(12.dp)) {
                        Column {
                            Text("Salvataggio…", fontFamily = Pixel, fontSize = 14.sp, color = P.Ink)
                            Spacer(Modifier.height(8.dp))
                            RetroProgress(0.6f)
                        }
                    }
                }
            }

            // nota testuale
            if (noteOpen) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("Nota:", fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        color = P.Ink, modifier = Modifier.padding(top = 10.dp, end = 8.dp))
                    RetroTextField(note, { note = it }, "Scrivi o detta una nota…", maxHeight = 130.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // palette degli strumenti
        RetroWindow(
            title = "Strumenti",
            small = true,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 7.dp, top = 8.dp, bottom = 9.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BevelButton("Colore", onClick = { colorMenu = true }) {
                    Swatch(ink.color, size = 26)
                    DropdownMenu(
                        expanded = colorMenu,
                        onDismissRequest = { colorMenu = false },
                        modifier = Modifier.background(P.Bg).border(1.dp, P.Ink),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            for (c in PALETTE) {
                                Box(Modifier.clickable { ink.color = c; ink.eraser = false; colorMenu = false }) {
                                    Swatch(c, size = 34, selected = c == ink.color)
                                }
                            }
                        }
                    }
                }
                val wIdx = WIDTHS.indexOfFirst { it.second == ink.widthFactor }.coerceAtLeast(0)
                BevelButton("Tratto", onClick = { ink.widthFactor = WIDTHS[(wIdx + 1) % WIDTHS.size].second }) {
                    BevelValue(WIDTHS[wIdx].first)
                }
                BevelButton("Gomma", on = ink.eraser, onClick = { ink.eraser = !ink.eraser }) {
                    BevelValue(if (ink.eraser) "SÌ" else "NO")
                }
                BevelButton("Input", on = stylusOnly, onClick = {
                    stylusOnly = !stylusOnly; Prefs.setStylusOnly(ctx, stylusOnly)
                }) {
                    BevelValue(if (stylusOnly) "PEN" else "+DITO", size = 15)
                }
                BevelButton("Nota", on = noteOpen, onClick = { noteOpen = !noteOpen }) {
                    BevelValue("T", size = 20)
                }
                BevelButton("Pulisci", enabled = ink.strokes.isNotEmpty(), onClick = { ink.clearAll() }) {
                    BevelValue("✕", enabled = ink.strokes.isNotEmpty(), size = 18)
                }
            }
        }
    }
}

/** Campione di colore quadrato. */
@Composable
private fun Swatch(color: Int, size: Int, selected: Boolean = false) {
    Box(
        Modifier
            .size(size.dp)
            .border(if (selected) 3.dp else 1.dp, if (selected) P.Accent else P.Ink)
            .padding(if (selected) 4.dp else 1.dp)
            .background(Color(color))
    )
}
