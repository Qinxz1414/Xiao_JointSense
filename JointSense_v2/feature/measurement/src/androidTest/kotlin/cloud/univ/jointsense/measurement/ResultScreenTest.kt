package cloud.univ.jointsense.measurement

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
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
import cloud.univ.jointsense.feature.measurement.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResultScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun resultExposesMeasurementDetailsAndIndependentActionsWithoutDisclaimer() {
        val result = TestResult(
            id = "result",
            sessionId = "session",
            draftId = "draft",
            factor = InflammationFactor.IL6,
            concentration = 42f,
            rangeStatus = RangeStatus.UNKNOWN,
            features = RgbFeatures(10f, 20f, 30f, 1f, 2f, 3f),
            timestamp = 2L,
        )
        val session = TestSession(
            id = "session",
            name = "Session",
            createdAt = 1L,
            source = DataSource.USER,
            results = listOf(result),
        )
        composeRule.setContent {
            JointSenseTheme {
                ResultScreen(
                    resolution = ResultResolution.Found(session, result),
                    onContinueMeasurement = {},
                    onReturnToOrigin = {},
                    onGoHome = {},
                )
            }
        }

        listOf(
            "result_concentration",
            "result_range_status",
            "result_features_summary",
            "result_home_action",
            "continue_measurement",
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
    fun missingRequestedResultShowsNotFoundWithBackAndHomeActions() {
        var backs = 0
        var homes = 0
        composeRule.setContent {
            JointSenseTheme {
                ResultScreen(
                    resolution = ResultResolution.NotFound,
                    onContinueMeasurement = {},
                    onReturnToOrigin = { backs += 1 },
                    onGoHome = { homes += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(RESULT_NOT_FOUND_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RESULT_NOT_FOUND_BACK_TAG).performClick()
        composeRule.onNodeWithTag(RESULT_NOT_FOUND_HOME_TAG).performClick()
        composeRule.runOnIdle {
            assertEquals(1, backs)
            assertEquals(1, homes)
        }
        composeRule.onAllNodesWithTag(RESULT_RANGE_STATUS_TAG).assertCountEquals(0)
    }

    @Test
    fun unresolvedRepositorySnapshotShowsLoadingWithoutContinueOrNotFound() {
        var backs = 0
        var continues = 0
        var homes = 0
        val backDescription = composeRule.activity.getString(R.string.measurement_action_back)
        composeRule.setContent {
            JointSenseTheme {
                ResultScreen(
                    resolution = ResultResolution.Loading,
                    onContinueMeasurement = { continues += 1 },
                    onReturnToOrigin = { backs += 1 },
                    onGoHome = { homes += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(RESULT_LOADING_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithTag(RESULT_NOT_FOUND_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(CONTINUE_MEASUREMENT_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(RESULT_HOME_ACTION_TAG).assertCountEquals(0)
        composeRule.onNodeWithContentDescription(backDescription).performClick()
        composeRule.runOnIdle {
            assertEquals(1, backs)
            assertEquals(0, continues)
            assertEquals(0, homes)
        }
    }

    @Test
    fun overflowingDerivedTealnessCannotReachEitherFeatureSummary() {
        val result = TestResult(
            id = "overflow",
            sessionId = "session",
            draftId = null,
            factor = InflammationFactor.TNF_ALPHA,
            concentration = 42f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = RgbFeatures(
                rMean = -Float.MAX_VALUE,
                gMean = 0f,
                bMean = Float.MAX_VALUE,
                rStd = Float.MAX_VALUE,
                gStd = Float.MAX_VALUE,
                bStd = Float.MAX_VALUE,
            ),
            timestamp = 2L,
        )
        val session = TestSession(
            id = "session",
            name = "session",
            createdAt = 1L,
            source = DataSource.USER,
            results = listOf(result),
        )
        composeRule.setContent {
            JointSenseTheme {
                ResultScreen(
                    resolution = ResultResolution.Found(session, result),
                    onContinueMeasurement = {},
                    onReturnToOrigin = {},
                    onGoHome = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(RESULT_FEATURES_SUMMARY_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithText("Infinity", substring = true).assertCountEquals(0)
    }

    @Test
    fun invalidConcentrationCannotRenderAnApparentlyValidRangeConclusion() {
        val result = TestResult(
            id = "invalid-range",
            sessionId = "session",
            draftId = null,
            factor = InflammationFactor.IL6,
            concentration = Float.NaN,
            rangeStatus = RangeStatus.ABOVE_RANGE,
            features = RgbFeatures(1f, 2f, 3f, 1f, 1f, 1f),
            timestamp = 2L,
        )
        val session = TestSession(
            id = "session",
            name = "session",
            createdAt = 1L,
            source = DataSource.USER,
            results = listOf(result),
        )
        val unknown = composeRule.activity.getString(R.string.measurement_range_unknown)
        val above = composeRule.activity.getString(R.string.measurement_range_above)

        composeRule.setContent {
            JointSenseTheme {
                ResultScreen(
                    resolution = ResultResolution.Found(session, result),
                    onContinueMeasurement = {},
                    onReturnToOrigin = {},
                    onGoHome = {},
                )
            }
        }

        composeRule.onAllNodesWithText(unknown, substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText(above, substring = true).assertCountEquals(0)
    }
}
