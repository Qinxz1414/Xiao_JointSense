package cloud.univ.jointsense.image

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ResourceOwnershipTest {
    @Test
    fun releasesOwnedResourceWhenPostDecodeStepFails() {
        val sampled = Releasable("sampled")

        assertThrows(IOException::class.java) {
            withResourceOwnership(sampled, Releasable::release) {
                throw IOException("EXIF stream failed")
            }
        }

        assertEquals(1, sampled.releaseCount)
    }

    @Test
    fun successfulTransferKeepsReturnedResourceAlive() {
        val sampled = Releasable("sampled")

        val returned = withResourceOwnership(sampled, Releasable::release) { ownership ->
            val result = ownership.current
            ownership.transfer()
            result
        }

        assertSame(sampled, returned)
        assertEquals(0, sampled.releaseCount)
    }

    @Test
    fun replacementReleasesOldResourceAndFailureReleasesNewResourceOnce() {
        val sampled = Releasable("sampled")
        val rotated = Releasable("rotated")

        assertThrows(IOException::class.java) {
            withResourceOwnership(sampled, Releasable::release) { ownership ->
                ownership.replace(rotated)
                throw IOException("result construction failed")
            }
        }

        assertEquals(1, sampled.releaseCount)
        assertEquals(1, rotated.releaseCount)
    }
}

private data class Releasable(
    val name: String,
    var releaseCount: Int = 0,
) {
    fun release() {
        releaseCount += 1
    }
}
