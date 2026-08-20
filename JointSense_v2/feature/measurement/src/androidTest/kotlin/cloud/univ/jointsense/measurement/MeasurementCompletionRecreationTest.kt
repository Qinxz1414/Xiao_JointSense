package cloud.univ.jointsense.measurement

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewMeasurementBatch
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementCompletionRecreationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedCommitIsDeliveredOnceWhenAnalysisRouteCollectorIsRecreated() {
        val repository = RecreationRepository()
        val viewModel = MeasurementViewModel(repository, RecreationAnalyzer()) { "draft-1" }
        val collectorAttached = mutableStateOf(true)
        val deliveredIds = mutableListOf<String>()

        composeRule.setContent {
            if (collectorAttached.value) {
                TriplexAnalysisRouteScreen(
                    viewModel = viewModel,
                    onResultReady = deliveredIds::add,
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { collectorAttached.value = false }
        composeRule.waitForIdle()

        composeRule.runOnIdle { viewModel.createNewSession("test-origin", "Test") }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.sessionCreationRequest?.completedSessionId != null
        }
        composeRule.runOnIdle {
            val request = requireNotNull(viewModel.state.value.sessionCreationRequest)
            requireNotNull(viewModel.acceptSessionCreation(request.requestId))
            viewModel.setImage(RecreationImage(width = 800, height = 600))
            viewModel.onAction(MeasurementAction.CropConfirmed)
            viewModel.analyze()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { repository.commitCount == 1 }
        assertEquals(emptyList<String>(), deliveredIds)

        composeRule.runOnIdle { collectorAttached.value = true }
        composeRule.waitUntil(timeoutMillis = 5_000) { deliveredIds.size == 1 }
        assertEquals(listOf("result-1"), deliveredIds)

        composeRule.runOnIdle { collectorAttached.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { collectorAttached.value = true }
        composeRule.waitForIdle()
        assertEquals(listOf("result-1"), deliveredIds)
    }
}

private data class RecreationImage(
    override val width: Int,
    override val height: Int,
) : MeasurementImage

private class RecreationAnalyzer : BaselinePhotoAnalysisAdapter {
    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
    ) = cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder.map { factor ->
        BaselineAnalysisResult(
            factor = factor,
            concentration = 42f,
            rangeStatus = RangeStatus.UNKNOWN,
            features = RgbFeatures(10f, 20f, 30f, 1f, 2f, 3f),
            rawSignal = 20f,
            signalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
        )
    }
}

private class RecreationRepository : TestSessionRepository {
    val sessions = MutableStateFlow<List<TestSession>>(emptyList())
    var commitCount = 0
        private set

    override fun observeSessions(): Flow<List<TestSession>> = sessions

    override fun observeSession(id: String): Flow<TestSession?> = MutableStateFlow(
        sessions.value.firstOrNull { it.id == id },
    )

    override suspend fun createSession(name: String, source: DataSource): String {
        sessions.value = sessions.value + TestSession(
            id = "session-1",
            name = name,
            createdAt = 1L,
            source = source,
            results = emptyList(),
        )
        return "session-1"
    }

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String {
        commitCount += 1
        val stored = TestResult(
            id = "result-1",
            sessionId = sessionId,
            draftId = draftId,
            factor = result.factor,
            concentration = result.concentration,
            rangeStatus = result.rangeStatus,
            features = result.features,
            timestamp = result.timestamp,
        )
        sessions.value = sessions.value.map { session ->
            if (session.id == sessionId) session.copy(results = listOf(stored)) else session
        }
        return stored.id
    }

    override suspend fun commitMeasurement(
        sessionId: String,
        draftId: String,
        measurement: NewMeasurementBatch,
    ): String {
        commitCount += 1
        val batchId = "result-1"
        val stored = measurement.results.mapIndexed { index, result ->
            TestResult(
                id = "$batchId-$index",
                sessionId = sessionId,
                draftId = null,
                factor = result.factor,
                concentration = result.concentration,
                rangeStatus = result.rangeStatus,
                features = result.features,
                timestamp = measurement.timestamp,
                measurementBatchId = batchId,
            )
        }
        sessions.value = sessions.value.map { session ->
            if (session.id == sessionId) session.copy(results = stored) else session
        }
        return batchId
    }

    override suspend fun deleteSession(id: String) {
        sessions.value = sessions.value.filterNot { it.id == id }
    }
}
