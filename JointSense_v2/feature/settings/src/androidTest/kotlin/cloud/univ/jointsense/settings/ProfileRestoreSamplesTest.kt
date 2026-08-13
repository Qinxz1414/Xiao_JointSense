package cloud.univ.jointsense.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.feature.settings.R
import cloud.univ.jointsense.settings.locale.LanguageOption
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileRestoreSamplesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pendingRestoreShowsExplicitCalibrationSafeConfirmationAndConfirmsOnce() {
        var confirms = 0
        val calibrationMessage = composeRule.activity.getString(R.string.settings_restore_samples_calibration_note)
        val restoreLabel = composeRule.activity.getString(R.string.settings_restore_samples_confirm)
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        dataAction = DataAction.Pending(DataActionType.RESTORE_BUILT_IN_SAMPLES),
                    ),
                    selectedLanguage = LanguageOption.SYSTEM,
                    readCurrentLanguage = { LanguageOption.SYSTEM },
                    onApplyLanguage = {},
                    onOpenHistory = {},
                    onCalibrate = {},
                    onOpenAbout = {},
                    onRequestClearAll = {},
                    onRequestRestoreSamples = {},
                    onConfirmDataAction = { confirms += 1 },
                    onDismissDataAction = {},
                    onRetryDataAction = {},
                    onConsumeDataActionResult = {},
                )
            }
        }

        composeRule.onNodeWithTag(RESTORE_SAMPLES_CONFIRMATION_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(calibrationMessage, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(restoreLabel).performClick()
        composeRule.runOnIdle { assertEquals(1, confirms) }
    }

    @Test
    fun restoreConfirmationCanBeCancelledWithoutConfirming() {
        var confirms = 0
        var cancels = 0
        val cancelLabel = composeRule.activity.getString(R.string.settings_cancel)
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        dataAction = DataAction.Pending(DataActionType.RESTORE_BUILT_IN_SAMPLES),
                    ),
                    selectedLanguage = LanguageOption.SYSTEM,
                    readCurrentLanguage = { LanguageOption.SYSTEM },
                    onApplyLanguage = {},
                    onOpenHistory = {},
                    onCalibrate = {},
                    onOpenAbout = {},
                    onRequestClearAll = {},
                    onRequestRestoreSamples = {},
                    onConfirmDataAction = { confirms += 1 },
                    onDismissDataAction = { cancels += 1 },
                    onRetryDataAction = {},
                    onConsumeDataActionResult = {},
                )
            }
        }

        composeRule.onNodeWithText(cancelLabel).performClick()
        composeRule.runOnIdle {
            assertEquals(0, confirms)
            assertEquals(1, cancels)
        }
    }
}
