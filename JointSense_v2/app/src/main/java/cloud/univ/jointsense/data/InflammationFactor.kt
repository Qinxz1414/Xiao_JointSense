package cloud.univ.jointsense.data

enum class InflammationFactor(
    val displayName: String,
    val shortName: String,
    val unit: String
) {
    IL6("Interleukin-6", "IL-6", "pg/mL"),
    TNF_ALPHA("Tumor Necrosis Factor-α", "TNF-α", "pg/mL"),
    IL1_BETA("Interleukin-1β", "IL-1β", "pg/mL")
}
