package cloud.univ.jointsense.analysis

import cloud.univ.jointsense.domain.model.InflammationFactor

object FactoryCurves {
    private val curves = mapOf(
        InflammationFactor.TNF_ALPHA to StandardCurve(
            listOf(
                CurveKnot(0f, -8f), CurveKnot(20f, -4f), CurveKnot(50f, 0f),
                CurveKnot(100f, 20f), CurveKnot(200f, 26f),
            ),
        ),
        InflammationFactor.IL6 to StandardCurve(
            listOf(
                CurveKnot(0f, -7f), CurveKnot(50f, -4f), CurveKnot(100f, 0f),
                CurveKnot(200f, 0f), CurveKnot(500f, 11f),
            ),
        ),
        InflammationFactor.IL1_BETA to StandardCurve(
            listOf(
                CurveKnot(0f, -11f), CurveKnot(20f, 17f), CurveKnot(50f, 17f),
                CurveKnot(100f, 20f), CurveKnot(200f, 33f),
            ),
        ),
    )

    fun forFactor(factor: InflammationFactor): StandardCurve = curves.getValue(factor)
}
