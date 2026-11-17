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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.IntentCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** Cresce a ogni ritorno sulla schermata, per rileggere sessioni e permessi. */
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.createChannels(this)
        if (Prefs.isMonitoring(this) && MediaHelper.hasPhotoPermission(this)) ServiceControl.start(this)
        importTemplate(intent)
        setContent {
            AppTheme {
                var user by remember { mutableStateOf(Prefs.userName(this)) }
                var settings by rememberSaveable { mutableStateOf(false) }
                when {
                    user.isBlank() -> WelcomeScreen { Prefs.setUserName(this, it); user = it }
                    settings -> SettingsScreen(tick, onNameChange = { user = it }) { settings = false }
                    else -> HomeScreen(tick, user) { settings = true }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importTemplate(intent)
    }

    /** Un file di template aperto o condiviso con Pastashoot da un'altra app. */
    private fun importTemplate(i: Intent?) {
        val uri = when (i?.action) {
            Intent.ACTION_VIEW -> i.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(i, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        TemplateStore.importWithToast(this, uri)
        tick.intValue++
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

private fun granted(c: Context, p: String) =
    ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

private fun photoPerms(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.POST_NOTIFICATIONS)
    } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
}.toTypedArray()

private val dayTime = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY)
private val dayOnly = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

// ------------------------------------------------------------------ primo avvio

/** Alla prima apertura si imposta il nome utente (usabile anche come campo automatico). */
@Composable
private fun WelcomeScreen(onDone: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AppScreen("Benvenuto") {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Come ti chiami?", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Caption(
                "Il tuo nome compare nei report PDF e si può stampare sulle foto con il campo \"Nome utente\". " +
                    "Puoi cambiarlo nelle impostazioni.",
                Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            CompactField(name, { name = it }, "Nome e cognome")
            AppButton(
                "Continua", { onDone(name.trim()) }, Modifier.fillMaxWidth().padding(top = 12.dp),
                primary = true, enabled = name.isNotBlank(),
            )
        }
    }
}

// ------------------------------------------------------------------ home: i sopralluoghi

@Composable
private fun HomeScreen(tick: MutableIntState, user: String, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val t = tick.intValue
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick.intValue++
    }

    val hasPhotos = remember(t) { MediaHelper.hasPhotoPermission(ctx) }
    val sessions = remember(t) { SessionStore.all(ctx) }
    val active = sessions.firstOrNull { it.open }
    val closed = sessions.filter { !it.open }
    val activeCount = remember(t, active?.id) {
        active?.let { s -> SessionStore.photos(ctx, s).count { it.uri.toString() !in s.excluded } } ?: 0
    }
    var askName by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf<String>() }   // id dei sopralluoghi selezionati
    var askDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    fun toggle(id: String) { if (id in selected) selected.remove(id) else selected.add(id) }
    BackHandler(enabled = selecting) { selected.clear() }

    if (askDelete) {
        val chosen = closed.filter { it.id in selected }
        val photos = chosen.sumOf { s -> s.releases.sumOf { it.items.size } }
        ConfirmDialog(
            title = if (chosen.size == 1) "Eliminare il sopralluogo?" else "Eliminare ${chosen.size} sopralluoghi?",
            text = chosen.take(6).joinToString("\n") { "• ${it.folder}" } +
                (if (chosen.size > 6) "\n… e altri ${chosen.size - 6}" else "") +
                "\n\nVerranno persi definitivamente i dati di questi sopralluoghi (campi, note, versioni) " +
                "e le $photos foto salvate nelle loro cartelle. Le foto originali della fotocamera restano nella Galleria.",
            confirm = "Elimina",
            onConfirm = {
                askDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) { chosen.forEach { SessionOps.delete(ctx, it) } }
                    Toast.makeText(
                        ctx, if (chosen.size == 1) "Sopralluogo eliminato" else "${chosen.size} sopralluoghi eliminati",
                        Toast.LENGTH_SHORT,
                    ).show()
                    selected.clear()
                    tick.intValue++
                }
            },
            dismiss = "Annulla",
            onDismiss = { askDelete = false },
        )
    }

    if (askName) NewSessionDialog(
        templates = remember { TemplateStore.all(ctx) },
        onConfirm = { name, template ->
            askName = false
            val s = SessionStore.create(ctx, name, template?.fields.orEmpty())
            ctx.startActivity(SessionActivity.intent(ctx, s.id))
        },
        onDismiss = { askName = false },
    )

    AppScreen(
        title = if (selecting) "${selected.size} selezionati" else "Sopralluoghi",
        onBack = if (selecting) ({ selected.clear() }) else null,
        actions = {
            if (selecting) {
                BarAction("Tutti", { selected.clear(); selected.addAll(closed.map { it.id }) })
                ToolIcon(AppIcons.Delete, "Elimina i selezionati", { askDelete = true })
            } else ToolIcon(AppIcons.Tune, "Impostazioni", onSettings)
        },
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
        ) {
            Text(
                "Ciao, $user!", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 2.dp, top = 4.dp),
            )
            if (!hasPhotos) Section("Prima di iniziare") {
                Caption("Per raccogliere le foto del sopralluogo serve l'accesso alle foto: scegli \"Consenti tutto\".")
                Spacer(Modifier.height(8.dp))
                AppButton("Concedi l'accesso", { permLauncher.launch(photoPerms()) }, primary = true)
            }

            if (active != null) Section("Sessione in corso") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(true)
                    Spacer(Modifier.width(8.dp))
                    Text(active.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                    active.versionTag?.let { Tag(it) }
                    if (active.paused) Tag("In pausa", strong = true)
                }
                Caption("Iniziata il ${dayTime.format(Date(active.start))} · $activeCount foto")
                Spacer(Modifier.height(10.dp))
                AppButton(
                    "Apri la sessione", { ctx.startActivity(SessionActivity.intent(ctx, active.id)) },
                    primary = true, modifier = Modifier.fillMaxWidth(),
                )
            } else Section("Nuovo sopralluogo") {
                Caption(
                    "Inizia una sessione e scatta: le foto finiscono nella sessione, dove le annoti. " +
                        "Alla chiusura vengono salvate in una cartella dedicata."
                )
                Spacer(Modifier.height(10.dp))
                AppButton(
                    "Inizia sopralluogo…", { askName = true }, primary = true, enabled = hasPhotos,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("Sopralluoghi salvati") {
                if (closed.isEmpty()) Caption("Ancora nessuno. I sopralluoghi chiusi compaiono qui.")
                else if (!selecting) Caption("Tieni premuto un sopralluogo per selezionarne più di uno.")
                closed.forEachIndexed { i, s ->
                    if (i > 0) ListDivider()
                    ListRow(
                        s.folder, "${s.items.size} foto · ${dayOnly.format(Date(s.start))}",
                        tags = listOfNotNull(s.versionTag?.let { it to false }),
                        locked = true,
                        selected = s.id in selected,
                        onLongClick = { toggle(s.id) },
                    ) {
                        if (selecting) toggle(s.id) else ctx.startActivity(SessionActivity.intent(ctx, s.id))
                    }
                }
            }
        }
    }
}

