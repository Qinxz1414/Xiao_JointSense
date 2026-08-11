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
import kotlinx.coroutines.launch

data class SettingsUiState(
    val sessionCount: Int = 0,
    val measurementCount: Int = 0,
    val calibrationCount: Int = 0,
    val calibrationReviewCount: Int = 0,
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
    val state: StateFlow<SettingsUiState> = combine(
        sessions.observeSessions(),
        calibrations.observeCalibrations(),
    ) { observedSessions, observedCalibrations ->
        SettingsUiState(
            sessionCount = observedSessions.size,
            measurementCount = observedSessions.sumOf { it.results.size },
            calibrationCount = observedCalibrations.count {
                it.status == CalibrationStatus.ACTIVE
            },
            calibrationReviewCount = observedCalibrations.count {
                it.status == CalibrationStatus.NEEDS_REVIEW
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun clearAllData() {
        viewModelScope.launch { dataManagement.clearAllData() }
    }

    fun restoreBuiltInSamples() {
        viewModelScope.launch { dataManagement.restoreBuiltInSamples() }
    }
}
