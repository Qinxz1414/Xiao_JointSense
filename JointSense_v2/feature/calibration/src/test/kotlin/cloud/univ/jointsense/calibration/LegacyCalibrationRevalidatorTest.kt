package cloud.univ.jointsense.calibration

import cloud.univ.jointsense.analysis.calibration.CalibrationInput
import cloud.univ.jointsense.analysis.calibration.CalibrationValidator
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyCalibrationRevalidatorTest {
    @Test
    fun scansEachNeedsReviewRecordOncePromotingOnlyValidatorApprovedRecords() = runTest {
        val valid = calibration(
            factor = InflammationFactor.TNF_ALPHA,
            signals = listOf(10f, 12f, 15f, 18f, 22f, 28f, 36f, 46f, 58f),
        )
        val invalid = calibration(
            factor = InflammationFactor.IL6,
            signals = listOf(10f, 12f, 15f, 18f, 22f),
        )
        val alreadyActive = valid.copy(factor = InflammationFactor.IL1_BETA, status = CalibrationStatus.ACTIVE)
        val repository = RevalidationRepository(listOf(valid, invalid, alreadyActive))
        val validations = mutableListOf<List<CalibrationInput>>()
        val validator = CalibrationValidator()
        val revalidator = LegacyCalibrationRevalidator(repository) { inputs ->
            validations += inputs
            validator.validate(inputs)
        }

        revalidator.revalidateNeedsReview()

        assertEquals(2, validations.size)
        assertEquals(listOf(9, 5), validations.map { it.size })
        val promoted = repository.saved.single()
        assertEquals(InflammationFactor.TNF_ALPHA, promoted.factor)
        assertEquals(CalibrationStatus.ACTIVE, promoted.status)
        assertEquals(10f, promoted.knots.first().rawSignal)
        assertEquals(48f, promoted.knots.last().fittedSignal)
        assertTrue(repository.current.value.single { it.factor == InflammationFactor.IL6 }.status == CalibrationStatus.NEEDS_REVIEW)
    }

    private fun calibration(
        factor: InflammationFactor,
        signals: List<Float>,
    ): Calibration {
        val concentrations = FACTORY_LADDER.getValue(factor)
        return Calibration(
            factor = factor,
            createdAt = 123L,
            version = 1,
            status = CalibrationStatus.NEEDS_REVIEW,
            kitName = null,
            kitLot = null,
            knots = signals.mapIndexed { index, signal ->
                CalibrationKnot(
                    position = index,
                    concentration = concentrations[index],
                    rawSignal = signal,
                    netSignal = signal,
                    fittedSignal = signal,
                    isBlank = concentrations[index] == 0f,
                )
            },
        )
    }
}

private class RevalidationRepository(initial: List<Calibration>) : CalibrationRepository {
    val current = MutableStateFlow(initial)
    val saved = mutableListOf<Calibration>()

    override fun observeCalibrations(): Flow<List<Calibration>> = current

    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        MutableStateFlow(current.value.firstOrNull { it.factor == factor })

    override suspend fun save(calibration: Calibration) {
        saved += calibration
        current.value = current.value.map { existing ->
            if (existing.factor == calibration.factor) calibration else existing
        }
    }

    override suspend fun clearAll() {
        current.value = emptyList()
    }
}
