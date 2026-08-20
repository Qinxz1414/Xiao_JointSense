package cloud.univ.jointsense.calibration

import cloud.univ.jointsense.domain.model.InflammationFactor

internal val FACTORY_LADDER: Map<InflammationFactor, List<Float>> = mapOf(
    InflammationFactor.TNF_ALPHA to listOf(0f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f),
    InflammationFactor.IL6 to listOf(0f, 5f, 10f, 20f, 50f, 100f, 200f, 500f, 1_000f),
    InflammationFactor.IL1_BETA to listOf(0f, 2f, 5f, 10f, 20f, 50f, 100f, 200f, 500f),
)

internal val InflammationFactor.shortName: String
    get() = when (this) {
        InflammationFactor.IL6 -> "IL-6"
        InflammationFactor.TNF_ALPHA -> "TNF-α"
        InflammationFactor.IL1_BETA -> "IL-1β"
    }
