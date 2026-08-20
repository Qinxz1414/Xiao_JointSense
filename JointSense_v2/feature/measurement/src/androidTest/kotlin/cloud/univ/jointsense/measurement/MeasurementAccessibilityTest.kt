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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
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
import java.text.DateFormat
import java.util.Date

@RunWith(AndroidJUnit4::class)
class MeasurementAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun imageSelectionActionsAreNamedLargeButtonTargets() {
        composeRule.setContent {
            JointSenseTheme {
                ImageSelectScreen(
                    onTakePhoto = {},
                    onPickImage = {},
                    onBack = {},
                    sessionName = "Study visit",
                )
            }
        }

        composeRule.onNodeWithTag(MEASUREMENT_TAKE_PHOTO_TAG)
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithTag(MEASUREMENT_GALLERY_TAG)
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeRule.onNodeWithTag(SCREEN_MEASUREMENT_SELECT_TAG).assertIsDisplayed()
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
    fun historyOpenCardHasOneCompleteAnnouncementAndDeleteStaysSeparate() {
        val session = TestSession(
            id = "history-summary",
            name = "Study visit",
            createdAt = 1_000L,
            source = DataSource.USER,
            results = listOf(
                historyResult("a", InflammationFactor.TNF_ALPHA),
                historyResult("b", InflammationFactor.IL6),
            ),
        )
        composeRule.setContent {
            JointSenseTheme { HistoryScreen(listOf(session), {}, {}, {}) }
        }

        val announcement = composeRule.onNodeWithTag(historySessionTag(session.id))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        val locale = composeRule.activity.resources.configuration.locales[0]
        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
            .format(Date(session.createdAt))
        listOf("Study visit", date, "2", InflammationFactor.TNF_ALPHA.shortName, InflammationFactor.IL6.shortName)
            .forEach { expected -> assertTrue(announcement.contains(expected)) }
        composeRule.onAllNodesWithText("Study visit").assertCountEquals(0)
        composeRule.onNodeWithTag(historyDeleteTag(session.id))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
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
    fun invalidCropUsesInlineGuidanceWithoutInterruptingMeasurement() {
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        composeRule.setContent {
            JointSenseTheme {
                ImageCropScreen(
                    bitmap = bitmap,
                    cropRect = Rect(10, 20, 210, 180),
                    isCropValid = false,
                    onCropRectChanged = {},
                    onConfirm = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.measurement_error_invalid_crop),
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            composeRule.activity.getString(R.string.measurement_interrupted),
        ).assertCountEquals(0)
        composeRule.onNodeWithTag(MEASUREMENT_CROP_CONFIRM_TAG).assertIsNotEnabled()
    }

    @Test
    fun twoHundredPercentTextKeepsMeasurementActionsReachableInCompactViewport() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        ImageSelectScreen(
                            onTakePhoto = {},
                            onPickImage = {},
                            onBack = {},
                            sessionName = "Study visit",
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithTag(MEASUREMENT_GALLERY_TAG)
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

    @Test
    fun twoHundredPercentTextKeepsHistoryOpenAndDeleteActionsReachableInCompactViewport() {
        val session = TestSession(
            id = "history-compact",
            name = "Long research study visit name",
            createdAt = 1_000L,
            source = DataSource.USER,
            results = listOf(historyResult("compact", InflammationFactor.IL1_BETA)),
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                JointSenseTheme {
                    Box(Modifier.requiredSize(360.dp, 640.dp)) {
                        HistoryScreen(listOf(session), {}, {}, {})
                    }
                }
            }
        }

        composeRule.onNodeWithTag(historySessionTag(session.id)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(historyDeleteTag(session.id)).performScrollTo().assertIsDisplayed()
    }

    private fun historyResult(id: String, factor: InflammationFactor) = TestResult(
        id = id,
        sessionId = "history-summary",
        draftId = null,
        factor = factor,
        concentration = 1f,
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(1f, 2f, 3f, 0f, 0f, 0f),
        timestamp = 1_000L,
    )
}
