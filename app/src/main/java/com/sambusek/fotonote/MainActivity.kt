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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
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
            Toast.makeText(ctx, "Attiva \"Comparsa sopra altre app\" per Pastashoot", Toast.LENGTH_LONG).show()
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

    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .desktop()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        MenuBar("Pastashoot")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 10.dp, end = 13.dp, top = 14.dp, bottom = 16.dp)
        ) {
            RetroWindow("Pastashoot", Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .well(P.AccentSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painterResource(R.drawable.ic_stat_pen), null,
                                colorFilter = ColorFilter.tint(P.Accent),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Pastashoot", fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = P.Ink)
                            Text("versione $version", fontFamily = Pixel, fontSize = 13.sp, color = P.Ink2)
                        }
                        Spacer(Modifier.weight(1f))
                        StatusLight(monitoring)
                    }

                    GroupBox("Azioni", Modifier.padding(top = 12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BevelButton("Ultima foto", height = 104.dp, onClick = {
                                val p = if (hasPhotos) MediaHelper.latestCameraPhoto(ctx.contentResolver) else null
                                if (p == null) {
                                    Toast.makeText(ctx, "Nessuna foto trovata in DCIM/Camera", Toast.LENGTH_SHORT).show()
                                } else ctx.startActivity(EditorActivity.intent(ctx, p.uri))
                            }) { BigGlyph("+") }
                            BevelButton("Nota multipla", height = 104.dp, onClick = {
                                ctx.startActivity(BatchActivity.intent(ctx))
                            }) { BigGlyph("≡") }
                        }
                    }

                    GroupBox("Dopo lo scatto") {
                        RetroCheckbox(monitoring, "Proponi l'annotazione", { toggleMonitoring() })
                        RetroCheckbox(autoOpen, "Apri l'editor da solo", { toggleAutoOpen() })
                        if (autoOpen && !hasOverlay) {
                            PixelCaption("Manca il permesso \"Comparsa sopra altre app\": per ora arriva solo la notifica.")
                        }
                    }

                    GroupBox("Pannello di controllo") {
                        PermRow("Accesso alle foto", hasPhotos) { if (hasPhotos) openAppSettings() else requestPerms() }
                        PermRow("Posizione GPS", hasGps) { if (hasGps) openAppSettings() else requestPerms() }
                        PermRow("Notifiche", hasNotif) { if (hasNotif) openAppSettings() else requestPerms() }
                        PermRow("Batteria libera", noBatteryLimit) { if (noBatteryLimit) openAppSettings() else battery() }
                    }

                    PixelCaption(
                        "Scatta con la Fotocamera: dopo ogni foto arriva una notifica per annotarla con l'S Pen. " +
                            "Gli originali restano intatti; le copie vanno in Immagini › Pastashoot.\n\n" +
                            "Per le foto scegli \"Consenti tutto\". In Impostazioni › Batteria aggiungi " +
                            "Pastashoot alle \"App mai in sospensione\".\n\n" +
                            "Dalla Galleria: Condividi › \"Annota con Pastashoot\" o, con più foto, \"Nota su più foto\".",
                        Modifier.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLight(ok)
        Spacer(Modifier.width(10.dp))
        Text(label, fontFamily = Sys, fontSize = 15.sp, color = P.Ink, modifier = Modifier.weight(1f))
        PushButton(if (ok) "Apri…" else "Concedi", onFix)
    }
}

@Composable
private fun BigGlyph(g: String) {
    Text(g, fontFamily = Sys, fontWeight = FontWeight.Light, fontSize = 52.sp, color = P.Accent)
}
