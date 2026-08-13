package cloud.univ.jointsense.measurement

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import cloud.univ.jointsense.designsystem.component.LoadingErrorState

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
            "Camera permission is disabled. Open system settings to allow camera access."
        } else {
            "Camera permission is required to take a measurement photo."
        }
        MeasurementError.PermissionHistoryUnavailable ->
            "Camera permission status could not be loaded. Retry before taking a photo."
        is MeasurementError.PermissionLaunchFailed ->
            "Camera permission could not be requested. ${error.reason} Retry when permission prompts are available."
        MeasurementError.ImageUnreadable -> "The image could not be read. Choose another image and try again."
        MeasurementError.UnsupportedImage -> "This image format is not supported. Choose a JPEG, PNG, or HEIF image."
        MeasurementError.ImageTooLarge -> "The image is too large to process safely. Choose a smaller image."
        MeasurementError.InvalidCrop -> "The selected analysis area is invalid. Adjust the crop and try again."
        MeasurementError.AnalysisFailed -> "Analysis could not be completed. Your image, crop, and factor are preserved."
        MeasurementError.PersistenceFailed -> "The result could not be saved. Retry to save the same measurement."
        is MeasurementError.CameraLaunchFailed ->
            "The camera could not be opened. ${error.reason} Retry when the camera is available."
    }
    LoadingErrorState(
        isLoading = false,
        headline = "Measurement interrupted",
        message = message,
        actionLabel = "Retry",
        onAction = onRetry,
        actionModifier = Modifier.fillMaxWidth().testTag(RETRY_BUTTON_TAG),
        secondaryActionLabel = if (permission?.permanentlyDenied == true) "Open settings" else null,
        onSecondaryAction = if (permission?.permanentlyDenied == true) onOpenSettings else null,
        secondaryActionModifier = Modifier.fillMaxWidth().testTag("open_settings"),
        modifier = modifier.fillMaxSize().testTag(MEASUREMENT_ERROR_TAG),
    )
}
