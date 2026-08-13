package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun factorChoicesAreASelectableGroupWithNamedLargeRadioTargets() {
        composeRule.setContent {
            JointSenseTheme {
                FactorSelectScreen(
                    selectedFactor = InflammationFactor.IL6,
                    onFactorSelected = {},
                    onAnalyze = {},
                    onBack = {},
                    isAnalyzing = false,
                )
            }
        }

        composeRule.onNodeWithTag(MEASUREMENT_FACTOR_GROUP_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        InflammationFactor.entries.forEach { factor ->
            composeRule.onNodeWithTag(measurementFactorTag(factor))
                .assertIsSelectable()
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        composeRule.onNodeWithTag(measurementFactorTag(InflammationFactor.IL6)).assertIsSelected()
        composeRule.onNodeWithTag(ANALYZE_BUTTON_TAG).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag(SCREEN_MEASUREMENT_FACTOR_TAG).assertIsDisplayed()
    }

    @Test
    fun historyDeleteHasItsOwnNamedFortyEightDpButtonTarget() {
        val session = TestSession(
            id = "history-a11y",
            name = "Study visit",
            createdAt = 1L,
            source = DataSource.USER,
            results = emptyList(),
        )
        composeRule.setContent {
            JointSenseTheme {
                HistoryScreen(listOf(session), {}, {}, {})
            }
        }

        composeRule.onNodeWithTag(SCREEN_HISTORY_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(historyDeleteTag(session.id))
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
    }

    @Test
    fun cropCanvasExposesLocalizedStateAndAlternativeMoveResizeActions() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        var updated: Rect? = null
        composeRule.setContent {
            JointSenseTheme {
                ImageCropScreen(
                    bitmap = bitmap,
                    cropRect = Rect(20, 20, 80, 80),
                    onCropRectChanged = { updated = it },
                    onConfirm = {},
                    onBack = {},
                )
            }
        }

        val cropNode = composeRule.onNodeWithTag(MEASUREMENT_CROP_VIEW_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val labels = cropNode.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(
            setOf(
                composeRule.activity.getString(R.string.measurement_crop_move_up),
                composeRule.activity.getString(R.string.measurement_crop_move_down),
                composeRule.activity.getString(R.string.measurement_crop_move_left),
                composeRule.activity.getString(R.string.measurement_crop_move_right),
                composeRule.activity.getString(R.string.measurement_crop_increase),
                composeRule.activity.getString(R.string.measurement_crop_decrease),
            ),
            labels.toSet(),
        )
        composeRule.runOnIdle { assertTrue(cropNode.fetchSemanticsNode().config[SemanticsActions.CustomActions].first().action()) }
        composeRule.runOnIdle { assertEquals(Rect(20, 15, 80, 75), updated) }
        composeRule.onNodeWithTag(MEASUREMENT_CROP_CONFIRM_TAG).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun cropAlternativeActionsNormalizeOversizedInputWithoutThrowing() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        var updated: Rect? = null
        composeRule.setContent {
            JointSenseTheme {
                ImageCropScreen(
                    bitmap = bitmap,
                    cropRect = Rect(-20, -20, 150, 150),
                    onCropRectChanged = { updated = it },
                    onConfirm = {},
                    onBack = {},
                )
            }
        }

        val action = composeRule.onNodeWithTag(MEASUREMENT_CROP_VIEW_TAG)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].first()
        composeRule.runOnIdle { assertTrue(action.action()) }
        composeRule.runOnIdle { assertEquals(Rect(0, 0, 100, 100), updated) }
    }

    @Test
    fun twoHundredPercentTextKeepsMeasurementActionsReachableInCompactViewport() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        FactorSelectScreen(
                            selectedFactor = InflammationFactor.TNF_ALPHA,
                            onFactorSelected = {},
                            onAnalyze = {},
                            onBack = {},
                            isAnalyzing = false,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(ANALYZE_BUTTON_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(measurementFactorTag(InflammationFactor.IL1_BETA))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun twoHundredPercentTextKeepsResultActionsReachableInCompactViewport() {
        val result = TestResult(
            id = "result-a11y",
            sessionId = "session-a11y",
            draftId = "draft-a11y",
            factor = InflammationFactor.TNF_ALPHA,
            concentration = 12.3f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = RgbFeatures(1f, 2f, 3f, 0.1f, 0.2f, 0.3f),
            timestamp = 1L,
        )
        val session = TestSession(
            id = result.sessionId,
            name = "Study visit",
            createdAt = 1L,
            source = DataSource.USER,
            results = listOf(result),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
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
            }
        }

        composeRule.onNodeWithTag(CONTINUE_MEASUREMENT_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(RESULT_HOME_ACTION_TAG).performScrollTo().assertIsDisplayed()
    }
}
