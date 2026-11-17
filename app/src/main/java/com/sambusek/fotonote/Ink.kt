package com.sambusek.fotonote

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Un punto del tratto, in pixel dell'immagine, con la pressione del pennino (0..1). */
data class Pt(val x: Float, val y: Float, val p: Float)

/** Un tratto: colore ARGB, spessore massimo in pixel dell'immagine, punti. */
class Stroke(val color: Int, val width: Float, val points: List<Pt>)

/** Spessore effettivo in base alla pressione. */
fun pressureWidth(width: Float, p: Float): Float = width * (0.35f + 0.65f * p.coerceIn(0f, 1f))

/** Spazio bianco aggiunto attorno alla foto, in pixel della foto (sinistra, sopra, destra, sotto). */
data class Pad(val l: Float = 0f, val t: Float = 0f, val r: Float = 0f, val b: Float = 0f) {
    val isZero: Boolean get() = l < 0.5f && t < 0.5f && r < 0.5f && b < 0.5f
}

class InkState {
    /** Tela: spazio bianco attorno alla foto. */
    var pad by mutableStateOf(Pad())
        private set

    fun setPad(p: Pad) {
        if (p != pad) { pad = p; dirty = true }
    }


    var strokes by mutableStateOf<List<Stroke>>(emptyList())
        private set
    val current = mutableStateListOf<Pt>()

    var color by mutableIntStateOf(0xFFE53935.toInt())
    var widthFactor by mutableFloatStateOf(0.004f)
    var eraser by mutableStateOf(false)
    var dirty by mutableStateOf(false)

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<List<Stroke>>()
    private val redoStack = ArrayDeque<List<Stroke>>()
    private var eraseStart: List<Stroke>? = null

    fun restore(saved: List<Stroke>, savedPad: Pad = Pad()) {
        strokes = saved
        pad = savedPad
        undoStack.clear(); redoStack.clear(); refresh()
    }

    private fun commit(newList: List<Stroke>, previous: List<Stroke> = strokes) {
        undoStack.addLast(previous)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        strokes = newList
        dirty = true
        refresh()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(strokes); strokes = prev; dirty = true; refresh()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(strokes); strokes = next; dirty = true; refresh()
    }

    private fun refresh() {
        canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty()
    }

    fun clearAll() {
        if (strokes.isNotEmpty()) commit(emptyList())
    }

    // --- disegno ---
    fun addPoint(pt: Pt) {
        current.add(pt)
    }

    fun endStroke(imageMaxSide: Float) {
        if (current.isNotEmpty()) {
            commit(strokes + Stroke(color, widthFactor * imageMaxSide, current.toList()))
        }
        current.clear()
    }

    fun cancelStroke() = current.clear()

    // --- gomma: cancella i tratti toccati ---
    fun beginErase() {
        eraseStart = strokes
    }

    fun eraseAt(x: Float, y: Float, radius: Float) {
        val keep = strokes.filterNot { s ->
            val r = radius + s.width / 2
            s.points.any { hypot(it.x - x, it.y - y) < r }
        }
        if (keep.size != strokes.size) strokes = keep
    }

    fun endErase() {
        val start = eraseStart ?: return
        eraseStart = null
        if (start.size != strokes.size) commit(strokes, start)
    }
}

private enum class Mode { NONE, DRAW, ERASE, TRANSFORM, RESIZE }

private class Gesture {
    var mode = Mode.NONE
    var prevCount = -1
    var prevCx = 0f
    var prevCy = 0f
    var prevSpan = 0f
    var swipeOk = false
    // bordi della tela trascinati (modalità tela)
    var dragL = false
    var dragT = false
    var dragR = false
    var dragB = false
    var lastX = 0f
    var lastY = 0f
    var swipeX = 0f
    var swipeY = 0f
}

/**
 * Area di disegno: mostra la foto e i tratti.
 * Il pennino disegna; con "solo pennino" attivo le dita spostano e zoomano.
 * Tenendo premuto il tasto dell'S Pen si usa la gomma.
 */
