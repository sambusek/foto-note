package com.sambusek.fotonote

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

/** Elenco dei cantieri, con la creazione di uno nuovo. */
@Composable
fun SitesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    open?.let { id ->
        SiteScreen(id, onBack = { open = null; tick++ })
        return
    }
    BackHandler { onBack() }
    val sites = remember(tick) { SiteStore.all(ctx) }
    val sessions = remember(tick) { SessionStore.all(ctx) }
    if (creating) NewSiteDialog(
        onConfirm = { name, template ->
            creating = false
            open = SiteStore.create(ctx, name, template?.fields.orEmpty()).id
        },
        onDismiss = { creating = false },
    )
    AppScreen("Cantieri", onBack = onBack, maxWidth = ReadableWidth) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Section("Cantieri") {
                ActionRow(AppIcons.Add, "Nuovo cantiere…", "Con le sue planimetrie e i campi per i sopralluoghi") { creating = true }
                for (s in sites) {
                    ListDivider()
                    val n = sessions.count { (it.siteId ?: SiteStore.GENERIC) == s.id }
                    ListRow(
                        s.name,
                        listOfNotNull(
                            s.client.ifBlank { null },
                            "${s.plans.size} tavol${if (s.plans.size == 1) "a" else "e"}",
                            "$n sopralluogh${if (n == 1) "o" else "i"}",
                        ).joinToString(" · "),
                    ) { open = s.id }
                }
            }
        }
    }
}

