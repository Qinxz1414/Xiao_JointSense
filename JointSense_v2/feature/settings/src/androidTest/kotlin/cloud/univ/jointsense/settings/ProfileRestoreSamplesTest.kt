package cloud.univ.jointsense.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
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
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(restoreSamplesConfirmationPending = true),
                    onOpenHistory = {},
                    onCalibrate = {},
                    onClearAllData = {},
                    onConfirmRestoreSamples = { confirms += 1 },
                    onCancelRestoreSamples = {},
                )
            }
        }

        composeRule.onNodeWithTag(RESTORE_SAMPLES_CONFIRMATION_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(
            "Restoring built-in sample sessions does not change or restore user calibration curves.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Restore samples").performClick()
        composeRule.runOnIdle { assertEquals(1, confirms) }
    }

    @Test
    fun restoreConfirmationCanBeCancelledWithoutConfirming() {
        var confirms = 0
        var cancels = 0
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(restoreSamplesConfirmationPending = true),
                    onOpenHistory = {},
                    onCalibrate = {},
                    onClearAllData = {},
                    onConfirmRestoreSamples = { confirms += 1 },
                    onCancelRestoreSamples = { cancels += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(0, confirms)
            assertEquals(1, cancels)
        }
    }
}
