package cloud.univ.jointsense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import cloud.univ.jointsense.domain.repository.DataManagementRepository
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class RestoreSamplesOutcome { SUCCESS, FAILURE }

data class SettingsUiState(
    val sessionCount: Int = 0,
    val measurementCount: Int = 0,
    val calibrationCount: Int = 0,
    val calibrationReviewCount: Int = 0,
    val restoreSamplesConfirmationPending: Boolean = false,
    val restoreSamplesInProgress: Boolean = false,
    val restoreSamplesOutcome: RestoreSamplesOutcome? = null,
) {
    val hasCalibration: Boolean
        get() = calibrationCount > 0

    val hasCalibrationNeedingReview: Boolean
        get() = calibrationReviewCount > 0
}

class SettingsViewModel(
    sessions: TestSessionRepository,
    calibrations: CalibrationRepository,
    private val dataManagement: DataManagementRepository,
) : ViewModel() {
    private val restoreSamplesState = MutableStateFlow(RestoreSamplesState())

    val state: StateFlow<SettingsUiState> = combine(
        sessions.observeSessions(),
        calibrations.observeCalibrations(),
        restoreSamplesState,
    ) { observedSessions, observedCalibrations, restoreState ->
        SettingsUiState(
            sessionCount = observedSessions.size,
            measurementCount = observedSessions.sumOf { it.results.size },
            calibrationCount = observedCalibrations.count {
                it.status == CalibrationStatus.ACTIVE
            },
            calibrationReviewCount = observedCalibrations.count {
                it.status == CalibrationStatus.NEEDS_REVIEW
            },
            restoreSamplesConfirmationPending = restoreState.confirmationPending,
            restoreSamplesInProgress = restoreState.inProgress,
            restoreSamplesOutcome = restoreState.outcome,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun clearAllData() {
        viewModelScope.launch { dataManagement.clearAllData() }
    }

    fun requestRestoreBuiltInSamplesConfirmation() {
        restoreSamplesState.update { current ->
            if (current.confirmationPending || current.inProgress) {
                current
            } else {
                current.copy(confirmationPending = true, outcome = null)
            }
        }
    }

    fun cancelRestoreBuiltInSamplesConfirmation() {
        restoreSamplesState.update { current ->
            if (current.inProgress) current else current.copy(confirmationPending = false)
        }
    }

    fun confirmRestoreBuiltInSamples() {
        if (!claimRestoreBuiltInSamples()) return
        viewModelScope.launch {
            try {
                dataManagement.restoreBuiltInSamples()
                restoreSamplesState.update {
                    it.copy(inProgress = false, outcome = RestoreSamplesOutcome.SUCCESS)
                }
            } catch (cancellation: CancellationException) {
                restoreSamplesState.update { it.copy(inProgress = false) }
                throw cancellation
            } catch (_: Exception) {
                restoreSamplesState.update {
                    it.copy(inProgress = false, outcome = RestoreSamplesOutcome.FAILURE)
                }
            }
        }
    }

    fun dismissRestoreSamplesOutcome() {
        restoreSamplesState.update { it.copy(outcome = null) }
    }

    private fun claimRestoreBuiltInSamples(): Boolean {
        while (true) {
            val current = restoreSamplesState.value
            if (!current.confirmationPending || current.inProgress) return false
            val claimed = current.copy(
                confirmationPending = false,
                inProgress = true,
                outcome = null,
            )
            if (restoreSamplesState.compareAndSet(current, claimed)) return true
        }
    }
}

private data class RestoreSamplesState(
    val confirmationPending: Boolean = false,
    val inProgress: Boolean = false,
    val outcome: RestoreSamplesOutcome? = null,
)
