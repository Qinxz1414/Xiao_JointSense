package cloud.univ.jointsense.measurement

fun classifyPermanentCameraDenial(
    wasRequestedBeforeLaunch: Boolean,
    shouldShowRationale: Boolean,
): Boolean = wasRequestedBeforeLaunch && !shouldShowRationale
