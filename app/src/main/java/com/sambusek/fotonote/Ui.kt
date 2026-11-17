package com.sambusek.fotonote

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.isSystemInDarkTheme
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.fillMaxSize
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Colori della skin attiva (da usare dentro le funzioni @Composable). */
object P {
    val Bg: Color @Composable get() = LocalSkin.current.bg
    val White: Color @Composable get() = LocalSkin.current.field
    val Shade: Color @Composable get() = LocalSkin.current.lo
    val Ink: Color @Composable get() = LocalSkin.current.ink
    val Ink2: Color @Composable get() = LocalSkin.current.ink2
    val Off: Color @Composable get() = LocalSkin.current.off
    val Accent: Color @Composable get() = LocalSkin.current.accent
    val AccentSoft: Color @Composable get() = LocalSkin.current.onBg
    val Canvas: Color @Composable get() = LocalSkin.current.canvas
}

/** Caratteri della skin attiva. */
val Pixel: FontFamily @Composable get() = LocalSkin.current.smallFont
val Sys: FontFamily @Composable get() = LocalSkin.current.bodyFont

/** Colore del contenuto dentro un pulsante (cambia quando è premuto o attivo). */
val LocalCellInk = compositionLocalOf { Color.Black }

/** Colore per icone e segni dentro un pulsante. */
@Composable
fun glyphInk(): Color {
    val sk = LocalSkin.current
    val ink = LocalCellInk.current
    return if ((sk.id == SkinId.PLATINUM || sk.id == SkinId.ANDROID) && ink == sk.ink) sk.accent else ink
}

/** Plan 9 scrive etichette e titoli in minuscolo. */
@Composable
fun tx(s: String): String = if (LocalSkin.current.lowercase) s.lowercase() else s

val isAndroidSkin: Boolean @Composable get() = LocalSkin.current.id == SkinId.ANDROID

@Composable
fun PlatinumTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    SkinStore.load(ctx)
    if (SkinStore.current.id == SkinId.ANDROID) {
        // Material 3 con i colori di sistema (Android 12+) e il tema chiaro/scuro del telefono
        val dark = isSystemInDarkTheme()
        val cs = when {
            Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            dark -> darkColorScheme()
            else -> lightColorScheme()
        }
        val sk = androidSkin(cs)
        CompositionLocalProvider(LocalSkin provides sk, LocalCellInk provides sk.ink) {
            MaterialTheme(
                colorScheme = cs,
                shapes = Shapes(
                    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp),
                    medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(8.dp),
                    extraLarge = RoundedCornerShape(12.dp),
                ),
                content = content,
            )
        }
        return
    }
    val sk = SkinStore.current
    val square = RoundedCornerShape(0.dp)
    CompositionLocalProvider(LocalSkin provides sk, LocalCellInk provides sk.ink) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = sk.accent, onPrimary = Color.White,
                primaryContainer = sk.onBg, onPrimaryContainer = sk.onInk,
                background = sk.bg, onBackground = sk.ink,
                surface = sk.bg, onSurface = sk.ink,
                surfaceVariant = sk.bg, onSurfaceVariant = sk.ink2,
                surfaceContainer = sk.bg, surfaceContainerHigh = sk.bg, surfaceContainerHighest = sk.bg,
                outline = sk.ink, outlineVariant = sk.lo,
            ),
            shapes = Shapes(square, square, square, square, square),
            content = content,
        )
    }
}

fun ComponentActivity.platinumEdgeToEdge() {
    SkinStore.load(this)
    if (SkinStore.current.id == SkinId.ANDROID) {
        enableEdgeToEdge() // icone delle barre di sistema secondo il tema chiaro/scuro
        return
    }
    val t = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(SystemBarStyle.light(t, t), SystemBarStyle.dark(t))
}

// ---------------------------------------------------------------- bordi e superfici

private fun DrawScope.hLine(c: Color, y: Float, x0: Float, x1: Float, w: Float) =
    drawRect(c, Offset(x0, y), Size(x1 - x0, w))

private fun DrawScope.vLine(c: Color, x: Float, y0: Float, y1: Float, w: Float) =
    drawRect(c, Offset(x, y0), Size(w, y1 - y0))

