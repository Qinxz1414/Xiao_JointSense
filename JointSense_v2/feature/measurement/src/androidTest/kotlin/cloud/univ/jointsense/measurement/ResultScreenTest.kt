package cloud.univ.jointsense.measurement

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
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
                    session = session,
                    lastResult = result,
                    canAddMore = true,
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
                    session = null,
                    lastResult = null,
                    canAddMore = false,
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
}
