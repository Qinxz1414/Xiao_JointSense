package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder
import cloud.univ.jointsense.domain.model.measurementBatches

data class ResultFactorPresentation(
    val factor: InflammationFactor,
    val value: Float?,
)

data class ResultMeasurementPresentation(
    val factor: InflammationFactor,
    val concentration: Float?,
    val rangeStatus: RangeStatus?,
    val features: RgbFeatures?,
    val rawSignal: Float?,
    val signalMethod: ColorSignalMethod,
)

data class ResultUiModel(
    val measuredFactor: InflammationFactor?,
    val concentration: Float?,
    val rangeStatus: RangeStatus?,
    val features: RgbFeatures?,
    val factorValues: List<ResultFactorPresentation>,
    val measurements: List<ResultMeasurementPresentation>,
    val oaIndex: Float?,
    val grade: Int?,
)

fun createResultUiModel(
    session: TestSession?,
    lastResult: TestResult?,
): ResultUiModel {
    val batchResults = session?.measurementBatches()
        ?.firstOrNull { batch ->
            batch.id == lastResult?.measurementBatchId || batch.results.any { it.id == lastResult?.id }
        }
        ?.results
        ?: listOfNotNull(lastResult)
    val validResults = batchResults.filter { result ->
        result.concentration.isFinite() && result.concentration >= 0f
    }
    val latest = BaselineMeasurementMetrics.latestPerFactor(validResults)
    val isCompleteTriplex = validResults.map(TestResult::factor) == inflammationFactorPresentationOrder
    val oaIndex = validResults.takeIf { isCompleteTriplex }
        ?.let(BaselineMeasurementMetrics::aiFromResults)
        ?.takeIf { it.isFinite() && it in 0f..1f }
    val concentration = lastResult?.concentration?.takeIf { it.isFinite() && it >= 0f }
    return ResultUiModel(
        measuredFactor = lastResult?.factor,
        concentration = concentration,
        rangeStatus = lastResult?.rangeStatus?.takeIf { concentration != null },
        features = lastResult?.features?.takeIf(RgbFeatures::hasOnlyFiniteValues),
        factorValues = inflammationFactorPresentationOrder.map { factor ->
            ResultFactorPresentation(factor, latest[factor]?.takeIf(Float::isFinite))
        },
        measurements = inflammationFactorPresentationOrder.mapNotNull { factor ->
            batchResults.firstOrNull { it.factor == factor }?.let { result ->
                val validConcentration = result.concentration.takeIf { it.isFinite() && it >= 0f }
                ResultMeasurementPresentation(
                    factor = factor,
                    concentration = validConcentration,
                    rangeStatus = result.rangeStatus.takeIf { validConcentration != null },
                    features = result.features.takeIf(RgbFeatures::hasOnlyFiniteValues),
                    rawSignal = result.rawSignal.takeIf(Float::isFinite),
                    signalMethod = result.signalMethod,
                )
            }
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
    && tealness.isFinite()