/** Rilievo a 1 pixel (solo nella skin Platinum). */
@Composable
fun Modifier.bevel(raised: Boolean = true): Modifier {
    val sk = LocalSkin.current
    if (!sk.bevel) return this
    return drawBehind {
        val w = 1.dp.toPx()
        val a = if (raised) sk.hi else sk.lo
        val b = if (raised) sk.lo else sk.hi
        hLine(a, 0f, 0f, size.width, w); vLine(a, 0f, 0f, size.height, w)
        hLine(b, size.height - w, 0f, size.width, w); vLine(b, size.width - w, 0f, size.height, w)
    }
}

/** Bordo dei riquadri di gruppo. */
@Composable
fun Modifier.etched(): Modifier {
    val sk = LocalSkin.current
    return when (sk.id) {
        SkinId.PLATINUM, SkinId.BEOS -> drawBehind {
            val w = 1.dp.toPx()
            val sw = size.width
            val sh = size.height
            hLine(sk.lo, 0f, 0f, sw - w, w); vLine(sk.lo, 0f, 0f, sh - w, w)
            hLine(sk.lo, sh - 2 * w, 0f, sw - w, w); vLine(sk.lo, sw - 2 * w, 0f, sh - w, w)
            hLine(sk.hi, w, w, sw - 2 * w, w); vLine(sk.hi, w, w, sh - 2 * w, w)
            hLine(sk.hi, sh - w, 0f, sw, w); vLine(sk.hi, sw - w, 0f, sh, w)
        }
        SkinId.PLAN9 -> border(1.dp, sk.lo)
        SkinId.SYSTEM7 -> border(1.dp, sk.ink)
        SkinId.ANDROID -> this
    }
}

/** Area incassata (campi di testo, liste, area foto). */
@Composable
fun Modifier.well(color: Color? = null): Modifier {
    val sk = LocalSkin.current
    val fill = color ?: sk.field
    return when (sk.id) {
        SkinId.PLATINUM, SkinId.BEOS -> this.bevel(raised = false).padding(1.dp).border(1.dp, sk.ink).padding(1.dp).background(fill)
        SkinId.PLAN9 -> this.border(1.dp, sk.lo).padding(1.dp).background(fill)
        SkinId.SYSTEM7 -> this.border(1.dp, sk.ink).padding(1.dp).background(fill)
        SkinId.ANDROID -> this.clip(RoundedCornerShape(4.dp)).background(fill)
    }
}

/** Scrivania: puntini (Platinum), grigio piatto (Plan 9), scacchiera retinata (Sistema 7). */
@Composable
fun Modifier.desktop(): Modifier {
    val sk = LocalSkin.current
    return when (sk.deskStyle) {
        Desk.FLAT -> this.background(sk.desk)
        Desk.DOTS -> this.background(sk.desk).drawWithCache {
            val step = 10.dp.toPx()
            val dot = 1.5.dp.toPx()
            val path = Path()
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else step / 2
                while (x < size.width) { path.addRect(Rect(x, y, x + dot, y + dot)); x += step }
                y += step / 2; row++
            }
            onDrawBehind { drawPath(path, sk.deskTex) }
        }
        Desk.CHECKER -> this.drawWithCache {
            val c = maxOf(2, (1.dp.toPx()).toInt())
            val bmp = android.graphics.Bitmap.createBitmap(2 * c, 2 * c, android.graphics.Bitmap.Config.ARGB_8888)
            val cv = android.graphics.Canvas(bmp)
            cv.drawColor(android.graphics.Color.WHITE)
            val p = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
            cv.drawRect(0f, 0f, c.toFloat(), c.toFloat(), p)
            cv.drawRect(c.toFloat(), c.toFloat(), 2f * c, 2f * c, p)
            val brush = ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
            onDrawBehind { drawRect(brush) }
        }
    }
}

// ---------------------------------------------------------------- barra dei menu

@Composable
fun MenuBar(title: String) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        // niente barra dei menu finta: solo lo spazio della barra di stato
        Spacer(Modifier.fillMaxWidth().background(sk.menuBg).statusBarsPadding())
        return
    }
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("HH:mm", Locale.ITALY)
        while (true) { time = f.format(Date()); delay(15_000) }
    }
    Column(Modifier.fillMaxWidth().background(sk.menuBg)) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(26.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.ic_stat_pen), null,
                colorFilter = ColorFilter.tint(if (sk.id == SkinId.PLATINUM) sk.accent else sk.ink),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(tx(title), fontFamily = sk.titleFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = sk.ink)
            Spacer(Modifier.weight(1f))
            Text(time, fontFamily = sk.smallFont, fontSize = 14.sp, color = sk.ink)
        }
        Box(Modifier.fillMaxWidth().height(if (sk.id == SkinId.PLAN9) 2.dp else 1.dp).background(sk.menuLine))
    }
}

