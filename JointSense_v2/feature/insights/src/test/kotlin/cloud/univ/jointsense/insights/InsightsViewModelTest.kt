package cloud.univ.jointsense.insights

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repositoryFlowDerivesDashboardTrendAndReportState() = runTest(dispatcher) {
        val repository = FakeTestSessionRepository(listOf(completeSession()))
        val viewModel = InsightsViewModel(repository, clock = { NOW })

        viewModel.homeState.collectIn(backgroundScope)
        viewModel.trendsState.collectIn(backgroundScope)
        viewModel.reportState.collectIn(backgroundScope)
        testScheduler.advanceUntilIdle()

        assertEquals(3, viewModel.homeState.value.allResults.size)
        assertEquals(500f, viewModel.homeState.value.latestValues.getValue(InflammationFactor.TNF_ALPHA))
        assertEquals(1f, viewModel.homeState.value.currentAi)
        assertEquals(4, viewModel.homeState.value.currentGrade)
        assertEquals(1, viewModel.trendsState.value.aiSeries.size)
        assertEquals(3, viewModel.trendsState.value.factorSeries.values.sumOf { it.size })
        assertEquals(1f, viewModel.reportState.value.currentAi)
        assertEquals(4, viewModel.reportState.value.currentGrade)
    }

    @Test
    fun everyDerivedStateUpdatesWhenRepositoryEmits() = runTest(dispatcher) {
        val repository = FakeTestSessionRepository(emptyList())
        val viewModel = InsightsViewModel(repository, clock = { NOW })
        viewModel.homeState.collectIn(backgroundScope)
        viewModel.trendsState.collectIn(backgroundScope)
        viewModel.reportState.collectIn(backgroundScope)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.homeState.value.allResults.isEmpty())

        repository.sessions.value = listOf(completeSession())
        testScheduler.advanceUntilIdle()

        assertEquals(3, viewModel.homeState.value.allResults.size)
        assertEquals(1, viewModel.trendsState.value.aiSeries.size)
        assertEquals(3, viewModel.reportState.value.latestValues.size)
    }

    private fun completeSession(): TestSession = TestSession(
        id = "session",
        name = "Complete",
        createdAt = NOW - 1_000,
        source = DataSource.USER,
        results = listOf(
            result("tnf", InflammationFactor.TNF_ALPHA, 500f),
            result("il6", InflammationFactor.IL6, 1_000f),
            result("il1", InflammationFactor.IL1_BETA, 500f),
        ),
    )

    private fun result(id: String, factor: InflammationFactor, concentration: Float) = TestResult(
        id = id,
        sessionId = "session",
        draftId = null,
        factor = factor,
        concentration = concentration,
        rangeStatus = RangeStatus.IN_RANGE,
        features = RgbFeatures(1f, 2f, 3f, 4f, 5f, 6f),
        timestamp = NOW - 500,
    )

    private companion object {
        const val NOW = 2_000_000_000_000L
    }
}

private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectIn(
    scope: kotlinx.coroutines.CoroutineScope,
) = scope.launch { collect {} }

private class FakeTestSessionRepository(initial: List<TestSession>) : TestSessionRepository {
    val sessions = MutableStateFlow(initial)

    override fun observeSessions(): Flow<List<TestSession>> = sessions
    override fun observeSession(id: String): Flow<TestSession?> =
        MutableStateFlow(sessions.value.firstOrNull { it.id == id })

    override suspend fun createSession(name: String, source: DataSource): String = error("unused")
    override suspend fun commitResult(sessionId: String, draftId: String, result: NewTestResult): String = error("unused")
    override suspend fun deleteSession(id: String) = error("unused")
}
