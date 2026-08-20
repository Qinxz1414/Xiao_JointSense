package cloud.univ.jointsense.migration

import cloud.univ.jointsense.data.legacy.MigrationOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationGateControllerTest {
    @Test
    fun failedMigrationHidesNavigationAndOffersBothRecoveryActions() = runTest {
        val controller = MigrationGateController(
            FakeMigrationGateOperations(MigrationOutcome.Failed("broken payload")),
        )

        controller.start()

        val failed = assertType<MigrationGateState.Failed>(controller.state.value)
        assertEquals("broken payload", failed.reason)
        assertTrue(failed.canRetry)
        assertTrue(failed.canStartEmpty)
        assertFalse(failed.isStartEmptyConfirmationVisible)
        assertFalse(controller.state.value.allowsNavigation)
    }

    @Test
    fun retryRerunsMigrationAndOnlyThenMakesNavigationReady() = runTest {
        val operations = FakeMigrationGateOperations(
            MigrationOutcome.Failed("first attempt"),
            MigrationOutcome.Completed(sessions = 2, results = 3, calibrations = 1),
        )
        val controller = MigrationGateController(operations)
        controller.start()
        assertFalse(controller.state.value.allowsNavigation)

        controller.retry()

        assertEquals(2, operations.migrateCalls)
        assertType<MigrationGateState.Ready>(controller.state.value)
        assertTrue(controller.state.value.allowsNavigation)
    }

    @Test
    fun startEmptyRequiresConfirmationBeforeCallingAtomicSkip() = runTest {
        val operations = FakeMigrationGateOperations(MigrationOutcome.Failed("cannot import"))
        val controller = MigrationGateController(operations)
        controller.start()

        controller.requestStartEmpty()

        assertEquals(0, operations.skipCalls)
        val confirming = assertType<MigrationGateState.Failed>(controller.state.value)
        assertTrue(confirming.isStartEmptyConfirmationVisible)
        assertFalse(confirming.allowsNavigation)

        controller.confirmStartEmpty()

        assertEquals(1, operations.skipCalls)
        assertEquals(
            MigrationGateState.Ready(MigrationOutcome.SkippedByUser),
            controller.state.value,
        )
        assertTrue(controller.state.value.allowsNavigation)
    }

    @Test
    fun cancellingStartEmptyConfirmationReturnsToRecoverableFailure() = runTest {
        val operations = FakeMigrationGateOperations(MigrationOutcome.Failed("cannot import"))
        val controller = MigrationGateController(operations)
        controller.start()
        controller.requestStartEmpty()

        controller.cancelStartEmpty()

        val failed = assertType<MigrationGateState.Failed>(controller.state.value)
        assertFalse(failed.isStartEmptyConfirmationVisible)
        assertEquals(0, operations.skipCalls)
    }

    @Test
    fun completedAlreadyCompletedAndSkippedAreTheOnlyReadyOutcomes() = runTest {
        val readyOutcomes = listOf(
            MigrationOutcome.Completed(sessions = 0, results = 0, calibrations = 0),
            MigrationOutcome.AlreadyCompleted,
            MigrationOutcome.SkippedByUser,
        )

        readyOutcomes.forEach { outcome ->
            val controller = MigrationGateController(FakeMigrationGateOperations(outcome))
            controller.start()

            assertEquals(MigrationGateState.Ready(outcome), controller.state.value)
            assertTrue(controller.state.value.allowsNavigation)
        }

        assertFalse(MigrationGateState.NotStarted.allowsNavigation)
        assertFalse(MigrationGateState.Migrating.allowsNavigation)
        assertFalse(MigrationGateState.Failed("nope").allowsNavigation)
    }
}

private inline fun <reified T> assertType(value: Any?): T {
    assertTrue("Expected ${T::class.java.simpleName}, was ${value?.javaClass?.simpleName}", value is T)
    return value as T
}

private class FakeMigrationGateOperations(
    vararg outcomes: MigrationOutcome,
) : MigrationGateOperations {
    private val outcomes = ArrayDeque(outcomes.toList())

    var migrateCalls: Int = 0
        private set

    var skipCalls: Int = 0
        private set

    override suspend fun migrate(): MigrationOutcome {
        migrateCalls += 1
        return outcomes.removeFirst()
    }

    override suspend fun skipLegacyAndStartFresh(): MigrationOutcome {
        skipCalls += 1
        return MigrationOutcome.SkippedByUser
    }
}
