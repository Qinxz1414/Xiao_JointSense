package cloud.univ.jointsense.measurement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.NewMeasurementBatch
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertNull(viewModel.state.value.imageUri)
        assertNull(viewModel.state.value.cropRect)
    }

    @Test
    fun committedResultWaitsForRepositoryConfirmationInsteadOfBecomingNotFound() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { delaySnapshotAfterCommit = true }
        val savedStateHandle = SavedStateHandle()
        val viewModel = readyViewModel(
            repository = repository,
            savedStateHandle = savedStateHandle,
        )
        assertTrue(viewModel.state.value.hasReceivedSessionsSnapshot)

        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()

        assertEquals(MeasurementEffect.NavigateToResult("result-1"), viewModel.effects.first())
        assertEquals("result-1", viewModel.state.value.awaitingRepositoryResultId)
        assertEquals(
            ResultResolution.Loading,
            resolveResultById(
                resultId = "result-1",
                currentSession = viewModel.state.value.currentSession,
                sessions = viewModel.state.value.sessions,
                hasReceivedSessionsSnapshot = viewModel.state.value.hasReceivedSessionsSnapshot,
                awaitingRepositoryResultId = viewModel.state.value.awaitingRepositoryResultId,
            ),
        )

        viewModel.finishMeasurement()
        assertEquals("result-1", viewModel.state.value.awaitingRepositoryResultId)
        val recreated = MeasurementViewModel(
            repository = repository,
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "recreated-draft" },
            savedStateHandle = savedStateHandle,
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()

        assertEquals("result-1", recreated.state.value.awaitingRepositoryResultId)
        assertEquals(
            ResultResolution.Loading,
            resolveResultById(
                resultId = "result-1",
                currentSession = recreated.state.value.currentSession,
                sessions = recreated.state.value.sessions,
                hasReceivedSessionsSnapshot = recreated.state.value.hasReceivedSessionsSnapshot,
                awaitingRepositoryResultId = recreated.state.value.awaitingRepositoryResultId,
            ),
        )

        repository.emitDelayedCommitSnapshot()
        advanceUntilIdle()

        assertNull(recreated.state.value.awaitingRepositoryResultId)
        assertTrue(
            resolveResultById(
                resultId = "result-1",
                currentSession = recreated.state.value.currentSession,
                sessions = recreated.state.value.sessions,
                hasReceivedSessionsSnapshot = recreated.state.value.hasReceivedSessionsSnapshot,
                awaitingRepositoryResultId = recreated.state.value.awaitingRepositoryResultId,
            ) is ResultResolution.Found,
        )
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
    fun cancelIsIgnoredAfterAtomicPersistenceHasStarted() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { suspendCommit = true }
        val viewModel = readyViewModel(repository = repository)

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        assertEquals(Stage.Persisting, viewModel.state.value.stage)

        viewModel.onAction(MeasurementAction.CancelAnalysis)
        runCurrent()

        assertEquals(Stage.Persisting, viewModel.state.value.stage)
        repository.releaseCommit()
        advanceUntilIdle()
        assertEquals(Stage.Success, viewModel.state.value.stage)
        assertEquals(1, repository.commitCalls)
    }

    @Test
    fun uriCropOriginAndDraftSurviveSavedStateRecreation() = runTest(dispatcher) {
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

        original.createNewSession("REPORT", "Test")
        advanceUntilIdle()
        original.acceptCreatedSession()
        original.onAction(MeasurementAction.ImageSelected("content://measurement/photo"))
        advanceUntilIdle()
        original.onAction(MeasurementAction.CropChanged(CropBounds(11, 12, 311, 112)))
        original.onAction(MeasurementAction.CropConfirmed)

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
        assertEquals(CropBounds(11, 12, 311, 112), restored.state.value.cropRect)
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
        assertEquals(CropBounds(80, 193, 720, 406), restored.state.value.cropRect)
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

        assertEquals(CropBounds(80, 193, 720, 406), viewModel.state.value.cropRect)
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

        viewModel.createNewSession("PROFILE", "Test")
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
    fun eventTimeLocalizedPrefixAffectsTheNextSessionWithoutRecreatingViewModel() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = RecordingAnalyzer(),
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )

        viewModel.createNewSession("HOME", "Test")
        advanceUntilIdle()
        viewModel.acceptCreatedSession()
        viewModel.createNewSession("HOME", "检测")
        advanceUntilIdle()

        assertEquals(listOf("Test #1", "检测 #1"), repository.sessions.value.map(TestSession::name))
    }

    @Test
    fun blankSessionPrefixFailsBeforeRepositoryMutation() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val viewModel = MeasurementViewModel(repository, RecordingAnalyzer())

        val failure = runCatching { viewModel.createNewSession("HOME", "   ") }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        advanceUntilIdle()
        assertTrue(repository.sessions.value.isEmpty())
        assertFalse(viewModel.state.value.isCreatingSession)
        assertNull(viewModel.state.value.sessionCreationRequest)
    }

    @Test
    fun cropOutsideDecodedImageStaysInEditorAndPreservesLastValidCrop() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        val validCrop = viewModel.state.value.cropRect

        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(-10, 20, 310, 220)))

        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals(MeasurementError.InvalidCrop, viewModel.state.value.error)
        assertNull(viewModel.state.value.resumeStage)
        assertEquals(validCrop, viewModel.state.value.cropRect)
    }

    @Test
    fun transientInvalidResizeStaysEditableAndClearsGuidanceWhenValidAgain() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        val invalidCrop = CropBounds(10, 20, 210, 220)
        val recoveredCrop = CropBounds(10, 20, 310, 120)

        viewModel.onAction(MeasurementAction.CropChanged(invalidCrop))

        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals(invalidCrop, viewModel.state.value.cropRect)
        assertEquals(MeasurementError.InvalidCrop, viewModel.state.value.error)

        viewModel.onAction(MeasurementAction.CropChanged(recoveredCrop))

        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals(recoveredCrop, viewModel.state.value.cropRect)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun onePixelImageCannotFormAThreeDiscMeasurementRow() = runTest(dispatcher) {
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-1" },
        )

        viewModel.setImage(FakeImage(1, 1))

        assertEquals(CropBounds(0, 0, 1, 1), viewModel.state.value.cropRect)
        viewModel.onAction(MeasurementAction.CropConfirmed)
        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals(MeasurementError.InvalidCrop, viewModel.state.value.error)
    }

    @Test
    fun temporaryPermissionDenialPreservesDraftAndRecoversToImageSelection() =
        runTest(dispatcher) {
            verifyPermissionDenialRecovery(permanentlyDenied = false)
        }

    @Test
    fun permanentPermissionDenialPreservesDraftAndRecoversToImageSelection() =
        runTest(dispatcher) {
            verifyPermissionDenialRecovery(permanentlyDenied = true)
        }

    @Test
    fun analyzeFlightIgnoresMutuallyExclusiveInputsUntilItCompletes() = runTest(dispatcher) {
        val analyzer = RecordingAnalyzer().apply { nonCooperativeAnalysis = true }
        val image = ReleasableImage(800, 600)
        val replacement = ReleasableImage(400, 300)
        val viewModel = readyOwnedViewModel(image = image, analyzer = analyzer)
        val operationData = viewModel.state.value.recoveryData()
        val sessionId = requireNotNull(viewModel.state.value.currentSession?.id)

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        viewModel.onAction(MeasurementAction.ImageSelected("content://ignored"))
        viewModel.onAction(
            MeasurementAction.CameraPermissionResult(
                claim = CameraPermissionLaunchClaim("stale-token", "draft-1"),
                granted = false,
                shouldShowRationale = false,
            ),
        )
        viewModel.onAction(MeasurementAction.ContinueMeasurement)
        viewModel.setImage(replacement)
        viewModel.selectSession("other-session")

        assertEquals(Stage.Analyzing, viewModel.state.value.stage)
        assertEquals(operationData, viewModel.state.value.recoveryData())
        assertEquals(sessionId, viewModel.state.value.currentSession?.id)
        assertEquals(0, image.releaseCalls)
        assertEquals(1, replacement.releaseCalls)

        analyzer.releaseAnalysis()
        advanceUntilIdle()
        assertEquals(Stage.Success, viewModel.state.value.stage)
        assertEquals(1, image.releaseCalls)
    }

    @Test
    fun busyImageSelectionIsIgnoredDuringPersisting() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { suspendCommit = true }
        val image = ReleasableImage(800, 600)
        val replacement = ReleasableImage(400, 300)
        val viewModel = readyOwnedViewModel(image = image, repository = repository)
        val operationData = viewModel.state.value.recoveryData()
        val sessionId = requireNotNull(viewModel.state.value.currentSession?.id)

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        assertEquals(Stage.Persisting, viewModel.state.value.stage)
        viewModel.onAction(MeasurementAction.ImageSelected("content://ignored"))
        viewModel.onAction(
            MeasurementAction.CameraPermissionResult(
                claim = CameraPermissionLaunchClaim("stale-token", "draft-1"),
                granted = false,
                shouldShowRationale = true,
            ),
        )
        viewModel.onAction(MeasurementAction.ContinueMeasurement)
        viewModel.setImage(replacement)
        viewModel.deleteSession(sessionId)
        runCurrent()

        assertEquals(Stage.Persisting, viewModel.state.value.stage)
        assertEquals(operationData, viewModel.state.value.recoveryData())
        assertTrue(repository.sessions.value.any { it.id == sessionId })
        assertEquals(0, image.releaseCalls)
        assertEquals(1, replacement.releaseCalls)
        repository.releaseCommit()
        advanceUntilIdle()
        assertEquals(Stage.Success, viewModel.state.value.stage)
    }

    @Test
    fun abandonDuringPersistingKeepsLateCompletionBoundToCapturedDraft() = runTest(dispatcher) {
        var draftNumber = 0
        val repository = RecordingRepository().apply { nonCooperativeCommit = true }
        val oldImage = ReleasableImage(800, 600)
        val replacement = ReleasableImage(400, 300)
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-${++draftNumber}" },
            savedStateHandle = SavedStateHandle(mapOf("measurement.sessionId" to "session-1")),
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.setImage(oldImage)
        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(10, 20, 310, 120)))
        viewModel.onAction(MeasurementAction.CropConfirmed)
        val capturedDraft = viewModel.state.value.draftId

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        assertEquals(Stage.Persisting, viewModel.state.value.stage)

        viewModel.abandonMeasurement()
        val replacementDraft = viewModel.state.value.draftId
        viewModel.setImage(replacement)
        repository.releaseCommit("late-result")
        runCurrent()

        assertEquals(listOf(capturedDraft), repository.committedDrafts)
        assertNotEquals(capturedDraft, replacementDraft)
        assertEquals(replacementDraft, viewModel.state.value.draftId)
        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertTrue(viewModel.state.value.image === replacement)
        assertEquals(1, oldImage.releaseCalls)
        assertEquals(0, replacement.releaseCalls)
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun onClearedDefersOwnedImageReleaseUntilNonCooperativeAnalyzerExits() = runTest(dispatcher) {
        val analyzer = RecordingAnalyzer().apply { nonCooperativeAnalysis = true }
        val image = ReleasableImage(800, 600)
        val viewModel = readyOwnedViewModel(image = image, analyzer = analyzer)
        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        val store = ViewModelStore().apply { put("measurement", viewModel) }

        store.clear()
        assertEquals(0, image.releaseCalls)
        analyzer.releaseAnalysis()
        advanceUntilIdle()

        assertEquals(1, image.releaseCalls)
    }

    @Test
    fun cancelledOuterDecodeBoundaryReleasesAllocatedUndeliveredImage() = runTest(dispatcher) {
        val io = ManualQueueDispatcher()
        val decoded = ReleasableImage(800, 600)
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-1" },
            savedStateHandle = SavedStateHandle(),
            decoder = object : MeasurementImageDecoder {
                override suspend fun decode(uri: String): MeasurementImage = decoded
            },
            ioDispatcher = io,
            defaultDispatcher = dispatcher,
        )

        viewModel.onAction(MeasurementAction.ImageSelected("content://queued"))
        runCurrent()
        io.runNext()
        viewModel.onAction(MeasurementAction.ContinueMeasurement)
        runCurrent()

        assertEquals(1, decoded.releaseCalls)
        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
    }

    @Test
    fun abandoningBeforePickedFallbackDecodeDeliveryCleansTheAcquiredOwnedCapture() =
        runTest(dispatcher) {
            val io = ManualQueueDispatcher()
            val owned = MeasurementCapture("content://app/picked", "picked-token")
            val store = TargetedCaptureStore(null)
            val viewModel = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = sequenceOf("draft-a", "draft-b").iterator()::next,
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                captureStore = store,
                pickedImageResolver = MeasurementPickedImageResolver {
                    store.replaceCurrent(owned)
                    MeasurementImageInput(owned.uri, owned)
                },
                ioDispatcher = io,
                defaultDispatcher = dispatcher,
            )

            viewModel.onAction(MeasurementAction.PickedImageSelected("content://provider/ephemeral"))
            runCurrent()
            io.runNext()
            assertEquals(owned, store.currentCapture())

            viewModel.abandonMeasurement()
            runCurrent()
            io.runNext()
            runCurrent()

            assertNull(store.currentCapture())
            assertEquals(listOf("picked-token"), store.clearedTokens)
            assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        }

    @Test
    fun restoredConfirmedCropIsRevalidatedAgainstSmallerDecodedImage() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                "measurement.draftId" to "draft-1",
                "measurement.imageUri" to "content://restored",
                "measurement.crop.left" to 0,
                "measurement.crop.top" to 0,
                "measurement.crop.right" to 700,
                "measurement.crop.bottom" to 500,
                "measurement.crop.confirmed" to true,
            ),
        )
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            savedStateHandle = handle,
            decoder = object : MeasurementImageDecoder {
                override suspend fun decode(uri: String) = FakeImage(200, 100)
            },
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()

        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals(CropBounds(20, 23, 180, 76), viewModel.state.value.cropRect)
    }

    @Test
    fun partialConfirmedCropStateIsRepairedToUnconfirmedDefault() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                "measurement.draftId" to "draft-1",
                "measurement.imageUri" to "content://restored-partial",
                "measurement.crop.left" to 5,
                "measurement.crop.top" to 10,
                "measurement.crop.right" to 700,
                "measurement.crop.confirmed" to true,
            ),
        )
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            savedStateHandle = handle,
            decoder = object : MeasurementImageDecoder {
                override suspend fun decode(uri: String) = FakeImage(200, 100)
            },
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()

        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals(CropBounds(20, 23, 180, 76), viewModel.state.value.cropRect)
        assertFalse(requireNotNull(handle.get<Boolean>("measurement.crop.confirmed")))
    }

    @Test
    fun cancelNonCooperativeAnalysisKeepsImageOwnedUntilOperationActuallyExits() =
        runTest(dispatcher) {
            val analyzer = RecordingAnalyzer().apply { nonCooperativeAnalysis = true }
            val image = ReleasableImage(800, 600)
            val repository = RecordingRepository()
            val viewModel = readyOwnedViewModel(
                image = image,
                repository = repository,
                analyzer = analyzer,
            )

            viewModel.onAction(MeasurementAction.Analyze)
            runCurrent()
            viewModel.onAction(MeasurementAction.CancelAnalysis)
            runCurrent()

            assertEquals(Stage.ReadyToAnalyze, viewModel.state.value.stage)
            assertEquals(0, image.releaseCalls)
            viewModel.onAction(MeasurementAction.Analyze)
            runCurrent()
            assertEquals(1, analyzer.calls)

            analyzer.releaseAnalysis()
            advanceUntilIdle()

            assertEquals(Stage.ReadyToAnalyze, viewModel.state.value.stage)
            assertEquals(0, image.releaseCalls)
            assertEquals(0, repository.commitCalls)
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
        viewModel.createNewSession("HOME", "Test")
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

    @Test
    fun cameraRequestLaunchesTheStoreOwnedUriAndSuccessfulCaptureDecodesIt() = runTest(dispatcher) {
        val captureStore = RecordingCaptureStore("content://measurement/camera")
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-camera" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            captureStore = captureStore,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )

        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
        advanceUntilIdle()

        val launch = viewModel.effects.first() as MeasurementEffect.LaunchCamera
        assertEquals("content://measurement/camera", launch.uri)
        val claim = requireNotNull(viewModel.claimCameraLaunch(launch))
        assertEquals("content://measurement/camera", claim.uri)
        viewModel.onAction(MeasurementAction.CameraLaunchAcknowledged(claim))
        assertEquals(1, captureStore.createCalls)

        viewModel.onAction(MeasurementAction.CameraCaptureCompleted(success = true))
        advanceUntilIdle()

        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals("content://measurement/camera", viewModel.state.value.imageUri)
        assertEquals(0, captureStore.clearCalls)
    }

    @Test
    fun abandoningWhileCameraUriIsBeingPreparedCannotLaunchCameraAfterExit() = runTest(dispatcher) {
        val io = ManualQueueDispatcher()
        val captureStore = RecordingCaptureStore("content://measurement/camera")
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-camera" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            captureStore = captureStore,
            ioDispatcher = io,
            defaultDispatcher = dispatcher,
        )

        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
        runCurrent()
        io.runNext()
        assertEquals(1, captureStore.createCalls)
        assertNotNull(captureStore.currentCapture())

        viewModel.abandonMeasurement()
        runCurrent()
        io.runNext()
        runCurrent()

        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
        assertNull(captureStore.currentCapture())
        assertEquals(1, captureStore.clearCalls)
    }

    @Test
    fun nonCooperativeCameraPreparationCompletingAfterCancelStillCleansItsCapture() =
        runTest(dispatcher) {
            val io = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            val store = BlockingCaptureStore()
            try {
                val viewModel = MeasurementViewModel(
                    repository = RecordingRepository(),
                    analyzer = RecordingAnalyzer(),
                    draftIdFactory = sequenceOf("draft-a", "draft-b").iterator()::next,
                    savedStateHandle = SavedStateHandle(),
                    decoder = RecordingDecoder(),
                    captureStore = store,
                    ioDispatcher = io,
                    defaultDispatcher = dispatcher,
                )

                viewModel.onAction(MeasurementAction.CameraCaptureRequested)
                runCurrent()
                assertTrue(store.awaitPreparationStarted())
                viewModel.abandonMeasurement()
                store.finishPreparation()

                repeat(200) {
                    runCurrent()
                    if (store.clearCalls == 1) return@repeat
                    Thread.sleep(5)
                }

                assertEquals(1, store.createCalls)
                assertEquals(1, store.clearCalls)
                assertNull(store.currentCapture())
                assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
            } finally {
                store.finishPreparation()
                io.close()
            }
        }

    @Test
    fun bufferedCameraLaunchIsRejectedAfterAbandonAndFreshDraft() = runTest(dispatcher) {
        val captureStore = TargetedCaptureStore(MeasurementCapture("content://camera/a", "capture-a"))
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = sequenceOf("draft-a", "draft-b").iterator()::next,
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            captureStore = captureStore,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
        advanceUntilIdle()

        viewModel.abandonMeasurement()
        advanceUntilIdle()
        val stale = viewModel.effects.first() as MeasurementEffect.LaunchCamera

        assertNull(viewModel.claimCameraLaunch(stale))
        assertEquals("draft-b", viewModel.state.value.draftId)
    }

    @Test
    fun claimedButUnacknowledgedCameraLaunchIsReissuedAfterViewModelRecreation() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val store = TargetedCaptureStore(MeasurementCapture("content://camera/a", "capture-a"))
        val first = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-a" },
            savedStateHandle = handle,
            decoder = RecordingDecoder(),
            captureStore = store,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        first.onAction(MeasurementAction.CameraCaptureRequested)
        advanceUntilIdle()
        val firstLaunch = first.effects.first() as MeasurementEffect.LaunchCamera
        assertNotNull(first.claimCameraLaunch(firstLaunch))

        val recreated = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "unexpected-draft" },
            savedStateHandle = handle,
            decoder = RecordingDecoder(),
            captureStore = store,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        val reissued = withTimeoutOrNull(100) {
            recreated.effects.first() as MeasurementEffect.LaunchCamera
        }

        assertEquals("content://camera/a", requireNotNull(reissued).uri)
        assertEquals("content://camera/a", requireNotNull(recreated.claimCameraLaunch(reissued)).uri)
        assertEquals("draft-a", recreated.state.value.draftId)
    }

    @Test
    fun repeatedCameraTapCannotReplaceAnOutstandingLaunchToken() = runTest(dispatcher) {
        val store = TargetedCaptureStore(MeasurementCapture("content://camera/a", "capture-a"))
        var nextRequest = 0
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-a" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            captureStore = store,
            captureRequestTokenFactory = { "request-${++nextRequest}" },
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )

        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
        advanceUntilIdle()
        val first = viewModel.effects.first() as MeasurementEffect.LaunchCamera
        val firstClaim = requireNotNull(viewModel.claimCameraLaunch(first))
        assertNull(viewModel.claimCameraLaunch(first))
        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
        advanceUntilIdle()

        assertEquals(1, store.createCalls)
        assertEquals("content://camera/a", firstClaim.uri)
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun cameraLaunchFailureRollsBackClaimShowsErrorAndRetryCanReclaim() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val store = TargetedCaptureStore(MeasurementCapture("content://camera/a", "capture-a"))
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-a" },
            savedStateHandle = handle,
            decoder = RecordingDecoder(),
            captureStore = store,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
        advanceUntilIdle()
        val launch = viewModel.effects.first() as MeasurementEffect.LaunchCamera
        val claim = requireNotNull(viewModel.claimCameraLaunch(launch))

        viewModel.onAction(
            MeasurementAction.CameraLaunchFailed(
                claim = claim,
                reason = "TakePicture launcher unavailable",
            ),
        )

        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
        assertEquals(
            MeasurementError.CameraLaunchFailed("TakePicture launcher unavailable"),
            viewModel.state.value.error,
        )
        viewModel.onAction(MeasurementAction.Retry)
        advanceUntilIdle()
        val retried = viewModel.effects.first() as MeasurementEffect.LaunchCamera
        val retryClaim = requireNotNull(viewModel.claimCameraLaunch(retried))
        viewModel.onAction(MeasurementAction.CameraLaunchAcknowledged(retryClaim))

        val recreated = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "unexpected" },
            savedStateHandle = handle,
            decoder = RecordingDecoder(),
            captureStore = store,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()

        assertNull(withTimeoutOrNull(1) { recreated.effects.first() })
        assertEquals("draft-a", recreated.state.value.draftId)
    }

    @Test
    fun cameraCancellationFinishesTargetedCleanupBeforeRetakeCanCreateAnotherUri() = runTest(dispatcher) {
        val io = ManualQueueDispatcher()
        val store = TargetedCaptureStore(MeasurementCapture("content://camera/a", "capture-a"))
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            captureStore = store,
            ioDispatcher = io,
            defaultDispatcher = dispatcher,
        )

        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
        runCurrent()
        io.runNext()
        runCurrent()
        assertEquals(1, store.createCalls)
        val launch = viewModel.effects.first() as MeasurementEffect.LaunchCamera
        val claim = requireNotNull(viewModel.claimCameraLaunch(launch))
        viewModel.onAction(MeasurementAction.CameraLaunchAcknowledged(claim))

        viewModel.onAction(MeasurementAction.CameraCaptureCompleted(success = false))
        runCurrent()
        assertEquals(Stage.Decoding, viewModel.state.value.stage)
        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
        runCurrent()

        assertEquals(1, store.createCalls)
        io.runNext()
        advanceUntilIdle()
        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        assertEquals(listOf("capture-a"), store.clearedTokens)
    }

    @Test
    fun stalePersistenceCannotDeleteTheNewFlowsCapture() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { nonCooperativeCommit = true }
        val store = TargetedCaptureStore(MeasurementCapture("content://camera/a", "capture-a"))
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = RecordingAnalyzer(),
            draftIdFactory = sequenceOf("draft-a", "draft-b").iterator()::next,
            savedStateHandle = SavedStateHandle(mapOf("measurement.sessionId" to "session-1")),
            decoder = RecordingDecoder(),
            captureStore = store,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.onAction(MeasurementAction.ImageSelected("content://camera/a"))
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CropConfirmed)
        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()

        viewModel.abandonMeasurement()
        advanceUntilIdle()
        store.replaceCurrent(MeasurementCapture("content://camera/b", "capture-b"))
        repository.releaseCommit()
        advanceUntilIdle()

        assertEquals("capture-b", store.currentCapture()?.token)
        assertFalse(store.clearedTokens.contains("capture-b"))
    }

    @Test
    fun committedResultRemainsSuccessWhenTempCleanupFailsAndWarningIsAuditable() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val store = TargetedCaptureStore(MeasurementCapture("content://camera/a", "capture-a")).apply {
            cleanupFailure = "cache file remained"
        }
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-a" },
            savedStateHandle = SavedStateHandle(mapOf("measurement.sessionId" to "session-1")),
            decoder = RecordingDecoder(),
            captureStore = store,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.onAction(MeasurementAction.ImageSelected("content://camera/a"))
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CropConfirmed)

        viewModel.onAction(MeasurementAction.Analyze)
        advanceUntilIdle()

        assertEquals(1, repository.commitCalls)
        assertEquals(Stage.Success, viewModel.state.value.stage)
        assertEquals("cache file remained", viewModel.state.value.captureCleanupWarning)
        assertEquals(MeasurementEffect.NavigateToResult("result-1"), viewModel.effects.first())
    }

    @Test
    fun successfulPersistenceClearsTheOwnedCameraFileOnlyAfterCommit() = runTest(dispatcher) {
        val repository = RecordingRepository().apply { suspendCommit = true }
        val captureStore = RecordingCaptureStore("content://measurement/camera")
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-camera" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            captureStore = captureStore,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.createNewSession("HOME", "Test")
        advanceUntilIdle()
        viewModel.acceptCreatedSession()
        viewModel.onAction(MeasurementAction.ImageSelected(requireNotNull(captureStore.currentCapture()).uri))
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CropConfirmed)

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        assertEquals(Stage.Persisting, viewModel.state.value.stage)
        assertEquals(0, captureStore.clearCalls)

        repository.releaseCommit()
        advanceUntilIdle()

        assertEquals(Stage.Success, viewModel.state.value.stage)
        assertEquals(1, captureStore.clearCalls)
    }

    @Test
    fun backFromCropReturnsToImageSelectionAndExplicitlyCancelsOwnedCapture() = runTest(dispatcher) {
        val image = ReleasableImage(800, 600)
        val captureStore = RecordingCaptureStore("content://measurement/camera")
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-back" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(decodedImage = image),
            captureStore = captureStore,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.onAction(
            MeasurementAction.ImageSelected(requireNotNull(captureStore.currentCapture()).uri),
        )
        advanceUntilIdle()
        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)

        viewModel.onAction(MeasurementAction.BackToImageSelection)
        advanceUntilIdle()

        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        assertNull(viewModel.state.value.image)
        assertNull(viewModel.state.value.imageUri)
        assertNull(viewModel.state.value.cropRect)
        assertEquals(1, image.releaseCalls)
        assertEquals(1, captureStore.clearCalls)
    }

    @Test
    fun backFromFactorReturnsToCropWithoutDiscardingRecoveryData() = runTest(dispatcher) {
        val viewModel = readyViewModel()
        val before = viewModel.state.value.recoveryData()

        viewModel.onAction(MeasurementAction.BackToCrop)

        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals(before, viewModel.state.value.recoveryData())
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun permissionIsPersistedBeforeLaunchAndFirstNoRationaleResultIsPermanent() =
        runTest(dispatcher) {
            val history = RecordingPermissionHistoryStore()
            val viewModel = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()
            viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
            advanceUntilIdle()

            assertEquals(listOf("mark"), history.events)
            assertTrue(viewModel.state.value.hasRequestedCameraPermission)
            val claim = viewModel.acknowledgePermissionLaunch()

            viewModel.onAction(
                MeasurementAction.CameraPermissionResult(
                    claim = claim,
                    granted = false,
                    shouldShowRationale = false,
                ),
            )
            assertEquals(
                MeasurementError.PermissionDenied(permanentlyDenied = true),
                viewModel.state.value.error,
            )
        }

    @Test
    fun takePhotoPermissionBoundaryWaitsForDurableEffectAndLaunchesExactlyOnce() =
        runTest(dispatcher) {
            val io = ManualQueueDispatcher()
            val history = RecordingPermissionHistoryStore()
            val viewModel = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = io,
                defaultDispatcher = dispatcher,
            )
            var permissionLaunches = 0
            var captureRequests = 0
            runCurrent()
            io.runAll()
            runCurrent()

            handleTakePhotoRequest(
                cameraPermissionGranted = false,
                onCaptureRequested = { captureRequests += 1 },
                onPermissionRequested = {
                    viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
                },
            )
            runCurrent()

            assertEquals(0, permissionLaunches)
            assertEquals(0, captureRequests)
            assertTrue(history.events.isEmpty())

            io.runAll()
            runCurrent()
            val effect = viewModel.effects.first() as MeasurementEffect.RequestCameraPermission
            val claim = requireNotNull(viewModel.claimCameraPermissionLaunch(effect))
            launchClaimedCameraPermission(
                claim = claim,
                launch = { permissionLaunches += 1 },
                onAcknowledged = {
                    viewModel.onAction(
                        MeasurementAction.CameraPermissionLaunchAcknowledged(claim),
                    )
                },
                onFailure = { reason ->
                    viewModel.onAction(
                        MeasurementAction.CameraPermissionLaunchFailed(claim, reason),
                    )
                },
            )

            assertEquals(listOf("mark"), history.events)
            assertEquals(1, permissionLaunches)
            assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
        }

    @Test
    fun firstPermissionRequestDuringHistoryLoadQueuesAndLaunchesExactlyOnceAfterRead() =
        runTest(dispatcher) {
            val io = ManualQueueDispatcher()
            val history = RecordingPermissionHistoryStore()
            val viewModel = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = io,
                defaultDispatcher = dispatcher,
            )
            runCurrent()

            viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
            runCurrent()

            assertEquals(0, history.readCalls)
            assertTrue(history.events.isEmpty())
            assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })

            io.runAll()
            runCurrent()
            io.runAll()
            runCurrent()

            assertEquals(1, history.readCalls)
            assertEquals(listOf("mark"), history.events)
            assertTrue(viewModel.effects.first() is MeasurementEffect.RequestCameraPermission)
            assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
        }

    @Test
    fun permissionIntentSurvivesRecreationWhileDurableMarkIsQueuedAndLaunchesExactlyOnce() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val io = ManualQueueDispatcher()
            val history = RecordingPermissionHistoryStore()
            val first = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "draft-a" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = io,
                defaultDispatcher = dispatcher,
            )
            val store = ViewModelStore().apply { put("measurement", first) }
            runCurrent()
            io.runAll()
            runCurrent()

            first.onAction(MeasurementAction.CameraPermissionRequestStarted)
            runCurrent()
            store.clear()

            val recreated = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "unexpected-draft" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = io,
                defaultDispatcher = dispatcher,
            )
            repeat(3) {
                runCurrent()
                io.runAll()
            }
            runCurrent()

            assertEquals(listOf("mark"), history.events)
            val effect = recreated.effects.first() as MeasurementEffect.RequestCameraPermission
            assertNotNull(recreated.claimCameraPermissionLaunch(effect))
            assertEquals("draft-a", recreated.state.value.draftId)
            assertNull(withTimeoutOrNull(1) { recreated.effects.first() })
        }

    @Test
    fun selectedImageWhilePermissionMarkCompletesCannotEmitPermissionOrBlockImageFlow() =
        runTest(dispatcher) {
            val io = ManualQueueDispatcher()
            val history = RecordingPermissionHistoryStore()
            val viewModel = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = io,
                defaultDispatcher = dispatcher,
            )
            runCurrent()
            io.runAll()
            runCurrent()

            viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
            runCurrent()
            io.runAll()
            viewModel.onAction(MeasurementAction.PickedImageSelected("content://gallery/photo"))
            runCurrent()
            io.runAll()
            runCurrent()

            assertEquals(listOf("mark"), history.events)
            assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
            assertEquals("content://gallery/photo", viewModel.state.value.imageUri)
            assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
        }

    @Test
    fun openingGalleryInvalidatesActivePermissionRequestAndDoesNotBlockNextCameraTap() =
        runTest(dispatcher) {
            val history = RecordingPermissionHistoryStore()
            var nextToken = 0
            val viewModel = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                permissionRequestTokenFactory = { "permission-${++nextToken}" },
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()
            viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
            advanceUntilIdle()
            val stale = viewModel.effects.first() as MeasurementEffect.RequestCameraPermission

            viewModel.onAction(MeasurementAction.GallerySelectionStarted)

            assertNull(viewModel.claimCameraPermissionLaunch(stale))
            viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
            advanceUntilIdle()
            val current = viewModel.effects.first() as MeasurementEffect.RequestCameraPermission
            assertNotNull(viewModel.claimCameraPermissionLaunch(current))
            assertNotEquals(stale.requestToken, current.requestToken)
            assertEquals(listOf("mark"), history.events)
        }

    @Test
    fun unacknowledgedPermissionLaunchIsReissuedExactlyOnceAfterViewModelRecreation() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val history = RecordingPermissionHistoryStore()
            val first = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "draft-a" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()
            first.onAction(MeasurementAction.CameraPermissionRequestStarted)
            advanceUntilIdle()
            val initial = first.effects.first() as MeasurementEffect.RequestCameraPermission
            assertNotNull(first.claimCameraPermissionLaunch(initial))

            val recreated = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "unexpected-draft" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()
            val reissued = recreated.effects.first() as MeasurementEffect.RequestCameraPermission

            assertNotNull(recreated.claimCameraPermissionLaunch(reissued))
            assertEquals("draft-a", recreated.state.value.draftId)
            assertNull(withTimeoutOrNull(1) { recreated.effects.first() })
        }

    @Test
    fun acknowledgedPermissionLaunchIsNotReissuedAfterViewModelRecreation() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val history = RecordingPermissionHistoryStore()
            val first = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "draft-a" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()
            first.onAction(MeasurementAction.CameraPermissionRequestStarted)
            advanceUntilIdle()
            val effect = first.effects.first() as MeasurementEffect.RequestCameraPermission
            val claim = requireNotNull(first.claimCameraPermissionLaunch(effect))
            first.onAction(MeasurementAction.CameraPermissionLaunchAcknowledged(claim))

            val recreated = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "unexpected-draft" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()

            assertNull(withTimeoutOrNull(1) { recreated.effects.first() })
            assertEquals("draft-a", recreated.state.value.draftId)
        }

    @Test
    fun synchronousPermissionLauncherFailureRollsBackAndRetryCanLaunch() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val history = RecordingPermissionHistoryStore()
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-a" },
            savedStateHandle = handle,
            decoder = RecordingDecoder(),
            cameraPermissionHistoryStore = history,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
        advanceUntilIdle()
        val effect = viewModel.effects.first() as MeasurementEffect.RequestCameraPermission
        val claim = requireNotNull(viewModel.claimCameraPermissionLaunch(effect))

        launchClaimedCameraPermission(
            claim = claim,
            launch = { error("permission launcher unavailable") },
            onAcknowledged = {
                viewModel.onAction(MeasurementAction.CameraPermissionLaunchAcknowledged(claim))
            },
            onFailure = { reason ->
                viewModel.onAction(MeasurementAction.CameraPermissionLaunchFailed(claim, reason))
            },
        )

        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
        assertEquals(
            MeasurementError.PermissionLaunchFailed("permission launcher unavailable"),
            viewModel.state.value.error,
        )
        viewModel.onAction(MeasurementAction.Retry)
        advanceUntilIdle()
        val retried = viewModel.effects.first() as MeasurementEffect.RequestCameraPermission
        val retryClaim = requireNotNull(viewModel.claimCameraPermissionLaunch(retried))
        launchClaimedCameraPermission(
            claim = retryClaim,
            launch = {},
            onAcknowledged = {
                viewModel.onAction(MeasurementAction.CameraPermissionLaunchAcknowledged(retryClaim))
            },
            onFailure = { reason ->
                viewModel.onAction(MeasurementAction.CameraPermissionLaunchFailed(retryClaim, reason))
            },
        )
        assertEquals(listOf("mark"), history.events)

        val recreated = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "unexpected-draft" },
            savedStateHandle = handle,
            decoder = RecordingDecoder(),
            cameraPermissionHistoryStore = history,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()
        assertNull(withTimeoutOrNull(1) { recreated.effects.first() })
        assertEquals(listOf("mark"), history.events)
    }

    @Test
    fun stalePermissionResultAfterNewDraftIsIgnored() = runTest(dispatcher) {
        val captureStore = RecordingCaptureStore("content://camera/pending")
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = sequenceOf("draft-a", "draft-b").iterator()::next,
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            captureStore = captureStore,
            cameraPermissionHistoryStore = RecordingPermissionHistoryStore(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
        advanceUntilIdle()
        val staleClaim = viewModel.acknowledgePermissionLaunch()

        viewModel.abandonMeasurement()
        advanceUntilIdle()
        viewModel.onAction(
            MeasurementAction.CameraPermissionResult(
                claim = staleClaim,
                granted = true,
                shouldShowRationale = false,
            ),
        )
        advanceUntilIdle()

        assertEquals("draft-b", viewModel.state.value.draftId)
        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        assertNull(viewModel.state.value.error)
        assertEquals(0, captureStore.createCalls)
        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
    }

    @Test
    fun acknowledgedPermissionRecreationKeepsGrantedResultWhenHistoryReadFails() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val captureStore = RecordingCaptureStore("content://camera/pending")
            val first = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "draft-a" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                captureStore = captureStore,
                cameraPermissionHistoryStore = RecordingPermissionHistoryStore(),
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()
            first.onAction(MeasurementAction.CameraPermissionRequestStarted)
            advanceUntilIdle()
            val claim = first.acknowledgePermissionLaunch()

            val failingHistory = RecordingPermissionHistoryStore(
                requested = true,
                readFailuresRemaining = 1,
            )
            val recreated = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "unexpected-draft" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                captureStore = captureStore,
                cameraPermissionHistoryStore = failingHistory,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()

            assertEquals(1, failingHistory.readCalls)
            assertEquals(Stage.AwaitingImage, recreated.state.value.stage)
            recreated.onAction(
                MeasurementAction.CameraPermissionResult(
                    claim = claim,
                    granted = true,
                    shouldShowRationale = false,
                ),
            )
            advanceUntilIdle()

            assertEquals(1, captureStore.createCalls)
            assertTrue(recreated.effects.first() is MeasurementEffect.LaunchCamera)
            assertNull(withTimeoutOrNull(1) { recreated.effects.first() })
        }

    @Test
    fun grantedPermissionBeforeRecreatedHistoryFailureStillLaunchesCamera() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val captureStore = RecordingCaptureStore("content://camera/pending")
            val first = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "draft-a" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                captureStore = captureStore,
                cameraPermissionHistoryStore = RecordingPermissionHistoryStore(),
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()
            first.onAction(MeasurementAction.CameraPermissionRequestStarted)
            advanceUntilIdle()
            val claim = first.acknowledgePermissionLaunch()

            val io = ManualQueueDispatcher()
            val failingHistory = RecordingPermissionHistoryStore(
                requested = true,
                readFailuresRemaining = 1,
            )
            val recreated = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = { "unexpected-draft" },
                savedStateHandle = handle,
                decoder = RecordingDecoder(),
                captureStore = captureStore,
                cameraPermissionHistoryStore = failingHistory,
                ioDispatcher = io,
                defaultDispatcher = dispatcher,
            )
            runCurrent()

            recreated.onAction(
                MeasurementAction.CameraPermissionResult(
                    claim = claim,
                    granted = true,
                    shouldShowRationale = false,
                ),
            )
            runCurrent()
            repeat(3) {
                io.runAll()
                runCurrent()
            }

            assertEquals(1, failingHistory.readCalls)
            assertEquals(Stage.AwaitingImage, recreated.state.value.stage)
            assertNull(recreated.state.value.error)
            assertEquals(1, captureStore.createCalls)
            assertTrue(recreated.effects.first() is MeasurementEffect.LaunchCamera)
            assertNull(withTimeoutOrNull(1) { recreated.effects.first() })
        }

    @Test
    fun permissionRequestAfterCancelledHistoryReadRestartsAuthorityBeforeMarking() =
        runTest(dispatcher) {
            val io = ManualQueueDispatcher()
            val history = RecordingPermissionHistoryStore()
            val viewModel = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                draftIdFactory = sequenceOf("draft-a", "draft-b").iterator()::next,
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = io,
                defaultDispatcher = dispatcher,
            )
            runCurrent()
            viewModel.abandonMeasurement()
            io.runAll()
            runCurrent()

            viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
            runCurrent()
            io.runAll()
            runCurrent()
            io.runAll()
            runCurrent()

            assertEquals(1, history.readCalls)
            assertEquals(listOf("mark"), history.events)
            assertTrue(viewModel.effects.first() is MeasurementEffect.RequestCameraPermission)
            assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
        }

    @Test
    fun permissionHistoryReadFailureIsFormalAndRetryRestoresAuthoritativeTruth() =
        runTest(dispatcher) {
            val history = RecordingPermissionHistoryStore(
                requested = true,
                readFailuresRemaining = 1,
            )
            val viewModel = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                cameraPermissionHistoryStore = history,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()

            assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
            assertEquals(
                MeasurementError.PermissionHistoryUnavailable,
                viewModel.state.value.error,
            )
            assertEquals(Stage.AwaitingImage, viewModel.state.value.resumeStage)
            assertFalse(viewModel.state.value.hasRequestedCameraPermission)

            viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
            advanceUntilIdle()
            assertTrue(history.events.isEmpty())
            assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })

            viewModel.onAction(MeasurementAction.Retry)
            advanceUntilIdle()

            assertEquals(2, history.readCalls)
            assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
            assertNull(viewModel.state.value.error)
            assertNull(viewModel.state.value.resumeStage)
            assertTrue(viewModel.state.value.hasRequestedCameraPermission)
        }

    @Test
    fun rationaleResultRemainsRecoverableAfterFormalPermissionRequest() = runTest(dispatcher) {
        val history = RecordingPermissionHistoryStore()
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            cameraPermissionHistoryStore = history,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
        advanceUntilIdle()
        val claim = viewModel.acknowledgePermissionLaunch()

        viewModel.onAction(
            MeasurementAction.CameraPermissionResult(
                claim = claim,
                granted = false,
                shouldShowRationale = true,
            ),
        )

        assertEquals(
            MeasurementError.PermissionDenied(permanentlyDenied = false),
            viewModel.state.value.error,
        )
    }

    @Test
    fun processPersistentPermissionHistoryAloneCannotAuthorizeActivityResult() =
        runTest(dispatcher) {
            val history = RecordingPermissionHistoryStore(requested = true)
            val captureStore = RecordingCaptureStore("content://camera/pending")
            val cold = MeasurementViewModel(
                repository = RecordingRepository(),
                analyzer = RecordingAnalyzer(),
                savedStateHandle = SavedStateHandle(),
                decoder = RecordingDecoder(),
                captureStore = captureStore,
                cameraPermissionHistoryStore = history,
                ioDispatcher = dispatcher,
                defaultDispatcher = dispatcher,
            )
            advanceUntilIdle()

            assertTrue(cold.state.value.hasRequestedCameraPermission)
            cold.onAction(
                MeasurementAction.CameraPermissionResult(
                    claim = CameraPermissionLaunchClaim("stale-token", cold.state.value.draftId),
                    granted = true,
                    shouldShowRationale = false,
                ),
            )
            advanceUntilIdle()

            assertEquals(Stage.AwaitingImage, cold.state.value.stage)
            assertNull(cold.state.value.error)
            assertEquals(0, captureStore.createCalls)
            assertNull(withTimeoutOrNull(1) { cold.effects.first() })
        }

    private fun TestScope.readyViewModel(
        repository: RecordingRepository = RecordingRepository(),
        analyzer: RecordingAnalyzer = RecordingAnalyzer(),
        draftIdFactory: () -> String = { "draft-1" },
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): MeasurementViewModel {
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = analyzer,
            draftIdFactory = draftIdFactory,
            savedStateHandle = savedStateHandle,
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.createNewSession("HOME", "Test")
        testScheduler.advanceUntilIdle()
        viewModel.acceptCreatedSession()
        viewModel.onAction(MeasurementAction.ImageSelected("content://measurement/photo"))
        testScheduler.advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(10, 20, 310, 120)))
        viewModel.onAction(MeasurementAction.CropConfirmed)
        return viewModel
    }

    private fun TestScope.readyOwnedViewModel(
        image: ReleasableImage,
        repository: RecordingRepository = RecordingRepository(),
        analyzer: RecordingAnalyzer = RecordingAnalyzer(),
    ): MeasurementViewModel {
        val viewModel = MeasurementViewModel(
            repository = repository,
            analyzer = analyzer,
            draftIdFactory = { "draft-owned" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.createNewSession("HOME", "Test")
        testScheduler.advanceUntilIdle()
        viewModel.acceptCreatedSession()
        viewModel.setImage(image)
        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(10, 20, 310, 120)))
        viewModel.onAction(MeasurementAction.CropConfirmed)
        return viewModel
    }

    private suspend fun TestScope.verifyPermissionDenialRecovery(permanentlyDenied: Boolean) {
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "permission-draft" },
            savedStateHandle = SavedStateHandle(),
            decoder = RecordingDecoder(),
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.createNewSession("HOME", "Test")
        testScheduler.advanceUntilIdle()
        viewModel.acceptCreatedSession()
        val recoveryData = viewModel.state.value.let {
            listOf(it.draftId, it.imageUri, it.cropRect, it.originDestination)
        }

        viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
        testScheduler.advanceUntilIdle()
        val claim = viewModel.acknowledgePermissionLaunch()
        viewModel.onAction(
            MeasurementAction.CameraPermissionResult(
                claim = claim,
                granted = false,
                shouldShowRationale = !permanentlyDenied,
            ),
        )

        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
        assertEquals(
            MeasurementError.PermissionDenied(permanentlyDenied),
            viewModel.state.value.error,
        )
        assertEquals(Stage.AwaitingImage, viewModel.state.value.resumeStage)
        assertEquals(
            recoveryData,
            viewModel.state.value.let {
                listOf(it.draftId, it.imageUri, it.cropRect, it.originDestination)
            },
        )

        viewModel.onAction(MeasurementAction.Retry)

        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.resumeStage)
        assertEquals(
            recoveryData,
            viewModel.state.value.let {
                listOf(it.draftId, it.imageUri, it.cropRect, it.originDestination)
            },
        )
    }
}

