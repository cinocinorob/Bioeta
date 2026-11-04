package it.cinocinorob.bioeta.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Report per il medico: un PDF A4 creato sul telefono con i dati che servono in una visita (pressione secondo il
 * protocollo ESH, ECG esportati, ultimi esami, andamenti di 3 mesi). Il file resta nella memoria dell'app finché
 * l'utente non lo condivide: nessun invio automatico.
 */
object DoctorReport {
    data class Section(val title: String, val lines: List<String>)

    data class Data(val name: String, val ageSex: String, val generated: LocalDate, val sections: List<Section>)

    private const val W = 595 // A4 a 72 dpi
    private const val H = 842
    private const val M = 48f

    fun write(ctx: Context, d: Data): File {
        val dir = File(ctx.filesDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // tiene solo l'ultimo report
        val file = File(dir, "BioEta-report-${d.generated}.pdf")
        val doc = PdfDocument()
        val title = Paint().apply { isAntiAlias = true; textSize = 18f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val h2 = Paint().apply { isAntiAlias = true; textSize = 12.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF00695C.toInt() }
        val body = Paint().apply { isAntiAlias = true; textSize = 10f; color = 0xFF222222.toInt() }
        val small = Paint().apply { isAntiAlias = true; textSize = 8f; color = 0xFF666666.toInt() }
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun newPage() {
            page?.let { pg ->
                pg.canvas.drawText("BioEtà · pagina $pageNo · dati da Connessione Salute e inserimenti dell'utente: non è un referto medico", M, H - 24f, small)
                doc.finishPage(pg)
            }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
        }
        fun ensure(h: Float) { if (y + h > H - 48f) newPage() }
        fun wrap(text: String, p: Paint, maxW: Float): List<String> {
            val out = mutableListOf<String>(); var line = ""
            for (word in text.split(" ")) {
                val t = if (line.isEmpty()) word else "$line $word"
                if (p.measureText(t) > maxW && line.isNotEmpty()) { out += line; line = word } else line = t
            }
            if (line.isNotEmpty()) out += line
            return out
        }
        newPage()
        page!!.canvas.drawText("Riepilogo per il medico", M, y + 18f, title); y += 30f
        page!!.canvas.drawText("${d.name} · ${d.ageSex} · creato il ${d.generated.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN))}", M, y, body); y += 22f
        for (s in d.sections) {
            ensure(40f)
            page!!.canvas.drawText(s.title, M, y, h2); y += 16f
            for (l in s.lines) for (w in wrap(l, body, W - 2 * M)) {
                ensure(14f)
                page!!.canvas.drawText(w, M, y, body); y += 13.5f
            }
            y += 10f
        }
        page?.let { pg ->
            pg.canvas.drawText("BioEtà · pagina $pageNo · dati da Connessione Salute e inserimenti dell'utente: non è un referto medico", M, H - 24f, small)
            doc.finishPage(pg)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
