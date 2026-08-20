package cloud.univ.jointsense.analysis

import kotlin.math.ceil

/** Nearest-rank percentile used by both calibration and measurement image paths. */
fun nearestRankPercentile(values: IntArray, percentile: Float): Float {
    require(values.isNotEmpty()) { "values must not be empty" }
    require(percentile.isFinite() && percentile > 0f && percentile <= 1f) {
        "percentile must be in (0, 1]"
    }
    val sorted = values.copyOf().also(IntArray::sort)
    val rank = ceil(percentile.toDouble() * sorted.size).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1].toFloat()
}

const val FACTORY_CURVE_SIGNAL_PERCENTILE: Float = 0.90f
