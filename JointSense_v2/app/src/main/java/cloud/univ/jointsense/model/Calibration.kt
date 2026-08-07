package cloud.univ.jointsense.model

import cloud.univ.jointsense.data.InflammationFactor

/**
 * A single (concentration, signal) calibration point.
 * [conc] is the known standard concentration in pg/mL; [signal] is the
 * background-corrected tealness (B − R) measured from the well.
 */
data class CalibrationKnot(val conc: Float, val signal: Float)

/**
 * Per-factor standard-curve calibration captured by the user through the
 * guided calibration flow. While present it replaces the built-in factory
 * knots in [StandardCurve] (factor by factor).
 */
data class Calibration(
    val factors: Map<InflammationFactor, List<CalibrationKnot>>,
    val createdAt: Long
)

/**
 * Factory standard ladder concentrations (pg/mL) for each factor, in the
 * reading order used by the calibration grid (row-major, 3×3). Used to
 * pre-fill the well concentration table so the user only corrects outliers.
 */
val FACTORY_LADDER: Map<InflammationFactor, List<Float>> = mapOf(
    InflammationFactor.TNF_ALPHA to listOf(0f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f),
    InflammationFactor.IL6 to listOf(0f, 5f, 10f, 20f, 50f, 100f, 200f, 500f, 1000f),
    InflammationFactor.IL1_BETA to listOf(0f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f)
)
