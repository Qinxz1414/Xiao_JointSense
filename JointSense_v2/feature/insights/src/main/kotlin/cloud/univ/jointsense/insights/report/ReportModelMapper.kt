package cloud.univ.jointsense.insights.report

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.report.ReportModel
import cloud.univ.jointsense.domain.report.ReportRecommendation
import cloud.univ.jointsense.insights.ReportUiState

internal class ReportActionModelFactory(
    private val stateProvider: () -> ReportUiState,
    private val clock: () -> Long,
) {
    fun create(): ReportModel = stateProvider().toReportModel(clock())
}

internal fun ReportUiState.toReportModel(generatedAtEpochMillis: Long): ReportModel {
    val validatedGrade = currentGrade?.takeIf { it in 0..4 }
    return ReportModel(
        generatedAtEpochMillis = generatedAtEpochMillis,
        oaIndex = currentAi?.toDouble(),
        grade = validatedGrade,
        latestConcentrations = InflammationFactor.entries.associateWith { factor ->
            latestValues[factor]?.toDouble()
        },
        weekChanges = InflammationFactor.entries.associateWith { factor ->
            factorDeltaPct7d[factor]?.toDouble()?.div(100.0)
        },
        oaWeekChange = aiWeekDeltaPct?.toDouble()?.div(100.0),
        recommendations = recommendations(validatedGrade, aiWeekDeltaPct),
    )
}

private fun recommendations(grade: Int?, aiWeekDeltaPct: Float?): List<ReportRecommendation> =
    buildList {
        when {
            grade == null -> Unit
            grade <= 1 -> {
                add(ReportRecommendation.CONTINUE_MONITORING)
                add(ReportRecommendation.LOW_IMPACT_EXERCISE)
            }
            grade == 2 -> {
                add(ReportRecommendation.DISCUSS_WITH_CLINICIAN)
                add(ReportRecommendation.AVOID_OVERLOAD)
                add(ReportRecommendation.REGULAR_MONITORING)
            }
            else -> {
                add(ReportRecommendation.SEEK_CLINICAL_REVIEW)
                add(ReportRecommendation.REDUCE_JOINT_LOAD)
                add(ReportRecommendation.FOLLOW_TREATMENT_PLAN)
            }
        }
        when {
            aiWeekDeltaPct != null && aiWeekDeltaPct > 10f ->
                add(ReportRecommendation.RETEST_SOONER)
            aiWeekDeltaPct != null && aiWeekDeltaPct < -10f ->
                add(ReportRecommendation.CURRENT_PLAN_EFFECTIVE)
        }
    }
