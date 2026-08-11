package cloud.univ.jointsense.calibration

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalibrationFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fiveRouteEntriesRenderDistinctStepContent() {
        val viewModel = viewModel()
        val routeIndex = mutableIntStateOf(0)
        val expectedTags = listOf(
            "calibration:select",
            "calibration:crop",
            "calibration:assign",
            "calibration:review",
            "calibration:done",
        )
        composeRule.setContent {
            JointSenseTheme {
                when (routeIndex.intValue) {
                    0 -> CalibrationSelectRouteScreen(viewModel, {}, {})
                    1 -> CalibrationCropRouteScreen(viewModel, {}, {})
                    2 -> CalibrationAssignRouteScreen(viewModel, {}, {})
                    3 -> CalibrationReviewRouteScreen(viewModel, {}, {})
                    else -> CalibrationDoneRouteScreen(viewModel, {}, {}, {})
                }
            }
        }

        expectedTags.forEachIndexed { index, expectedTag ->
            composeRule.runOnUiThread { routeIndex.intValue = index }
            composeRule.onNodeWithTag(expectedTag).assertIsDisplayed()
        }
    }

    @Test
    fun restoreFactoryRequiresConfirmationBeforeClearingRepository() {
        val repository = AndroidCalibrationRepository()
        val viewModel = viewModel(repository)
        composeRule.setContent {
            JointSenseTheme {
                CalibrationDoneRouteScreen(viewModel, onDone = {}, onAnother = {}, onBack = {})
            }
        }

        composeRule.onNodeWithText("Restore factory curve").performClick()
        composeRule.onNodeWithText("Restore factory curves?").assertIsDisplayed()
        assertEquals(0, repository.clearCalls)

        composeRule.onNodeWithText("Restore", substring = false).performClick()
        composeRule.waitForIdle()

        assertEquals(1, repository.clearCalls)
        composeRule.onAllNodesWithText("Restore factory curves?").assertCountEquals(0)
    }

    @Test
    fun legacyFailureShowsRetryAndExplicitRetryPromotesRecord() {
        val repository = AndroidCalibrationRepository(listOf(needsReviewCalibration()))
        var fail = true
        val validator = cloud.univ.jointsense.analysis.calibration.CalibrationValidator()
        val revalidator = LegacyCalibrationRevalidator(repository) { inputs ->
            if (fail) error("temporary legacy failure")
            validator.validate(inputs)
        }
        val viewModel = viewModel(repository, revalidator)
        composeRule.setContent {
            JointSenseTheme {
                CalibrationSelectRouteScreen(viewModel, onImageReady = {}, onBack = {})
            }
        }
        composeRule.waitUntil { viewModel.state.value.legacyRevalidationSummary?.failures?.size == 1 }
        composeRule.onNodeWithText("Retry legacy review").assertIsDisplayed()

        fail = false
        composeRule.onNodeWithText("Retry legacy review").performClick()
        composeRule.waitUntil { repository.savedCount == 1 }
        composeRule.onAllNodesWithText("Retry legacy review").assertCountEquals(0)
    }

    private fun viewModel(
        repository: AndroidCalibrationRepository = AndroidCalibrationRepository(),
        legacyRevalidator: LegacyCalibrationRevalidator? = null,
    ) = CalibrationViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(),
        decoder = null,
        legacyRevalidator = legacyRevalidator,
        ioDispatcher = Dispatchers.Main.immediate,
        defaultDispatcher = Dispatchers.Main.immediate,
    )

    private fun needsReviewCalibration(): Calibration {
        val concentrations = FACTORY_LADDER.getValue(InflammationFactor.TNF_ALPHA)
        val signals = listOf(10f, 12f, 15f, 18f, 22f, 28f, 36f, 46f, 58f)
        return Calibration(
            factor = InflammationFactor.TNF_ALPHA,
            createdAt = 1L,
            version = 1,
            status = CalibrationStatus.NEEDS_REVIEW,
            kitName = null,
            kitLot = null,
            knots = signals.mapIndexed { index, signal ->
                CalibrationKnot(
                    position = index,
                    concentration = concentrations[index],
                    rawSignal = signal,
                    netSignal = signal,
                    fittedSignal = signal,
                    isBlank = index == 0,
                )
            },
        )
    }
}

private class AndroidCalibrationRepository(initial: List<Calibration> = emptyList()) : CalibrationRepository {
    private val calibrations = MutableStateFlow(initial)
    var clearCalls = 0
    var savedCount = 0

    override fun observeCalibrations(): Flow<List<Calibration>> = calibrations
    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        MutableStateFlow(calibrations.value.firstOrNull { it.factor == factor })

    override suspend fun save(calibration: Calibration) {
        savedCount += 1
        calibrations.value = calibrations.value.filterNot { it.factor == calibration.factor } + calibration
    }

    override suspend fun clearAll() {
        clearCalls += 1
        calibrations.value = emptyList()
    }
}