private fun MeasurementUiState.recoveryData(): List<Any?> =
    listOf(draftId, imageUri, cropRect, originDestination)

private suspend fun MeasurementViewModel.acknowledgePermissionLaunch(): CameraPermissionLaunchClaim {
    val effect = effects.first() as MeasurementEffect.RequestCameraPermission
    val claim = requireNotNull(claimCameraPermissionLaunch(effect))
    onAction(MeasurementAction.CameraPermissionLaunchAcknowledged(claim))
    return claim
}

private fun MeasurementViewModel.acceptCreatedSession(): String {
    val request = requireNotNull(state.value.sessionCreationRequest)
    return requireNotNull(acceptSessionCreation(request.requestId))
}

private class RecordingDecoder(
    private val activeBoundary: ThreadLocal<String?>? = null,
    private val decodedImage: MeasurementImage? = null,
) : MeasurementImageDecoder {
    var boundary: String? = null
        private set

    override suspend fun decode(uri: String): MeasurementImage {
        boundary = activeBoundary?.get()
        return decodedImage ?: FakeImage(width = 800, height = 600)
    }
}

private class RecordingCaptureStore(
    pendingCaptureUri: String?,
) : MeasurementCaptureStore {
    private var current = pendingCaptureUri?.let { MeasurementCapture(it, "recording-capture") }
    var createCalls = 0
    var clearCalls = 0

    override fun currentCapture(): MeasurementCapture? = current

    override fun createOrRestorePending(): MeasurementCapture {
        createCalls += 1
        return requireNotNull(current)
    }

    override fun importOwned(write: (java.io.OutputStream) -> Unit): MeasurementCapture =
        error("not used")

    override fun clearExpected(expected: MeasurementCapture): CaptureCleanupResult {
        if (current != expected) return CaptureCleanupResult.NotCurrent
        clearCalls += 1
        current = null
        return CaptureCleanupResult.Removed
    }
}

