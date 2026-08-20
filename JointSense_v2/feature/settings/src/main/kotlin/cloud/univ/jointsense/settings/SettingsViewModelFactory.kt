package cloud.univ.jointsense.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import cloud.univ.jointsense.domain.repository.DataManagementRepository
import cloud.univ.jointsense.domain.repository.TestSessionRepository

class SettingsViewModelFactory(
    private val sessions: TestSessionRepository,
    private val calibrations: CalibrationRepository,
    private val dataManagement: DataManagementRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return SettingsViewModel(sessions, calibrations, dataManagement) as T
    }
}
