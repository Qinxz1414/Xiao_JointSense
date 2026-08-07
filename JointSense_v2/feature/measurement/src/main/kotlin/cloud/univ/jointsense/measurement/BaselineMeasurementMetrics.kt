package cloud.univ.jointsense.measurement

import androidx.compose.ui.graphics.Color
import cloud.univ.jointsense.designsystem.theme.WellPalette
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.TestResult
import kotlin.math.roundToInt

/** Phase-1 result compatibility math, intentionally local for Phase-2 replacement. */
internal object BaselineMeasurementMetrics {
    private val caps = mapOf(
        InflammationFactor.TNF_ALPHA to 500f,
        InflammationFactor.IL6 to 1_000f,
        InflammationFactor.IL1_BETA to 500f,
    )
    private val weights = mapOf(
        InflammationFactor.TNF_ALPHA to 0.40f,
        InflammationFactor.IL6 to 0.35f,
        InflammationFactor.IL1_BETA to 0.25f,
    )

    fun latestPerFactor(results: List<TestResult>): Map<InflammationFactor, Float> =
        results.groupBy(TestResult::factor)
            .mapValues { (_, values) -> values.maxBy(TestResult::timestamp).concentration }

    fun aiFromResults(results: List<TestResult>): Float? {
        val values = latestPerFactor(results)
        if (values.isEmpty()) return null
        var total = 0f
        var weightSum = 0f
        values.forEach { (factor, value) ->
            val weight = weights.getValue(factor)
            total += weight * normalize(factor, value)
            weightSum += weight
        }
        return if (weightSum == 0f) null else (total / weightSum).coerceIn(0f, 1f)
    }

    fun normalize(factor: InflammationFactor, value: Float): Float =
        (value / caps.getValue(factor)).coerceIn(0f, 1f)

    fun grade(ai: Float): Int = when {
        ai < 0.25f -> 0
        ai < 0.50f -> 1
        ai < 0.75f -> 2
        ai < 0.90f -> 3
        else -> 4
    }

    fun gradeLabel(grade: Int): String =
        listOf("No risk", "Mild", "Moderate", "Severe", "Very severe")[grade.coerceIn(0, 4)]

    fun wellColor(intensity: Float): Color {
        val index = (intensity.coerceIn(0f, 1f) * (WellPalette.size - 1)).roundToInt()
        return WellPalette[index]
    }
}

internal val InflammationFactor.shortName: String
    get() = when (this) {
        InflammationFactor.IL6 -> "IL-6"
        InflammationFactor.TNF_ALPHA -> "TNF-α"
        InflammationFactor.IL1_BETA -> "IL-1β"
    }

internal val InflammationFactor.displayName: String
    get() = when (this) {
        InflammationFactor.IL6 -> "Interleukin-6"
        InflammationFactor.TNF_ALPHA -> "Tumor Necrosis Factor-α"
        InflammationFactor.IL1_BETA -> "Interleukin-1β"
    }

internal const val FACTOR_UNIT = "pg/mL"