private class RecordingPermissionHistoryStore(
    requested: Boolean = false,
    private var readFailuresRemaining: Int = 0,
) : CameraPermissionHistoryStore {
    private var value = requested
    val events = mutableListOf<String>()
    var readCalls = 0
        private set

    override fun wasRequested(): Boolean {
        readCalls += 1
        if (readFailuresRemaining-- > 0) error("permission history unavailable")
        return value
    }

    override fun markRequested() {
        events += "mark"
        value = true
    }
}

private class BlockingCaptureStore : MeasurementCaptureStore {
    private val preparationStarted = CountDownLatch(1)
    private val preparationRelease = CountDownLatch(1)

    @Volatile
    private var current: MeasurementCapture? = null

    @Volatile
    var createCalls = 0
        private set

    @Volatile
    var clearCalls = 0
        private set

    override fun currentCapture(): MeasurementCapture? = current

    override fun createOrRestorePending(): MeasurementCapture {
        createCalls += 1
        preparationStarted.countDown()
        preparationRelease.await()
        return MeasurementCapture(
            uri = "content://camera/non-cooperative",
            token = "non-cooperative-capture",
        ).also { current = it }
    }

    override fun importOwned(write: (java.io.OutputStream) -> Unit): MeasurementCapture =
        error("not used")

