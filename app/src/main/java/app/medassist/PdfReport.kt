package app.medassist

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Turns one analysis into an A4 PDF that can be sent to a doctor or printed. */
object PdfReport {
    private const val W = 595
    private const val H = 842
    private const val M = 48

    private data class Line(val text: String, val size: Float, val bold: Boolean, val gapBefore: Int)

    fun share(context: Context, markdown: String, title: String, patient: String?, heading: String = "МедАнализ — разбор документов") {
        val file = build(context, markdown, title, patient, heading)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Отправить PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun build(context: Context, markdown: String, title: String, patient: String?, heading: String): File {
        val date = SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date())
        val lines = mutableListOf(
            Line(heading, 18f, true, 0),
            Line(listOfNotNull(title, patient?.let { "Пациент: $it" }, "PDF создан $date").joinToString("  ·  "), 9f, false, 4),
        )
        markdown.lines().forEach { raw -> toLine(raw)?.let(lines::add) }
        lines += Line(
            "Документ создан ИИ-помощником. Это не диагноз и не замена очной консультации врача.",
            8f, false, 18,
        )

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0

        fun newPage() {
            page?.let(doc::finishPage)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M
            val p = TextPaint().apply { textSize = 8f; color = Color.GRAY; isAntiAlias = true }
            page!!.canvas.drawText("МедАнализ · стр. $pageNo", M.toFloat(), (H - 24).toFloat(), p)
        }
        newPage()

        for (line in lines) {
            val paint = TextPaint().apply {
                isAntiAlias = true
                textSize = line.size
                color = if (line.bold && line.size > 11f) Color.rgb(7, 94, 93) else Color.BLACK
                typeface = if (line.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val layout = StaticLayout.Builder.obtain(line.text, 0, line.text.length, paint, W - 2 * M)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1f)
                .build()
            // Split long paragraphs across pages line by line.
            var first = 0
            y += line.gapBefore
            while (first < layout.lineCount) {
                if (y + (layout.getLineBottom(first) - layout.getLineTop(first)) > H - M) newPage()
                var last = first
                while (last + 1 < layout.lineCount &&
                    y + layout.getLineBottom(last + 1) - layout.getLineTop(first) <= H - M
                ) last++
                val c = page!!.canvas
                c.save()
                c.translate(M.toFloat(), (y - layout.getLineTop(first)).toFloat())
                c.clipRect(0, layout.getLineTop(first), W - 2 * M, layout.getLineBottom(last))
                layout.draw(c)
                c.restore()
                y += layout.getLineBottom(last) - layout.getLineTop(first)
                first = last + 1
            }
        }
        page?.let(doc::finishPage)

        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "MedAnaliz_${SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())}.pdf")
        file.outputStream().use(doc::writeTo)
        doc.close()
        return file
    }

    private fun toLine(raw: String): Line? {
        val t = raw.trimEnd()
        if (t.isBlank()) return null
        val plain = t.replace("**", "").trim()
        if (plain.startsWith("СТАТУС:") || plain.startsWith("ЗАГОЛОВОК:")) return null
        if (plain.startsWith("КОРОТКО:")) return Line(plain.removePrefix("КОРОТКО:").trim(), 11f, true, 8)
        if (Regex("^\\|?\\s*:?-{2,}").containsMatchIn(t)) return null // table separator row
        val heading = Regex("^(#{1,6})\\s+(.*)").find(t)
        if (heading != null) {
            val level = heading.groupValues[1].length
            return Line(clean(heading.groupValues[2]), if (level <= 2) 14f else 12f, true, 10)
        }
        var text = t
        val indent = text.length - text.trimStart().length
        text = text.trimStart()
        text = when {
            text.startsWith("- ") || text.startsWith("* ") -> " ".repeat(indent) + "•  " + text.drop(2)
            text.startsWith("|") -> text.trim('|').split("|").joinToString("   |   ") { it.trim() }
            else -> " ".repeat(indent) + text
        }
        return Line(clean(text), 10f, false, 3)
    }

    private fun clean(s: String) = s.replace("**", "").replace("__", "").replace("`", "")
}
