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
    val text: String,
)

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

internal val InflammationFactor.displayName: String
    get() = when (this) {
        InflammationFactor.IL6 -> "Interleukin-6"
        InflammationFactor.TNF_ALPHA -> "Tumor Necrosis Factor-α"
        InflammationFactor.IL1_BETA -> "Interleukin-1β"
    }

internal const val FACTOR_UNIT = "pg/mL"
internal const val DAY_MILLIS = 86_400_000L
