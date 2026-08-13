package cloud.univ.jointsense.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LoadingErrorState(
    isLoading: Boolean,
    headline: String? = null,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    progressModifier: Modifier = Modifier,
    actionModifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    secondaryActionModifier: Modifier = Modifier,
) {
    if (!isLoading && headline == null && message == null && actionLabel == null && secondaryActionLabel == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
            }
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = progressModifier)
        }
        headline?.let {
            Text(text = it, style = MaterialTheme.typography.headlineSmall)
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLoading) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (!isLoading && actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = actionModifier) { Text(text = actionLabel) }
        }
        if (!isLoading && secondaryActionLabel != null && onSecondaryAction != null) {
            OutlinedButton(
                onClick = onSecondaryAction,
                modifier = secondaryActionModifier,
            ) {
                Text(text = secondaryActionLabel)
            }
        }
    }
}
