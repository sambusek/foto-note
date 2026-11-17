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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Editor di una foto: disegno con l'S Pen e testi. Dentro una sessione in corso salva bozze
 * e permette di passare da una foto all'altra; fuori sessione salva subito la copia annotata.
 */
class EditorActivity : ComponentActivity() {

    companion object {
        fun intent(c: Context, uri: Uri): Intent = Intent(c, EditorActivity::class.java).setData(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri: Uri = (
            if (intent.action == Intent.ACTION_SEND) IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else intent.data
            ) ?: return finish()
        setContent {
            val session = remember { SessionStore.sessionFor(this, uri) }
            // le foto della sessione, per passare dall'una all'altra senza uscire
            val list = remember {
                session?.let { s -> SessionStore.photos(this, s).map { it.uri }.filter { it.toString() !in s.excluded } }
                    .orEmpty()
            }
            var current by remember { mutableStateOf(uri) }
            val index = list.indexOfFirst { it.toString() == current.toString() }
            AppTheme {
                key(current.toString()) {
                    EditorScreen(
                        uri = current,
                        session = session,
                        index = index,
                        total = list.size,
                        onNavigate = { d -> list.getOrNull(index + d)?.let { current = it } },
                        onClose = { finish() },
                    )
                }
            }
        }
    }
}

private val PALETTE = listOf(
    0xFFE53935.toInt(), // rosso
    0xFFFFD600.toInt(), // giallo
    0xFF6B6BE0.toInt(), // lavanda
    0xFF1E88E5.toInt(), // blu
    0xFF43A047.toInt(), // verde
    0xFF000000.toInt(), // nero
    0xFFFFFFFF.toInt(), // bianco
)

/** Spessori del tratto, in frazione del lato lungo della foto. */
private val WIDTHS = listOf(0.002f, 0.004f, 0.008f)

@Composable
private fun EditorScreen(
    uri: Uri,
    session: Session?,
    index: Int,
    total: Int,
    onNavigate: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ink = remember { InkState() }
    val staging = session != null
    val fields = session?.fields.orEmpty()
    val inputFields = fields.filter { !it.auto }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var savedNote by remember { mutableStateOf("") }
    val values = remember { mutableStateMapOf<String, String>() }   // campi del layout
    var savedValues by remember { mutableStateOf(emptyMap<String, String>()) }
    val vals = values.toMap().filterValues { it.isNotBlank() }
    var textsOpen by remember { mutableStateOf(inputFields.isNotEmpty()) }
    var stylusOnly by remember { mutableStateOf(Prefs.stylusOnly(ctx)) }
    var saving by remember { mutableStateOf(false) }
    var askExit by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }

    val unsaved = ink.dirty || note != savedNote || vals != savedValues
    val canNav = staging && index >= 0 && total > 1
    val ready = bitmap != null && !saving

    val fileName by produceState("Senza titolo", uri) {
        value = withContext(Dispatchers.IO) { MediaHelper.displayName(ctx.contentResolver, uri) } ?: "Senza titolo"
    }
    val takenMillis by produceState(System.currentTimeMillis(), uri) {
        value = withContext(Dispatchers.IO) { MediaHelper.takenMillis(ctx.contentResolver, uri) }
    }

    LaunchedEffect(uri) {
        try {
            val (bmp, saved) = withContext(Dispatchers.IO) {
                AnnotationIO.loadBitmap(ctx.contentResolver, uri) to AnnotationStore.load(ctx, uri)
            }
            if (saved != null) {
                ink.restore(saved.strokes)
                note = saved.note; savedNote = saved.note
                values.putAll(saved.values)
                savedValues = saved.values.filterValues { it.isNotBlank() }
                if (saved.note.isNotBlank()) textsOpen = true
            }
            bitmap = bmp
        } catch (e: Exception) {
            error = "Impossibile aprire la foto: ${e.message}"
        }
    }

