package cloud.univ.jointsense.analysis.calibration

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
    val concentration = text.trim().toFloatOrNull()
        ?: return ConcentrationParseResult.Invalid
    return if (concentration.isFinite() && concentration >= 0f) {
        ConcentrationParseResult.Valid(concentration)
    } else {
        ConcentrationParseResult.Invalid
    }
}
