package cloud.univ.jointsense.insights.report

import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.report.ReportRecommendation
import cloud.univ.jointsense.insights.ReportUiState
import org.junit.Assert.assertEquals
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
}
