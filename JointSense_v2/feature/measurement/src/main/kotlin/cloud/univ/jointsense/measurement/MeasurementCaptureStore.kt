package cloud.univ.jointsense.measurement

/** Owns a camera output URI for the lifetime of one measurement draft. */
interface MeasurementCaptureStore {
    val pendingCaptureUri: String?

    fun createOrRestorePendingUri(): String

    fun onPersistenceSucceeded()

    fun onFlowCancelled()
}
