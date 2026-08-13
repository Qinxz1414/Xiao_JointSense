package cloud.univ.jointsense.measurement

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import cloud.univ.jointsense.designsystem.component.LoadingErrorState
import cloud.univ.jointsense.feature.measurement.R

@Composable
fun MeasurementProgressContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    LoadingErrorState(
        isLoading = true,
        message = message,
        actionLabel = null,
        onAction = null,
        modifier = modifier.fillMaxSize(),
        progressModifier = Modifier.testTag(MEASUREMENT_PROGRESS_TAG),
    )
}

@Composable
fun MeasurementErrorContent(
    error: MeasurementError,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val permission = error as? MeasurementError.PermissionDenied
    val message = when (error) {
        is MeasurementError.PermissionDenied -> if (error.permanentlyDenied) {
            stringResource(R.string.measurement_error_camera_disabled)
        } else {
            stringResource(R.string.measurement_error_camera_required)
        }
        MeasurementError.PermissionHistoryUnavailable ->
            stringResource(R.string.measurement_error_permission_history)
        is MeasurementError.PermissionLaunchFailed ->
            stringResource(R.string.measurement_error_permission_launch)
        MeasurementError.ImageUnreadable -> stringResource(R.string.measurement_error_image_unreadable)
        MeasurementError.UnsupportedImage -> stringResource(R.string.measurement_error_image_unsupported)
        MeasurementError.ImageTooLarge -> stringResource(R.string.measurement_error_image_too_large)
        MeasurementError.InvalidCrop -> stringResource(R.string.measurement_error_invalid_crop)
        MeasurementError.AnalysisFailed -> stringResource(R.string.measurement_error_analysis)
        MeasurementError.PersistenceFailed -> stringResource(R.string.measurement_error_persistence)
        is MeasurementError.CameraLaunchFailed ->
            stringResource(R.string.measurement_error_camera_launch)
    }
    LoadingErrorState(
        isLoading = false,
        headline = stringResource(R.string.measurement_interrupted),
        message = message,
        actionLabel = stringResource(R.string.measurement_action_retry),
        onAction = onRetry,
        actionModifier = Modifier.fillMaxWidth().testTag(RETRY_BUTTON_TAG),
        secondaryActionLabel = if (permission?.permanentlyDenied == true) stringResource(R.string.measurement_action_open_settings) else null,
        onSecondaryAction = if (permission?.permanentlyDenied == true) onOpenSettings else null,
        secondaryActionModifier = Modifier.fillMaxWidth().testTag("open_settings"),
        modifier = modifier.fillMaxSize().testTag(MEASUREMENT_ERROR_TAG),
    )
}
