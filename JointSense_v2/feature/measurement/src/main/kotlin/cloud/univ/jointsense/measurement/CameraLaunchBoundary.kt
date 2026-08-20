package cloud.univ.jointsense.measurement

internal fun launchClaimedCamera(
    claim: CameraLaunchClaim,
    launch: (String) -> Unit,
    onAcknowledged: () -> Unit,
    onFailure: (String) -> Unit,
) {
    try {
        launch(claim.uri)
        onAcknowledged()
    } catch (error: RuntimeException) {
        onFailure(error.message ?: error::class.java.simpleName)
    }
}
