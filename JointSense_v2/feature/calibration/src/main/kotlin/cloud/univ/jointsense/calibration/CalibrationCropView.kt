package cloud.univ.jointsense.calibration

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun CalibrationCropView(
    bitmap: Bitmap,
    cropRect: Rect,
    onCropRectChanged: (Rect) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val handleColor = MaterialTheme.colorScheme.primary
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val imageAspect = bitmap.width.toFloat() / bitmap.height
        val containerAspect = widthPx / heightPx
        val displayWidth: Float
        val displayHeight: Float
        if (imageAspect > containerAspect) {
            displayWidth = widthPx
            displayHeight = widthPx / imageAspect
        } else {
            displayWidth = heightPx * imageAspect
            displayHeight = heightPx
        }
        val scaleX = displayWidth / bitmap.width
        val scaleY = displayHeight / bitmap.height
        val offsetX = (widthPx - displayWidth) / 2f
        val offsetY = (heightPx - displayHeight) / 2f
        val touchRadius = with(LocalDensity.current) { 28.dp.toPx() }
        val latestRect = rememberUpdatedState(cropRect)
        var dragMode by remember { mutableStateOf(CalibrationDragMode.NONE) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, scaleX, scaleY, offsetX, offsetY, enabled) {
                    if (!enabled) return@pointerInput
                    var activeRect = cropRect
                    detectDragGestures(
                        onDragStart = { point ->
                            activeRect = latestRect.value
                            val left = activeRect.left * scaleX + offsetX
                            val top = activeRect.top * scaleY + offsetY
                            val right = activeRect.right * scaleX + offsetX
                            val bottom = activeRect.bottom * scaleY + offsetY
                            dragMode = when {
                                point.near(left, top, touchRadius) -> CalibrationDragMode.TOP_LEFT
                                point.near(right, top, touchRadius) -> CalibrationDragMode.TOP_RIGHT
                                point.near(left, bottom, touchRadius) -> CalibrationDragMode.BOTTOM_LEFT
                                point.near(right, bottom, touchRadius) -> CalibrationDragMode.BOTTOM_RIGHT
                                point.x in left..right && point.y in top..bottom -> CalibrationDragMode.MOVE
                                else -> CalibrationDragMode.NONE
                            }
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            val dx = (amount.x / scaleX).roundToInt()
                            val dy = (amount.y / scaleY).roundToInt()
                            activeRect = when (dragMode) {
                                CalibrationDragMode.MOVE -> {
                                    val left = (activeRect.left + dx)
                                        .coerceIn(0, bitmap.width - activeRect.width())
                                    val top = (activeRect.top + dy)
                                        .coerceIn(0, bitmap.height - activeRect.height())
                                    Rect(left, top, left + activeRect.width(), top + activeRect.height())
                                }
                                CalibrationDragMode.TOP_LEFT -> Rect(
                                    (activeRect.left + dx).coerceIn(0, activeRect.right - 50),
                                    (activeRect.top + dy).coerceIn(0, activeRect.bottom - 50),
                                    activeRect.right,
                                    activeRect.bottom,
                                )
                                CalibrationDragMode.TOP_RIGHT -> Rect(
                                    activeRect.left,
                                    (activeRect.top + dy).coerceIn(0, activeRect.bottom - 50),
                                    (activeRect.right + dx).coerceIn(activeRect.left + 50, bitmap.width),
                                    activeRect.bottom,
                                )
                                CalibrationDragMode.BOTTOM_LEFT -> Rect(
                                    (activeRect.left + dx).coerceIn(0, activeRect.right - 50),
                                    activeRect.top,
                                    activeRect.right,
                                    (activeRect.bottom + dy).coerceIn(activeRect.top + 50, bitmap.height),
                                )
                                CalibrationDragMode.BOTTOM_RIGHT -> Rect(
                                    activeRect.left,
                                    activeRect.top,
                                    (activeRect.right + dx).coerceIn(activeRect.left + 50, bitmap.width),
                                    (activeRect.bottom + dy).coerceIn(activeRect.top + 50, bitmap.height),
                                )
                                CalibrationDragMode.NONE -> activeRect
                            }
                            if (dragMode != CalibrationDragMode.NONE) onCropRectChanged(activeRect)
                        },
                        onDragEnd = { dragMode = CalibrationDragMode.NONE },
                    )
                },
        ) {
            drawImage(
                image = image,
                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                dstSize = IntSize(displayWidth.roundToInt(), displayHeight.roundToInt()),
            )
            val left = cropRect.left * scaleX + offsetX
            val top = cropRect.top * scaleY + offsetY
            val right = cropRect.right * scaleX + offsetX
            val bottom = cropRect.bottom * scaleY + offsetY
            val shade = Color.Black.copy(alpha = 0.5f)
            drawRect(shade, Offset(offsetX, offsetY), Size(displayWidth, top - offsetY))
            drawRect(shade, Offset(offsetX, bottom), Size(displayWidth, offsetY + displayHeight - bottom))
            drawRect(shade, Offset(offsetX, top), Size(left - offsetX, bottom - top))
            drawRect(shade, Offset(right, top), Size(offsetX + displayWidth - right, bottom - top))
            drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = Stroke(3f))
            calibrationGridLines(left, top, right, bottom).forEach { line ->
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(line.startX, line.startY),
                    end = Offset(line.endX, line.endY),
                    strokeWidth = 1f,
                )
            }
            listOf(
                Offset(left, top),
                Offset(right, top),
                Offset(left, bottom),
                Offset(right, bottom),
            ).forEach { point ->
                drawCircle(Color.White, radius = 5.5f * density, center = point)
                drawCircle(handleColor, radius = 4.5f * density, center = point)
            }
        }
    }
}

private enum class CalibrationDragMode {
    NONE,
    MOVE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

private fun Offset.near(x: Float, y: Float, radius: Float): Boolean {
    val dx = this.x - x
    val dy = this.y - y
    return dx * dx + dy * dy <= radius * radius
}
