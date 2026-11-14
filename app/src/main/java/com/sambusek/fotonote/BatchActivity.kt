package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
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

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { picked ->
        if (picked.isNotEmpty()) uris = (uris + picked).distinct()
        else if (uris.isEmpty()) onClose()
    }

    // aperta senza foto: parte subito il selettore
    LaunchedEffect(Unit) {
        if (initial.isEmpty()) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
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
        OutlinedButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !running,
        ) { Text("Aggiungi altre foto") }

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

@Composable
private fun Thumb(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, Size(240, 240), null) }.getOrNull()
        }
    }
    Box(
        Modifier
            .size(84.dp)
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
