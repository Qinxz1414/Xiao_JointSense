package cloud.univ.jointsense.domain.report

import cloud.univ.jointsense.domain.model.InflammationFactor

/** Platform-independent snapshot used by every report renderer. */
data class ReportModel(
    val generatedAtEpochMillis: Long,
    val oaIndex: Double?,
    val grade: Int?,
    val latestConcentrations: Map<InflammationFactor, Double?>,
    /** Fractional changes: 0.25 is formatted as 25%. */
    val weekChanges: Map<InflammationFactor, Double?>,
    /** Fractional OA-index change: 0.25 is formatted as 25%. */
    val oaWeekChange: Double?,
    val recommendations: List<ReportRecommendation> = emptyList(),
)

enum class ReportRecommendation {
    CONTINUE_MONITORING,
    LOW_IMPACT_EXERCISE,
    DISCUSS_WITH_CLINICIAN,
    AVOID_OVERLOAD,
    REGULAR_MONITORING,
    SEEK_CLINICAL_REVIEW,
    REDUCE_JOINT_LOAD,
    FOLLOW_TREATMENT_PLAN,
    RETEST_SOONER,
    CURRENT_PLAN_EFFECTIVE,
}
