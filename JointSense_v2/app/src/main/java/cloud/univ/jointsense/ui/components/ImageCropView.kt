package cloud.univ.jointsense.ui.components

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cloud.univ.jointsense.ui.theme.PrimaryAccent
import kotlin.math.roundToInt

/**
 * A composable that displays a bitmap image with an interactive crop rectangle overlay.
 * The user can drag the rectangle to reposition it and drag corners to resize it.
 *
 * @param bitmap The source bitmap to display
 * @param cropRect The current crop rectangle in image coordinates
 * @param onCropRectChanged Callback when the crop rectangle changes
 * @param modifier Modifier for the composable
 */
@Composable
fun ImageCropView(
    bitmap: Bitmap,
    cropRect: Rect,
    onCropRectChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val cornerRadius = 12.dp

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // Calculate scale to fit image in the available space
        val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val containerAspect = maxWidthPx / maxHeightPx

        val (displayWidth, displayHeight) = if (imageAspect > containerAspect) {
            maxWidthPx to maxWidthPx / imageAspect
        } else {
            maxHeightPx * imageAspect to maxHeightPx
        }

        val scaleX = displayWidth / bitmap.width
        val scaleY = displayHeight / bitmap.height
        val offsetX = (maxWidthPx - displayWidth) / 2
        val offsetY = (maxHeightPx - displayHeight) / 2

        // Track which part is being dragged
        var dragMode by remember { mutableStateOf(DragMode.NONE) }

        // Always-readable latest rect: pointerInput keys below do NOT
        // include cropRect (restarting the gesture on every change would
        // cancel drags), so the handlers must read through this state.
        val latestCropRect = rememberUpdatedState(cropRect)

        val cornerTouchRadius = with(LocalDensity.current) { 28.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, scaleX, scaleY, offsetX, offsetY) {
                    // Rect accumulated locally during one gesture so each
                    // per-event delta is applied on top of the previous
                    // one, independent of recomposition timing.
                    var activeRect = Rect(0, 0, 0, 0)

                    detectDragGestures(
                        onDragStart = { offset ->
                            val rect = latestCropRect.value
                            activeRect = rect
                            val screenLeft = rect.left * scaleX + offsetX
                            val screenTop = rect.top * scaleY + offsetY
                            val screenRight = rect.right * scaleX + offsetX
                            val screenBottom = rect.bottom * scaleY + offsetY

                            dragMode = when {
                                isNearPoint(offset, screenLeft, screenTop, cornerTouchRadius) -> DragMode.TOP_LEFT
                                isNearPoint(offset, screenRight, screenTop, cornerTouchRadius) -> DragMode.TOP_RIGHT
                                isNearPoint(offset, screenLeft, screenBottom, cornerTouchRadius) -> DragMode.BOTTOM_LEFT
                                isNearPoint(offset, screenRight, screenBottom, cornerTouchRadius) -> DragMode.BOTTOM_RIGHT
                                offset.x in screenLeft..screenRight && offset.y in screenTop..screenBottom -> DragMode.MOVE
                                else -> DragMode.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragMode != DragMode.NONE) {
                                val dx = (dragAmount.x / scaleX).roundToInt()
                                val dy = (dragAmount.y / scaleY).roundToInt()

                                val newRect = when (dragMode) {
                                    DragMode.MOVE -> {
                                        val newLeft = (activeRect.left + dx).coerceIn(0, bitmap.width - activeRect.width())
                                        val newTop = (activeRect.top + dy).coerceIn(0, bitmap.height - activeRect.height())
                                        Rect(newLeft, newTop, newLeft + activeRect.width(), newTop + activeRect.height())
                                    }
                                    DragMode.TOP_LEFT -> {
                                        Rect(
                                            (activeRect.left + dx).coerceIn(0, activeRect.right - 50),
                                            (activeRect.top + dy).coerceIn(0, activeRect.bottom - 50),
                                            activeRect.right,
                                            activeRect.bottom
                                        )
                                    }
                                    DragMode.TOP_RIGHT -> {
                                        Rect(
                                            activeRect.left,
                                            (activeRect.top + dy).coerceIn(0, activeRect.bottom - 50),
                                            (activeRect.right + dx).coerceIn(activeRect.left + 50, bitmap.width),
                                            activeRect.bottom
                                        )
                                    }
                                    DragMode.BOTTOM_LEFT -> {
                                        Rect(
                                            (activeRect.left + dx).coerceIn(0, activeRect.right - 50),
                                            activeRect.top,
                                            activeRect.right,
                                            (activeRect.bottom + dy).coerceIn(activeRect.top + 50, bitmap.height)
                                        )
                                    }
                                    else -> {
                                        Rect(
                                            activeRect.left,
                                            activeRect.top,
                                            (activeRect.right + dx).coerceIn(activeRect.left + 50, bitmap.width),
                                            (activeRect.bottom + dy).coerceIn(activeRect.top + 50, bitmap.height)
                                        )
                                    }
                                }
                                activeRect = newRect
                                onCropRectChanged(newRect)
                            }
                        },
                        onDragEnd = {
                            dragMode = DragMode.NONE
                        }
                    )
                }
        ) {
            // Draw the image
            drawImage(
                image = imageBitmap,
                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                dstSize = IntSize(displayWidth.roundToInt(), displayHeight.roundToInt())
            )

            // Draw semi-transparent overlay outside the crop area
            val screenCropLeft = cropRect.left * scaleX + offsetX
            val screenCropTop = cropRect.top * scaleY + offsetY
            val screenCropRight = cropRect.right * scaleX + offsetX
            val screenCropBottom = cropRect.bottom * scaleY + offsetY

            val overlayColor = Color.Black.copy(alpha = 0.5f)

            // Top overlay
            drawRect(
                color = overlayColor,
                topLeft = Offset(offsetX, offsetY),
                size = Size(displayWidth, screenCropTop - offsetY)
            )
            // Bottom overlay
            drawRect(
                color = overlayColor,
                topLeft = Offset(offsetX, screenCropBottom),
                size = Size(displayWidth, offsetY + displayHeight - screenCropBottom)
            )
            // Left overlay
            drawRect(
                color = overlayColor,
                topLeft = Offset(offsetX, screenCropTop),
                size = Size(screenCropLeft - offsetX, screenCropBottom - screenCropTop)
            )
            // Right overlay
            drawRect(
                color = overlayColor,
                topLeft = Offset(screenCropRight, screenCropTop),
                size = Size(offsetX + displayWidth - screenCropRight, screenCropBottom - screenCropTop)
            )

            // Draw crop rectangle border
            drawRect(
                color = Color.White,
                topLeft = Offset(screenCropLeft, screenCropTop),
                size = Size(screenCropRight - screenCropLeft, screenCropBottom - screenCropTop),
                style = Stroke(width = 3f)
            )

            // Draw grid lines (rule of thirds)
            val thirdW = (screenCropRight - screenCropLeft) / 3
            val thirdH = (screenCropBottom - screenCropTop) / 3
            val gridColor = Color.White.copy(alpha = 0.5f)
            for (i in 1..2) {
                drawLine(
                    color = gridColor,
                    start = Offset(screenCropLeft + thirdW * i, screenCropTop),
                    end = Offset(screenCropLeft + thirdW * i, screenCropBottom),
                    strokeWidth = 1f
                )
                drawLine(
                    color = gridColor,
                    start = Offset(screenCropLeft, screenCropTop + thirdH * i),
                    end = Offset(screenCropRight, screenCropTop + thirdH * i),
                    strokeWidth = 1f
                )
            }

            // Draw corner handles (density-scaled so they stay visible)
            val handleRadius = 5.5f * density
            val corners = listOf(
                Offset(screenCropLeft, screenCropTop),
                Offset(screenCropRight, screenCropTop),
                Offset(screenCropLeft, screenCropBottom),
                Offset(screenCropRight, screenCropBottom)
            )
            for (corner in corners) {
                drawCircle(
                    color = Color.White,
                    radius = handleRadius,
                    center = corner
                )
                drawCircle(
                    color = PrimaryAccent,
                    radius = handleRadius - 2f * density / 2,
                    center = corner
                )
            }
        }
    }
}

private enum class DragMode {
    NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

private fun isNearPoint(offset: Offset, x: Float, y: Float, radius: Float): Boolean {
    val dx = offset.x - x
    val dy = offset.y - y
    return dx * dx + dy * dy <= radius * radius
}
