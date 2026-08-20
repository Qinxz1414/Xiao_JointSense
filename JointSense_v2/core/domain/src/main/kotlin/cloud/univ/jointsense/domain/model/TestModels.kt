package cloud.univ.jointsense.domain.model

enum class DataSource { USER, BUILT_IN }

enum class RangeStatus { UNKNOWN, BELOW_RANGE, IN_RANGE, ABOVE_RANGE }

data class RgbFeatures(
    val rMean: Float,
    val gMean: Float,
    val bMean: Float,
    val rStd: Float,
    val gStd: Float,
    val bStd: Float,
) {
    val tealness: Float
        get() = bMean - rMean
}

data class NewTestResult(
    val factor: InflammationFactor,
    val concentration: Float,
    val rangeStatus: RangeStatus,
    val features: RgbFeatures,
    val rawSignal: Float,
    val signalMethod: ColorSignalMethod,
    val timestamp: Long = System.currentTimeMillis(),
)

/** One photographed horizontal three-disc assay, in physical left-to-right order. */
data class NewMeasurementBatch(
    val results: List<NewTestResult>,
    val timestamp: Long = System.currentTimeMillis(),
) {
    init {
        require(results.map(NewTestResult::factor) == inflammationFactorPresentationOrder) {
            "A measurement batch must contain TNF-alpha, IL-6, and IL-1beta in left-to-right order."
        }
        require(results.all(NewTestResult::isScientificallyValid)) {
            "Measurement batch results must contain finite non-negative concentrations and finite RGB features."
        }
        require(results.all { it.signalMethod == ColorSignalMethod.PIXEL_BR_P90_V1 }) {
            "A new photo measurement batch must use the pixel B-R P90 signal method."
        }
    }
}

data class TestResult(
    val id: String,
    val sessionId: String,
    val draftId: String?,
    val factor: InflammationFactor,
    val concentration: Float,
    val rangeStatus: RangeStatus,
    val features: RgbFeatures,
    val timestamp: Long,
    val measurementBatchId: String? = null,
    val rawSignal: Float = features.tealness,
    val signalMethod: ColorSignalMethod = ColorSignalMethod.LEGACY_MEAN_BR,
)

data class TestMeasurementBatch(
    val id: String,
    val sessionId: String,
    val timestamp: Long,
    val results: List<TestResult>,
    val isLegacySingleFactor: Boolean,
)

data class TestSession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val source: DataSource,
    val results: List<TestResult>,
)

fun TestSession.measurementBatches(): List<TestMeasurementBatch> {
    if (
        source == DataSource.BUILT_IN &&
        results.size == inflammationFactorPresentationOrder.size &&
        results.all { it.measurementBatchId == null } &&
        results.map { it.factor }.toSet() == inflammationFactorPresentationOrder.toSet()
    ) {
        return listOf(
            TestMeasurementBatch(
                id = id,
                sessionId = id,
                timestamp = results.maxOf(TestResult::timestamp),
                results = results.sortedBy { inflammationFactorPresentationOrder.indexOf(it.factor) },
                isLegacySingleFactor = false,
            ),
        )
    }
    return results.groupBy { result -> result.measurementBatchId ?: "legacy:${result.id}" }
    .map { (groupKey, groupedResults) ->
        val ordered = groupedResults.sortedBy { result ->
            inflammationFactorPresentationOrder.indexOf(result.factor).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
        TestMeasurementBatch(
            id = ordered.first().measurementBatchId ?: ordered.first().id,
            sessionId = id,
            timestamp = ordered.maxOf(TestResult::timestamp),
            results = ordered,
            isLegacySingleFactor = groupKey.startsWith("legacy:"),
        )
    }
    .sortedWith(compareBy<TestMeasurementBatch> { it.timestamp }.thenBy { it.id })
}

fun TestSession.measurementBatchCount(): Int = measurementBatches().size

private fun NewTestResult.isScientificallyValid(): Boolean =
    concentration.isFinite() && concentration >= 0f && rawSignal.isFinite() &&
        listOf(
            features.rMean,
            features.gMean,
            features.bMean,
            features.rStd,
            features.gStd,
            features.bStd,
        ).all(Float::isFinite)