// ---------------------------------------------------------------- finestre

private fun Modifier.stripes(top: Dp, bottom: Dp, a: Color, b: Color?, gap: Dp): Modifier = drawBehind {
    val w = 1.dp.toPx()
    val x0 = 3.dp.toPx()
    val x1 = size.width - 3.dp.toPx()
    var y = top.toPx()
    val end = size.height - bottom.toPx()
    val lines = if (b != null) 2 else 1
    while (y + lines * w <= end) {
        hLine(a, y, x0, x1, w)
        if (b != null) hLine(b, y + w, x0, x1, w)
        y += lines * w + gap.toPx()
    }
}

/** Finestra con barra del titolo nello stile della skin. */
@Composable
fun RetroWindow(
    title: String,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sk = LocalSkin.current
    if (sk.titleBar == TitleBar.MATERIAL) {
        Column(modifier.background(sk.bg)) {
            if (!small) {
                // barra superiore compatta: indietro, titolo, azioni
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onBack) { Icon(AppIcons.Back, "Indietro") }
                    } else Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(start = 4.dp, end = 4.dp)) {
                        Text(
                            title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                            color = sk.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle != null) Text(
                            subtitle, style = MaterialTheme.typography.bodySmall,
                            color = sk.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (actions != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
                    }
                }
            }
            content()
        }
        return
    }
    val barH = if (small) 20.dp else 28.dp
    val shadowMod = when (sk.shadow) {
        Shadow.SOFT -> Modifier.drawBehind {
            val s = 3.dp.toPx(); drawRect(Color.Black.copy(alpha = 0.45f), Offset(s, s), size)
        }
        Shadow.HARD -> Modifier.drawBehind {
            val s = 2.dp.toPx(); drawRect(Color.Black, Offset(s, s), size)
        }
        Shadow.NONE -> Modifier
    }
    val frame = Modifier
        .then(shadowMod)
        .background(sk.bg)
        .border(sk.winBorderW, sk.winBorder)
        .padding(sk.winBorderW)
        .bevel(raised = true)

    if (sk.titleBar == TitleBar.TAB) {
        // linguetta del titolo sopra la finestra, larga quanto il titolo.
        // La finestra prende lo spazio che resta: tutta l'altezza disponibile se la
        // finestra ha un'altezza fissa, solo quella del contenuto in una pagina scorrevole.
        Layout(
            modifier = modifier,
            content = {
                Box(
                    Modifier
                        .background(sk.titleBg)
                        .border(1.dp, sk.winBorder)
                        .padding(1.dp)
                        .bevel(raised = true)
                        .padding(horizontal = if (small) 8.dp else 12.dp, vertical = if (small) 2.dp else 5.dp)
                ) {
                    Text(
                        tx(title),
                        fontFamily = sk.titleFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (small) 12.sp else 14.sp,
                        color = sk.ink,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 260.dp),
                    )
                }
                Column(frame) {
                    if (onBack != null || actions != null || subtitle != null) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (onBack != null) ToolIcon(AppIcons.Back, "Indietro", onBack)
                            if (subtitle != null) PixelCaption(subtitle, Modifier.weight(1f).padding(start = 4.dp))
                            else Spacer(Modifier.weight(1f))
                            actions?.invoke(this)
                        }
                    }
                    content()
                }
            },
        ) { measurables, c ->
            val tab = measurables[0].measure(c.copy(minWidth = 0, minHeight = 0))
            val maxH = if (c.hasBoundedHeight) (c.maxHeight - tab.height).coerceAtLeast(0) else Constraints.Infinity
            val minH = (c.minHeight - tab.height).coerceAtLeast(0).coerceAtMost(maxH)
            val body = measurables[1].measure(
                Constraints(minWidth = c.maxWidth, maxWidth = c.maxWidth, minHeight = minH, maxHeight = maxH)
            )
            layout(c.maxWidth, tab.height + body.height) {
                tab.place(0, 0)
                body.place(0, tab.height)
            }
        }
        return
    }

    Column(modifier.then(frame)) {
        val titleText = tx(title)
        when (sk.titleBar) {
            TitleBar.PLATINUM, TitleBar.STRIPES_BW -> {
                val bw = sk.titleBar == TitleBar.STRIPES_BW
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barH)
                        .background(sk.titleBg)
                        .then(
                            if (bw) Modifier.stripes(5.dp, 5.dp, sk.ink, null, 1.dp)
                            else Modifier.stripes(6.dp, 5.dp, sk.hi, sk.lo, 0.6.dp)
                        )
                ) {
                    Text(
                        titleText,
                        fontFamily = sk.titleFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (small) 12.sp else 14.sp,
                        color = sk.ink,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 240.dp)
                            .background(sk.titleBg)
                            .padding(horizontal = 8.dp),
                    )
                }
            }
            TitleBar.TAB, TitleBar.MATERIAL -> {}
            TitleBar.TAG -> {
                Row(
                    Modifier.fillMaxWidth().height(barH).background(sk.titleBg).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(if (small) 9.dp else 12.dp).background(sk.accent))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        titleText,
                        fontFamily = sk.titleFont,
                        fontSize = if (small) 12.sp else 14.sp,
                        color = sk.ink,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(1.dp).background(
                when (sk.id) { SkinId.PLATINUM, SkinId.BEOS, SkinId.ANDROID -> sk.lo; SkinId.PLAN9 -> sk.winBorder; SkinId.SYSTEM7 -> sk.ink }
            )
        )
        if (onBack != null || actions != null || subtitle != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (onBack != null) ToolIcon(AppIcons.Back, "Indietro", onBack)
                if (subtitle != null) PixelCaption(subtitle, Modifier.weight(1f).padding(start = 4.dp))
                else Spacer(Modifier.weight(1f))
                actions?.invoke(this)
            }
        }
        content()
    }
}

