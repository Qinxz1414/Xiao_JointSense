package cloud.univ.jointsense.migration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cloud.univ.jointsense.R

@Composable
internal fun MigrationErrorScreen(
    state: MigrationGateState.Failed,
    onRetry: () -> Unit,
    onRequestStartEmpty: () -> Unit,
    onCancelStartEmpty: () -> Unit,
    onConfirmStartEmpty: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.migration_error_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.migration_error_message, state.reason),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            if (state.canRetry) {
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.action_retry))
                }
            }
            if (state.canStartEmpty) {
                OutlinedButton(onClick = onRequestStartEmpty) {
                    Text(stringResource(R.string.migration_start_empty))
                }
            }
        }
    }

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
