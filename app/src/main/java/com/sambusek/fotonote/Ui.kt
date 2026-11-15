package com.sambusek.fotonote

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Palette in omaggio all'aspetto "Platinum" di fine anni '90:
 * grigi, rilievi a 1 pixel, scrivania azzurro-viola e selezione lavanda.
 */
object P {
    val Bg = Color(0xFFDDDDDD)        // platinum
    val BgLight = Color(0xFFEEEEEE)
    val White = Color(0xFFFFFFFF)
    val Shade = Color(0xFF999999)
    val Dark = Color(0xFF666666)
    val Ink = Color(0xFF111111)
    val Ink2 = Color(0xFF444444)
    val Off = Color(0xFFA0A0A0)
    val Desk = Color(0xFF6363A5)      // scrivania
    val DeskTex = Color(0xFF6D6DAE)
    val Accent = Color(0xFF6666CC)
    val AccentSoft = Color(0xFFCCCCFF) // lavanda della selezione
    val Canvas = Color(0xFFA4A4A4)
    val Ok = Color(0xFF2E9E3E)
    val Ko = Color(0xFFCC3333)
}

/** Font pixel (Pixelify Sans, licenza OFL) per etichette, titoli piccoli e didascalie. */
val Pixel = FontFamily(
    Font(R.font.pixel_regular, FontWeight.Normal),
    Font(R.font.pixel_bold, FontWeight.Bold),
)

/** Testo "di sistema" per pulsanti, valori e titoli delle finestre. */
val Sys = FontFamily.SansSerif

private val square = RoundedCornerShape(0.dp)

@Composable
fun PlatinumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = P.Accent, onPrimary = Color.White,
            primaryContainer = P.AccentSoft, onPrimaryContainer = P.Ink,
            background = P.Bg, onBackground = P.Ink,
            surface = P.Bg, onSurface = P.Ink,
            surfaceVariant = P.BgLight, onSurfaceVariant = P.Ink2,
            surfaceContainer = P.Bg, surfaceContainerHigh = P.Bg, surfaceContainerHighest = P.Bg,
            outline = P.Ink, outlineVariant = P.Shade,
        ),
        shapes = Shapes(square, square, square, square, square),
        content = content,
    )
}

/** Barra di stato chiara (sopra la barra dei menu), barra di navigazione sulla scrivania. */
fun ComponentActivity.platinumEdgeToEdge() {
    val t = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(SystemBarStyle.light(t, t), SystemBarStyle.dark(t))
}

// ---------------------------------------------------------------- rilievi

private fun DrawScope.hLine(c: Color, y: Float, x0: Float, x1: Float, w: Float) =
    drawRect(c, Offset(x0, y), Size(x1 - x0, w))

private fun DrawScope.vLine(c: Color, x: Float, y0: Float, y1: Float, w: Float) =
    drawRect(c, Offset(x, y0), Size(w, y1 - y0))

/** Rilievo a 1 pixel: chiaro in alto/sinistra, scuro in basso/destra (o viceversa se incassato). */
fun Modifier.bevel(raised: Boolean = true): Modifier = drawBehind {
    val w = 1.dp.toPx()
    val hi = if (raised) P.White else P.Shade
    val lo = if (raised) P.Shade else P.White
    hLine(hi, 0f, 0f, size.width, w)
    vLine(hi, 0f, 0f, size.height, w)
    hLine(lo, size.height - w, 0f, size.width, w)
    vLine(lo, size.width - w, 0f, size.height, w)
}

/** Bordo inciso (riquadri di gruppo). */
fun Modifier.etched(): Modifier = drawBehind {
    val w = 1.dp.toPx()
    val sw = size.width
    val sh = size.height
    hLine(P.Shade, 0f, 0f, sw - w, w); vLine(P.Shade, 0f, 0f, sh - w, w)
    hLine(P.Shade, sh - 2 * w, 0f, sw - w, w); vLine(P.Shade, sw - 2 * w, 0f, sh - w, w)
    hLine(P.White, w, w, sw - 2 * w, w); vLine(P.White, w, w, sh - 2 * w, w)
    hLine(P.White, sh - w, 0f, sw, w); vLine(P.White, sw - w, 0f, sh, w)
}

/** Area incassata con cornice nera (campi di testo, liste, area foto). */
fun Modifier.well(color: Color = P.White): Modifier =
    this.bevel(raised = false).padding(1.dp).border(1.dp, P.Ink).padding(1.dp).background(color)

