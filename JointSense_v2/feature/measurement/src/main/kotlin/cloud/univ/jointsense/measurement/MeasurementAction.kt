package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.InflammationFactor

sealed interface MeasurementAction {
    data class ImageSelected(val uri: String) : MeasurementAction

    data class CropChanged(val bounds: CropBounds) : MeasurementAction

    data object CropConfirmed : MeasurementAction

    data class FactorSelected(val factor: InflammationFactor) : MeasurementAction

    data object Analyze : MeasurementAction

    data object Retry : MeasurementAction

    data object CancelAnalysis : MeasurementAction

    data object ContinueMeasurement : MeasurementAction
}
