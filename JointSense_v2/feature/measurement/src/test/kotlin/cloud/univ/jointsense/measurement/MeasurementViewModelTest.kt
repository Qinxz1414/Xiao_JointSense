package cloud.univ.jointsense.measurement

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementViewModelTest {
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
    fun ownsSessionImageCropFactorAndCommittedResultFlow() = runTest(dispatcher) {
        val repository = FakeMeasurementRepository()
        val analyzer = FakeAnalyzer()
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = analyzer,
            draftIdFactory = { "draft-1" },
        )
        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.createNewSession()
        testScheduler.advanceUntilIdle()
        assertEquals("session-1", viewModel.state.value.currentSession?.id)

        viewModel.setImage(FakeImage(width = 800, height = 600))
        assertEquals(CropBounds(200, 150, 600, 450), viewModel.state.value.cropBounds)
        viewModel.updateCropBounds(CropBounds(10, 20, 310, 220))
        viewModel.selectFactor(InflammationFactor.TNF_ALPHA)

        viewModel.analyze()
        testScheduler.advanceUntilIdle()

        assertEquals(1, analyzer.calls)
        assertEquals(CropBounds(10, 20, 310, 220), analyzer.lastCrop)
        assertEquals(InflammationFactor.TNF_ALPHA, analyzer.lastFactor)
        assertEquals("draft-1", repository.lastDraftId)
        assertEquals(42f, viewModel.state.value.lastResult?.concentration)
        assertFalse(viewModel.state.value.isAnalyzing)
    }

    @Test
    fun historySelectionAndAbandonmentUseTheDomainRepository() = runTest(dispatcher) {
        val repository = FakeMeasurementRepository(
            listOf(session("history", result = storedResult("old-result", "history"))),
        )
        val viewModel = MeasurementViewModel(repository, FakeAnalyzer())
        backgroundScope.launch { viewModel.state.collect {} }
        testScheduler.advanceUntilIdle()

        viewModel.selectSession("history")
        assertEquals("old-result", viewModel.state.value.currentSession?.results?.single()?.id)

        viewModel.createNewSession()
        testScheduler.advanceUntilIdle()
        assertNotNull(viewModel.state.value.currentSession)
        viewModel.abandonMeasurement()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("session-1"), repository.deletedIds)
        assertNull(viewModel.state.value.currentSession)
    }
}

private data class FakeImage(
    override val width: Int,
    override val height: Int,
) : MeasurementImage

private class FakeAnalyzer : BaselinePhotoAnalysisAdapter {
    var calls = 0
    var lastCrop: CropBounds? = null
    var lastFactor: InflammationFactor? = null

    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
        factor: InflammationFactor,
    ): BaselineAnalysisResult {
        calls += 1
        lastCrop = cropBounds
        lastFactor = factor
        return BaselineAnalysisResult(
            concentration = 42f,
            rangeStatus = RangeStatus.UNKNOWN,
            features = RgbFeatures(10f, 20f, 30f, 1f, 2f, 3f),
        )
    }
}

private class FakeMeasurementRepository(
    initial: List<TestSession> = emptyList(),
) : TestSessionRepository {
    private var nextSession = 1
    private var nextResult = 1
    val sessions = MutableStateFlow(initial)
    val deletedIds = mutableListOf<String>()
    var lastDraftId: String? = null

    override fun observeSessions(): Flow<List<TestSession>> = sessions
    override fun observeSession(id: String): Flow<TestSession?> = MutableStateFlow(
        sessions.value.firstOrNull { it.id == id },
    )

    override suspend fun createSession(name: String, source: DataSource): String {
        val id = "session-${nextSession++}"
        sessions.value = sessions.value + session(id)
        return id
    }

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String {
        lastDraftId = draftId
        val id = "result-${nextResult++}"
        val stored = TestResult(
            id = id,
            sessionId = sessionId,
            draftId = draftId,
            factor = result.factor,
            concentration = result.concentration,
            rangeStatus = result.rangeStatus,
            features = result.features,
            timestamp = result.timestamp,
        )
        sessions.value = sessions.value.map {
            if (it.id == sessionId) it.copy(results = it.results + stored) else it
        }
        return id
    }

    override suspend fun deleteSession(id: String) {
        deletedIds += id
        sessions.value = sessions.value.filterNot { it.id == id }
    }
}

private fun session(id: String, result: TestResult? = null) = TestSession(
    id = id,
    name = "Test",
    createdAt = 1L,
    source = DataSource.USER,
    results = listOfNotNull(result),
)

private fun storedResult(id: String, sessionId: String) = TestResult(
    id = id,
    sessionId = sessionId,
    draftId = null,
    factor = InflammationFactor.IL6,
    concentration = 1f,
    rangeStatus = RangeStatus.UNKNOWN,
    features = RgbFeatures(1f, 2f, 3f, 4f, 5f, 6f),
    timestamp = 2L,
)
