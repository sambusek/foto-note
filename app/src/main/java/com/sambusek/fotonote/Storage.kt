package com.sambusek.fotonote

import java.io.File
import java.io.InputStream

/**
 * Scrive il file in modo atomico: prima su un file temporaneo, poi lo sostituisce.
 * Se l'app si chiude a metà scrittura il file precedente resta integro.
 */
fun File.writeAtomic(text: String) {
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(this)) {
        // su alcuni file system rename non sovrascrive: si cancella prima
        delete()
        if (!tmp.renameTo(this)) { writeText(text); tmp.delete() }
    }
}

/** Legge al massimo [max] byte: i file più grandi sono rifiutati (protezione da file anomali). */
fun InputStream.readLimited(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        require(total <= max) { "file troppo grande" }
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

const val KB = 1024
const val MB = 1024 * KB
