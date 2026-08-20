package cloud.univ.jointsense.calibration

import cloud.univ.jointsense.analysis.calibration.CalibrationInput
import cloud.univ.jointsense.analysis.calibration.CalibrationValidator
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LegacyCalibrationRevalidatorTest {
    @Test
    fun legacyMeanCurveIsRetainedForRecalibrationAndNeverPromotedIntoP90Domain() = runTest {
        val legacy = calibration(InflammationFactor.TNF_ALPHA, validSignals).copy(
            signalMethod = ColorSignalMethod.LEGACY_MEAN_BR,
        )
        val repository = RevalidationRepository(listOf(legacy))
        var validationCalls = 0
        val revalidator = LegacyCalibrationRevalidator(repository) {
            validationCalls += 1
            error("Legacy mean curve must not reach the P90 validator")
        }

        val summary = revalidator.revalidateNeedsReview()

        assertEquals(0, validationCalls)
        assertEquals(1, summary.attempted)
        assertEquals(0, summary.promoted)
        assertEquals(1, summary.retained)
        assertTrue(repository.saved.isEmpty())
        assertEquals(CalibrationStatus.NEEDS_REVIEW, repository.current.value.single().status)
    }

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

    @Test
    fun userSaveCompletedBeforeLegacyScanMakesScanObserveActiveAndNeverOverwriteIt() = runTest {
        val legacy = calibration(InflammationFactor.TNF_ALPHA, validSignals)
        val user = legacy.copy(
            createdAt = 999L,
            status = CalibrationStatus.ACTIVE,
            knots = legacy.knots.map { knot ->
                knot.copy(
                    rawSignal = knot.rawSignal + 100f,
                    netSignal = knot.netSignal + 100f,
                    fittedSignal = knot.fittedSignal + 100f,
                )
            },
        )
        val repository = RevalidationRepository(listOf(legacy))
        val revalidator = LegacyCalibrationRevalidator(repository)

        revalidator.saveUserCalibration(user)
        val summary = revalidator.revalidateNeedsReview()

        assertEquals(0, summary.attempted)
        assertEquals(listOf(user), repository.saved)
        assertEquals(listOf(user), repository.current.value)
    }

    @Test
    fun cancelledUserSaveReleasesCoordinatorForLegacyScan() = runTest {
        val legacy = calibration(InflammationFactor.TNF_ALPHA, validSignals)
        val user = legacy.copy(createdAt = 999L, status = CalibrationStatus.ACTIVE)
        val saveEntered = CompletableDeferred<Unit>()
        val repository = RevalidationRepository(
            initial = listOf(legacy),
            saveEntered = saveEntered,
            allowSave = CompletableDeferred(),
            gatedSaveCreatedAt = user.createdAt,
        )
        val revalidator = LegacyCalibrationRevalidator(repository)

        val userSave = async { revalidator.saveUserCalibration(user) }
        saveEntered.await()
        userSave.cancelAndJoin()
        val summary = revalidator.revalidateNeedsReview()

        assertEquals(1, summary.promoted)
        assertEquals(listOf(legacy.createdAt), repository.saved.map(Calibration::createdAt))
        assertEquals(CalibrationStatus.ACTIVE, repository.current.value.single().status)
    }

    @Test
    fun cancelledLegacyScanReleasesCoordinatorForUserSave() = runTest {
        val legacy = calibration(InflammationFactor.TNF_ALPHA, validSignals)
        val user = legacy.copy(createdAt = 999L, status = CalibrationStatus.ACTIVE)
        val saveEntered = CompletableDeferred<Unit>()
        val repository = RevalidationRepository(
            initial = listOf(legacy),
            saveEntered = saveEntered,
            allowSave = CompletableDeferred(),
            gatedSaveCreatedAt = legacy.createdAt,
        )
        val revalidator = LegacyCalibrationRevalidator(repository)

        val scan = async { revalidator.revalidateNeedsReview() }
        saveEntered.await()
        scan.cancelAndJoin()
        revalidator.saveUserCalibration(user)

        assertEquals(listOf(user), repository.saved)
        assertEquals(listOf(user), repository.current.value)
    }

    @Test
    fun clearWaitsForInFlightLegacySaveThenLeavesRepositoryEmpty() = runTest {
        val saveEntered = CompletableDeferred<Unit>()
        val allowSave = CompletableDeferred<Unit>()
        val repository = RevalidationRepository(
            initial = listOf(calibration(InflammationFactor.TNF_ALPHA, validSignals)),
            saveEntered = saveEntered,
            allowSave = allowSave,
        )
        val revalidator = LegacyCalibrationRevalidator(repository)

        val scan = async { revalidator.revalidateNeedsReview() }
        saveEntered.await()
        val clear = async { revalidator.clearAllUserCalibrations() }
        runCurrent()
        assertFalse(clear.isCompleted)

        allowSave.complete(Unit)
        scan.await()
        clear.await()

        assertTrue(repository.current.value.isEmpty())
    }

    @Test
    fun legacyScanStartedBehindClearSeesTheEmptyPostClearSnapshot() = runTest {
        val clearEntered = CompletableDeferred<Unit>()
        val allowClear = CompletableDeferred<Unit>()
        val repository = RevalidationRepository(
            initial = listOf(calibration(InflammationFactor.TNF_ALPHA, validSignals)),
            clearEntered = clearEntered,
            allowClear = allowClear,
        )
        val revalidator = LegacyCalibrationRevalidator(repository)

        val clear = async { revalidator.clearAllUserCalibrations() }
        clearEntered.await()
        val scan = async { revalidator.revalidateNeedsReview() }
        runCurrent()
        assertFalse(scan.isCompleted)

        allowClear.complete(Unit)
        clear.await()
        val summary = scan.await()

        assertEquals(0, summary.attempted)
        assertTrue(repository.saved.isEmpty())
        assertTrue(repository.current.value.isEmpty())
    }

    @Test
    fun cancelledLegacySaveReleasesCoordinatorForFactoryClear() = runTest {
        val saveEntered = CompletableDeferred<Unit>()
        val repository = RevalidationRepository(
            initial = listOf(calibration(InflammationFactor.TNF_ALPHA, validSignals)),
            saveEntered = saveEntered,
            allowSave = CompletableDeferred(),
        )
        val revalidator = LegacyCalibrationRevalidator(repository)

        val scan = async { revalidator.revalidateNeedsReview() }
        saveEntered.await()
        scan.cancelAndJoin()

        revalidator.clearAllUserCalibrations()

        assertTrue(repository.current.value.isEmpty())
    }

    @Test
    fun cancelledFactoryClearReleasesCoordinatorForLegacyScan() = runTest {
        val clearEntered = CompletableDeferred<Unit>()
        val repository = RevalidationRepository(
            initial = listOf(calibration(InflammationFactor.TNF_ALPHA, validSignals)),
            clearEntered = clearEntered,
            allowClear = CompletableDeferred(),
        )
        val revalidator = LegacyCalibrationRevalidator(repository)

        val clear = async { revalidator.clearAllUserCalibrations() }
        clearEntered.await()
        clear.cancelAndJoin()

        val summary = revalidator.revalidateNeedsReview()

        assertEquals(1, summary.promoted)
        assertEquals(CalibrationStatus.ACTIVE, repository.current.value.single().status)
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
    private val saveEntered: CompletableDeferred<Unit>? = null,
    private val allowSave: CompletableDeferred<Unit>? = null,
    private val gatedSaveCreatedAt: Long? = null,
    private val clearEntered: CompletableDeferred<Unit>? = null,
    private val allowClear: CompletableDeferred<Unit>? = null,
) : CalibrationRepository {
    val current = MutableStateFlow(initial)
    val saved = mutableListOf<Calibration>()

    override fun observeCalibrations(): Flow<List<Calibration>> = current

    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        MutableStateFlow(current.value.firstOrNull { it.factor == factor })

    override suspend fun save(calibration: Calibration) {
        if (saveFailures.remove(calibration.factor)) error("save exploded")
        if (gatedSaveCreatedAt == null || calibration.createdAt == gatedSaveCreatedAt) {
            saveEntered?.complete(Unit)
            allowSave?.await()
        }
        saved += calibration
        current.value = current.value.map { existing ->
            if (existing.factor == calibration.factor) calibration else existing
        }
    }

    override suspend fun clearAll() {
        clearEntered?.complete(Unit)
        allowClear?.await()
        current.value = emptyList()
    }
}
