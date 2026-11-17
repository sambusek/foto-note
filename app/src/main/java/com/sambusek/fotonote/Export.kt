package com.sambusek.fotonote

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {
    /** ZIP con le foto di un'emissione, dentro una cartella col suo nome, chiamate come [names]. */
    fun zip(c: Context, r: Release, names: List<String>, out: File, onProgress: (Float) -> Unit) {
        val used = HashSet<String>()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
            zos.setLevel(0) // i JPEG sono già compressi
            r.items.forEachIndexed { i, item ->
                val wanted = names.getOrNull(i) ?: item.name
                var name = wanted
                var n = 2
                while (!used.add(name.lowercase())) {
                    name = wanted.substringBeforeLast('.') + "_${n++}." + wanted.substringAfterLast('.', "jpg")
                }
                val input = runCatching { c.contentResolver.openInputStream(Uri.parse(item.out)) }.getOrNull()
                if (input != null) {
                    zos.putNextEntry(ZipEntry("${r.folder}/$name"))
                    input.use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                onProgress((i + 1f) / r.items.size)
            }
        }
    }

    fun share(c: Context, f: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, f.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(f.name, uri)
        c.startActivity(Intent.createChooser(send, title))
    }
}

/**
 * Report PDF multipagina: copertina e poi una foto per pagina (A4, orientata come la foto).
 * Il PDF è scritto direttamente, con le foto incorporate come JPEG, così il file resta leggero.
 */
object PdfReport {
    private val CP1252: Charset = Charset.forName("windows-1252")
    private const val A4_SHORT = 595f
    private const val A4_LONG = 842f
    private const val MARGIN = 36f
    private const val IMG_MAX_SIDE = 2000

    private class Out(val os: OutputStream) {
        var pos = 0L
        fun w(b: ByteArray) { os.write(b); pos += b.size }
        fun w(s: String) = w(s.toByteArray(Charsets.ISO_8859_1))
    }

    private fun n(v: Float) = String.format(Locale.US, "%.2f", v)

    private class Content {
        val buf = ByteArrayOutputStream()
        private fun raw(s: String) = buf.write(s.toByteArray(Charsets.ISO_8859_1))

        fun text(bold: Boolean, size: Float, x: Float, y: Float, s: String, gray: Float = 0f) {
            raw("BT ${n(gray)} g /${if (bold) "F2" else "F1"} ${n(size)} Tf ${n(x)} ${n(y)} Td (")
            for (b in s.toByteArray(CP1252)) {
                val ch = b.toInt() and 0xFF
                if (ch == '('.code || ch == ')'.code || ch == '\\'.code) buf.write('\\'.code)
                if (ch >= 32) buf.write(ch)
            }
            raw(") Tj ET\n")
        }

        fun line(x1: Float, y: Float, x2: Float) =
            raw("0.5 G 0.5 w ${n(x1)} ${n(y)} m ${n(x2)} ${n(y)} l S\n")

        fun image(x: Float, y: Float, w: Float, h: Float) =
            raw("q ${n(w)} 0 0 ${n(h)} ${n(x)} ${n(y)} cm /Im0 Do Q\n")
    }

    /** Larghezza approssimata di un testo in Helvetica (per ridurre i titoli lunghi). */
    private fun fit(text: String, size: Float, maxWidth: Float): Float {
        val w = text.length * size * 0.58f
        return if (w <= maxWidth) size else size * maxWidth / w
    }

