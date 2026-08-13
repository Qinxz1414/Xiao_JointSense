package cloud.univ.jointsense.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cloud.univ.jointsense.feature.settings.R

@Composable
internal fun DataManagementDialogs(
    action: DataAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onConsumeResult: () -> Unit,
) {
    when (action) {
        DataAction.Idle -> Unit
        is DataAction.Pending -> ConfirmationDialog(action.type, onDismiss, onConfirm)
        is DataAction.Running -> RunningDialog(action.type)
        is DataAction.Completed -> ResultDialog(
            type = action.type,
            success = true,
            onDismiss = onConsumeResult,
            onAction = onConsumeResult,
        )
        is DataAction.Error -> ResultDialog(
            type = action.type,
            success = false,
            onDismiss = onDismiss,
            onAction = onRetry,
        )
    }
}

@Composable
private fun ConfirmationDialog(
    type: DataActionType,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val clear = type == DataActionType.CLEAR_ALL
    AlertDialog(
        modifier = Modifier.testTag(
            if (clear) CONFIRM_CLEAR_ALL_TAG else CONFIRM_RESTORE_SAMPLES_TAG,
        ),
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (clear) R.string.settings_clear_title
                    else R.string.settings_restore_samples_title,
                ),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (clear) {
                    Text(stringResource(R.string.settings_clear_scope))
                } else {
                    Text(stringResource(R.string.settings_restore_samples_message))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.settings_restore_samples_calibration_note))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(DATA_DIALOG_CONFIRM_TAG),
            ) {
                Text(
                    text = stringResource(
                        if (clear) R.string.settings_delete
                        else R.string.settings_restore_samples_confirm,
                    ),
                    color = if (clear) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(DATA_DIALOG_DISMISS_TAG),
            ) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun RunningDialog(type: DataActionType) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                stringResource(
                    if (type == DataActionType.CLEAR_ALL) R.string.settings_clear_title
                    else R.string.settings_restore_samples_title,
                ),
            )
        },
        text = {
            Column {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_data_action_running))
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun ResultDialog(
    type: DataActionType,
    success: Boolean,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    val clear = type == DataActionType.CLEAR_ALL
    val title = when {
        clear && success -> R.string.settings_clear_success_title
        clear -> R.string.settings_clear_failure_title
        success -> R.string.settings_restore_samples_success_title
        else -> R.string.settings_restore_samples_failure_title
    }
    val message = when {
        clear && success -> R.string.settings_clear_success_message
        clear -> R.string.settings_clear_failure_message
        success -> R.string.settings_restore_samples_success_message
        else -> R.string.settings_restore_samples_failure_message
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onAction) {
                Text(stringResource(if (success) R.string.settings_ok else R.string.settings_retry))
            }
        },
        dismissButton = if (success) null else {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        },
    )
}

const val CONFIRM_RESTORE_SAMPLES_TAG = "confirm_restore_samples"
const val CONFIRM_CLEAR_ALL_TAG = "confirm_clear_all"
const val DATA_DIALOG_CONFIRM_TAG = "data_dialog_confirm"
const val DATA_DIALOG_DISMISS_TAG = "data_dialog_dismiss"

// Preserved for the Task 4 production navigation integration assertion.
const val RESTORE_SAMPLES_CONFIRMATION_TAG = CONFIRM_RESTORE_SAMPLES_TAG
