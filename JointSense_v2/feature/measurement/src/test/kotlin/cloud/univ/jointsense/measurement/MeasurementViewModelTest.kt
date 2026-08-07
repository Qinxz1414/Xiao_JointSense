package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun sessionCreationWaitsForInsertAndGuardsRepeatedRequests() = runTest(dispatcher) {
        val repository = ControlledCreateRepository()
        val viewModel = MeasurementViewModel(repository, FakeAnalyzer())
        var navigationCalls = 0

        viewModel.createNewSession { navigationCalls += 1 }
        viewModel.createNewSession { navigationCalls += 1 }
        runCurrent()

        assertEquals(1, repository.createCalls)
        assertEquals(0, navigationCalls)
        assertTrue(viewModel.state.value.isCreatingSession)

        repository.completeCreate("created-session")
        testScheduler.advanceUntilIdle()

        assertEquals(1, navigationCalls)
        assertFalse(viewModel.state.value.isCreatingSession)
        assertEquals("created-session", viewModel.state.value.currentSession?.id)
    }

    @Test
    fun failedSessionCreationStaysPutAndExposesConsumableError() = runTest(dispatcher) {
        val repository = ControlledCreateRepository()
        val viewModel = MeasurementViewModel(repository, FakeAnalyzer())
        var navigationCalls = 0

        viewModel.createNewSession { navigationCalls += 1 }
        runCurrent()
        repository.failCreate(IllegalStateException("insert failed"))
        testScheduler.advanceUntilIdle()

        assertEquals(0, navigationCalls)
        assertFalse(viewModel.state.value.isCreatingSession)
        assertEquals("insert failed", viewModel.state.value.sessionCreationError)

        viewModel.consumeSessionCreationError()
        assertNull(viewModel.state.value.sessionCreationError)
    }

    @Test
    fun abandonWhileInsertIsPendingDeletesLateEmptySessionAndNeverNavigates() = runTest(dispatcher) {
        val repository = ControlledCreateRepository()
        val viewModel = MeasurementViewModel(repository, FakeAnalyzer())
        var navigationCalls = 0

        viewModel.createNewSession { navigationCalls += 1 }
        runCurrent()
        viewModel.abandonMeasurement()
        runCurrent()
        repository.completeCreate("late-session")
        testScheduler.advanceUntilIdle()

        assertEquals(0, navigationCalls)
        assertEquals(listOf("late-session"), repository.deletedIds)
        assertNull(viewModel.state.value.currentSession)
        assertFalse(viewModel.state.value.isCreatingSession)
    }

    @Test
    fun completionCommittedWithoutCollectorIsDeliveredExactlyOnceToLaterCollector() =
        runTest(dispatcher) {
            val repository = FakeMeasurementRepository()
            val viewModel = MeasurementViewModel(repository, FakeAnalyzer())

            viewModel.createNewSession()
            testScheduler.advanceUntilIdle()
            viewModel.setImage(FakeImage(width = 800, height = 600))
            viewModel.analyze()
            testScheduler.advanceUntilIdle()

            val committedId = repository.sessions.value.single().results.single().id
            val deliveredId = withTimeoutOrNull(1_000) {
                viewModel.analysisCompletions.first()
            }
            val duplicate = withTimeoutOrNull(1) {
                viewModel.analysisCompletions.first()
            }

            assertEquals(committedId, deliveredId)
            assertEquals("result-1", deliveredId)
            assertNull(duplicate)
        }

    @Test
    fun retryReusesDraftAndResultWhileContinueAndNewSessionRotateDraft() = runTest(dispatcher) {
        val repository = FakeMeasurementRepository()
        var draftNumber = 0
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = FakeAnalyzer(),
            draftIdFactory = { "draft-${++draftNumber}" },
        )

        viewModel.createNewSession()
        testScheduler.advanceUntilIdle()
        viewModel.setImage(FakeImage(width = 800, height = 600))

        viewModel.analyze()
        testScheduler.advanceUntilIdle()
        val firstResultId = viewModel.analysisCompletions.first()
        viewModel.analyze()
        testScheduler.advanceUntilIdle()
        val retryResultId = viewModel.analysisCompletions.first()

        assertEquals(listOf("draft-1", "draft-1"), repository.draftIds)
        assertEquals(firstResultId, retryResultId)
        assertEquals(1, repository.sessions.value.single().results.size)

        viewModel.startNewTestInSession()
        viewModel.setImage(FakeImage(width = 800, height = 600))
        viewModel.analyze()
        testScheduler.advanceUntilIdle()
        val continuedResultId = viewModel.analysisCompletions.first()

        assertEquals("draft-2", repository.draftIds.last())
        assertFalse(continuedResultId == firstResultId)

        viewModel.createNewSession()
        testScheduler.advanceUntilIdle()
        viewModel.setImage(FakeImage(width = 800, height = 600))
        viewModel.analyze()
        testScheduler.advanceUntilIdle()

        assertEquals("draft-3", repository.draftIds.last())
    }
}

private class ControlledCreateRepository : TestSessionRepository {
    private val createResult = CompletableDeferred<Result<String>>()
    val sessions = MutableStateFlow<List<TestSession>>(emptyList())
    val deletedIds = mutableListOf<String>()
    var createCalls = 0
        private set

    fun completeCreate(id: String) {
        createResult.complete(Result.success(id))
    }

    fun failCreate(exception: Throwable) {
        createResult.complete(Result.failure(exception))
    }

    override fun observeSessions(): Flow<List<TestSession>> = sessions
    override fun observeSession(id: String): Flow<TestSession?> = MutableStateFlow(
        sessions.value.firstOrNull { it.id == id },
    )

    override suspend fun createSession(name: String, source: DataSource): String {
        createCalls += 1
        val id = createResult.await().getOrThrow()
        sessions.value = sessions.value + session(id)
        return id
    }

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String = error("unused")

    override suspend fun deleteSession(id: String) {
        deletedIds += id
        sessions.value = sessions.value.filterNot { it.id == id }
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
    private val resultIdsByDraft = mutableMapOf<String, String>()
    val sessions = MutableStateFlow(initial)
    val deletedIds = mutableListOf<String>()
    val draftIds = mutableListOf<String>()
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
        draftIds += draftId
        resultIdsByDraft[draftId]?.let { return it }
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
        resultIdsByDraft[draftId] = id
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
