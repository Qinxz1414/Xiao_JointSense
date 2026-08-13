package cloud.univ.jointsense.designsystem

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cloud.univ.jointsense.designsystem.component.GradeScale
import cloud.univ.jointsense.designsystem.component.GradeBadge
import cloud.univ.jointsense.designsystem.component.GRADE_BADGE_CONTAINER_TAG
import cloud.univ.jointsense.designsystem.component.GRADE_BADGE_SWATCH_TAG
import cloud.univ.jointsense.designsystem.component.LoadingErrorState
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DesignSystemComponentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun gradeScaleExposesReadOnlyCurrentState() {
        composeRule.setContent {
            JointSenseTheme {
                GradeScale(
                    currentGrade = 2,
                    labels = listOf("0", "1", "2", "3", "4"),
                    contentDescription = "OA grade scale",
                    stateDescription = "Grade 2",
                )
            }
        }

        composeRule.onNodeWithContentDescription("OA grade scale")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Grade 2"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun gradeScaleExposesUnknownStateWithoutSelectingAnEndpoint() {
        composeRule.setContent {
            JointSenseTheme {
                GradeScale(
                    currentGrade = null,
                    labels = listOf("0", "1", "2", "3", "4"),
                    contentDescription = "OA grade scale",
                    stateDescription = "Grade unavailable",
                )
            }
        }

        composeRule.onNodeWithContentDescription("OA grade scale")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Grade unavailable"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun gradeBadgeExposesNeutralReadOnlyTextAndGradeState() {
        composeRule.setContent {
            JointSenseTheme {
                GradeBadge(
                    grade = 2,
                    label = "Moderate",
                    contentDescription = "OA inflammation grade",
                    stateDescription = "Grade 2, Moderate",
                )
            }
        }

        composeRule.onNodeWithContentDescription("OA inflammation grade")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Grade 2, Moderate"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        composeRule.onNodeWithText("Moderate").assertIsDisplayed()
        composeRule.onNodeWithTag(GRADE_BADGE_CONTAINER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(GRADE_BADGE_SWATCH_TAG, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun loadingErrorStateSupportsTwoIndependentActions() {
        var primaryCalls = 0
        var secondaryCalls = 0
        composeRule.setContent {
            JointSenseTheme {
                LoadingErrorState(
                    isLoading = false,
                    message = "Migration failed",
                    actionLabel = "Retry",
                    onAction = { primaryCalls++ },
                    secondaryActionLabel = "Start empty",
                    onSecondaryAction = { secondaryCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("Retry").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Start empty").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(1, primaryCalls)
            assertEquals(1, secondaryCalls)
        }
    }
}
