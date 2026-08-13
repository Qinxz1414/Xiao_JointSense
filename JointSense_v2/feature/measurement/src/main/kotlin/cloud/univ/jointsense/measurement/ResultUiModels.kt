package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder

data class ResultFactorPresentation(
    val factor: InflammationFactor,
    val value: Float?,
)

data class ResultUiModel(
    val measuredFactor: InflammationFactor?,
    val concentration: Float?,
    val rangeStatus: RangeStatus?,
    val features: RgbFeatures?,
    val factorValues: List<ResultFactorPresentation>,
    val oaIndex: Float?,
    val grade: Int?,
)

fun createResultUiModel(
    session: TestSession?,
    lastResult: TestResult?,
): ResultUiModel {
    val validResults = session?.results.orEmpty().filter { result ->
        result.concentration.isFinite() && result.concentration >= 0f
    }
    val latest = BaselineMeasurementMetrics.latestPerFactor(validResults)
    val oaIndex = BaselineMeasurementMetrics.aiFromResults(validResults)
        ?.takeIf { it.isFinite() && it in 0f..1f }
    return ResultUiModel(
        measuredFactor = lastResult?.factor,
        concentration = lastResult?.concentration?.takeIf { it.isFinite() && it >= 0f },
        rangeStatus = lastResult?.rangeStatus,
        features = lastResult?.features?.takeIf(RgbFeatures::hasOnlyFiniteValues),
        factorValues = inflammationFactorPresentationOrder.map { factor ->
            ResultFactorPresentation(factor, latest[factor]?.takeIf(Float::isFinite))
        },
        oaIndex = oaIndex,
        grade = oaIndex?.let(BaselineMeasurementMetrics::grade),
    )
}

private fun RgbFeatures.hasOnlyFiniteValues(): Boolean = listOf(
    rMean,
    gMean,
    bMean,
    rStd,
    gStd,
    bStd,
).all(Float::isFinite)
