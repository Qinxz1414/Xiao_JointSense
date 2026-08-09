package cloud.univ.jointsense.analysis.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationValidatorTest {
    private val validator = CalibrationValidator()

    @Test
    fun rejectsEightReadingsWithOnlyTheCountError() {
        assertInvalid(
            validator.validate(validInputs().take(8)),
            listOf(CalibrationError.WrongReadingCount),
        )
    }

    @Test
    fun rejectsTenReadingsWithOnlyTheCountError() {
        val tenInputs = validInputs() + CalibrationInput(wellIndex = 9, concentration = 9f, rawSignal = 19f)

        assertInvalid(
            validator.validate(tenInputs),
            listOf(CalibrationError.WrongReadingCount),
        )
    }

    @Test
    fun rejectsMissingBlankInsteadOfUsingFirstWell() {
        val withoutBlank = List(9) { index ->
            CalibrationInput(
                wellIndex = index,
                concentration = (index + 1).toFloat(),
                rawSignal = (index + 10).toFloat(),
            )
        }

        assertInvalid(
            validator.validate(withoutBlank),
            listOf(CalibrationError.MissingBlank),
        )
    }

    @Test
    fun rejectsMultipleBlanks() {
        val inputs = validInputs().replaceAt(
            1,
            CalibrationInput(wellIndex = 1, concentration = 0f, rawSignal = 11f),
        )

        assertInvalid(
            validator.validate(inputs),
            listOf(CalibrationError.MultipleBlanks),
        )
    }

    @Test
    fun rejectsEveryNegativeOrNonFiniteConcentration() {
        listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { concentration ->
            val inputs = validInputs().replaceAt(
                4,
                CalibrationInput(wellIndex = 4, concentration = concentration, rawSignal = 14f),
            )

            assertInvalid(
                validator.validate(inputs),
                listOf(CalibrationError.InvalidConcentration),
            )
        }
    }

    @Test
    fun rejectsDuplicateNonBlankConcentration() {
        val inputs = validInputs().replaceAt(
            4,
            CalibrationInput(wellIndex = 4, concentration = 3f, rawSignal = 14f),
        )

        assertInvalid(
            validator.validate(inputs),
            listOf(CalibrationError.DuplicateNonBlankConcentration),
        )
    }

    @Test
    fun rejectsEveryNonFiniteRawSignal() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { signal ->
            val inputs = validInputs().replaceAt(
                4,
                CalibrationInput(wellIndex = 4, concentration = 4f, rawSignal = signal),
            )

            assertInvalid(
                validator.validate(inputs),
                listOf(CalibrationError.NonFiniteSignal),
            )
        }
    }

    @Test
    fun returnsEveryIndependentStructuralErrorInStableOrder() {
        val malformed = validInputs().drop(1).mapIndexed { index, input ->
            when (index) {
                0 -> input.copy(concentration = -1f)
                1 -> input.copy(concentration = 3f)
                2 -> input.copy(rawSignal = Float.NaN)
                else -> input
            }
        }

        assertInvalid(
            validator.validate(malformed),
            listOf(
                CalibrationError.WrongReadingCount,
                CalibrationError.InvalidConcentration,
                CalibrationError.MissingBlank,
                CalibrationError.DuplicateNonBlankConcentration,
                CalibrationError.NonFiniteSignal,
            ),
        )
    }

    @Test
    fun rejectsDynamicRangeImmediatelyBelowEight() {
        val signals = List(9) { index -> index * (7.999f / 8f) }

        assertInvalid(
            validator.validate(inputsWithSignals(signals)),
            listOf(CalibrationError.DynamicRangeTooLow),
        )
    }

    @Test
    fun acceptsDynamicRangeOfExactlyEight() {
        val result = assertValid(validator.validate(inputsWithSignals(List(9) { it.toFloat() })))

        assertEquals(9, result.knots.size)
    }

    @Test
    fun rejectsPositiveDerivedOverflowFromFiniteFloatInputs() {
        val inputs = inputsWithSignals(
            listOf(-Float.MAX_VALUE) + List(8) { Float.MAX_VALUE },
        )

        assertInvalid(
            validator.validate(inputs),
            listOf(CalibrationError.NonFiniteSignal),
        )
    }

    @Test
    fun rejectsNegativeDerivedOverflowFromFiniteFloatInputs() {
        val inputs = inputsWithSignals(
            listOf(Float.MAX_VALUE) + List(8) { -Float.MAX_VALUE },
        )

        assertInvalid(
            validator.validate(inputs),
            listOf(CalibrationError.NonFiniteSignal),
        )
    }

    @Test
    fun pavaPoolsAdjacentViolationAndExpandsMeanToBothPositions() {
        assertEquals(
            listOf(1f, 2.5f, 2.5f, 5f),
            IsotonicRegression.fit(listOf(1f, 3f, 2f, 5f)),
        )
    }

    @Test
    fun pavaCascadesPoolsAcrossThreeDescendingPositions() {
        assertEquals(
            listOf(2f, 2f, 2f),
            IsotonicRegression.fit(listOf(3f, 2f, 1f)),
        )
    }

    @Test
    fun acceptsPavaAdjustmentExactlyAtThreeSignalFloorTolerance() {
        val result = assertValid(
            validator.validate(
                inputsWithSignals(listOf(0f, 6f, 0f, 8f, 9f, 10f, 11f, 12f, 13f)),
            ),
        )

        assertEquals(
            listOf(0f, 3f, 3f, 8f, 9f, 10f, 11f, 12f, 13f),
            result.knots.map(CalibrationKnot::fittedSignal),
        )
    }

    @Test
    fun rejectsPavaAdjustmentImmediatelyAboveThreeSignalFloorTolerance() {
        assertInvalid(
            validator.validate(
                inputsWithSignals(listOf(0f, 6.002f, -0.002f, 8f, 9f, 10f, 11f, 12f, 13f)),
            ),
            listOf(CalibrationError.NonMonotonicBeyondTolerance),
        )
    }

    @Test
    fun acceptsPavaAdjustmentExactlyAtFifteenPercentTolerance() {
        val result = assertValid(
            validator.validate(
                inputsWithSignals(listOf(0f, 45f, 15f, 50f, 60f, 70f, 80f, 90f, 100f)),
            ),
        )

        assertEquals(
            listOf(0f, 30f, 30f, 50f, 60f, 70f, 80f, 90f, 100f),
            result.knots.map(CalibrationKnot::fittedSignal),
        )
    }

    @Test
    fun rejectsPavaAdjustmentAboveFifteenPercentTolerance() {
        assertInvalid(
            validator.validate(
                inputsWithSignals(listOf(0f, 45.2f, 14.8f, 50f, 60f, 70f, 80f, 90f, 100f)),
            ),
            listOf(CalibrationError.NonMonotonicBeyondTolerance),
        )
    }

    @Test
    fun subtractsBlankAndReturnsCompleteKnotsSortedByConcentration() {
        val unsorted = listOf(
            CalibrationInput(wellIndex = 0, concentration = 40f, rawSignal = 15f),
            CalibrationInput(wellIndex = 1, concentration = 0f, rawSignal = 5f),
            CalibrationInput(wellIndex = 2, concentration = 20f, rawSignal = 10f),
            CalibrationInput(wellIndex = 3, concentration = 10f, rawSignal = 7f),
            CalibrationInput(wellIndex = 4, concentration = 80f, rawSignal = 25f),
            CalibrationInput(wellIndex = 5, concentration = 70f, rawSignal = 23f),
            CalibrationInput(wellIndex = 6, concentration = 60f, rawSignal = 21f),
            CalibrationInput(wellIndex = 7, concentration = 50f, rawSignal = 19f),
            CalibrationInput(wellIndex = 8, concentration = 30f, rawSignal = 13f),
        )

        val result = assertValid(validator.validate(unsorted))

        assertEquals(
            listOf(
                CalibrationKnot(1, 0f, 5f, 0f, 0f),
                CalibrationKnot(3, 10f, 7f, 2f, 2f),
                CalibrationKnot(2, 20f, 10f, 5f, 5f),
                CalibrationKnot(8, 30f, 13f, 8f, 8f),
                CalibrationKnot(0, 40f, 15f, 10f, 10f),
                CalibrationKnot(7, 50f, 19f, 14f, 14f),
                CalibrationKnot(6, 60f, 21f, 16f, 16f),
                CalibrationKnot(5, 70f, 23f, 18f, 18f),
                CalibrationKnot(4, 80f, 25f, 20f, 20f),
            ),
            result.knots,
        )
    }

    @Test
    fun parsesTrimmedFiniteNonNegativeConcentration() {
        assertEquals(ConcentrationParseResult.Valid(12.5f), parseConcentration("  12.5  "))
        assertEquals(ConcentrationParseResult.Valid(0f), parseConcentration("0"))
    }

    @Test
    fun rejectsPositiveNonZeroDecimalThatUnderflowsFloat() {
        assertEquals(ConcentrationParseResult.Invalid, parseConcentration("1e-50"))
    }

    @Test
    fun rejectsNegativeNonZeroDecimalThatUnderflowsFloat() {
        assertEquals(ConcentrationParseResult.Invalid, parseConcentration("-1e-50"))
    }

    @Test
    fun normalizesNegativeExactZeroToPositiveFloatZero() {
        val result = parseConcentration("-0.0")

        assertEquals(ConcentrationParseResult.Valid(0f), result)
        assertEquals(
            java.lang.Float.floatToRawIntBits(0f),
            java.lang.Float.floatToRawIntBits((result as ConcentrationParseResult.Valid).concentration),
        )
    }

    @Test
    fun parsesNormalScientificNotation() {
        assertEquals(ConcentrationParseResult.Valid(125f), parseConcentration("1.25e2"))
    }

    @Test
    fun rejectsOverflowingDecimalExponent() {
        assertEquals(ConcentrationParseResult.Invalid, parseConcentration("1e1000"))
    }

    @Test
    fun rejectsBlankMalformedNegativeAndNonFiniteConcentrationText() {
        listOf("", "   ", "abc", "-1", "NaN", "Infinity", "-Infinity").forEach { text ->
            assertEquals(text, ConcentrationParseResult.Invalid, parseConcentration(text))
        }
    }

    private fun validInputs(): List<CalibrationInput> = List(9) { index ->
        CalibrationInput(
            wellIndex = index,
            concentration = index.toFloat(),
            rawSignal = (index + 10).toFloat(),
        )
    }

    private fun inputsWithSignals(signals: List<Float>): List<CalibrationInput> = signals.mapIndexed { index, signal ->
        CalibrationInput(
            wellIndex = index,
            concentration = index.toFloat(),
            rawSignal = signal,
        )
    }

    private fun List<CalibrationInput>.replaceAt(
        index: Int,
        replacement: CalibrationInput,
    ): List<CalibrationInput> = mapIndexed { position, input ->
        if (position == index) replacement else input
    }

    private fun assertInvalid(
        validation: CalibrationValidation,
        expectedErrors: List<CalibrationError>,
    ) {
        assertEquals(CalibrationValidation.Invalid(expectedErrors), validation)
    }

    private fun assertValid(validation: CalibrationValidation): CalibrationValidation.Valid {
        assertTrue(validation is CalibrationValidation.Valid)
        return (validation as CalibrationValidation.Valid).also { valid ->
            valid.knots.forEach { knot ->
                assertTrue("raw signal must be finite: $knot", knot.rawSignal.isFinite())
                assertTrue("net signal must be finite: $knot", knot.netSignal.isFinite())
                assertTrue("fitted signal must be finite: $knot", knot.fittedSignal.isFinite())
            }
        }
    }
}
