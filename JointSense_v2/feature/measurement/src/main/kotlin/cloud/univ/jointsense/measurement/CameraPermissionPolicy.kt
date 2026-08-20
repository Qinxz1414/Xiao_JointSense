package cloud.univ.jointsense.measurement

fun classifyPermanentCameraDenial(
    wasRequestFormallyRecorded: Boolean,
    shouldShowRationale: Boolean,
): Boolean = wasRequestFormallyRecorded && !shouldShowRationale
