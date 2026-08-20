package cloud.univ.jointsense.analysis.calibration

import java.math.BigDecimal

data class CalibrationInput(
    val wellIndex: Int,
    val concentration: Float,
    val rawSignal: Float,
)

data class CalibrationKnot(
    val wellIndex: Int,
    val concentration: Float,
    val rawSignal: Float,
    val netSignal: Float,
    val fittedSignal: Float,
)

sealed interface ConcentrationParseResult {
    data class Valid(val concentration: Float) : ConcentrationParseResult
    data object Invalid : ConcentrationParseResult
}

fun parseConcentration(text: String): ConcentrationParseResult {
    val exact = try {
        BigDecimal(text.trim())
    } catch (_: NumberFormatException) {
        return ConcentrationParseResult.Invalid
    }
    if (exact.signum() < 0) {
        return ConcentrationParseResult.Invalid
    }
    if (exact.signum() == 0) {
        return ConcentrationParseResult.Valid(0f)
    }

    val concentration = exact.toFloat()
    return if (concentration.isFinite() && concentration != 0f) {
        ConcentrationParseResult.Valid(concentration)
    } else {
        ConcentrationParseResult.Invalid
    }
}
