package cloud.univ.jointsense.calibration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import cloud.univ.jointsense.analysis.calibration.CalibrationError
import cloud.univ.jointsense.analysis.calibration.CalibrationValidation
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CalibrationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun factorChangeLoadsThatFactorsExistingFactoryLadder() {
        val viewModel = viewModel()

        viewModel.selectFactor(InflammationFactor.IL6)

        assertEquals(
            FACTORY_LADDER.getValue(InflammationFactor.IL6).map(::formatConcentration),
            viewModel.state.value.concentrationTexts,
        )
        assertEquals(InflammationFactor.IL6, viewModel.state.value.factor)
    }

    @Test
    fun invalidConcentrationTextIsPreservedAndReportedAtItsField() {
        val viewModel = viewModel()
        viewModel.setDetectedSignals(validSignals)

        viewModel.updateConcentration(3, "not-a-number")
        val canReview = viewModel.review()

        assertFalse(canReview)
        assertEquals("not-a-number", viewModel.state.value.concentrationTexts[3])
        assertEquals(setOf(3), viewModel.state.value.concentrationFieldErrors)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun missingBlankBlocksReview() {
        val viewModel = viewModel()
        viewModel.setDetectedSignals(validSignals)
        viewModel.updateConcentration(0, "1")

        val canReview = viewModel.review()

        assertFalse(canReview)
        val invalid = viewModel.state.value.validation as CalibrationValidation.Invalid
        assertTrue(CalibrationError.MissingBlank in invalid.errors)
    }

    @Test
    fun excessivePavaAdjustmentAllowsReviewButBlocksSave() {
        val viewModel = viewModel()
        viewModel.setDetectedSignals(listOf(0f, 100f, 0f, 100f, 110f, 120f, 130f, 140f, 150f))

        val canReview = viewModel.review()

        assertTrue(canReview)
        assertFalse(viewModel.state.value.canSave)
        val invalid = viewModel.state.value.validation as CalibrationValidation.Invalid
        assertEquals(listOf(CalibrationError.NonMonotonicBeyondTolerance), invalid.errors)
    }

    @Test
    fun reviewRequiresExactlyNineFiniteSignalsWithoutTruncatingExtras() {
        listOf(
            validSignals.take(8) to CalibrationError.WrongReadingCount,
            (validSignals + 70f) to CalibrationError.WrongReadingCount,
            validSignals.toMutableList().also { it[4] = Float.NaN } to CalibrationError.NonFiniteSignal,
            validSignals.toMutableList().also { it[4] = Float.POSITIVE_INFINITY } to CalibrationError.NonFiniteSignal,
        ).forEach { (signals, expected) ->
            val viewModel = viewModel()
            viewModel.setDetectedSignals(signals)

            assertFalse(viewModel.review())
            val invalid = viewModel.state.value.validation as CalibrationValidation.Invalid
            assertTrue(expected in invalid.errors)
            assertFalse(viewModel.state.value.canSave)
        }
    }

    @Test
    fun validSavePersistsValidatorRawAndFittedKnotsExactlyOnce() = runTest(dispatcher) {
        val repository = ViewModelCalibrationRepository()
        val viewModel = viewModel(repository = repository)
        viewModel.setDetectedSignals(validSignals)
        assertTrue(viewModel.review())

        viewModel.save()
        viewModel.save()
        advanceUntilIdle()

        val saved = repository.saved.single()
        assertEquals(InflammationFactor.TNF_ALPHA, saved.factor)
        assertEquals(456L, saved.createdAt)
        assertEquals(CalibrationStatus.ACTIVE, saved.status)
        assertEquals(9, saved.knots.size)
        assertEquals(10f, saved.knots.first().rawSignal)
        assertEquals(0f, saved.knots.first().fittedSignal)
        assertEquals(58f, saved.knots.last().rawSignal)
        assertEquals(48f, saved.knots.last().fittedSignal)
        assertTrue(viewModel.state.value.saveCompleted)
    }

    @Test
    fun savedStateRestoresFactorTextsAndSignals() {
        val handle = SavedStateHandle()
        val original = viewModel(savedStateHandle = handle)
        original.selectFactor(InflammationFactor.IL6)
        original.updateConcentration(4, "55.5")
        original.setDetectedSignals(validSignals)

        val restored = viewModel(savedStateHandle = handle)

        assertEquals(InflammationFactor.IL6, restored.state.value.factor)
        assertEquals("55.5", restored.state.value.concentrationTexts[4])
        assertEquals(validSignals, restored.state.value.signals)
    }

    @Test
    fun savedStateRevalidatesAnAlreadyReviewedCurve() {
        val handle = SavedStateHandle()
        val original = viewModel(savedStateHandle = handle)
        original.setDetectedSignals(validSignals)
        assertTrue(original.review())

        val restored = viewModel(savedStateHandle = handle)

        assertTrue(restored.state.value.validation is CalibrationValidation.Valid)
        assertTrue(restored.state.value.canSave)
    }

    @Test
    fun inFlightSaveRejectsResetAndCompletesOriginalFactor() = runTest(dispatcher) {
        val saveGate = CompletableDeferred<Unit>()
        val repository = ViewModelCalibrationRepository(saveGate)
        val viewModel = viewModel(repository = repository)
        viewModel.setDetectedSignals(validSignals)
        assertTrue(viewModel.review())

        viewModel.save()
        runCurrent()
        viewModel.resetForAnotherFactor()
        saveGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
        assertTrue(viewModel.state.value.saveCompleted)
        assertEquals(InflammationFactor.TNF_ALPHA, viewModel.state.value.savedFactor)
    }

    @Test
    fun completedSaveRestoresAsCompletedAndCannotWriteAgain() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val repository = ViewModelCalibrationRepository()
        val original = viewModel(repository, handle)
        original.setDetectedSignals(validSignals)
        assertTrue(original.review())
        original.save()
        advanceUntilIdle()

        val restored = viewModel(repository, handle)
        restored.save()
        advanceUntilIdle()

        assertTrue(restored.state.value.saveCompleted)
        assertEquals(InflammationFactor.TNF_ALPHA, restored.state.value.savedFactor)
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun claimingSaveNavigationDoesNotClearDurableCompletionOrAllowDuplicateWrite() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val repository = ViewModelCalibrationRepository()
        val original = viewModel(repository, handle)
        original.setDetectedSignals(validSignals)
        assertTrue(original.review())
        original.save()
        advanceUntilIdle()

        assertTrue(original.claimSaveNavigation())
        assertFalse(original.claimSaveNavigation())
        assertTrue(original.state.value.saveCompleted)
        assertFalse(original.state.value.canSave)

        val recreated = viewModel(repository, handle)
        recreated.save()
        advanceUntilIdle()
        assertTrue(recreated.state.value.saveCompleted)
        assertEquals(1, repository.saved.size)
    }

    @Test
    fun mutationsAndFactoryRestoreAreRejectedWhileSaveIsInFlight() = runTest(dispatcher) {
        val saveGate = CompletableDeferred<Unit>()
        val repository = ViewModelCalibrationRepository(saveGate = saveGate)
        val viewModel = viewModel(repository)
        viewModel.setDetectedSignals(validSignals)
        assertTrue(viewModel.review())
        val originalTexts = viewModel.state.value.concentrationTexts

        viewModel.save()
        runCurrent()
        viewModel.selectFactor(InflammationFactor.IL6)
        viewModel.updateConcentration(1, "999")
        viewModel.setDetectedSignals(List(9) { 999f })
        viewModel.resetForAnotherFactor()
        viewModel.confirmRestoreFactory()

        assertTrue(viewModel.state.value.isPersistenceBusy)
        assertEquals(InflammationFactor.TNF_ALPHA, viewModel.state.value.factor)
        assertEquals(originalTexts, viewModel.state.value.concentrationTexts)
        assertEquals(validSignals, viewModel.state.value.signals)
        assertEquals(0, repository.clearCalls)
        saveGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(InflammationFactor.TNF_ALPHA, viewModel.state.value.savedFactor)
    }

    @Test
    fun saveAndRestoreAreMutuallyExclusiveInBothOperationOrders() = runTest(dispatcher) {
        val saveGate = CompletableDeferred<Unit>()
        val saveRepository = ViewModelCalibrationRepository(saveGate = saveGate)
        val saving = viewModel(saveRepository)
        saving.setDetectedSignals(validSignals)
        assertTrue(saving.review())
        saving.save()
        runCurrent()
        saving.confirmRestoreFactory()
        assertEquals(0, saveRepository.clearCalls)
        saveGate.complete(Unit)
        advanceUntilIdle()

        val clearGate = CompletableDeferred<Unit>()
        val clearRepository = ViewModelCalibrationRepository(clearGate = clearGate)
        val restoring = viewModel(clearRepository)
        restoring.setDetectedSignals(validSignals)
        assertTrue(restoring.review())
        restoring.confirmRestoreFactory()
        runCurrent()
        restoring.save()
        restoring.confirmRestoreFactory()
        assertEquals(0, clearRepository.saved.size)
        assertEquals(1, clearRepository.clearCalls)
        assertTrue(restoring.state.value.isPersistenceBusy)
        clearGate.complete(Unit)
        advanceUntilIdle()
        assertTrue(restoring.state.value.factoryRestoreCompleted)
    }

    @Test
    fun restoredImageDecodePreservesValidReviewWhenCropStillFits() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val original = viewModel(
            savedStateHandle = handle,
            decoder = CalibrationBitmapDecoder { FakeCalibrationImage(100, 100) },
        )
        original.onImageSelected("content://calibration")
        advanceUntilIdle()
        original.setDetectedSignals(validSignals)
        assertTrue(original.review())

        val restored = viewModel(
            savedStateHandle = handle,
            decoder = CalibrationBitmapDecoder { FakeCalibrationImage(100, 100) },
        )
        advanceUntilIdle()

        assertTrue(restored.state.value.validation is CalibrationValidation.Valid)
        assertTrue(restored.state.value.canSave)
        assertEquals(validSignals, restored.state.value.signals)
    }

    @Test
    fun restoredImageDecodeInvalidatesReviewWhenSavedCropDoesNotFit() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val original = viewModel(
            savedStateHandle = handle,
            decoder = CalibrationBitmapDecoder { FakeCalibrationImage(100, 100) },
        )
        original.onImageSelected("content://calibration")
        advanceUntilIdle()
        original.updateCrop(CalibrationIntBounds(80, 80, 100, 100))
        original.setDetectedSignals(validSignals)
        assertTrue(original.review())

        val restored = viewModel(
            savedStateHandle = handle,
            decoder = CalibrationBitmapDecoder { FakeCalibrationImage(10, 10) },
        )
        advanceUntilIdle()

        assertNull(restored.state.value.validation)
        assertFalse(restored.state.value.canSave)
        assertTrue(restored.state.value.errorMessage?.contains("crop", ignoreCase = true) == true)
    }

    @Test
    fun replacingImageWaitsForNonCooperativeDetectorBeforeReleasingBorrowedImage() = runTest(dispatcher) {
        val detectorDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        try {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val exited = CountDownLatch(1)
            val old = FakeCalibrationImage(20, 20)
            val replacement = FakeCalibrationImage(20, 20)
            var decoded: CalibrationImage = old
            val viewModel = viewModel(
                decoder = CalibrationBitmapDecoder { decoded },
                detector = CalibrationSignalDetector { image, _ ->
                    assertFalse(image.isReleased)
                    entered.countDown()
                    release.await()
                    assertFalse(image.isReleased)
                    exited.countDown()
                    List(9) { GridWellReading(0, 0, it, it.toFloat()) }
                },
                defaultDispatcher = detectorDispatcher,
            )
            viewModel.onImageSelected("old")
            advanceUntilIdle()
            viewModel.detectSignals()
            runCurrent()
            assertTrue(entered.await(2, TimeUnit.SECONDS))

            decoded = replacement
            viewModel.onImageSelected("replacement")
            runCurrent()
            assertEquals(0, old.releaseCalls)
            release.countDown()
            assertTrue(exited.await(2, TimeUnit.SECONDS))
            var attempts = 0
            while (old.releaseCalls == 0 && attempts++ < 100) {
                runCurrent()
                Thread.sleep(5)
            }

            assertEquals(1, old.releaseCalls)
            assertEquals(0, replacement.releaseCalls)
        } finally {
            detectorDispatcher.close()
        }
    }

    @Test
    fun resetAndOnClearedDelayBorrowedImageReleaseAndReleaseExactlyOnce() = runTest(dispatcher) {
        suspend fun exercise(clear: (CalibrationViewModel) -> Unit) {
            val detectorDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            try {
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val exited = CountDownLatch(1)
                val image = FakeCalibrationImage(20, 20)
                val viewModel = viewModel(
                    decoder = CalibrationBitmapDecoder { image },
                    detector = CalibrationSignalDetector { borrowed, _ ->
                        entered.countDown()
                        release.await()
                        assertFalse(borrowed.isReleased)
                        exited.countDown()
                        emptyList()
                    },
                    defaultDispatcher = detectorDispatcher,
                )
                viewModel.onImageSelected("image")
                advanceUntilIdle()
                viewModel.detectSignals()
                runCurrent()
                assertTrue(entered.await(2, TimeUnit.SECONDS))
                clear(viewModel)
                assertEquals(0, image.releaseCalls)
                release.countDown()
                assertTrue(exited.await(2, TimeUnit.SECONDS))
                var attempts = 0
                while (image.releaseCalls == 0 && attempts++ < 100) {
                    runCurrent()
                    Thread.sleep(5)
                }
                assertEquals(1, image.releaseCalls)
            } finally {
                detectorDispatcher.close()
            }
        }

        exercise { it.resetForAnotherFactor() }
        exercise { viewModel -> ViewModelStore().apply { put("calibration", viewModel); clear() } }
    }

    @Test
    fun confirmedFactoryRestoreClearsAllUserCurvesOnce() = runTest(dispatcher) {
        val repository = ViewModelCalibrationRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.confirmRestoreFactory()
        viewModel.confirmRestoreFactory()
        advanceUntilIdle()

        assertEquals(1, repository.clearCalls)
    }

    @Test
    fun legacyPerRecordFailureIsVisibleInViewModelState() = runTest(dispatcher) {
        val repository = ViewModelCalibrationRepository()
        repository.calibrations.value = listOf(needsReviewCalibration())
        val revalidator = LegacyCalibrationRevalidator(repository) { error("validator exploded") }

        val viewModel = viewModel(repository = repository, legacyRevalidator = revalidator)
        advanceUntilIdle()

        val summary = viewModel.state.value.legacyRevalidationSummary
        assertEquals(1, summary?.failures?.size)
        assertEquals(LegacyRevalidationStage.VALIDATE, summary?.failures?.single()?.stage)
        assertTrue(viewModel.state.value.errorMessage?.contains("legacy", ignoreCase = true) == true)
    }

    private fun viewModel(
        repository: ViewModelCalibrationRepository = ViewModelCalibrationRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        decoder: CalibrationBitmapDecoder? = null,
        detector: CalibrationSignalDetector = CalibrationSignalDetector { _, _ -> emptyList() },
        defaultDispatcher: kotlinx.coroutines.CoroutineDispatcher = dispatcher,
        legacyRevalidator: LegacyCalibrationRevalidator? = null,
    ) = CalibrationViewModel(
        repository = repository,
        savedStateHandle = savedStateHandle,
        decoder = decoder,
        detector = detector,
        legacyRevalidator = legacyRevalidator,
        clock = { 456L },
        ioDispatcher = dispatcher,
        defaultDispatcher = defaultDispatcher,
    )

    private companion object {
        val validSignals = listOf(10f, 12f, 15f, 18f, 22f, 28f, 36f, 46f, 58f)

        fun needsReviewCalibration(): Calibration {
            val concentrations = FACTORY_LADDER.getValue(InflammationFactor.TNF_ALPHA)
            return Calibration(
                factor = InflammationFactor.TNF_ALPHA,
                createdAt = 1L,
                version = 1,
                status = CalibrationStatus.NEEDS_REVIEW,
                kitName = null,
                kitLot = null,
                knots = validSignals.mapIndexed { index, signal ->
                    CalibrationKnot(
                        position = index,
                        concentration = concentrations[index],
                        rawSignal = signal,
                        netSignal = signal,
                        fittedSignal = signal,
                        isBlank = index == 0,
                    )
                },
            )
        }
    }
}

private class ViewModelCalibrationRepository(
    private val saveGate: CompletableDeferred<Unit>? = null,
    private val clearGate: CompletableDeferred<Unit>? = null,
) : CalibrationRepository {
    val calibrations = MutableStateFlow<List<Calibration>>(emptyList())
    val saved = mutableListOf<Calibration>()
    var clearCalls = 0

    override fun observeCalibrations(): Flow<List<Calibration>> = calibrations

    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        MutableStateFlow(calibrations.value.firstOrNull { it.factor == factor })

    override suspend fun save(calibration: Calibration) {
        saveGate?.await()
        saved += calibration
    }

    override suspend fun clearAll() {
        clearCalls += 1
        clearGate?.await()
    }
}

private class FakeCalibrationImage(
    override val width: Int,
    override val height: Int,
) : CalibrationImage {
    var releaseCalls = 0
    override val isReleased: Boolean get() = releaseCalls > 0

    override fun release() {
        releaseCalls += 1
    }
}
