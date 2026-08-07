package cloud.univ.jointsense.data

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltInSampleProviderTest {
    @Test
    fun allTwelveSessionsAndThirtySixResultsMatchNormalizedGoldenData() {
        val actual = BuiltInSampleProvider().sessions

        assertEquals(12, actual.size)
        assertEquals(36, actual.sumOf { it.results.size })
        GOLDEN_SESSIONS.zip(actual).forEach { (expectedSession, actualSession) ->
            assertEquals(expectedSession.id, actualSession.id)
            assertEquals(expectedSession.name, actualSession.name)
            assertEquals(expectedSession.timestamp, actualSession.createdAt)
            assertEquals(DataSource.BUILT_IN, actualSession.source)
            assertEquals(3, actualSession.results.size)
            expectedSession.results.zip(actualSession.results).forEach { (expectedResult, actualResult) ->
                assertEquals(expectedResult.id, actualResult.id)
                assertEquals(expectedSession.id, actualResult.sessionId)
                assertEquals(expectedResult.factor, actualResult.factor)
                assertEquals(expectedResult.concentration, actualResult.concentration)
                assertEquals(expectedResult.features, actualResult.features)
                assertEquals(expectedSession.timestamp, actualResult.timestamp)
                assertEquals(RangeStatus.UNKNOWN, actualResult.rangeStatus)
                assertNull(actualResult.draftId)
            }
        }
    }

    private data class GoldenSession(
        val id: String,
        val name: String,
        val timestamp: Long,
        val results: List<GoldenResult>,
    )

    private data class GoldenResult(
        val id: String,
        val factor: InflammationFactor,
        val concentration: Float,
        val features: RgbFeatures,
    )

    private companion object {
        val GOLDEN_SESSIONS = listOf(
            goldenSession("builtin-tc1", "Test Plate 1 · 2026-07-28", 1_785_240_000_000L, 0f, 142f, 0f, 143f, 0f, 139f),
            goldenSession("builtin-tc2", "Test Plate 2 · 2026-07-29", 1_785_326_400_000L, 2f, 142.4f, 5f, 143.3f, 2f, 141.8f),
            goldenSession("builtin-tc3", "Test Plate 3 · 2026-07-30", 1_785_412_800_000L, 5f, 143f, 10f, 143.6f, 5f, 146f),
            goldenSession("builtin-tc4", "Test Plate 4 · 2026-07-31", 1_785_499_200_000L, 10f, 144f, 20f, 144.2f, 10f, 153f),
            goldenSession("builtin-tc5", "Test Plate 5 · 2026-08-01", 1_785_585_600_000L, 20f, 146f, 50f, 146f, 20f, 167f),
            goldenSession("builtin-tc6", "Test Plate 6 · 2026-08-02", 1_785_672_000_000L, 50f, 150f, 100f, 150f, 50f, 167f),
            goldenSession("builtin-tc7", "Test Plate 7 · 2026-08-03", 1_785_758_400_000L, 100f, 170f, 200f, 150f, 100f, 170f),
            goldenSession("builtin-tc8", "Test Plate 8 · 2026-08-04", 1_785_844_800_000L, 200f, 176f, 500f, 161f, 200f, 183f),
            goldenSession("builtin-tc9", "Test Plate 9 · 2026-08-05", 1_785_931_200_000L, 500f, 176f, 1000f, 161f, 500f, 183f),
            goldenSession("builtin-clip-1", "Clipboard Plate 1 · 2026-08-06 19:25", 1_786_015_500_000L, 0f, 142f, 0f, 143f, 0f, 139f),
            goldenSession("builtin-clip-2", "Clipboard Plate 2 · 2026-08-06 19:31", 1_786_015_860_000L, 15f, 145f, 0f, 143f, 7.142857f, 149f),
            goldenSession("builtin-clip-3", "Clipboard Plate 3 · 2026-08-06 23:20", 1_786_029_600_000L, 50f, 150f, 75f, 148f, 130.76923f, 174f),
        )

        fun goldenSession(
            id: String,
            name: String,
            timestamp: Long,
            tnfConcentration: Float,
            tnfBlue: Float,
            il6Concentration: Float,
            il6Blue: Float,
            il1Concentration: Float,
            il1Blue: Float,
        ) = GoldenSession(
            id = id,
            name = name,
            timestamp = timestamp,
            results = listOf(
                goldenResult("$id-tnf_alpha", InflammationFactor.TNF_ALPHA, tnfConcentration, tnfBlue),
                goldenResult("$id-il6", InflammationFactor.IL6, il6Concentration, il6Blue),
                goldenResult("$id-il1_beta", InflammationFactor.IL1_BETA, il1Concentration, il1Blue),
            ),
        )

        fun goldenResult(
            id: String,
            factor: InflammationFactor,
            concentration: Float,
            blueMean: Float,
        ) = GoldenResult(
            id = id,
            factor = factor,
            concentration = concentration,
            features = RgbFeatures(
                rMean = 150f,
                gMean = 146f,
                bMean = blueMean,
                rStd = 8f,
                gStd = 6f,
                bStd = 6f,
            ),
        )
    }
}
