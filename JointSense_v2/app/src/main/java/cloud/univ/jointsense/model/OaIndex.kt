package cloud.univ.jointsense.model

import androidx.compose.ui.graphics.Color
import cloud.univ.jointsense.data.InflammationFactor
import cloud.univ.jointsense.data.TestResult
import cloud.univ.jointsense.data.TestSession
import cloud.univ.jointsense.designsystem.theme.WellPalette
import kotlin.math.roundToInt

/**
 * OA Inflammation Index (AI) model.
 *
 * The AI is a heuristic composite of the three inflammation factors,
 * normalized against each assay's working-range cap and combined with
 * fixed clinical-priority weights. It is a PLACEHOLDER model pending
 * the trained LASSO composite export; thresholds follow the design
 * specification in Rule/界面.png.
 *
 * Grade mapping (design spec):
 *   0 no risk      AI in [0.00, 0.25)
 *   1 mild         AI in [0.25, 0.50)
 *   2 moderate     AI in [0.50, 0.75)
 *   3 severe       AI in [0.75, 0.90)
 *   4 very severe  AI in [0.90, 1.00]
 */
object OaIndex {

    /**
     * Assay working-range caps in pg/mL (normalization denominators).
     *
     * These define the upper end of each factor's standard curve and are
     * used to normalize a concentration to 0..1 before the composite AI
     * is computed. They were raised from the previous placeholder values
     * (6 / 8 / 4) to the actual upper bounds of the 样本.pptx standard
     * ladder — TNF-α & IL-1β reach 500 pg/mL, IL-6 reaches 1000 pg/mL —
     * so real measurements (and the built-in detection data) no longer
     * saturate the AI index at grade 4.
     *
     * Tune these to the validated kit's working range for production use.
     */
    val caps: Map<InflammationFactor, Float> = mapOf(
        InflammationFactor.TNF_ALPHA to 500f,
        InflammationFactor.IL6 to 1000f,
        InflammationFactor.IL1_BETA to 500f
    )

    /** Composite weights; renormalized over whichever factors are present. */
    val weights: Map<InflammationFactor, Float> = mapOf(
        InflammationFactor.TNF_ALPHA to 0.40f,
        InflammationFactor.IL6 to 0.35f,
        InflammationFactor.IL1_BETA to 0.25f
    )

    /** Normalize a concentration to 0..1 against the factor cap. */
    fun normalize(factor: InflammationFactor, concentration: Float): Float {
        val cap = caps[factor] ?: return 0f
        return (concentration / cap).coerceIn(0f, 1f)
    }

    /** Latest result per factor from a result list (by timestamp). */
    fun latestPerFactor(results: List<TestResult>): Map<InflammationFactor, Float> =
        results.groupBy { it.factor }
            .mapValues { (_, list) -> list.maxBy { it.timestamp }.concentration }

    /**
     * Composite AI from a set of factor values. Returns null when no
     * factor is available.
     */
    fun aiFromValues(values: Map<InflammationFactor, Float>): Float? {
        if (values.isEmpty()) return null
        var weightSum = 0f
        var acc = 0f
        for ((factor, value) in values) {
            val w = weights[factor] ?: continue
            acc += w * normalize(factor, value)
            weightSum += w
        }
        return if (weightSum > 0f) (acc / weightSum).coerceIn(0f, 1f) else null
    }

    /** Composite AI for a result list (latest value per factor). */
    fun aiFromResults(results: List<TestResult>): Float? =
        aiFromValues(latestPerFactor(results))

    /** Grade 0..4 from an AI value. */
    fun grade(ai: Float): Int = when {
        ai < 0.25f -> 0
        ai < 0.50f -> 1
        ai < 0.75f -> 2
        ai < 0.90f -> 3
        else -> 4
    }

    val gradeLabels = listOf("No risk", "Mild", "Moderate", "Severe", "Very severe")
    val gradeRanges = listOf("0~0.25", "0.25~0.50", "0.50~0.75", "0.75~0.90", "0.90~1.00")

    fun gradeLabel(grade: Int) = gradeLabels[grade.coerceIn(0, 4)]

    /** Activity-state headline used on the AI report card. */
    fun activityLabel(grade: Int): String = listOf(
        "Minimal OA activity",
        "Low OA activity",
        "Moderate OA activity",
        "High OA activity",
        "Very high OA activity"
    )[grade.coerceIn(0, 4)]

    /** Risk headline used under the gauge. */
    fun riskLabel(grade: Int): String = listOf(
        "Very low risk",
        "Low risk",
        "Medium risk",
        "High risk",
        "Very high risk"
    )[grade.coerceIn(0, 4)]

    /**
     * Map a normalized signal intensity (0..1) onto the ELISA well
     * palette (transparent -> blue-green), per Rule/SKILL.md.
     */
    fun wellColor(intensity: Float): Color {
        val idx = (intensity.coerceIn(0f, 1f) * (WellPalette.size - 1)).roundToInt()
        return WellPalette[idx]
    }

    /**
     * Chronological AI series: one point per session that has results,
     * timestamped at its most recent result.
     */
    fun aiSeries(sessions: List<TestSession>): List<Pair<Long, Float>> =
        sessions
            .filter { it.results.isNotEmpty() }
            .mapNotNull { session ->
                aiFromResults(session.results)?.let {
                    session.results.maxOf { r -> r.timestamp } to it
                }
            }
            .sortedBy { it.first }

    /**
     * Rule-based AI suggestions for the report screen.
     *
     * @param grade current OA inflammation grade (0..4)
     * @param weekDeltaPct change of the latest AI vs the previous week
     *        in percent, null when no comparison is possible
     */
    fun suggestions(grade: Int, weekDeltaPct: Float?): List<String> {
        val out = mutableListOf<String>()
        when {
            grade <= 1 -> {
                out += "Continue the current care and monitoring plan."
                out += "Maintain regular, low-impact joint-friendly exercise."
            }
            grade == 2 -> {
                out += "Discuss the result with your clinician at the next visit."
                out += "Prefer low-impact activity and avoid joint overloading."
                out += "Keep a regular monitoring cadence (2-3 times / week)."
            }
            else -> {
                out += "Seek clinical review promptly for treatment adjustment."
                out += "Reduce joint load; pause high-impact activity."
                out += "Follow the prescribed treatment plan and re-test after therapy."
            }
        }
        when {
            weekDeltaPct != null && weekDeltaPct > 10f ->
                out += "Inflammatory markers trended up vs last week - re-check sooner."
            weekDeltaPct != null && weekDeltaPct < -10f ->
                out += "Markers trended down vs last week - current plan appears effective."
        }
        return out
    }
}
