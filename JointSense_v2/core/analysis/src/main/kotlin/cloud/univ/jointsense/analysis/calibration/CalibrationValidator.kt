package cloud.univ.jointsense.analysis.calibration

import kotlin.math.abs

class CalibrationValidator {
    fun validate(inputs: List<CalibrationInput>): CalibrationValidation {
        val structuralErrors = buildList {
            if (inputs.size != REQUIRED_READING_COUNT) {
                add(CalibrationError.WrongReadingCount)
            }
            if (inputs.any { !it.concentration.isFinite() || it.concentration < 0f }) {
                add(CalibrationError.InvalidConcentration)
            }

            when (inputs.count { it.concentration == BLANK_CONCENTRATION }) {
                0 -> add(CalibrationError.MissingBlank)
                1 -> Unit
                else -> add(CalibrationError.MultipleBlanks)
            }

            val validNonBlankConcentrations = inputs
                .map(CalibrationInput::concentration)
                .filter { it.isFinite() && it > BLANK_CONCENTRATION }
            if (validNonBlankConcentrations.size != validNonBlankConcentrations.distinct().size) {
                add(CalibrationError.DuplicateNonBlankConcentration)
            }
            if (inputs.any { !it.rawSignal.isFinite() }) {
                add(CalibrationError.NonFiniteSignal)
            }
        }
        if (structuralErrors.isNotEmpty()) {
            return CalibrationValidation.Invalid(structuralErrors)
        }

        val blankSignal = inputs
            .single { it.concentration == BLANK_CONCENTRATION }
            .rawSignal
            .toDouble()
        val sortedKnots = inputs
            .sortedBy(CalibrationInput::concentration)
            .map { input ->
                NetKnot(
                    input = input,
                    netSignal = input.rawSignal.toDouble() - blankSignal,
                )
            }
        val netSignals = sortedKnots.map(NetKnot::netSignal)
        if (netSignals.any { !it.isRepresentableAsFiniteFloat() }) {
            return CalibrationValidation.Invalid(listOf(CalibrationError.NonFiniteSignal))
        }
        val netDynamicRange = netSignals.maxOrNull()!! - netSignals.minOrNull()!!
        if (netDynamicRange < MINIMUM_DYNAMIC_RANGE) {
            return CalibrationValidation.Invalid(listOf(CalibrationError.DynamicRangeTooLow))
        }

        val fittedSignals = IsotonicRegression.fitDoubles(netSignals)
        if (fittedSignals.any { !it.isRepresentableAsFiniteFloat() }) {
            return CalibrationValidation.Invalid(listOf(CalibrationError.NonFiniteSignal))
        }
        val rawDynamicRange = inputs.maxOf { it.rawSignal.toDouble() } -
            inputs.minOf { it.rawSignal.toDouble() }
        val tolerance = maxOf(ABSOLUTE_CORRECTION_TOLERANCE, rawDynamicRange * RELATIVE_CORRECTION_TOLERANCE)
        if (fittedSignals.indices.any { index ->
                abs(fittedSignals[index] - netSignals[index]) > tolerance
            }
        ) {
            return CalibrationValidation.Invalid(listOf(CalibrationError.NonMonotonicBeyondTolerance))
        }

        return CalibrationValidation.Valid(
            sortedKnots.mapIndexed { index, knot ->
                CalibrationKnot(
                    wellIndex = knot.input.wellIndex,
                    concentration = knot.input.concentration,
                    rawSignal = knot.input.rawSignal,
                    netSignal = knot.netSignal.toFloat(),
                    fittedSignal = fittedSignals[index].toFloat(),
                )
            },
        )
    }

    private data class NetKnot(
        val input: CalibrationInput,
        val netSignal: Double,
    )

    private fun Double.isRepresentableAsFiniteFloat(): Boolean {
        if (!isFinite()) return false
        val converted = toFloat()
        return converted.isFinite() && (this == 0.0 || converted != 0f)
    }

    private companion object {
        const val REQUIRED_READING_COUNT = 9
        const val BLANK_CONCENTRATION = 0f
        const val MINIMUM_DYNAMIC_RANGE = 8.0
        const val ABSOLUTE_CORRECTION_TOLERANCE = 3.0
        const val RELATIVE_CORRECTION_TOLERANCE = 0.15
    }
}
