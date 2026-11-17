package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stessa nota (banda inferiore) su più foto, fuori dalle sessioni.
 * Si apre da "Condividi" nella Galleria con più foto, oppure dalle impostazioni.
 */
class BatchActivity : ComponentActivity() {

    companion object {
        fun intent(c: Context) = Intent(c, BatchActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            else -> emptyList()
        }
        setContent { AppTheme { BatchScreen(shared, onClose = { finish() }) } }
    }
}

@Composable
private fun BatchScreen(initial: List<Uri>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var uris by remember { mutableStateOf(initial) }
    var note by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var done by remember { mutableIntStateOf(0) }

    val hasPerm = remember { MediaHelper.hasPhotoPermission(ctx) }
    var picking by remember { mutableStateOf(initial.isEmpty() && hasPerm) }

    // senza permesso foto si usa il selettore di Android (che però toglie il GPS)
    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { picked ->
        if (picked.isNotEmpty()) uris = (uris + picked).distinct()
        else if (uris.isEmpty()) onClose()
    }
    val pickRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    LaunchedEffect(Unit) { if (initial.isEmpty() && !hasPerm) systemPicker.launch(pickRequest) }

    if (picking) {
        PhotoGrid(
            preselected = uris,
            onDone = { sel ->
                picking = false
                uris = sel
                if (sel.isEmpty() && initial.isEmpty()) onClose()
            },
            onCancel = { picking = false; if (uris.isEmpty()) onClose() },
        )
        return
    }

    fun apply() {
        val text = note.trim()
        if (text.isEmpty() || uris.isEmpty() || running) return
        running = true; done = 0
        scope.launch {
            var errors = 0
            for (u in uris) {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val bmp = AnnotationIO.loadBitmap(ctx.contentResolver, u)
                        // i disegni già fatti su quella foto restano
                        AnnotationIO.save(ctx, u, bmp, AnnotationStore.load(ctx, u)?.strokes.orEmpty(), text)
                        bmp.recycle()
                    }.isSuccess
                }
                if (!ok) errors++
                done++
            }
            running = false
            Toast.makeText(
                ctx,
                if (errors == 0) "Nota aggiunta a ${uris.size} foto (Immagini › Pastashoot)"
                else "Fatto, ma $errors foto non sono state elaborate",
                Toast.LENGTH_LONG,
            ).show()
            if (errors == 0) onClose()
        }
    }

    AppScreen(
        maxWidth = ReadableWidth,
        title = "Nota multipla",
        subtitle = "${uris.size} foto",
        onBack = { if (!running) onClose() },
        actions = {
            BarAction("Applica", ::apply, primary = true, enabled = !running && note.isNotBlank() && uris.isNotEmpty())
        },
    ) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Section("Foto selezionate") {
                LazyRow(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    items(uris, key = { it.toString() }) { PhotoThumb(it, Modifier.size(76.dp)) }
                }
                AppButton(
                    "Modifica selezione…",
                    { if (hasPerm) picking = true else systemPicker.launch(pickRequest) },
                    Modifier.padding(top = 8.dp), enabled = !running,
                )
            }
            Section("Nota") {
                CompactField(note, { note = it }, "es. Piano 3 – controsoffitto open space", singleLine = false, maxHeight = 200.dp, enabled = !running)
                Caption("Va nella banda sotto ogni foto; una nota precedente viene sostituita, i disegni restano.", Modifier.padding(top = 6.dp))
            }
            if (running) LinearProgressIndicator(
                progress = { if (uris.isEmpty()) 0f else done.toFloat() / uris.size },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }
    }
}

/** Foto del telefono (più recenti prima) con selezione multipla, numerata nell'ordine di scelta. */
@Composable
fun PhotoGrid(preselected: List<Uri>, onDone: (List<Uri>) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var onlyCamera by remember { mutableStateOf(true) }
    val selected = remember { mutableStateListOf<Uri>().apply { addAll(preselected) } }
    val photos by produceState(emptyList<Uri>(), onlyCamera) {
        value = withContext(Dispatchers.IO) { MediaHelper.allPhotos(ctx.contentResolver, onlyCamera) }
    }
    BackHandler { onCancel() }

    AppScreen(
        title = if (selected.isEmpty()) "Scegli le foto" else "${selected.size} selezionate",
        subtitle = "${photos.size} ${if (onlyCamera) "foto della fotocamera" else "immagini"}",
        onBack = onCancel,
        actions = { BarAction("Fatto", { onDone(selected.toList()) }, primary = true, enabled = selected.isNotEmpty()) },
    ) {
        Box(Modifier.padding(horizontal = 12.dp)) {
            SwitchRow("Solo foto della fotocamera", onlyCamera, { onlyCamera = !onlyCamera })
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(ThumbSize),
            contentPadding = PaddingValues(GridGap),
            horizontalArrangement = Arrangement.spacedBy(GridGap),
            verticalArrangement = Arrangement.spacedBy(GridGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(photos, key = { it.toString() }) { u ->
                val isSelected = u in selected
                Box(Modifier.aspectRatio(1f).clickable { if (isSelected) selected.remove(u) else selected.add(u) }) {
                    PhotoThumb(u, Modifier.fillMaxSize())
                    if (isSelected) {
                        Box(Modifier.matchParentSize().selectedOverlay(true))
                        ThumbBadge(AppIcons.Check, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
    }
}
