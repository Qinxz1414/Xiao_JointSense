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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MeasurementViewModel(
    private val repository: TestSessionRepository,
    private val analyzer: BaselinePhotoAnalysisAdapter,
    private val draftIdFactory: () -> String = { UUID.randomUUID().toString() },
    private val savedStateHandle: SavedStateHandle,
    private val decoder: MeasurementImageDecoder?,
    private val ioDispatcher: CoroutineDispatcher,
    private val defaultDispatcher: CoroutineDispatcher,
    private val sessionNamePrefix: () -> String = { "Test" },
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
        ioDispatcher = Dispatchers.Main.immediate,
        defaultDispatcher = Dispatchers.Main.immediate,
    )

    private val mutableState = MutableStateFlow(restoredState())
    val state: StateFlow<MeasurementUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<MeasurementEffect>(Channel.BUFFERED)
    val effects: Flow<MeasurementEffect> = effectChannel.receiveAsFlow()
    private val legacyCompletionChannel = Channel<String>(Channel.BUFFERED)
    val analysisCompletions: Flow<String> = legacyCompletionChannel.receiveAsFlow()

    private var currentSessionId: String? = savedStateHandle[KEY_SESSION_ID]
    private var retryPersistence: PersistenceOperationSnapshot? = null
    private var analysisJob: Job? = null
    private var decodeJob: Job? = null
    private var nextOperationToken = 0L
    private var activeOperation: ActiveOperation? = null
    private var cropConfirmed: Boolean = savedStateHandle[KEY_CROP_CONFIRMED] ?: false
    private var nextSessionCreationId = 0L
    private var activeSessionCreationId: Long? = null

    init {
        viewModelScope.launch {
            repository.observeSessions().collectLatest(::applySessions)
        }
        state.value.imageUri?.takeIf { decoder != null }?.let { decodeImage(it, restoring = true) }
    }

    fun onAction(action: MeasurementAction) {
        when (action) {
            is MeasurementAction.ImageSelected -> if (!blocksMutuallyExclusiveInputs()) {
                decodeImage(action.uri, restoring = false)
            }
            is MeasurementAction.PermissionDenied -> if (!blocksMutuallyExclusiveInputs()) {
                setRecoverableError(
                    error = MeasurementError.PermissionDenied(action.permanentlyDenied),
                    resumeStage = Stage.AwaitingImage,
                )
            }
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
            MeasurementAction.ContinueMeasurement -> if (!blocksMutuallyExclusiveInputs()) beginNewDraft()
        }
    }

    fun createNewSession(originIdentity: String) {
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
        val requestedName = nextSessionName(state.value.sessions.map(TestSession::name), sessionNamePrefix())
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
        decodeJob?.cancel()
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

    private fun decodeImage(uri: String, restoring: Boolean) {
        val imageDecoder = decoder ?: run {
            if (!restoring) setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
            return
        }
        decodeJob?.cancel()
        if (!restoring) {
            state.value.image?.release()
            cropConfirmed = false
            retryPersistence = null
        }
        updateState {
            it.copy(
                stage = Stage.Decoding,
                imageUri = uri,
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
            try {
                val decoded = withContext(ioDispatcher) {
                    imageDecoder.decode(uri).also { ownedImage = it }
                }
                if (state.value.imageUri != uri) {
                    return@launch
                }
                state.value.image?.takeIf { it !== decoded }?.release()
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
                        cropRect = if (restoredCropIsValid) restoredCrop else defaultCrop(decoded),
                    )
                }
                ownedImage = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: MeasurementImageDecodeException) {
                setRecoverableError(error.error, Stage.AwaitingImage)
            } catch (_: Exception) {
                setRecoverableError(MeasurementError.ImageUnreadable, Stage.AwaitingImage)
            } finally {
                ownedImage?.release()
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
            retryPersistence = null
            operation.releaseWhenFinished = true
            updateState {
                it.copy(
                    stage = Stage.Success,
                    resultId = resultId,
                    image = null,
                    lastResult = findResult(it.sessions, resultId),
                )
            }
            if (!isCurrent(operation)) return
            effectChannel.send(MeasurementEffect.NavigateToResult(resultId))
            if (!isCurrent(operation)) return
            legacyCompletionChannel.send(resultId)
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
            Stage.AwaitingImage -> updateState {
                it.copy(stage = Stage.AwaitingImage, error = null, resumeStage = null)
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

    private fun beginNewDraft() {
        invalidateActiveOperation(releaseImage = true)
        decodeJob?.cancel()
        releaseStateImageUnlessBorrowed()
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
            )
        }
    }

    private fun clearTransient() {
        invalidateActiveOperation(releaseImage = true)
        decodeJob?.cancel()
        releaseStateImageUnlessBorrowed()
        currentSessionId = null
        savedStateHandle[KEY_SESSION_ID] = null
        retryPersistence = null
        cropConfirmed = false
        val sessions = state.value.sessions
        val origin = state.value.originDestination
        val draft = draftIdFactory()
        mutableState.value = MeasurementUiState(
            draftId = draft,
            sessions = sessions,
            originDestination = origin,
        )
        persistFormalState(mutableState.value)
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
    }

    override fun onCleared() {
        invalidateActiveOperation(releaseImage = true)
        decodeJob?.cancel()
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
    )

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
    }
}
