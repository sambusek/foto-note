package com.sambusek.fotonote

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Palette "Platinum": i grigi del Mac OS classico,
 * con il lavanda dell'evidenziazione Mac e un azzurro-viola come accento.
 */
object P {
    val Bg = Color(0xFFDDDDDD)        // platinum
    val BgLight = Color(0xFFEEEEEE)
    val Line = Color(0xFF8E8E8E)
    val Ink = Color(0xFF1A1A1A)
    val Ink2 = Color(0xFF666666)
    val Off = Color(0xFFAAAAAA)
    val Accent = Color(0xFF6B6BE0)    // azzurro-viola
    val AccentSoft = Color(0xFFCCCCFF) // lavanda evidenziazione Mac
    val Canvas = Color(0xFFA4A4A4)
}

private val square = RoundedCornerShape(0.dp)

@Composable
fun PlatinumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = P.Accent,
            onPrimary = Color.White,
            primaryContainer = P.AccentSoft,
            onPrimaryContainer = P.Ink,
            secondaryContainer = P.AccentSoft,
            onSecondaryContainer = P.Ink,
            background = P.Bg,
            onBackground = P.Ink,
            surface = P.Bg,
            onSurface = P.Ink,
            surfaceVariant = P.BgLight,
            onSurfaceVariant = P.Ink2,
            surfaceContainer = P.BgLight,
            surfaceContainerHigh = P.BgLight,
            surfaceContainerHighest = P.BgLight,
            outline = P.Line,
            outlineVariant = P.Line,
        ),
        shapes = Shapes(square, square, square, square, square),
        content = content,
    )
}

/** Barre di sistema chiare con icone scure, su sfondo platinum. */
fun ComponentActivity.platinumEdgeToEdge() {
    val t = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(SystemBarStyle.light(t, t), SystemBarStyle.light(t, t))
}

/** Linee della griglia: ogni cella disegna il bordo destro e inferiore. */
fun Modifier.cellLines(): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawLine(P.Line, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
    drawLine(P.Line, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

/** Linee di chiusura in alto e a sinistra del contenitore della griglia. */
fun Modifier.gridFrame(top: Boolean = true, left: Boolean = false): Modifier = drawBehind {
    val w = 1.dp.toPx()
    if (top) drawLine(P.Line, Offset(0f, w / 2), Offset(size.width, w / 2), w)
    if (left) drawLine(P.Line, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
}

val LabelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp)
val ValueStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp)
val ButtonStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)

/**
 * Cella della griglia: etichetta piccola in alto a sinistra, valore grande al centro.
 * [on] = stato attivo (etichetta in accento, fondo lavanda); [accent] = cella piena in accento.
 */
@Composable
fun RowScope.Cell(
    label: String? = null,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    height: Dp = 84.dp,
    on: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    CellBox(label, modifier.weight(weight).height(height), on, accent, enabled, onClick, content)
}

@Composable
fun CellBox(
    label: String?,
    modifier: Modifier,
    on: Boolean = false,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val bg = when {
        accent && enabled -> P.Accent
        on -> P.AccentSoft
        else -> Color.Transparent
    }
    Box(
        modifier
            .background(bg)
            .cellLines()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (label != null) {
            Text(
                label.uppercase(),
                style = LabelStyle,
                color = when {
                    accent && enabled -> Color.White
                    !enabled -> P.Off
                    on -> P.Accent
                    else -> P.Ink
                },
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp),
            )
        }
        Box(Modifier.matchParentSize().padding(top = if (label != null) 18.dp else 0.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** Valore grande dentro una cella. */
@Composable
fun CellValue(text: String, enabled: Boolean = true, color: Color? = null, size: Int = 30) {
    Text(
        text,
        style = ValueStyle.copy(fontSize = size.sp),
        color = color ?: if (enabled) P.Ink else P.Off,
        maxLines = 1,
        softWrap = false,
    )
}

/** Testo di un pulsante-cella (senza etichetta). */
@Composable
fun CellButtonText(text: String, enabled: Boolean = true, accent: Boolean = false) {
    Text(
        text.uppercase(),
        style = ButtonStyle,
        color = when {
            accent && enabled -> Color.White
            enabled -> P.Ink
            else -> P.Off
        },
        maxLines = 1,
        softWrap = false,
    )
}

/** Riga della griglia a tutta larghezza. */
@Composable
fun GridRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth(), content = content)
}

/** Blocco di testo descrittivo dentro la griglia. */
@Composable
fun ColumnScope.Caption(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = P.Ink2,
        modifier = Modifier.fillMaxWidth().cellLines().padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
fun ThinProgress(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(4.dp).background(P.BgLight)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(P.Accent))
    }
}
