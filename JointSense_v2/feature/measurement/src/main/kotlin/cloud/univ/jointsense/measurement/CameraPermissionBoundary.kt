package cloud.univ.jointsense.measurement

internal fun handleTakePhotoRequest(
    cameraPermissionGranted: Boolean,
    onCaptureRequested: () -> Unit,
    onPermissionRequested: () -> Unit,
) {
    if (cameraPermissionGranted) {
        onCaptureRequested()
    } else {
        onPermissionRequested()
    }
}

internal fun launchClaimedCameraPermission(
    claim: CameraPermissionLaunchClaim,
    launch: () -> Unit,
    onAcknowledged: () -> Unit,
    onFailure: (String) -> Unit,
) {
    try {
        launch()
        onAcknowledged()
    } catch (error: RuntimeException) {
        onFailure(error.message ?: error::class.java.simpleName)
    }
}
