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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("Modifiche non salvate") },
            text = { Text("Vuoi salvare l'annotazione prima di uscire?") },
            confirmButton = {
                TextButton(onClick = { askExit = false; save(false) }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { askExit = false; onClose() }) { Text("Esci senza salvare") }
            },
        )
    }

    var colorMenu by remember { mutableStateOf(false) }
    val ready = bitmap != null && !saving

    Column(
        Modifier
            .fillMaxSize()
            .background(P.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // --- barra superiore ---
        GridRow(Modifier.gridFrame()) {
            Cell(weight = 1.1f, height = 56.dp, onClick = { if (unsaved) askExit = true else onClose() }) {
                CellButtonText("Chiudi")
            }
            Cell(weight = 0.7f, height = 56.dp, enabled = ink.canUndo, onClick = { ink.undo() }) {
                CellValue("↶", enabled = ink.canUndo, size = 24)
            }
            Cell(weight = 0.7f, height = 56.dp, enabled = ink.canRedo, onClick = { ink.redo() }) {
                CellValue("↷", enabled = ink.canRedo, size = 24)
            }
            Cell(weight = 1.3f, height = 56.dp, enabled = ready, onClick = { save(true) }) {
                CellButtonText("Condividi", enabled = ready)
            }
            Cell(weight = 1.2f, height = 56.dp, accent = true, enabled = ready, onClick = { save(false) }) {
                CellButtonText("Salva", enabled = ready, accent = true)
            }
        }

        // --- foto e disegno ---
        Box(
            Modifier.weight(1f).fillMaxWidth().background(P.Canvas),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            when {
                error != null -> Text(error!!, color = P.Ink, modifier = Modifier.padding(24.dp))
                bmp == null -> CircularProgressIndicator(color = P.Accent)
                else -> {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    InkCanvas(image = image, state = ink, stylusOnly = stylusOnly, note = note)
                }
            }
            if (saving) CircularProgressIndicator(color = P.Accent)
        }

        // --- nota testuale ---
        if (noteOpen) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .gridFrame()
                    .cellLines()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("NOTA", style = LabelStyle, color = P.Accent)
                BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = TextStyle(fontSize = 17.sp, color = P.Ink, lineHeight = 23.sp),
                    cursorBrush = SolidColor(P.Accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp, max = 150.dp)
                        .padding(top = 6.dp),
                    decorationBox = { inner ->
                        if (note.isEmpty()) {
                            Text("Scrivi o detta una nota…", fontSize = 17.sp, color = P.Off)
                        }
                        inner()
                    },
                )
            }
        }

        // --- strumenti ---
        GridRow(Modifier.gridFrame()) {
            Cell("Colore", onClick = { colorMenu = true }) {
                Swatch(ink.color, size = 34)
                DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (c in PALETTE) {
                            Box(Modifier.clickable { ink.color = c; ink.eraser = false; colorMenu = false }) {
                                Swatch(c, size = 38, selected = c == ink.color)
                            }
                        }
                    }
                }
            }
            val wIdx = WIDTHS.indexOfFirst { it.second == ink.widthFactor }.coerceAtLeast(0)
            Cell("Tratto", onClick = { ink.widthFactor = WIDTHS[(wIdx + 1) % WIDTHS.size].second }) {
                CellValue(WIDTHS[wIdx].first)
            }
            Cell("Gomma", on = ink.eraser, onClick = { ink.eraser = !ink.eraser }) {
                CellValue(if (ink.eraser) "ON" else "OFF")
            }
        }
        GridRow {
            Cell("Input", on = stylusOnly, onClick = {
                stylusOnly = !stylusOnly; Prefs.setStylusOnly(ctx, stylusOnly)
            }) {
                CellValue(if (stylusOnly) "PENNA" else "+ DITO", size = 22)
            }
            Cell("Nota", on = noteOpen, onClick = { noteOpen = !noteOpen }) {
                CellValue(if (noteOpen) "ON" else "OFF")
            }
            Cell("Cancella", enabled = ink.strokes.isNotEmpty(), onClick = { ink.clearAll() }) {
                CellValue("TUTTO", enabled = ink.strokes.isNotEmpty(), size = 22)
            }
        }
    }
}

/** Campione di colore quadrato, bordato come un'icona Platinum. */
@Composable
private fun Swatch(color: Int, size: Int, selected: Boolean = false) {
    Box(
        Modifier
            .size(size.dp)
            .border(if (selected) 3.dp else 1.dp, if (selected) P.Accent else P.Ink)
            .padding(if (selected) 4.dp else 0.dp)
            .background(Color(color))
    )
}
