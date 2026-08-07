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
    val timestamp: Long = System.currentTimeMillis(),
)

data class TestResult(
    val id: String,
    val sessionId: String,
    val draftId: String?,
    val factor: InflammationFactor,
    val concentration: Float,
    val rangeStatus: RangeStatus,
    val features: RgbFeatures,
    val timestamp: Long,
)

data class TestSession(
    val id: String,
    val name: String,
    val createdAt: Long,
    val source: DataSource,
    val results: List<TestResult>,
)
