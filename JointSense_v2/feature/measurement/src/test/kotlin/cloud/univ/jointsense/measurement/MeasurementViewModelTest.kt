package cloud.univ.jointsense.measurement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun analyzeEmitsAnalyzingPersistingSuccessInOrder() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { suspendCommit = true }
        val viewModel = readyViewModel(repository = repository)
        val stages = mutableListOf<Stage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.map { it.stage }.distinctUntilChanged().drop(1).take(3).collect {
                stages += it
            }
        }

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        assertEquals(Stage.Persisting, viewModel.state.value.stage)
        repository.releaseCommit()
        advanceUntilIdle()

        assertEquals(listOf(Stage.Analyzing, Stage.Persisting, Stage.Success), stages)
    }

    @Test
    fun secondAnalyzeDuringFlightCommitsSameDraftOnlyOnce() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { suspendCommit = true }
        val viewModel = readyViewModel(repository = repository)

        viewModel.onAction(MeasurementAction.Analyze)
        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()

        assertEquals(1, repository.commitCalls)
        repository.releaseCommit()
        advanceUntilIdle()
        assertEquals(listOf("draft-1"), repository.committedDrafts)
    }

    @Test
    fun repositoryMustReturnBeforeSuccessIsVisible() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { suspendCommit = true }
        val viewModel = readyViewModel(repository = repository)

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()

        assertEquals(Stage.Persisting, viewModel.state.value.stage)
        assertNull(viewModel.state.value.resultId)
        repository.releaseCommit("persisted-result")
        advanceUntilIdle()
        assertEquals(Stage.Success, viewModel.state.value.stage)
        assertEquals("persisted-result", viewModel.state.value.resultId)
    }

    @Test
    fun analysisFailureResumesFromReadyToAnalyzeAndRetryReanalyzes() = runTest(dispatcher) {
        val analyzer = RecordingAnalyzer().apply { failuresRemaining = 1 }
        val repository = RecordingRepository()
        val viewModel = readyViewModel(repository = repository, analyzer = analyzer)

        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()

        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
        assertEquals(MeasurementError.AnalysisFailed, viewModel.state.value.error)
        assertEquals(Stage.ReadyToAnalyze, viewModel.state.value.resumeStage)
        assertEquals(0, repository.commitCalls)

        viewModel.onAction(MeasurementAction.Retry)
        advanceUntilIdle()
        assertEquals(2, analyzer.calls)
        assertEquals(Stage.Success, viewModel.state.value.stage)
    }

    @Test
    fun persistenceRetryReusesAnalysisAndDraftWithoutDuplicateSuccess() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { commitFailuresRemaining = 1 }
        val analyzer = RecordingAnalyzer()
        val viewModel = readyViewModel(repository = repository, analyzer = analyzer)

        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()
        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
        assertEquals(MeasurementError.PersistenceFailed, viewModel.state.value.error)
        assertEquals(Stage.Persisting, viewModel.state.value.resumeStage)

        viewModel.onAction(MeasurementAction.Retry)
        advanceUntilIdle()

        assertEquals(1, analyzer.calls)
        assertEquals(listOf("draft-1", "draft-1"), repository.committedDrafts)
        assertEquals(Stage.Success, viewModel.state.value.stage)
        assertEquals(MeasurementEffect.NavigateToResult("result-1"), viewModel.effects.first())
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun analyzeIsIgnoredInPersistenceErrorUntilExplicitRetry() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { commitFailuresRemaining = 1 }
        val analyzer = RecordingAnalyzer()
        val viewModel = readyViewModel(repository = repository, analyzer = analyzer)
        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()
        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)

        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()

        assertEquals(1, analyzer.calls)
        assertEquals(1, repository.commitCalls)
        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
        assertEquals(Stage.Persisting, viewModel.state.value.resumeStage)
    }

    @Test
    fun cancelDuringAnalysisReturnsToReadyWithoutCommit() = runTest(dispatcher) {
        val analyzer = RecordingAnalyzer().apply { suspendAnalysis = true }
        val repository = RecordingRepository()
        val viewModel = readyViewModel(repository = repository, analyzer = analyzer)

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        assertEquals(Stage.Analyzing, viewModel.state.value.stage)

        viewModel.onAction(MeasurementAction.CancelAnalysis)
        runCurrent()

        assertEquals(Stage.ReadyToAnalyze, viewModel.state.value.stage)
        assertEquals(0, repository.commitCalls)
    }

    @Test
    fun uriCropFactorOriginAndDraftSurviveSavedStateRecreation() = runTest(dispatcher) {
        var draftNumber = 0
        val repository = RecordingRepository()
        val originalHandle = SavedStateHandle()
        val original = MeasurementViewModel(
            repository = repository,
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-${++draftNumber}" },
            savedStateHandle = originalHandle,
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )

        original.createNewSession("REPORT")
        advanceUntilIdle()
        original.acceptCreatedSession()
        original.onAction(MeasurementAction.ImageSelected("content://measurement/photo"))
        advanceUntilIdle()
        original.onAction(MeasurementAction.CropChanged(CropBounds(11, 12, 111, 112)))
        original.onAction(MeasurementAction.CropConfirmed)
        original.onAction(MeasurementAction.FactorSelected(InflammationFactor.IL1_BETA))

        val restoredHandle = SavedStateHandle(
            originalHandle.keys().associateWith { key -> originalHandle.get<Any?>(key) },
        )
        val restored = MeasurementViewModel(
            repository = repository,
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-${++draftNumber}" },
            savedStateHandle = restoredHandle,
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()

        assertEquals("content://measurement/photo", restored.state.value.imageUri)
        assertEquals(CropBounds(11, 12, 111, 112), restored.state.value.cropRect)
        assertEquals(InflammationFactor.IL1_BETA, restored.state.value.factor)
        assertEquals("REPORT", restored.state.value.originDestination)
        assertEquals(original.state.value.draftId, restored.state.value.draftId)
        assertEquals(Stage.ReadyToAnalyze, restored.state.value.stage)
    }

    @Test
    fun unconfirmedCropRestoresToReadyToCropInsteadOfSkippingConfirmation() = runTest(dispatcher) {
        val originalHandle = SavedStateHandle()
        val original = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-1" },
            savedStateHandle = originalHandle,
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        original.onAction(MeasurementAction.ImageSelected("content://unconfirmed"))
        advanceUntilIdle()
        original.onAction(MeasurementAction.CropChanged(CropBounds(1, 2, 101, 102)))

        val restored = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "unexpected-new-draft" },
            savedStateHandle = SavedStateHandle(
                originalHandle.keys().associateWith { key -> originalHandle.get<Any?>(key) },
            ),
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()

        assertEquals(Stage.ReadyToCrop, restored.state.value.stage)
        assertEquals(CropBounds(1, 2, 101, 102), restored.state.value.cropRect)
    }

    @Test
    fun selectingNewUriClearsPriorCropBeforeUsingNewImageDefaults() = runTest(dispatcher) {
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-1" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.onAction(MeasurementAction.ImageSelected("content://first"))
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(1, 2, 101, 102)))
        viewModel.onAction(MeasurementAction.CropConfirmed)

        viewModel.onAction(MeasurementAction.ImageSelected("content://second"))
        assertNull(viewModel.state.value.cropRect)
        advanceUntilIdle()

        assertEquals(CropBounds(200, 150, 600, 450), viewModel.state.value.cropRect)
        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
    }

    @Test
    fun clearingViewModelStoreReleasesOwnedDecodedImage() = runTest(dispatcher) {
        val image = ReleasableImage(800, 600)
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-1" },
            savedStateHandle = SavedStateHandle(),
            decoder = object : MeasurementImageDecoder {
                override suspend fun decode(uri: String): MeasurementImage = image
            },
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.onAction(MeasurementAction.ImageSelected("content://owned"))
        advanceUntilIdle()
        val store = ViewModelStore()
        store.put("measurement", viewModel)

        store.clear()

        assertEquals(1, image.releaseCalls)
    }

    @Test
    fun successfulCommitEmitsNavigationEffectExactlyOnce() = runTest(dispatcher) {
        val viewModel = readyViewModel()

        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()

        assertEquals(MeasurementEffect.NavigateToResult("result-1"), viewModel.effects.first())
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun continueMeasurementCreatesNewDraftAndClearsPriorImageResult() = runTest(dispatcher) {
        var draftNumber = 0
        val viewModel = readyViewModel(draftIdFactory = { "draft-${++draftNumber}" })
        val committedDraft = viewModel.state.value.draftId
        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()
        assertEquals(Stage.Success, viewModel.state.value.stage)

        viewModel.onAction(MeasurementAction.ContinueMeasurement)

        assertNotEquals(committedDraft, viewModel.state.value.draftId)
        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        assertNull(viewModel.state.value.imageUri)
        assertNull(viewModel.state.value.resultId)
    }

    @Test
    fun acceptingASeparateNewFlowCreatesAnotherDraft() = runTest(dispatcher) {
        var draftNumber = 0
        val viewModel = readyViewModel(draftIdFactory = { "draft-${++draftNumber}" })
        val firstFlowDraft = viewModel.state.value.draftId

        viewModel.createNewSession("PROFILE")
        advanceUntilIdle()
        viewModel.acceptCreatedSession()

        assertNotEquals(firstFlowDraft, viewModel.state.value.draftId)
        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        assertEquals("PROFILE", viewModel.state.value.originDestination)
    }

    @Test
    fun factoryCreatesFreshDraftWhileRestoringProvidedOrigin() = runTest(dispatcher) {
        var draftNumber = 0
        val factory = MeasurementViewModelFactory(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
            draftIdFactory = { "draft-${++draftNumber}" },
        )
        val first = factory.create(SavedStateHandle())
        val next = factory.create(
            SavedStateHandle(mapOf("measurement.origin" to "HOME")),
        )

        assertNotEquals(first.state.value.draftId, next.state.value.draftId)
        assertEquals("HOME", next.state.value.originDestination)
    }

    @Test
    fun cropOutsideDecodedImageIsRecoverableAndPreservesLastValidCrop() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        val validCrop = viewModel.state.value.cropRect

        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(-10, 20, 310, 220)))

        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
        assertEquals(MeasurementError.InvalidCrop, viewModel.state.value.error)
        assertEquals(Stage.ReadyToCrop, viewModel.state.value.resumeStage)
        assertEquals(validCrop, viewModel.state.value.cropRect)
    }

    @Test
    fun decoderAnalyzerAndRepositoryUseTheirInjectedDispatcherBoundaries() = runTest(dispatcher) {
        val activeBoundary = ThreadLocal<String?>()
        val io = BoundaryDispatcher("IO", activeBoundary, dispatcher)
        val cpu = BoundaryDispatcher("DEFAULT", activeBoundary, dispatcher)
        val decoder = RecordingDecoder(activeBoundary)
        val analyzer = RecordingAnalyzer(activeBoundary)
        val repository = RecordingRepository(activeBoundary)
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = analyzer,
            draftIdFactory = { "draft-1" },
            savedStateHandle = SavedStateHandle(),
            decoder = decoder,
            ioDispatcher = io,
            defaultDispatcher = cpu,
        )
        viewModel.createNewSession("HOME")
        advanceUntilIdle()
        viewModel.acceptCreatedSession()

        viewModel.onAction(MeasurementAction.ImageSelected("content://photo"))
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CropConfirmed)
        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()

        assertEquals("IO", decoder.boundary)
        assertEquals("DEFAULT", analyzer.boundary)
        assertEquals("IO", repository.commitBoundary)
    }

    private fun TestScope.readyViewModel(
        repository: RecordingRepository = RecordingRepository(),
        analyzer: RecordingAnalyzer = RecordingAnalyzer(),
        draftIdFactory: () -> String = { "draft-1" },
    ): MeasurementViewModel {
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = analyzer,
            draftIdFactory = draftIdFactory,
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.createNewSession("HOME")
        testScheduler.advanceUntilIdle()
        viewModel.acceptCreatedSession()
        viewModel.onAction(MeasurementAction.ImageSelected("content://measurement/photo"))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(10, 20, 310, 220)))
        viewModel.onAction(MeasurementAction.CropConfirmed)
        return viewModel
    }
}

