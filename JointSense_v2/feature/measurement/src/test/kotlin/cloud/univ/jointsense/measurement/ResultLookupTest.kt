package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResultLookupTest {
    @Test
    fun staleCurrentSessionCannotOverrideRequestedResultId() {
        val stale = session("stale", result("wrong", "stale"))
        val requested = session("requested", result("target", "requested"))

        val located = locateResultById(
            resultId = "target",
            currentSession = stale,
            sessions = listOf(stale, requested),
        )

        assertEquals("requested", located?.session?.id)
        assertEquals("target", located?.result?.id)
    }

    @Test
    fun currentSessionIsUsedOnlyWhenItContainsRequestedResult() {
        val current = session("current", result("target", "current"))

        val located = locateResultById("target", current, emptyList())

        assertEquals("current", located?.session?.id)
        assertEquals("target", located?.result?.id)
    }

    @Test
    fun missingResultReturnsNoSyntheticUnknownResult() {
        val current = session("current", result("other", "current"))

        assertNull(locateResultById("missing", current, listOf(current)))
    }

    private fun session(id: String, result: TestResult) = TestSession(
        id = id,
        name = id,
        createdAt = 1L,
        source = DataSource.USER,
        results = listOf(result),
    )

    private fun result(id: String, sessionId: String) = TestResult(
        id = id,
        sessionId = sessionId,
        draftId = null,
        factor = InflammationFactor.IL6,
        concentration = 1f,
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(1f, 1f, 1f, 1f, 1f, 1f),
        timestamp = 1L,
    )
}