    override fun clearExpected(expected: MeasurementCapture): CaptureCleanupResult {
        if (current != expected) return CaptureCleanupResult.NotCurrent
        clearCalls += 1
        current = null
        return CaptureCleanupResult.Removed
    }

    fun awaitPreparationStarted(): Boolean = preparationStarted.await(2, TimeUnit.SECONDS)

    fun finishPreparation() {
        preparationRelease.countDown()
    }
}

private class TargetedCaptureStore(
    private var current: MeasurementCapture?,
) : MeasurementCaptureStore {
    var createCalls = 0
    var cleanupFailure: String? = null
    val clearedTokens = mutableListOf<String>()

    override fun currentCapture(): MeasurementCapture? = current

    override fun createOrRestorePending(): MeasurementCapture {
        createCalls += 1
        return requireNotNull(current)
    }

    override fun importOwned(write: (java.io.OutputStream) -> Unit): MeasurementCapture =
        error("not used")

    override fun clearExpected(expected: MeasurementCapture): CaptureCleanupResult {
        val failure = cleanupFailure
        if (failure != null) return CaptureCleanupResult.Failed(failure)
        if (current != expected) return CaptureCleanupResult.NotCurrent
        clearedTokens += expected.token
        current = null
        return CaptureCleanupResult.Removed
    }

    fun replaceCurrent(capture: MeasurementCapture) {
        current = capture
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
    var nonCooperativeAnalysis = false
    var boundary: String? = null
        private set
    private val analysisGate = CompletableDeferred<Unit>()

    fun releaseAnalysis() {
        analysisGate.complete(Unit)
    }

    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
    ): List<BaselineAnalysisResult> {
        calls += 1
        boundary = activeBoundary?.get()
        if (nonCooperativeAnalysis) {
            withContext(NonCancellable) { analysisGate.await() }
        } else if (suspendAnalysis) {
            analysisGate.await()
        }
        if (failuresRemaining-- > 0) error("analysis failed")
        return inflammationFactorPresentationOrder.mapIndexed { index, factor ->
            BaselineAnalysisResult(
                factor = factor,
                concentration = 42f + index,
                rangeStatus = RangeStatus.IN_RANGE,
                features = RgbFeatures(10f + index, 20f, 30f + index, 1f, 2f, 3f),
                rawSignal = 20f,
                signalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
            )
        }
    }
}

