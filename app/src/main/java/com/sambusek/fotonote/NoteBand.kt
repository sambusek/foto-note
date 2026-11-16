package com.sambusek.fotonote

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil

/** Dove può comparire un campo di testo sulla foto. */
enum class FieldPos(val label: String, val short: String) {
    TOP("Banda superiore", "Banda sup."),
    BOTTOM("Banda inferiore", "Banda inf."),
    TL("Angolo superiore sinistro", "Ang. sup. sx"),
    TR("Angolo superiore destro", "Ang. sup. dx"),
    BL("Angolo inferiore sinistro", "Ang. inf. sx"),
    BR("Angolo inferiore destro", "Ang. inf. dx"),
}

/** Un campo del layout di una sessione (es. "Piano", "Stanza") con la sua posizione. */
class NoteField(val id: String, val label: String, val pos: FieldPos)

/**
 * Riquadro nero con testo bianco. Dimensioni proporzionali alla foto, così il testo
 * resta piccolo ma leggibile a qualunque risoluzione.
 */
class NoteBand private constructor(
    private val layout: StaticLayout,
    private val pad: Float,
    val width: Int,
) {
    val height: Int = (layout.height + pad * 2).toInt()

    fun draw(canvas: Canvas, left: Float, top: Float) {
        canvas.drawRect(left, top, left + width, top + height, BG)
        canvas.save()
        canvas.translate(left + pad, top + pad)
        layout.draw(canvas)
        canvas.restore()
    }

    companion object {
        private val BG = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }

        private fun paint(imageWidth: Int, imageHeight: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = minOf(imageWidth, imageHeight) * 0.026f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }

        private fun layout(text: String, paint: TextPaint, w: Int) =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, w.coerceAtLeast(1))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build()

        /** Banda larga quanto la foto. */
        fun band(text: String, imageWidth: Int, imageHeight: Int): NoteBand? {
            val t = text.trim()
            if (t.isEmpty() || imageWidth <= 0) return null
            val p = paint(imageWidth, imageHeight)
            val pad = p.textSize * 0.8f
            return NoteBand(layout(t, p, (imageWidth - pad * 2).toInt()), pad, imageWidth)
        }

        /** Riquadro d'angolo: largo quanto il testo, al massimo il 46% della foto. */
        fun box(text: String, imageWidth: Int, imageHeight: Int): NoteBand? {
            val t = text.trim()
            if (t.isEmpty() || imageWidth <= 0) return null
            val p = paint(imageWidth, imageHeight)
            val pad = p.textSize * 0.5f
            val maxW = (imageWidth * 0.46f - pad * 2).toInt()
            val first = layout(t, p, maxW)
            var w = 0f
            for (i in 0 until first.lineCount) w = maxOf(w, first.getLineWidth(i))
            val tw = ceil(w).toInt() + 1
            return NoteBand(layout(t, p, tw), pad, (tw + pad * 2).toInt())
        }
    }
}

/**
 * Tutti i testi di una foto: banda sopra, banda sotto e riquadri negli angoli.
 * La nota libera va sempre nella banda inferiore; i campi nella posizione scelta.
 */
class Overlay private constructor(
    private val top: NoteBand?,
    private val bottom: NoteBand?,
    private val corners: List<Pair<FieldPos, NoteBand>>,
) {
    val topH: Int get() = top?.height ?: 0
    val bottomH: Int get() = bottom?.height ?: 0

    /** Disegna i testi; l'origine del canvas è l'angolo in alto a sinistra della foto. */
    fun draw(canvas: Canvas, photoW: Float, photoH: Float) {
        top?.draw(canvas, 0f, -topH.toFloat())
        bottom?.draw(canvas, 0f, photoH)
        for ((pos, b) in corners) {
            val x = if (pos == FieldPos.TL || pos == FieldPos.BL) 0f else photoW - b.width
            val y = if (pos == FieldPos.TL || pos == FieldPos.TR) 0f else photoH - b.height
            b.draw(canvas, x, y)
        }
    }

    companion object {
        private fun lines(pos: FieldPos, fields: List<NoteField>, values: Map<String, String>): List<String> =
            fields.filter { it.pos == pos }.mapNotNull { f ->
                values[f.id]?.trim()?.takeIf { it.isNotEmpty() }?.let { "${f.label}: $it" }
            }

        fun create(
            note: String, fields: List<NoteField>, values: Map<String, String>,
            imageWidth: Int, imageHeight: Int,
        ): Overlay {
            val bottomLines = lines(FieldPos.BOTTOM, fields, values) +
                listOfNotNull(note.trim().takeIf { it.isNotEmpty() })
            return Overlay(
                NoteBand.band(lines(FieldPos.TOP, fields, values).joinToString("\n"), imageWidth, imageHeight),
                NoteBand.band(bottomLines.joinToString("\n"), imageWidth, imageHeight),
                listOf(FieldPos.TL, FieldPos.TR, FieldPos.BL, FieldPos.BR).mapNotNull { pos ->
                    NoteBand.box(lines(pos, fields, values).joinToString("\n"), imageWidth, imageHeight)
                        ?.let { pos to it }
                },
            )
        }

        /** Tutti i testi in una riga sola (per i metadati); vuoto se non c'è nulla. */
        fun describe(note: String, fields: List<NoteField>, values: Map<String, String>): String =
            (FieldPos.entries.flatMap { lines(it, fields, values) } +
                listOfNotNull(note.trim().takeIf { it.isNotEmpty() })).joinToString("; ")
    }
}
