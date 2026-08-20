package cloud.univ.jointsense.insights

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.TestResult

data class InsightPoint(
    val time: Long,
    val value: Float,
)

enum class EventKind { TEST, UP, DOWN }

data class KeyEventItem(
    val time: Long,
    val kind: EventKind,
    val measurementCount: Int? = null,
    val aiValue: Float? = null,
    val previousAi: Float? = null,
    val currentAi: Float? = null,
)

enum class InsightSuggestion {
    CONTINUE_MONITORING,
    LOW_IMPACT_EXERCISE,
    DISCUSS_WITH_CLINICIAN,
    AVOID_OVERLOAD,
    REGULAR_MONITORING,
    SEEK_CLINICAL_REVIEW,
    REDUCE_JOINT_LOAD,
    FOLLOW_TREATMENT_PLAN,
    RETEST_SOONER,
    CURRENT_PLAN_EFFECTIVE,
}

data class HomeUiState(
    val allResults: List<TestResult> = emptyList(),
    val latestValues: Map<InflammationFactor, Float> = emptyMap(),
    val factorSeries: Map<InflammationFactor, List<InsightPoint>> = emptyMap(),
    val currentAi: Float? = null,
    val currentGrade: Int? = null,
    val aiSeries: List<InsightPoint> = emptyList(),
)

data class TrendsUiState(
    val factorSeries: Map<InflammationFactor, List<InsightPoint>> = emptyMap(),
    val aiSeries: List<InsightPoint> = emptyList(),
    val keyEvents: List<KeyEventItem> = emptyList(),
)

data class ReportUiState(
    val latestValues: Map<InflammationFactor, Float> = emptyMap(),
    val currentAi: Float? = null,
    val currentGrade: Int? = null,
    val factorDeltaPct7d: Map<InflammationFactor, Float?> = emptyMap(),
    val aiWeekDeltaPct: Float? = null,
)

internal val InflammationFactor.shortName: String
    get() = when (this) {
        InflammationFactor.IL6 -> "IL-6"
        InflammationFactor.TNF_ALPHA -> "TNF-α"
        InflammationFactor.IL1_BETA -> "IL-1β"
    }

internal const val DAY_MILLIS = 86_400_000L
