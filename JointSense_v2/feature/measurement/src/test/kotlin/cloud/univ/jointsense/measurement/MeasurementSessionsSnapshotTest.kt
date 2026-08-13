package cloud.univ.jointsense.measurement

import androidx.lifecycle.SavedStateHandle
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
class MeasurementSessionsSnapshotTest {
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
    fun delayedRepositoryFlowMarksOnlyItsFirstEmissionAndRecreationWaitsAgain() = runTest(dispatcher) {
        val repository = DelayedSessionsRepository()
        val savedStateHandle = SavedStateHandle()
        val factory = MeasurementViewModelFactory(
            repository = repository,
            analyzer = UnusedSnapshotAnalyzer,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        val first = factory.create(savedStateHandle)
        testScheduler.runCurrent()

        assertFalse(first.state.value.hasReceivedSessionsSnapshot)
        first.selectSession("local-selection-before-snapshot")
        assertFalse(first.state.value.hasReceivedSessionsSnapshot)
        assertEquals(
            ResultResolution.Loading,
            resolveResultById("target", null, emptyList(), first.state.value.hasReceivedSessionsSnapshot),
        )

        repository.snapshots.emit(emptyList())
        testScheduler.runCurrent()

        assertTrue(first.state.value.hasReceivedSessionsSnapshot)
        first.finishMeasurement()
        assertTrue(first.state.value.hasReceivedSessionsSnapshot)
        assertEquals(
            ResultResolution.NotFound,
            resolveResultById(
                "target",
                first.state.value.currentSession,
                first.state.value.sessions,
                first.state.value.hasReceivedSessionsSnapshot,
            ),
        )

        val recreated = factory.create(savedStateHandle)
        testScheduler.runCurrent()

        assertFalse(recreated.state.value.hasReceivedSessionsSnapshot)
        assertEquals(
            ResultResolution.Loading,
            resolveResultById(
                "target",
                recreated.state.value.currentSession,
                recreated.state.value.sessions,
                recreated.state.value.hasReceivedSessionsSnapshot,
            ),
        )

        repository.snapshots.emit(listOf(sessionWithTarget()))
        testScheduler.runCurrent()

        assertTrue(recreated.state.value.hasReceivedSessionsSnapshot)
        assertTrue(
            resolveResultById(
                "target",
                recreated.state.value.currentSession,
                recreated.state.value.sessions,
                recreated.state.value.hasReceivedSessionsSnapshot,
            ) is ResultResolution.Found,
        )
    }

    private fun sessionWithTarget(): TestSession {
        val result = TestResult(
            id = "target",
            sessionId = "session",
            draftId = null,
            factor = InflammationFactor.TNF_ALPHA,
            concentration = 10f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = RgbFeatures(1f, 1f, 1f, 1f, 1f, 1f),
            timestamp = 1L,
        )
        return TestSession(
            id = "session",
            name = "session",
            createdAt = 1L,
            source = DataSource.USER,
            results = listOf(result),
        )
    }
}

private class DelayedSessionsRepository : TestSessionRepository {
    val snapshots = MutableSharedFlow<List<TestSession>>(extraBufferCapacity = 1)

    override fun observeSessions(): Flow<List<TestSession>> = snapshots
    override fun observeSession(id: String): Flow<TestSession?> = error("unused")
    override suspend fun createSession(name: String, source: DataSource): String = error("unused")
    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String = error("unused")

    override suspend fun deleteSession(id: String) = error("unused")
}

private object UnusedSnapshotAnalyzer : BaselinePhotoAnalysisAdapter {
    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
        factor: InflammationFactor,
    ): BaselineAnalysisResult = error("unused")
}
