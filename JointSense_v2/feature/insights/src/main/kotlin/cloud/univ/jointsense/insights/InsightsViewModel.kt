package cloud.univ.jointsense.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class InsightsViewModel(
    repository: TestSessionRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val sessions = repository.observeSessions()

    val homeState: StateFlow<HomeUiState> = sessions
        .map(::deriveHomeState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val trendsState: StateFlow<TrendsUiState> = sessions
        .map(::deriveTrendsState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrendsUiState())

    val reportState: StateFlow<ReportUiState> = sessions
        .map(::deriveReportState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportUiState())

    private fun deriveHomeState(sessions: List<TestSession>): HomeUiState {
        val results = sessions.allResults()
        val ai = BaselineInsightsMetrics.aiFromResults(results)
        return HomeUiState(
            allResults = results,
            latestValues = BaselineInsightsMetrics.latestPerFactor(results),
            factorSeries = results.factorSeries(),
            currentAi = ai,
            currentGrade = ai?.let(BaselineInsightsMetrics::grade),
            aiSeries = BaselineInsightsMetrics.aiSeries(sessions),
        )
    }

    private fun deriveTrendsState(sessions: List<TestSession>): TrendsUiState {
        val results = sessions.allResults()
        val aiSeries = BaselineInsightsMetrics.aiSeries(sessions)
        return TrendsUiState(
            factorSeries = results.factorSeries(),
            aiSeries = aiSeries,
            keyEvents = BaselineInsightsMetrics.keyEvents(sessions, aiSeries),
        )
    }

    private fun deriveReportState(sessions: List<TestSession>): ReportUiState {
        val results = sessions.allResults()
        val aiSeries = BaselineInsightsMetrics.aiSeries(sessions)
        val ai = BaselineInsightsMetrics.aiFromResults(results)
        return ReportUiState(
            latestValues = BaselineInsightsMetrics.latestPerFactor(results),
            currentAi = ai,
            currentGrade = ai?.let(BaselineInsightsMetrics::grade),
            factorDeltaPct7d = InflammationFactor.entries.associateWith { factor ->
                BaselineInsightsMetrics.factorDeltaPct7d(results, factor, clock())
            },
            aiWeekDeltaPct = BaselineInsightsMetrics.aiWeekDeltaPct(aiSeries, clock()),
        )
    }
}

private fun List<TestSession>.allResults(): List<TestResult> =
    flatMap(TestSession::results).sortedBy(TestResult::timestamp)

private fun List<TestResult>.factorSeries(): Map<InflammationFactor, List<InsightPoint>> =
    InflammationFactor.entries.associateWith { factor ->
        filter { it.factor == factor }.map { InsightPoint(it.timestamp, it.concentration) }
    }
