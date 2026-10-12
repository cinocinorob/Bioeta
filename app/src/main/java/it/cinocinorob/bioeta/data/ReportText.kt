package it.cinocinorob.bioeta.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Estrae il testo da un referto: PDF digitale (PdfBox, anche protetto da password),
 * PDF scansionato o foto (OCR ML Kit, modello incluso nell'app, nessuna connessione).
 */
object ReportText {

    class PasswordRequired(val wrongPassword: Boolean) : Exception("PDF protetto da password")

    data class Extracted(val text: String, val usedOcr: Boolean)

    private const val MAX_OCR_PAGES = 6

    suspend fun extract(context: Context, uri: Uri, password: String?): Extracted {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: ""
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: error("File non leggibile")
        val isPdf = mime == "application/pdf" ||
            (bytes.size > 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() &&
                bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte())

        if (!isPdf) {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            return Extracted(ocr(bmp), usedOcr = true)
        }

        PDFBoxResourceLoader.init(context.applicationContext)
        val doc = try {
            if (password.isNullOrEmpty()) PDDocument.load(ByteArrayInputStream(bytes))
            else PDDocument.load(ByteArrayInputStream(bytes), password)
        } catch (e: InvalidPasswordException) {
            throw PasswordRequired(wrongPassword = !password.isNullOrEmpty())
        }

        doc.use { d ->
            d.isAllSecurityToBeRemoved = true
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val text = stripper.getText(d)
            if (text.count { !it.isWhitespace() } >= 80) return Extracted(text, usedOcr = false)

            // PDF senza testo (scansione): rendering delle pagine e OCR
            val pages = minOf(d.numberOfPages, MAX_OCR_PAGES)
            val sb = StringBuilder()
            val cleanBytes = if (d.isEncrypted || !password.isNullOrEmpty()) {
                val out = java.io.ByteArrayOutputStream()
                d.save(out)
                out.toByteArray()
            } else bytes
            val tmp = java.io.File.createTempFile("referto", ".pdf", context.cacheDir)
            try {
                tmp.writeBytes(cleanBytes)
                android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (i in 0 until minOf(pages, renderer.pageCount)) {
                            renderer.openPage(i).use { page ->
                                val scale = 3
                                val bmp = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                sb.append(ocr(bmp)).append('\n')
                                bmp.recycle()
                            }
                        }
                    }
                }
            } finally {
                tmp.delete()
            }
            return Extracted(sb.toString(), usedOcr = true)
        }
    }

    private suspend fun ocr(bmp: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result: Text = suspendCancellableCoroutine { cont ->
                recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
            return rows(result)
        } finally {
            recognizer.close()
        }
    }

    /**
     * L'OCR restituisce blocchi per colonna: ricostruisce le righe della tabella
     * raggruppando le linee per coordinata verticale e ordinandole da sinistra a destra.
     */
    private fun rows(t: Text): String {
        data class L(val text: String, val left: Int, val cy: Float, val h: Int)
        val lines = t.textBlocks.flatMap { b ->
            b.lines.mapNotNull { l ->
                val r = l.boundingBox ?: return@mapNotNull null
                L(l.text, r.left, r.exactCenterY(), r.height())
            }
        }.sortedBy { it.cy }
        if (lines.isEmpty()) return ""
        val medianH = lines.map { it.h }.sorted()[lines.size / 2].coerceAtLeast(1)
        val out = StringBuilder()
        var row = mutableListOf<L>()
        var rowY = lines.first().cy
        for (l in lines) {
            if (row.isNotEmpty() && kotlin.math.abs(l.cy - rowY) > medianH * 0.6f) {
                out.append(row.sortedBy { it.left }.joinToString("   ") { it.text }).append('\n')
                row = mutableListOf()
            }
            if (row.isEmpty()) rowY = l.cy
            row += l
        }
        if (row.isNotEmpty()) out.append(row.sortedBy { it.left }.joinToString("   ") { it.text }).append('\n')
        return out.toString()
    }
}
