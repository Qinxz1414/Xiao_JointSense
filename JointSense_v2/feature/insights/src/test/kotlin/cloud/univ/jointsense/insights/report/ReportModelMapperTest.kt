package cloud.univ.jointsense.insights.report

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.report.ReportRecommendation
import cloud.univ.jointsense.insights.ReportUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportModelMapperTest {
    @Test
    fun actionFactoryReadsGenerationTimeForEveryUserAction() {
        var currentTime = 100L
        val factory = ReportActionModelFactory(
            stateProvider = { ReportUiState() },
            clock = { currentTime++ },
        )

        val first = factory.create()
        val second = factory.create()

        assertEquals(100L, first.generatedAtEpochMillis)
        assertEquals(101L, second.generatedAtEpochMillis)
    }

    @Test
    fun mapsUiPercentagesToFractionsAndBuildsStructuredRecommendations() {
        val state = ReportUiState(
            latestValues = mapOf(InflammationFactor.IL6 to 12.5f),
            currentAi = 0.55f,
            currentGrade = 2,
            factorDeltaPct7d = mapOf(InflammationFactor.IL6 to 25f),
            aiWeekDeltaPct = 12.5f,
        )

        val model = state.toReportModel(generatedAtEpochMillis = 99L)

        assertEquals(99L, model.generatedAtEpochMillis)
        assertEquals(0.55, model.oaIndex!!, 0.0001)
        assertEquals(12.5, model.latestConcentrations[InflammationFactor.IL6]!!, 0.0001)
        assertEquals(0.25, model.weekChanges[InflammationFactor.IL6]!!, 0.0001)
        assertEquals(0.125, model.oaWeekChange!!, 0.0001)
        assertEquals(
            listOf(
                ReportRecommendation.DISCUSS_WITH_CLINICIAN,
                ReportRecommendation.AVOID_OVERLOAD,
                ReportRecommendation.REGULAR_MONITORING,
                ReportRecommendation.RETEST_SOONER,
            ),
            model.recommendations,
        )
    }

    @Test
    fun corruptGradesMapToUnknownWithoutGradeAdviceThroughExport() {
        val formatter = tokenFormatter()

        listOf(-1, 5).forEach { corruptGrade ->
            val model = ReportUiState(
                currentAi = 0.5f,
                currentGrade = corruptGrade,
            ).toReportModel(generatedAtEpochMillis = 99L)
            val export = formatter.formatExport(model).plainText

            assertNull(model.grade)
            assertTrue(model.recommendations.isEmpty())
            GRADE_LABELS.forEach { label -> assertFalse(export.contains(label.name)) }
            GRADE_ADVICE.forEach { advice -> assertFalse(export.contains(advice.name)) }
            assertTrue(export.contains(ReportText.DISCLAIMER.name))
        }
    }

    @Test
    fun exportBoundaryRemovesNonFiniteAndOutOfRangeValues() {
        val model = ReportUiState(
            latestValues = mapOf(
                InflammationFactor.TNF_ALPHA to Float.NaN,
                InflammationFactor.IL6 to 12f,
                InflammationFactor.IL1_BETA to Float.POSITIVE_INFINITY,
            ),
            currentAi = 1.1f,
            currentGrade = 4,
            factorDeltaPct7d = mapOf(
                InflammationFactor.TNF_ALPHA to Float.NEGATIVE_INFINITY,
            ),
            aiWeekDeltaPct = Float.POSITIVE_INFINITY,
        ).toReportModel(generatedAtEpochMillis = 99L)

        assertNull(model.oaIndex)
        assertNull(model.grade)
        assertNull(model.latestConcentrations[InflammationFactor.TNF_ALPHA])
        assertEquals(12.0, model.latestConcentrations[InflammationFactor.IL6]!!, 0.0001)
        assertNull(model.latestConcentrations[InflammationFactor.IL1_BETA])
        assertNull(model.weekChanges[InflammationFactor.TNF_ALPHA])
        assertNull(model.oaWeekChange)
        assertTrue(model.recommendations.isEmpty())
    }

    @Test
    fun everyValidGradePreservesItsLabelAdviceAndExactDisclaimerScope() {
        val formatter = tokenFormatter()
        val expectedRecommendations = mapOf(
            0 to listOf(
                ReportRecommendation.CONTINUE_MONITORING,
                ReportRecommendation.LOW_IMPACT_EXERCISE,
            ),
            1 to listOf(
                ReportRecommendation.CONTINUE_MONITORING,
                ReportRecommendation.LOW_IMPACT_EXERCISE,
            ),
            2 to listOf(
                ReportRecommendation.DISCUSS_WITH_CLINICIAN,
                ReportRecommendation.AVOID_OVERLOAD,
                ReportRecommendation.REGULAR_MONITORING,
            ),
            3 to listOf(
                ReportRecommendation.SEEK_CLINICAL_REVIEW,
                ReportRecommendation.REDUCE_JOINT_LOAD,
                ReportRecommendation.FOLLOW_TREATMENT_PLAN,
            ),
            4 to listOf(
                ReportRecommendation.SEEK_CLINICAL_REVIEW,
                ReportRecommendation.REDUCE_JOINT_LOAD,
                ReportRecommendation.FOLLOW_TREATMENT_PLAN,
            ),
        )

        expectedRecommendations.forEach { (grade, recommendations) ->
            val model = ReportUiState(
                currentAi = grade / 4f,
                currentGrade = grade,
            ).toReportModel(generatedAtEpochMillis = 99L)
            val export = formatter.formatExport(model).plainText
            val resultSummary = formatter.formatResultSummary(model)

            assertEquals(grade, model.grade)
            assertEquals(recommendations, model.recommendations)
            assertTrue(export.contains(GRADE_LABELS[grade].name))
            recommendations.forEach { recommendation -> assertTrue(export.contains(recommendation.reportToken().name)) }
            assertEquals(1, export.lineSequence().count { it == ReportText.DISCLAIMER.name })
            assertEquals(0, resultSummary.lineSequence().count { it == ReportText.DISCLAIMER.name })
        }
    }

    private fun tokenFormatter() = LocalizedReportFormatter(
        locale = java.util.Locale.US,
        timeZone = java.util.TimeZone.getTimeZone("UTC"),
        text = { key ->
            when (key) {
                ReportText.PAGE_HEADER_FORMAT -> "%1\$s • %2\$s: %3\$s"
                ReportText.INDEX_GRADE_FORMAT -> "%1\$s: %2\$s (%3\$s %4\$d, %5\$s)"
                ReportText.LABELED_VALUE_FORMAT -> "%1\$s: %2\$s"
                ReportText.CONCENTRATION_FORMAT -> "%1\$s pg/mL"
                ReportText.BULLET_FORMAT -> "• %1\$s"
                else -> key.name
            }
        },
    )

    private fun ReportRecommendation.reportToken(): ReportText = when (this) {
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

    private companion object {
        val GRADE_LABELS = listOf(
            ReportText.GRADE_0,
            ReportText.GRADE_1,
            ReportText.GRADE_2,
            ReportText.GRADE_3,
            ReportText.GRADE_4,
        )
        val GRADE_ADVICE = listOf(
            ReportText.RECOMMEND_CONTINUE_MONITORING,
            ReportText.RECOMMEND_LOW_IMPACT_EXERCISE,
            ReportText.RECOMMEND_DISCUSS_WITH_CLINICIAN,
            ReportText.RECOMMEND_AVOID_OVERLOAD,
            ReportText.RECOMMEND_REGULAR_MONITORING,
            ReportText.RECOMMEND_SEEK_CLINICAL_REVIEW,
            ReportText.RECOMMEND_REDUCE_JOINT_LOAD,
            ReportText.RECOMMEND_FOLLOW_TREATMENT_PLAN,
        )
    }
}
