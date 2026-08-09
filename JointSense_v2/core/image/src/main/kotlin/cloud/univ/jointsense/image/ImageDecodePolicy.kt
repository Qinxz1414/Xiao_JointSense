package cloud.univ.jointsense.image

fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxEdge: Int,
): Int {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    require(maxEdge > 0) { "maxEdge must be positive" }

    val longestEdge = maxOf(width, height)
    var sampleSize = 1
    while (longestEdge / sampleSize > maxEdge) {
        sampleSize *= 2
    }
    return sampleSize
}
