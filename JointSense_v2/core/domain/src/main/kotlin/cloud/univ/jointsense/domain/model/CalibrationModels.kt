package cloud.univ.jointsense.domain.model

enum class CalibrationStatus { ACTIVE, NEEDS_REVIEW }

/** Versioned color-signal domain used by measurements and their calibration curves. */
enum class ColorSignalMethod { LEGACY_MEAN_BR, PIXEL_BR_P90_V1 }

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
    val signalMethod: ColorSignalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
)
