package cloud.univ.jointsense.insights.report

import java.io.IOException
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfReportExporterUnitTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun productionLifecycleSuccessIsExactAndSingleShot() {
        val events = mutableListOf<String>()
        val backend = object : ReportDocumentBackend {
            override fun startPage() {
                events += "start-page"
            }

            override fun drawReport() {
                events += "draw-report"
            }

            override fun finishPage() {
                events += "finish-page"
            }

            override fun writeDocument() {
                events += "write-document"
            }

            override fun closeDocument() {
                events += "close-document"
            }
        }

        writeDocumentWithBackend(backend)

        assertEquals(
            listOf("start-page", "draw-report", "finish-page", "write-document", "close-document"),
            events,
        )
    }

    @Test
    fun productionLifecyclePreservesDrawFailureAndBothCleanupFailures() {
        val events = mutableListOf<String>()
        val drawFailure = IllegalStateException("draw failed")
        val finishFailure = IOException("finish failed")
        val closeFailure = SecurityException("close failed")
        val backend = object : ReportDocumentBackend {
            override fun startPage() {
                events += "start-page"
            }

            override fun drawReport() {
                events += "draw-report"
                throw drawFailure
            }

            override fun finishPage() {
                events += "finish-page"
                throw finishFailure
            }

            override fun writeDocument() {
                events += "write-document"
            }

            override fun closeDocument() {
                events += "close-document"
                throw closeFailure
            }
        }

        val thrown: IllegalStateException = assertThrows(IllegalStateException::class.java) {
            writeDocumentWithBackend(backend)
        }

        assertSame(drawFailure, thrown)
        assertEquals(
            listOf("start-page", "draw-report", "finish-page", "close-document"),
            events,
        )
        assertArrayEquals(arrayOf(finishFailure, closeFailure), thrown.suppressed)
    }

    @Test
    fun concurrentFirstExportsCreateAbsentDirectoryAndOwnDistinctFiles() {
        val directory = temporaryFolder.root.resolve("new-reports")
        assertFalse(directory.exists())
        val report = FormattedReport("Title", "Header", "Body")
        val creationBarrier = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)

        val futures = List(2) { index ->
            executor.submit<PdfExportResult> {
                PdfReportExporter.exportWithWriter(
                    outputDirectory = directory,
                    report = report,
                    now = { 789L },
                    beforeDirectoryCreation = {
                        creationBarrier.await(10, TimeUnit.SECONDS)
                    },
                ) { file, _ ->
                    file.writeText("report-$index")
                }
            }
        }

        val results = try {
            futures.map { it.get(15, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }

        assertTrue(results.all { it is PdfExportResult.Success })
        val files = results.map { (it as PdfExportResult.Success).file }
        assertNotEquals(files[0].canonicalPath, files[1].canonicalPath)
        assertTrue(files.all { it.isFile && it.length() > 0L })
        assertEquals(files.map { it.name }.toSet(), directory.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun sameTimestampExportsReserveDistinctFiles() {
        val directory = temporaryFolder.newFolder("reports")
        val report = FormattedReport("Title", "Header", "Body")

        val first = PdfReportExporter.exportWithWriter(directory, report, now = { 123L }) { file, _ ->
            file.writeText("first")
        }
        val second = PdfReportExporter.exportWithWriter(directory, report, now = { 123L }) { file, _ ->
            file.writeText("second")
        }

        assertTrue(first is PdfExportResult.Success)
        assertTrue(second is PdfExportResult.Success)
        val firstFile = (first as PdfExportResult.Success).file
        val secondFile = (second as PdfExportResult.Success).file
        assertNotEquals(firstFile.canonicalPath, secondFile.canonicalPath)
        assertEquals("first", firstFile.readText())
        assertEquals("second", secondFile.readText())
    }

    @Test
    fun failedLaterExportNeverDeletesTheEarlierValidReport() {
        val directory = temporaryFolder.newFolder("reports")
        val report = FormattedReport("Title", "Header", "Body")
        val first = PdfReportExporter.exportWithWriter(directory, report, now = { 456L }) { file, _ ->
            file.writeText("valid report")
        } as PdfExportResult.Success

        val failed = PdfReportExporter.exportWithWriter(directory, report, now = { 456L }) { _, _ ->
            throw IOException("write failed")
        }

        assertEquals(PdfExportResult.Failure(ReportError.CREATE_FILE), failed)
        assertTrue(first.file.isFile)
        assertEquals("valid report", first.file.readText())
        assertEquals(listOf(first.file.name), directory.listFiles()!!.map { it.name })
    }
}
