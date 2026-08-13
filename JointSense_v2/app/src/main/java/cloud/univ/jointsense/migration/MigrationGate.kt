package cloud.univ.jointsense.migration

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import cloud.univ.jointsense.R
import cloud.univ.jointsense.designsystem.component.LoadingErrorState
import cloud.univ.jointsense.data.legacy.LegacyMigrationCoordinator
import cloud.univ.jointsense.data.legacy.MigrationOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface MigrationGateOperations {
    suspend fun migrate(): MigrationOutcome
    suspend fun skipLegacyAndStartFresh(): MigrationOutcome
}

class LegacyMigrationGateOperations(
    private val coordinator: LegacyMigrationCoordinator,
) : MigrationGateOperations {
    override suspend fun migrate(): MigrationOutcome = coordinator.migrate()

    override suspend fun skipLegacyAndStartFresh(): MigrationOutcome =
        coordinator.skipLegacyAndStartFresh()
}

sealed interface MigrationGateState {
    data object NotStarted : MigrationGateState
    data object Migrating : MigrationGateState

    data class Failed(
        val reason: String,
        val canRetry: Boolean = true,
        val canStartEmpty: Boolean = true,
        val isStartEmptyConfirmationVisible: Boolean = false,
    ) : MigrationGateState

    data class Ready(val outcome: MigrationOutcome) : MigrationGateState
}

val MigrationGateState.allowsNavigation: Boolean
    get() = this is MigrationGateState.Ready

class MigrationGateController(
    private val operations: MigrationGateOperations,
) {
    private val mutableState = MutableStateFlow<MigrationGateState>(MigrationGateState.NotStarted)
    val state: StateFlow<MigrationGateState> = mutableState.asStateFlow()

    suspend fun start() {
        if (mutableState.value != MigrationGateState.NotStarted) return
        migrate(previousState = MigrationGateState.NotStarted)
    }

    suspend fun retry() {
        val failed = mutableState.value as? MigrationGateState.Failed ?: return
        migrate(previousState = failed.copy(isStartEmptyConfirmationVisible = false))
    }

    fun requestStartEmpty() {
        val failed = mutableState.value as? MigrationGateState.Failed ?: return
        mutableState.value = failed.copy(isStartEmptyConfirmationVisible = true)
    }

    fun cancelStartEmpty() {
        val failed = mutableState.value as? MigrationGateState.Failed ?: return
        mutableState.value = failed.copy(isStartEmptyConfirmationVisible = false)
    }

    suspend fun confirmStartEmpty() {
        val failed = mutableState.value as? MigrationGateState.Failed ?: return
        if (!failed.isStartEmptyConfirmationVisible) return
        mutableState.value = MigrationGateState.Migrating
        mutableState.value = try {
            operations.skipLegacyAndStartFresh().toGateState()
        } catch (exception: CancellationException) {
            mutableState.value = failed
            throw exception
        } catch (exception: Exception) {
            MigrationGateState.Failed(exception.message ?: exception::class.java.simpleName)
        }
    }

    private suspend fun migrate(previousState: MigrationGateState) {
        mutableState.value = MigrationGateState.Migrating
        mutableState.value = try {
            operations.migrate().toGateState()
        } catch (exception: CancellationException) {
            mutableState.value = previousState
            throw exception
        } catch (exception: Exception) {
            MigrationGateState.Failed(exception.message ?: exception::class.java.simpleName)
        }
    }

    private fun MigrationOutcome.toGateState(): MigrationGateState = when (this) {
        is MigrationOutcome.Failed -> MigrationGateState.Failed(reason)
        is MigrationOutcome.Completed,
        MigrationOutcome.AlreadyCompleted,
        MigrationOutcome.SkippedByUser,
        -> MigrationGateState.Ready(this)
    }
}

/**
 * Lifecycle-aware startup barrier. [content] is not composed until migration
 * has reached one of the three successful terminal outcomes.
 */
@Composable
fun MigrationGate(
    coordinator: LegacyMigrationCoordinator,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val operations = remember(coordinator) { LegacyMigrationGateOperations(coordinator) }
    val controller = remember(operations) { MigrationGateController(operations) }
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val actionScope = rememberCoroutineScope()

    LaunchedEffect(controller, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            controller.start()
        }
    }

    when (val current = state) {
        MigrationGateState.NotStarted,
        MigrationGateState.Migrating,
        -> MigrationProgressScreen(modifier)

        is MigrationGateState.Failed -> MigrationErrorScreen(
            state = current,
            onRetry = { actionScope.launch { controller.retry() } },
            onRequestStartEmpty = controller::requestStartEmpty,
            onCancelStartEmpty = controller::cancelStartEmpty,
            onConfirmStartEmpty = {
                actionScope.launch { controller.confirmStartEmpty() }
            },
            modifier = modifier,
        )

        is MigrationGateState.Ready -> content()
    }
}

@Composable
private fun MigrationProgressScreen(modifier: Modifier = Modifier) {
    LoadingErrorState(
        isLoading = true,
        message = stringResource(R.string.migration_preparing_data),
        actionLabel = null,
        onAction = null,
        modifier = modifier.fillMaxSize(),
    )
}
