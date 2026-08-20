package cloud.univ.jointsense.data.legacy

import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LegacyJsonParserTest {
    private val parser = LegacyJsonParser()

    @Test
    fun parsesLegacySessionWithoutInventingRangeOrDraft() {
        val parsed = parser.parseSessions(LEGACY_SESSION_JSON)

        val session = parsed.single()
        val result = session.results.single()
        assertEquals("session-1", session.id)
        assertEquals("Legacy test", session.name)
        assertEquals(123L, session.createdAt)
        assertEquals(DataSource.USER, session.source)
        assertEquals("result-1", result.id)
        assertEquals("session-1", result.sessionId)
        assertEquals(InflammationFactor.TNF_ALPHA, result.factor)
        assertEquals(42.5f, result.concentration)
        assertEquals(RangeStatus.UNKNOWN, result.rangeStatus)
        assertNull(result.draftId)
        assertEquals(130f, result.features.bMean)
    }

    @Test
    fun malformedSecondResultRejectsWholePayload() {
        val malformedResult = LEGACY_RESULT_JSON.replace(
            "\"concentration\":42.5",
            "\"concentration\":\"bad\"",
        )
        val malformed = LEGACY_SESSION_JSON.replace(
            LEGACY_RESULT_JSON,
            "$LEGACY_RESULT_JSON,$malformedResult",
        )

        assertThrows(LegacyParseException::class.java) {
            parser.parseSessions(malformed)
        }
    }

    @Test
    fun rejectsWrongIdTypeUnknownEnumMissingNumberAndNonFiniteNumber() {
        val malformedPayloads = listOf(
            LEGACY_SESSION_JSON.replace("\"id\":\"result-1\"", "\"id\":7"),
            LEGACY_SESSION_JSON.replace("\"TNF_ALPHA\"", "\"TNF-A\""),
            LEGACY_SESSION_JSON.replace("\"rStd\":1.0,", ""),
            LEGACY_SESSION_JSON.replace("\"concentration\":42.5", "\"concentration\":1e999"),
        )

        malformedPayloads.forEach { payload ->
            assertThrows(LegacyParseException::class.java) {
                parser.parseSessions(payload)
            }
        }
    }

    @Test
    fun parsesEveryLegacyCalibrationAsNeedsReview() {
        val parsed = parser.parseCalibrations(
            """{
                "createdAt":456,
                "factors":{
                    "IL6":[{"c":0.0,"s":-8.0},{"c":5.0,"s":2.5}],
                    "IL1_BETA":[{"c":0.0,"s":-10.0}]
                }
            }""".trimIndent(),
        )

        assertEquals(2, parsed.size)
        val il6 = parsed.single { it.factor == InflammationFactor.IL6 }
        assertEquals(456L, il6.createdAt)
        assertEquals(1, il6.version)
        assertEquals(CalibrationStatus.NEEDS_REVIEW, il6.status)
        assertNull(il6.kitName)
        assertNull(il6.kitLot)
        assertEquals(2, il6.knots.size)
        assertEquals(-8f, il6.knots.first().rawSignal)
        assertEquals(-8f, il6.knots.first().netSignal)
        assertEquals(-8f, il6.knots.first().fittedSignal)
        assertEquals(true, il6.knots.first().isBlank)
        assertFalse(il6.knots.last().isBlank)
    }

    @Test
    fun malformedSecondCalibrationKnotRejectsWholePayload() {
        val malformed = """{
            "createdAt":456,
            "factors":{
                "IL6":[{"c":0.0,"s":-8.0},{"c":5.0,"s":"bad"}]
            }
        }""".trimIndent()

        assertThrows(LegacyParseException::class.java) {
            parser.parseCalibrations(malformed)
        }
    }

    private companion object {
        const val LEGACY_RESULT_JSON = """{
            "id":"result-1",
            "factor":"TNF_ALPHA",
            "concentration":42.5,
            "timestamp":124,
            "rMean":100.0,
            "gMean":110.0,
            "bMean":130.0,
            "rStd":1.0,
            "gStd":2.0,
            "bStd":3.0
        }"""

        val LEGACY_SESSION_JSON = """[{
            "id":"session-1",
            "name":"Legacy test",
            "createdAt":123,
            "results":[$LEGACY_RESULT_JSON]
        }]""".trimIndent()
    }
}