// ---------------------------------------------------------------- controlli

/**
 * Superficie di un pulsante. Alla pressione si incassa o si inverte secondo la skin
 * e, al tocco, resta premuta per un istante prima di eseguire l'azione,
 * come i pulsanti dei vecchi desktop.
 */
@Composable
fun BevelBox(
    label: String?,
    modifier: Modifier,
    on: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    shape: Shape = RectangleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        // riquadro tonale Material con l'effetto "ripple" di sistema
        val shp = if (shape == RectangleShape) RoundedCornerShape(6.dp) else shape
        val ink = when { !enabled -> sk.off; on -> sk.onInk; else -> sk.ink }
        Box(
            modifier
                .clip(shp)
                .background(if (on) sk.onBg else sk.ctlBg)
                .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
        ) {
            CompositionLocalProvider(LocalCellInk provides ink) {
                if (label != null) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (on || !enabled) ink else sk.ink2,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 5.dp),
                    )
                }
                Box(
                    Modifier.align(Alignment.Center).padding(top = if (label != null) 12.dp else 0.dp),
                    contentAlignment = Alignment.Center,
                    content = content,
                )
            }
        }
        return
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var flash by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val down = pressed || flash
    val sunk = down || on
    val shift = if (down && sk.bevel) 1.dp else 0.dp
    val bg = when { down -> sk.pressBg; on -> sk.onBg; else -> sk.ctlBg }
    val ink = when { !enabled -> sk.off; down -> sk.pressInk; on -> sk.onInk; else -> sk.ink }
    Box(
        modifier
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, if (enabled) sk.ctlBorder else sk.off, shape)
            .padding(1.dp)
            .bevel(raised = !sunk)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = src, indication = null, enabled = enabled,
                    onClick = {
                        if (!flash) scope.launch {
                            flash = true
                            delay(120)
                            flash = false
                            onClick()
                        }
                    },
                ) else Modifier
            )
    ) {
        CompositionLocalProvider(LocalCellInk provides ink) {
            if (label != null) {
                Text(
                    tx(label),
                    fontFamily = sk.smallFont,
                    fontSize = 11.sp,
                    color = if (on && !down && sk.id == SkinId.PLATINUM) sk.accent
                    else if (!down && !on && enabled) sk.ink2 else ink,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = shift, y = shift)
                        .padding(start = 6.dp, top = 3.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(x = shift, y = shift)
                    .padding(top = if (label != null) 12.dp else 0.dp),
                contentAlignment = Alignment.Center,
                content = content,
            )
        }
    }
}

@Composable
fun RowScope.BevelButton(
    label: String?,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    height: Dp = 64.dp,
    on: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    BevelBox(label, modifier.weight(weight).height(height), on, enabled, onClick, content = content)
}

