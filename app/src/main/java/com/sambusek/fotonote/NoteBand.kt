package com.sambusek.fotonote

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Banda nera aggiunta sotto la foto con la nota in bianco.
 * Dimensioni proporzionali alla foto, così il testo resta piccolo ma leggibile
 * a qualunque risoluzione.
 */
class NoteBand private constructor(private val layout: StaticLayout, private val pad: Float) {

    val height: Int = (layout.height + pad * 2).toInt()

    /** Disegna la banda a partire dalla quota [top] (in pixel della foto). */
    fun draw(canvas: Canvas, top: Float, width: Float) {
        canvas.drawRect(0f, top, width, top + height, BG)
        canvas.save()
        canvas.translate(pad, top + pad)
        layout.draw(canvas)
        canvas.restore()
    }

    companion object {
        private val BG = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }

        fun create(note: String, imageWidth: Int, imageHeight: Int): NoteBand? {
            val text = note.trim()
            if (text.isEmpty() || imageWidth <= 0) return null
            val size = minOf(imageWidth, imageHeight) * 0.026f
            val pad = size * 0.8f
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = size
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val w = (imageWidth - pad * 2).toInt().coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build()
            return NoteBand(layout, pad)
        }
    }
}
