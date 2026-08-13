package cloud.univ.jointsense.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.feature.settings.R
import cloud.univ.jointsense.settings.locale.LanguageOption
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun profileShowsGroupedEntriesAndLanguageSelectionClosesBeforeApply() {
        var languageAtApply: LanguageOption? = null
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        sessionCount = 14,
                        builtInSampleCount = 12,
                        calibrationCount = 2,
                        calibrationReviewCount = 1,
                        countsLoaded = true,
                    ),
                    selectedLanguage = LanguageOption.ENGLISH,
                    readCurrentLanguage = { LanguageOption.ENGLISH },
                    onApplyLanguage = {
                        languageAtApply = it
                    },
                    onOpenHistory = {},
                    onCalibrate = {},
                    onOpenAbout = {},
                    onRequestClearAll = {},
                    onRequestRestoreSamples = {},
                    onConfirmDataAction = {},
                    onDismissDataAction = {},
                    onRetryDataAction = {},
                    onConsumeDataActionResult = {},
                )
            }
        }

        listOf(
            SETTINGS_LANGUAGE_TAG,
            SETTINGS_CALIBRATION_TAG,
            SETTINGS_HISTORY_TAG,
            SETTINGS_RESTORE_SAMPLES_TAG,
            SETTINGS_CLEAR_ALL_TAG,
            SETTINGS_ABOUT_TAG,
        ).forEach {
            composeRule.onNodeWithTag(it)
                .performScrollTo()
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(SemanticsMatcher("profile announcement is not repeated as the action label") { node ->
                    val announcement = node.config[SemanticsProperties.ContentDescription].single()
                    node.config[SemanticsActions.OnClick].label != announcement
                })
        }
        composeRule.onNodeWithTag(SCREEN_PROFILE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(PROFILE_SCREEN_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(SETTINGS_LANGUAGE_TAG).performClick()
        composeRule.onNodeWithTag(LANGUAGE_EN_TAG).assertIsSelected()
        composeRule.onNodeWithTag(LANGUAGE_ZH_CN_TAG).performClick()
        composeRule.onNodeWithTag(LANGUAGE_ZH_CN_TAG).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(LanguageOption.SIMPLIFIED_CHINESE, languageAtApply)
        }
    }

    @Test
    fun unchangedLanguageIsANoOp() {
        var applyCalls = 0
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    selectedLanguage = LanguageOption.SYSTEM,
                    readCurrentLanguage = { LanguageOption.SYSTEM },
                    onApplyLanguage = { applyCalls += 1 },
                    onOpenHistory = {}, onCalibrate = {}, onOpenAbout = {},
                    onRequestClearAll = {}, onRequestRestoreSamples = {},
                    onConfirmDataAction = {}, onDismissDataAction = {},
                    onRetryDataAction = {}, onConsumeDataActionResult = {},
                )
            }
        }

        composeRule.onNodeWithTag(SETTINGS_LANGUAGE_TAG).performClick()
        composeRule.onNodeWithTag(LANGUAGE_SYSTEM_TAG).performClick()
        composeRule.runOnIdle { assertEquals(0, applyCalls) }
    }

    @Test
    fun clearDialogDescribesExactScopeAndConfirmsTheStateMachine() {
        var confirmations = 0
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(dataAction = DataAction.Pending(DataActionType.CLEAR_ALL)),
                    selectedLanguage = LanguageOption.SYSTEM,
                    readCurrentLanguage = { LanguageOption.SYSTEM },
                    onApplyLanguage = {}, onOpenHistory = {}, onCalibrate = {}, onOpenAbout = {},
                    onRequestClearAll = {}, onRequestRestoreSamples = {},
                    onConfirmDataAction = { confirmations += 1 },
                    onDismissDataAction = {}, onRetryDataAction = {}, onConsumeDataActionResult = {},
                )
            }
        }
        composeRule.onNodeWithTag(CONFIRM_CLEAR_ALL_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_clear_scope), substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_delete)).performClick()
        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun restoreDialogDescribesExactScopeAndConfirmsTheStateMachine() {
        var confirmations = 0
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(
                        dataAction = DataAction.Pending(DataActionType.RESTORE_BUILT_IN_SAMPLES),
                    ),
                    selectedLanguage = LanguageOption.SYSTEM,
                    readCurrentLanguage = { LanguageOption.SYSTEM },
                    onApplyLanguage = {}, onOpenHistory = {}, onCalibrate = {}, onOpenAbout = {},
                    onRequestClearAll = {}, onRequestRestoreSamples = {},
                    onConfirmDataAction = { confirmations += 1 },
                    onDismissDataAction = {}, onRetryDataAction = {}, onConsumeDataActionResult = {},
                )
            }
        }
        composeRule.onNodeWithTag(CONFIRM_RESTORE_SAMPLES_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.settings_restore_samples_calibration_note),
            substring = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.settings_restore_samples_confirm)).performClick()
        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }

    @Test
    fun profileShowsLocalizedPlaceholdersBeforeCountsLoad() {
        val loading = composeRule.activity.getString(R.string.settings_counts_loading)
        composeRule.setContent {
            JointSenseTheme {
                SettingsScreen(
                    state = SettingsUiState(countsLoaded = false),
                    selectedLanguage = LanguageOption.SYSTEM,
                    readCurrentLanguage = { LanguageOption.SYSTEM },
                    onApplyLanguage = {}, onOpenHistory = {}, onCalibrate = {}, onOpenAbout = {},
                    onRequestClearAll = {}, onRequestRestoreSamples = {}, onConfirmDataAction = {},
                    onDismissDataAction = {}, onRetryDataAction = {}, onConsumeDataActionResult = {},
                )
            }
        }

        composeRule.onAllNodesWithText(loading).assertCountEquals(3)
    }

    @Test
    fun aboutShowsVersionMethodCurveWeightsAndExactDisclaimer() {
        composeRule.setContent {
            JointSenseTheme { AboutRouteScreen(appVersionName = "9.8.7", onBack = {}) }
        }

        listOf(
            "9.8.7",
            composeRule.activity.getString(R.string.settings_about_rgb_features),
            composeRule.activity.getString(R.string.settings_about_tealness),
            composeRule.activity.getString(R.string.settings_about_curve_behavior),
            composeRule.activity.getString(R.string.settings_about_oa_weights, 40, 35, 25),
            composeRule.activity.getString(R.string.research_disclaimer),
        ).forEach {
            composeRule.onNodeWithText(it, substring = true).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun profileActionsRemainReachableAtCompactWidthAndTwoHundredPercentText() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        SettingsScreen(
                            state = SettingsUiState(countsLoaded = true),
                            selectedLanguage = LanguageOption.SYSTEM,
                            readCurrentLanguage = { LanguageOption.SYSTEM },
                            onApplyLanguage = {}, onOpenHistory = {}, onCalibrate = {},
                            onOpenAbout = {}, onRequestClearAll = {}, onRequestRestoreSamples = {},
                            onConfirmDataAction = {}, onDismissDataAction = {},
                            onRetryDataAction = {}, onConsumeDataActionResult = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(PROFILE_IDENTITY_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_CLEAR_ALL_TAG).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_ABOUT_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutContentRemainsReachableAtCompactWidthAndTwoHundredPercentText() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        AboutRouteScreen(appVersionName = "9.8.7", onBack = {})
                    }
                }
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.research_disclaimer), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun confirmationActionsRemainReachableAtCompactWidthAndTwoHundredPercentText() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f),
            ) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        DataManagementDialogs(
                            action = DataAction.Pending(DataActionType.RESTORE_BUILT_IN_SAMPLES),
                            onDismiss = {},
                            onConfirm = {},
                            onRetry = {},
                            onConsumeResult = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(DATA_DIALOG_CONFIRM_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(DATA_DIALOG_DISMISS_TAG).assertIsDisplayed()
    }
}