@Composable
fun BevelValue(text: String, enabled: Boolean = true, size: Int = 18) {
    val sk = LocalSkin.current
    Text(
        tx(text),
        fontFamily = sk.titleFont,
        fontWeight = if (sk.id == SkinId.ANDROID) FontWeight.Medium else FontWeight.Bold,
        fontSize = size.sp,
        color = if (enabled) LocalCellInk.current else sk.off,
        maxLines = 1,
        softWrap = false,
    )
}

/** Pulsante di testo; [default] aggiunge l'anello spesso del pulsante predefinito. */
@Composable
fun PushButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    default: Boolean = false,
    enabled: Boolean = true,
) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        // azione principale: pulsante pieno; le altre: pulsante con contorno
        val shape = RoundedCornerShape(6.dp)
        val pad = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        if (default) Button(onClick, modifier, enabled = enabled, shape = shape, contentPadding = pad) {
            Text(text, maxLines = 1, softWrap = false)
        } else OutlinedButton(onClick, modifier, enabled = enabled, shape = shape, contentPadding = pad) {
            Text(text, maxLines = 1, softWrap = false)
        }
        return
    }
    val r = sk.pushRadius
    val ringColor = if (!enabled) sk.off else if (sk.id == SkinId.PLAN9) sk.pressBg else sk.ink
    val ring = if (default) Modifier
        .border(3.dp, ringColor, RoundedCornerShape(if (r > 0.dp) r + 4.dp else 0.dp))
        .padding(4.dp) else Modifier
    Box(modifier.then(ring)) {
        BevelBox(
            null,
            Modifier.heightIn(min = 40.dp).widthIn(min = 56.dp),
            enabled = enabled,
            onClick = onClick,
            shape = RoundedCornerShape(r),
        ) {
            Text(
                tx(text),
                fontFamily = sk.titleFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = LocalCellInk.current,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            )
        }
    }
}

/** Casella di controllo. */
@Composable
fun RetroCheckbox(checked: Boolean, label: String, onToggle: () -> Unit, enabled: Boolean = true) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) sk.ink else sk.off, modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(sk.field)
                .border(1.dp, if (!enabled) sk.off else if (sk.id == SkinId.PLAN9) sk.lo else sk.ink)
                .padding(1.dp)
                .bevel(raised = false)
        ) {
            if (checked) {
                if (sk.id == SkinId.PLAN9) {
                    Box(Modifier.matchParentSize().padding(3.dp).background(sk.accent))
                } else {
                    Canvas(Modifier.matchParentSize().padding(3.dp)) {
                        val w = 2.dp.toPx()
                        drawLine(sk.ink, Offset(0f, 0f), Offset(size.width, size.height), w)
                        drawLine(sk.ink, Offset(size.width, 0f), Offset(0f, size.height), w)
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(tx(label), fontFamily = sk.bodyFont, fontSize = 15.sp, color = if (enabled) sk.ink else sk.off)
    }
}

/** Riquadro di gruppo con il titolo sul bordo. */
@Composable
fun GroupBox(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        // intestazione di sezione + scheda arrotondata
        Column(modifier.fillMaxWidth().padding(top = 12.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleSmall, color = sk.accent,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                content = content,
            )
        }
        return
    }
    Box(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .etched()
                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            content = content,
        )
        Text(
            tx(title),
            fontFamily = if (sk.id == SkinId.SYSTEM7) sk.titleFont else sk.smallFont,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = sk.ink,
            modifier = Modifier
                .offset(x = 10.dp, y = (-9).dp)
                .background(sk.bg)
                .padding(horizontal = 4.dp),
        )
    }
}

/** Spia di stato. */
@Composable
fun StatusLight(ok: Boolean) {
    val sk = LocalSkin.current
    when (sk.id) {
        SkinId.PLATINUM, SkinId.BEOS -> {
            val c = if (ok) sk.ok else sk.ko
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), c, c)))
                    .border(1.dp, sk.ink, CircleShape)
            )
        }
        SkinId.SYSTEM7 -> Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (ok) sk.ink else sk.field)
                .border(1.dp, sk.ink, CircleShape)
        )
        SkinId.PLAN9 -> Box(Modifier.size(12.dp).background(if (ok) sk.ok else sk.ko))
        SkinId.ANDROID -> Box(Modifier.size(10.dp).clip(CircleShape).background(if (ok) sk.ok else sk.ko))
    }
}

