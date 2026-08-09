package cloud.univ.jointsense.image

import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SampledBitmapDecoderTest {
    @Test
    fun decodesWithPowerOfTwoSamplingAndAppliesExifRotation() {
        val source = createJpeg(width = 64, height = 32, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val decoder = SampledBitmapDecoder(context.contentResolver)

        val decoded = runBlocking {
            decoder.decode(Uri.fromFile(source), maxEdge = 16)
        }

        assertEquals(4, decoded.inSampleSize)
        assertEquals(90, decoded.rotationDegrees)
        assertEquals(8, decoded.bitmap.width)
        assertEquals(16, decoded.bitmap.height)
        decoded.bitmap.recycle()
    }

    @Test
    fun throwsUnsupportedForContentThatIsNotAnImage() {
        val source = File(context.cacheDir, "not-an-image-${System.nanoTime()}.txt")
        source.writeText("not an image")
        val decoder = SampledBitmapDecoder(context.contentResolver)

        assertThrows(ImageDecodeError.Unsupported::class.java) {
            runBlocking { decoder.decode(Uri.fromFile(source)) }
        }
    }

    @Test
    fun throwsUnreadableForMissingUri() {
        val source = File(context.cacheDir, "missing-${System.nanoTime()}.jpg")
        val decoder = SampledBitmapDecoder(context.contentResolver)

        assertThrows(ImageDecodeError.Unreadable::class.java) {
            runBlocking { decoder.decode(Uri.fromFile(source)) }
        }
    }

    private fun createJpeg(width: Int, height: Int, orientation: Int): File {
        val file = File(context.cacheDir, "decoder-${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        bitmap.recycle()
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            saveAttributes()
        }
        return file
    }

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
}
