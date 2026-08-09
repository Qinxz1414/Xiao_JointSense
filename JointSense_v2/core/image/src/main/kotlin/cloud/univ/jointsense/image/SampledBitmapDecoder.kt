package cloud.univ.jointsense.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SampledBitmapDecoder(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun decode(
        uri: Uri,
        maxEdge: Int = DEFAULT_MAX_EDGE,
    ): DecodedImage = withContext(ioDispatcher) {
        decodeBlocking(uri, maxEdge)
    }

    private fun decodeBlocking(uri: Uri, maxEdge: Int): DecodedImage {
        require(maxEdge > 0) { "maxEdge must be positive" }
        var decodedBitmap: Bitmap? = null
        try {
            val bounds = readBounds(uri)
            val sampleSize = calculateInSampleSize(bounds.width, bounds.height, maxEdge)
            decodedBitmap = readSampledBitmap(uri, sampleSize)
            val rotationDegrees = readRotationDegrees(uri)
            val orientedBitmap = rotate(decodedBitmap, rotationDegrees)
            if (orientedBitmap !== decodedBitmap) {
                decodedBitmap.recycle()
            }
            decodedBitmap = null
            return DecodedImage(
                bitmap = orientedBitmap,
                sourceWidth = bounds.width,
                sourceHeight = bounds.height,
                inSampleSize = sampleSize,
                rotationDegrees = rotationDegrees,
            )
        } catch (error: ImageDecodeError) {
            throw error
        } catch (error: OutOfMemoryError) {
            decodedBitmap?.recycle()
            throw ImageDecodeError.OutOfMemory(uri, error)
        } catch (error: SecurityException) {
            throw ImageDecodeError.Unreadable(uri, error)
        } catch (error: IOException) {
            throw ImageDecodeError.Unreadable(uri, error)
        } catch (error: IllegalArgumentException) {
            throw ImageDecodeError.Unsupported(uri, error)
        }
    }

    private fun readBounds(uri: Uri): ImageBounds {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { stream -> BitmapFactory.decodeStream(stream, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw ImageDecodeError.Unsupported(uri)
        }
        return ImageBounds(options.outWidth, options.outHeight)
    }

    private fun readSampledBitmap(uri: Uri, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return open(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw ImageDecodeError.Unsupported(uri)
    }

    private fun readRotationDegrees(uri: Uri): Int {
        val orientation = try {
            open(uri).use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        } catch (_: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun open(uri: Uri) =
        contentResolver.openInputStream(uri) ?: throw ImageDecodeError.Unreadable(uri)

    private fun rotate(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true,
        )
    }

    private data class ImageBounds(val width: Int, val height: Int)

    private companion object {
        const val DEFAULT_MAX_EDGE = 2048
    }
}