/** Barra di avanzamento. */
@Composable
fun RetroProgress(fraction: Float, modifier: Modifier = Modifier) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth())
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .border(1.dp, if (sk.id == SkinId.PLAN9) sk.lo else sk.ink)
            .padding(1.dp)
            .background(sk.field)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Brush.verticalGradient(sk.progress))
        )
    }
}

/** Campo di testo. */
@Composable
fun RetroTextField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    maxHeight: Dp = 160.dp,
    enabled: Boolean = true,
) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        // campo compatto: 40 dp di altezza minima, angoli piccoli, bordo sottile
        val single = maxHeight <= 80.dp
        val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = if (enabled) sk.ink else sk.off)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = single,
            textStyle = style,
            cursorBrush = SolidColor(sk.accent),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = if (single) 40.dp else maxOf(maxHeight, 40.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
            decorationBox = { inner ->
                Box(
                    Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    contentAlignment = if (single) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, style = style.copy(color = sk.off), maxLines = 1)
                    inner()
                }
            },
        )
        return
    }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        textStyle = TextStyle(fontFamily = sk.bodyFont, fontSize = 16.sp, color = sk.ink, lineHeight = 22.sp),
        cursorBrush = SolidColor(sk.ink),
        modifier = modifier
            .fillMaxWidth()
            .well()
            .heightIn(min = minHeight, max = maxHeight)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, fontFamily = sk.bodyFont, fontSize = 16.sp, color = sk.off)
                inner()
            }
        },
    )
}

/** Didascalia nel carattere piccolo della skin. */
@Composable
fun PixelCaption(text: String, modifier: Modifier = Modifier) {
    val sk = LocalSkin.current
    Text(text, fontFamily = sk.smallFont, fontSize = 12.sp, lineHeight = 18.sp, color = sk.ink2, modifier = modifier)
}

/** Avviso modale con l'icona di attenzione. */
@Composable
fun RetroAlert(
    message: String,
    detail: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String,
    onDismiss: () -> Unit,
) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        AlertDialog(
            shape = RoundedCornerShape(12.dp),
            onDismissRequest = onDismiss,
            title = { Text(message) },
            text = { Text(detail) },
            confirmButton = { TextButton(onConfirm) { Text(confirm) } },
            dismissButton = { TextButton(onDismiss) { Text(dismiss) } },
        )
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalSkin provides sk, LocalCellInk provides sk.ink) {
            val frame = when (sk.id) {
                SkinId.PLATINUM, SkinId.BEOS -> Modifier
                    .border(1.dp, sk.ink).padding(1.dp).bevel(true).padding(3.dp).border(1.dp, sk.lo)
                SkinId.SYSTEM7 -> Modifier
                    .border(1.dp, sk.ink).padding(3.dp).border(3.dp, sk.ink)
                SkinId.PLAN9 -> Modifier.border(4.dp, sk.winBorder)
                SkinId.ANDROID -> Modifier
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(sk.bg)
                    .then(frame)
                    .padding(16.dp)
            ) {
                Row {
                    Canvas(Modifier.size(40.dp)) {
                        val p = Path().apply {
                            moveTo(size.width / 2, 2f); lineTo(size.width - 2f, size.height - 2f)
                            lineTo(2f, size.height - 2f); close()
                        }
                        drawPath(p, sk.alertFill)
                        drawPath(p, sk.ink, style = Stroke(2.dp.toPx()))
                        val cx = size.width / 2
                        drawRect(sk.ink, Offset(cx - 2.dp.toPx(), size.height * 0.35f), Size(4.dp.toPx(), size.height * 0.32f))
                        drawRect(sk.ink, Offset(cx - 2.dp.toPx(), size.height * 0.74f), Size(4.dp.toPx(), 4.dp.toPx()))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(message, fontFamily = sk.titleFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = sk.ink)
                        Spacer(Modifier.height(6.dp))
                        Text(detail, fontFamily = sk.bodyFont, fontSize = 14.sp, color = sk.ink)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    PushButton(dismiss, onDismiss)
                    PushButton(confirm, onConfirm, default = true)
                }
            }
        }
    }
}

/** Testo centrato di servizio (caricamento, errori). */
@Composable
fun CenterNote(text: String) {
    val sk = LocalSkin.current
    Text(text, fontFamily = sk.smallFont, fontSize = 15.sp, color = sk.ink, textAlign = TextAlign.Center)
}

