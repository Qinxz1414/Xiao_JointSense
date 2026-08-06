package cloud.univ.jointsense.data

import java.util.UUID

data class TestResult(
    val id: String = UUID.randomUUID().toString(),
    val factor: InflammationFactor,
    val concentration: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val rMean: Float = 0f,
    val gMean: Float = 0f,
    val bMean: Float = 0f,
    val rStd: Float = 0f,
    val gStd: Float = 0f,
    val bStd: Float = 0f
)

data class TestSession(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val results: List<TestResult> = emptyList()
)
