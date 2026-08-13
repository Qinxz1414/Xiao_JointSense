package cloud.univ.jointsense.settings

import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import cloud.univ.jointsense.domain.repository.DataManagementRepository
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import kotlinx.coroutines.CompletableDeferred
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
class DataActionStateMachineTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun duplicateClearAndRestoreConfirmationsEachCallTheRepositoryOnce() = runTest(dispatcher) {
        val data = ControllableDataManagementRepository()
        val viewModel = viewModel(data)
        val collection = backgroundScope.launch { viewModel.state.collect {} }

        viewModel.requestClearAllConfirmation()
        repeat(20) { viewModel.confirmDataAction() }
        testScheduler.advanceUntilIdle()
        assertEquals(1, data.clearCalls)
        assertEquals(DataAction.Completed(DataActionType.CLEAR_ALL), viewModel.state.value.dataAction)

        viewModel.consumeDataActionResult()
        viewModel.requestRestoreBuiltInSamplesConfirmation()
        repeat(20) { viewModel.confirmDataAction() }
        testScheduler.advanceUntilIdle()
        assertEquals(1, data.restoreCalls)
        assertEquals(DataAction.Completed(DataActionType.RESTORE_BUILT_IN_SAMPLES), viewModel.state.value.dataAction)
        collection.cancel()
    }

    @Test
    fun runningActionBlocksCompetingRequestsAndCancellationIsNotConvertedToFailure() = runTest(dispatcher) {
        val data = ControllableDataManagementRepository(blockClear = true)
        val viewModel = viewModel(data)
        val collection = backgroundScope.launch { viewModel.state.collect {} }

        viewModel.requestClearAllConfirmation()
        viewModel.confirmDataAction()
        testScheduler.runCurrent()
        assertEquals(DataAction.Running(DataActionType.CLEAR_ALL), viewModel.state.value.dataAction)

        viewModel.requestRestoreBuiltInSamplesConfirmation()
        assertEquals(DataAction.Running(DataActionType.CLEAR_ALL), viewModel.state.value.dataAction)

        data.clearGate.cancel()
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.dataAction is DataAction.Idle)
        collection.cancel()
    }

    @Test
    fun failureCanRetryWithoutASecondConfirmationAndStillSuppressesDuplicates() = runTest(dispatcher) {
        val data = ControllableDataManagementRepository(failRestoreCalls = 1)
        val viewModel = viewModel(data)
        val collection = backgroundScope.launch { viewModel.state.collect {} }

        viewModel.requestRestoreBuiltInSamplesConfirmation()
        viewModel.confirmDataAction()
        testScheduler.advanceUntilIdle()
        assertEquals(DataAction.Error(DataActionType.RESTORE_BUILT_IN_SAMPLES), viewModel.state.value.dataAction)

        repeat(20) { viewModel.retryDataAction() }
        testScheduler.advanceUntilIdle()
        assertEquals(2, data.restoreCalls)
        assertEquals(DataAction.Completed(DataActionType.RESTORE_BUILT_IN_SAMPLES), viewModel.state.value.dataAction)
        collection.cancel()
    }

    private fun viewModel(data: DataManagementRepository) = SettingsViewModel(
        EmptySessionRepository,
        EmptyCalibrationRepository,
        data,
    )
}

private class ControllableDataManagementRepository(
    blockClear: Boolean = false,
    private var failRestoreCalls: Int = 0,
) : DataManagementRepository {
    var clearCalls = 0
    var restoreCalls = 0
    val clearGate = CompletableDeferred<Unit>().also { if (!blockClear) it.complete(Unit) }

    override suspend fun clearAllData() {
        clearCalls += 1
        clearGate.await()
    }

    override suspend fun restoreBuiltInSamples() {
        restoreCalls += 1
        if (failRestoreCalls > 0) {
            failRestoreCalls -= 1
            error("restore failed")
        }
    }
}

private object EmptySessionRepository : TestSessionRepository {
    override fun observeSessions(): Flow<List<TestSession>> = MutableStateFlow(emptyList())
    override fun observeSession(id: String): Flow<TestSession?> = MutableStateFlow(null)
    override suspend fun createSession(name: String, source: DataSource): String = error("unused")
    override suspend fun commitResult(sessionId: String, draftId: String, result: NewTestResult): String = error("unused")
    override suspend fun deleteSession(id: String) = error("unused")
}

private object EmptyCalibrationRepository : CalibrationRepository {
    override fun observeCalibrations(): Flow<List<Calibration>> = MutableStateFlow(emptyList())
    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> = MutableStateFlow(null)
    override suspend fun save(calibration: Calibration) = error("unused")
    override suspend fun clearAll() = error("unused")
}