/** Cornice delle finestre di dialogo, secondo la skin. */
@Composable
fun Modifier.dialogFrame(): Modifier {
    val sk = LocalSkin.current
    val frame = when (sk.id) {
        SkinId.PLATINUM, SkinId.BEOS -> Modifier
            .border(1.dp, sk.ink).padding(1.dp).bevel(true).padding(3.dp).border(1.dp, sk.lo)
        SkinId.SYSTEM7 -> Modifier
            .border(1.dp, sk.ink).padding(3.dp).border(3.dp, sk.ink)
        SkinId.PLAN9 -> Modifier.border(4.dp, sk.winBorder)
        SkinId.ANDROID -> return this
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp)
    }
    return this.background(sk.bg).then(frame).padding(16.dp)
}

/** Dialogo con un campo di testo (es. il nome di un nuovo sopralluogo). */
@Composable
fun RetroPrompt(
    title: String,
    message: String,
    placeholder: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initial: String = "",
    multiline: Boolean = false,
    allowEmpty: Boolean = false,
) {
    val sk = LocalSkin.current
    var text by remember { mutableStateOf(initial) }
    if (sk.id == SkinId.ANDROID) {
        AlertDialog(
            shape = RoundedCornerShape(12.dp),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    Text(message)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = if (multiline) it else it.replace("\n", " ") },
                        singleLine = !multiline,
                        minLines = if (multiline) 3 else 1,
                        placeholder = { Text(placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton({ onConfirm(text.trim()) }, enabled = allowEmpty || text.isNotBlank()) { Text(confirm) }
            },
            dismissButton = { TextButton(onDismiss) { Text("Annulla") } },
        )
        return
    }
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalSkin provides sk, LocalCellInk provides sk.ink) {
            Column(Modifier.fillMaxWidth().dialogFrame()) {
                Text(tx(title), fontFamily = sk.titleFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = sk.ink)
                Spacer(Modifier.height(6.dp))
                Text(message, fontFamily = sk.bodyFont, fontSize = 14.sp, color = sk.ink)
                Spacer(Modifier.height(12.dp))
                if (multiline) RetroTextField(text, { text = it }, placeholder, minHeight = 96.dp, maxHeight = 200.dp)
                else RetroTextField(text, { text = it.replace("\n", " ") }, placeholder, minHeight = 40.dp, maxHeight = 80.dp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    PushButton("Annulla", onDismiss)
                    PushButton(confirm, { onConfirm(text.trim()) }, default = true, enabled = allowEmpty || text.isNotBlank())
                }
            }
        }
    }
}

/** Finestrella non chiudibile che mostra l'avanzamento di un'operazione lunga. */
@Composable
fun BusyDialog(message: String, fraction: Float) {
    val sk = LocalSkin.current
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        CompositionLocalProvider(LocalSkin provides sk, LocalCellInk provides sk.ink) {
            Column(Modifier.fillMaxWidth().dialogFrame()) {
                Text(message, fontFamily = sk.bodyFont, fontSize = 15.sp, color = sk.ink)
                Spacer(Modifier.height(12.dp))
                RetroProgress(fraction)
            }
        }
    }
}

/** Miniatura quadrata di una foto. */
@Composable
fun PhotoThumb(uri: Uri, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(uri, android.util.Size(240, 240), null) }.getOrNull()
        }
    }
    Box(modifier.background(LocalSkin.current.canvas)) {
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

/** Impalcatura comune: scrivania, barra dei menu e contenuto. */
@Composable
fun DeskScreen(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .desktop()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
    ) {
        MenuBar("Pastashoot")
        content()
    }
}

/** Pulsanti in fondo a un dialogo: testuali in Android, pulsanti della skin negli altri aspetti. */
@Composable
fun DialogButtons(dismiss: String, onDismiss: () -> Unit, confirm: String, onConfirm: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
        if (isAndroidSkin) {
            TextButton(onDismiss) { Text(dismiss) }
            TextButton(onConfirm, enabled = enabled) { Text(confirm) }
        } else {
            PushButton(dismiss, onDismiss)
            PushButton(confirm, onConfirm, default = true, enabled = enabled)
        }
    }
}

/** Cornice dei menu a tendina: quella di sistema in Android, squadrata negli altri aspetti. */
@Composable
fun Modifier.menuFrame(): Modifier =
    if (isAndroidSkin) this else this.background(P.Bg).border(1.dp, P.Ink)

