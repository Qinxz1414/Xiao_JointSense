package cloud.univ.jointsense.settings

import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import cloud.univ.jointsense.domain.repository.DataManagementRepository
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
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
    fun repositoryFlowsDriveSessionMeasurementAndCalibrationCounts() = runTest(dispatcher) {
        val sessions = FakeSettingsSessionRepository(listOf(sessionWithTwoResults()))
        val calibrations = FakeSettingsCalibrationRepository(listOf(calibration(InflammationFactor.IL6)))
        val viewModel = SettingsViewModel(sessions, calibrations, FakeDataManagementRepository())
        val collection = backgroundScope.launch { viewModel.state.collect {} }

        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.sessionCount)
        assertEquals(2, viewModel.state.value.measurementCount)
        assertEquals(1, viewModel.state.value.calibrationCount)
        assertTrue(viewModel.state.value.hasCalibration)

        sessions.sessions.value = emptyList()
        calibrations.calibrations.value = emptyList()
        testScheduler.advanceUntilIdle()

        assertEquals(SettingsUiState(), viewModel.state.value)
        collection.cancel()
    }

    @Test
    fun dataManagementActionsAreDelegatedToTheRepository() = runTest(dispatcher) {
        val data = FakeDataManagementRepository()
        val viewModel = SettingsViewModel(
            FakeSettingsSessionRepository(emptyList()),
            FakeSettingsCalibrationRepository(emptyList()),
            data,
        )

        viewModel.clearAllData()
        viewModel.restoreBuiltInSamples()
        testScheduler.advanceUntilIdle()

        assertEquals(1, data.clearCalls)
        assertEquals(1, data.restoreCalls)
    }

    @Test
    fun needsReviewCalibrationDoesNotReportAnActiveUserCurve() = runTest(dispatcher) {
        val calibrations = FakeSettingsCalibrationRepository(
            listOf(calibration(InflammationFactor.IL6, CalibrationStatus.NEEDS_REVIEW)),
        )
        val viewModel = SettingsViewModel(
            FakeSettingsSessionRepository(emptyList()),
            calibrations,
            FakeDataManagementRepository(),
        )
        val collection = backgroundScope.launch { viewModel.state.collect {} }

        testScheduler.advanceUntilIdle()

        assertEquals(0, viewModel.state.value.calibrationCount)
        assertFalse(viewModel.state.value.hasCalibration)
        assertEquals(1, viewModel.state.value.calibrationReviewCount)
        assertTrue(viewModel.state.value.hasCalibrationNeedingReview)
        assertEquals(
            "Calibration needs review (1 factor(s))",
            calibrationSubtitle(viewModel.state.value),
        )
        collection.cancel()
    }

    @Test
    fun mixedCalibrationStatusesCountOnlyActiveCurves() = runTest(dispatcher) {
        val calibrations = FakeSettingsCalibrationRepository(
            listOf(
                calibration(InflammationFactor.IL6, CalibrationStatus.NEEDS_REVIEW),
                calibration(InflammationFactor.TNF_ALPHA, CalibrationStatus.ACTIVE),
            ),
        )
        val viewModel = SettingsViewModel(
            FakeSettingsSessionRepository(emptyList()),
            calibrations,
            FakeDataManagementRepository(),
        )
        val collection = backgroundScope.launch { viewModel.state.collect {} }

        testScheduler.advanceUntilIdle()

        assertEquals(1, viewModel.state.value.calibrationCount)
        assertTrue(viewModel.state.value.hasCalibration)
        assertEquals(1, viewModel.state.value.calibrationReviewCount)
        assertTrue(viewModel.state.value.hasCalibrationNeedingReview)
        collection.cancel()
    }

    private fun sessionWithTwoResults() = TestSession(
        id = "session",
        name = "Settings fixture",
        createdAt = 1L,
        source = DataSource.USER,
        results = listOf(result("one"), result("two")),
    )

    private fun result(id: String) = TestResult(
        id = id,
        sessionId = "session",
        draftId = null,
        factor = InflammationFactor.TNF_ALPHA,
        concentration = 1f,
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(1f, 1f, 1f, 0f, 0f, 0f),
        timestamp = 1L,
    )

    private fun calibration(
        factor: InflammationFactor,
        status: CalibrationStatus = CalibrationStatus.ACTIVE,
    ) = Calibration(
        factor = factor,
        createdAt = 1L,
        version = 1,
        status = status,
        kitName = null,
        kitLot = null,
        knots = emptyList(),
    )
}

private class FakeSettingsSessionRepository(initial: List<TestSession>) : TestSessionRepository {
    val sessions = MutableStateFlow(initial)

    override fun observeSessions(): Flow<List<TestSession>> = sessions
    override fun observeSession(id: String): Flow<TestSession?> =
        MutableStateFlow(sessions.value.firstOrNull { it.id == id })

    override suspend fun createSession(name: String, source: DataSource): String = error("unused")
    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String = error("unused")

    override suspend fun deleteSession(id: String) = error("unused")
}

private class FakeSettingsCalibrationRepository(initial: List<Calibration>) : CalibrationRepository {
    val calibrations = MutableStateFlow(initial)

    override fun observeCalibrations(): Flow<List<Calibration>> = calibrations
    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        MutableStateFlow(calibrations.value.firstOrNull { it.factor == factor })

    override suspend fun save(calibration: Calibration) = error("unused")
    override suspend fun clearAll() = error("unused")
}

private class FakeDataManagementRepository : DataManagementRepository {
    var clearCalls = 0
    var restoreCalls = 0

    override suspend fun clearAllData() {
        clearCalls += 1
    }

    override suspend fun restoreBuiltInSamples() {
        restoreCalls += 1
    }
}
