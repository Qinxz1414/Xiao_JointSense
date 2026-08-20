package cloud.univ.jointsense.migration

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cloud.univ.jointsense.R
import cloud.univ.jointsense.designsystem.component.LoadingErrorState

@Composable
internal fun MigrationErrorScreen(
    state: MigrationGateState.Failed,
    onRetry: () -> Unit,
    onRequestStartEmpty: () -> Unit,
    onCancelStartEmpty: () -> Unit,
    onConfirmStartEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LoadingErrorState(
        isLoading = false,
        headline = stringResource(R.string.migration_error_title),
        message = stringResource(R.string.migration_error_message),
        actionLabel = if (state.canRetry) stringResource(R.string.action_retry) else null,
        onAction = if (state.canRetry) onRetry else null,
        actionModifier = Modifier.fillMaxWidth(),
        secondaryActionLabel = if (state.canStartEmpty) {
            stringResource(R.string.migration_start_empty)
        } else {
            null
        },
        onSecondaryAction = if (state.canStartEmpty) onRequestStartEmpty else null,
        secondaryActionModifier = Modifier.fillMaxWidth(),
        modifier = modifier.fillMaxSize(),
    )

    if (state.isStartEmptyConfirmationVisible) {
        AlertDialog(
            onDismissRequest = onCancelStartEmpty,
            title = { Text(stringResource(R.string.migration_start_empty_confirm_title)) },
            text = { Text(stringResource(R.string.migration_start_empty_confirm_message)) },
            confirmButton = {
                Button(onClick = onConfirmStartEmpty) {
                    Text(stringResource(R.string.migration_start_empty_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelStartEmpty) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
