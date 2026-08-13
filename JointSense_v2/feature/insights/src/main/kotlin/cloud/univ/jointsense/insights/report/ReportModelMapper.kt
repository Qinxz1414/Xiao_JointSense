package cloud.univ.jointsense.insights.report

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder
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
    val validatedAi = currentAi?.takeIf { it.isFinite() && it in 0f..1f }
    val validatedGrade = currentGrade?.takeIf { validatedAi != null && it in 0..4 }
    val validatedWeekChange = aiWeekDeltaPct?.takeIf(Float::isFinite)
    return ReportModel(
        generatedAtEpochMillis = generatedAtEpochMillis,
        oaIndex = validatedAi?.toDouble(),
        grade = validatedGrade,
        latestConcentrations = inflammationFactorPresentationOrder.associateWith { factor ->
            latestValues[factor]?.takeIf { it.isFinite() && it >= 0f }?.toDouble()
        },
        weekChanges = inflammationFactorPresentationOrder.associateWith { factor ->
            factorDeltaPct7d[factor]?.takeIf(Float::isFinite)?.toDouble()?.div(100.0)
        },
        oaWeekChange = validatedWeekChange?.toDouble()?.div(100.0),
        recommendations = recommendations(validatedGrade, validatedWeekChange),
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
