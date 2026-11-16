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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        platinumEdgeToEdge()
        val shared: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.toList() ?: emptyList()
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            else -> emptyList()
        }
        setContent { PlatinumTheme { BatchScreen(shared, onClose = { finish() }) } }
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
            val msg = if (errors == 0) "Nota aggiunta a ${uris.size} foto (Immagini › Pastashoot)"
            else "Fatto, ma $errors foto non sono state elaborate"
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
            if (errors == 0) onClose()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .desktop()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        MenuBar("Pastashoot")
        RetroWindow(
            title = "Nota multipla",
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 9.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                GroupBox("Foto selezionate: ${uris.size}") {
                    LazyRow(
                        Modifier.fillMaxWidth().height(86.dp).well(P.White),
                        contentPadding = PaddingValues(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(uris, key = { it.toString() }) { u -> Thumb(u, Modifier.size(74.dp)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    PushButton("Modifica selezione…", { addMore() }, enabled = !running)
                }
                GroupBox("Nota", Modifier.weight(1f)) {
                    RetroTextField(
                        note, { note = it }, "es. Piano 3 – controsoffitto open space",
                        minHeight = 110.dp, maxHeight = 260.dp, enabled = !running,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (running) {
                    Text("Elaborazione di $done su ${uris.size}…", fontFamily = Pixel, fontSize = 14.sp, color = P.Ink)
                    Spacer(Modifier.height(6.dp))
                    RetroProgress(if (uris.isEmpty()) 0f else done.toFloat() / uris.size)
                } else {
                    PixelCaption("Banda nera con la nota sotto ogni foto. Una nota precedente viene sostituita, i disegni restano. Originali intatti.")
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PushButton("Annulla", onClose, enabled = !running)
                PushButton(
                    "Applica a ${uris.size}", { apply() }, default = true,
                    enabled = !running && note.isNotBlank() && uris.isNotEmpty(),
                )
            }
        }
    }
}

/** Finestra con le foto del telefono (più recenti prima) e selezione multipla. */
@Composable
fun PhotoGrid(preselected: List<Uri>, onDone: (List<Uri>) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var onlyCamera by remember { mutableStateOf(true) }
    val selected = remember { mutableStateListOf<Uri>().apply { addAll(preselected) } }
    val photos by produceState<List<Uri>>(emptyList(), onlyCamera) {
        value = withContext(Dispatchers.IO) { MediaHelper.allPhotos(ctx.contentResolver, onlyCamera) }
    }
    BackHandler { onCancel() }

    Column(
        Modifier
            .fillMaxSize()
            .desktop()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        MenuBar("Pastashoot")
        RetroWindow(
            title = if (onlyCamera) "Fotocamera" else "Tutte le immagini",
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 9.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    RetroCheckbox(onlyCamera, "Solo fotocamera", { onlyCamera = !onlyCamera })
                }
                PixelCaption("${photos.size} elementi")
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(92.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp).well(P.White),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                    .background(P.Accent.copy(alpha = 0.35f))
                                    .border(3.dp, P.Accent)
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(P.Accent)
                                    .border(1.dp, P.Ink),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${idx + 1}", fontFamily = Pixel, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PushButton("Annulla", onCancel)
                PushButton("Scegli ${selected.size}", { onDone(selected.toList()) }, default = true, enabled = selected.isNotEmpty())
            }
        }
    }
}

@Composable
private fun Thumb(uri: Uri, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, Size(240, 240), null) }.getOrNull()
        }
    }
    Box(modifier.background(P.Canvas)) {
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
