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
    fun onePixelImageGetsAConfirmableDefaultCrop() = runTest(dispatcher) {
        val viewModel = MeasurementViewModel(
            repository = RecordingRepository(),
            analyzer = RecordingAnalyzer(),
            draftIdFactory = { "draft-1" },
        )

        viewModel.setImage(FakeImage(1, 1))

        assertEquals(CropBounds(0, 0, 1, 1), viewModel.state.value.cropRect)
        viewModel.onAction(MeasurementAction.CropConfirmed)
        assertEquals(Stage.ReadyToAnalyze, viewModel.state.value.stage)
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
        viewModel.onAction(MeasurementAction.PermissionDenied(permanentlyDenied = true))
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
        viewModel.onAction(MeasurementAction.PermissionDenied(permanentlyDenied = false))
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
        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(10, 20, 310, 220)))
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
        assertEquals(CropBounds(50, 25, 150, 75), viewModel.state.value.cropRect)
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
        assertEquals(CropBounds(50, 25, 150, 75), viewModel.state.value.cropRect)
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

        assertEquals(
            MeasurementEffect.LaunchCamera("content://measurement/camera"),
            viewModel.effects.first(),
        )
        assertEquals(1, captureStore.createCalls)

        viewModel.onAction(MeasurementAction.CameraCaptureCompleted(success = true))
        advanceUntilIdle()

        assertEquals(Stage.ReadyToCrop, viewModel.state.value.stage)
        assertEquals("content://measurement/camera", viewModel.state.value.imageUri)
        assertEquals(0, captureStore.cancelCalls)
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
        viewModel.abandonMeasurement()
        io.runNext()
        advanceUntilIdle()

        assertNull(withTimeoutOrNull(1) { viewModel.effects.first() })
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
        viewModel.createNewSession("HOME")
        advanceUntilIdle()
        viewModel.acceptCreatedSession()
        viewModel.onAction(MeasurementAction.ImageSelected(captureStore.pendingCaptureUri!!))
        advanceUntilIdle()
        viewModel.onAction(MeasurementAction.CropConfirmed)

        viewModel.onAction(MeasurementAction.Analyze)
        runCurrent()
        assertEquals(Stage.Persisting, viewModel.state.value.stage)
        assertEquals(0, captureStore.successCalls)

        repository.releaseCommit()
        advanceUntilIdle()

        assertEquals(Stage.Success, viewModel.state.value.stage)
        assertEquals(1, captureStore.successCalls)
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
            decoder = RecordingDecoder(),
            captureStore = captureStore,
            ioDispatcher = dispatcher,
            defaultDispatcher = dispatcher,
        )
        viewModel.setImage(image)

        viewModel.onAction(MeasurementAction.BackToImageSelection)
        advanceUntilIdle()

        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        assertNull(viewModel.state.value.image)
        assertNull(viewModel.state.value.imageUri)
        assertNull(viewModel.state.value.cropRect)
        assertEquals(1, image.releaseCalls)
        assertEquals(1, captureStore.cancelCalls)
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
        viewModel.createNewSession("HOME")
        testScheduler.advanceUntilIdle()
        viewModel.acceptCreatedSession()
        viewModel.setImage(image)
        viewModel.onAction(MeasurementAction.CropChanged(CropBounds(10, 20, 310, 220)))
        viewModel.onAction(MeasurementAction.CropConfirmed)
        return viewModel
    }

    private fun TestScope.verifyPermissionDenialRecovery(permanentlyDenied: Boolean) {
        val viewModel = readyViewModel()
        viewModel.onAction(MeasurementAction.FactorSelected(InflammationFactor.IL1_BETA))
        val recoveryData = viewModel.state.value.let {
            listOf(it.draftId, it.imageUri, it.cropRect, it.factor, it.originDestination)
        }

        viewModel.onAction(MeasurementAction.PermissionDenied(permanentlyDenied))

        assertEquals(Stage.RecoverableError, viewModel.state.value.stage)
        assertEquals(
            MeasurementError.PermissionDenied(permanentlyDenied),
            viewModel.state.value.error,
        )
        assertEquals(Stage.AwaitingImage, viewModel.state.value.resumeStage)
        assertEquals(
            recoveryData,
            viewModel.state.value.let {
                listOf(it.draftId, it.imageUri, it.cropRect, it.factor, it.originDestination)
            },
        )

        viewModel.onAction(MeasurementAction.Retry)

        assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        assertNull(viewModel.state.value.error)
        assertNull(viewModel.state.value.resumeStage)
        assertEquals(
            recoveryData,
            viewModel.state.value.let {
                listOf(it.draftId, it.imageUri, it.cropRect, it.factor, it.originDestination)
            },
        )
    }
}

private fun MeasurementUiState.recoveryData(): List<Any?> =
    listOf(draftId, imageUri, cropRect, factor, originDestination)

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

private class RecordingCaptureStore(
    override var pendingCaptureUri: String?,
) : MeasurementCaptureStore {
    var createCalls = 0
    var successCalls = 0
    var cancelCalls = 0

    override fun createOrRestorePendingUri(): String {
        createCalls += 1
        return requireNotNull(pendingCaptureUri)
    }

    override fun onPersistenceSucceeded() {
        successCalls += 1
        pendingCaptureUri = null
    }

    override fun onFlowCancelled() {
        cancelCalls += 1
        pendingCaptureUri = null
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
        factor: InflammationFactor,
    ): BaselineAnalysisResult {
        calls += 1
        boundary = activeBoundary?.get()
        if (nonCooperativeAnalysis) {
            withContext(NonCancellable) { analysisGate.await() }
        } else if (suspendAnalysis) {
            analysisGate.await()
        }
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
    private var commitContinuation: Continuation<String>? = null
    val sessions = MutableStateFlow<List<TestSession>>(emptyList())
    val committedDrafts = mutableListOf<String>()
    var commitCalls = 0
        private set
    var commitFailuresRemaining = 0
    var suspendCommit = false
    var nonCooperativeCommit = false
    var commitBoundary: String? = null
        private set

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

private class ManualQueueDispatcher : CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks += block
    }

    fun runNext() {
        tasks.removeFirst().run()
    }
}
