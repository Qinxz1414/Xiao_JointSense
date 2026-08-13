package cloud.univ.jointsense.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
) {
    if (!isLoading && message == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
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
    }
}
