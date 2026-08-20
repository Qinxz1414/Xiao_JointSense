package cloud.univ.jointsense.analysis

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.TestResult

object OaIndexCalculator {
    private val caps = mapOf(
        InflammationFactor.TNF_ALPHA to 500f,
        InflammationFactor.IL6 to 1000f,
        InflammationFactor.IL1_BETA to 500f,
    )
    private val weights = mapOf(
        InflammationFactor.TNF_ALPHA to 0.40f,
        InflammationFactor.IL6 to 0.35f,
        InflammationFactor.IL1_BETA to 0.25f,
    )

    fun calculate(latest: Map<InflammationFactor, TestResult>): Float? {
        if (latest.isEmpty()) return null

        var weightedTotal = 0f
        var presentWeight = 0f
        latest.forEach { (factor, result) ->
            require(result.concentration.isFinite()) { "Concentration must be finite." }
            val weight = weights[factor] ?: return@forEach
            val cap = caps.getValue(factor)
            weightedTotal += weight * (result.concentration / cap).coerceIn(0f, 1f)
            presentWeight += weight
        }
        return if (presentWeight == 0f) null else (weightedTotal / presentWeight).coerceIn(0f, 1f)
    }

    fun grade(ai: Float): Int {
        require(ai.isFinite()) { "OA index must be finite." }
        return when {
        ai < 0.25f -> 0
        ai < 0.50f -> 1
        ai < 0.75f -> 2
        ai < 0.90f -> 3
        else -> 4
        }
    }
}
