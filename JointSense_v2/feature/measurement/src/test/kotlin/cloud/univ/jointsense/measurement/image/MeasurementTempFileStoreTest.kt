package cloud.univ.jointsense.measurement.image

import androidx.lifecycle.SavedStateHandle
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

        val capture = store.createOrRestorePending()

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
        val first = firstStore.createOrRestorePending()

        val recreatedStore = store(
            cacheDir,
            SavedStatePendingCaptureState(savedStateHandle),
            "capture-b.jpg",
        )
        val restored = recreatedStore.createOrRestorePending()

        assertEquals(first, restored)
        assertTrue(first.file.exists())
        assertEquals(first, recreatedStore.pendingCapture)
    }

    @Test
    fun successfulPersistenceDeletesOwnedPendingFileAndClearsState() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val savedState = InMemoryPendingCaptureState()
        val store = store(cacheDir, savedState, "capture.jpg")
        val capture = store.createOrRestorePending()

        store.onPersistenceSucceeded()

        assertFalse(capture.file.exists())
        assertNull(store.pendingCapture)
        assertNull(store(cacheDir, savedState, "unused.jpg").pendingCapture)
    }

    @Test
    fun explicitCancellationDeletesOwnedPendingFileAndClearsState() {
        val cacheDir = temporaryFolder.newFolder("cache")
        val store = store(cacheDir, InMemoryPendingCaptureState(), "capture.jpg")
        val capture = store.createOrRestorePending()

        store.onFlowCancelled()

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

        store.onFlowCancelled()

        assertTrue(outside.exists())
        assertNull(store.pendingCapture)
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
