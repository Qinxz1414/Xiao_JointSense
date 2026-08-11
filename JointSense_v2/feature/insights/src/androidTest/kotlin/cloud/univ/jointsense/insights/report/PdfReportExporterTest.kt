package cloud.univ.jointsense.insights.report

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.feature.insights.R
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfReportExporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun exportCreatesNonEmptyMultipagePdfAndRepeatsHeaderInk() {
        val report = FormattedReport(
            title = "JointSense Research Trend Report",
            pageHeader = "Generated: Jan 1, 2026, 12:00:00 PM",
            body = List(180) { index ->
                "Observation ${index + 1}: 炎症生物标志物 longitudinal result with enough text to wrap safely."
            }.joinToString("\n"),
        )
        val outputDirectory = File(context.cacheDir, "pdf-export-test").apply { mkdirs() }

        val result = PdfReportExporter.export(outputDirectory, report, now = { 123L })

        assertTrue(result is PdfExportResult.Success)
        val file = (result as PdfExportResult.Success).file
        assertTrue(file.isFile)
        assertTrue(file.length() > 0L)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount > 1)
                repeat(renderer.pageCount) { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertTrue("page ${pageIndex + 1} has no repeated header ink", bitmap.hasInkInHeader())
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun exportReportsFileFailureInsteadOfSuccess() {
        val notADirectory = File(context.cacheDir, "not-a-report-directory").apply {
            parentFile?.mkdirs()
            writeText("occupied")
        }

        val result = PdfReportExporter.export(
            outputDirectory = notADirectory,
            report = FormattedReport("Title", "Header", "Body"),
        )

        assertEquals(PdfExportResult.Failure(ReportError.CREATE_FILE), result)
    }

    @Test
    fun shareTextMapsMissingActivityToVisibleError() {
        val throwingContext = object : ContextWrapper(context) {
            override fun startActivity(intent: Intent?) {
                throw ActivityNotFoundException("no share target")
            }
        }

        val result = ReportSharing.shareText(throwingContext, "complete report", "Share")

        assertEquals(ReportShareResult.Failure(ReportError.NO_SHARE_APP), result)
    }

    @Test
    fun localizedResourcesContainTheExactApprovedDisclaimers() {
        val english = context.forLocale(Locale.US).getString(R.string.report_disclaimer)
        val chinese = context.forLocale(Locale.SIMPLIFIED_CHINESE).getString(R.string.report_disclaimer)

        assertEquals(ENGLISH_DISCLAIMER, english)
        assertEquals(CHINESE_DISCLAIMER, chinese)
    }

    private fun Context.forLocale(locale: Locale): Context {
        val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
        return createConfigurationContext(configuration)
    }

    private fun Bitmap.hasInkInHeader(): Boolean {
        val bottom = minOf(height, 125)
        for (y in 30 until bottom step 2) {
            for (x in 30 until width - 30 step 2) {
                if (getPixel(x, y) != Color.WHITE) return true
            }
        }
        return false
    }

    private companion object {
        const val ENGLISH_DISCLAIMER = "Results in this report are estimates derived from smartphone-photo colorimetry for research and longitudinal trend observation only. They are not intended for clinical diagnosis, treatment decisions, or as a substitute for validated laboratory testing."
        const val CHINESE_DISCLAIMER = "本报告结果基于手机照片色度代理估算，仅供科研与纵向趋势观察，不作为临床诊断、治疗决策或替代经验证实验室检测的依据。"
    }
}
