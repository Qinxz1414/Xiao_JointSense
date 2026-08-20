package cloud.univ.jointsense.image

import android.net.Uri

sealed class ImageDecodeError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Unsupported(
        val uri: Uri,
        cause: Throwable? = null,
    ) : ImageDecodeError("Unsupported image: $uri", cause)

    class Unreadable(
        val uri: Uri,
        cause: Throwable? = null,
    ) : ImageDecodeError("Image cannot be read: $uri", cause)

    class OutOfMemory(
        val uri: Uri,
        cause: OutOfMemoryError,
    ) : ImageDecodeError("Not enough memory to decode image: $uri", cause)
}
