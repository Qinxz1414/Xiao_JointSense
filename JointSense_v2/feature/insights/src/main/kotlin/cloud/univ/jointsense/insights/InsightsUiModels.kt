package cloud.univ.jointsense.insights

import cloud.univ.jointsense.domain.model.InflammationFactor

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
    return HomePresentation(
        isEmpty = empty,
        latestTimestamp = if (empty) null else allResults.maxOf { it.timestamp },
        oaIndex = currentAi.takeUnless { empty },
        grade = currentGrade.takeUnless { empty },
        factorValues = InflammationFactor.entries.map { factor ->
            FactorPresentation(factor, latestValues[factor].takeUnless { empty })
        },
        recentObservations = if (empty) {
            emptyList()
        } else {
            aiSeries.sortedBy(InsightPoint::time).takeLast(RECENT_OBSERVATION_COUNT)
        },
    )
}

fun ReportUiState.toReportPresentation(): ReportPresentation = ReportPresentation(
    oaIndex = currentAi,
    grade = currentGrade?.takeIf { it in 0..4 },
    factorValues = InflammationFactor.entries.map { factor ->
        FactorPresentation(factor, latestValues[factor])
    },
    weekChangePercent = aiWeekDeltaPct,
    trend = trendInterpretation(aiWeekDeltaPct),
)

fun trendInterpretation(weekChangePercent: Float?): TrendInterpretation = when {
    weekChangePercent == null || !weekChangePercent.isFinite() ->
        TrendInterpretation.INSUFFICIENT_DATA
    weekChangePercent > STABLE_TREND_BOUND_PERCENT -> TrendInterpretation.RISING
    weekChangePercent < -STABLE_TREND_BOUND_PERCENT -> TrendInterpretation.FALLING
    else -> TrendInterpretation.STABLE
}

private const val RECENT_OBSERVATION_COUNT = 7
private const val STABLE_TREND_BOUND_PERCENT = 10f
