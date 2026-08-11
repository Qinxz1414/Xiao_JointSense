package cloud.univ.jointsense.insights.report

import android.content.res.Resources
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.report.ReportModel
import cloud.univ.jointsense.domain.report.ReportRecommendation
import cloud.univ.jointsense.feature.insights.R
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ReportText {
    TITLE,
    PAGE_HEADER,
    GENERATED,
    OA_INDEX,
    GRADE,
    RISK,
    LATEST_VALUES,
    WEEK_CHANGES,
    OA_WEEK_CHANGE,
    RECOMMENDATIONS,
    NOT_MEASURED,
    NO_COMPARISON,
    DISCLAIMER_LABEL,
    DISCLAIMER,
    GRADE_0,
    GRADE_1,
    GRADE_2,
    GRADE_3,
    GRADE_4,
    RECOMMEND_CONTINUE_MONITORING,
    RECOMMEND_LOW_IMPACT_EXERCISE,
    RECOMMEND_DISCUSS_WITH_CLINICIAN,
    RECOMMEND_AVOID_OVERLOAD,
    RECOMMEND_REGULAR_MONITORING,
    RECOMMEND_SEEK_CLINICAL_REVIEW,
    RECOMMEND_REDUCE_JOINT_LOAD,
    RECOMMEND_FOLLOW_TREATMENT_PLAN,
    RECOMMEND_RETEST_SOONER,
    RECOMMEND_CURRENT_PLAN_EFFECTIVE,
}

data class FormattedReport(
    val title: String,
    /** Short metadata repeated below the title on every PDF page. */
    val pageHeader: String,
    /** Explicit line and paragraph boundaries are preserved by the PDF exporter. */
    val body: String,
) {
    val plainText: String
        get() = "$title\n$pageHeader\n\n$body"
}

class LocalizedReportFormatter(
    private val locale: Locale,
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val text: (ReportText) -> String,
) {
    fun formatExport(model: ReportModel): FormattedReport =
        format(model, includeDisclaimer = true)

    /** Summary intended for an on-screen result; it deliberately excludes the report disclaimer. */
    fun formatResultSummary(model: ReportModel): String =
        format(model, includeDisclaimer = false).plainText

    private fun format(model: ReportModel, includeDisclaimer: Boolean): FormattedReport {
        val generated = dateTimeFormat().format(Date(model.generatedAtEpochMillis))
        val grade = model.grade?.coerceIn(0, 4)
        val sections = mutableListOf<String>()

        sections += buildString {
            append(text(ReportText.OA_INDEX))
            append(": ")
            append(model.oaIndex?.let(::decimal) ?: text(ReportText.NOT_MEASURED))
            if (grade != null) {
                append(" (")
                append(text(ReportText.GRADE))
                append(' ')
                append(grade)
                append(", ")
                append(gradeLabel(grade))
                append(')')
                append('\n')
                append(text(ReportText.RISK))
                append(": ")
                append(gradeLabel(grade))
            }
        }

        sections += buildList {
            add(text(ReportText.LATEST_VALUES))
            InflammationFactor.entries.forEach { factor ->
                val value = model.latestConcentrations[factor]
                add("${factor.label()}: ${value?.let { "${decimal(it)} pg/mL" } ?: text(ReportText.NOT_MEASURED)}")
            }
        }.joinToString("\n")

        sections += buildList {
            add(text(ReportText.WEEK_CHANGES))
            InflammationFactor.entries.forEach { factor ->
                add("${factor.label()}: ${model.weekChanges[factor]?.let(::percent) ?: text(ReportText.NO_COMPARISON)}")
            }
            add("${text(ReportText.OA_WEEK_CHANGE)}: ${model.oaWeekChange?.let(::percent) ?: text(ReportText.NO_COMPARISON)}")
        }.joinToString("\n")

        if (model.recommendations.isNotEmpty()) {
            sections += buildList {
                add(text(ReportText.RECOMMENDATIONS))
                model.recommendations.forEach { add("• ${text(it.reportText())}") }
            }.joinToString("\n")
        }

        if (includeDisclaimer) {
            sections += "${text(ReportText.DISCLAIMER_LABEL)}\n${text(ReportText.DISCLAIMER)}"
        }

        return FormattedReport(
            title = text(ReportText.TITLE),
            pageHeader = "${text(ReportText.PAGE_HEADER)} • ${text(ReportText.GENERATED)}: $generated",
            body = sections.joinToString("\n\n"),
        )
    }

    private fun dateTimeFormat(): DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale).apply {
            timeZone = this@LocalizedReportFormatter.timeZone
        }

    private fun decimal(value: Double): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)

    private fun percent(value: Double): String {
        val formatted = NumberFormat.getPercentInstance(locale).apply {
            maximumFractionDigits = 0
        }.format(value)
        return if (value > 0.0) "+$formatted" else formatted
    }

    private fun gradeLabel(grade: Int): String = text(
        when (grade) {
            0 -> ReportText.GRADE_0
            1 -> ReportText.GRADE_1
            2 -> ReportText.GRADE_2
            3 -> ReportText.GRADE_3
            else -> ReportText.GRADE_4
        }
    )

    companion object {
        fun from(resources: Resources, locale: Locale): LocalizedReportFormatter =
            LocalizedReportFormatter(locale = locale) { key -> resources.getString(key.resourceId()) }
    }
}

