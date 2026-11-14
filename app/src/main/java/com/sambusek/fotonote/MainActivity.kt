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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.createChannels(this)
        if (Prefs.isMonitoring(this) && MediaHelper.hasPhotoPermission(this)) ServiceControl.start(this)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { MainScreen(tick) }
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

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Foto Note", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Scatta con la Fotocamera Samsung come sempre: subito dopo ti arriva una notifica " +
                "per annotare la foto con l'S Pen e aggiungere una nota. L'originale non viene " +
                "modificato; la copia annotata va in Immagini › FotoNote.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // 1. permessi
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Permessi", fontWeight = FontWeight.Bold)
                Text(status(hasPhotos, "Accesso alle foto"))
                Text(status(hasNotif, "Notifiche"))
                if (!hasPhotos || !hasNotif) {
                    Text(
                        "Quando Android chiede l'accesso alle foto scegli \"Consenti tutto\", " +
                            "non \"Seleziona foto\".",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { requestPerms() }) { Text("Concedi") }
                        OutlinedButton(onClick = { openAppSettings() }) { Text("Impostazioni app") }
                    }
                }
            }
        }

        // 2. monitoraggio
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Monitoraggio", fontWeight = FontWeight.Bold)
                SwitchRow("Proponi l'annotazione dopo ogni scatto", monitoring) { on ->
                    if (on && !hasPhotos) {
                        Toast.makeText(ctx, "Prima concedi l'accesso alle foto", Toast.LENGTH_SHORT).show()
                        requestPerms()
                        return@SwitchRow
                    }
                    monitoring = on
                    Prefs.setMonitoring(ctx, on)
                    if (on) ServiceControl.start(ctx) else ServiceControl.stop(ctx)
                }
                SwitchRow("Apri l'editor da solo (senza toccare la notifica)", autoOpen) { on ->
                    autoOpen = on
                    Prefs.setAutoOpen(ctx, on)
                    if (on && !Settings.canDrawOverlays(ctx)) {
                        Toast.makeText(
                            ctx, "Attiva \"Comparsa sopra altre app\" per Foto Note", Toast.LENGTH_LONG
                        ).show()
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    }
                }
                if (autoOpen && !hasOverlay) {
                    Text(
                        "Manca il permesso \"Comparsa sopra altre app\": per ora riceverai solo la notifica.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // 3. batteria
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3. Risparmio batteria", fontWeight = FontWeight.Bold)
                Text(status(noBatteryLimit, "Escluso dall'ottimizzazione batteria"))
                Text(
                    "Samsung tende a chiudere le app in background. Escludi Foto Note e, in " +
                        "Impostazioni › Batteria › Limiti utilizzo in background, aggiungila " +
                        "alle \"App mai in sospensione\".",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!noBatteryLimit) {
                    Button(onClick = {
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
                    }) { Text("Escludi dal risparmio batteria") }
                }
            }
        }

        // prova manuale
        OutlinedButton(
            onClick = {
                val p = if (hasPhotos) MediaHelper.latestCameraPhoto(ctx.contentResolver) else null
                if (p == null) {
                    Toast.makeText(ctx, "Nessuna foto trovata in DCIM/Camera", Toast.LENGTH_SHORT).show()
                } else ctx.startActivity(EditorActivity.intent(ctx, p.uri))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Annota l'ultima foto scattata") }

        Text(
            "Suggerimento: dalla Galleria puoi anche usare Condividi › Annota con Foto Note " +
                "su qualsiasi foto.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun status(ok: Boolean, label: String) = (if (ok) "✅ " else "⚠️ ") + label

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