    fun write(c: Context, s: Session, r: Release, user: String, file: File, onProgress: (Float) -> Unit) {
        BufferedOutputStream(FileOutputStream(file)).use { os ->
            val out = Out(os)
            val offsets = HashMap<Int, Long>()
            var next = 5 // 1 catalogo, 2 pagine, 3 e 4 font
            val pages = ArrayList<Int>()

            fun begin(id: Int) { offsets[id] = out.pos; out.w("$id 0 obj\n") }
            fun end() = out.w("\nendobj\n")
            fun stream(id: Int, dict: String, data: ByteArray) {
                begin(id)
                out.w("<< $dict/Length ${data.size} >>\nstream\n")
                out.w(data)
                out.w("\nendstream")
                end()
            }
            fun page(w: Float, h: Float, content: Content, imageId: Int?) {
                val cid = next++
                stream(cid, "", content.buf.toByteArray())
                val pid = next++
                begin(pid)
                val xo = if (imageId != null) "/XObject << /Im0 $imageId 0 R >> " else ""
                out.w(
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${n(w)} ${n(h)}] " +
                        "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> $xo>> /Contents $cid 0 R >>"
                )
                end()
                pages += pid
            }

            out.w("%PDF-1.4\n")
            out.w(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
            begin(3); out.w("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"); end()
            begin(4); out.w("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"); end()

            // --- copertina ---
            val dayFmt = SimpleDateFormat("d MMMM yyyy", Locale.ITALY)
            val timeFmt = SimpleDateFormat("HH:mm", Locale.ITALY)
            val cover = Content()
            val cw = A4_SHORT - 2 * MARGIN
            cover.text(false, 11f, MARGIN, 700f, "REPORT DI SOPRALLUOGO", 0.4f)
            cover.text(true, fit(s.name, 30f, cw), MARGIN, 660f, s.name)
            cover.line(MARGIN, 640f, A4_SHORT - MARGIN)
            var y = 612f
            fun line(t: String) { cover.text(false, 13f, MARGIN, y, t); y -= 20f }
            line("Emissione: ${r.label} del ${dayFmt.format(Date(r.time))}, ${timeFmt.format(Date(r.time))}")
            line("Sopralluogo iniziato il ${dayFmt.format(Date(s.start))}, ${timeFmt.format(Date(s.start))}")
            line("Foto: ${r.items.size}")
            line("Cartella: ${r.folder}")
            if (user.isNotBlank()) line("Redatto da: $user")
            cover.text(false, 9f, MARGIN, MARGIN, "Generato con Pastashoot", 0.4f)
            page(A4_SHORT, A4_LONG, cover, null)

            // --- una foto per pagina ---
            val total = r.items.size
            r.items.forEachIndexed { i, item ->
                val bmp = runCatching {
                    AnnotationIO.loadBitmap(c.contentResolver, Uri.parse(item.out), IMG_MAX_SIDE)
                }.getOrNull()
                if (bmp != null) {
                    val jpg = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 82, jpg)
                    val iw = bmp.width
                    val ih = bmp.height
                    bmp.recycle()
                    val imageId = next++
                    stream(
                        imageId,
                        "/Type /XObject /Subtype /Image /Width $iw /Height $ih " +
                            "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode ",
                        jpg.toByteArray(),
                    )
                    val landscape = iw > ih
                    val pw = if (landscape) A4_LONG else A4_SHORT
                    val ph = if (landscape) A4_SHORT else A4_LONG
                    val ct = Content()
                    ct.text(true, 10f, MARGIN, ph - MARGIN - 8f, "${s.name.take(60)} · ${r.label}")
                    ct.text(false, 10f, pw - MARGIN - 60f, ph - MARGIN - 8f, "${i + 1} / $total", 0.3f)
                    ct.text(false, 8f, MARGIN, MARGIN - 6f, item.name, 0.4f)
                    val boxW = pw - 2 * MARGIN
                    val boxH = ph - 2 * MARGIN - 28f
                    val k = minOf(boxW / iw, boxH / ih)
                    val dw = iw * k
                    val dh = ih * k
                    ct.image(MARGIN + (boxW - dw) / 2, MARGIN + 8f + (boxH - dh) / 2, dw, dh)
                    page(pw, ph, ct, imageId)
                }
                onProgress((i + 1f) / total)
            }

            begin(2)
            out.w("<< /Type /Pages /Count ${pages.size} /Kids [${pages.joinToString(" ") { "$it 0 R" }}] >>")
            end()
            begin(1); out.w("<< /Type /Catalog /Pages 2 0 R >>"); end()

            val xref = out.pos
            val count = next
            out.w("xref\n0 $count\n")
            out.w("0000000000 65535 f \n")
            for (id in 1 until count) out.w(String.format(Locale.US, "%010d 00000 n \n", offsets[id] ?: 0L))
            out.w("trailer\n<< /Size $count /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
    }
}
