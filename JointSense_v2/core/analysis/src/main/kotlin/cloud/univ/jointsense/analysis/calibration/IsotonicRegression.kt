package cloud.univ.jointsense.analysis.calibration

object IsotonicRegression {
    fun fit(signals: List<Float>): List<Float> =
        fitDoubles(signals.map(Float::toDouble)).map(Double::toFloat)

    internal fun fitDoubles(signals: List<Double>): List<Double> {
        val blocks = mutableListOf<Block>()
        signals.forEach { signal ->
            blocks += Block(sum = signal, count = 1)
            while (blocks.size >= 2 && blocks[blocks.lastIndex - 1].mean > blocks.last().mean) {
                val right = blocks.removeAt(blocks.lastIndex)
                val left = blocks.removeAt(blocks.lastIndex)
                blocks += Block(
                    sum = left.sum + right.sum,
                    count = left.count + right.count,
                )
            }
        }

        return buildList(signals.size) {
            blocks.forEach { block ->
                repeat(block.count) {
                    add(block.mean)
                }
            }
        }
    }

    private data class Block(
        val sum: Double,
        val count: Int,
    ) {
        val mean: Double
            get() = sum / count
    }
}
