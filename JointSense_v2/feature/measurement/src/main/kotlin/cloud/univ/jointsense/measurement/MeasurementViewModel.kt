package cloud.univ.jointsense.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeasurementUiState(
    val sessions: List<TestSession> = emptyList(),
    val currentSession: TestSession? = null,
    val image: MeasurementImage? = null,
    val cropBounds: CropBounds = CropBounds(0, 0, 200, 200),
    val selectedFactor: InflammationFactor = InflammationFactor.IL6,
    val lastResult: TestResult? = null,
    val isAnalyzing: Boolean = false,
    val errorMessage: String? = null,
) {
    val canAddMore: Boolean get() = (currentSession?.results?.size ?: 0) < 5
}

class MeasurementViewModel(
    private val repository: TestSessionRepository,
    private val analyzer: BaselinePhotoAnalysisAdapter,
    private val draftIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(MeasurementUiState())
    val state: StateFlow<MeasurementUiState> = mutableState.asStateFlow()
    private val completions = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val analysisCompletions = completions.asSharedFlow()
    private var currentSessionId: String? = null
    private var lastResultId: String? = null

    init {
        viewModelScope.launch {
            repository.observeSessions().collectLatest(::applySessions)
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val id = repository.createSession("Test #${state.value.sessions.size + 1}")
            currentSessionId = id
            lastResultId = null
            applySessions(state.value.sessions)
        }
    }

    fun selectSession(id: String) {
        currentSessionId = id
        lastResultId = null
        applySessions(state.value.sessions)
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            repository.deleteSession(id)
            if (currentSessionId == id) clearTransient()
        }
    }

    fun abandonMeasurement() {
        val session = state.value.currentSession
        viewModelScope.launch {
            if (session != null && session.results.isEmpty()) repository.deleteSession(session.id)
            clearTransient()
        }
    }

    fun finishMeasurement() {
        clearTransient()
    }

    fun setImage(image: MeasurementImage) {
        mutableState.update { current -> current.copy(
            image = image,
            cropBounds = CropBounds(
                left = image.width / 4,
                top = image.height / 4,
                right = image.width * 3 / 4,
                bottom = image.height * 3 / 4,
            ),
            errorMessage = null,
        ) }
    }

    fun updateCropBounds(bounds: CropBounds) {
        mutableState.update { it.copy(cropBounds = bounds) }
    }

    fun selectFactor(factor: InflammationFactor) {
        mutableState.update { it.copy(selectedFactor = factor) }
    }

    fun analyze() {
        val current = state.value
        val image = current.image ?: return
        val sessionId = currentSessionId ?: return
        if (current.isAnalyzing) return
        mutableState.value = current.copy(isAnalyzing = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val analysis = analyzer.analyze(image, current.cropBounds, current.selectedFactor)
                val resultId = repository.commitResult(
                    sessionId = sessionId,
                    draftId = draftIdFactory(),
                    result = NewTestResult(
                        factor = current.selectedFactor,
                        concentration = analysis.concentration,
                        rangeStatus = analysis.rangeStatus,
                        features = analysis.features,
                    ),
                )
                lastResultId = resultId
                applySessions(state.value.sessions)
                completions.emit(resultId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableState.update { it.copy(
                    errorMessage = exception.message ?: exception::class.java.simpleName,
                ) }
            } finally {
                mutableState.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    fun startNewTestInSession() {
        lastResultId = null
        mutableState.update { it.copy(
            image = null,
            cropBounds = CropBounds(0, 0, 200, 200),
            lastResult = null,
            errorMessage = null,
        ) }
    }

    private fun clearTransient() {
        currentSessionId = null
        lastResultId = null
        mutableState.value = MeasurementUiState(sessions = state.value.sessions)
    }

    private fun applySessions(sessions: List<TestSession>) {
        mutableState.update { current ->
            current.copy(
                sessions = sessions,
                currentSession = sessions.firstOrNull { it.id == currentSessionId },
                lastResult = sessions.asSequence().flatMap { it.results.asSequence() }
                    .firstOrNull { it.id == lastResultId },
            )
        }
    }
}
