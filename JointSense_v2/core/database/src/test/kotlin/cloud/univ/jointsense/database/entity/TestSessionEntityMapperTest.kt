package cloud.univ.jointsense.database.entity

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TestSessionEntityMapperTest {
    @Test
    fun mapperOrdersInverseTimeInputByTimestampThenId() {
        val mapped = TestSessionWithResults(
            session = TestSessionEntity(
                id = "session-1",
                name = "Test #1",
                createdAt = 1L,
                source = DataSource.USER,
            ),
            results = listOf(
                result(id = "result-latest", timestamp = 30L),
                result(id = "result-tie-z", timestamp = 20L),
                result(id = "result-tie-a", timestamp = 20L),
                result(id = "result-oldest", timestamp = 10L),
            ),
        ).toDomain()

        assertEquals(
            listOf("result-oldest", "result-tie-a", "result-tie-z", "result-latest"),
            mapped.results.map { it.id },
        )
    }
}

private fun result(id: String, timestamp: Long) = TestResultEntity(
    id = id,
    sessionId = "session-1",
    draftId = null,
    factor = InflammationFactor.IL6,
    concentration = 10f,
    rangeStatus = RangeStatus.IN_RANGE,
    timestamp = timestamp,
    rMean = 90f,
    gMean = 100f,
    bMean = 110f,
    rStd = 1f,
    gStd = 1f,
    bStd = 1f,
)
