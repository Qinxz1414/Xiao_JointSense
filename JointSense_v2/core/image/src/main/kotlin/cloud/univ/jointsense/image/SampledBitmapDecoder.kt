package cloud.univ.jointsense.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SampledBitmapDecoder internal constructor(
    private val streamOpener: ImageStreamOpener,
    private val operations: ImageDecodeOperations,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(
        contentResolver: ContentResolver,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        streamOpener = ImageStreamOpener(contentResolver::openInputStream),
        operations = AndroidImageDecodeOperations,
        ioDispatcher = ioDispatcher,
    )

    suspend fun decode(
        uri: Uri,
        maxEdge: Int = DEFAULT_MAX_EDGE,
    ): DecodedImage = withContext(ioDispatcher) {
        decodeBlocking(uri, maxEdge)
    }

    private fun decodeBlocking(uri: Uri, maxEdge: Int): DecodedImage {
        require(maxEdge > 0) { "maxEdge must be positive" }
        try {
            val bounds = readBounds(uri)
            val sampleSize = calculateInSampleSize(bounds.width, bounds.height, maxEdge)
            val decodedBitmap = readSampledBitmap(uri, sampleSize)
            return withResourceOwnership(decodedBitmap, Bitmap::recycle) { ownership ->
                val rotationDegrees = readRotationDegrees(uri)
                ownership.replace(operations.rotate(ownership.current, rotationDegrees))
                val result = DecodedImage(
                    bitmap = ownership.current,
                    sourceWidth = bounds.width,
                    sourceHeight = bounds.height,
                    inSampleSize = sampleSize,
                    rotationDegrees = rotationDegrees,
                )
                ownership.transfer()
                result
            }
        } catch (error: ImageDecodeError) {
            throw error
        } catch (error: OutOfMemoryError) {
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
        return open(uri).use(operations::readBounds) ?: throw ImageDecodeError.Unsupported(uri)
    }

    private fun readSampledBitmap(uri: Uri, sampleSize: Int): Bitmap {
        return open(uri).use { stream -> operations.readBitmap(stream, sampleSize) }
            ?: throw ImageDecodeError.Unsupported(uri)
    }

    private fun readRotationDegrees(uri: Uri): Int {
        return open(uri).use(operations::readRotationDegrees)
    }

    private fun open(uri: Uri) =
        streamOpener.open(uri) ?: throw ImageDecodeError.Unreadable(uri)

    private companion object {
        const val DEFAULT_MAX_EDGE = 2048
    }
}

internal fun interface ImageStreamOpener {
    fun open(uri: Uri): InputStream?
}

internal interface ImageDecodeOperations {
    fun readBounds(stream: InputStream): ImageBounds?

    fun readBitmap(stream: InputStream, sampleSize: Int): Bitmap?

    fun readRotationDegrees(stream: InputStream): Int

    fun rotate(bitmap: Bitmap, rotationDegrees: Int): Bitmap
}

internal data class ImageBounds(val width: Int, val height: Int)

private object AndroidImageDecodeOperations : ImageDecodeOperations {
    override fun readBounds(stream: InputStream): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(stream, null, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            ImageBounds(options.outWidth, options.outHeight)
        } else {
            null
        }
    }

    override fun readBitmap(stream: InputStream, sampleSize: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeStream(stream, null, options)
    }

    override fun readRotationDegrees(stream: InputStream): Int {
        val orientation = try {
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
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

    override fun rotate(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
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
}
