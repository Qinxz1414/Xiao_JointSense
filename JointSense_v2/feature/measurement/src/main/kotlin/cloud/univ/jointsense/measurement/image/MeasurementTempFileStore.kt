package cloud.univ.jointsense.measurement.image

import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import cloud.univ.jointsense.measurement.CaptureCleanupResult
import cloud.univ.jointsense.measurement.MeasurementCapture
import cloud.univ.jointsense.measurement.MeasurementCaptureStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

data class PendingMeasurementCapture(
    val capture: MeasurementCapture,
    val file: File,
) {
    val uri: String get() = capture.uri
}

internal interface PendingCaptureState {
    fun read(key: String): String?

    fun write(key: String, value: String?)
}

class MeasurementTempFileStore internal constructor(
    cacheDir: File,
    private val pendingState: PendingCaptureState,
    private val uriFactory: (File) -> String,
    private val fileNameFactory: () -> String = { "measurement-${UUID.randomUUID()}.jpg" },
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() },
) : MeasurementCaptureStore {
    constructor(
        context: Context,
        savedStateHandle: SavedStateHandle,
        authority: String = "${context.packageName}.fileprovider",
    ) : this(
        cacheDir = context.cacheDir,
        pendingState = SavedStatePendingCaptureState(savedStateHandle),
        uriFactory = { file -> FileProvider.getUriForFile(context, authority, file).toString() },
    )

    private val measurementDirectory = File(cacheDir, DIRECTORY_NAME).canonicalFile

    val pendingCapture: PendingMeasurementCapture?
        get() {
            val uri = pendingState.read(PENDING_URI_KEY)
            val path = pendingState.read(PENDING_PATH_KEY)
            if (uri == null || path == null) {
                clearState()
                return null
            }
            val file = File(path)
            if (!isOwned(file) || !file.exists()) {
                clearState()
                return null
            }
            val token = pendingState.read(PENDING_TOKEN_KEY)
                ?: tokenFactory().also { pendingState.write(PENDING_TOKEN_KEY, it) }
            return PendingMeasurementCapture(
                capture = MeasurementCapture(uri = uri, token = token),
                file = file.canonicalFile,
            )
        }

    override fun currentCapture(): MeasurementCapture? = pendingCapture?.capture

    override fun createOrRestorePending(): MeasurementCapture =
        createOrRestorePendingFile().capture

    internal fun createOrRestorePendingFile(): PendingMeasurementCapture {
        pendingCapture?.let { return it }
        return createOwnedFile { }
    }

    override fun importOwned(write: (OutputStream) -> Unit): MeasurementCapture =
        createOwnedFile(write).capture

    override fun clearExpected(expected: MeasurementCapture): CaptureCleanupResult {
        val current = try {
            pendingCapture
        } catch (error: Exception) {
            return CaptureCleanupResult.Failed(error.message ?: error::class.java.simpleName)
        } ?: return CaptureCleanupResult.NotCurrent
        if (current.capture != expected) return CaptureCleanupResult.NotCurrent
        return try {
            if (current.file.exists() && !current.file.delete()) {
                CaptureCleanupResult.Failed("Could not delete measurement camera file")
            } else {
                clearState()
                CaptureCleanupResult.Removed
            }
        } catch (error: Exception) {
            CaptureCleanupResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private fun createOwnedFile(write: (OutputStream) -> Unit): PendingMeasurementCapture {
        check(pendingCapture == null) { "An owned measurement input is already pending" }
        ensureMeasurementDirectory()
        val file = File(measurementDirectory, fileNameFactory()).canonicalFile
        require(isOwned(file)) { "Measurement files must stay inside the measurement cache directory" }
        if (!file.createNewFile()) throw IOException("Could not create measurement input file")
        val capture = try {
            FileOutputStream(file).use(write)
            MeasurementCapture(uriFactory(file), tokenFactory())
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        pendingState.write(PENDING_URI_KEY, capture.uri)
        pendingState.write(PENDING_PATH_KEY, file.absolutePath)
        pendingState.write(PENDING_TOKEN_KEY, capture.token)
        return PendingMeasurementCapture(capture, file)
    }

    private fun ensureMeasurementDirectory() {
        if (!measurementDirectory.isDirectory && !measurementDirectory.mkdirs()) {
            throw IOException("Could not create measurement cache directory")
        }
    }

    private fun isOwned(file: File): Boolean =
        file.canonicalFile.parentFile == measurementDirectory

    private fun clearState() {
        pendingState.write(PENDING_URI_KEY, null)
        pendingState.write(PENDING_PATH_KEY, null)
        pendingState.write(PENDING_TOKEN_KEY, null)
    }

    internal companion object {
        const val PENDING_URI_KEY = "measurement.pendingCameraUri"
        const val PENDING_PATH_KEY = "measurement.pendingCameraPath"
        const val PENDING_TOKEN_KEY = "measurement.pendingCameraToken"
        private const val DIRECTORY_NAME = "measurement"
    }
}

internal class SavedStatePendingCaptureState(
    private val savedStateHandle: SavedStateHandle,
) : PendingCaptureState {
    override fun read(key: String): String? = savedStateHandle[key]

    override fun write(key: String, value: String?) {
        savedStateHandle[key] = value
    }
}
