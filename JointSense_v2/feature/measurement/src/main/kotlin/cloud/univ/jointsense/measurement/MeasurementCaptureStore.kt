package cloud.univ.jointsense.measurement

import java.io.OutputStream

data class MeasurementCapture(
    val uri: String,
    val token: String,
)

sealed interface CaptureCleanupResult {
    data object Removed : CaptureCleanupResult
    data object NotCurrent : CaptureCleanupResult
    data class Failed(val reason: String) : CaptureCleanupResult
}

/** Owns app-private image inputs for the lifetime of one measurement draft. */
interface MeasurementCaptureStore {
    /** File-system work: call only from the injected IO boundary. */
    fun currentCapture(): MeasurementCapture?

    /** File-system work: call only from the injected IO boundary. */
    fun createOrRestorePending(): MeasurementCapture

    /** Copies an ephemeral provider input into an app-owned file on the IO boundary. */
    fun importOwned(write: (OutputStream) -> Unit): MeasurementCapture

    /** Removes only [expected]; a newer capture is never touched. */
    fun clearExpected(expected: MeasurementCapture): CaptureCleanupResult
}

data class MeasurementImageInput(
    val uri: String,
    val ownedCapture: MeasurementCapture?,
)

fun interface MeasurementPickedImageResolver {
    /** Permission/file work: call only from the injected IO boundary. */
    fun acquire(uri: String): MeasurementImageInput
}
