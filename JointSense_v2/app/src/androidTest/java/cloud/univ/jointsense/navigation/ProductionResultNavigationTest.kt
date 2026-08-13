package cloud.univ.jointsense.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import cloud.univ.jointsense.measurement.BaselineAnalysisResult
import cloud.univ.jointsense.measurement.BaselinePhotoAnalysisAdapter
import cloud.univ.jointsense.measurement.CONTINUE_MEASUREMENT_TAG
import cloud.univ.jointsense.measurement.CropBounds
import cloud.univ.jointsense.measurement.MeasurementImage
import cloud.univ.jointsense.measurement.MeasurementViewModel
import cloud.univ.jointsense.measurement.RESULT_HOME_ACTION_TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionResultNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun productionHistoricalResultBackReturnsToRealHistoryOrigin() {
        val viewModel = createMeasurementViewModel()
        showProductionResultHost(viewModel)

        composeRule.onNodeWithTag("production:profile").performClick()
        composeRule.onNodeWithTag("production:history").performClick()
        composeRule.onNodeWithTag("production:historical-result").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("production-screen:history").assertIsDisplayed()
    }

    @Test
    fun productionActiveResultBackReturnsToRealMeasurementOrigin() {
        val viewModel = createMeasurementViewModel()
        showProductionResultHost(viewModel)
        composeRule.onNodeWithTag("production:trends").performClick()
        composeRule.onNodeWithTag("production:start-measurement").performClick()
        composeRule.onNodeWithTag("production:continued-result").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("production-screen:trends").assertIsDisplayed()
    }

    @Test
    fun productionHistoricalResultHomeActionClearsToTheRealHomeRoot() {
        val viewModel = createMeasurementViewModel()
        showProductionResultHost(viewModel)

        composeRule.onNodeWithTag("production:profile").performClick()
        composeRule.onNodeWithTag("production:history").performClick()
        composeRule.onNodeWithTag("production:historical-result").performClick()
        composeRule.onNodeWithTag(RESULT_HOME_ACTION_TAG).performScrollTo().performClick()

        composeRule.onNodeWithTag("production-screen:home").assertIsDisplayed()
    }

    @Test
    fun productionContinueReplacesCompletedGraphChangesDraftAndLaterBackKeepsOrigin() {
        val viewModel = createMeasurementViewModel()
        showProductionResultHost(viewModel)
        composeRule.onNodeWithTag("production:profile").performClick()
        composeRule.onNodeWithTag("production:history").performClick()
        composeRule.onNodeWithTag("production:historical-result").performClick()
        val completedDraft = viewModel.state.value.draftId

        composeRule.onNodeWithTag(CONTINUE_MEASUREMENT_TAG).performScrollTo().performClick()

        composeRule.onNodeWithTag("production-screen:image-select").assertIsDisplayed()
        composeRule.runOnIdle {
            assertNotEquals(completedDraft, viewModel.state.value.draftId)
        }
        composeRule.onNodeWithTag("production:continued-result").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag("production-screen:history").assertIsDisplayed()
    }

    private fun createMeasurementViewModel(): MeasurementViewModel {
        lateinit var viewModel: MeasurementViewModel
        composeRule.runOnIdle {
            viewModel = MeasurementViewModel(
                repository = ProductionResultRepository(),
                analyzer = ProductionResultAnalyzer,
                draftIdFactory = sequenceOf("completed-draft", "fresh-draft").iterator()::next,
                savedStateHandle = SavedStateHandle(),
                decoder = null,
                ioDispatcher = Dispatchers.Main.immediate,
                defaultDispatcher = Dispatchers.Main.immediate,
            )
            viewModel.selectSession(SESSION_ID)
        }
        composeRule.waitUntil { viewModel.state.value.currentSession != null }
        return viewModel
    }

    private fun showProductionResultHost(viewModel: MeasurementViewModel) {
        composeRule.setContent {
            JointSenseTheme {
                JointSenseNavHostForTest(
                    navController = rememberNavController(),
                    measurementViewModel = viewModel,
                    screenSlot = { route, actions ->
                        Column {
                            Text(
                                text = route.toString(),
                                modifier = Modifier.testTag("production-screen:${route.marker()}"),
                            )
                            when (route) {
                                HomeRoute -> {
                                    NavButton("production:profile") {
                                        actions.openTopLevel(TopLevelDestination.PROFILE)
                                    }
                                    NavButton("production:trends") {
                                        actions.openTopLevel(TopLevelDestination.TRENDS)
                                    }
                                }
                                TrendsRoute -> NavButton("production:start-measurement") {
                                    actions.startMeasurement(TopLevelDestination.TRENDS)
                                }
                                ProfileRoute -> NavButton("production:history", actions::openHistory)
                                HistoryRoute -> NavButton("production:historical-result") {
                                    actions.openResult(RESULT_ID)
                                }
                                ImageSelectRoute -> NavButton("production:continued-result") {
                                    actions.openResult("continued-result")
                                }
                                else -> Unit
                            }
                        }
                    },
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun NavButton(tag: String, onClick: () -> Unit) {
        Button(onClick = onClick, modifier = Modifier.testTag(tag)) { Text(tag) }
    }
}

private fun JointSenseRoute.marker(): String = when (this) {
    HomeRoute -> "home"
    TrendsRoute -> "trends"
    ReportRoute -> "report"
    ProfileRoute -> "profile"
    HistoryRoute -> "history"
    ImageSelectRoute -> "image-select"
    CropRoute -> "crop"
    FactorSelectRoute -> "factor"
    is ResultRoute -> "result"
    CalibrationSelectRoute -> "calibration-select"
    CalibrationCropRoute -> "calibration-crop"
    CalibrationAssignRoute -> "calibration-assign"
    CalibrationReviewRoute -> "calibration-review"
    CalibrationDoneRoute -> "calibration-done"
    else -> "unknown"
}

private object ProductionResultAnalyzer : BaselinePhotoAnalysisAdapter {
    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
        factor: InflammationFactor,
    ) = BaselineAnalysisResult(12f, RangeStatus.IN_RANGE, FEATURES)
}

private class ProductionResultRepository : TestSessionRepository {
    private val sessions = MutableStateFlow(
        listOf(
            TestSession(
                id = SESSION_ID,
                name = "Production route session",
                createdAt = 1L,
                source = DataSource.USER,
                results = listOf(
                    TestResult(
                        id = RESULT_ID,
                        sessionId = SESSION_ID,
                        draftId = "stored-draft",
                        factor = InflammationFactor.IL6,
                        concentration = 12f,
                        rangeStatus = RangeStatus.IN_RANGE,
                        features = FEATURES,
                        timestamp = 2L,
                    ),
                ),
            ),
        ),
    )

    override fun observeSessions(): Flow<List<TestSession>> = sessions

    override fun observeSession(id: String): Flow<TestSession?> =
        sessions.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun createSession(name: String, source: DataSource): String = SESSION_ID

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String = "unused"

    override suspend fun deleteSession(id: String) = Unit
}

private const val SESSION_ID = "production-result-session"
private const val RESULT_ID = "production-result"
private val FEATURES = RgbFeatures(1f, 2f, 3f, 0f, 0f, 0f)
