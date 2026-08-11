package cloud.univ.jointsense.calibration

import androidx.lifecycle.SavedStateHandle
import cloud.univ.jointsense.analysis.calibration.CalibrationError
import cloud.univ.jointsense.analysis.calibration.CalibrationValidation
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun staleSaveCompletionCannotMoveResetFlowToDone() = runTest(dispatcher) {
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
        assertFalse(viewModel.state.value.saveCompleted)
        assertNull(viewModel.state.value.savedFactor)
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
    fun confirmedFactoryRestoreClearsAllUserCurvesOnce() = runTest(dispatcher) {
        val repository = ViewModelCalibrationRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.confirmRestoreFactory()
        viewModel.confirmRestoreFactory()
        advanceUntilIdle()

        assertEquals(1, repository.clearCalls)
    }

    private fun viewModel(
        repository: ViewModelCalibrationRepository = ViewModelCalibrationRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = CalibrationViewModel(
        repository = repository,
        savedStateHandle = savedStateHandle,
        decoder = null,
        legacyRevalidator = null,
        clock = { 456L },
        ioDispatcher = dispatcher,
        defaultDispatcher = dispatcher,
    )

    private companion object {
        val validSignals = listOf(10f, 12f, 15f, 18f, 22f, 28f, 36f, 46f, 58f)
    }
}

private class ViewModelCalibrationRepository(
    private val saveGate: CompletableDeferred<Unit>? = null,
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
    }
}
