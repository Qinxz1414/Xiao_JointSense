package cloud.univ.jointsense.calibration

internal data class CalibrationIntBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal data class CalibrationSampleWindow(
    val row: Int,
    val col: Int,
    val index: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

internal fun legacyCalibrationSampleWindows(
    crop: CalibrationIntBounds,
    rows: Int,
    cols: Int,
    wellFraction: Float,
): List<CalibrationSampleWindow> {
    require(rows > 0 && cols > 0)
    val cellWidth = crop.width.toFloat() / cols
    val cellHeight = crop.height.toFloat() / rows
    val margin = (1f - wellFraction) / 2f
    return buildList {
        var index = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val cellLeft = crop.left + (cellWidth * col).toInt()
                val cellTop = crop.top + (cellHeight * row).toInt()
                val subLeft = (cellLeft + cellWidth * margin).toInt()
                val subTop = (cellTop + cellHeight * margin).toInt()
                val subWidth = (cellWidth * wellFraction).toInt().coerceAtLeast(1)
                val subHeight = (cellHeight * wellFraction).toInt().coerceAtLeast(1)
                add(
                    CalibrationSampleWindow(
                        row = row,
                        col = col,
                        index = index++,
                        left = subLeft,
                        top = subTop,
                        width = subWidth,
                        height = subHeight,
                    ),
                )
            }
        }
    }
}

internal data class CalibrationGridLine(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
)

internal fun calibrationGridLines(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
): List<CalibrationGridLine> {
    val thirdWidth = (right - left) / 3f
    val thirdHeight = (bottom - top) / 3f
    return buildList {
        for (index in 1..2) {
            val x = left + thirdWidth * index
            val y = top + thirdHeight * index
            add(CalibrationGridLine(x, top, x, bottom))
            add(CalibrationGridLine(left, y, right, y))
        }
    }
}
