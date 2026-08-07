package cloud.univ.jointsense.domain.model

enum class CalibrationStatus { ACTIVE, NEEDS_REVIEW }

data class CalibrationKnot(
    val position: Int,
    val concentration: Float,
    val rawSignal: Float,
    val netSignal: Float,
    val fittedSignal: Float,
    val isBlank: Boolean,
)

data class Calibration(
    val factor: InflammationFactor,
    val createdAt: Long,
    val version: Int,
    val status: CalibrationStatus,
    val kitName: String?,
    val kitLot: String?,
    val knots: List<CalibrationKnot>,
)
