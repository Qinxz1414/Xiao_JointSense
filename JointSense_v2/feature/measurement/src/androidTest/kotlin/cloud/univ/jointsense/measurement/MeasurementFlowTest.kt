package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.InflammationFactor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun analysisProgressIsVisibleAndAnalyzeCannotBeSubmittedAgain() {
        composeRule.setContent {
            JointSenseTheme {
                FactorSelectScreen(
                    selectedFactor = InflammationFactor.IL6,
                    onFactorSelected = {},
                    onAnalyze = {},
                    onBack = {},
                    isAnalyzing = true,
                )
            }
        }

        composeRule.onNodeWithTag(MEASUREMENT_PROGRESS_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ANALYZE_BUTTON_TAG).assertIsNotEnabled()
    }

    @Test
    fun recoverablePermissionErrorOffersRetryAndSystemSettings() {
        composeRule.setContent {
            JointSenseTheme {
                MeasurementErrorContent(
                    error = MeasurementError.PermissionDenied(permanentlyDenied = true),
                    onRetry = {},
                    onOpenSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag(MEASUREMENT_ERROR_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RETRY_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open settings").assertIsDisplayed()
    }

    @Test
    fun cropBackReturnsToImageSelectionCallback() {
        var returned = false
        composeRule.setContent {
            JointSenseTheme {
                ImageCropScreen(
                    bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888),
                    cropRect = Rect(10, 10, 90, 90),
                    onCropRectChanged = {},
                    onConfirm = {},
                    onBack = { returned = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle { assertTrue(returned) }
    }

    @Test
    fun resultBackInvokesReturnToOrigin() {
        var returned = false
        composeRule.setContent {
            JointSenseTheme {
                ResultScreen(
                    session = null,
                    lastResult = null,
                    canAddMore = false,
                    onContinueMeasurement = {},
                    onReturnToOrigin = { returned = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle { assertTrue(returned) }
    }

    @Test
    fun continueMeasurementInvokesFreshFlowCallback() {
        var continued = false
        composeRule.setContent {
            JointSenseTheme {
                ResultScreen(
                    session = null,
                    lastResult = null,
                    canAddMore = true,
                    onContinueMeasurement = { continued = true },
                    onReturnToOrigin = {},
                )
            }
        }

        composeRule.onNodeWithTag(CONTINUE_MEASUREMENT_TAG).performScrollTo().performClick()

        composeRule.runOnIdle { assertTrue(continued) }
    }
}
