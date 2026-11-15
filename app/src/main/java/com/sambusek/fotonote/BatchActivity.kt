package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Aggiunge la stessa nota (banda nera in basso) a più foto insieme.
 * Si apre da "Condividi" nella Galleria con più foto selezionate,
 * oppure dalla schermata principale (si apre il selettore foto).
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
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.toList() ?: emptyList()
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            else -> emptyList()
        }
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { BatchScreen(shared, onClose = { finish() }) }
            }
        }
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

    // ripiego se manca il permesso foto: selettore di Android (toglie il GPS)
    val systemPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { picked ->
        if (picked.isNotEmpty()) uris = (uris + picked).distinct()
        else if (uris.isEmpty()) onClose()
    }
    LaunchedEffect(Unit) {
        if (initial.isEmpty() && !hasPerm) {
            systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    fun addMore() {
        if (hasPerm) picking = true
        else systemPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

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
                    try {
                        val bmp = AnnotationIO.loadBitmap(ctx.contentResolver, u)
                        // i disegni già fatti su quella foto restano
                        val strokes = AnnotationStore.load(ctx, u)?.strokes ?: emptyList()
                        AnnotationIO.save(ctx, u, bmp, strokes, text)
                        bmp.recycle()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (!ok) errors++
                done++
            }
            running = false
            val msg = if (errors == 0) "Nota aggiunta a ${uris.size} foto (Immagini › FotoNote)"
            else "Fatto, ma $errors foto non sono state elaborate"
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            if (errors == 0) onClose()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Nota su più foto", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "La nota viene scritta in bianco su una banda nera aggiunta sotto ogni foto. " +
                "Gli originali restano intatti; le copie vanno in Immagini › FotoNote.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("${uris.size} foto selezionate", fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(uris, key = { it.toString() }) { u -> Thumb(u) }
        }
        OutlinedButton(onClick = { addMore() }, enabled = !running) { Text("Modifica selezione") }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Nota") },
            placeholder = { Text("es. Piano 3 – controsoffitto zona open space") },
            minLines = 3,
            enabled = !running,
            modifier = Modifier.fillMaxWidth(),
        )

        if (running) {
            LinearProgressIndicator(
                progress = { if (uris.isEmpty()) 0f else done.toFloat() / uris.size },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("$done / ${uris.size}")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, enabled = !running) { Text("Annulla") }
            Button(
                onClick = { apply() },
                enabled = !running && note.isNotBlank() && uris.isNotEmpty(),
            ) { Text("Aggiungi a ${uris.size} foto") }
        }
        Text(
            "Se una foto aveva già una nota, viene sostituita; i disegni restano.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Griglia delle foto del telefono (più recenti prima) con selezione multipla. */
@Composable
private fun PhotoGrid(preselected: List<Uri>, onDone: (List<Uri>) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var onlyCamera by remember { mutableStateOf(true) }
    val selected = remember { mutableStateListOf<Uri>().apply { addAll(preselected) } }
    val photos by produceState<List<Uri>>(emptyList(), onlyCamera) {
        value = withContext(Dispatchers.IO) { MediaHelper.allPhotos(ctx.contentResolver, onlyCamera) }
    }
    BackHandler { onCancel() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onCancel) { Text("Annulla") }
            FilterChip(
                selected = onlyCamera,
                onClick = { onlyCamera = !onlyCamera },
                label = { Text("Solo fotocamera") },
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = { onDone(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text("Fatto (${selected.size})", maxLines = 1)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            gridItems(photos, key = { it.toString() }) { u ->
                val idx = selected.indexOf(u)
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clickable { if (idx >= 0) selected.remove(u) else selected.add(u) }
                ) {
                    Thumb(u, Modifier.fillMaxSize())
                    if (idx >= 0) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .border(4.dp, MaterialTheme.colorScheme.primary)
                                .background(Color.Black.copy(alpha = 0.25f))
                        )
                        Box(
                            Modifier
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .align(Alignment.TopEnd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${idx + 1}", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(uri: Uri, modifier: Modifier = Modifier.size(84.dp)) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, Size(240, 240), null) }.getOrNull()
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
    ) {
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
