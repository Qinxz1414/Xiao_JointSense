package cloud.univ.jointsense.measurement

sealed interface MeasurementAction {
    data class ImageSelected(val uri: String) : MeasurementAction

    data class PickedImageSelected(val uri: String) : MeasurementAction

    data object GallerySelectionStarted : MeasurementAction

    data object CameraCaptureRequested : MeasurementAction

    data class CameraCaptureCompleted(val success: Boolean) : MeasurementAction

    class CameraLaunchAcknowledged internal constructor(
        val claim: CameraLaunchClaim,
    ) : MeasurementAction

    class CameraLaunchFailed internal constructor(
        val claim: CameraLaunchClaim,
        val reason: String,
    ) : MeasurementAction

    data object CameraPermissionRequestStarted : MeasurementAction

    class CameraPermissionLaunchAcknowledged internal constructor(
        val claim: CameraPermissionLaunchClaim,
    ) : MeasurementAction

    class CameraPermissionLaunchFailed internal constructor(
        val claim: CameraPermissionLaunchClaim,
        val reason: String,
    ) : MeasurementAction

    data class CameraPermissionResult(
        val claim: CameraPermissionLaunchClaim,
        val granted: Boolean,
        val shouldShowRationale: Boolean,
    ) : MeasurementAction

    data class CropChanged(val bounds: CropBounds) : MeasurementAction

    data object CropConfirmed : MeasurementAction

    data object Analyze : MeasurementAction

    data object Retry : MeasurementAction

    data object CancelAnalysis : MeasurementAction

    data object BackToImageSelection : MeasurementAction

    data object BackToCrop : MeasurementAction

    data object ContinueMeasurement : MeasurementAction
}
