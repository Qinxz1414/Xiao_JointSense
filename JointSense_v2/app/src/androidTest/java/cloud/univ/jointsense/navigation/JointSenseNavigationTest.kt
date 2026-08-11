package cloud.univ.jointsense.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.rememberNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.MainActivity
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JointSenseNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun showNavigationHarness() {
        composeRule.setContent {
            JointSenseTheme {
                JointSenseNavHost(
                    navController = rememberNavController(),
                    screenSlot = { route, actions -> TestScreen(route, actions) }
                )
            }
        }
    }

    @Test
    fun topLevelBackUsesTheRealNavigationHistory() {
        composeRule.onNodeWithTag("screen:home").assertIsDisplayed()

        composeRule.onNodeWithTag("go:trends").performClick()
        composeRule.onNodeWithTag("go:report").performClick()
        composeRule.onNodeWithTag("screen:report").assertIsDisplayed()
        check(!composeRule.activity.isFinishing)

        pressActivityBack()
        composeRule.onNodeWithTag("screen:trends").assertIsDisplayed()
        check(!composeRule.activity.isFinishing)

        pressActivityBack()
        composeRule.onNodeWithTag("screen:home").assertIsDisplayed()
        check(!composeRule.activity.isFinishing)
    }

    @Test
    fun openingHomeClearsTheRealNavigationHistory() {
        composeRule.onNodeWithTag("go:trends").performClick()
        composeRule.onNodeWithTag("go:report").performClick()
        composeRule.onNodeWithTag("go:home").performClick()
        composeRule.onNodeWithTag("screen:home").assertIsDisplayed()

        pressActivityBack()

        check(composeRule.activity.isFinishing)
    }

    @Test
    fun cropBackReturnsToImageSelect() {
        composeRule.onNodeWithTag("go:measurement-from-home").performClick()
        composeRule.onNodeWithTag("screen:image-select").assertIsDisplayed()
        composeRule.onNodeWithTag("go:crop").performClick()
        composeRule.onNodeWithTag("screen:crop").assertIsDisplayed()

        pressActivityBack()

        composeRule.onNodeWithTag("screen:image-select").assertIsDisplayed()
        check(!composeRule.activity.isFinishing)
    }

    @Test
    fun resultBackExitsTheWholeMeasurementGraphToItsOrigin() {
        composeRule.onNodeWithTag("go:trends").performClick()
        composeRule.onNodeWithTag("go:measurement-from-trends").performClick()
        composeRule.onNodeWithTag("go:crop").performClick()
        composeRule.onNodeWithTag("go:factor").performClick()
        composeRule.onNodeWithTag("go:result").performClick()
        composeRule.onNodeWithTag("screen:result:real-result-id").assertIsDisplayed()

        pressActivityBack()

        composeRule.onNodeWithTag("screen:trends").assertIsDisplayed()
        check(!composeRule.activity.isFinishing)
    }

    @Test
    fun historicalResultBackReturnsToHistory() {
        composeRule.onNodeWithTag("go:profile").performClick()
        composeRule.onNodeWithTag("go:history").performClick()
        composeRule.onNodeWithTag("go:historical-result").performClick()
        composeRule.onNodeWithTag("screen:result:historical-result-id").assertIsDisplayed()

        pressActivityBack()

        composeRule.onNodeWithTag("screen:history").assertIsDisplayed()
        check(!composeRule.activity.isFinishing)
    }

    @Test
    fun historicalResultContinuationReturnsToHistoryAfterTheNewResult() {
        composeRule.onNodeWithTag("go:profile").performClick()
        composeRule.onNodeWithTag("go:history").performClick()
        composeRule.onNodeWithTag("go:historical-result").performClick()
        composeRule.onNodeWithTag("continue:historical-result").performClick()
        composeRule.onNodeWithTag("screen:image-select").assertIsDisplayed()

        composeRule.onNodeWithTag("complete:continued-result").performClick()
        composeRule.onNodeWithTag("screen:result:continued-result-id").assertIsDisplayed()

        pressActivityBack()

        composeRule.onNodeWithTag("screen:history").assertIsDisplayed()
        check(!composeRule.activity.isFinishing)
    }

    @Test
    fun calibrationSystemBackPopsExactlyOneRouteAtATime() {
        composeRule.onNodeWithTag("go:profile").performClick()
        composeRule.onNodeWithTag("go:calibration").performClick()
        composeRule.onNodeWithTag("go:calibration-crop").performClick()
        composeRule.onNodeWithTag("go:calibration-assign").performClick()
        composeRule.onNodeWithTag("go:calibration-review").performClick()
        composeRule.onNodeWithTag("go:calibration-done").performClick()

        listOf("calibration-review", "calibration-assign", "calibration-crop", "calibration-select", "profile")
            .forEach { expected ->
                pressActivityBack()
                composeRule.onNodeWithTag("screen:$expected").assertIsDisplayed()
                check(!composeRule.activity.isFinishing)
            }
    }

    @Test
    fun calibrationTopBackPopsOneRouteAndDoneExitsTheGraph() {
        composeRule.onNodeWithTag("go:profile").performClick()
        composeRule.onNodeWithTag("go:calibration").performClick()
        composeRule.onNodeWithTag("go:calibration-crop").performClick()
        composeRule.onNodeWithTag("back:calibration").performClick()
        composeRule.onNodeWithTag("screen:calibration-select").assertIsDisplayed()

        composeRule.onNodeWithTag("go:calibration-crop").performClick()
        composeRule.onNodeWithTag("go:calibration-assign").performClick()
        composeRule.onNodeWithTag("go:calibration-review").performClick()
        composeRule.onNodeWithTag("go:calibration-done").performClick()
        composeRule.onNodeWithTag("finish:calibration").performClick()

        composeRule.onNodeWithTag("screen:profile").assertIsDisplayed()
        check(!composeRule.activity.isFinishing)
    }

    @Test
    fun mainActivityStartsWithTheAppCompatTheme() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(activity is AppCompatActivity)
            }
        }
    }

    private fun pressActivityBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun TestScreen(route: JointSenseRoute, actions: NavigationActions) {
        Column {
            when (route) {
                HomeRoute -> {
                    ScreenMarker("home")
                    NavButton("go:trends") {
                        actions.openTopLevel(TopLevelDestination.TRENDS)
                    }
                    NavButton("go:measurement-from-home") {
                        actions.startMeasurement(TopLevelDestination.HOME)
                    }
                    NavButton("go:profile") {
                        actions.openTopLevel(TopLevelDestination.PROFILE)
                    }
                }

                TrendsRoute -> {
                    ScreenMarker("trends")
                    NavButton("go:report") {
                        actions.openTopLevel(TopLevelDestination.REPORT)
                    }
                    NavButton("go:measurement-from-trends") {
                        actions.startMeasurement(TopLevelDestination.TRENDS)
                    }
                }

                ReportRoute -> {
                    ScreenMarker("report")
                    NavButton("go:home") {
                        actions.openTopLevel(TopLevelDestination.HOME)
                    }
                }
                ProfileRoute -> {
                    ScreenMarker("profile")
                    NavButton("go:history", actions::openHistory)
                    NavButton("go:calibration", actions::startCalibration)
                }
                HistoryRoute -> {
                    ScreenMarker("history")
                    NavButton("go:historical-result") {
                        actions.openResult("historical-result-id")
                    }
                }
                ImageSelectRoute -> {
                    ScreenMarker("image-select")
                    NavButton("go:crop", actions::openCrop)
                    NavButton("complete:continued-result") {
                        actions.openResult("continued-result-id")
                    }
                }

                CropRoute -> {
                    ScreenMarker("crop")
                    NavButton("go:factor", actions::openFactorSelect)
                }

                FactorSelectRoute -> {
                    ScreenMarker("factor")
                    NavButton("go:result") { actions.openResult("real-result-id") }
                }

                is ResultRoute -> {
                    ScreenMarker("result:${route.resultId}")
                    if (route.resultId == "historical-result-id") {
                        NavButton("continue:historical-result") {
                            actions.continueMeasurementFromResult(TopLevelDestination.PROFILE)
                        }
                    }
                }
                CalibrationSelectRoute -> {
                    ScreenMarker("calibration-select")
                    NavButton("go:calibration-crop", actions::openCalibrationCrop)
                }
                CalibrationCropRoute -> {
                    ScreenMarker("calibration-crop")
                    NavButton("go:calibration-assign", actions::openCalibrationAssign)
                    NavButton("back:calibration", actions::navigateBack)
                }
                CalibrationAssignRoute -> {
                    ScreenMarker("calibration-assign")
                    NavButton("go:calibration-review", actions::openCalibrationReview)
                }
                CalibrationReviewRoute -> {
                    ScreenMarker("calibration-review")
                    NavButton("go:calibration-done", actions::openCalibrationDone)
                }
                CalibrationDoneRoute -> {
                    ScreenMarker("calibration-done")
                    NavButton("finish:calibration", actions::exitCalibration)
                }
            }
        }
    }

    @Composable
    private fun ScreenMarker(name: String) {
        Text(text = name, modifier = Modifier.testTag("screen:$name"))
    }

    @Composable
    private fun NavButton(tag: String, onClick: () -> Unit) {
        Button(onClick = onClick, modifier = Modifier.testTag(tag)) {
            Text(tag)
        }
    }
}
