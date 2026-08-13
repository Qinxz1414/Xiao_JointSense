package cloud.univ.jointsense.insights

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InsightsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homePublishesApprovedClinicalHierarchyTags() {
        composeRule.setContent {
            JointSenseTheme {
                HomeScreen(
                    state = completeHomeState(),
                    onTestNow = {},
                    onRestoreSamples = {},
                    onOpenReport = {},
                )
            }
        }

        listOf(
            "oa_index_value",
            "oa_grade",
            "factor_value_tnf_alpha",
            "factor_value_il6",
            "factor_value_il1_beta",
            "recent_trend",
            "start_measurement",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun emptyHomeOffersStartAndRestoreWithoutMetricNodes() {
        var restoreRequests = 0
        composeRule.setContent {
            JointSenseTheme {
                HomeScreen(
                    state = HomeUiState(),
                    onTestNow = {},
                    onRestoreSamples = { restoreRequests += 1 },
                    onOpenReport = {},
                )
            }
        }

        composeRule.onNodeWithTag("start_measurement").assertIsDisplayed()
        composeRule.onNodeWithTag("restore_samples").assertIsDisplayed()
        composeRule.onNodeWithTag("restore_samples").performClick()
        composeRule.runOnIdle { assertEquals(1, restoreRequests) }
        composeRule.onAllNodesWithText("0.00").assertCountEquals(0)
        composeRule.onAllNodesWithText("4").assertCountEquals(0)
    }

    @Test
    fun trendsExposeChartsAndDirectAxisAndSeriesLabels() {
        val now = 2_000_000_000_000L
        composeRule.setContent {
            JointSenseTheme { TrendsScreen(completeTrendsState(now), nowMillis = { now }) }
        }

        listOf(
            "factor_trend_chart",
            "oa_trend_chart",
            "trend_date_axis_label",
            "trend_unit_axis_label",
            "trend_series_labels",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun reportShowsFactorsTrendSuggestionsAndExportsWithoutDisclaimer() {
        composeRule.setContent {
            JointSenseTheme {
                ReportScreen(
                    ReportUiState(
                        latestValues = InflammationFactor.entries.associateWith { 10f + it.ordinal },
                        currentAi = 0.4f,
                        currentGrade = 1,
                        aiWeekDeltaPct = 12f,
                    ),
                )
            }
        }

        listOf(
            "report_factor_summary",
            "report_trend_interpretation",
            "report_suggestions",
            "report_export_pdf",
            "report_export_share",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
        composeRule.onAllNodesWithText(
            "Results in this report are estimates derived from smartphone-photo colorimetry for research and longitudinal trend observation only. They are not intended for clinical diagnosis, treatment decisions, or as a substitute for validated laboratory testing.",
        ).assertCountEquals(0)
        composeRule.onAllNodesWithText(
            "本报告结果基于手机照片色度代理估算，仅供科研与纵向趋势观察，不作为临床诊断、治疗决策或替代经验证实验室检测的依据。",
        ).assertCountEquals(0)
    }

    private fun completeHomeState(): HomeUiState {
        val results = InflammationFactor.entries.mapIndexed { index, factor -> result(index, factor) }
        return HomeUiState(
            allResults = results,
            latestValues = results.associate { it.factor to it.concentration },
            factorSeries = results.groupBy(TestResult::factor).mapValues { (_, values) ->
                values.map { InsightPoint(it.timestamp, it.concentration) }
            },
            currentAi = 0.4f,
            currentGrade = 1,
            aiSeries = (1L..8L).map { InsightPoint(it, it / 20f) },
        )
    }

    private fun completeTrendsState(now: Long): TrendsUiState = TrendsUiState(
        factorSeries = InflammationFactor.entries.associateWith { factor ->
            listOf(
                InsightPoint(now - DAY_MILLIS * 2, 10f + factor.ordinal),
                InsightPoint(now - DAY_MILLIS, 11f + factor.ordinal),
            )
        },
        aiSeries = listOf(
            InsightPoint(now - DAY_MILLIS * 2, 0.2f),
            InsightPoint(now - DAY_MILLIS, 0.3f),
        ),
    )

    private fun result(index: Int, factor: InflammationFactor) = TestResult(
        id = "result-$index",
        sessionId = "session",
        draftId = null,
        factor = factor,
        concentration = 10f + index,
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(1f, 2f, 3f, 4f, 5f, 6f),
        timestamp = 100L + index,
    )
}