/** Nome del nuovo sopralluogo e, se ce ne sono, il template da cui prendere i campi. */
@Composable
private fun NewSessionDialog(templates: List<Template>, onConfirm: (String, Template?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf<Template?>(null) }
    var open by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text("Nuovo sopralluogo") },
        text = {
            Column {
                Text("Il nome diventerà anche il nome della cartella.")
                OutlinedTextField(
                    value = name, onValueChange = { name = it.replace("\n", " ") }, singleLine = true,
                    placeholder = { Text("es. Via Roma piano 3") }, shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (templates.isNotEmpty()) {
                    Text("Template", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Box {
                        AppButton(template?.name ?: "Nessuno (senza campi)", { open = true }, Modifier.fillMaxWidth())
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            DropdownMenuItem(text = { Text("Nessuno (senza campi)") }, onClick = { template = null; open = false })
                            for (t in templates) DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(t.name)
                                        Caption(t.fields.summary())
                                    }
                                },
                                onClick = { template = t; open = false },
                            )
                        }
                    }
                    template?.let { Caption(it.fields.summary(), Modifier.padding(top = 4.dp)) }
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(name.trim(), template) }, enabled = name.isNotBlank()) { Text("Inizia") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}

// ------------------------------------------------------------------ impostazioni

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(tick: MutableIntState, onNameChange: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val t = tick.intValue
    var showTemplates by rememberSaveable { mutableStateOf(false) }
    if (showTemplates) {
        TemplatesScreen { showTemplates = false; tick.intValue++ }
        return
    }
    BackHandler { onBack() }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        tick.intValue++
    }

    val hasPhotos = remember(t) { MediaHelper.hasPhotoPermission(ctx) }
    val hasNotif = remember(t) { Build.VERSION.SDK_INT < 33 || granted(ctx, Manifest.permission.POST_NOTIFICATIONS) }
    val hasGps = remember(t) { granted(ctx, Manifest.permission.ACCESS_MEDIA_LOCATION) }
    val hasOverlay = remember(t) { Settings.canDrawOverlays(ctx) }
    val noBatteryLimit = remember(t) {
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    }
    var monitoring by remember { mutableStateOf(Prefs.isMonitoring(ctx)) }
    var autoOpen by remember { mutableStateOf(Prefs.autoOpen(ctx)) }
    var name by remember { mutableStateOf(Prefs.userName(ctx)) }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }

    fun requestPerms() = permLauncher.launch(photoPerms())
    fun open(action: String) = runCatching {
        ctx.startActivity(Intent(action, Uri.parse("package:${ctx.packageName}")))
    }
    fun openAppSettings() {
        open(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    fun toggleMonitoring() {
        if (!monitoring && !hasPhotos) {
            Toast.makeText(ctx, "Prima concedi l'accesso alle foto", Toast.LENGTH_SHORT).show()
            requestPerms(); return
        }
        monitoring = !monitoring
        Prefs.setMonitoring(ctx, monitoring)
        if (monitoring) ServiceControl.start(ctx) else ServiceControl.stop(ctx)
    }

    fun toggleAutoOpen() {
        autoOpen = !autoOpen
        Prefs.setAutoOpen(ctx, autoOpen)
        if (autoOpen && !Settings.canDrawOverlays(ctx)) {
            Toast.makeText(ctx, "Attiva \"Comparsa sopra altre app\" per Pastashoot", Toast.LENGTH_LONG).show()
            open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        }
    }

    AppScreen("Impostazioni", onBack = onBack) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
        ) {
            Section("Profilo") {
                CompactField(name, {
                    name = it
                    if (it.isNotBlank()) { Prefs.setUserName(ctx, it); onNameChange(it.trim()) }
                }, "Nome e cognome")
                Caption("Usato nei report PDF e nel campo automatico \"Nome utente\".", Modifier.padding(top = 4.dp))
            }

            Section("Template di sopralluogo") {
                val n = remember(t) { TemplateStore.all(ctx).size }
                Caption(
                    if (n == 0) "Schemi di campi pronti da scegliere quando inizi un sopralluogo, esportabili su altri telefoni."
                    else "$n template salvati"
                )
                AppButton("Gestisci template…", { showTemplates = true }, Modifier.padding(top = 8.dp))
            }

            Section("Tema") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = ThemeStore.mode == m,
                            onClick = { ThemeStore.setMode(ctx, m) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(m.label) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Caption("Colore d'accento: ${ACCENTS[ThemeStore.accent].first}")
                Spacer(Modifier.height(8.dp))
                AccentPicker()
            }

            Section("Dopo lo scatto") {
                SwitchRow("Notifica per annotare subito", monitoring, { toggleMonitoring() })
                SwitchRow("Apri l'editor da solo", autoOpen, { toggleAutoOpen() })
                if (autoOpen && !hasOverlay) {
                    Caption("Manca il permesso \"Comparsa sopra altre app\": per ora arriva solo la notifica.")
                }
            }

            Section("Permessi") {
                PermRow("Accesso alle foto", hasPhotos) { if (hasPhotos) openAppSettings() else requestPerms() }
                PermRow("Posizione GPS", hasGps) { if (hasGps) openAppSettings() else requestPerms() }
                PermRow("Notifiche", hasNotif) { if (hasNotif) openAppSettings() else requestPerms() }
                PermRow("Batteria libera", noBatteryLimit) {
                    if (noBatteryLimit) openAppSettings()
                    else open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).onFailure { openAppSettings() }
                }
            }

            Section("Foto singole, fuori sessione") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("Annota l'ultima foto", {
                        val p = if (hasPhotos) MediaHelper.latestCameraPhoto(ctx.contentResolver) else null
                        if (p == null) Toast.makeText(ctx, "Nessuna foto trovata in DCIM/Camera", Toast.LENGTH_SHORT).show()
                        else ctx.startActivity(EditorActivity.intent(ctx, p.uri))
                    })
                    AppButton("Nota multipla", { ctx.startActivity(BatchActivity.intent(ctx)) })
                }
            }

            Caption(
                "Pastashoot $version. Per non perdere le notifiche, in Impostazioni › Batteria " +
                    "aggiungi Pastashoot alle \"App mai in sospensione\".",
                Modifier.padding(top = 14.dp),
            )
        }
    }
}

/** Pallini dei colori d'accento; il primo usa i colori del sistema (sfumato). */
@Composable
private fun AccentPicker() {
    val ctx = LocalContext.current
    val ring = MaterialTheme.colorScheme.onSurface
    val systemFill = Brush.sweepGradient(
        listOf(Color(0xFF6750A4), Color(0xFF2E7D4F), Color(0xFFD9661F), Color(0xFF6750A4))
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ACCENTS.forEachIndexed { i, (name, color) ->
            if (color == null && !dynamicColorAvailable) return@forEachIndexed
            val selected = ThemeStore.accent == i
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(if (selected) 3.dp else 0.dp, ring, CircleShape)
                    .padding(if (selected) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .then(if (color != null) Modifier.background(color) else Modifier.background(systemFill))
                    .clickable { ThemeStore.setAccent(ctx, i) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(AppIcons.Check, name, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(ok)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        AppButton(if (ok) "Apri" else "Concedi", onFix, primary = !ok)
    }
}
