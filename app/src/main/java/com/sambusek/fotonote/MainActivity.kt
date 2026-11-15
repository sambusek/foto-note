package com.sambusek.fotonote

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platinumEdgeToEdge()
        Notifications.createChannels(this)
        if (Prefs.isMonitoring(this) && MediaHelper.hasPhotoPermission(this)) ServiceControl.start(this)
        setContent { PlatinumTheme { MainScreen(tick) } }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

private fun granted(c: Context, p: String) =
    ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

@Composable
private fun MainScreen(tick: MutableIntState) {
    val ctx = LocalContext.current
    val t = tick.intValue // si aggiorna al ritorno dalle Impostazioni

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick.intValue++ }

    val hasPhotos = remember(t) { MediaHelper.hasPhotoPermission(ctx) }
    val hasNotif = remember(t) {
        Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS)
    }
    val hasGps = remember(t) { granted(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION) }
    val hasOverlay = remember(t) { Settings.canDrawOverlays(ctx) }
    val noBatteryLimit = remember(t) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    var monitoring by remember { mutableStateOf(Prefs.isMonitoring(ctx)) }
    var autoOpen by remember { mutableStateOf(Prefs.autoOpen(ctx)) }

    fun requestPerms() {
        val list = mutableListOf(Manifest.permission.ACCESS_MEDIA_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.POST_NOTIFICATIONS
        } else list += Manifest.permission.READ_EXTERNAL_STORAGE
        permLauncher.launch(list.toTypedArray())
    }

    fun openAppSettings() {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        )
    }

    fun toggleMonitoring() {
        val on = !monitoring
        if (on && !hasPhotos) {
            Toast.makeText(ctx, "Prima concedi l'accesso alle foto", Toast.LENGTH_SHORT).show()
            requestPerms(); return
        }
        monitoring = on
        Prefs.setMonitoring(ctx, on)
        if (on) ServiceControl.start(ctx) else ServiceControl.stop(ctx)
    }

    fun toggleAutoOpen() {
        autoOpen = !autoOpen
        Prefs.setAutoOpen(ctx, autoOpen)
        if (autoOpen && !Settings.canDrawOverlays(ctx)) {
            Toast.makeText(ctx, "Attiva \"Comparsa sopra altre app\" per Foto Note", Toast.LENGTH_LONG).show()
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        }
    }

    fun battery() {
        try {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${ctx.packageName}")
                )
            )
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(P.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
    ) {
        // intestazione
        GridRow(Modifier.gridFrame()) {
            Cell(height = 112.dp) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "FOTO NOTE", fontSize = 34.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp, color = P.Ink,
                    )
                    Text("Scatta. Annota. Fatto.", fontSize = 15.sp, color = P.Ink2)
                }
            }
            Cell("Stato", weight = 0.55f, height = 112.dp, on = monitoring, onClick = { toggleMonitoring() }) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (monitoring) P.Accent else P.Off)
                )
            }
        }

        // azioni principali
        GridRow {
            Cell("Ultima foto", height = 150.dp, accent = true, onClick = {
                val p = if (hasPhotos) MediaHelper.latestCameraPhoto(ctx.contentResolver) else null
                if (p == null) {
                    Toast.makeText(ctx, "Nessuna foto trovata in DCIM/Camera", Toast.LENGTH_SHORT).show()
                } else ctx.startActivity(EditorActivity.intent(ctx, p.uri))
            }) {
                PlusDisc(filled = false)
            }
            Cell("Nota multipla", height = 150.dp, onClick = { ctx.startActivity(BatchActivity.intent(ctx)) }) {
                PlusDisc(filled = true)
            }
        }

        // impostazioni
        GridRow {
            Cell("Dopo lo scatto", on = monitoring, onClick = { toggleMonitoring() }) {
                CellValue(if (monitoring) "ON" else "OFF")
            }
            Cell("Apri da solo", on = autoOpen, onClick = { toggleAutoOpen() }) {
                CellValue(if (!autoOpen) "OFF" else if (hasOverlay) "ON" else "ON*")
            }
        }
        GridRow {
            Cell("Foto", on = hasPhotos, onClick = { if (hasPhotos) openAppSettings() else requestPerms() }) {
                CellValue(if (hasPhotos) "OK" else "NO", size = 24)
            }
            Cell("GPS", on = hasGps, onClick = { if (hasGps) openAppSettings() else requestPerms() }) {
                CellValue(if (hasGps) "OK" else "NO", size = 24)
            }
            Cell("Avvisi", on = hasNotif, onClick = { if (hasNotif) openAppSettings() else requestPerms() }) {
                CellValue(if (hasNotif) "OK" else "NO", size = 24)
            }
            Cell("Batteria", on = noBatteryLimit, onClick = { if (noBatteryLimit) openAppSettings() else battery() }) {
                CellValue(if (noBatteryLimit) "OK" else "NO", size = 24)
            }
        }

        Caption(
            "Scatta con la Fotocamera Samsung: dopo ogni foto arriva una notifica per annotarla con " +
                "l'S Pen. Gli originali restano intatti; le copie vanno in Immagini › FotoNote.\n\n" +
                "Le celle in lavanda sono attive. Tocca una cella \"NO\" per concedere il permesso; " +
                "per le foto scegli \"Consenti tutto\".\n\n" +
                "Batteria: in Impostazioni › Batteria › Limiti di utilizzo in background, aggiungi " +
                "Foto Note alle \"App mai in sospensione\"." +
                (if (autoOpen && !hasOverlay) "\n\n* Manca il permesso \"Comparsa sopra altre app\": per ora arriva solo la notifica." else "") +
                "\n\nDalla Galleria: Condividi › \"Annota con Foto Note\" o, con più foto, \"Nota su più foto\"."
        )
    }
}

/** Il grande disco con il "+", come il pulsante di scatto. */
@Composable
private fun PlusDisc(filled: Boolean) {
    Box(
        Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(if (filled) P.Accent else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", fontSize = 44.sp, fontWeight = FontWeight.Light, color = if (filled) Color.White else P.Accent)
    }
}
