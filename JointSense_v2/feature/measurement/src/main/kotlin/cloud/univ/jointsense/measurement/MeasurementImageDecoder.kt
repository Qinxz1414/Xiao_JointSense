package cloud.univ.jointsense.measurement

import android.net.Uri
import cloud.univ.jointsense.image.ImageDecodeError
import cloud.univ.jointsense.image.SampledBitmapDecoder

interface MeasurementImageDecoder {
    suspend fun decode(uri: String): MeasurementImage
}

class SampledMeasurementImageDecoder(
    private val decoder: SampledBitmapDecoder,
) : MeasurementImageDecoder {
    override suspend fun decode(uri: String): MeasurementImage = try {
        BitmapMeasurementImage(decoder.decode(Uri.parse(uri)).bitmap)
    } catch (error: ImageDecodeError.Unsupported) {
        throw MeasurementImageDecodeException(MeasurementError.UnsupportedImage, error)
    } catch (error: ImageDecodeError.Unreadable) {
        throw MeasurementImageDecodeException(MeasurementError.ImageUnreadable, error)
    } catch (error: ImageDecodeError.OutOfMemory) {
        throw MeasurementImageDecodeException(MeasurementError.ImageTooLarge, error)
    }
}

class MeasurementImageDecodeException(
    val error: MeasurementError,
    cause: Throwable? = null,
) : Exception(cause)