private fun MeasurementViewModel.acceptCreatedSession(): String {
    val request = requireNotNull(state.value.sessionCreationRequest)
    return requireNotNull(acceptSessionCreation(request.requestId))
}

private class RecordingDecoder(
    private val activeBoundary: ThreadLocal<String?>? = null,
) : MeasurementImageDecoder {
    var boundary: String? = null
        private set

    override suspend fun decode(uri: String): MeasurementImage {
        boundary = activeBoundary?.get()
        return FakeImage(width = 800, height = 600)
    }
}

private data class FakeImage(
    override val width: Int,
    override val height: Int,
) : MeasurementImage

private class ReleasableImage(
    override val width: Int,
    override val height: Int,
) : MeasurementImage {
    var releaseCalls = 0
        private set

    override fun release() {
        releaseCalls += 1
    }
}

private class RecordingAnalyzer(
    private val activeBoundary: ThreadLocal<String?>? = null,
) : BaselinePhotoAnalysisAdapter {
    var calls = 0
    var failuresRemaining = 0
    var suspendAnalysis = false
    var boundary: String? = null
        private set
    private val analysisGate = CompletableDeferred<Unit>()

    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
        factor: InflammationFactor,
    ): BaselineAnalysisResult {
        calls += 1
        boundary = activeBoundary?.get()
        if (suspendAnalysis) analysisGate.await()
        if (failuresRemaining-- > 0) error("analysis failed")
        return BaselineAnalysisResult(
            concentration = 42f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = RgbFeatures(10f, 20f, 30f, 1f, 2f, 3f),
        )
    }
}

