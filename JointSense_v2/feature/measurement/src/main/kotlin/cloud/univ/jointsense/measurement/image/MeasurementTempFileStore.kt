package cloud.univ.jointsense.measurement.image

import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import java.io.File
import java.io.IOException
import java.util.UUID

data class PendingMeasurementCapture(
    val uri: String,
    val file: File,
)

internal interface PendingCaptureState {
    fun read(key: String): String?

    fun write(key: String, value: String?)
}

class MeasurementTempFileStore internal constructor(
    cacheDir: File,
    private val pendingState: PendingCaptureState,
    private val uriFactory: (File) -> String,
    private val fileNameFactory: () -> String = { "measurement-${UUID.randomUUID()}.jpg" },
) {
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
            return PendingMeasurementCapture(uri = uri, file = file.canonicalFile)
        }

    fun createOrRestorePending(): PendingMeasurementCapture {
        pendingCapture?.let { return it }
        ensureMeasurementDirectory()
        val file = File(measurementDirectory, fileNameFactory()).canonicalFile
        require(isOwned(file)) { "Camera files must stay inside the measurement cache directory" }
        if (!file.createNewFile()) {
            throw IOException("Could not create measurement camera file")
        }

        val uri = try {
            uriFactory(file)
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        pendingState.write(PENDING_URI_KEY, uri)
        pendingState.write(PENDING_PATH_KEY, file.absolutePath)
        return PendingMeasurementCapture(uri = uri, file = file)
    }

    fun onPersistenceSucceeded() {
        deleteOwnedPendingAndClearState()
    }

    fun onFlowCancelled() {
        deleteOwnedPendingAndClearState()
    }

    private fun ensureMeasurementDirectory() {
        if (!measurementDirectory.isDirectory && !measurementDirectory.mkdirs()) {
            throw IOException("Could not create measurement cache directory")
        }
    }

    private fun deleteOwnedPendingAndClearState() {
        val path = pendingState.read(PENDING_PATH_KEY)
        val file = path?.let(::File)
        if (file != null && isOwned(file) && file.exists() && !file.delete()) {
            throw IOException("Could not delete measurement camera file")
        }
        clearState()
    }

    private fun isOwned(file: File): Boolean =
        file.canonicalFile.parentFile == measurementDirectory

    private fun clearState() {
        pendingState.write(PENDING_URI_KEY, null)
        pendingState.write(PENDING_PATH_KEY, null)
    }

    internal companion object {
        const val PENDING_URI_KEY = "measurement.pendingCameraUri"
        const val PENDING_PATH_KEY = "measurement.pendingCameraPath"
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