/** Scrivania azzurro-viola con una trama discreta a puntini. */
fun Modifier.desktop(): Modifier = this.background(P.Desk).drawWithCache {
    val step = 10.dp.toPx()
    val dot = 1.5.dp.toPx()
    val path = Path()
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else step / 2
        while (x < size.width) {
            path.addRect(Rect(x, y, x + dot, y + dot))
            x += step
        }
        y += step / 2; row++
    }
    onDrawBehind { drawPath(path, P.DeskTex) }
}

// ---------------------------------------------------------------- barra dei menu

@Composable
fun MenuBar(title: String) {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("HH:mm", Locale.ITALY)
        while (true) { time = f.format(Date()); delay(15_000) }
    }
    Column(Modifier.fillMaxWidth().background(P.Bg)) {
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
                colorFilter = ColorFilter.tint(P.Accent),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(title, fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = P.Ink)
            Spacer(Modifier.weight(1f))
            Text(time, fontFamily = Pixel, fontSize = 14.sp, color = P.Ink)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(P.Ink))
    }
}

// ---------------------------------------------------------------- finestre

/** Righe orizzontali della barra del titolo. */
private fun Modifier.pinstripes(top: Dp, bottom: Dp): Modifier = drawBehind {
    val w = 1.dp.toPx()
    val gap = 0.6.dp.toPx()
    val x0 = 3.dp.toPx()
    val x1 = size.width - 3.dp.toPx()
    var y = top.toPx()
    val end = size.height - bottom.toPx()
    while (y + 2 * w <= end) {
        hLine(P.White, y, x0, x1, w)
        hLine(P.Shade, y + w, x0, x1, w)
        y += 2 * w + gap
    }
}

@Composable
private fun TitleBox(box: Dp, onClick: (() -> Unit)?, content: @Composable BoxScope.() -> Unit = {}) {
    val tap = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(Modifier.size(box + 20.dp).then(tap), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(box)
                .background(P.Bg)
                .border(1.dp, P.Ink)
                .padding(1.dp)
                .bevel(raised = true),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/**
 * Finestra: barra del titolo a righe, casella di chiusura a sinistra,
 * casella decorativa a destra, ombra netta.
 */
@Composable
fun RetroWindow(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
    small: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val barH = if (small) 20.dp else 28.dp
    val boxS = if (small) 10.dp else 14.dp
    Column(
        modifier
            .drawBehind {
                val s = 3.dp.toPx()
                drawRect(Color.Black.copy(alpha = 0.45f), Offset(s, s), size)
            }
            .background(P.Bg)
            .border(1.dp, P.Ink)
            .padding(1.dp)
            .bevel(raised = true)
    ) {
        Box(Modifier.fillMaxWidth().height(barH).pinstripes(6.dp, 5.dp)) {
            Row(Modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(P.Bg)) { TitleBox(boxS, onClose) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.background(P.Bg)) {
                    TitleBox(boxS, null) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 2.dp).height(1.dp).background(P.Ink))
                    }
                }
            }
            Text(
                title,
                fontFamily = if (small) Pixel else Sys,
                fontWeight = FontWeight.Bold,
                fontSize = if (small) 12.sp else 14.sp,
                color = P.Ink,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 240.dp)
                    .background(P.Bg)
                    .padding(horizontal = 8.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(P.Shade))
        content()
    }
}

// ---------------------------------------------------------------- controlli