private fun InflammationFactor.label(): String = when (this) {
    InflammationFactor.IL6 -> "IL-6"
    InflammationFactor.TNF_ALPHA -> "TNF-α"
    InflammationFactor.IL1_BETA -> "IL-1β"
}

private fun ReportRecommendation.reportText(): ReportText = when (this) {
    ReportRecommendation.CONTINUE_MONITORING -> ReportText.RECOMMEND_CONTINUE_MONITORING
    ReportRecommendation.LOW_IMPACT_EXERCISE -> ReportText.RECOMMEND_LOW_IMPACT_EXERCISE
    ReportRecommendation.DISCUSS_WITH_CLINICIAN -> ReportText.RECOMMEND_DISCUSS_WITH_CLINICIAN
    ReportRecommendation.AVOID_OVERLOAD -> ReportText.RECOMMEND_AVOID_OVERLOAD
    ReportRecommendation.REGULAR_MONITORING -> ReportText.RECOMMEND_REGULAR_MONITORING
    ReportRecommendation.SEEK_CLINICAL_REVIEW -> ReportText.RECOMMEND_SEEK_CLINICAL_REVIEW
    ReportRecommendation.REDUCE_JOINT_LOAD -> ReportText.RECOMMEND_REDUCE_JOINT_LOAD
    ReportRecommendation.FOLLOW_TREATMENT_PLAN -> ReportText.RECOMMEND_FOLLOW_TREATMENT_PLAN
    ReportRecommendation.RETEST_SOONER -> ReportText.RECOMMEND_RETEST_SOONER
    ReportRecommendation.CURRENT_PLAN_EFFECTIVE -> ReportText.RECOMMEND_CURRENT_PLAN_EFFECTIVE
}

private fun ReportText.resourceId(): Int = when (this) {
    ReportText.TITLE -> R.string.report_title
    ReportText.PAGE_HEADER -> R.string.report_page_header
    ReportText.GENERATED -> R.string.report_generated
    ReportText.OA_INDEX -> R.string.report_oa_index
    ReportText.GRADE -> R.string.report_grade
    ReportText.RISK -> R.string.report_risk
    ReportText.LATEST_VALUES -> R.string.report_latest_values
    ReportText.WEEK_CHANGES -> R.string.report_week_changes
    ReportText.OA_WEEK_CHANGE -> R.string.report_oa_week_change
    ReportText.RECOMMENDATIONS -> R.string.report_recommendations
    ReportText.NOT_MEASURED -> R.string.report_not_measured
    ReportText.NO_COMPARISON -> R.string.report_no_comparison
    ReportText.DISCLAIMER_LABEL -> R.string.report_disclaimer_label
    ReportText.DISCLAIMER -> R.string.report_disclaimer
    ReportText.GRADE_0 -> R.string.report_grade_0
    ReportText.GRADE_1 -> R.string.report_grade_1
    ReportText.GRADE_2 -> R.string.report_grade_2
    ReportText.GRADE_3 -> R.string.report_grade_3
    ReportText.GRADE_4 -> R.string.report_grade_4
    ReportText.RECOMMEND_CONTINUE_MONITORING -> R.string.report_recommend_continue_monitoring
    ReportText.RECOMMEND_LOW_IMPACT_EXERCISE -> R.string.report_recommend_low_impact_exercise
    ReportText.RECOMMEND_DISCUSS_WITH_CLINICIAN -> R.string.report_recommend_discuss_with_clinician
    ReportText.RECOMMEND_AVOID_OVERLOAD -> R.string.report_recommend_avoid_overload
    ReportText.RECOMMEND_REGULAR_MONITORING -> R.string.report_recommend_regular_monitoring
    ReportText.RECOMMEND_SEEK_CLINICAL_REVIEW -> R.string.report_recommend_seek_clinical_review
    ReportText.RECOMMEND_REDUCE_JOINT_LOAD -> R.string.report_recommend_reduce_joint_load
    ReportText.RECOMMEND_FOLLOW_TREATMENT_PLAN -> R.string.report_recommend_follow_treatment_plan
    ReportText.RECOMMEND_RETEST_SOONER -> R.string.report_recommend_retest_sooner
    ReportText.RECOMMEND_CURRENT_PLAN_EFFECTIVE -> R.string.report_recommend_current_plan_effective
}
