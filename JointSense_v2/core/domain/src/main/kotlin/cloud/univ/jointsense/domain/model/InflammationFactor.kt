package cloud.univ.jointsense.domain.model

enum class InflammationFactor { IL6, TNF_ALPHA, IL1_BETA }

/** Approved clinical presentation order. Storage and enum declaration order are not UI contracts. */
val inflammationFactorPresentationOrder: List<InflammationFactor> = listOf(
    InflammationFactor.TNF_ALPHA,
    InflammationFactor.IL6,
    InflammationFactor.IL1_BETA,
)
