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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platinumEdgeToEdge()
        Notifications.createChannels(this)
        if (Prefs.isMonitoring(this) && MediaHelper.hasPhotoPermission(this)) ServiceControl.start(this)
        setContent {
            PlatinumTheme {
                var settings by rememberSaveable { mutableStateOf(false) }
                if (settings) SettingsScreen(tick) { settings = false }
                else HomeScreen(tick) { settings = true }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

private fun granted(c: Context, p: String) =
    ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

private fun photoPerms(): Array<String> {
    val list = mutableListOf(Manifest.permission.ACCESS_MEDIA_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) {
        list += Manifest.permission.READ_MEDIA_IMAGES
        list += Manifest.permission.POST_NOTIFICATIONS
    } else list += Manifest.permission.READ_EXTERNAL_STORAGE
    return list.toTypedArray()
}

private val dayTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
private val dayOnly = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

// ------------------------------------------------------------------ home: i sopralluoghi

@Composable
private fun HomeScreen(tick: MutableIntState, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val t = tick.intValue // si aggiorna a ogni ritorno sulla schermata

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { tick.intValue++ }

    val hasPhotos = remember(t) { MediaHelper.hasPhotoPermission(ctx) }
    val sessions = remember(t) { SessionStore.all(ctx) }
    val active = sessions.firstOrNull { it.open }
    val closed = sessions.filter { !it.open }
    val activeCount = remember(t, active?.id) {
        active?.let { s -> SessionStore.photos(ctx, s).count { it.uri.toString() !in s.excluded } } ?: 0
    }
    var askName by remember { mutableStateOf(false) }

    if (askName) {
        RetroPrompt(
            title = "Nuovo sopralluogo",
            message = "Dai un nome alla sessione: diventerà il nome della cartella.",
            placeholder = "es. Via Roma piano 3",
            confirm = "Inizia",
            onConfirm = { name ->
                askName = false
                val s = SessionStore.create(ctx, name)
                ctx.startActivity(SessionActivity.intent(ctx, s.id))
            },
            onDismiss = { askName = false },
        )
    }

    DeskScreen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowPad(start = 10.dp, end = 13.dp, top = 14.dp, bottom = 16.dp)
        ) {
            RetroWindow(
                "Sopralluoghi", Modifier.fillMaxWidth(),
                actions = { ToolIcon(AppIcons.Tune, "Impostazioni", onSettings) },
            ) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 16.dp)) {
                    if (!hasPhotos) {
                        GroupBox("Prima di iniziare") {
                            PixelCaption("Per raccogliere le foto del sopralluogo serve l'accesso alle foto: scegli \"Consenti tutto\".")
                            Spacer(Modifier.height(8.dp))
                            PushButton("Concedi l'accesso", { permLauncher.launch(photoPerms()) }, default = true)
                        }
                    }

                    if (active != null) {
                        GroupBox("Sessione in corso") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusLight(true)
                                Spacer(Modifier.width(10.dp))
                                BigText(active.name)
                            }
                            Spacer(Modifier.height(4.dp))
                            PixelCaption(
                                "Iniziata il ${dayTime.format(Date(active.start))} · $activeCount foto"
                            )
                            Spacer(Modifier.height(10.dp))
                            PushButton(
                                "Apri la sessione",
                                { ctx.startActivity(SessionActivity.intent(ctx, active.id)) },
                                default = true,
                            )
                        }
                    } else {
                        GroupBox("Nuovo sopralluogo") {
                            PixelCaption(
                                "Inizia una sessione e scatta: le foto finiscono nella sessione, dove le annoti. " +
                                    "Alla chiusura vengono salvate in una cartella dedicata."
                            )
                            Spacer(Modifier.height(10.dp))
                            PushButton("Inizia sopralluogo…", { askName = true }, default = true, enabled = hasPhotos)
                        }
                    }

                    GroupBox("Sopralluoghi salvati") {
                        if (closed.isEmpty()) {
                            PixelCaption("Ancora nessuno. I sopralluoghi chiusi compaiono qui.")
                        } else {
                            Column {
                                closed.forEachIndexed { i, s ->
                                    if (i > 0) ListDivider()
                                    ListRow(s.folder, "${s.items.size} foto · ${dayOnly.format(Date(s.start))}") {
                                        ctx.startActivity(SessionActivity.intent(ctx, s.id))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ impostazioni

@Composable
private fun SettingsScreen(tick: MutableIntState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val t = tick.intValue
    BackHandler { onBack() }

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
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    fun requestPerms() = permLauncher.launch(photoPerms())

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

    DeskScreen {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowPad(start = 10.dp, end = 13.dp, top = 14.dp, bottom = 16.dp)
        ) {
            RetroWindow("Impostazioni", Modifier.fillMaxWidth(), onBack = onBack) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 0.dp, bottom = 16.dp)) {
                    GroupBox("Aspetto") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (row in SkinId.entries.chunked(3)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (id in row) {
                                        BevelButton(
                                            null, height = 40.dp,
                                            on = LocalSkin.current.id == id,
                                            onClick = {
                                                SkinStore.set(ctx, id)
                                                // le barre di sistema cambiano stile con l'aspetto
                                                (ctx as? android.app.Activity)?.recreate()
                                            },
                                        ) { BevelValue(id.label, size = 13) }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }

                    GroupBox("Dopo lo scatto") {
                        RetroCheckbox(monitoring, "Notifica per annotare subito", { toggleMonitoring() })
                        RetroCheckbox(autoOpen, "Apri l'editor da solo", { toggleAutoOpen() })
                        if (autoOpen && !hasOverlay) {
                            PixelCaption("Manca il permesso \"Comparsa sopra altre app\": per ora arriva solo la notifica.")
                        }
                    }

                    GroupBox("Permessi") {
                        PermRow("Accesso alle foto", hasPhotos) { if (hasPhotos) openAppSettings() else requestPerms() }
                        PermRow("Posizione GPS", hasGps) { if (hasGps) openAppSettings() else requestPerms() }
                        PermRow("Notifiche", hasNotif) { if (hasNotif) openAppSettings() else requestPerms() }
                        PermRow("Batteria libera", noBatteryLimit) { if (noBatteryLimit) openAppSettings() else battery() }
                    }

                    GroupBox("Foto singole, fuori sessione") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PushButton("Annota l'ultima foto", {
                                val p = if (hasPhotos) MediaHelper.latestCameraPhoto(ctx.contentResolver) else null
                                if (p == null) {
                                    Toast.makeText(ctx, "Nessuna foto trovata in DCIM/Camera", Toast.LENGTH_SHORT).show()
                                } else ctx.startActivity(EditorActivity.intent(ctx, p.uri))
                            })
                            PushButton("Nota multipla", { ctx.startActivity(BatchActivity.intent(ctx)) })
                        }
                    }

                    PixelCaption(
                        "Pastashoot $version. Per non perdere le notifiche, in Impostazioni › Batteria " +
                            "aggiungi Pastashoot alle \"App mai in sospensione\".",
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
        Text(tx(label), fontFamily = Sys, fontSize = 15.sp, color = P.Ink, modifier = Modifier.weight(1f))
        PushButton(if (ok) "Apri…" else "Concedi", onFix)
    }
}
