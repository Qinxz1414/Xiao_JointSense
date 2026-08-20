package cloud.univ.jointsense.navigation

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import cloud.univ.jointsense.measurement.BaselineAnalysisResult
import cloud.univ.jointsense.measurement.BaselinePhotoAnalysisAdapter
import cloud.univ.jointsense.measurement.CropBounds
import cloud.univ.jointsense.measurement.MeasurementImage
import cloud.univ.jointsense.measurement.MeasurementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCreationNavigationLifecycleTest {
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
    fun trendsBackToHomeBeforeInsertNeverNavigatesAndDeletesLateSession() = runTest(dispatcher) {
        val repository = DelayedSessionRepository()
        val viewModel = MeasurementViewModel(repository, UnusedAnalyzer)
        val navigation = LifecycleStackNavigationDriver(mutableListOf(HomeRoute))
        val actions = NavigationActions(navigation)
        val sessionDriver = SessionCreationNavigationDriver(viewModel, actions)
        actions.openTopLevel(TopLevelDestination.TRENDS)

        sessionDriver.request(TopLevelDestination.TRENDS, "Test")
        runCurrent()
        actions.navigateBack()
        sessionDriver.synchronize(TopLevelDestination.HOME)
        assertEquals(listOf(HomeRoute), navigation.stack)

        repository.completeCreate("late-session")
        advanceUntilIdle()
        sessionDriver.synchronize(TopLevelDestination.HOME)

        assertEquals(listOf(HomeRoute), navigation.stack)
        assertEquals(listOf("late-session"), repository.deletedIds)
        assertEquals(null, viewModel.state.value.currentSession)
    }

    @Test
    fun recreationCompletionNavigatesOnlyTheCurrentOwnerExactlyOnce() = runTest(dispatcher) {
        val repository = DelayedSessionRepository()
        val viewModel = MeasurementViewModel(repository, UnusedAnalyzer)
        val oldNavigation = LifecycleStackNavigationDriver(
            mutableListOf(HomeRoute, TrendsRoute),
        )
        val oldActions = NavigationActions(oldNavigation)
        val oldOwner = SessionCreationNavigationDriver(viewModel, oldActions)

        oldOwner.request(TopLevelDestination.TRENDS, "Test")
        runCurrent()

        val currentNavigation = LifecycleStackNavigationDriver(
            mutableListOf(HomeRoute, TrendsRoute),
        )
        val currentOwner = SessionCreationNavigationDriver(
            viewModel,
            NavigationActions(currentNavigation),
        )
        currentOwner.synchronize(currentOrigin = null, routeReady = false)
        assertNotNull(viewModel.state.value.sessionCreationRequest)
        repository.completeCreate("created-session")
        advanceUntilIdle()

        assertEquals(0, oldNavigation.measurementStarts)
        currentOwner.synchronize(currentOrigin = null, routeReady = false)
        assertNotNull(viewModel.state.value.sessionCreationRequest)
        currentOwner.synchronize(TopLevelDestination.TRENDS, routeReady = true)
        currentOwner.synchronize(TopLevelDestination.TRENDS, routeReady = true)
        assertEquals(1, currentNavigation.measurementStarts)
        assertEquals(
            listOf(HomeRoute, TrendsRoute, MeasurementGraph(TopLevelDestination.TRENDS), ImageSelectRoute),
            currentNavigation.stack,
        )
    }

    @Test
    fun retainedDriverUsesThePrefixFromEachRequest() = runTest(dispatcher) {
        val repository = DelayedSessionRepository()
        val viewModel = MeasurementViewModel(repository, UnusedAnalyzer)
        val navigation = LifecycleStackNavigationDriver(mutableListOf(HomeRoute))
        val sessionDriver = SessionCreationNavigationDriver(viewModel, NavigationActions(navigation))

        sessionDriver.request(TopLevelDestination.HOME, "Test")
        repository.completeCreate("english-session")
        advanceUntilIdle()
        sessionDriver.synchronize(TopLevelDestination.HOME)

        sessionDriver.request(TopLevelDestination.HOME, "检测")
        repository.completeCreate("chinese-session")
        advanceUntilIdle()

        assertEquals(listOf("Test #1", "检测 #1"), repository.sessions.value.map(TestSession::name))
    }
}

private class DelayedSessionRepository : TestSessionRepository {
    private val createResults = Channel<String>(Channel.UNLIMITED)
    val sessions = MutableStateFlow<List<TestSession>>(emptyList())
    val deletedIds = mutableListOf<String>()

    fun completeCreate(id: String) {
        check(createResults.trySend(id).isSuccess)
    }

    override fun observeSessions(): Flow<List<TestSession>> = sessions

    override fun observeSession(id: String): Flow<TestSession?> = MutableStateFlow(
        sessions.value.firstOrNull { it.id == id },
    )

    override suspend fun createSession(name: String, source: DataSource): String {
        val id = createResults.receive()
        sessions.value = sessions.value + TestSession(
            id = id,
            name = name,
            createdAt = 1L,
            source = source,
            results = emptyList(),
        )
        return id
    }

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String = error("unused")

    override suspend fun commitMeasurement(
        sessionId: String,
        draftId: String,
        measurement: cloud.univ.jointsense.domain.model.NewMeasurementBatch,
    ): String = error("unused")

    override suspend fun deleteSession(id: String) {
        deletedIds += id
        sessions.value = sessions.value.filterNot { it.id == id }
    }
}

private object UnusedAnalyzer : BaselinePhotoAnalysisAdapter {
    override suspend fun analyze(
        image: MeasurementImage,
        cropBounds: CropBounds,
    ): List<BaselineAnalysisResult> = error("unused")
}

private class LifecycleStackNavigationDriver(
    val stack: MutableList<NavigationTarget>,
) : NavigationDriver {
    var measurementStarts = 0
        private set

    override fun navigate(target: NavigationTarget, launchSingleTop: Boolean) {
        if (!launchSingleTop || stack.lastOrNull() != target) stack += target
        if (target is MeasurementGraph) {
            measurementStarts += 1
            stack += ImageSelectRoute
        }
    }

    override fun popOne(): Boolean = if (stack.size > 1) {
        stack.removeLast()
        true
    } else {
        false
    }

    override fun popMeasurement(): Boolean = false

    override fun popToImageSelect(): Boolean = false

    override fun popCalibration(): Boolean = false

    override fun popToHome() {
        while (stack.size > 1) stack.removeLast()
    }

    override fun containsMeasurement(): Boolean = stack.any { it is MeasurementGraph }
}
