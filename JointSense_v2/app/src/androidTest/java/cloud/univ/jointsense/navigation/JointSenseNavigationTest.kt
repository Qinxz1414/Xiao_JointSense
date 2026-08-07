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
import cloud.univ.jointsense.ui.theme.JointSenseTheme
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

                ReportRoute -> ScreenMarker("report")
                ProfileRoute -> {
                    ScreenMarker("profile")
                    NavButton("go:history", actions::openHistory)
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
                }

                CropRoute -> {
                    ScreenMarker("crop")
                    NavButton("go:factor", actions::openFactorSelect)
                }

                FactorSelectRoute -> {
                    ScreenMarker("factor")
                    NavButton("go:result") { actions.openResult("real-result-id") }
                }

                is ResultRoute -> ScreenMarker("result:${route.resultId}")
                CalibrationSelectRoute -> ScreenMarker("calibration-select")
                CalibrationCropRoute -> ScreenMarker("calibration-crop")
                CalibrationAssignRoute -> ScreenMarker("calibration-assign")
                CalibrationReviewRoute -> ScreenMarker("calibration-review")
                CalibrationDoneRoute -> ScreenMarker("calibration-done")
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
