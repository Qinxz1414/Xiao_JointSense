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

internal fun handleCameraPermissionEffect(
    effect: MeasurementEffect,
    launchPermission: () -> Unit,
) {
    if (effect == MeasurementEffect.RequestCameraPermission) launchPermission()
}
