package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.model.measurementBatchCount

enum class Stage {
    AwaitingImage,
    Decoding,
    ReadyToCrop,
    ReadyToAnalyze,
    Analyzing,
    Persisting,
    Success,
    RecoverableError,
}

sealed interface MeasurementError {
    data class PermissionDenied(val permanentlyDenied: Boolean) : MeasurementError

    data object PermissionHistoryUnavailable : MeasurementError

    data class PermissionLaunchFailed(val reason: String) : MeasurementError

    data object ImageUnreadable : MeasurementError

    data object UnsupportedImage : MeasurementError

    data object ImageTooLarge : MeasurementError

    data object InvalidCrop : MeasurementError

    data object AnalysisFailed : MeasurementError

    data object PersistenceFailed : MeasurementError

    data class CameraLaunchFailed(val reason: String) : MeasurementError
}

class CameraLaunchClaim internal constructor(
    val uri: String,
    internal val requestToken: String,
    internal val draftId: String,
    internal val captureToken: String,
)

class CameraPermissionLaunchClaim internal constructor(
    internal val requestToken: String,
    internal val draftId: String,
)

sealed interface MeasurementEffect {
    class RequestCameraPermission internal constructor(
        internal val requestToken: String,
        internal val draftId: String,
    ) : MeasurementEffect

    class LaunchCamera internal constructor(
        val uri: String,
        internal val requestToken: String,
        internal val draftId: String,
        internal val captureToken: String,
    ) : MeasurementEffect

    data class NavigateToResult(val resultId: String) : MeasurementEffect
}

data class MeasurementUiState(
    val stage: Stage = Stage.AwaitingImage,
    val draftId: String,
    val imageUri: String? = null,
    val cropRect: CropBounds? = null,
    val error: MeasurementError? = null,
    val resumeStage: Stage? = null,
    val resultId: String? = null,
    val awaitingRepositoryResultId: String? = null,
    val originDestination: String? = null,
    val hasRequestedCameraPermission: Boolean = false,
    val captureCleanupWarning: String? = null,
    // Transitional Phase-1 surface. Task 5 moves callers to the formal fields above.
    val sessions: List<TestSession> = emptyList(),
    val hasReceivedSessionsSnapshot: Boolean = false,
    val currentSession: TestSession? = null,
    val image: MeasurementImage? = null,
    val lastResult: TestResult? = null,
    val isCreatingSession: Boolean = false,
    val sessionCreationRequest: SessionCreationRequest? = null,
    val sessionCreationError: String? = null,
    val errorMessage: String? = null,
) {
    val cropBounds: CropBounds get() = cropRect ?: DEFAULT_CROP_BOUNDS

    val isAnalyzing: Boolean get() = stage == Stage.Analyzing || stage == Stage.Persisting

    val canAddMore: Boolean get() = (currentSession?.measurementBatchCount() ?: 0) < 5

    private companion object {
        val DEFAULT_CROP_BOUNDS = CropBounds(0, 0, 200, 200)
    }
}

data class SessionCreationRequest(
    val requestId: Long,
    val originIdentity: String,
    val completedSessionId: String? = null,
)
