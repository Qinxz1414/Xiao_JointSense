package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySelectionTest {
    @Test
    fun historyChoosesNewestResultUsingTimestampAndStableIdFromUnsortedSession() {
        val session = TestSession(
            id = "session-1",
            name = "Test #1",
            createdAt = 1L,
            source = DataSource.USER,
            results = listOf(
                result(id = "result-z", timestamp = 200L),
                result(id = "result-a", timestamp = 200L),
                result(id = "result-old", timestamp = 100L),
            ),
        )

        assertEquals("result-z", latestHistoryResultId(session))
    }
}

private fun result(id: String, timestamp: Long) = TestResult(
    id = id,
    sessionId = "session-1",
    draftId = null,
    factor = InflammationFactor.IL6,
    concentration = 10f,
    rangeStatus = RangeStatus.IN_RANGE,
    features = RgbFeatures(90f, 100f, 110f, 1f, 1f, 1f),
    timestamp = timestamp,
)
