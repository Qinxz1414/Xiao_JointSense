package cloud.univ.jointsense.insights

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder

data class FactorPresentation(
    val factor: InflammationFactor,
    val value: Float?,
)

data class HomePresentation(
    val isEmpty: Boolean,
    val latestTimestamp: Long?,
    val oaIndex: Float?,
    val grade: Int?,
    val factorValues: List<FactorPresentation>,
    val recentObservations: List<InsightPoint>,
)

enum class TrendInterpretation {
    RISING,
    STABLE,
    FALLING,
    INSUFFICIENT_DATA,
}

data class ReportPresentation(
    val oaIndex: Float?,
    val grade: Int?,
    val factorValues: List<FactorPresentation>,
    val weekChangePercent: Float?,
    val trend: TrendInterpretation,
)

fun HomeUiState.toHomePresentation(): HomePresentation {
    val empty = allResults.isEmpty()
    val validAi = currentAi?.takeIf { it.isFinite() && it in 0f..1f }
    return HomePresentation(
        isEmpty = empty,
        latestTimestamp = if (empty) null else allResults.maxOf { it.timestamp },
        oaIndex = validAi.takeUnless { empty },
        grade = currentGrade?.takeIf { !empty && validAi != null && it in 0..4 },
        factorValues = inflammationFactorPresentationOrder.map { factor ->
            FactorPresentation(
                factor,
                latestValues[factor]?.takeIf { !empty && it.isFinite() && it >= 0f },
            )
        },
        recentObservations = if (empty) {
            emptyList()
        } else {
            aiSeries
                .filter { it.value.isFinite() && it.value in 0f..1f }
                .sortedBy(InsightPoint::time)
                .takeLast(RECENT_OBSERVATION_COUNT)
        },
    )
}

fun ReportUiState.toReportPresentation(): ReportPresentation {
    val validAi = currentAi?.takeIf { it.isFinite() && it in 0f..1f }
    val validWeekChange = aiWeekDeltaPct?.takeIf(Float::isFinite)
    return ReportPresentation(
        oaIndex = validAi,
        grade = currentGrade?.takeIf { validAi != null && it in 0..4 },
        factorValues = inflammationFactorPresentationOrder.map { factor ->
            FactorPresentation(factor, latestValues[factor]?.takeIf { it.isFinite() && it >= 0f })
        },
        weekChangePercent = validWeekChange,
        trend = trendInterpretation(validWeekChange),
    )
}

fun trendInterpretation(weekChangePercent: Float?): TrendInterpretation = when {
    weekChangePercent == null || !weekChangePercent.isFinite() ->
        TrendInterpretation.INSUFFICIENT_DATA
    weekChangePercent > STABLE_TREND_BOUND_PERCENT -> TrendInterpretation.RISING
    weekChangePercent < -STABLE_TREND_BOUND_PERCENT -> TrendInterpretation.FALLING
    else -> TrendInterpretation.STABLE
}

private const val RECENT_OBSERVATION_COUNT = 7
private const val STABLE_TREND_BOUND_PERCENT = 10f
