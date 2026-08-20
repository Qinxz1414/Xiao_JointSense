package cloud.univ.jointsense.image

import android.graphics.Bitmap

data class DecodedImage(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val inSampleSize: Int,
    val rotationDegrees: Int,
)
