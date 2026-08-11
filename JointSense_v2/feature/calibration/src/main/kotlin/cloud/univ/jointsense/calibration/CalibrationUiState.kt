package cloud.univ.jointsense.calibration

import android.graphics.Bitmap
import cloud.univ.jointsense.analysis.calibration.CalibrationValidation
import cloud.univ.jointsense.domain.model.InflammationFactor

internal data class CalibrationUiState(
    val factor: InflammationFactor = InflammationFactor.TNF_ALPHA,
    val concentrationTexts: List<String> = emptyList(),
    val concentrationFieldErrors: Set<Int> = emptySet(),
    val signals: List<Float> = emptyList(),
    val validation: CalibrationValidation? = null,
    val imageUri: String? = null,
    val bitmap: Bitmap? = null,
    val cropBounds: CalibrationIntBounds? = null,
    val isDecoding: Boolean = false,
    val isDetecting: Boolean = false,
    val imageReadyToOpenCrop: Boolean = false,
    val signalsReadyToOpenAssign: Boolean = false,
    val isSaving: Boolean = false,
    val saveCompleted: Boolean = false,
    val savedFactor: InflammationFactor? = null,
    val isRestoringFactory: Boolean = false,
    val factoryRestoreCompleted: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSave: Boolean
        get() = validation is CalibrationValidation.Valid && !isSaving && !saveCompleted
}