private class RecordingRepository(
    private val activeBoundary: ThreadLocal<String?>? = null,
) : TestSessionRepository {
    private val nextSession = AtomicInteger(1)
    private val nextResult = AtomicInteger(1)
    private val commitGate = CompletableDeferred<String>()
    val sessions = MutableStateFlow<List<TestSession>>(emptyList())
    val committedDrafts = mutableListOf<String>()
    var commitCalls = 0
        private set
    var commitFailuresRemaining = 0
    var suspendCommit = false
    var commitBoundary: String? = null
        private set

    fun releaseCommit(id: String = "result-1") {
        commitGate.complete(id)
    }

    override fun observeSessions(): Flow<List<TestSession>> = sessions

    override fun observeSession(id: String): Flow<TestSession?> = MutableStateFlow(
        sessions.value.firstOrNull { it.id == id },
    )

    override suspend fun createSession(name: String, source: DataSource): String {
        val id = "session-${nextSession.getAndIncrement()}"
        sessions.value = sessions.value + TestSession(id, name, 1L, source, emptyList())
        return id
    }

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String {
        commitCalls += 1
        committedDrafts += draftId
        commitBoundary = activeBoundary?.get()
        if (commitFailuresRemaining-- > 0) error("persistence failed")
        val id = if (suspendCommit) commitGate.await() else "result-${nextResult.getAndIncrement()}"
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
        sessions.value = sessions.value.map { session ->
            if (session.id == sessionId && session.results.none { it.draftId == draftId }) {
                session.copy(results = session.results + stored)
            } else {
                session
            }
        }
        return id
    }

    override suspend fun deleteSession(id: String) {
        sessions.value = sessions.value.filterNot { it.id == id }
    }
}

private class BoundaryDispatcher(
    private val name: String,
    private val activeBoundary: ThreadLocal<String?>,
    private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        delegate.dispatch(context) {
            val previous = activeBoundary.get()
            activeBoundary.set(name)
            try {
                block.run()
            } finally {
                activeBoundary.set(previous)
            }
        }
    }
}
