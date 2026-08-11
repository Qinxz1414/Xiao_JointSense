package cloud.univ.jointsense.calibration

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.domain.model.Calibration
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

    private fun viewModel(
        repository: AndroidCalibrationRepository = AndroidCalibrationRepository(),
    ) = CalibrationViewModel(
        repository = repository,
        savedStateHandle = SavedStateHandle(),
        decoder = null,
        legacyRevalidator = null,
        ioDispatcher = Dispatchers.Main.immediate,
        defaultDispatcher = Dispatchers.Main.immediate,
    )
}

private class AndroidCalibrationRepository : CalibrationRepository {
    private val calibrations = MutableStateFlow<List<Calibration>>(emptyList())
    var clearCalls = 0

    override fun observeCalibrations(): Flow<List<Calibration>> = calibrations
    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        MutableStateFlow(calibrations.value.firstOrNull { it.factor == factor })

    override suspend fun save(calibration: Calibration) {
        calibrations.value = calibrations.value.filterNot { it.factor == calibration.factor } + calibration
    }

    override suspend fun clearAll() {
        clearCalls += 1
        calibrations.value = emptyList()
    }
}
