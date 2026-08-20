package cloud.univ.jointsense.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cloud.univ.jointsense.domain.repository.TestSessionRepository

class InsightsViewModelFactory(
    private val repository: TestSessionRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InsightsViewModel::class.java))
        return InsightsViewModel(repository) as T
    }
}