    /** Esegue un salvataggio in background, segnalando gli errori. */
    fun runSave(block: suspend () -> Unit) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                Toast.makeText(ctx, "Errore nel salvataggio: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                saving = false
            }
        }
    }

    suspend fun saveDraft() {
        val (strokes, text, v) = Triple(ink.strokes, note, vals)
        withContext(Dispatchers.IO) { AnnotationStore.saveDraft(ctx, uri, text, strokes, v) }
        ink.dirty = false; savedNote = text; savedValues = v
    }

    fun save(share: Boolean) {
        val bmp = bitmap ?: return
        runSave {
            if (staging) {
                saveDraft()
                Toast.makeText(ctx, "Salvata nella sessione", Toast.LENGTH_SHORT).show()
                onClose()
                return@runSave
            }
            val text = note
            val out = withContext(Dispatchers.IO) { AnnotationIO.save(ctx, uri, bmp, ink.strokes, text) }
            ink.dirty = false; savedNote = text
            if (share) {
                val send = Intent(Intent.ACTION_SEND).setType("image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, out)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (text.isNotBlank()) send.putExtra(Intent.EXTRA_TEXT, text)
                ctx.startActivity(Intent.createChooser(send, "Condividi foto annotata"))
            } else {
                Toast.makeText(ctx, "Salvata in Immagini › Pastashoot", Toast.LENGTH_SHORT).show()
                onClose()
            }
        }
    }

    /** Foto precedente (-1) o successiva (+1) della sessione, salvando prima la bozza. */
    fun go(d: Int) {
        if (!canNav || (index + d) !in 0 until total) return
        if (!unsaved) onNavigate(d) else runSave { saveDraft(); onNavigate(d) }
    }

    BackHandler(enabled = unsaved) { askExit = true }
    if (askExit) ConfirmDialog(
        title = "Salvare le modifiche?",
        text = "Se non salvi, i tratti e i testi andranno persi.",
        confirm = "Salva",
        onConfirm = { askExit = false; save(false) },
        dismiss = "Non salvare",
        onDismiss = { askExit = false; onClose() },
    )

    AppScreen(
        title = fileName,
        subtitle = if (canNav) "Foto ${index + 1} di $total" else null,
        brand = false,
        onBack = { if (unsaved) askExit = true else onClose() },
        actions = {
            ToolIcon(AppIcons.Undo, "Annulla", { ink.undo() }, enabled = ink.canUndo)
            ToolIcon(AppIcons.Redo, "Ripeti", { ink.redo() }, enabled = ink.canRedo)
            if (!staging) ToolIcon(AppIcons.Share, "Condividi", { save(true) }, enabled = ready)
            BarAction("Salva", { save(false) }, primary = true, enabled = ready)
        },
        bottomBar = {
            // barra degli strumenti: una sola riga di icone
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canNav) ToolIcon(AppIcons.Prev, "Foto precedente", { go(-1) }, enabled = index > 0)
                Box {
                    IconButton({ colorMenu = true }) { Swatch(ink.color, size = 22) }
                    DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in PALETTE) {
                                Box(Modifier.clickable { ink.color = c; ink.eraser = false; colorMenu = false }) {
                                    Swatch(c, size = 34, selected = c == ink.color)
                                }
                            }
                        }
                    }
                }
                val wIdx = WIDTHS.indexOf(ink.widthFactor).coerceAtLeast(0)
                IconButton({ ink.widthFactor = WIDTHS[(wIdx + 1) % WIDTHS.size] }) {
                    val d = listOf(6.dp, 10.dp, 16.dp)[wIdx]
                    Box(Modifier.size(d).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
                }
                ToolIcon(AppIcons.Eraser, "Gomma", { ink.eraser = !ink.eraser }, selected = ink.eraser)
                ToolIcon(
                    AppIcons.Touch, "Disegna anche con il dito",
                    { stylusOnly = !stylusOnly; Prefs.setStylusOnly(ctx, stylusOnly) }, selected = !stylusOnly,
                )
                ToolIcon(AppIcons.Notes, "Testi", { textsOpen = !textsOpen }, selected = textsOpen)
                ToolIcon(AppIcons.Delete, "Cancella i tratti", { ink.clearAll() }, enabled = ink.strokes.isNotEmpty())
                if (canNav) ToolIcon(AppIcons.Next, "Foto successiva", { go(1) }, enabled = index < total - 1)
            }
        },
    ) {
        // foto e disegno
        Box(
            Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            when {
                error != null -> CenterMessage(error!!)
                bmp == null -> CenterMessage("Apertura in corso…")
                else -> {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    // anteprima dei testi, come verranno stampati
                    val overlay = remember(note, vals, bmp, takenMillis) {
                        Overlay.create(
                            note, fields, AutoFields.resolve(fields, vals, session?.name.orEmpty(), takenMillis),
                            bmp.width, bmp.height,
                        )
                    }
                    InkCanvas(image, ink, stylusOnly, overlay, onSwipe = ::go)
                }
            }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }

        // testi: campi del layout di sessione e nota libera
        if (textsOpen) Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (f in inputFields) Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel(f.label)
                ChoiceField(
                    values[f.id].orEmpty(), { values[f.id] = it }, f.pos.short,
                    if (f.kind == FieldKind.CHOICE) f.options else emptyList(),
                )
            }
            Row {
                FieldLabel("Nota", Modifier.padding(top = 10.dp))
                CompactField(note, { note = it }, "Scrivi o detta una nota…", singleLine = false, maxHeight = 110.dp)
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) = Text(
    text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.width(76.dp).padding(end = 8.dp),
)

/** Campione di colore tondo; con [selected] ha l'anello d'accento. */
@Composable
private fun Swatch(color: Int, size: Int, selected: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) cs.primary else cs.outline, CircleShape)
            .padding(if (selected) 4.dp else 1.dp)
            .clip(CircleShape)
            .background(Color(color))
    )
}
