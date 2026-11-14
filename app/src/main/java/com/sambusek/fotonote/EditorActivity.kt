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
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
        enableEdgeToEdge()
        val uri: Uri? = if (intent.action == Intent.ACTION_SEND) {
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        } else intent.data
        if (uri == null) {
            finish(); return
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                EditorScreen(uri, onClose = { finish() })
            }
        }
    }
}

private val PALETTE = listOf(
    0xFFE53935.toInt(), // rosso
    0xFFFFD600.toInt(), // giallo
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
                    Toast.makeText(ctx, "Salvata in Immagini › FotoNote", Toast.LENGTH_SHORT).show()
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // --- barra superiore ---
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val compact = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            TextButton(onClick = { if (unsaved) askExit = true else onClose() }, contentPadding = compact) {
                Text("Chiudi", maxLines = 1, softWrap = false)
            }
            TextButton(onClick = { ink.undo() }, enabled = ink.canUndo, contentPadding = compact) {
                Text("↶", fontSize = 22.sp, maxLines = 1)
            }
            TextButton(onClick = { ink.redo() }, enabled = ink.canRedo, contentPadding = compact) {
                Text("↷", fontSize = 22.sp, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { save(true) }, enabled = bitmap != null && !saving, contentPadding = compact) {
                Text("Condividi", maxLines = 1, softWrap = false)
            }
            Button(onClick = { save(false) }, enabled = bitmap != null && !saving, contentPadding = compact) {
                Text("Salva", maxLines = 1, softWrap = false)
            }
        }

        // --- foto e disegno ---
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val bmp = bitmap
            when {
                error != null -> Text(error!!, color = Color.White, modifier = Modifier.padding(24.dp))
                bmp == null -> CircularProgressIndicator()
                else -> {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    InkCanvas(image = image, state = ink, stylusOnly = stylusOnly, note = note)
                }
            }
            if (saving) CircularProgressIndicator()
        }

        // --- nota testuale ---
        if (noteOpen) {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota") },
                placeholder = { Text("Scrivi o detta una nota da associare alla foto") },
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        // --- strumenti ---
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorPicker(
                current = ink.color,
                active = !ink.eraser,
                onPick = { ink.color = it; ink.eraser = false },
            )
            Spacer(Modifier.width(4.dp))
            for ((label, f) in WIDTHS) {
                FilterChip(
                    selected = ink.widthFactor == f,
                    onClick = { ink.widthFactor = f },
                    label = { Text(label) },
                )
            }
            FilterChip(selected = ink.eraser, onClick = { ink.eraser = !ink.eraser }, label = { Text("Gomma") })
            FilterChip(
                selected = stylusOnly,
                onClick = { stylusOnly = !stylusOnly; Prefs.setStylusOnly(ctx, stylusOnly) },
                label = { Text("Solo S Pen") },
            )
            FilterChip(selected = noteOpen, onClick = { noteOpen = !noteOpen }, label = { Text("Nota") })
            TextButton(onClick = { ink.clearAll() }, enabled = ink.strokes.isNotEmpty()) { Text("Cancella tutto") }
        }
    }
}

/** Un solo pallino con il colore attuale: toccandolo si apre la scelta dei colori. */
@Composable
private fun ColorPicker(current: Int, active: Boolean, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ColorDot(current, selected = active, size = 36) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (c in PALETTE) {
                    ColorDot(c, selected = c == current, size = 36) { onPick(c); open = false }
                }
            }
        }
    }
}

@Composable
private fun ColorDot(color: Int, selected: Boolean, size: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color.White else Color.Gray,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
    )
}
