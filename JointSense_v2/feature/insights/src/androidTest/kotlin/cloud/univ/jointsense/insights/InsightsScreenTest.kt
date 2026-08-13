package cloud.univ.jointsense.insights

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        composeRule.onNodeWithTag(TRENDS_PERIOD_7_TAG)
            .assertIsSelected()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        composeRule.onNodeWithTag(FACTOR_TREND_CHART_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        composeRule.onNodeWithTag(OA_TREND_CHART_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
        composeRule.onNodeWithTag(TREND_SERIES_TNF_ALPHA_LEGEND_TAG)
            .assertContentDescriptionEquals("TNF-α: solid line, circle markers")
        composeRule.onNodeWithTag(TREND_SERIES_IL6_LEGEND_TAG)
            .assertContentDescriptionEquals("IL-6: dashed line, square markers")
        composeRule.onNodeWithTag(TREND_SERIES_IL1_BETA_LEGEND_TAG)
            .assertContentDescriptionEquals("IL-1β: dotted line, triangle markers")

        composeRule.onNodeWithTag(FACTOR_TREND_CHART_TAG).assert(
            contentDescriptionContainsAll(
                "TNF-α", "IL-6", "IL-1β", "from", "Latest", "rising",
                "Current OA inflammation index (AI) 0.3", "grade 1",
            ),
        )
        composeRule.onNodeWithTag(OA_TREND_CHART_TAG).assert(
            contentDescriptionContainsAll(
                "OA inflammation index (AI)", "from", "Latest value 0.3", "rising",
                "grade 1",
            ),
        )
    }

    @Test
    fun homeFactorSparklinesPublishConcreteSeriesSummariesForSinglePoints() {
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

        composeRule.onNodeWithTag(FACTOR_SPARKLINE_TNF_ALPHA_TAG).performScrollTo().assert(
            contentDescriptionContainsAll(
                "TNF-α", "from", "Latest 11", "insufficient",
                "Current OA inflammation index (AI) 0.4", "grade 1",
                "solid line", "circle markers",
            ),
        )
        composeRule.onNodeWithTag(FACTOR_SPARKLINE_IL6_TAG).performScrollTo().assert(
            contentDescriptionContainsAll(
                "IL-6", "Latest 10", "dashed line", "square markers",
            ),
        )
        composeRule.onNodeWithTag(FACTOR_SPARKLINE_IL1_BETA_TAG).performScrollTo().assert(
            contentDescriptionContainsAll(
                "IL-1β", "Latest 12", "dotted line", "triangle markers",
            ),
        )
        composeRule.onNodeWithTag(RECENT_TREND_TAG).performScrollTo().assert(
            contentDescriptionContainsAll(
                "OA inflammation index (AI)", "from", "Latest value 0.4", "rising", "grade 1",
            ),
        )
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

    @Test
    fun criticalActionsRemainReachableAtCompactWidthAndTwoHundredPercentText() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = LocalDensity.current.density,
                    fontScale = 2f,
                ),
            ) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        ReportScreen(
                            ReportUiState(
                                latestValues = InflammationFactor.entries.associateWith { 10f },
                                currentAi = 0.4f,
                                currentGrade = 1,
                                aiWeekDeltaPct = 2f,
                            ),
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(REPORT_EXPORT_PDF_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(REPORT_EXPORT_SHARE_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeNamingLayoutStacksAndReportEntryWorksAtCompactWidthAndTwoHundredPercentText() {
        var reportRequests = 0
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        HomeScreen(
                            state = completeHomeState(),
                            onTestNow = {},
                            onRestoreSamples = {},
                            onOpenReport = { reportRequests += 1 },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(HOME_OPEN_REPORT_TAG).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(1, reportRequests) }
        composeRule.onNodeWithTag(OA_GRADE_TAG).assertIsDisplayed()
        val value = composeRule.onNodeWithTag(OA_INDEX_VALUE_TAG).fetchSemanticsNode().boundsInRoot
        val grade = composeRule.onNodeWithTag(OA_GRADE_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("OA grade should stack below the value at large text", grade.top >= value.bottom)
    }

    @Test
    fun trendsPeriodControlsAndLegendWrapAtCompactWidthAndTwoHundredPercentText() {
        val now = 2_000_000_000_000L
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        TrendsScreen(completeTrendsState(now), nowMillis = { now })
                    }
                }
            }
        }
        composeRule.onNodeWithTag(TREND_SERIES_IL1_BETA_LEGEND_TAG).performScrollTo().assertIsDisplayed()
        val period7 = composeRule.onNodeWithTag(TRENDS_PERIOD_7_TAG).fetchSemanticsNode().boundsInRoot
        val period90 = composeRule.onNodeWithTag(TRENDS_PERIOD_90_TAG).fetchSemanticsNode().boundsInRoot
        val tnf = composeRule.onNodeWithTag(TREND_SERIES_TNF_ALPHA_LEGEND_TAG).fetchSemanticsNode().boundsInRoot
        val il6 = composeRule.onNodeWithTag(TREND_SERIES_IL6_LEGEND_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("period controls should wrap to another row", period90.top >= period7.bottom)
        assertTrue("legend entries should stack instead of overflow", il6.top >= tnf.bottom)
    }

    @Test
    fun reportFactorComparisonsRemainReachableAtCompactWidthAndTwoHundredPercentText() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        ReportScreen(
                            ReportUiState(
                                latestValues = InflammationFactor.entries.associateWith { 10f },
                                currentAi = 0.4f,
                                currentGrade = 1,
                                factorDeltaPct7d = InflammationFactor.entries.associateWith { 25f },
                            ),
                        )
                    }
                }
            }
        }
        listOf(
            REPORT_FACTOR_TNF_ALPHA_COMPARISON_TAG,
            REPORT_FACTOR_IL6_COMPARISON_TAG,
            REPORT_FACTOR_IL1_BETA_COMPARISON_TAG,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
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

    private fun contentDescriptionContainsAll(vararg fragments: String) =
        SemanticsMatcher("content description contains ${fragments.joinToString()}") { node ->
            val description = node.config
                .getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .joinToString(" ")
            fragments.all(description::contains)
        }
}
