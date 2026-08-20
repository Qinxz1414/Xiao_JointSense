package cloud.univ.jointsense.measurement.image

import androidx.lifecycle.SavedStateHandle
import cloud.univ.jointsense.measurement.CaptureCleanupResult
import java.io.File
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MeasurementTempFileStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun createsCameraFileOnlyInsideMeasurementCacheDirectory() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val store = store(cacheDir, InMemoryPendingCaptureState(), "capture-a.jpg")

        val capture = store.createOrRestorePendingFile()

        assertEquals(
            File(cacheDir, "measurement").canonicalFile,
            requireNotNull(capture.file.parentFile).canonicalFile,
        )
        assertTrue(capture.file.exists())
        assertEquals("content", URI(capture.uri).scheme)
    }

    @Test
    fun retryAndStoreRecreationRetainTheSamePendingFileAndUri() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val savedStateHandle = SavedStateHandle()
        val firstStore = store(
            cacheDir,
            SavedStatePendingCaptureState(savedStateHandle),
            "capture-a.jpg",
        )
        val first = firstStore.createOrRestorePendingFile()

        val recreatedStore = store(
            cacheDir,
            SavedStatePendingCaptureState(savedStateHandle),
            "capture-b.jpg",
        )
        val restored = recreatedStore.createOrRestorePendingFile()

        assertEquals(first, restored)
        assertTrue(first.file.exists())
        assertEquals(first, recreatedStore.pendingCapture)
    }

    @Test
    fun importedPickerFallbackSurvivesStoreRecreationWithItsBytesAndOwnershipToken() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val savedStateHandle = SavedStateHandle()
        val firstStore = store(
            cacheDir,
            SavedStatePendingCaptureState(savedStateHandle),
            "picked-fallback.jpg",
        )

        val imported = firstStore.importOwned { output -> output.write(byteArrayOf(4, 2, 1)) }
        val recreatedStore = store(
            cacheDir,
            SavedStatePendingCaptureState(savedStateHandle),
            "unused.jpg",
        )

        assertEquals(imported, recreatedStore.currentCapture())
        assertEquals(listOf<Byte>(4, 2, 1), requireNotNull(recreatedStore.pendingCapture).file.readBytes().toList())
    }

    @Test
    fun successfulPersistenceDeletesOwnedPendingFileAndClearsState() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val savedState = InMemoryPendingCaptureState()
        val store = store(cacheDir, savedState, "capture.jpg")
        val capture = store.createOrRestorePendingFile()

        assertEquals(CaptureCleanupResult.Removed, store.clearExpected(capture.capture))

        assertFalse(capture.file.exists())
        assertNull(store.pendingCapture)
        assertNull(store(cacheDir, savedState, "unused.jpg").pendingCapture)
    }

    @Test
    fun explicitCancellationDeletesOwnedPendingFileAndClearsState() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val store = store(cacheDir, InMemoryPendingCaptureState(), "capture.jpg")
        val capture = store.createOrRestorePendingFile()

        assertEquals(CaptureCleanupResult.Removed, store.clearExpected(capture.capture))

        assertFalse(capture.file.exists())
        assertNull(store.pendingCapture)
    }

    @Test
    fun cleanupNeverDeletesFileOutsideOwnedMeasurementDirectory() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val outside = temporaryFolder.newFile("outside.jpg")
        val savedState = InMemoryPendingCaptureState().apply {
            write(MeasurementTempFileStore.PENDING_URI_KEY, "content://test/outside.jpg")
            write(MeasurementTempFileStore.PENDING_PATH_KEY, outside.absolutePath)
        }
        val store = store(cacheDir, savedState, "capture.jpg")

        assertEquals(
            CaptureCleanupResult.NotCurrent,
            store.clearExpected(cloud.univ.jointsense.measurement.MeasurementCapture(
                "content://test/outside.jpg",
                "outside",
            )),
        )

        assertTrue(outside.exists())
        assertNull(store.pendingCapture)
    }

    @Test
    fun cleanupForOldCaptureDoesNotDeleteOrClearNewCapture() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val savedState = InMemoryPendingCaptureState()
        val store = store(cacheDir, savedState, "capture-a.jpg")
        val old = store.createOrRestorePendingFile().capture
        val newFile = File(cacheDir, "measurement/capture-b.jpg").apply { createNewFile() }
        savedState.write(MeasurementTempFileStore.PENDING_URI_KEY, "content://test/capture-b.jpg")
        savedState.write(MeasurementTempFileStore.PENDING_PATH_KEY, newFile.absolutePath)
        savedState.write(MeasurementTempFileStore.PENDING_TOKEN_KEY, "capture-b")

        val outcome = store.clearExpected(old)

        assertEquals(CaptureCleanupResult.NotCurrent, outcome)
        assertTrue(newFile.exists())
        assertEquals("capture-b", requireNotNull(store.currentCapture()).token)
    }

    private fun store(
        cacheDir: File,
        pendingState: PendingCaptureState,
        fileName: String,
    ) = MeasurementTempFileStore(
        cacheDir = cacheDir,
        pendingState = pendingState,
        uriFactory = { file -> "content://test/${file.name}" },
        fileNameFactory = { fileName },
    )
}

private class InMemoryPendingCaptureState : PendingCaptureState {
    private val values = mutableMapOf<String, String>()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String?) {
        if (value == null) {
            values.remove(key)
        } else {
            values[key] = value
        }
    }
}
