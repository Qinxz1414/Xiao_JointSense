package cloud.univ.jointsense.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cloud.univ.jointsense.domain.repository.TestSessionRepository

class MeasurementViewModelFactory(
    private val repository: TestSessionRepository,
    private val analyzer: BaselinePhotoAnalysisAdapter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MeasurementViewModel::class.java))
        return MeasurementViewModel(repository, analyzer) as T
    }
}
