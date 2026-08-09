package cloud.univ.jointsense.image

fun calculateInSampleSize(
    width: Int,
    height: Int,
    maxEdge: Int,
): Int {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    require(maxEdge > 0) { "maxEdge must be positive" }

    val longestEdge = maxOf(width, height).toLong()
    val edgeLimit = maxEdge.toLong()
    var sampleSize = 1L
    while (ceilingDivide(longestEdge, sampleSize) > edgeLimit) {
        sampleSize *= 2L
    }
    require(sampleSize <= Int.MAX_VALUE.toLong()) {
        "Required sample size cannot be represented as a positive Int"
    }
    return sampleSize.toInt()
}

private fun ceilingDivide(value: Long, divisor: Long): Long =
    value / divisor + if (value % divisor == 0L) 0L else 1L
