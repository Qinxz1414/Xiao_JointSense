package cloud.univ.jointsense.insights

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import kotlin.math.abs

/** Phase-1 compatibility math retained only until the Phase-2 analysis contracts replace it. */
internal object BaselineInsightsMetrics {
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

    fun aiFromResults(results: List<TestResult>): Float? = aiFromValues(latestPerFactor(results))

    fun aiFromValues(values: Map<InflammationFactor, Float>): Float? {
        if (values.isEmpty()) return null
        var weighted = 0f
        var weightSum = 0f
        values.forEach { (factor, concentration) ->
            val weight = weights[factor] ?: return@forEach
            val cap = caps.getValue(factor)
            weighted += weight * (concentration / cap).coerceIn(0f, 1f)
            weightSum += weight
        }
        return if (weightSum == 0f) null else (weighted / weightSum).coerceIn(0f, 1f)
    }

    fun grade(ai: Float): Int {
        require(ai.isFinite() && ai in 0f..1f) { "AI must be finite and between 0 and 1" }
        return when {
            ai < 0.25f -> 0
            ai < 0.50f -> 1
            ai < 0.75f -> 2
            ai < 0.90f -> 3
            else -> 4
        }
    }

    fun gradeLabel(grade: Int): String =
        listOf("No risk", "Mild", "Moderate", "Severe", "Very severe")[validGrade(grade)]

    fun activityLabel(grade: Int): String = listOf(
        "Minimal OA activity",
        "Low OA activity",
        "Moderate OA activity",
        "High OA activity",
        "Very high OA activity",
    )[validGrade(grade)]

    fun riskLabel(grade: Int): String =
        listOf("Very low risk", "Low risk", "Medium risk", "High risk", "Very high risk")[validGrade(grade)]

    fun aiSeries(sessions: List<TestSession>): List<InsightPoint> = sessions
        .filter { it.results.isNotEmpty() }
        .mapNotNull { session ->
            aiFromResults(session.results)?.let { ai ->
                InsightPoint(session.results.maxOf(TestResult::timestamp), ai)
            }
        }
        .sortedBy(InsightPoint::time)

    fun factorDeltaPct7d(
        results: List<TestResult>,
        factor: InflammationFactor,
        now: Long,
    ): Float? {
        val recent = results.filter {
            it.factor == factor && it.timestamp > now - DAY_MILLIS * 7
        }
        val previous = results.filter {
            it.factor == factor &&
                it.timestamp <= now - DAY_MILLIS * 7 &&
                it.timestamp > now - DAY_MILLIS * 14
        }
        if (recent.isEmpty() || previous.isEmpty()) return null
        val currentMean = recent.map { it.concentration.toDouble() }.average()
        val previousMean = previous.map { it.concentration.toDouble() }.average()
        if (previousMean <= 0.0) return null
        return ((currentMean - previousMean) / previousMean * 100.0).toFloat()
    }

    fun aiWeekDeltaPct(series: List<InsightPoint>, now: Long): Float? {
        if (series.size < 2) return null
        val latest = series.last().value
        val baseline = series.lastOrNull { it.time <= now - DAY_MILLIS * 7 }?.value
            ?: series.first().value
        if (baseline <= 0f) return null
        return (latest - baseline) / baseline * 100f
    }

    fun keyEvents(sessions: List<TestSession>, series: List<InsightPoint>): List<KeyEventItem> {
        val events = mutableListOf<KeyEventItem>()
        val aiByTimestamp = series.associate { it.time to it.value }
        sessions.filter { it.results.isNotEmpty() }.forEach { session ->
            val timestamp = session.results.maxOf(TestResult::timestamp)
            events += KeyEventItem(
                time = timestamp,
                kind = EventKind.TEST,
                text = "Test completed - ${session.results.size} measurement(s)" +
                    (aiByTimestamp[timestamp]?.let { ", AI %.2f".format(it) } ?: ""),
            )
        }
        for (index in 1 until series.size) {
            val current = series[index]
            val previous = series[index - 1]
            val delta = current.value - previous.value
            if (abs(delta) >= 0.03f) {
                events += KeyEventItem(
                    time = current.time,
                    kind = if (delta > 0) EventKind.UP else EventKind.DOWN,
                    text = "AI index ${if (delta > 0) "rose" else "dropped"} " +
                        "from %.2f to %.2f".format(previous.value, current.value),
                )
            }
        }
        return events.sortedByDescending(KeyEventItem::time).take(10)
    }

    fun suggestions(grade: Int, weekDeltaPct: Float?): List<String> {
        validGrade(grade)
        return buildList {
            when {
                grade <= 1 -> {
                    add("Continue the current care and monitoring plan.")
                    add("Maintain regular, low-impact joint-friendly exercise.")
                }
                grade == 2 -> {
                    add("Discuss the result with your clinician at the next visit.")
                    add("Prefer low-impact activity and avoid joint overloading.")
                    add("Keep a regular monitoring cadence (2-3 times / week).")
                }
                else -> {
                    add("Seek clinical review promptly for treatment adjustment.")
                    add("Reduce joint load; pause high-impact activity.")
                    add("Follow the prescribed treatment plan and re-test after therapy.")
                }
            }
            when {
                weekDeltaPct != null && weekDeltaPct > 10f ->
                    add("Inflammatory markers trended up vs last week - re-check sooner.")
                weekDeltaPct != null && weekDeltaPct < -10f ->
                    add("Markers trended down vs last week - current plan appears effective.")
            }
        }
    }

    private fun validGrade(grade: Int): Int {
        require(grade in 0..4) { "Grade must be between 0 and 4" }
        return grade
    }
}
