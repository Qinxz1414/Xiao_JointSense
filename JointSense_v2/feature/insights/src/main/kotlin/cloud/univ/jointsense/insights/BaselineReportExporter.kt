package cloud.univ.jointsense.insights

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Builds a simple text-layout PDF report and shares it via the
 * app's FileProvider (cache-path is already declared).
 */
internal object BaselineReportExporter {

    fun buildPdf(context: Context, title: String, lines: List<String>): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "JointSense_report_${System.currentTimeMillis()}.pdf")

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
        )
        var canvas = page.canvas
        var y = 64f

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0E2841")
            textSize = 20f
            isFakeBoldText = true
        }
        val bodyPaint = Paint().apply {
            color = Color.parseColor("#0E2841")
            textSize = 12f
        }

        canvas.drawText(title, 48f, y, titlePaint)
        y += 32f

        for (line in lines) {
            if (y > 800f) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                )
                canvas = page.canvas
                y = 64f
            }
            canvas.drawText(line, 48f, y, bodyPaint)
            y += 20f
        }

        document.finishPage(page)
        document.writeTo(FileOutputStream(file))
        document.close()
        return file
    }

    fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share summary"))
    }
}
