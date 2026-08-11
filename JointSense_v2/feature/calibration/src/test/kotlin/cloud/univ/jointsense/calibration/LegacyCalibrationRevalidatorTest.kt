package cloud.univ.jointsense.calibration

import cloud.univ.jointsense.analysis.calibration.CalibrationInput
import cloud.univ.jointsense.analysis.calibration.CalibrationValidator
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.CancellationException
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

        val summary = revalidator.revalidateNeedsReview()

        assertEquals(2, validations.size)
        assertEquals(listOf(9, 5), validations.map { it.size })
        val promoted = repository.saved.single()
        assertEquals(InflammationFactor.TNF_ALPHA, promoted.factor)
        assertEquals(CalibrationStatus.ACTIVE, promoted.status)
        assertEquals(10f, promoted.knots.first().rawSignal)
        assertEquals(48f, promoted.knots.last().fittedSignal)
        assertTrue(repository.current.value.single { it.factor == InflammationFactor.IL6 }.status == CalibrationStatus.NEEDS_REVIEW)
        assertEquals(2, summary.attempted)
        assertEquals(1, summary.promoted)
        assertEquals(1, summary.retained)
        assertTrue(summary.failures.isEmpty())
    }

    @Test
    fun sameInvalidRecordIsAttemptedOnlyOnceAcrossRepeatedGraphRuns() = runTest {
        val invalid = calibration(InflammationFactor.IL6, listOf(10f, 12f, 15f))
        val repository = RevalidationRepository(listOf(invalid))
        var validatorCalls = 0
        val validator = CalibrationValidator()
        val revalidator = LegacyCalibrationRevalidator(repository) { inputs ->
            validatorCalls += 1
            validator.validate(inputs)
        }

        val first = revalidator.revalidateNeedsReview()
        val second = revalidator.revalidateNeedsReview()

        assertEquals(1, validatorCalls)
        assertEquals(1, first.attempted)
        assertEquals(0, second.attempted)
    }

    @Test
    fun validatorFailureIsReportedAndDoesNotAbortLaterRecords() = runTest {
        val first = calibration(InflammationFactor.TNF_ALPHA, validSignals)
        val second = calibration(InflammationFactor.IL6, validSignals)
        val repository = RevalidationRepository(listOf(first, second))
        val validator = CalibrationValidator()
        val revalidator = LegacyCalibrationRevalidator(repository) { inputs ->
            if (inputs.first().concentration == FACTORY_LADDER.getValue(InflammationFactor.TNF_ALPHA).first()) {
                // The two ladders share a blank, so identify the first record by its second knot.
                if (inputs[1].concentration == FACTORY_LADDER.getValue(InflammationFactor.TNF_ALPHA)[1]) {
                    error("validator exploded")
                }
            }
            validator.validate(inputs)
        }

        val summary = revalidator.revalidateNeedsReview()

        assertEquals(listOf(InflammationFactor.IL6), repository.saved.map { it.factor })
        assertEquals(1, summary.failures.size)
        assertEquals(InflammationFactor.TNF_ALPHA, summary.failures.single().factor)
        assertEquals(LegacyRevalidationStage.VALIDATE, summary.failures.single().stage)
        assertEquals(2, summary.attempted)
    }

    @Test
    fun saveFailureIsReportedThenExplicitRetryCanPromoteTheRecord() = runTest {
        val first = calibration(InflammationFactor.TNF_ALPHA, validSignals)
        val second = calibration(InflammationFactor.IL6, validSignals)
        val repository = RevalidationRepository(
            initial = listOf(first, second),
            saveFailures = mutableSetOf(InflammationFactor.TNF_ALPHA),
        )
        val revalidator = LegacyCalibrationRevalidator(repository)

        val firstRun = revalidator.revalidateNeedsReview()
        val secondRun = revalidator.revalidateNeedsReview()

        assertEquals(
            listOf(InflammationFactor.IL6, InflammationFactor.TNF_ALPHA),
            repository.saved.map { it.factor },
        )
        assertEquals(LegacyRevalidationStage.SAVE, firstRun.failures.single().stage)
        assertEquals(1, secondRun.attempted)
        assertEquals(1, secondRun.promoted)
    }

    @Test
    fun validatorFailureCanBeRetriedExplicitlyAndThenCompletesExactlyOnce() = runTest {
        val record = calibration(InflammationFactor.TNF_ALPHA, validSignals)
        val repository = RevalidationRepository(listOf(record))
        var fail = true
        val validator = CalibrationValidator()
        val revalidator = LegacyCalibrationRevalidator(repository) { inputs ->
            if (fail) error("transient validator failure")
            validator.validate(inputs)
        }

        val failed = revalidator.revalidateNeedsReview()
        fail = false
        val retried = revalidator.revalidateNeedsReview()
        val afterSuccess = revalidator.revalidateNeedsReview()

        assertEquals(1, failed.failures.size)
        assertEquals(1, retried.promoted)
        assertEquals(0, afterSuccess.attempted)
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun cancellationDoesNotConsumeRecordAndLaterRetryCanPromoteIt() = runTest {
        val record = calibration(InflammationFactor.TNF_ALPHA, validSignals)
        val repository = RevalidationRepository(listOf(record))
        var cancel = true
        val validator = CalibrationValidator()
        val revalidator = LegacyCalibrationRevalidator(repository) { inputs ->
            if (cancel) throw CancellationException("cancelled")
            validator.validate(inputs)
        }

        try {
            revalidator.revalidateNeedsReview()
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected: cancellation must leave the record retryable.
        }
        cancel = false
        val retried = revalidator.revalidateNeedsReview()

        assertEquals(1, retried.promoted)
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun changedContentWithSameMetadataIsTreatedAsANewRecord() = runTest {
        val invalid = calibration(InflammationFactor.TNF_ALPHA, validSignals.take(3))
        val repository = RevalidationRepository(listOf(invalid))
        val revalidator = LegacyCalibrationRevalidator(repository)
        val first = revalidator.revalidateNeedsReview()
        repository.current.value = listOf(calibration(InflammationFactor.TNF_ALPHA, validSignals))

        val changed = revalidator.revalidateNeedsReview()

        assertEquals(1, first.retained)
        assertEquals(1, changed.attempted)
        assertEquals(1, changed.promoted)
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

    private companion object {
        val validSignals = listOf(10f, 12f, 15f, 18f, 22f, 28f, 36f, 46f, 58f)
    }
}

private class RevalidationRepository(
    initial: List<Calibration>,
    private val saveFailures: MutableSet<InflammationFactor> = mutableSetOf(),
) : CalibrationRepository {
    val current = MutableStateFlow(initial)
    val saved = mutableListOf<Calibration>()

    override fun observeCalibrations(): Flow<List<Calibration>> = current

    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        MutableStateFlow(current.value.firstOrNull { it.factor == factor })

    override suspend fun save(calibration: Calibration) {
        if (saveFailures.remove(calibration.factor)) error("save exploded")
        saved += calibration
        current.value = current.value.map { existing ->
            if (existing.factor == calibration.factor) calibration else existing
        }
    }

    override suspend fun clearAll() {
        current.value = emptyList()
    }
}
