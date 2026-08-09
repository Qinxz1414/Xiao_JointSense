package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun realFactorRouteBlocksTopAndSystemBackAndSingleFlightsThroughAnalyzeAndPersist() {
        val repository = RouteRepository(blockCommit = true)
        val analyzer = RouteAnalyzer(blockAnalysis = true)
        val viewModel = readyViewModel(repository, analyzer)
        var backCalls = 0
        var resultCalls = 0
        composeRule.setContent {
            JointSenseTheme {
                FactorSelectRouteScreen(
                    viewModel = viewModel,
                    onResultReady = { resultCalls += 1 },
                    onBack = { backCalls += 1 },
                )
            }
        }

        composeRule.onNodeWithTag(ANALYZE_BUTTON_TAG).performClick()
        composeRule.waitUntil { viewModel.state.value.stage == Stage.Analyzing }

        composeRule.onNodeWithTag(MEASUREMENT_PROGRESS_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ANALYZE_BUTTON_TAG).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Back").assertIsNotEnabled()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.runOnIdle {
            assertEquals(0, backCalls)
            assertEquals(1, analyzer.calls)
        }

        analyzer.release()
        composeRule.waitUntil { viewModel.state.value.stage == Stage.Persisting }
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.runOnIdle {
            assertEquals(0, backCalls)
            assertEquals(1, repository.commitCalls)
        }

        repository.releaseCommit()
        composeRule.waitUntil { resultCalls == 1 }
        composeRule.runOnIdle {
            assertEquals(Stage.Success, viewModel.state.value.stage)
            assertEquals(1, repository.commitCalls)
            assertEquals(1, resultCalls)
        }
    }

    @Test
    fun realFactorRouteRetryPreservesDraftAndFactorAndCommitsOnce() {
        val repository = RouteRepository()
        val analyzer = RouteAnalyzer(failuresRemaining = 1)
        val viewModel = readyViewModel(repository, analyzer)
        composeRule.runOnIdle {
            viewModel.onAction(MeasurementAction.FactorSelected(InflammationFactor.TNF_ALPHA))
        }
        val draft = viewModel.state.value.draftId
        var resultCalls = 0
        composeRule.setContent {
            JointSenseTheme {
                FactorSelectRouteScreen(
                    viewModel = viewModel,
                    onResultReady = { resultCalls += 1 },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(ANALYZE_BUTTON_TAG).performClick()
        composeRule.waitUntil { viewModel.state.value.stage == Stage.RecoverableError }
        composeRule.onNodeWithTag(MEASUREMENT_ERROR_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RETRY_BUTTON_TAG).performClick()
        composeRule.waitUntil { resultCalls == 1 }

        composeRule.runOnIdle {
            assertEquals(draft, viewModel.state.value.draftId)
            assertEquals(InflammationFactor.TNF_ALPHA, viewModel.state.value.factor)
            assertEquals(2, analyzer.calls)
            assertEquals(1, repository.commitCalls)
        }
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
    fun realCropRouteBackReturnsViewModelToImageSelection() {
        lateinit var viewModel: MeasurementViewModel
        composeRule.runOnIdle {
            viewModel = MeasurementViewModel(
                repository = RouteRepository(),
                analyzer = RouteAnalyzer(),
                draftIdFactory = { "crop-draft" },
                savedStateHandle = SavedStateHandle(),
                decoder = null,
                ioDispatcher = Dispatchers.Main.immediate,
                defaultDispatcher = Dispatchers.Main.immediate,
            )
            viewModel.setImage(
                BitmapMeasurementImage(Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)),
            )
        }
        var returned = false
        composeRule.setContent {
            JointSenseTheme {
                CropRouteScreen(
                    viewModel = viewModel,
                    onConfirm = {},
                    onBack = { returned = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle {
            assertTrue(returned)
            assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
            assertEquals(null, viewModel.state.value.image)
        }
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
    fun resultRouteContinueCreatesFreshDraftInTheRealViewModel() {
        val repository = RouteRepository(withResult = true)
        val drafts = sequenceOf("draft-before", "draft-after").iterator()
        lateinit var viewModel: MeasurementViewModel
        composeRule.runOnIdle {
            viewModel = MeasurementViewModel(
                repository = repository,
                analyzer = RouteAnalyzer(),
                draftIdFactory = drafts::next,
                savedStateHandle = SavedStateHandle(),
                decoder = null,
                ioDispatcher = Dispatchers.Main.immediate,
                defaultDispatcher = Dispatchers.Main.immediate,
            )
            viewModel.selectSession(SESSION_ID)
        }
        composeRule.waitUntil { viewModel.state.value.currentSession != null }
        var continued = false
        composeRule.setContent {
            JointSenseTheme {
                ResultRouteScreen(
                    viewModel = viewModel,
                    resultId = RESULT_ID,
                    onContinueMeasurement = {
                        viewModel.startNewTestInSession()
                        continued = true
                    },
                    onReturnToOrigin = {},
                )
            }
        }

        composeRule.onNodeWithTag(CONTINUE_MEASUREMENT_TAG).performScrollTo().performClick()

        composeRule.runOnIdle {
            assertTrue(continued)
            assertEquals("draft-after", viewModel.state.value.draftId)
            assertEquals(Stage.AwaitingImage, viewModel.state.value.stage)
        }
    }

    @Test
    fun cleanupWarningIsVisibleOnResult() {
        composeRule.setContent {
            JointSenseTheme {
                ResultScreen(
                    session = null,
                    lastResult = null,
                    canAddMore = false,
                    cleanupWarning = "Temporary image cleanup failed",
                    onContinueMeasurement = {},
                    onReturnToOrigin = {},
                )
            }
        }

        composeRule.onNodeWithTag(MEASUREMENT_CLEANUP_WARNING_TAG).assertIsDisplayed()
    }

    private fun readyViewModel(
        repository: RouteRepository,
        analyzer: RouteAnalyzer,
    ): MeasurementViewModel {
        lateinit var viewModel: MeasurementViewModel
        composeRule.runOnIdle {
            viewModel = MeasurementViewModel(
                repository = repository,
                analyzer = analyzer,
                draftIdFactory = { "route-draft" },
                savedStateHandle = SavedStateHandle(),
                decoder = null,
                ioDispatcher = Dispatchers.Main.immediate,
                defaultDispatcher = Dispatchers.Main.immediate,
            )
            viewModel.selectSession(SESSION_ID)
            viewModel.setImage(RouteImage)
            viewModel.onAction(MeasurementAction.CropConfirmed)
        }
        composeRule.waitUntil { viewModel.state.value.stage == Stage.ReadyToAnalyze }
        return viewModel
    }
}

private object RouteImage : MeasurementImage {
    override val width = 100
    override val height = 100
}

private class RouteAnalyzer(
    private val blockAnalysis: Boolean = false,
    var failuresRemaining: Int = 0,
) : BaselinePhotoAnalysisAdapter {
    private val gate = CompletableDeferred<Unit>()
    var calls = 0

    fun release() {
        gate.complete(Unit)
    }

    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
        factor: InflammationFactor,
    ): BaselineAnalysisResult {
        calls += 1
        if (failuresRemaining > 0) {
            failuresRemaining -= 1
            error("analysis failed")
        }
        if (blockAnalysis) gate.await()
        return BaselineAnalysisResult(
            concentration = 12f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = TEST_FEATURES,
        )
    }
}

private class RouteRepository(
    blockCommit: Boolean = false,
    withResult: Boolean = false,
) : TestSessionRepository {
    private val commitGate = CompletableDeferred<Unit>().also {
        if (!blockCommit) it.complete(Unit)
    }
    private val sessions = MutableStateFlow(
        listOf(
            TestSession(
                id = SESSION_ID,
                name = "Session",
                createdAt = 1L,
                source = DataSource.USER,
                results = if (withResult) listOf(
                    TestResult(
                        id = RESULT_ID,
                        sessionId = SESSION_ID,
                        draftId = "old-draft",
                        factor = InflammationFactor.IL6,
                        concentration = 12f,
                        rangeStatus = RangeStatus.IN_RANGE,
                        features = TEST_FEATURES,
                        timestamp = 2L,
                    ),
                ) else emptyList(),
            ),
        ),
    )
    var commitCalls = 0

    fun releaseCommit() {
        commitGate.complete(Unit)
    }

    override fun observeSessions(): Flow<List<TestSession>> = sessions

    override fun observeSession(id: String): Flow<TestSession?> =
        sessions.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun createSession(name: String, source: DataSource): String = SESSION_ID

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String {
        commitCalls += 1
        commitGate.await()
        return RESULT_ID
    }

    override suspend fun deleteSession(id: String) = Unit
}

private const val SESSION_ID = "route-session"
private const val RESULT_ID = "route-result"
private val TEST_FEATURES = RgbFeatures(1f, 2f, 3f, 0f, 0f, 0f)