@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InkCanvas(
    image: ImageBitmap,
    state: InkState,
    stylusOnly: Boolean,
    overlay: Overlay? = null,
    modifier: Modifier = Modifier,
    canvasMode: Boolean = false,
    onSwipe: ((Int) -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    val swipe by rememberUpdatedState(onSwipe)
    val accent = MaterialTheme.colorScheme.primary
    val imgW = image.width.toFloat()
    val photoH = image.height.toFloat()
    val maxSide = maxOf(imgW, photoH)
    val pad = state.pad
    // tela = foto + spazio bianco; sopra e sotto le strisce dei testi, come verranno salvate
    val canvasW = imgW + pad.l + pad.r
    val canvasH = photoH + pad.t + pad.b
    val topH = (overlay?.topH ?: 0).toFloat()
    val contentH = topH + canvasH + (overlay?.bottomH ?: 0)

    var fitK by remember { mutableFloatStateOf(1f) }
    var k by remember { mutableFloatStateOf(1f) }
    var tx by remember { mutableFloatStateOf(0f) }
    var ty by remember { mutableFloatStateOf(0f) }
    var viewW by remember { mutableFloatStateOf(0f) }
    var viewH by remember { mutableFloatStateOf(0f) }
    val g = remember { Gesture() }

    /** Inquadra tutto il contenuto; in modalità tela lascia margine per allargare i bordi. */
    fun resetView() {
        if (viewW <= 0f || g.mode == Mode.RESIZE) return
        val f = min(viewW / canvasW, viewH / contentH) * (if (canvasMode) 0.7f else 1f)
        fitK = f; k = f
        // l'origine (0,0) è l'angolo in alto a sinistra della foto
        tx = (viewW - canvasW * f) / 2f + pad.l * f
        ty = (viewH - contentH * f) / 2f + (topH + pad.t) * f
    }

    /** In modalità tela: quali bordi si trovano sotto il dito, entro una tolleranza. */
    fun grabEdges(x: Float, y: Float): Boolean {
        val tol = 28f * density
        val left = tx - pad.l * k
        val right = tx + (imgW + pad.r) * k
        val top = ty - pad.t * k
        val bottom = ty + (photoH + pad.b) * k
        val inY = y in (top - tol)..(bottom + tol)
        val inX = x in (left - tol)..(right + tol)
        g.dragL = inY && abs(x - left) < tol
        g.dragR = inY && abs(x - right) < tol
        g.dragT = inX && abs(y - top) < tol
        g.dragB = inX && abs(y - bottom) < tol
        g.lastX = x; g.lastY = y
        return g.dragL || g.dragR || g.dragT || g.dragB
    }

    fun dragEdges(x: Float, y: Float) {
        val dx = (x - g.lastX) / k
        val dy = (y - g.lastY) / k
        g.lastX = x; g.lastY = y
        val p = state.pad
        // ogni lato si può allargare al massimo di una volta il lato lungo della foto
        state.setPad(
            Pad(
                if (g.dragL) (p.l - dx).coerceIn(0f, maxSide) else p.l,
                if (g.dragT) (p.t - dy).coerceIn(0f, maxSide) else p.t,
                if (g.dragR) (p.r + dx).coerceIn(0f, maxSide) else p.r,
                if (g.dragB) (p.b + dy).coerceIn(0f, maxSide) else p.b,
            )
        )
        // il bordo segue il dito: la foto resta ferma sullo schermo
    }

    fun toImg(sx: Float, sy: Float) = Offset((sx - tx) / k, (sy - ty) / k)

    fun centroidAndSpan(ev: MotionEvent): Triple<Float, Float, Float> {
        var cx = 0f
        var cy = 0f
        val n = ev.pointerCount
        for (i in 0 until n) { cx += ev.getX(i); cy += ev.getY(i) }
        cx /= n; cy /= n
        var span = 0f
        for (i in 0 until n) span += hypot(ev.getX(i) - cx, ev.getY(i) - cy)
        return Triple(cx, cy, span / n)
    }

    fun applyTransform(ev: MotionEvent) {
        val (cx, cy, span) = centroidAndSpan(ev)
        if (g.prevCount == ev.pointerCount) {
            // spostamento
            tx += cx - g.prevCx
            ty += cy - g.prevCy
            // zoom attorno al centro delle dita
            if (ev.pointerCount >= 2 && g.prevSpan > 1f && span > 1f) {
                val newK = (k * span / g.prevSpan).coerceIn(fitK, fitK * 12f)
                val f = newK / k
                tx = (tx - cx) * f + cx
                ty = (ty - cy) * f + cy
                k = newK
            }
            if (k <= fitK * 1.001f) resetView()
        }
        g.prevCount = ev.pointerCount
        g.prevCx = cx; g.prevCy = cy; g.prevSpan = span
    }

    fun addPoints(ev: MotionEvent, finger: Boolean) {
        for (h in 0 until ev.historySize) {
            val o = toImg(ev.getHistoricalX(0, h), ev.getHistoricalY(0, h))
            val p = if (finger) 0.6f else ev.getHistoricalPressure(0, h)
            if (g.mode == Mode.DRAW) state.addPoint(Pt(o.x, o.y, p))
            else state.eraseAt(o.x, o.y, 18f * density / k)
        }
        val o = toImg(ev.getX(0), ev.getY(0))
        val p = if (finger) 0.6f else ev.getPressure(0)
        if (g.mode == Mode.DRAW) state.addPoint(Pt(o.x, o.y, p))
        else state.eraseAt(o.x, o.y, 18f * density / k)
    }

    fun onTouch(ev: MotionEvent): Boolean {
        val tool = ev.getToolType(0)
        val isPen = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
        val penButton = (ev.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                g.prevCount = -1
                if (canvasMode) {
                    g.mode = if (grabEdges(ev.x, ev.y)) Mode.RESIZE else Mode.TRANSFORM
                    if (g.mode == Mode.TRANSFORM) applyTransform(ev)
                    return true
                }
                g.mode = when {
                    isPen && (penButton || tool == MotionEvent.TOOL_TYPE_ERASER || state.eraser) -> Mode.ERASE
                    isPen -> Mode.DRAW
                    stylusOnly -> Mode.TRANSFORM
                    state.eraser -> Mode.ERASE
                    else -> Mode.DRAW
                }
                when (g.mode) {
                    Mode.DRAW -> addPoints(ev, !isPen)
                    Mode.ERASE -> { state.beginErase(); addPoints(ev, !isPen) }
                    Mode.TRANSFORM -> {
                        // a foto intera, un dito che scorre in orizzontale cambia foto
                        g.swipeOk = k <= fitK * 1.001f
                        g.swipeX = ev.x; g.swipeY = ev.y
                        applyTransform(ev)
                    }
                    Mode.NONE -> {}
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                g.swipeOk = false
                // un secondo dito mentre si disegna con il dito = zoom
                if (!isPen && (g.mode == Mode.DRAW || g.mode == Mode.ERASE)) {
                    if (g.mode == Mode.DRAW) state.cancelStroke() else state.endErase()
                    g.mode = Mode.TRANSFORM
                }
                g.prevCount = -1
                if (g.mode == Mode.TRANSFORM) applyTransform(ev)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                g.prevCount = -1
            }
            MotionEvent.ACTION_MOVE -> when (g.mode) {
                Mode.DRAW, Mode.ERASE -> addPoints(ev, !isPen)
                Mode.TRANSFORM -> applyTransform(ev)
                Mode.RESIZE -> dragEdges(ev.x, ev.y)
                Mode.NONE -> {}
            }
            MotionEvent.ACTION_UP -> {
                when (g.mode) {
                    Mode.DRAW -> state.endStroke(maxSide)
                    Mode.ERASE -> state.endErase()
                    Mode.TRANSFORM -> if (g.swipeOk) {
                        val dx = ev.x - g.swipeX
                        val dy = ev.y - g.swipeY
                        if (abs(dx) > 64f * density && abs(dx) > 2f * abs(dy)) {
                            swipe?.invoke(if (dx < 0) 1 else -1)
                        }
                    }
                    else -> {}
                }
                g.swipeOk = false
                val wasResize = g.mode == Mode.RESIZE
                g.mode = Mode.NONE
                if (wasResize) resetView()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (g.mode == Mode.DRAW) state.cancelStroke()
                if (g.mode == Mode.ERASE) state.endErase()
                val wasResize = g.mode == Mode.RESIZE
                g.mode = Mode.NONE
                if (wasResize) resetView()
            }
        }
        return true
    }

    LaunchedEffect(contentH, canvasW, topH, canvasMode) { resetView() }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { sz ->
                viewW = sz.width.toFloat(); viewH = sz.height.toFloat()
                resetView()
            }
            .pointerInteropFilter { ev -> onTouch(ev) }
    ) {
        withTransform({
            translate(tx, ty)
            scale(k, k, pivot = Offset.Zero)
        }) {
            if (!pad.isZero || canvasMode) drawRect(Color.White, Offset(-pad.l, -pad.t), Size(canvasW, canvasH))
            drawImage(image)
            overlay?.let { o ->
                drawIntoCanvas {
                    it.nativeCanvas.save()
                    it.nativeCanvas.translate(-pad.l, -pad.t)
                    o.draw(it.nativeCanvas, canvasH)
                    it.nativeCanvas.restore()
                }
            }
            for (s in state.strokes) drawStrokeC(Color(s.color), s.width, s.points)
            if (state.current.isNotEmpty()) {
                drawStrokeC(Color(state.color), state.widthFactor * maxSide, state.current)
            }
            if (canvasMode) {
                // cornice della tela con le maniglie, di dimensione costante sullo schermo
                val w = 2.dp.toPx() / k
                val h = 14.dp.toPx() / k
                val l = -pad.l
                val t = -pad.t
                drawRect(accent, Offset(l, t), Size(canvasW, canvasH), style = DrawStroke(w))
                for (hx in listOf(l, l + canvasW / 2, l + canvasW)) for (hy in listOf(t, t + canvasH / 2, t + canvasH)) {
                    if (hx == l + canvasW / 2 && hy == t + canvasH / 2) continue
                    drawRect(accent, Offset(hx - h / 2, hy - h / 2), Size(h, h))
                }
            }
        }
    }
}

private fun DrawScope.drawStrokeC(color: Color, width: Float, pts: List<Pt>) {
    if (pts.size == 1) {
        val p = pts[0]
        drawCircle(color, pressureWidth(width, p.p) / 2f, Offset(p.x, p.y))
        return
    }
    for (i in 1 until pts.size) {
        val a = pts[i - 1]
        val b = pts[i]
        drawLine(
            color = color,
            start = Offset(a.x, a.y),
            end = Offset(b.x, b.y),
            strokeWidth = pressureWidth(width, (a.p + b.p) / 2f),
            cap = StrokeCap.Round,
        )
    }
}
