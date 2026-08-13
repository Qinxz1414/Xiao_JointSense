package cloud.univ.jointsense.insights.report

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.report.ReportModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedReportFormatterTest {
    private val instant = 1_767_268_800_000L
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun englishExportUsesLocaleAwareNumbersDatePercentUnitAndExactDisclaimer() {
        val locale = Locale.US
        val formatter = LocalizedReportFormatter(
            locale = locale,
            timeZone = utc,
            text = englishText,
        )

        val report = formatter.formatExport(sampleModel())

        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
            .apply { timeZone = utc }
            .format(Date(instant))
        val decimal = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val percent = NumberFormat.getPercentInstance(locale).apply {
            maximumFractionDigits = 0
        }
        assertTrue(report.plainText.contains(date))
        assertTrue(report.plainText.contains(decimal.format(0.375)))
        assertTrue(report.plainText.contains("${decimal.format(12.5)} pg/mL"))
        assertTrue(report.plainText.contains(percent.format(0.25)))
        assertTrue(report.plainText.contains(ENGLISH_DISCLAIMER))
        assertFalse(formatter.formatResultSummary(sampleModel()).contains(ENGLISH_DISCLAIMER))
    }

    @Test
    fun simplifiedChineseExportUsesLocaleAwareNumbersDatePercentUnitAndExactDisclaimer() {
        val locale = Locale.SIMPLIFIED_CHINESE
        val formatter = LocalizedReportFormatter(
            locale = locale,
            timeZone = utc,
            text = chineseText,
        )

        val report = formatter.formatExport(sampleModel())

        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, locale)
            .apply { timeZone = utc }
            .format(Date(instant))
        val decimal = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val percent = NumberFormat.getPercentInstance(locale).apply {
            maximumFractionDigits = 0
        }
        assertTrue(report.plainText.contains(date))
        assertTrue(report.plainText.contains(decimal.format(0.375)))
        assertTrue(report.plainText.contains("${decimal.format(12.5)} pg/mL"))
        assertTrue(report.plainText.contains(percent.format(0.25)))
        assertTrue(report.plainText.contains(CHINESE_DISCLAIMER))
        assertFalse(formatter.formatResultSummary(sampleModel()).contains(CHINESE_DISCLAIMER))
    }

    @Test
    fun corruptGradesAreFormattedAsNoGradeInsteadOfValidEndpoints() {
        val formatter = LocalizedReportFormatter(
            locale = Locale.US,
            timeZone = utc,
            text = englishText,
        )
        val noGrade = formatter.formatExport(sampleModel().copy(grade = null))

        listOf(-1, 5).forEach { corruptGrade ->
            assertEquals(noGrade, formatter.formatExport(sampleModel().copy(grade = corruptGrade)))
        }
    }

    private fun sampleModel() = ReportModel(
        generatedAtEpochMillis = instant,
        oaIndex = 0.375,
        grade = 2,
        latestConcentrations = mapOf(
            InflammationFactor.IL6 to 12.5,
            InflammationFactor.TNF_ALPHA to null,
            InflammationFactor.IL1_BETA to 3.25,
        ),
        weekChanges = mapOf(
            InflammationFactor.IL6 to 0.25,
            InflammationFactor.TNF_ALPHA to null,
            InflammationFactor.IL1_BETA to -0.1,
        ),
        oaWeekChange = 0.125,
    )

    private val englishText: (ReportText) -> String = { key ->
        when (key) {
            ReportText.TITLE -> "JointSense Research Trend Report"
            ReportText.PAGE_HEADER -> "Longitudinal inflammatory biomarker summary"
            ReportText.GENERATED -> "Generated"
            ReportText.OA_INDEX -> "OA inflammation index (AI)"
            ReportText.GRADE -> "grade"
            ReportText.RISK -> "14-day progression risk"
            ReportText.LATEST_VALUES -> "Latest quantitative values"
            ReportText.WEEK_CHANGES -> "Change vs previous week"
            ReportText.OA_WEEK_CHANGE -> "OA inflammation index (AI) change vs previous week"
            ReportText.RECOMMENDATIONS -> "Research trend observations"
            ReportText.NOT_MEASURED -> "not measured"
            ReportText.NO_COMPARISON -> "no comparison"
            ReportText.DISCLAIMER_LABEL -> "Research-use statement"
            ReportText.DISCLAIMER -> ENGLISH_DISCLAIMER
            ReportText.GRADE_0 -> "No risk"
            ReportText.GRADE_1 -> "Mild"
            ReportText.GRADE_2 -> "Moderate"
            ReportText.GRADE_3 -> "Severe"
            ReportText.GRADE_4 -> "Very severe"
            ReportText.RECOMMEND_CONTINUE_MONITORING -> "Continue monitoring."
            ReportText.RECOMMEND_LOW_IMPACT_EXERCISE -> "Use low-impact exercise."
            ReportText.RECOMMEND_DISCUSS_WITH_CLINICIAN -> "Discuss with a clinician."
            ReportText.RECOMMEND_AVOID_OVERLOAD -> "Avoid overload."
            ReportText.RECOMMEND_REGULAR_MONITORING -> "Monitor regularly."
            ReportText.RECOMMEND_SEEK_CLINICAL_REVIEW -> "Seek clinical review."
            ReportText.RECOMMEND_REDUCE_JOINT_LOAD -> "Reduce joint load."
            ReportText.RECOMMEND_FOLLOW_TREATMENT_PLAN -> "Follow the treatment plan."
            ReportText.RECOMMEND_RETEST_SOONER -> "Retest sooner."
            ReportText.RECOMMEND_CURRENT_PLAN_EFFECTIVE -> "Continue observation."
            ReportText.PAGE_HEADER_FORMAT -> "%1\$s • %2\$s: %3\$s"
            ReportText.INDEX_GRADE_FORMAT -> "%1\$s: %2\$s (%3\$s %4\$d, %5\$s)"
            ReportText.LABELED_VALUE_FORMAT -> "%1\$s: %2\$s"
            ReportText.CONCENTRATION_FORMAT -> "%1\$s pg/mL"
            ReportText.BULLET_FORMAT -> "• %1\$s"
        }
    }

    private val chineseText: (ReportText) -> String = { key ->
        when (key) {
            ReportText.TITLE -> "JointSense 科研趋势报告"
            ReportText.PAGE_HEADER -> "炎症生物标志物纵向摘要"
            ReportText.GENERATED -> "生成时间"
            ReportText.OA_INDEX -> "OA 炎症综合指数（AI）"
            ReportText.GRADE -> "级别"
            ReportText.RISK -> "14天进展风险"
            ReportText.LATEST_VALUES -> "最新定量结果"
            ReportText.WEEK_CHANGES -> "较前一周变化"
            ReportText.OA_WEEK_CHANGE -> "OA 炎症综合指数（AI）较前一周变化"
            ReportText.RECOMMENDATIONS -> "科研趋势观察"
            ReportText.NOT_MEASURED -> "未测量"
            ReportText.NO_COMPARISON -> "无可比数据"
            ReportText.DISCLAIMER_LABEL -> "科研使用声明"
            ReportText.DISCLAIMER -> CHINESE_DISCLAIMER
            ReportText.GRADE_0 -> "无风险"
            ReportText.GRADE_1 -> "轻度"
            ReportText.GRADE_2 -> "中度"
            ReportText.GRADE_3 -> "重度"
            ReportText.GRADE_4 -> "极重度"
            ReportText.RECOMMEND_CONTINUE_MONITORING -> "继续监测。"
            ReportText.RECOMMEND_LOW_IMPACT_EXERCISE -> "低冲击运动。"
            ReportText.RECOMMEND_DISCUSS_WITH_CLINICIAN -> "与临床医生讨论。"
            ReportText.RECOMMEND_AVOID_OVERLOAD -> "避免过度负荷。"
            ReportText.RECOMMEND_REGULAR_MONITORING -> "规律监测。"
            ReportText.RECOMMEND_SEEK_CLINICAL_REVIEW -> "进行临床复核。"
            ReportText.RECOMMEND_REDUCE_JOINT_LOAD -> "降低关节负荷。"
            ReportText.RECOMMEND_FOLLOW_TREATMENT_PLAN -> "遵循治疗计划。"
            ReportText.RECOMMEND_RETEST_SOONER -> "提前复测。"
            ReportText.RECOMMEND_CURRENT_PLAN_EFFECTIVE -> "继续观察。"
            ReportText.PAGE_HEADER_FORMAT -> "%1\$s • %2\$s：%3\$s"
            ReportText.INDEX_GRADE_FORMAT -> "%1\$s：%2\$s（%3\$s %4\$d，%5\$s）"
            ReportText.LABELED_VALUE_FORMAT -> "%1\$s：%2\$s"
            ReportText.CONCENTRATION_FORMAT -> "%1\$s pg/mL"
            ReportText.BULLET_FORMAT -> "• %1\$s"
        }
    }

    private companion object {
        const val ENGLISH_DISCLAIMER = "Results in this report are estimates derived from smartphone-photo colorimetry for research and longitudinal trend observation only. They are not intended for clinical diagnosis, treatment decisions, or as a substitute for validated laboratory testing."
        const val CHINESE_DISCLAIMER = "本报告结果基于手机照片色度代理估算，仅供科研与纵向趋势观察，不作为临床诊断、治疗决策或替代经验证实验室检测的依据。"
    }
}