/** Pulsante arrotondato; [default] aggiunge l'anello spesso del pulsante predefinito. */
@Composable
fun PushButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    default: Boolean = false,
    enabled: Boolean = true,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(5.dp)
    val ring = if (default) Modifier
        .border(3.dp, if (enabled) P.Ink else P.Off, RoundedCornerShape(9.dp))
        .padding(4.dp) else Modifier
    Box(modifier.then(ring)) {
        Box(
            Modifier
                .heightIn(min = 34.dp)
                .clip(shape)
                .background(
                    if (pressed) Brush.verticalGradient(listOf(P.Dark, P.Ink2))
                    else Brush.verticalGradient(listOf(P.White, P.BgLight, Color(0xFFCCCCCC)))
                )
                .border(1.dp, if (enabled) P.Ink else P.Off, shape)
                .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                fontFamily = Sys,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = when { !enabled -> P.Off; pressed -> P.White; else -> P.Ink },
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Pulsante squadrato in rilievo (strumenti, scelte): etichetta pixel in alto,
 * valore al centro. Attivo = incassato su fondo lavanda.
 */
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
    BevelBox(label, modifier.weight(weight).height(height), on, enabled, onClick, content)
}

@Composable
fun BevelBox(
    label: String?,
    modifier: Modifier,
    on: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val down = pressed || on
    Box(
        modifier
            .border(1.dp, if (enabled) P.Ink else P.Off)
            .padding(1.dp)
            .background(if (pressed) P.Shade else if (on) P.AccentSoft else P.Bg)
            .bevel(raised = !down)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = src, indication = null, enabled = enabled, onClick = onClick
                ) else Modifier
            )
    ) {
        if (label != null) {
            Text(
                label,
                fontFamily = Pixel,
                fontSize = 11.sp,
                color = if (!enabled) P.Off else if (on) P.Accent else P.Ink2,
                maxLines = 1,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 6.dp, top = 3.dp),
            )
        }
        Box(
            Modifier.matchParentSize().padding(top = if (label != null) 12.dp else 0.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
fun BevelValue(text: String, enabled: Boolean = true, size: Int = 18) {
    Text(
        text,
        fontFamily = Sys,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        color = if (enabled) P.Ink else P.Off,
        maxLines = 1,
        softWrap = false,
    )
}

/** Casella di controllo con la "X". */
@Composable
fun RetroCheckbox(checked: Boolean, label: String, onToggle: () -> Unit, enabled: Boolean = true) {
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
                .background(P.White)
                .border(1.dp, if (enabled) P.Ink else P.Off)
                .padding(1.dp)
                .bevel(raised = false)
        ) {
            if (checked) {
                Canvas(Modifier.matchParentSize().padding(3.dp)) {
                    val w = 2.dp.toPx()
                    drawLine(P.Ink, Offset(0f, 0f), Offset(size.width, size.height), w)
                    drawLine(P.Ink, Offset(size.width, 0f), Offset(0f, size.height), w)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(label, fontFamily = Sys, fontSize = 15.sp, color = if (enabled) P.Ink else P.Off)
    }
}

/** Riquadro di gruppo con titolo sul bordo inciso. */
@Composable
fun GroupBox(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().padding(top = 10.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .etched()
                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            content = content,
        )
        Text(
            title,
            fontFamily = Pixel,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = P.Ink,
            modifier = Modifier
                .offset(x = 10.dp, y = (-9).dp)
                .background(P.Bg)
                .padding(horizontal = 4.dp),
        )
    }
}

/** Spia luminosa da hardware anni '90. */
@Composable
fun StatusLight(ok: Boolean) {
    val c = if (ok) P.Ok else P.Ko
    Box(
        Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), c, c)))
            .border(1.dp, P.Ink, CircleShape)
    )
}

/** Barra di avanzamento a gradiente lavanda. */
@Composable
fun RetroProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .border(1.dp, P.Ink)
            .padding(1.dp)
            .background(P.BgLight)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(P.AccentSoft, P.Accent, Color(0xFF9999FF))))
        )
    }
}

/** Campo di testo incassato bianco. */
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
    BasicTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        textStyle = TextStyle(fontFamily = Sys, fontSize = 16.sp, color = P.Ink, lineHeight = 22.sp),
        cursorBrush = SolidColor(P.Ink),
        modifier = modifier
            .fillMaxWidth()
            .well()
            .heightIn(min = minHeight, max = maxHeight)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, fontFamily = Sys, fontSize = 16.sp, color = P.Off)
                inner()
            }
        },
    )
}

/** Didascalia in font pixel. */
@Composable
fun PixelCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, fontFamily = Pixel, fontSize = 13.sp, lineHeight = 18.sp, color = P.Ink2, modifier = modifier)
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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(P.Bg)
                .border(1.dp, P.Ink)
                .padding(1.dp)
                .bevel(raised = true)
                .padding(3.dp)
                .border(1.dp, P.Shade)
                .padding(16.dp)
        ) {
            Row {
                Canvas(Modifier.size(40.dp)) {
                    val p = Path().apply {
                        moveTo(size.width / 2, 2f); lineTo(size.width - 2f, size.height - 2f)
                        lineTo(2f, size.height - 2f); close()
                    }
                    drawPath(p, Color(0xFFFFCC00))
                    drawPath(p, P.Ink, style = Stroke(2.dp.toPx()))
                    val cx = size.width / 2
                    drawRect(P.Ink, Offset(cx - 2.dp.toPx(), size.height * 0.35f), Size(4.dp.toPx(), size.height * 0.32f))
                    drawRect(P.Ink, Offset(cx - 2.dp.toPx(), size.height * 0.74f), Size(4.dp.toPx(), 4.dp.toPx()))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(message, fontFamily = Sys, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = P.Ink)
                    Spacer(Modifier.height(6.dp))
                    Text(detail, fontFamily = Sys, fontSize = 14.sp, color = P.Ink)
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

/** Testo centrato di servizio (caricamento, errori). */
@Composable
fun CenterNote(text: String) {
    Text(text, fontFamily = Pixel, fontSize = 15.sp, color = P.Ink, textAlign = TextAlign.Center)
}
