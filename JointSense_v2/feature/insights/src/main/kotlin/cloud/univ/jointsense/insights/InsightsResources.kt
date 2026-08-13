package cloud.univ.jointsense.insights

import androidx.annotation.StringRes
import cloud.univ.jointsense.feature.insights.R

@StringRes
internal fun gradeResource(grade: Int): Int = when (grade) {
    0 -> R.string.report_grade_0
    1 -> R.string.report_grade_1
    2 -> R.string.report_grade_2
    3 -> R.string.report_grade_3
    4 -> R.string.report_grade_4
    else -> error("Grade must be between 0 and 4")
}

@StringRes
internal fun activityResource(grade: Int): Int = when (grade) {
    0 -> R.string.insights_activity_0
    1 -> R.string.insights_activity_1
    2 -> R.string.insights_activity_2
    3 -> R.string.insights_activity_3
    4 -> R.string.insights_activity_4
    else -> error("Grade must be between 0 and 4")
}

@StringRes
internal fun riskResource(grade: Int): Int = when (grade) {
    0 -> R.string.insights_risk_0
    1 -> R.string.insights_risk_1
    2 -> R.string.insights_risk_2
    3 -> R.string.insights_risk_3
    4 -> R.string.insights_risk_4
    else -> error("Grade must be between 0 and 4")
}

@StringRes
internal fun suggestionResource(suggestion: InsightSuggestion): Int = when (suggestion) {
    InsightSuggestion.CONTINUE_MONITORING -> R.string.report_recommend_continue_monitoring
    InsightSuggestion.LOW_IMPACT_EXERCISE -> R.string.report_recommend_low_impact_exercise
    InsightSuggestion.DISCUSS_WITH_CLINICIAN -> R.string.report_recommend_discuss_with_clinician
    InsightSuggestion.AVOID_OVERLOAD -> R.string.report_recommend_avoid_overload
    InsightSuggestion.REGULAR_MONITORING -> R.string.report_recommend_regular_monitoring
    InsightSuggestion.SEEK_CLINICAL_REVIEW -> R.string.report_recommend_seek_clinical_review
    InsightSuggestion.REDUCE_JOINT_LOAD -> R.string.report_recommend_reduce_joint_load
    InsightSuggestion.FOLLOW_TREATMENT_PLAN -> R.string.report_recommend_follow_treatment_plan
    InsightSuggestion.RETEST_SOONER -> R.string.report_recommend_retest_sooner
    InsightSuggestion.CURRENT_PLAN_EFFECTIVE -> R.string.report_recommend_current_plan_effective
}
