package cloud.univ.jointsense.measurement

import android.content.Context
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** Process-persistent permission request truth. Call only from the injected IO boundary. */
interface CameraPermissionHistoryStore {
    fun wasRequested(): Boolean

    /** Returns only after the request marker is durable. */
    fun markRequested()
}

class ApplicationCameraPermissionHistoryStore(
    context: Context,
) : CameraPermissionHistoryStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun wasRequested(): Boolean = preferences.getBoolean(KEY_REQUESTED, false)

    override fun markRequested() {
        if (!preferences.edit().putBoolean(KEY_REQUESTED, true).commit()) {
            throw IOException("Could not persist camera permission request history")
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "measurement_permission_history"
        const val KEY_REQUESTED = "camera_requested"
    }
}

internal class VolatileCameraPermissionHistoryStore : CameraPermissionHistoryStore {
    private val requested = AtomicBoolean(false)

    override fun wasRequested(): Boolean = requested.get()

    override fun markRequested() {
        requested.set(true)
    }
}
