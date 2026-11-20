package com.sambusek.fotonote

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tavole del cantiere di un sopralluogo, con i pin delle foto. Si sceglie una foto nella
 * striscia in basso e si tocca la pianta per posizionarla; toccando un pin si sceglie la sua foto.
 */
class PlanActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    companion object {
        /** [photo]: foto da posizionare (aperta dall'editor). */
        fun intent(c: Context, sessionId: String, photo: Uri? = null): Intent =
            Intent(c, PlanActivity::class.java).putExtra("session", sessionId).apply { photo?.let { putExtra("photo", it.toString()) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getStringExtra("session") ?: return finish()
        val photo = intent.getStringExtra("photo")
        setContent { AppTheme { PlanScreen(id, photo, tick) { finish() } } }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

private val revFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALY)

@Composable
private fun PlanScreen(sessionId: String, startPhoto: String?, tick: MutableIntState, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val t = tick.intValue + refresh
    val s = remember(t) { SessionStore.get(ctx, sessionId) } ?: return AppScreen("Planimetrie", onBack = onClose) {
        CenterMessage("Il sopralluogo non esiste più.", Modifier.fillMaxWidth())
    }
    val site = remember(t) { SiteStore.of(ctx, s) }
    var planId by remember { mutableStateOf(s.pins[startPhoto]?.plan ?: site.plans.firstOrNull()?.id) }
    val plan = site.plans.firstOrNull { it.id == planId } ?: site.plans.firstOrNull()
    val photos by produceState(emptyList<Photo>(), t) { value = withContext(Dispatchers.IO) { SessionStore.photos(ctx, s) } }
    var selected by remember { mutableStateOf(startPhoto) }
    // un pin già messo è bloccato: si sblocca tenendolo premuto (o con «Sposta») e allora pulsa
    var unlocked by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "Caricamento della planimetria…"
            val r = withContext(Dispatchers.IO) { runCatching { SiteStore.addPlans(ctx, site.id, uri) } }
            busy = null
            r.onSuccess { planId = it.plans.lastOrNull()?.id; refresh++ }
                .onFailure { Toast.makeText(ctx, "Non riesco ad aprire il PDF: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    busy?.let { ProgressDialog(it, 0f) }

    var menu by remember { mutableStateOf(false) }
    AppScreen(
        plan?.name ?: "Planimetrie",
        subtitle = "${site.name} · ${s.folder}",
        onBack = onClose,
        actions = {
            if (site.plans.size > 1 || s.open) Box {
                BarAction("Tavole", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    for (p in site.plans) DropdownMenuItem(
                        text = { Text((if (p.id == plan?.id) "✓ " else "") + p.name) },
                        onClick = { planId = p.id; menu = false },
                    )
                    DropdownMenuItem(text = { Text("Carica una planimetria (PDF)…") }, onClick = { menu = false; picker.launch(arrayOf("application/pdf")) })
                }
            }
        },
    ) {
        if (plan == null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CenterMessage("Il cantiere «${site.name}» non ha ancora planimetrie.", Modifier.fillMaxWidth())
                AppButton("Carica una planimetria (PDF)…", { picker.launch(arrayOf("application/pdf")) }, Modifier.fillMaxWidth(), primary = true)
                Caption("Ogni pagina del PDF diventa una tavola. Le tavole restano nel cantiere e servono a tutti i suoi sopralluoghi.")
            }
            return@AppScreen
        }
        // revisione usata da questo sopralluogo; quelli aperti possono passare alla più recente
        val rev = plan.rev(s.planRevs[plan.id])
        if (s.open && rev.id != plan.current.id) InfoBanner(
            AppIcons.Layout, "C'è una revisione più recente di questa tavola (${revFmt.format(Date(plan.current.time))}).",
            action = "Usa la nuova",
            onAction = {
                // i pin restano nello stesso punto della pagina
                SessionStore.save(ctx, s.copy(
                    planRevs = s.planRevs + (plan.id to plan.current.id),
                    pins = s.pins.mapValues { (_, p) -> if (p.plan == plan.id) p.copy(rev = plan.current.id) else p },
                ))
                refresh++
            },
        )
        val numbers = photos.withIndex().associate { it.value.uri.toString() to it.index + 1 }
        PlanCanvas(
            site.id, rev, s.pins.filter { it.value.plan == plan.id }, numbers, selected,
            Modifier.weight(1f).fillMaxWidth(),
            unlocked = unlocked,
            onPinTap = { selected = it; if (unlocked != it) unlocked = null },
            onPinLongPress = { key ->
                selected = key
                if (s.open) unlocked = key
                else Toast.makeText(ctx, "Il sopralluogo è chiuso: i pin non si modificano", Toast.LENGTH_SHORT).show()
            },
            onPlace = { x, y ->
                val key = selected
                if (!s.open) Toast.makeText(ctx, "Il sopralluogo è chiuso: i pin non si modificano", Toast.LENGTH_SHORT).show()
                else if (key == null) Toast.makeText(ctx, "Scegli prima una foto qui sotto", Toast.LENGTH_SHORT).show()
                else if (key in s.pins && unlocked != key) Toast.makeText(
                    ctx, "Il pin è bloccato: tienilo premuto, o tocca «Sposta», per spostarlo", Toast.LENGTH_SHORT,
                ).show()
                else {
                    unlocked = null
                    val old = s.pins[key]
                    SessionStore.save(ctx, s.copy(
                        pins = s.pins + (key to Pin(plan.id, rev.id, x, y, old?.dir)),
                        planRevs = s.planRevs + (plan.id to rev.id),
                    ))
                    // si passa da sola alla prossima foto senza pin
                    val next = photos.map { it.uri.toString() }.dropWhile { it != key }.drop(1).firstOrNull { it !in s.pins }
                    if (startPhoto == null && next != null) selected = next
                    refresh++
                }
            },
        )
        PhotoStrip(
            photos, s, plan, selected, numbers, unlocked,
            onSelect = { selected = it; unlocked = null },
            onUnlock = { key -> unlocked = if (unlocked == key) null else key },
            onRemovePin = { key -> unlocked = null; SessionStore.save(ctx, s.copy(pins = s.pins - key)); refresh++ },
            onOpen = { key -> ctx.startActivity(EditorActivity.intent(ctx, Uri.parse(key), s.id)) },
        )
    }
}

/** La tavola con zoom (due dita) e spostamento; un tocco posiziona la foto scelta o sceglie un pin. */
@Composable
private fun PlanCanvas(
    siteId: String,
    rev: PlanRev,
    pins: Map<String, Pin>,
    numbers: Map<String, Int>,
    selected: String?,
    modifier: Modifier,
    unlocked: String?,
    onPinTap: (String) -> Unit,
    onPinLongPress: (String) -> Unit,
    onPlace: (Float, Float) -> Unit,
) {
    // pulsazione del pin sbloccato
    val pulse by androidx.compose.animation.core.rememberInfiniteTransition(label = "pin").animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
        ),
        label = "pulse",
    )
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val image by produceState<ImageBitmap?>(null, rev.id) {
        value = withContext(Dispatchers.IO) { runCatching { PlanRender.bitmap(ctx, siteId, rev).asImageBitmap() }.getOrNull() }
    }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(rev.id) { mutableStateOf(0f) }
    var origin by remember(rev.id) { mutableStateOf(Offset.Zero) }
    val img = image
    // adattamento iniziale alla finestra
    LaunchedEffect(img, box) {
        if (img != null && box.width > 0 && scale == 0f) {
            scale = minOf(box.width.toFloat() / img.width, box.height.toFloat() / img.height)
            origin = Offset((box.width - img.width * scale) / 2, (box.height - img.height * scale) / 2)
        }
    }
    val numberPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; color = android.graphics.Color.WHITE }
    }
    Box(modifier.background(Color(0xFFE9E9E9)).onSizeChanged { box = it }) {
        if (img == null) {
            CenterMessage("Preparazione della tavola…", Modifier.align(Alignment.Center))
            return@Box
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(rev.id) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val fit = minOf(size.width.toFloat() / img.width, size.height.toFloat() / img.height)
                        val ns = (scale * zoom).coerceIn(fit, fit * 10f)
                        origin = centroid - (centroid - origin) * (ns / scale) + pan
                        scale = ns
                    }
                }
                .pointerInput(rev.id, pins, selected, unlocked) {
                    // il pin più vicino entro un dito
                    fun hit(p: Offset) = pins.entries.minByOrNull { (_, pin) ->
                        (origin + Offset(pin.x * img.width, pin.y * img.height) * scale - p).getDistance()
                    }?.takeIf { (_, pin) ->
                        (origin + Offset(pin.x * img.width, pin.y * img.height) * scale - p).getDistance() < 22.dp.toPx()
                    }
                    detectTapGestures(
                        onLongPress = { p -> hit(p)?.let { onPinLongPress(it.key) } },
                    ) { p ->
                        val hit = hit(p)
                        // toccare un pin lo sceglie; con un pin sbloccato il tocco lo sposta
                        if (hit != null && hit.key != unlocked) onPinTap(hit.key)
                        else {
                            val q = (p - origin) / scale
                            val x = q.x / img.width; val y = q.y / img.height
                            if (x in 0f..1f && y in 0f..1f) onPlace(x, y)
                        }
                    }
                },
        ) {
            if (scale == 0f) return@Canvas
            drawImage(
                img, dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                dstSize = IntSize((img.width * scale).toInt(), (img.height * scale).toInt()),
                filterQuality = FilterQuality.Medium,
            )
            val r = 12.dp.toPx()
            numberPaint.textSize = 11.dp.toPx()
            // i pin: cerchio numerato; quello della foto scelta più grande, con l'anello
            for ((key, pin) in pins.entries.sortedBy { it.key == selected }) {
                val c = origin + Offset(pin.x * img.width, pin.y * img.height) * scale
                val sel = key == selected
                val rr = if (sel) r * 1.35f else r
                pin.dir?.let { d ->
                    // cono della direzione dello scatto
                    drawArc(cs.primary.copy(alpha = 0.3f), d - 90f - 25f, 50f, true, c - Offset(rr * 3, rr * 3), androidx.compose.ui.geometry.Size(rr * 6, rr * 6))
                }
                drawCircle(Color.White, rr + 2.dp.toPx(), c)
                drawCircle(if (sel) cs.primary else cs.primary.copy(alpha = 0.85f), rr, c)
                if (key == unlocked) {
                    // sbloccato: anelli che si allargano e svaniscono
                    drawCircle(cs.primary.copy(alpha = 0.6f * (1f - pulse)), rr + (6f + 18f * pulse).dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    drawCircle(cs.primary.copy(alpha = 0.25f * (1f - pulse)), rr + (18f * pulse).dp.toPx(), c)
                } else if (sel) drawCircle(cs.primary, rr + 6.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                drawIntoCanvas {
                    numberPaint.color = cs.onPrimary.toArgb()
                    it.nativeCanvas.drawText("${numbers[key] ?: "?"}", c.x, c.y + numberPaint.textSize / 3, numberPaint)
                }
            }
        }
    }
}

