package cloud.univ.jointsense.data

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession

class BuiltInSampleProvider {
    val sessions: List<TestSession> = buildList {
        TEST_CASE_CONCENTRATIONS.indices.forEach { index -> add(testCasePlate(index)) }
        add(clipboardPlate("builtin-clip-1", CLIP_1_TIMESTAMP, CLIPBOARD_SIGNALS[0]))
        add(clipboardPlate("builtin-clip-2", CLIP_2_TIMESTAMP, CLIPBOARD_SIGNALS[1]))
        add(clipboardPlate("builtin-clip-3", CLIP_3_TIMESTAMP, CLIPBOARD_SIGNALS[2]))
    }

    private fun testCasePlate(index: Int): TestSession {
        val id = "builtin-tc${index + 1}"
        val timestamp = TEST_CASE_TIMESTAMPS[index]
        return TestSession(
            id = id,
            name = id,
            createdAt = timestamp,
            source = DataSource.BUILT_IN,
            results = FACTORS.mapIndexed { factorIndex, factor ->
                result(id, factor, TEST_CASE_CONCENTRATIONS[index][factorIndex], timestamp)
            },
        )
    }

    private fun clipboardPlate(
        id: String,
        timestamp: Long,
        signals: FloatArray,
    ): TestSession = TestSession(
        id = id,
        name = id,
        createdAt = timestamp,
        source = DataSource.BUILT_IN,
        results = FACTORS.mapIndexed { index, factor ->
            result(id, factor, concentrationFor(signals[index], factor), timestamp)
        },
    )

    private fun result(
        sessionId: String,
        factor: InflammationFactor,
        concentration: Float,
        timestamp: Long,
    ): TestResult {
        val signal = signalForConcentration(concentration, factor).coerceIn(-20f, 40f)
        return TestResult(
            id = "$sessionId-${factor.name.lowercase()}",
            sessionId = sessionId,
            draftId = null,
            factor = factor,
            concentration = concentration,
            rangeStatus = RangeStatus.UNKNOWN,
            features = RgbFeatures(
                rMean = 150f,
                gMean = 146f,
                bMean = (150f + signal).coerceIn(0f, 255f),
                rStd = 8f,
                gStd = 6f,
                bStd = 6f,
            ),
            timestamp = timestamp,
        )
    }

    private fun concentrationFor(signal: Float, factor: InflammationFactor): Float {
        val knots = FACTORY_KNOTS.getValue(factor)
        if (signal <= knots.first().second) return 0f
        if (signal >= knots.last().second) return knots.last().first
        for (index in 1 until knots.size) {
            if (signal <= knots[index].second) {
                val (c0, s0) = knots[index - 1]
                val (c1, s1) = knots[index]
                if (s1 == s0) return c1
                return c0 + (c1 - c0) * (signal - s0) / (s1 - s0)
            }
        }
        return knots.last().first
    }

    private fun signalForConcentration(concentration: Float, factor: InflammationFactor): Float {
        val knots = FACTORY_KNOTS.getValue(factor)
        if (concentration <= 0f) return knots.first().second
        if (concentration >= knots.last().first) return knots.last().second
        for (index in 1 until knots.size) {
            if (concentration <= knots[index].first) {
                val (c0, s0) = knots[index - 1]
                val (c1, s1) = knots[index]
                if (c1 == c0) return s1
                return s0 + (s1 - s0) * (concentration - c0) / (c1 - c0)
            }
        }
        return knots.last().second
    }

    private companion object {
        val FACTORS = listOf(
            InflammationFactor.TNF_ALPHA,
            InflammationFactor.IL6,
            InflammationFactor.IL1_BETA,
        )
        val FACTORY_KNOTS = mapOf(
            InflammationFactor.TNF_ALPHA to listOf(0f to -8f, 20f to -4f, 50f to 0f, 100f to 20f, 200f to 26f),
            InflammationFactor.IL6 to listOf(0f to -7f, 50f to -4f, 100f to 0f, 200f to 0f, 500f to 11f),
            InflammationFactor.IL1_BETA to listOf(0f to -11f, 20f to 17f, 50f to 17f, 100f to 20f, 200f to 33f),
        )
        val TEST_CASE_TIMESTAMPS = longArrayOf(
            1_785_240_000_000L, 1_785_326_400_000L, 1_785_412_800_000L,
            1_785_499_200_000L, 1_785_585_600_000L, 1_785_672_000_000L,
            1_785_758_400_000L, 1_785_844_800_000L, 1_785_931_200_000L,
        )
        val TEST_CASE_CONCENTRATIONS = arrayOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(2f, 5f, 2f),
            floatArrayOf(5f, 10f, 5f),
            floatArrayOf(10f, 20f, 10f),
            floatArrayOf(20f, 50f, 20f),
            floatArrayOf(50f, 100f, 50f),
            floatArrayOf(100f, 200f, 100f),
            floatArrayOf(200f, 500f, 200f),
            floatArrayOf(500f, 1000f, 500f),
        )
        val CLIPBOARD_SIGNALS = arrayOf(
            floatArrayOf(-9f, -8f, -12f),
            floatArrayOf(-5f, -17f, -1f),
            floatArrayOf(0f, -2f, 24f),
        )
        const val CLIP_1_TIMESTAMP = 1_786_015_500_000L
        const val CLIP_2_TIMESTAMP = 1_786_015_860_000L
        const val CLIP_3_TIMESTAMP = 1_786_029_600_000L
    }
}
