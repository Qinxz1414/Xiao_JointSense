package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession

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
    val latest = BaselineMeasurementMetrics.latestPerFactor(session?.results.orEmpty())
    val oaIndex = BaselineMeasurementMetrics.aiFromResults(session?.results.orEmpty())
    return ResultUiModel(
        measuredFactor = lastResult?.factor,
        concentration = lastResult?.concentration,
        rangeStatus = lastResult?.rangeStatus,
        features = lastResult?.features,
        factorValues = InflammationFactor.entries.map { factor ->
            ResultFactorPresentation(factor, latest[factor])
        },
        oaIndex = oaIndex,
        grade = oaIndex?.let(BaselineMeasurementMetrics::grade),
    )
}
