package cloud.univ.jointsense.measurement.image

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import cloud.univ.jointsense.measurement.MeasurementCapture
import cloud.univ.jointsense.measurement.MeasurementCaptureStore
import cloud.univ.jointsense.measurement.MeasurementImageInput
import cloud.univ.jointsense.measurement.MeasurementPickedImageResolver
import java.io.IOException

class DurablePickedImageResolver internal constructor(
    private val persistReadPermission: (String) -> Boolean,
    private val copyToOwned: (String) -> MeasurementCapture,
) : MeasurementPickedImageResolver {
    constructor(
        contentResolver: ContentResolver,
        captureStore: MeasurementCaptureStore,
    ) : this(
        persistReadPermission = { encodedUri ->
            try {
                contentResolver.takePersistableUriPermission(
                    Uri.parse(encodedUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                true
            } catch (_: SecurityException) {
                false
            } catch (_: UnsupportedOperationException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
        },
        copyToOwned = { encodedUri ->
            captureStore.importOwned { output ->
                val input = contentResolver.openInputStream(Uri.parse(encodedUri))
                    ?: throw IOException("Picked image cannot be opened")
                input.use { it.copyTo(output) }
            }
        },
    )

    override fun acquire(uri: String): MeasurementImageInput =
        if (persistReadPermission(uri)) {
            MeasurementImageInput(uri = uri, ownedCapture = null)
        } else {
            val owned = copyToOwned(uri)
            MeasurementImageInput(uri = owned.uri, ownedCapture = owned)
        }
}
