package cloud.univ.jointsense.model

import cloud.univ.jointsense.data.InflammationFactor

/**
 * ELISA standard-curve interpolation ("插补").
 *
 * Converts a well's measured color signal — tealness = (B − R) — into a
 * concentration in pg/mL by interpolating along the calibrated standard
 * curve, instead of snapping to coarse intensity levels.
 *
 * The factory knots were derived from all sample data in the Rule/ assets
 * (测试用例.pptx + 样本.pptx + the 3 clipboard plates), analysed with the
 * Rule/SKILL.md ELISA palette. They are only ordinal photo-derived
 * estimates, so the curve is now **user-overridable**: [CalibrationManager]
 * calls [setKnots] with a calibration captured through the guided
 * calibration flow (a photo of the standard ladder plate). While an override
 * is active, [concentrationFor] uses it; otherwise the factory knots below
 * apply. Replace the factory knots with a 4-parameter logistic fit on real
 * OD450 readings for clinical-grade accuracy.
 */
object StandardCurve {

    /**
     * Factory calibration knots: Pair(concentrationPgMl, signalTealness).
     * signal = median(B − R) at the 90th percentile of liquid pixels inside
     * the well (teal dye = positive). Sorted ascending by concentration.
     */
    private val FACTORY_KNOTS: Map<InflammationFactor, List<Pair<Float, Float>>> = mapOf(
        InflammationFactor.TNF_ALPHA to listOf(
            0f to -8f, 20f to -4f, 50f to 0f, 100f to 20f, 200f to 26f
        ),
        InflammationFactor.IL6 to listOf(
            0f to -7f, 50f to -4f, 100f to 0f, 200f to 0f, 500f to 11f
        ),
        InflammationFactor.IL1_BETA to listOf(
            0f to -11f, 20f to 17f, 50f to 17f, 100f to 20f, 200f to 33f
        )
    )

    /** User override set by [setKnots]; null → use factory knots. */
    @Volatile
    private var override: Map<InflammationFactor, List<Pair<Float, Float>>>? = null

    /** Top of the working range per factor (from 样本.pptx ladder). */
    val maxConcentration: Map<InflammationFactor, Float> = mapOf(
        InflammationFactor.TNF_ALPHA to 500f,
        InflammationFactor.IL6 to 1000f,
        InflammationFactor.IL1_BETA to 500f
    )

    /** Apply a user calibration: each factor's knots replace the factory set. */
    fun setKnots(calibration: Calibration) {
        override = calibration.factors.mapValues { (_, knots) ->
            knots.map { it.conc to it.signal }.sortedBy { it.first }
        }
    }

    /** Revert to the factory knots. */
    fun resetKnots() {
        override = null
    }

    private fun knotsFor(factor: InflammationFactor): List<Pair<Float, Float>> =
        override?.get(factor) ?: (FACTORY_KNOTS[factor] ?: emptyList())

    /**
     * Interpolate concentration (pg/mL) from a well's tealness signal.
     * Below the first knot's signal → 0; above the last → capped at max.
     */
    fun concentrationFor(signal: Float, factor: InflammationFactor): Float {
        val knots = knotsFor(factor)
        if (knots.isEmpty()) return 0f
        val signals = knots.map { it.second }
        val concs = knots.map { it.first }
        if (signal <= signals.first()) return 0f
        if (signal >= signals.last()) return concs.last()
        for (i in 1 until knots.size) {
            if (signal <= signals[i]) {
                val (s0, c0) = knots[i - 1]
                val (s1, c1) = knots[i]
                if (s1 == s0) return c1
                return c0 + (c1 - c0) * (signal - s0) / (s1 - s0)
            }
        }
        return concs.last()
    }

    /**
     * Inverse lookup: tealness signal implied by a concentration.
     * Used only to synthesise representative well RGB for embedded samples.
     */
    fun signalForConcentration(conc: Float, factor: InflammationFactor): Float {
        val knots = knotsFor(factor)
        if (knots.isEmpty()) return 0f
        val signals = knots.map { it.second }
        val concs = knots.map { it.first }
        if (conc <= 0f) return signals.first()
        if (conc >= concs.last()) return signals.last()
        for (i in 1 until knots.size) {
            if (conc <= concs[i]) {
                val (c0, s0) = knots[i - 1]
                val (c1, s1) = knots[i]
                if (c1 == c0) return s1
                return s0 + (s1 - s0) * (conc - c0) / (c1 - c0)
            }
        }
        return signals.last()
    }

    /** Convenience: tealness signal from the 6-feature vector [rMean … bMean …]. */
    fun signalFromFeatures(features: FloatArray): Float {
        require(features.size == 6) { "Expected 6 features [rMean,gMean,bMean,rStd,gStd,bStd]" }
        return features[2] - features[0] // bMean - rMean
    }
}
