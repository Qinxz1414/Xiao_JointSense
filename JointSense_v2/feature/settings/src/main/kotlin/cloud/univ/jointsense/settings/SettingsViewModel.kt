package cloud.univ.jointsense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import cloud.univ.jointsense.domain.repository.DataManagementRepository
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DataActionType {
    CLEAR_ALL,
    RESTORE_BUILT_IN_SAMPLES,
}

sealed interface DataAction {
    data object Idle : DataAction
    data class Pending(val type: DataActionType) : DataAction
    data class Running(val type: DataActionType) : DataAction
    data class Completed(val type: DataActionType) : DataAction
    data class Error(val type: DataActionType) : DataAction
}

data class SettingsUiState(
    val sessionCount: Int = 0,
    val measurementCount: Int = 0,
    val builtInSampleCount: Int = 0,
    val calibrationCount: Int = 0,
    val calibrationReviewCount: Int = 0,
    val dataAction: DataAction = DataAction.Idle,
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
    private val dataAction = MutableStateFlow<DataAction>(DataAction.Idle)

    val state: StateFlow<SettingsUiState> = combine(
        sessions.observeSessions(),
        calibrations.observeCalibrations(),
        dataAction,
    ) { observedSessions, observedCalibrations, action ->
        SettingsUiState(
            sessionCount = observedSessions.size,
            measurementCount = observedSessions.sumOf { it.results.size },
            builtInSampleCount = observedSessions.count { it.source == DataSource.BUILT_IN },
            calibrationCount = observedCalibrations.count {
                it.status == CalibrationStatus.ACTIVE
            },
            calibrationReviewCount = observedCalibrations.count {
                it.status == CalibrationStatus.NEEDS_REVIEW
            },
            dataAction = action,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun requestClearAllConfirmation() {
        request(DataActionType.CLEAR_ALL)
    }

    fun requestRestoreBuiltInSamplesConfirmation() {
        request(DataActionType.RESTORE_BUILT_IN_SAMPLES)
    }

    fun dismissDataAction() {
        dataAction.update { current ->
            when (current) {
                is DataAction.Running -> current
                else -> DataAction.Idle
            }
        }
    }

    fun confirmDataAction() {
        val action = claimPending() ?: return
        execute(action)
    }

    fun retryDataAction() {
        val action = claimError() ?: return
        execute(action)
    }

    fun consumeDataActionResult() {
        dataAction.update { current ->
            when (current) {
                is DataAction.Completed, is DataAction.Error -> DataAction.Idle
                else -> current
            }
        }
    }

    private fun request(type: DataActionType) {
        dataAction.compareAndSet(DataAction.Idle, DataAction.Pending(type))
    }

    private fun claimPending(): DataActionType? {
        while (true) {
            val current = dataAction.value as? DataAction.Pending ?: return null
            if (dataAction.compareAndSet(current, DataAction.Running(current.type))) {
                return current.type
            }
        }
    }

    private fun claimError(): DataActionType? {
        while (true) {
            val current = dataAction.value as? DataAction.Error ?: return null
            if (dataAction.compareAndSet(current, DataAction.Running(current.type))) {
                return current.type
            }
        }
    }

    private fun execute(type: DataActionType) {
        viewModelScope.launch {
            try {
                when (type) {
                    DataActionType.CLEAR_ALL -> dataManagement.clearAllData()
                    DataActionType.RESTORE_BUILT_IN_SAMPLES -> dataManagement.restoreBuiltInSamples()
                }
                dataAction.compareAndSet(
                    DataAction.Running(type),
                    DataAction.Completed(type),
                )
            } catch (cancellation: CancellationException) {
                dataAction.compareAndSet(DataAction.Running(type), DataAction.Idle)
                throw cancellation
            } catch (_: Exception) {
                dataAction.compareAndSet(
                    DataAction.Running(type),
                    DataAction.Error(type),
                )
            }
        }
    }
}
