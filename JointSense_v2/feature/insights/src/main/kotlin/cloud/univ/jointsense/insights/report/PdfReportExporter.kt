package cloud.univ.jointsense.insights.report

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

enum class ReportError {
    CREATE_FILE,
    EMPTY_FILE,
    NO_SHARE_APP,
    OPEN_FILE,
}

sealed interface PdfExportResult {
    class Success private constructor(val file: File) : PdfExportResult {
        companion object {
            internal fun verified(file: File): Success {
                require(file.isFile && file.length() > 0L)
                return Success(file)
            }
        }
    }

    data class Failure(val error: ReportError) : PdfExportResult
}

sealed interface ReportShareResult {
    data object Started : ReportShareResult
    data class Failure(val error: ReportError) : ReportShareResult
}

object PdfReportExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 48f
    private const val RIGHT = 48f
    private const val BOTTOM = 800f
    private const val BODY_LINE_HEIGHT = 18f

    fun export(
        outputDirectory: File,
        report: FormattedReport,
        now: () -> Long = System::currentTimeMillis,
    ): PdfExportResult {
        if ((!outputDirectory.exists() && !outputDirectory.mkdirs()) || !outputDirectory.isDirectory) {
            return PdfExportResult.Failure(ReportError.CREATE_FILE)
        }

        val output = File(outputDirectory, "JointSense_report_${now()}.pdf")
        return try {
            writeDocument(output, report)
            if (!output.isFile || output.length() <= 0L) {
                output.delete()
                PdfExportResult.Failure(ReportError.EMPTY_FILE)
            } else {
                PdfExportResult.Success.verified(output)
            }
        } catch (_: Exception) {
            output.delete()
            PdfExportResult.Failure(ReportError.CREATE_FILE)
        }
    }

    private fun writeDocument(output: File, report: FormattedReport) {
        val document = PdfDocument()
        try {
            val writer = PageWriter(document, report.title, report.pageHeader)
            writer.startPage()
            layoutLines(report.body, writer.bodyPaint, PAGE_WIDTH - LEFT - RIGHT).forEach { line ->
                writer.drawBodyLine(line)
            }
            writer.finishPage()
            FileOutputStream(output).use { stream ->
                document.writeTo(stream)
                stream.fd.sync()
            }
        } finally {
            document.close()
        }
    }

    private class PageWriter(
        private val document: PdfDocument,
        private val title: String,
        private val header: String,
    ) {
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0E2841")
            textSize = 11.5f
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0E2841")
            textSize = 20f
            isFakeBoldText = true
        }
        private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#52697D")
            textSize = 9.5f
        }

        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f

        fun startPage() {
            pageNumber += 1
            val nextPage = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            page = nextPage
            y = 60f
            nextPage.canvas.drawText(title, LEFT, y, titlePaint)
            y += 21f
            nextPage.canvas.drawText(header, LEFT, y, headerPaint)
            y += 31f
        }

        fun drawBodyLine(line: String) {
            if (y + BODY_LINE_HEIGHT > BOTTOM) {
                finishPage()
                startPage()
            }
            if (line.isNotEmpty()) {
                requireNotNull(page).canvas.drawText(line, LEFT, y, bodyPaint)
            }
            y += BODY_LINE_HEIGHT
        }

        fun finishPage() {
            page?.let(document::finishPage)
            page = null
        }
    }
}

object ReportSharing {
    fun sharePdf(context: Context, file: File, chooserTitle: String): ReportShareResult {
        if (!file.isFile || file.length() <= 0L) {
            return ReportShareResult.Failure(ReportError.EMPTY_FILE)
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            ReportShareResult.Started
        } catch (_: ActivityNotFoundException) {
            ReportShareResult.Failure(ReportError.NO_SHARE_APP)
        } catch (_: Exception) {
            ReportShareResult.Failure(ReportError.OPEN_FILE)
        }
    }

    fun shareText(context: Context, text: String, chooserTitle: String): ReportShareResult = try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
        ReportShareResult.Started
    } catch (_: ActivityNotFoundException) {
        ReportShareResult.Failure(ReportError.NO_SHARE_APP)
    } catch (_: Exception) {
        ReportShareResult.Failure(ReportError.OPEN_FILE)
    }
}