private class RecordingRepository(
    private val activeBoundary: ThreadLocal<String?>? = null,
) : TestSessionRepository {
    private val nextSession = AtomicInteger(1)
    private val nextResult = AtomicInteger(1)
    private val commitGate = CompletableDeferred<String>()
    private var commitContinuation: Continuation<String>? = null
    val sessions = MutableStateFlow<List<TestSession>>(emptyList())
    val committedDrafts = mutableListOf<String>()
    var commitCalls = 0
        private set
    var commitFailuresRemaining = 0
    var suspendCommit = false
    var nonCooperativeCommit = false
    var delaySnapshotAfterCommit = false
    var commitBoundary: String? = null
        private set
    private var delayedCommitSnapshot: List<TestSession>? = null

    fun emitDelayedCommitSnapshot() {
        sessions.value = requireNotNull(delayedCommitSnapshot)
        delayedCommitSnapshot = null
    }

    fun releaseCommit(id: String = "result-1") {
        if (nonCooperativeCommit) {
            requireNotNull(commitContinuation).resumeWith(Result.success(id))
        } else {
            commitGate.complete(id)
        }
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
        val id = when {
            nonCooperativeCommit -> suspendCoroutine { commitContinuation = it }
            suspendCommit -> commitGate.await()
            else -> "result-${nextResult.getAndIncrement()}"
        }
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
        val committedSnapshot = sessions.value.map { session ->
            if (session.id == sessionId && session.results.none { it.draftId == draftId }) {
                session.copy(results = session.results + stored)
            } else {
                session
            }
        }
        if (delaySnapshotAfterCommit) {
            delayedCommitSnapshot = committedSnapshot
        } else {
            sessions.value = committedSnapshot
        }
        return id
    }

    override suspend fun commitMeasurement(
        sessionId: String,
        draftId: String,
        measurement: NewMeasurementBatch,
    ): String {
        commitCalls += 1
        committedDrafts += draftId
        commitBoundary = activeBoundary?.get()
        if (commitFailuresRemaining-- > 0) error("persistence failed")
        val batchId = when {
            nonCooperativeCommit -> suspendCoroutine { commitContinuation = it }
            suspendCommit -> commitGate.await()
            else -> "result-${nextResult.getAndIncrement()}"
        }
        val stored = measurement.results.mapIndexed { index, result ->
            TestResult(
                id = if (index == 0) batchId else "$batchId-${result.factor.name.lowercase()}",
                sessionId = sessionId,
                draftId = null,
                factor = result.factor,
                concentration = result.concentration,
                rangeStatus = result.rangeStatus,
                features = result.features,
                timestamp = measurement.timestamp,
                measurementBatchId = batchId,
            )
        }
        val committedSnapshot = sessions.value.map { session ->
            if (session.id == sessionId && session.results.none { it.measurementBatchId == batchId }) {
                session.copy(results = session.results + stored)
            } else {
                session
            }
        }
        if (delaySnapshotAfterCommit) {
            delayedCommitSnapshot = committedSnapshot
        } else {
            sessions.value = committedSnapshot
        }
        return batchId
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

private class ManualQueueDispatcher : CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks += block
    }

    fun runNext() {
        tasks.removeFirst().run()
    }

    fun runAll() {
        while (tasks.isNotEmpty()) runNext()
    }
}
