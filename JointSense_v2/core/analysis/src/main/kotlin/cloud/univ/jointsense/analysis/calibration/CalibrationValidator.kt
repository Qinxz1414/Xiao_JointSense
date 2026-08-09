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

        val blankSignal = inputs.single { it.concentration == BLANK_CONCENTRATION }.rawSignal
        val sortedKnots = inputs
            .sortedBy(CalibrationInput::concentration)
            .map { input ->
                NetKnot(
                    input = input,
                    netSignal = input.rawSignal - blankSignal,
                )
            }
        val netSignals = sortedKnots.map(NetKnot::netSignal)
        val netDynamicRange = netSignals.maxOrNull()!! - netSignals.minOrNull()!!
        if (netDynamicRange < MINIMUM_DYNAMIC_RANGE) {
            return CalibrationValidation.Invalid(listOf(CalibrationError.DynamicRangeTooLow))
        }

        val fittedSignals = IsotonicRegression.fit(netSignals)
        val rawDynamicRange = inputs.maxOf(CalibrationInput::rawSignal) -
            inputs.minOf(CalibrationInput::rawSignal)
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
                    netSignal = knot.netSignal,
                    fittedSignal = fittedSignals[index],
                )
            },
        )
    }

    private data class NetKnot(
        val input: CalibrationInput,
        val netSignal: Float,
    )

    private companion object {
        const val REQUIRED_READING_COUNT = 9
        const val BLANK_CONCENTRATION = 0f
        const val MINIMUM_DYNAMIC_RANGE = 8f
        const val ABSOLUTE_CORRECTION_TOLERANCE = 3f
        const val RELATIVE_CORRECTION_TOLERANCE = 0.15f
    }
}
