package cloud.univ.jointsense.calibration

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.feature.calibration.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalibrationAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun factorCardsAreNamedLargeRadioTargetsInASelectableGroup() {
        composeRule.setContent {
            JointSenseTheme {
                CalibrationAssignScreen(
                    state = CalibrationUiState(factor = InflammationFactor.TNF_ALPHA),
                    onFactorChanged = {},
                    onConcentrationChanged = { _, _ -> },
                    onReview = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(CALIBRATION_FACTOR_GROUP_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        InflammationFactor.entries.forEach { factor ->
            composeRule.onNodeWithTag(calibrationFactorTag(factor))
                .assertIsSelectable()
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        composeRule.onNodeWithTag(calibrationFactorTag(InflammationFactor.TNF_ALPHA)).assertIsSelected()
        composeRule.onNodeWithTag(SCREEN_CALIBRATION_ASSIGN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(CALIBRATION_ASSIGN_LEGACY_TAG).assertIsDisplayed()
    }

    @Test
    fun detectingCropIsExplicitlyDisabledAndHasNoMisleadingActions() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            JointSenseTheme {
                CalibrationCropScreen(
                    state = CalibrationUiState(
                        image = BitmapCalibrationImage(bitmap),
                        cropBounds = CalibrationIntBounds(20, 20, 80, 80),
                        isDetecting = true,
                    ),
                    onCropChanged = {},
                    onDetect = {},
                    onBack = {},
                )
            }
        }

        val crop = composeRule.onNodeWithTag(CALIBRATION_CROP_VIEW_TAG)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
        val actions = crop.fetchSemanticsNode().config.getOrElse(SemanticsActions.CustomActions) { emptyList() }
        assertEquals(emptyList<String>(), actions.map { it.label })
        composeRule.onNodeWithTag(CALIBRATION_DETECT_TAG).assertIsNotEnabled().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun enabledCropHasLocalizedMoveAndResizeAlternativesAndStableActionTags() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        var updated: CalibrationIntBounds? = null
        composeRule.setContent {
            JointSenseTheme {
                CalibrationCropScreen(
                    state = CalibrationUiState(
                        image = BitmapCalibrationImage(bitmap),
                        cropBounds = CalibrationIntBounds(20, 20, 80, 80),
                    ),
                    onCropChanged = { updated = it },
                    onDetect = {},
                    onBack = {},
                )
            }
        }

        val labels = composeRule.onNodeWithTag(CALIBRATION_CROP_VIEW_TAG)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(
            setOf(
                composeRule.activity.getString(R.string.calibration_crop_move_up),
                composeRule.activity.getString(R.string.calibration_crop_move_down),
                composeRule.activity.getString(R.string.calibration_crop_move_left),
                composeRule.activity.getString(R.string.calibration_crop_move_right),
                composeRule.activity.getString(R.string.calibration_crop_increase),
                composeRule.activity.getString(R.string.calibration_crop_decrease),
            ),
            labels.toSet(),
        )
        val action = composeRule.onNodeWithTag(CALIBRATION_CROP_VIEW_TAG)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].first()
        composeRule.runOnIdle { assertTrue(action.action()) }
        composeRule.runOnIdle { assertEquals(CalibrationIntBounds(20, 15, 80, 75), updated) }
        composeRule.onNodeWithTag(CALIBRATION_DETECT_TAG).assertIsDisplayed()
    }

    @Test
    fun saveActionHasStableNamedFortyEightDpTarget() {
        composeRule.setContent {
            JointSenseTheme {
                CalibrationReviewScreen(
                    state = CalibrationUiState(),
                    onSave = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(CALIBRATION_SAVE_TAG)
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun compactViewportAtTwoHundredPercentTextKeepsAssignActionsReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        CalibrationAssignScreen(
                            state = CalibrationUiState(factor = InflammationFactor.TNF_ALPHA),
                            onFactorChanged = {},
                            onConcentrationChanged = { _, _ -> },
                            onReview = {},
                            onBack = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(calibrationFactorTag(InflammationFactor.IL1_BETA))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CALIBRATION_REVIEW_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun compactViewportAtTwoHundredPercentTextKeepsSaveActionReachable() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        CalibrationReviewScreen(CalibrationUiState(), {}, {})
                    }
                }
            }
        }

        composeRule.onNodeWithTag(CALIBRATION_SAVE_TAG).performScrollTo().assertIsDisplayed()
    }
}