/** Striscia delle foto: si sceglie quella da posizionare; quelle già posizionate hanno il numero pieno. */
@Composable
private fun PhotoStrip(
    photos: List<Photo>,
    s: Session,
    plan: Plan,
    selected: String?,
    numbers: Map<String, Int>,
    unlocked: String?,
    onSelect: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onRemovePin: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val state = rememberLazyListState()
    LaunchedEffect(selected, photos.size) {
        val i = photos.indexOfFirst { it.uri.toString() == selected }
        if (i >= 0) state.animateScrollToItem(maxOf(0, i - 2))
    }
    Column(Modifier.fillMaxWidth().background(cs.surface).padding(vertical = 6.dp)) {
        val pin = selected?.let { s.pins[it] }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Caption(
                when {
                    selected == null -> "Scegli una foto e tocca la pianta nel punto in cui l'hai scattata."
                    pin == null -> "Foto ${numbers[selected]}: tocca la pianta per posizionarla."
                    unlocked == selected -> "Foto ${numbers[selected]}: pin sbloccato, tocca il punto in cui spostarlo."
                    pin.plan != plan.id -> "Foto ${numbers[selected]}: è su un'altra tavola. «Sposta» per portarla qui."
                    else -> "Foto ${numbers[selected]}: pin bloccato. Tienilo premuto o tocca «Sposta» per spostarlo."
                },
                Modifier.weight(1f),
            )
            if (selected != null) {
                BarAction("Apri", { onOpen(selected) })
                if (pin != null && s.open) {
                    BarAction(if (unlocked == selected) "Blocca" else "Sposta", { onUnlock(selected) })
                    BarAction("Togli", { onRemovePin(selected) })
                }
            }
        }
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(4.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
            itemsIndexed(photos, key = { _, p -> p.id }) { _, p ->
                val key = p.uri.toString()
                val pinned = s.pins[key]
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(if (key == selected) 3.dp else 0.dp, cs.primary, RoundedCornerShape(4.dp))
                        .clickable { onSelect(key) },
                ) {
                    PhotoThumb(p.uri, Modifier.fillMaxSize())
                    Text(
                        "${numbers[key]}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pinned != null) cs.onPrimary else Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (pinned != null) cs.primary else Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}
