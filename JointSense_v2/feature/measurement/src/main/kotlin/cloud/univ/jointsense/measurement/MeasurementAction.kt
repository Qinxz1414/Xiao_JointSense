package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.InflammationFactor

sealed interface MeasurementAction {
    data class ImageSelected(val uri: String) : MeasurementAction

    data class PickedImageSelected(val uri: String) : MeasurementAction

    data object CameraCaptureRequested : MeasurementAction

    data class CameraCaptureCompleted(val success: Boolean) : MeasurementAction

    data object CameraPermissionRequestStarted : MeasurementAction

    data class CameraPermissionResult(
        val granted: Boolean,
        val shouldShowRationale: Boolean,
    ) : MeasurementAction

    data class CropChanged(val bounds: CropBounds) : MeasurementAction

    data object CropConfirmed : MeasurementAction

    data class FactorSelected(val factor: InflammationFactor) : MeasurementAction

    data object Analyze : MeasurementAction

    data object Retry : MeasurementAction

    data object CancelAnalysis : MeasurementAction

    data object BackToImageSelection : MeasurementAction

    data object BackToCrop : MeasurementAction

    data object ContinueMeasurement : MeasurementAction
}
