package cloud.univ.jointsense.measurement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(MEASUREMENT_ERROR_TAG),
    ) {
        LoadingErrorState(
            isLoading = false,
            headline = "Measurement interrupted",
            message = message,
            actionLabel = "Retry",
            onAction = onRetry,
            actionModifier = Modifier.fillMaxWidth().testTag(RETRY_BUTTON_TAG),
        )
        if (permission?.permanentlyDenied == true) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Open settings" },
            ) {
                Text("Open settings", modifier = Modifier.testTag("open_settings"))
            }
        }
    }
}
