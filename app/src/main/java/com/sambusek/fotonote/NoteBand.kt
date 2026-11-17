package com.sambusek.fotonote

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/** Dove può comparire un campo di testo: una delle sei caselle delle due strisce aggiunte. */
enum class FieldPos(val label: String, val short: String) {
    TOP("Banda superiore", "Banda sup."),
    BOTTOM("Banda inferiore", "Banda inf."),
    TL("Angolo superiore sinistro", "Ang. sup. sx"),
    TR("Angolo superiore destro", "Ang. sup. dx"),
    BL("Angolo inferiore sinistro", "Ang. inf. sx"),
    BR("Angolo inferiore destro", "Ang. inf. dx"),
}

/** Tipo di campo: da compilare foto per foto, oppure automatico (uguale o calcolato per ogni foto). */
enum class FieldKind(val label: String, val defaultLabel: String, val defaultPos: FieldPos) {
    INPUT("Campo da compilare", "", FieldPos.BOTTOM),
    DATE("Data dello scatto", "Data", FieldPos.TL),
    TIME("Ora dello scatto", "Ora", FieldPos.TL),
    SESSION("Nome del sopralluogo", "Sopralluogo", FieldPos.TOP),
    FIXED("Etichetta fissa", "Etichetta", FieldPos.TR),
}

/**
 * Un campo del layout di una sessione (es. "Piano", "Stanza") con la sua posizione.
 * Con [showLabel] il testo è stampato come "Piano: 3", altrimenti solo "3".
 * [text] è il testo dell'etichetta fissa ([FieldKind.FIXED]).
 */
data class NoteField(
    val id: String,
    val label: String,
    val pos: FieldPos,
    val showLabel: Boolean = true,
    val kind: FieldKind = FieldKind.INPUT,
    val text: String = "",
    /** Elenco di scelte, scritto separato da virgole (es. "P3, P4"); vuoto = testo libero. */
    val choices: String = "",
) {
    val auto: Boolean get() = kind != FieldKind.INPUT
    val options: List<String> get() = choices.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** Valori dei campi automatici: data e ora dello scatto, nome del sopralluogo, etichette fisse. */
object AutoFields {
    fun resolve(
        fields: List<NoteField>, values: Map<String, String>, sessionName: String, takenMillis: Long,
    ): Map<String, String> {
        if (fields.none { it.auto }) return values
        val out = values.toMutableMap()
        val date = java.util.Date(takenMillis)
        for (f in fields) when (f.kind) {
            FieldKind.INPUT -> {}
            FieldKind.DATE -> out[f.id] = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.ITALY).format(date)
            FieldKind.TIME -> out[f.id] = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALY).format(date)
            FieldKind.SESSION -> out[f.id] = sessionName
            FieldKind.FIXED -> out[f.id] = f.text
        }
        return out
    }
}

/**
 * Striscia nera aggiunta sopra o sotto la foto, divisa in tre caselle:
 * angolo sinistro (20%), banda centrale (60%), angolo destro (20%).
 * Il testo è bianco, di dimensione proporzionale alla foto.
 */
class Strip private constructor(
    private val cells: List<Pair<Float, StaticLayout>>, // x di partenza e testo
    private val pad: Float,
    private val width: Int,
    val height: Int,
) {
    fun draw(canvas: Canvas, top: Float) {
        canvas.drawRect(0f, top, width.toFloat(), top + height, BG)
        for ((x, layout) in cells) {
            canvas.save()
            canvas.translate(x, top + pad)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    companion object {
        const val SIDE = 0.20f // larghezza di ciascun angolo; il centro ha il resto
        private val BG = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }

        fun create(left: String, center: String, right: String, imageWidth: Int, imageHeight: Int): Strip? {
            if (imageWidth <= 0 || (left.isBlank() && center.isBlank() && right.isBlank())) return null
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = minOf(imageWidth, imageHeight) * 0.026f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            val pad = paint.textSize * 0.6f
            val side = imageWidth * SIDE
            val mid = imageWidth - 2 * side
            val specs = listOf(
                Triple(left.trim(), 0f to side, Layout.Alignment.ALIGN_NORMAL),
                Triple(center.trim(), side to mid, Layout.Alignment.ALIGN_CENTER),
                Triple(right.trim(), side + mid to side, Layout.Alignment.ALIGN_OPPOSITE),
            )
            val cells = specs.filter { it.first.isNotEmpty() }.map { (text, box, align) ->
                val w = (box.second - 2 * pad).toInt().coerceAtLeast(1)
                (box.first + pad) to StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
                    .setAlignment(align)
                    .setLineSpacing(0f, 1.15f)
                    .build()
            }
            val h = (cells.maxOf { it.second.height } + 2 * pad).toInt()
            return Strip(cells, pad, imageWidth, h)
        }
    }
}

/**
 * Tutti i testi di una foto, sempre nello spazio aggiunto e mai sopra la foto.
 * La nota libera va nella banda inferiore (al centro); i campi nella casella scelta.
 */
class Overlay private constructor(private val top: Strip?, private val bottom: Strip?) {
    val topH: Int get() = top?.height ?: 0
    val bottomH: Int get() = bottom?.height ?: 0

    /** Disegna le strisce; l'origine del canvas è l'angolo in alto a sinistra della foto. */
    fun draw(canvas: Canvas, photoH: Float) {
        top?.draw(canvas, -topH.toFloat())
        bottom?.draw(canvas, photoH)
    }

    companion object {
        private fun lines(pos: FieldPos, fields: List<NoteField>, values: Map<String, String>): List<String> =
            fields.filter { it.pos == pos }.mapNotNull { f ->
                values[f.id]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    if (f.showLabel && f.label.isNotBlank()) "${f.label.trim()}: $it" else it
                }
            }

        fun create(
            note: String, fields: List<NoteField>, values: Map<String, String>,
            imageWidth: Int, imageHeight: Int,
        ): Overlay {
            fun text(pos: FieldPos) = lines(pos, fields, values).joinToString("\n")
            val bottomCenter = (lines(FieldPos.BOTTOM, fields, values) +
                listOfNotNull(note.trim().takeIf { it.isNotEmpty() })).joinToString("\n")
            return Overlay(
                Strip.create(text(FieldPos.TL), text(FieldPos.TOP), text(FieldPos.TR), imageWidth, imageHeight),
                Strip.create(text(FieldPos.BL), bottomCenter, text(FieldPos.BR), imageWidth, imageHeight),
            )
        }

        /** Tutti i testi in una riga sola (per i metadati); vuoto se non c'è nulla. */
        fun describe(note: String, fields: List<NoteField>, values: Map<String, String>): String =
            (FieldPos.entries.flatMap { lines(it, fields, values) } +
                listOfNotNull(note.trim().takeIf { it.isNotEmpty() })).joinToString("; ")
    }
}