/** Nome del nuovo cantiere e, se ce ne sono, il template da cui prendere i campi. */
@Composable
fun NewSiteDialog(onConfirm: (String, Template?) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val templates = remember { TemplateStore.all(ctx) }
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf<Template?>(null) }
    var open by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        title = { Text("Nuovo cantiere") },
        text = {
            Column {
                CompactField(name, { name = it.replace("\n", " ") }, "es. Via Gonzaga 4")
                if (templates.isNotEmpty()) {
                    Text("Campi da un template", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    Box {
                        AppButton(template?.name ?: "Nessuno (senza campi)", { open = true }, Modifier.fillMaxWidth())
                        DropdownMenu(open, { open = false }) {
                            DropdownMenuItem(text = { Text("Nessuno (senza campi)") }, onClick = { template = null; open = false })
                            for (t in templates) DropdownMenuItem(
                                text = { Column { Text(t.name); Caption(t.fields.summary()) } },
                                onClick = { template = t; open = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(name.trim(), template) }, enabled = name.isNotBlank()) { Text("Crea") } },
        dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
    )
}

/** Scheda di un cantiere: planimetrie (con revisioni), campi e layout, sopralluoghi. */
@Composable
fun SiteScreen(id: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val site = remember(tick) { SiteStore.get(ctx, id) }
    if (site == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val sessions = remember(tick) { SessionStore.all(ctx).filter { (it.siteId ?: SiteStore.GENERIC) == id } }
    var editFields by remember { mutableStateOf(false) }
    var planMenu by remember { mutableStateOf<Plan?>(null) }
    var renamePlan by remember { mutableStateOf<Plan?>(null) }
    var revFor by remember { mutableStateOf<Plan?>(null) }
    var pageChoice by remember { mutableStateOf<Pair<Uri, Int>?>(null) }
    var renameSite by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    busy?.let { ProgressDialog(it, 0f) }

    if (editFields) {
        LayoutScreen(
            site.fields, fieldSources(ctx),
            onDone = { SiteStore.save(ctx, site.copy(fields = it)); editFields = false; tick++ },
            onCancel = { editFields = false },
            title = "Campi del cantiere",
        )
        return
    }
    BackHandler { onBack() }

    fun run(msg: String, work: () -> Unit) {
        scope.launch {
            busy = msg
            val r = withContext(Dispatchers.IO) { runCatching { work() } }
            busy = null
            r.onFailure { Toast.makeText(ctx, "Non riesco ad aprire il PDF: ${it.message}", Toast.LENGTH_LONG).show() }
            tick++
        }
    }
    val addPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run("Caricamento delle planimetrie…") { SiteStore.addPlans(ctx, id, uri) }
    }
    val revPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val plan = revFor
        if (uri != null && plan != null) scope.launch {
            val pages = withContext(Dispatchers.IO) { runCatching { SiteStore.pageCount(ctx, uri) }.getOrDefault(0) }
            if (pages > 1) pageChoice = uri to pages
            else run("Caricamento della revisione…") { SiteStore.addRevision(ctx, id, plan.id, uri, 0) }
        }
    }

    planMenu?.let { p ->
        AlertDialog(
            onDismissRequest = { planMenu = null },
            shape = RoundedCornerShape(12.dp),
            title = { Text(p.name) },
            text = {
                Column {
                    Caption("Revisioni:")
                    p.revisions.reversed().forEachIndexed { i, r ->
                        Caption("${if (i == 0) "● attuale" else "○"}  ${dayFmt.format(Date(r.time))} · ${r.source}${if (r.page > 0) " (p. ${r.page + 1})" else ""}")
                    }
                    Caption(
                        "Una nuova revisione vale per i prossimi sopralluoghi; quelli chiusi restano sulla loro.",
                        Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton({ planMenu = null; revFor = p; revPicker.launch(arrayOf("application/pdf")) }) { Text("Nuova revisione…") }
            },
            dismissButton = {
                Column {
                    TextButton({ planMenu = null; renamePlan = p }) { Text("Rinomina") }
                    TextButton({ planMenu = null; SiteStore.removePlan(ctx, id, p.id); tick++ }) { Text("Togli dal cantiere") }
                }
            },
        )
    }
    renamePlan?.let { p ->
        PromptDialog(
            "Nome della tavola", "Es. Piano 3, Copertura, Prospetto nord.", "Nome", "Salva",
            onConfirm = { SiteStore.renamePlan(ctx, id, p.id, it); renamePlan = null; tick++ },
            onDismiss = { renamePlan = null }, initial = p.name,
        )
    }
    pageChoice?.let { (uri, pages) ->
        AlertDialog(
            onDismissRequest = { pageChoice = null },
            shape = RoundedCornerShape(12.dp),
            title = { Text("Quale pagina?") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Caption("Il PDF ha $pages pagine: scegli quella che sostituisce «${revFor?.name}».")
                    for (p in 0 until pages) TextButton({
                        val plan = revFor
                        pageChoice = null
                        if (plan != null) run("Caricamento della revisione…") { SiteStore.addRevision(ctx, id, plan.id, uri, p) }
                    }) { Text("Pagina ${p + 1}") }
                }
            },
            confirmButton = { TextButton({ pageChoice = null }) { Text("Annulla") } },
        )
    }
    if (renameSite) PromptDialog(
        "Nome del cantiere", "", "Nome", "Salva",
        onConfirm = { n -> if (n.isNotBlank()) SiteStore.save(ctx, site.copy(name = n)); renameSite = false; tick++ },
        onDismiss = { renameSite = false }, initial = site.name,
    )
    if (askDelete) ConfirmDialog(
        title = "Eliminare il cantiere «${site.name}»?",
        text = "Le sue planimetrie vengono cancellate; i suoi ${sessions.size} sopralluoghi restano e passano al cantiere «Generico», senza pin.",
        confirm = "Elimina",
        onConfirm = { askDelete = false; SiteStore.delete(ctx, id); onBack() },
        dismiss = "Annulla",
        onDismiss = { askDelete = false },
    )

    AppScreen(
        site.name, subtitle = site.client.ifBlank { null }, onBack = onBack, maxWidth = ReadableWidth,
        actions = {
            if (id != SiteStore.GENERIC) OverflowMenu(listOf("Rinomina…" to { renameSite = true }, "Elimina il cantiere…" to { askDelete = true }))
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Section("Planimetrie") {
                ActionRow(AppIcons.Add, "Carica planimetrie (PDF)…", "Ogni pagina diventa una tavola") {
                    addPicker.launch(arrayOf("application/pdf"))
                }
                for (p in site.plans) {
                    ListDivider()
                    ListRow(
                        p.name, "${dayFmt.format(Date(p.current.time))} · ${p.current.source}",
                        tags = if (p.revisions.size > 1) listOf("Rev. ${p.revisions.size}" to false) else emptyList(),
                    ) { planMenu = p }
                }
            }
            Section("Campi e layout") {
                Caption("Schema da cui partono i nuovi sopralluoghi del cantiere: ${site.fields.summary()}")
                AppButton("Modifica…", { editFields = true }, Modifier.padding(top = 8.dp))
            }
            Section("Sopralluoghi") {
                if (sessions.isEmpty()) Caption("Ancora nessuno.")
                sessions.forEachIndexed { i, s ->
                    if (i > 0) ListDivider()
                    ListRow(
                        s.folder, "${dayFmt.format(Date(s.start))} · ${s.pins.size} foto sulle tavole",
                        tags = listOfNotNull(if (s.open) (if (s.paused) "In pausa" else "In corso") to true else null, s.versionTag?.let { it to false }),
                        locked = !s.open,
                    ) { ctx.startActivity(SessionActivity.intent(ctx, s.id)) }
                }
            }
        }
    }
}
