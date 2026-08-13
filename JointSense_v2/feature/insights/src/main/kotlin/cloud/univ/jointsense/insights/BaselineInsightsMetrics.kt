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
        results.filter { it.concentration.isFinite() && it.concentration >= 0f }
            .groupBy(TestResult::factor)
            .mapValues { (_, values) -> values.maxBy(TestResult::timestamp).concentration }

    fun aiFromResults(results: List<TestResult>): Float? = aiFromValues(latestPerFactor(results))

    fun aiFromValues(values: Map<InflammationFactor, Float>): Float? {
        if (values.isEmpty()) return null
        var weighted = 0f
        var weightSum = 0f
        values.filterValues { it.isFinite() && it >= 0f }.forEach { (factor, concentration) ->
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

    fun requireValidGrade(grade: Int): Int = validGrade(grade)

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
        val boundary = now - DAY_MILLIS * 7
        val previousBoundary = now - DAY_MILLIS * 14
        val valid = results.filter {
            it.factor == factor &&
                it.timestamp <= now &&
                it.concentration.isFinite() &&
                it.concentration >= 0f
        }
        val recent = valid.filter {
            it.timestamp > boundary
        }
        val previous = valid.filter {
            it.timestamp <= boundary && it.timestamp > previousBoundary
        }
        if (recent.isEmpty() || previous.isEmpty()) return null
        val currentMean = recent.map { it.concentration.toDouble() }.average()
        val previousMean = previous.map { it.concentration.toDouble() }.average()
        if (!currentMean.isFinite() || !previousMean.isFinite() || previousMean <= 0.0) return null
        return ((currentMean - previousMean) / previousMean * 100.0)
            .takeIf(Double::isFinite)
            ?.toFloat()
            ?.takeIf(Float::isFinite)
    }

    fun aiWeekDeltaPct(series: List<InsightPoint>, now: Long): Float? {
        val boundary = now - DAY_MILLIS * 7
        val valid = series
            .asSequence()
            .filter { it.time <= now && it.value.isFinite() && it.value in 0f..1f }
            .sortedBy(InsightPoint::time)
            .toList()
        val baseline = valid.lastOrNull { it.time <= boundary }?.value
            ?.takeIf { it > 0f }
            ?: return null
        val latest = valid.lastOrNull { it.time > boundary }?.value ?: return null
        return ((latest - baseline) / baseline * 100f).takeIf(Float::isFinite)
    }

    fun keyEvents(sessions: List<TestSession>, series: List<InsightPoint>): List<KeyEventItem> {
        val events = mutableListOf<KeyEventItem>()
        val aiByTimestamp = series.associate { it.time to it.value }
        sessions.filter { it.results.isNotEmpty() }.forEach { session ->
            val timestamp = session.results.maxOf(TestResult::timestamp)
            events += KeyEventItem(
                time = timestamp,
                kind = EventKind.TEST,
                measurementCount = session.results.size,
                aiValue = aiByTimestamp[timestamp],
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
                    previousAi = previous.value,
                    currentAi = current.value,
                )
            }
        }
        return events.sortedByDescending(KeyEventItem::time).take(10)
    }

    fun suggestions(grade: Int, weekDeltaPct: Float?): List<InsightSuggestion> {
        validGrade(grade)
        return buildList {
            when {
                grade <= 1 -> {
                    add(InsightSuggestion.CONTINUE_MONITORING)
                    add(InsightSuggestion.LOW_IMPACT_EXERCISE)
                }
                grade == 2 -> {
                    add(InsightSuggestion.DISCUSS_WITH_CLINICIAN)
                    add(InsightSuggestion.AVOID_OVERLOAD)
                    add(InsightSuggestion.REGULAR_MONITORING)
                }
                else -> {
                    add(InsightSuggestion.SEEK_CLINICAL_REVIEW)
                    add(InsightSuggestion.REDUCE_JOINT_LOAD)
                    add(InsightSuggestion.FOLLOW_TREATMENT_PLAN)
                }
            }
            when {
                weekDeltaPct != null && weekDeltaPct > 10f ->
                    add(InsightSuggestion.RETEST_SOONER)
                weekDeltaPct != null && weekDeltaPct < -10f ->
                    add(InsightSuggestion.CURRENT_PLAN_EFFECTIVE)
            }
        }
    }

    private fun validGrade(grade: Int): Int {
        require(grade in 0..4) { "Grade must be between 0 and 4" }
        return grade
    }
}