/**
 * Pulsante a icona per le barre: icona Material in Android, pulsante in rilievo negli altri aspetti.
 * [on] indica uno strumento attivo.
 */
@Composable
fun ToolIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    on: Boolean = false,
    tint: Color? = null,
) {
    val sk = LocalSkin.current
    if (sk.id == SkinId.ANDROID) {
        if (on) FilledTonalIconButton(onClick, enabled = enabled, shape = RoundedCornerShape(6.dp)) {
            Icon(icon, description, tint = tint ?: LocalContentColor.current)
        } else IconButton(onClick, enabled = enabled) {
            Icon(icon, description, tint = if (!enabled) sk.off else tint ?: sk.ink2)
        }
        return
    }
    BevelBox(null, Modifier.size(40.dp), on = on, enabled = enabled, onClick = onClick) {
        Icon(icon, description, tint = if (!enabled) sk.off else LocalCellInk.current, modifier = Modifier.size(20.dp))
    }
}

/** Azione testuale nella barra superiore: pulsante pieno se principale, testuale altrimenti. */
@Composable
fun BarAction(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    if (isAndroidSkin) {
        val pad = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        if (primary) Button(
            onClick, enabled = enabled, shape = RoundedCornerShape(6.dp), contentPadding = pad,
            modifier = Modifier.padding(horizontal = 4.dp).height(36.dp),
        ) { Text(text, maxLines = 1) }
        else TextButton(onClick, enabled = enabled, contentPadding = pad) { Text(text, maxLines = 1) }
    } else PushButton(text, onClick, default = primary, enabled = enabled)
}

/** Voce di un menu "altre azioni". */
class MenuItemSpec(val text: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** Pulsante "⋮" che apre un menu con le azioni meno frequenti. */
@Composable
fun OverflowMenu(items: List<MenuItemSpec>) {
    var open by remember { mutableStateOf(false) }
    Box {
        ToolIcon(AppIcons.More, "Altre azioni", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.menuFrame()) {
            for (it in items) {
                DropdownMenuItem(
                    text = { Text(tx(it.text), fontFamily = Sys) },
                    enabled = it.enabled,
                    onClick = { open = false; it.onClick() },
                )
            }
        }
    }
}

/** Margini delle finestre: nessuno in Android (contenuto a tutto schermo), quelli indicati altrove. */
@Composable
fun Modifier.windowPad(start: Dp = 6.dp, end: Dp = 9.dp, top: Dp = 10.dp, bottom: Dp = 12.dp): Modifier =
    if (isAndroidSkin) this else this.padding(start = start, end = end, top = top, bottom = bottom)

/** Spaziatura interna dei contenuti delle finestre: più stretta in Android. */
val contentPad: Dp @Composable get() = if (isAndroidSkin) 12.dp else 12.dp

/** Area delle griglie di foto: a tutta larghezza in Android, incassata negli altri aspetti. */
@Composable
fun Modifier.gridArea(): Modifier =
    if (isAndroidSkin) this.padding(horizontal = 2.dp) else this.padding(horizontal = 8.dp).well()

/** Spazio tra le miniature. */
val gridGap: Dp @Composable get() = if (isAndroidSkin) 2.dp else 4.dp

/** Riga di elenco toccabile: compatta e separata da una linea in Android, pulsante negli altri aspetti. */
@Composable
fun ListRow(title: String, subtitle: String?, onClick: () -> Unit) {
    if (isAndroidSkin) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 52.dp)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = P.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = P.Ink2, maxLines = 1)
            }
            Icon(AppIcons.Next, null, tint = P.Ink2)
        }
        return
    }
    BevelBox(null, Modifier.fillMaxWidth().heightIn(min = 54.dp), onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(title, fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = LocalCellInk.current, maxLines = 1)
            if (subtitle != null) Text(subtitle, fontFamily = Pixel, fontSize = 12.sp, color = LocalCellInk.current)
        }
    }
}

/** Separatore sottile tra le righe di un elenco (solo Android). */
@Composable
fun ListDivider() {
    if (isAndroidSkin) Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    else Spacer(Modifier.height(6.dp))
}

/** Titolo in evidenza dentro un riquadro (es. il nome della sessione in corso). */
@Composable
fun BigText(text: String) {
    if (isAndroidSkin) Text(text, style = MaterialTheme.typography.titleMedium, color = P.Ink, maxLines = 2)
    else Text(text, fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = P.Ink)
}
