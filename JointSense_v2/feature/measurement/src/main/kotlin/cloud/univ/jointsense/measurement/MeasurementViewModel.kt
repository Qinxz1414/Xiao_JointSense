package cloud.univ.jointsense.measurement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MeasurementViewModel(
    private val repository: TestSessionRepository,
    private val analyzer: BaselinePhotoAnalysisAdapter,
    private val draftIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val savedStateHandle: SavedStateHandle,
    private val decoder: MeasurementImageDecoder?,
    private val captureStore: MeasurementCaptureStore? = null,
    private val pickedImageResolver: MeasurementPickedImageResolver =
        MeasurementPickedImageResolver { MeasurementImageInput(it, null) },
    private val cameraPermissionHistoryStore: CameraPermissionHistoryStore? = null,
    private val captureRequestTokenFactory: () -> String = { UUID.randomUUID().toString() },
    private val permissionRequestTokenFactory: () -> String = { UUID.randomUUID().toString() },
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    /** Transitional constructor for Phase-1 callers; production uses the dispatcher-aware factory. */
    constructor(
        repository: TestSessionRepository,
        analyzer: BaselinePhotoAnalysisAdapter,
        draftIdFactory: () -> String = { UUID.randomUUID().toString() },
    ) : this(
        repository = repository,
        analyzer = analyzer,
        draftIdFactory = draftIdFactory,
        savedStateHandle = SavedStateHandle(),
        decoder = null,
        captureStore = null,
        ioDispatcher = Dispatchers.Main.immediate,
        defaultDispatcher = Dispatchers.Main.immediate,
    )

    private val mutableState = MutableStateFlow(restoredState())
    val state: StateFlow<MeasurementUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<MeasurementEffect>(Channel.BUFFERED)
    val effects: Flow<MeasurementEffect> = effectChannel.receiveAsFlow()

    private var currentSessionId: String? = savedStateHandle[KEY_SESSION_ID]
    private var retryPersistence: PersistenceOperationSnapshot? = null
    private var analysisJob: Job? = null
    private var decodeJob: Job? = null
    private var captureRequestJob: Job? = null
    private var permissionRequestJob: Job? = null
    private var permissionRequestGeneration = 0L
    private var permissionHistoryReadJob: Job? = null
    private var permissionHistoryReadGeneration = 0L
    private var permissionHistoryReady = cameraPermissionHistoryStore == null
    private var captureCleanupJob: Job? = null
    private val captureMutex = Mutex()
    private var selectedCapture: MeasurementCapture? = null
    private var activeCameraRequest: CameraRequestSnapshot? = restoredCameraRequest()
    private var activePermissionRequest: PermissionRequestSnapshot? = restoredPermissionRequest()
    private var permissionRequestIntentQueued: Boolean =
        savedStateHandle[KEY_PERMISSION_REQUEST_INTENT_QUEUED] ?: false
    private var decodeGeneration = 0L
    private var nextOperationToken = 0L
    private var activeOperation: ActiveOperation? = null
    private var cropConfirmed: Boolean = savedStateHandle[KEY_CROP_CONFIRMED] ?: false
    private var nextSessionCreationId = 0L
    private var activeSessionCreationId: Long? = null

    init {
        viewModelScope.launch {
            repository.observeSessions().collectLatest(::applySessions)
        }
        state.value.imageUri?.takeIf { decoder != null }?.let {
            decodeImage(it, restoring = true, picked = false)
        }
        restorePermissionHistory()
        activePermissionRequest?.let { request ->
            persistPermissionRequest(request)
            clearQueuedPermissionRequestIntent()
            reissuePermissionRequest()
        }
        if (activePermissionRequest == null && permissionRequestIntentQueued && permissionHistoryReady) {
            recordCameraPermissionRequest()
        }
        reissueCameraRequest()
    }

    fun onAction(action: MeasurementAction) {
        when (action) {
            is MeasurementAction.ImageSelected -> if (!blocksMutuallyExclusiveInputs()) {
                decodeImage(action.uri, restoring = false, picked = false)
            }
            is MeasurementAction.PickedImageSelected -> if (!blocksMutuallyExclusiveInputs()) {
                decodeImage(action.uri, restoring = false, picked = true)
            }
            MeasurementAction.GallerySelectionStarted -> if (!blocksMutuallyExclusiveInputs()) {
                invalidatePermissionRequestFlow()
            }
            MeasurementAction.CameraCaptureRequested -> if (!blocksMutuallyExclusiveInputs()) {
                invalidatePermissionRequestFlow()
                requestCameraCapture()
            }
            is MeasurementAction.CameraCaptureCompleted -> if (!blocksMutuallyExclusiveInputs()) {
                completeCameraCapture(action.success)
            }
            is MeasurementAction.CameraLaunchAcknowledged -> acknowledgeCameraLaunch(action.claim)
            is MeasurementAction.CameraLaunchFailed -> failCameraLaunch(action.claim, action.reason)
            MeasurementAction.CameraPermissionRequestStarted -> recordCameraPermissionRequest()
            is MeasurementAction.CameraPermissionLaunchAcknowledged ->
                acknowledgeCameraPermissionLaunch(action.claim)
            is MeasurementAction.CameraPermissionLaunchFailed ->
                failCameraPermissionLaunch(action.claim, action.reason)
            is MeasurementAction.CameraPermissionResult -> completeCameraPermissionRequest(action)
            is MeasurementAction.CropChanged -> if (!blocksMutuallyExclusiveInputs()) {
                updateCrop(action.bounds)
            }
            MeasurementAction.CropConfirmed -> if (!blocksMutuallyExclusiveInputs()) confirmCrop()
            is MeasurementAction.FactorSelected -> if (!blocksMutuallyExclusiveInputs()) {
                updateState { it.copy(factor = action.factor, error = null, errorMessage = null) }
            }
            MeasurementAction.Analyze -> startAnalysis()
            MeasurementAction.Retry -> retry()
            MeasurementAction.CancelAnalysis -> cancelAnalysis()
            MeasurementAction.BackToImageSelection -> if (!blocksMutuallyExclusiveInputs()) {
                returnToImageSelection()
            }
            MeasurementAction.BackToCrop -> if (!blocksMutuallyExclusiveInputs()) returnToCrop()
            MeasurementAction.ContinueMeasurement -> if (!blocksMutuallyExclusiveInputs()) beginNewDraft()
        }
    }

    fun createNewSession(originIdentity: String, sessionNamePrefix: String) {
        require(sessionNamePrefix.isNotBlank()) { "Session name prefix must not be blank." }
        if (state.value.isCreatingSession) return
        val creationId = ++nextSessionCreationId
        activeSessionCreationId = creationId
        updateState {
            it.copy(
                originDestination = originIdentity,
                isCreatingSession = true,
                sessionCreationRequest = SessionCreationRequest(creationId, originIdentity),
                sessionCreationError = null,
            )
        }
        val requestedName = nextSessionName(state.value.sessions.map(TestSession::name), sessionNamePrefix)
        viewModelScope.launch {
            try {
                val id = withContext(ioDispatcher) { repository.createSession(requestedName) }
                if (activeSessionCreationId != creationId) {
                    withContext(ioDispatcher) { repository.deleteSession(id) }
                    return@launch
                }
                mutableState.update { current ->
                    val request = current.sessionCreationRequest
                    if (request?.requestId == creationId) {
                        current.copy(sessionCreationRequest = request.copy(completedSessionId = id))
                    } else {
                        current
                    }
                }
            } catch (error: CancellationException) {
                clearCreationIfOwned(creationId)
                throw error
            } catch (error: Exception) {
                if (activeSessionCreationId == creationId) {
                    activeSessionCreationId = null
                    mutableState.update {
                        it.copy(
                            isCreatingSession = false,
                            sessionCreationRequest = null,
                            sessionCreationError = error.message ?: error::class.java.simpleName,
                        )
                    }
                }
            }
        }
    }

    fun acceptSessionCreation(requestId: Long): String? {
        val request = state.value.sessionCreationRequest ?: return null
        val sessionId = request.completedSessionId ?: return null
        if (request.requestId != requestId || activeSessionCreationId != requestId) return null
        activeSessionCreationId = null
        currentSessionId = sessionId
        savedStateHandle[KEY_SESSION_ID] = sessionId
        beginNewDraft()
        mutableState.update { current ->
            current.copy(isCreatingSession = false, sessionCreationRequest = null)
        }
        applySessions(state.value.sessions)
        return sessionId
    }

    fun cancelSessionCreation(requestId: Long) {
        val request = state.value.sessionCreationRequest ?: return
        if (request.requestId != requestId) return
        activeSessionCreationId = null
        mutableState.update { current ->
            current.copy(isCreatingSession = false, sessionCreationRequest = null)
        }
        request.completedSessionId?.let { sessionId ->
            viewModelScope.launch { withContext(ioDispatcher) { repository.deleteSession(sessionId) } }
        }
    }

    fun consumeSessionCreationError() {
        mutableState.update { it.copy(sessionCreationError = null) }
    }

    fun selectSession(id: String) {
        if (blocksMutuallyExclusiveInputs()) return
        currentSessionId = id
        savedStateHandle[KEY_SESSION_ID] = id
        updateState { it.copy(resultId = null) }
        applySessions(state.value.sessions)
    }

    fun deleteSession(id: String) {
        if (blocksMutuallyExclusiveInputs() && currentSessionId == id) return
        viewModelScope.launch {
            withContext(ioDispatcher) { repository.deleteSession(id) }
            if (currentSessionId == id) clearTransient()
        }
    }

    fun abandonMeasurement() {
        val session = state.value.currentSession
        state.value.sessionCreationRequest?.let { cancelSessionCreation(it.requestId) }
        activeSessionCreationId = null
        clearTransient()
        viewModelScope.launch {
            if (session != null && session.results.isEmpty()) {
                withContext(ioDispatcher) { repository.deleteSession(session.id) }
            }
        }
    }

    fun finishMeasurement() {
        clearTransient()
    }

    fun setImage(image: MeasurementImage) {
        if (blocksMutuallyExclusiveInputs()) {
            image.release()
            return
        }
        invalidatePermissionRequestFlow()
        decodeJob?.cancel()
        selectedCapture = null
        state.value.image?.takeIf { it !== image }?.release()
        cropConfirmed = false
        retryPersistence = null
        updateState {
            it.copy(
                stage = Stage.ReadyToCrop,
                imageUri = null,
                image = image,
                cropRect = defaultCrop(image),
                error = null,
                resumeStage = null,
                errorMessage = null,
            )
        }
    }

    fun updateCropBounds(bounds: CropBounds) {
        onAction(MeasurementAction.CropChanged(bounds))
    }

    fun selectFactor(factor: InflammationFactor) {
        onAction(MeasurementAction.FactorSelected(factor))
    }

    fun analyze() {
        if (state.value.stage == Stage.ReadyToCrop) confirmCrop()
        onAction(MeasurementAction.Analyze)
    }

    fun startNewTestInSession() {
        onAction(MeasurementAction.ContinueMeasurement)
    }

    private fun decodeImage(uri: String, restoring: Boolean, picked: Boolean) {
        if (!restoring) invalidatePermissionRequestFlow()
        val imageDecoder = decoder ?: run {
            if (!restoring) setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
            return
        }
        decodeJob?.cancel()
        val generation = ++decodeGeneration
        if (!restoring) {
            state.value.image?.release()
            selectedCapture = null
            cropConfirmed = false
            retryPersistence = null
        }
        updateState {
            it.copy(
                stage = Stage.Decoding,
                imageUri = if (picked) null else uri,
                cropRect = if (restoring) it.cropRect else null,
                image = null,
                error = null,
                resumeStage = null,
                resultId = null,
                errorMessage = null,
            )
        }
        decodeJob = viewModelScope.launch {
            var ownedImage: MeasurementImage? = null
            var acquiredCapture: MeasurementCapture? = null
            var captureAdopted = false
            try {
                val (input, decoded) = withContext(ioDispatcher) {
                    captureCleanupJob?.join()
                    captureMutex.withLock {
                        val resolved = if (picked) {
                            val existing = captureStore?.currentCapture()
                            if (existing != null) {
                                when (val cleanup = captureStore.clearExpected(existing)) {
                                    CaptureCleanupResult.Removed,
                                    CaptureCleanupResult.NotCurrent,
                                    -> Unit
                                    is CaptureCleanupResult.Failed -> throw CaptureOwnershipException(
                                        cleanup.reason,
                                    )
                                }
                            }
                            pickedImageResolver.acquire(uri)
                        } else {
                            val capture = captureStore?.currentCapture()?.takeIf { it.uri == uri }
                            MeasurementImageInput(uri = uri, ownedCapture = capture)
                        }
                        acquiredCapture = resolved.ownedCapture
                        val image = imageDecoder.decode(resolved.uri).also { ownedImage = it }
                        resolved to image
                    }
                }
                if (decodeGeneration != generation) return@launch
                state.value.image?.takeIf { it !== decoded }?.release()
                selectedCapture = input.ownedCapture
                captureAdopted = true
                val restoredCrop = state.value.cropRect
                val restoredCropIsValid = restoring &&
                    restoredCrop != null &&
                    isValidCrop(restoredCrop, decoded)
                val restoredConfirmationIsValid = restoredCropIsValid && cropConfirmed
                if (!restoredConfirmationIsValid) cropConfirmed = false
                updateState {
                    it.copy(
                        stage = if (restoredConfirmationIsValid) {
                            Stage.ReadyToAnalyze
                        } else {
                            Stage.ReadyToCrop
                        },
                        image = decoded,
                        imageUri = input.uri,
                        cropRect = if (restoredCropIsValid) restoredCrop else defaultCrop(decoded),
                    )
                }
                ownedImage = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: MeasurementImageDecodeException) {
                setRecoverableError(error.error, Stage.AwaitingImage)
            } catch (error: CaptureOwnershipException) {
                updateState { it.copy(captureCleanupWarning = error.message) }
                setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
            } catch (_: Exception) {
                setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
            } finally {
                ownedImage?.release()
                val orphan = acquiredCapture?.takeUnless { captureAdopted }
                if (orphan != null) {
                    val cleanup = withContext(NonCancellable + ioDispatcher) {
                        captureMutex.withLock {
                            try {
                                captureStore?.clearExpected(orphan) ?: CaptureCleanupResult.NotCurrent
                            } catch (error: Exception) {
                                CaptureCleanupResult.Failed(
                                    error.message ?: error::class.java.simpleName,
                                )
                            }
                        }
                    }
                    if (cleanup is CaptureCleanupResult.Failed) {
                        updateState { it.copy(captureCleanupWarning = cleanup.reason) }
                    }
                }
            }
        }
    }

    private fun updateCrop(bounds: CropBounds) {
        cropConfirmed = false
        if (!isValidCrop(bounds, state.value.image)) {
            setRecoverableError(MeasurementError.InvalidCrop, Stage.ReadyToCrop)
            return
        }
        updateState {
            it.copy(
                stage = Stage.ReadyToCrop,
                cropRect = bounds,
                error = null,
                resumeStage = null,
                errorMessage = null,
            )
        }
    }

    private fun confirmCrop() {
        val crop = state.value.cropRect
        if (crop == null || !isValidCrop(crop, state.value.image)) {
            setRecoverableError(MeasurementError.InvalidCrop, Stage.ReadyToCrop)
            return
        }
        cropConfirmed = true
        updateState { it.copy(stage = Stage.ReadyToAnalyze, error = null, resumeStage = null) }
    }

    private fun startAnalysis() {
        if (activeOperation != null || analysisJob?.isActive == true || state.value.isAnalyzing) return
        val current = state.value
        val image = current.image ?: return
        val crop = current.cropRect ?: return
        val sessionId = currentSessionId ?: return
        if (current.stage != Stage.ReadyToAnalyze) return
        val snapshot = AnalysisOperationSnapshot(
            token = ++nextOperationToken,
            sessionId = sessionId,
            draftId = current.draftId,
            image = image,
            crop = crop,
            factor = current.factor,
            capture = selectedCapture,
        )
        val operation = ActiveOperation(snapshot)
        activeOperation = operation
        retryPersistence = null
        updateState {
            it.copy(
                stage = Stage.Analyzing,
                error = null,
                resumeStage = null,
                resultId = null,
                errorMessage = null,
            )
        }
        analysisJob = viewModelScope.launch {
            try {
                val analysis = withContext(defaultDispatcher) {
                    analyzer.analyze(snapshot.image, snapshot.crop, snapshot.factor)
                }
                if (!isCurrent(operation)) return@launch
                val persistence = PersistenceOperationSnapshot(
                    analysis = snapshot,
                    result = NewTestResult(
                        factor = snapshot.factor,
                        concentration = analysis.concentration,
                        rangeStatus = analysis.rangeStatus,
                        features = analysis.features,
                    ),
                )
                persist(operation, persistence)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrent(operation)) {
                    setRecoverableError(MeasurementError.AnalysisFailed, Stage.ReadyToAnalyze)
                }
            } finally {
                completeOperation(operation)
            }
        }
    }

    private suspend fun persist(
        operation: ActiveOperation,
        snapshot: PersistenceOperationSnapshot,
    ) {
        if (!isCurrent(operation)) return
        updateState { it.copy(stage = Stage.Persisting, error = null, resumeStage = null) }
        try {
            val resultId = withContext(ioDispatcher) {
                repository.commitResult(
                    snapshot.analysis.sessionId,
                    snapshot.analysis.draftId,
                    snapshot.result,
                )
            }
            if (!isCurrent(operation)) return
            val cleanup = snapshot.analysis.capture?.let { capture ->
                withContext(ioDispatcher) {
                    captureMutex.withLock {
                        try {
                            captureStore?.clearExpected(capture) ?: CaptureCleanupResult.NotCurrent
                        } catch (error: Exception) {
                            CaptureCleanupResult.Failed(
                                error.message ?: error::class.java.simpleName,
                            )
                        }
                    }
                }
            }
            if (!isCurrent(operation)) return
            retryPersistence = null
            if (cleanup == CaptureCleanupResult.Removed) selectedCapture = null
            operation.releaseWhenFinished = true
            updateState {
                it.copy(
                    stage = Stage.Success,
                    resultId = resultId,
                    image = null,
                    lastResult = findResult(it.sessions, resultId),
                    captureCleanupWarning = (cleanup as? CaptureCleanupResult.Failed)?.reason,
                )
            }
            if (!isCurrent(operation)) return
            effectChannel.send(MeasurementEffect.NavigateToResult(resultId))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (isCurrent(operation)) {
                retryPersistence = snapshot
                setRecoverableError(MeasurementError.PersistenceFailed, Stage.Persisting)
            }
        }
    }

    private fun retry() {
        if (activeOperation != null || analysisJob?.isActive == true ||
            state.value.stage != Stage.RecoverableError
        ) return
        when (state.value.resumeStage) {
            Stage.Persisting -> {
                val failed = retryPersistence ?: return
                if (state.value.image !== failed.analysis.image) return
                val retryAnalysis = failed.analysis.copy(token = ++nextOperationToken)
                val retrySnapshot = failed.copy(analysis = retryAnalysis)
                val operation = ActiveOperation(retryAnalysis)
                activeOperation = operation
                updateState { it.copy(stage = Stage.Persisting, error = null, resumeStage = null) }
                analysisJob = viewModelScope.launch {
                    try {
                        persist(operation, retrySnapshot)
                    } finally {
                        completeOperation(operation)
                    }
                }
            }
            Stage.ReadyToAnalyze -> {
                updateState { it.copy(stage = Stage.ReadyToAnalyze, error = null, resumeStage = null) }
                startAnalysis()
            }
            Stage.AwaitingImage -> {
                if (state.value.error == MeasurementError.PermissionHistoryUnavailable) {
                    restorePermissionHistory()
                    return
                }
                updateState {
                    it.copy(stage = Stage.AwaitingImage, error = null, resumeStage = null)
                }
                reissuePermissionRequest()
                reissueCameraRequest()
            }
            Stage.ReadyToCrop -> updateState {
                it.copy(stage = Stage.ReadyToCrop, error = null, resumeStage = null)
            }
            else -> Unit
        }
    }

    private fun cancelAnalysis() {
        if (state.value.stage != Stage.Analyzing && state.value.stage != Stage.Persisting) return
        activeOperation?.invalidated = true
        analysisJob?.cancel()
        updateState {
            it.copy(
                stage = Stage.ReadyToAnalyze,
                error = null,
                resumeStage = null,
                errorMessage = null,
            )
        }
    }

    private fun recordCameraPermissionRequest() {
        if (blocksMutuallyExclusiveInputs() || activePermissionRequest != null ||
            permissionRequestJob?.isActive == true ||
            state.value.stage != Stage.AwaitingImage
        ) return
        queueCameraPermissionRequestIntent()
        if (permissionHistoryReadJob != null || !permissionHistoryReady) {
            if (permissionHistoryReadJob == null) restorePermissionHistory()
            return
        }
        val requestedDraft = state.value.draftId
        val generation = permissionRequestGeneration
        permissionRequestJob = viewModelScope.launch {
            val ownerJob = coroutineContext[Job]
            try {
                if (!state.value.hasRequestedCameraPermission) {
                    cameraPermissionHistoryStore?.let { store ->
                        withContext(ioDispatcher) { store.markRequested() }
                    }
                }
                if (!ownsQueuedPermissionIntent(generation, requestedDraft)) return@launch
                updateState { it.copy(hasRequestedCameraPermission = true) }
                val request = PermissionRequestSnapshot(
                    requestToken = permissionRequestTokenFactory(),
                    draftId = requestedDraft,
                    claimed = false,
                    acknowledged = false,
                )
                activePermissionRequest = request
                persistPermissionRequest(request)
                clearQueuedPermissionRequestIntent()
                if (activePermissionRequest == request &&
                    state.value.draftId == request.draftId &&
                    state.value.stage == Stage.AwaitingImage
                ) {
                    effectChannel.send(request.toEffect())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (ownsQueuedPermissionIntent(generation, requestedDraft)) {
                    permissionHistoryReady = false
                    setRecoverableError(
                        MeasurementError.PermissionHistoryUnavailable,
                        Stage.AwaitingImage,
                    )
                }
            } finally {
                if (permissionRequestJob === ownerJob) permissionRequestJob = null
            }
        }
    }

    private fun completeCameraPermissionRequest(action: MeasurementAction.CameraPermissionResult) {
        if (blocksMutuallyExclusiveInputs()) return
        val request = activePermissionRequest ?: return
        if (!request.acknowledged || !request.matches(action.claim) ||
            state.value.draftId != request.draftId ||
            state.value.stage != Stage.AwaitingImage
        ) return
        invalidatePermissionRequest()
        if (action.granted) {
            requestCameraCapture()
        } else {
            setRecoverableError(
                MeasurementError.PermissionDenied(
                    permanentlyDenied = classifyPermanentCameraDenial(
                        wasRequestFormallyRecorded = true,
                        shouldShowRationale = action.shouldShowRationale,
                    ),
                ),
                Stage.AwaitingImage,
            )
        }
    }

    private fun restorePermissionHistory() {
        val store = cameraPermissionHistoryStore ?: return
        if (permissionHistoryReadJob != null || permissionRequestJob?.isActive == true) return
        permissionHistoryReady = false
        val generation = ++permissionHistoryReadGeneration
        val requestedDraft = state.value.draftId
        val durableHistoryProof = activePermissionRequest?.takeIf { it.draftId == requestedDraft }
        lateinit var job: Job
        job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var resumeQueuedRequest = false
            try {
                val requested = withContext(ioDispatcher) { store.wasRequested() }
                if (generation != permissionHistoryReadGeneration) return@launch
                permissionHistoryReady = true
                updateState { current ->
                    val recovering = current.stage == Stage.RecoverableError &&
                        current.error == MeasurementError.PermissionHistoryUnavailable &&
                        current.resumeStage == Stage.AwaitingImage
                    current.copy(
                        stage = if (recovering) Stage.AwaitingImage else current.stage,
                        hasRequestedCameraPermission =
                            current.hasRequestedCameraPermission || requested,
                        error = if (recovering) null else current.error,
                        resumeStage = if (recovering) null else current.resumeStage,
                    )
                }
                resumeQueuedRequest = permissionRequestIntentQueued &&
                    activePermissionRequest == null &&
                    state.value.draftId == requestedDraft &&
                    state.value.stage == Stage.AwaitingImage
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation != permissionHistoryReadGeneration) return@launch
                val current = state.value
                val pendingOwnsDurableHistory = durableHistoryProof != null &&
                    current.draftId == durableHistoryProof.draftId
                if (pendingOwnsDurableHistory) {
                    permissionHistoryReady = true
                    updateState { state -> state.copy(hasRequestedCameraPermission = true) }
                } else if (current.draftId == requestedDraft &&
                    (current.stage == Stage.AwaitingImage ||
                        current.error == MeasurementError.PermissionHistoryUnavailable)
                ) {
                    permissionHistoryReady = false
                    setRecoverableError(
                        MeasurementError.PermissionHistoryUnavailable,
                        Stage.AwaitingImage,
                    )
                }
            } finally {
                if (permissionHistoryReadJob === job) permissionHistoryReadJob = null
            }
            if (resumeQueuedRequest) recordCameraPermissionRequest()
        }
        permissionHistoryReadJob = job
        job.start()
    }

    private fun queueCameraPermissionRequestIntent() {
        permissionRequestIntentQueued = true
        savedStateHandle[KEY_PERMISSION_REQUEST_INTENT_QUEUED] = true
    }

    private fun clearQueuedPermissionRequestIntent() {
        permissionRequestIntentQueued = false
        savedStateHandle[KEY_PERMISSION_REQUEST_INTENT_QUEUED] = false
    }

    private fun ownsQueuedPermissionIntent(generation: Long, draftId: String): Boolean =
        permissionRequestGeneration == generation &&
            permissionRequestIntentQueued &&
            activePermissionRequest == null &&
            state.value.draftId == draftId &&
            state.value.stage == Stage.AwaitingImage

    private fun invalidatePermissionRequestFlow() {
        permissionRequestGeneration += 1
        permissionRequestJob?.cancel()
        permissionRequestJob = null
        clearQueuedPermissionRequestIntent()
        invalidatePermissionRequest()
    }

    private fun reissuePermissionRequest() {
        val restored = activePermissionRequest?.takeUnless { it.claimed || it.acknowledged } ?: return
        if (state.value.draftId == restored.draftId && state.value.stage == Stage.AwaitingImage) {
            viewModelScope.launch {
                if (activePermissionRequest == restored &&
                    state.value.draftId == restored.draftId &&
                    state.value.stage == Stage.AwaitingImage
                ) {
                    effectChannel.send(restored.toEffect())
                }
            }
        } else if (activePermissionRequest == restored) {
            invalidatePermissionRequestFlow()
        }
    }

    fun claimCameraPermissionLaunch(
        effect: MeasurementEffect.RequestCameraPermission,
    ): CameraPermissionLaunchClaim? {
        val active = activePermissionRequest ?: return null
        if (!active.matches(effect) || active.claimed || active.acknowledged ||
            state.value.draftId != active.draftId ||
            state.value.stage != Stage.AwaitingImage
        ) return null
        val claimed = active.copy(claimed = true)
        activePermissionRequest = claimed
        return claimed.toClaim()
    }

    private fun acknowledgeCameraPermissionLaunch(claim: CameraPermissionLaunchClaim) {
        val active = activePermissionRequest ?: return
        if (!active.claimed || active.acknowledged || !active.matches(claim) ||
            state.value.draftId != active.draftId ||
            state.value.stage != Stage.AwaitingImage
        ) return
        val acknowledged = active.copy(claimed = false, acknowledged = true)
        activePermissionRequest = acknowledged
        persistPermissionRequest(acknowledged)
    }

    private fun failCameraPermissionLaunch(claim: CameraPermissionLaunchClaim, reason: String) {
        val active = activePermissionRequest ?: return
        if (!active.claimed || active.acknowledged || !active.matches(claim) ||
            state.value.draftId != active.draftId ||
            state.value.stage != Stage.AwaitingImage
        ) return
        val rolledBack = active.copy(claimed = false, acknowledged = false)
        activePermissionRequest = rolledBack
        persistPermissionRequest(rolledBack)
        setRecoverableError(
            MeasurementError.PermissionLaunchFailed(reason),
            Stage.AwaitingImage,
        )
    }

    private fun requestCameraCapture() {
        val store = captureStore ?: run {
            setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
            return
        }
        if (activeCameraRequest != null || captureRequestJob != null ||
            state.value.stage != Stage.AwaitingImage
        ) return
        val requestedDraft = state.value.draftId
        val requestToken = captureRequestTokenFactory()
        captureRequestJob = viewModelScope.launch {
            val ownerJob = coroutineContext[Job]
            var acquired: MeasurementCapture? = null
            var ownershipTransferred = false
            try {
                captureCleanupJob?.join()
                val capture = withContext(ioDispatcher) {
                    captureMutex.withLock {
                        store.createOrRestorePending().also { acquired = it }
                    }
                }
                if (state.value.draftId != requestedDraft || state.value.stage != Stage.AwaitingImage) {
                    return@launch
                }
                val snapshot = CameraRequestSnapshot(
                    requestToken = requestToken,
                    draftId = requestedDraft,
                    capture = capture,
                    claimed = false,
                    acknowledged = false,
                )
                activeCameraRequest = snapshot
                persistCameraRequest(snapshot)
                ownershipTransferred = true
                effectChannel.send(snapshot.toEffect())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
            } finally {
                val orphan = acquired?.takeUnless { ownershipTransferred }
                if (orphan != null) {
                    val cleanup = withContext(NonCancellable + ioDispatcher) {
                        captureMutex.withLock {
                            try {
                                store.clearExpected(orphan)
                            } catch (error: Exception) {
                                CaptureCleanupResult.Failed(
                                    error.message ?: error::class.java.simpleName,
                                )
                            }
                        }
                    }
                    if (cleanup is CaptureCleanupResult.Failed) {
                        updateState { it.copy(captureCleanupWarning = cleanup.reason) }
                    }
                }
                if (captureRequestJob === ownerJob) captureRequestJob = null
            }
        }
    }

    private fun reissueCameraRequest() {
        if (captureRequestJob != null) return
        val restored = activeCameraRequest?.takeUnless { it.claimed || it.acknowledged } ?: return
        val store = captureStore ?: return
        captureRequestJob = viewModelScope.launch {
            val ownerJob = coroutineContext[Job]
            try {
                val current = withContext(ioDispatcher) {
                    captureMutex.withLock { store.currentCapture() }
                }
                if (activeCameraRequest == restored && current == restored.capture &&
                    state.value.draftId == restored.draftId && state.value.stage == Stage.AwaitingImage
                ) {
                    effectChannel.send(restored.toEffect())
                } else if (activeCameraRequest == restored) {
                    invalidateCameraRequest()
                    setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (activeCameraRequest == restored) {
                    invalidateCameraRequest()
                    setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
                }
            } finally {
                if (captureRequestJob === ownerJob) captureRequestJob = null
            }
        }
    }

    fun claimCameraLaunch(effect: MeasurementEffect.LaunchCamera): CameraLaunchClaim? {
        val active = activeCameraRequest ?: return null
        if (!active.matches(effect) || active.claimed || active.acknowledged ||
            state.value.draftId != active.draftId ||
            state.value.stage != Stage.AwaitingImage
        ) return null
        val claimed = active.copy(claimed = true)
        activeCameraRequest = claimed
        return claimed.toClaim()
    }

    private fun acknowledgeCameraLaunch(claim: CameraLaunchClaim) {
        val active = activeCameraRequest ?: return
        if (!active.claimed || active.acknowledged || !active.matches(claim)) return
        val acknowledged = active.copy(claimed = false, acknowledged = true)
        activeCameraRequest = acknowledged
        persistCameraRequest(acknowledged)
    }

    private fun failCameraLaunch(claim: CameraLaunchClaim, reason: String) {
        val active = activeCameraRequest ?: return
        if (!active.claimed || active.acknowledged || !active.matches(claim)) return
        val rolledBack = active.copy(claimed = false, acknowledged = false)
        activeCameraRequest = rolledBack
        persistCameraRequest(rolledBack)
        setRecoverableError(
            MeasurementError.CameraLaunchFailed(reason),
            Stage.AwaitingImage,
        )
    }

    private fun completeCameraCapture(success: Boolean) {
        val request = activeCameraRequest ?: return
        if (!request.acknowledged) return
        if (success) {
            captureRequestJob = viewModelScope.launch {
                val current = withContext(ioDispatcher) {
                    captureMutex.withLock { captureStore?.currentCapture() }
                }
                if (activeCameraRequest != request || current != request.capture ||
                    state.value.draftId != request.draftId
                ) return@launch
                invalidateCameraRequest()
                onAction(MeasurementAction.ImageSelected(request.capture.uri))
            }
        } else {
            updateState {
                it.copy(stage = Stage.Decoding, error = null, resumeStage = null)
            }
            invalidateCameraRequest()
            clearCapture(
                expected = request.capture,
                after = { outcome ->
                    if (outcome is CaptureCleanupResult.Failed) {
                        updateState { it.copy(captureCleanupWarning = outcome.reason) }
                        setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
                    } else {
                        updateState {
                            it.copy(stage = Stage.AwaitingImage, captureCleanupWarning = null)
                        }
                    }
                },
            )
        }
    }

    private fun returnToImageSelection() {
        val expected = selectedCapture ?: activeCameraRequest?.capture
        invalidatePermissionRequestFlow()
        captureRequestJob?.cancel()
        invalidateCameraRequest()
        decodeJob?.cancel()
        decodeGeneration += 1
        releaseStateImageUnlessBorrowed()
        selectedCapture = null
        retryPersistence = null
        cropConfirmed = false
        updateState {
            it.copy(
                stage = if (expected == null) Stage.AwaitingImage else Stage.Decoding,
                imageUri = null,
                cropRect = null,
                image = null,
                error = null,
                resumeStage = null,
                resultId = null,
                errorMessage = null,
            )
        }
        if (expected != null) {
            clearCapture(expected) { outcome ->
                if (outcome is CaptureCleanupResult.Failed) {
                    updateState { it.copy(captureCleanupWarning = outcome.reason) }
                    setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
                } else {
                    updateState { it.copy(stage = Stage.AwaitingImage, captureCleanupWarning = null) }
                }
            }
        }
    }

    private fun returnToCrop() {
        if (state.value.image == null || state.value.cropRect == null) return
        cropConfirmed = false
        updateState {
            it.copy(stage = Stage.ReadyToCrop, error = null, resumeStage = null, errorMessage = null)
        }
    }

    private fun clearCapture(
        expected: MeasurementCapture,
        after: (CaptureCleanupResult) -> Unit = {},
    ) {
        val store = captureStore ?: return after(CaptureCleanupResult.NotCurrent)
        val job = viewModelScope.launch {
            val outcome = try {
                withContext(ioDispatcher) {
                    captureMutex.withLock { store.clearExpected(expected) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                CaptureCleanupResult.Failed(error.message ?: error::class.java.simpleName)
            }
            if (selectedCapture == expected && outcome == CaptureCleanupResult.Removed) {
                selectedCapture = null
            }
            after(outcome)
        }
        captureCleanupJob = job
        job.invokeOnCompletion {
            if (captureCleanupJob === job) captureCleanupJob = null
        }
    }

    private fun beginNewDraft() {
        val expected = selectedCapture ?: activeCameraRequest?.capture
        invalidatePermissionRequestFlow()
        invalidateCameraRequest()
        invalidateActiveOperation(releaseImage = true)
        decodeJob?.cancel()
        decodeGeneration += 1
        releaseStateImageUnlessBorrowed()
        selectedCapture = null
        retryPersistence = null
        cropConfirmed = false
        updateState {
            it.copy(
                stage = Stage.AwaitingImage,
                draftId = draftIdFactory(),
                imageUri = null,
                cropRect = null,
                error = null,
                resumeStage = null,
                resultId = null,
                image = null,
                lastResult = null,
                errorMessage = null,
                captureCleanupWarning = null,
            )
        }
        if (expected != null) clearCapture(expected) { outcome ->
            if (outcome is CaptureCleanupResult.Failed) {
                updateState { it.copy(captureCleanupWarning = outcome.reason) }
            }
        }
    }

    private fun clearTransient() {
        val expected = selectedCapture ?: activeCameraRequest?.capture
        permissionHistoryReadGeneration += 1
        permissionHistoryReadJob?.cancel()
        permissionHistoryReadJob = null
        invalidatePermissionRequestFlow()
        captureRequestJob?.cancel()
        invalidateCameraRequest()
        invalidateActiveOperation(releaseImage = true)
        decodeJob?.cancel()
        decodeGeneration += 1
        releaseStateImageUnlessBorrowed()
        selectedCapture = null
        currentSessionId = null
        savedStateHandle[KEY_SESSION_ID] = null
        retryPersistence = null
        cropConfirmed = false
        val sessions = state.value.sessions
        val origin = state.value.originDestination
        val permissionHistory = state.value.hasRequestedCameraPermission
        val draft = draftIdFactory()
        mutableState.value = MeasurementUiState(
            draftId = draft,
            sessions = sessions,
            originDestination = origin,
            hasRequestedCameraPermission = permissionHistory,
        )
        persistFormalState(mutableState.value)
        if (expected != null) clearCapture(expected) { outcome ->
            if (outcome is CaptureCleanupResult.Failed) {
                updateState { it.copy(captureCleanupWarning = outcome.reason) }
            }
        }
    }

    private fun clearCreationIfOwned(creationId: Long) {
        if (activeSessionCreationId != creationId) return
        activeSessionCreationId = null
        mutableState.update { current ->
            if (current.sessionCreationRequest?.requestId == creationId) {
                current.copy(isCreatingSession = false, sessionCreationRequest = null)
            } else {
                current
            }
        }
    }

    private fun setRecoverableError(error: MeasurementError, resumeStage: Stage) {
        updateState {
            it.copy(
                stage = Stage.RecoverableError,
                error = error,
                resumeStage = resumeStage,
                errorMessage = error.toString(),
            )
        }
    }

    private fun applySessions(sessions: List<TestSession>) {
        mutableState.update { current ->
            current.copy(
                sessions = sessions,
                currentSession = sessions.firstOrNull { it.id == currentSessionId },
                lastResult = current.resultId?.let { findResult(sessions, it) },
            )
        }
    }

    private fun updateState(transform: (MeasurementUiState) -> MeasurementUiState) {
        mutableState.update { current -> transform(current).also(::persistFormalState) }
    }

    private fun restoredState(): MeasurementUiState {
        val draftId = savedStateHandle.get<String>(KEY_DRAFT_ID)
            ?: draftIdFactory().also { savedStateHandle[KEY_DRAFT_ID] = it }
        val uri = savedStateHandle.get<String>(KEY_IMAGE_URI)
        val crop = restoredCrop()
        val factor = savedStateHandle.get<String>(KEY_FACTOR)
            ?.let { runCatching { InflammationFactor.valueOf(it) }.getOrNull() }
            ?: InflammationFactor.IL6
        val origin = savedStateHandle.get<String>(KEY_ORIGIN)
        return MeasurementUiState(
            stage = if (uri == null) Stage.AwaitingImage else Stage.Decoding,
            draftId = draftId,
            imageUri = uri,
            cropRect = crop,
            factor = factor,
            originDestination = origin,
            hasRequestedCameraPermission = savedStateHandle[KEY_PERMISSION_REQUESTED] ?: false,
            captureCleanupWarning = savedStateHandle[KEY_CAPTURE_CLEANUP_WARNING],
        )
    }

    private fun restoredCrop(): CropBounds? {
        val left = savedStateHandle.get<Int>(KEY_CROP_LEFT) ?: return null
        val top = savedStateHandle.get<Int>(KEY_CROP_TOP) ?: return null
        val right = savedStateHandle.get<Int>(KEY_CROP_RIGHT) ?: return null
        val bottom = savedStateHandle.get<Int>(KEY_CROP_BOTTOM) ?: return null
        return CropBounds(left, top, right, bottom)
    }

    private fun persistFormalState(state: MeasurementUiState) {
        savedStateHandle[KEY_DRAFT_ID] = state.draftId
        savedStateHandle[KEY_IMAGE_URI] = state.imageUri
        savedStateHandle[KEY_FACTOR] = state.factor.name
        savedStateHandle[KEY_ORIGIN] = state.originDestination
        savedStateHandle[KEY_CROP_LEFT] = state.cropRect?.left
        savedStateHandle[KEY_CROP_TOP] = state.cropRect?.top
        savedStateHandle[KEY_CROP_RIGHT] = state.cropRect?.right
        savedStateHandle[KEY_CROP_BOTTOM] = state.cropRect?.bottom
        savedStateHandle[KEY_CROP_CONFIRMED] = cropConfirmed
        savedStateHandle[KEY_PERMISSION_REQUESTED] = state.hasRequestedCameraPermission
        savedStateHandle[KEY_CAPTURE_CLEANUP_WARNING] = state.captureCleanupWarning
    }

    private fun persistCameraRequest(request: CameraRequestSnapshot?) {
        savedStateHandle[KEY_CAMERA_REQUEST_TOKEN] = request?.requestToken
        savedStateHandle[KEY_CAMERA_REQUEST_DRAFT] = request?.draftId
        savedStateHandle[KEY_CAMERA_CAPTURE_URI] = request?.capture?.uri
        savedStateHandle[KEY_CAMERA_CAPTURE_TOKEN] = request?.capture?.token
        savedStateHandle[KEY_CAMERA_REQUEST_ACKNOWLEDGED] = request?.acknowledged
    }

    private fun persistPermissionRequest(request: PermissionRequestSnapshot?) {
        savedStateHandle[KEY_PERMISSION_REQUEST_PENDING] = request != null
        savedStateHandle[KEY_PERMISSION_REQUEST_TOKEN] = request?.requestToken
        savedStateHandle[KEY_PERMISSION_REQUEST_DRAFT] = request?.draftId
        savedStateHandle[KEY_PERMISSION_REQUEST_ACKNOWLEDGED] = request?.acknowledged
    }

    private fun restoredPermissionRequest(): PermissionRequestSnapshot? {
        if (savedStateHandle.get<Boolean>(KEY_PERMISSION_REQUEST_PENDING) != true) return null
        return PermissionRequestSnapshot(
            requestToken = savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_TOKEN)
                ?: permissionRequestTokenFactory(),
            draftId = savedStateHandle.get<String>(KEY_PERMISSION_REQUEST_DRAFT)
                ?: requireNotNull(savedStateHandle.get<String>(KEY_DRAFT_ID)),
            claimed = false,
            acknowledged = savedStateHandle[KEY_PERMISSION_REQUEST_ACKNOWLEDGED] ?: false,
        )
    }

    private fun invalidatePermissionRequest() {
        activePermissionRequest = null
        persistPermissionRequest(null)
    }

    private fun restoredCameraRequest(): CameraRequestSnapshot? {
        val requestToken = savedStateHandle.get<String>(KEY_CAMERA_REQUEST_TOKEN) ?: return null
        val draft = savedStateHandle.get<String>(KEY_CAMERA_REQUEST_DRAFT) ?: return null
        val uri = savedStateHandle.get<String>(KEY_CAMERA_CAPTURE_URI) ?: return null
        val captureToken = savedStateHandle.get<String>(KEY_CAMERA_CAPTURE_TOKEN) ?: return null
        return CameraRequestSnapshot(
            requestToken = requestToken,
            draftId = draft,
            capture = MeasurementCapture(uri, captureToken),
            claimed = false,
            acknowledged = savedStateHandle[KEY_CAMERA_REQUEST_ACKNOWLEDGED] ?: false,
        )
    }

    private fun invalidateCameraRequest() {
        activeCameraRequest = null
        persistCameraRequest(null)
    }

    override fun onCleared() {
        invalidateActiveOperation(releaseImage = true)
        decodeJob?.cancel()
        captureRequestJob?.cancel()
        permissionRequestJob?.cancel()
        permissionHistoryReadJob?.cancel()
        captureCleanupJob?.cancel()
        releaseStateImageUnlessBorrowed()
        super.onCleared()
    }

    private fun blocksMutuallyExclusiveInputs(): Boolean = activeOperation?.let {
        !it.invalidated || !it.releaseWhenFinished
    } == true

    private fun isCurrent(operation: ActiveOperation): Boolean =
        activeOperation === operation &&
            activeOperation?.snapshot?.token == operation.snapshot.token &&
            !operation.invalidated

    private fun invalidateActiveOperation(releaseImage: Boolean) {
        activeOperation?.let { operation ->
            operation.invalidated = true
            operation.releaseWhenFinished = operation.releaseWhenFinished || releaseImage
        }
        analysisJob?.cancel()
    }

    private fun releaseStateImageUnlessBorrowed() {
        val image = state.value.image ?: return
        if (activeOperation?.snapshot?.image !== image) image.release()
    }

    private fun completeOperation(operation: ActiveOperation) {
        if (activeOperation === operation) {
            activeOperation = null
            analysisJob = null
        }
        operation.releaseIfRequested()
    }

    private fun defaultCrop(image: MeasurementImage): CropBounds {
        val left = image.width / 4
        val top = image.height / 4
        return CropBounds(
            left = left,
            top = top,
            right = maxOf(image.width * 3 / 4, left + 1).coerceAtMost(image.width),
            bottom = maxOf(image.height * 3 / 4, top + 1).coerceAtMost(image.height),
        )
    }

    private fun isValidCrop(bounds: CropBounds, image: MeasurementImage?): Boolean =
        image != null &&
            bounds.left >= 0 && bounds.top >= 0 &&
            bounds.right <= image.width && bounds.bottom <= image.height &&
            bounds.width > 0 && bounds.height > 0

    private fun findResult(sessions: List<TestSession>, id: String) = sessions.asSequence()
        .flatMap { it.results.asSequence() }
        .firstOrNull { it.id == id }

    private data class AnalysisOperationSnapshot(
        val token: Long,
        val sessionId: String,
        val draftId: String,
        val image: MeasurementImage,
        val crop: CropBounds,
        val factor: InflammationFactor,
        val capture: MeasurementCapture?,
    )

    private data class CameraRequestSnapshot(
        val requestToken: String,
        val draftId: String,
        val capture: MeasurementCapture,
        val claimed: Boolean,
        val acknowledged: Boolean,
    ) {
        fun toEffect() = MeasurementEffect.LaunchCamera(
            uri = capture.uri,
            requestToken = requestToken,
            draftId = draftId,
            captureToken = capture.token,
        )

        fun matches(effect: MeasurementEffect.LaunchCamera): Boolean =
            requestToken == effect.requestToken &&
                draftId == effect.draftId &&
                capture.uri == effect.uri &&
                capture.token == effect.captureToken

        fun toClaim() = CameraLaunchClaim(
            uri = capture.uri,
            requestToken = requestToken,
            draftId = draftId,
            captureToken = capture.token,
        )

        fun matches(claim: CameraLaunchClaim): Boolean =
            requestToken == claim.requestToken &&
                draftId == claim.draftId &&
                capture.uri == claim.uri &&
                capture.token == claim.captureToken
    }

    private data class PermissionRequestSnapshot(
        val requestToken: String,
        val draftId: String,
        val claimed: Boolean,
        val acknowledged: Boolean,
    ) {
        fun toEffect() = MeasurementEffect.RequestCameraPermission(
            requestToken = requestToken,
            draftId = draftId,
        )

        fun matches(effect: MeasurementEffect.RequestCameraPermission): Boolean =
            requestToken == effect.requestToken && draftId == effect.draftId

        fun toClaim() = CameraPermissionLaunchClaim(
            requestToken = requestToken,
            draftId = draftId,
        )

        fun matches(claim: CameraPermissionLaunchClaim): Boolean =
            requestToken == claim.requestToken && draftId == claim.draftId
    }

    private data class PersistenceOperationSnapshot(
        val analysis: AnalysisOperationSnapshot,
        val result: NewTestResult,
    )

    private class ActiveOperation(
        val snapshot: AnalysisOperationSnapshot,
    ) {
        var invalidated: Boolean = false
        var releaseWhenFinished: Boolean = false
        private var imageReleased: Boolean = false

        fun releaseIfRequested() {
            if (!releaseWhenFinished || imageReleased) return
            imageReleased = true
            snapshot.image.release()
        }
    }

    private companion object {
        const val KEY_DRAFT_ID = "measurement.draftId"
        const val KEY_IMAGE_URI = "measurement.imageUri"
        const val KEY_CROP_LEFT = "measurement.crop.left"
        const val KEY_CROP_TOP = "measurement.crop.top"
        const val KEY_CROP_RIGHT = "measurement.crop.right"
        const val KEY_CROP_BOTTOM = "measurement.crop.bottom"
        const val KEY_CROP_CONFIRMED = "measurement.crop.confirmed"
        const val KEY_FACTOR = "measurement.factor"
        const val KEY_ORIGIN = "measurement.origin"
        const val KEY_SESSION_ID = "measurement.sessionId"
        const val KEY_PERMISSION_REQUESTED = "measurement.permission.camera.requested"
        const val KEY_PERMISSION_REQUEST_PENDING = "measurement.permission.camera.pending"
        const val KEY_PERMISSION_REQUEST_INTENT_QUEUED = "measurement.permission.camera.intent.queued"
        const val KEY_PERMISSION_REQUEST_TOKEN = "measurement.permission.camera.request.token"
        const val KEY_PERMISSION_REQUEST_DRAFT = "measurement.permission.camera.request.draft"
        const val KEY_PERMISSION_REQUEST_ACKNOWLEDGED =
            "measurement.permission.camera.request.acknowledged"
        const val KEY_CAPTURE_CLEANUP_WARNING = "measurement.capture.cleanup.warning"
        const val KEY_CAMERA_REQUEST_TOKEN = "measurement.camera.request.token"
        const val KEY_CAMERA_REQUEST_DRAFT = "measurement.camera.request.draft"
        const val KEY_CAMERA_CAPTURE_URI = "measurement.camera.capture.uri"
        const val KEY_CAMERA_CAPTURE_TOKEN = "measurement.camera.capture.token"
        // Keep the original key string so acknowledged launches migrate without duplication.
        const val KEY_CAMERA_REQUEST_ACKNOWLEDGED = "measurement.camera.request.consumed"
    }
}

private class CaptureOwnershipException(message: String) : Exception(message)
