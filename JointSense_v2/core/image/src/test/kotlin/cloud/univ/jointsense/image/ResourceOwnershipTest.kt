package cloud.univ.jointsense.image

import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class ResourceOwnershipTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun cancellationAfterDispatcherAllocationReleasesUndeliveredResource() = runTest {
        val dispatcher = QueueDispatcher()
        val resource = Releasable("decoded")
        var delivered: Releasable? = null
        val job = launch {
            delivered = withContextResourceOwnership(
                dispatcher = dispatcher,
                acquire = { resource },
                release = Releasable::release,
            )
        }
        runCurrent()

        dispatcher.runNext()
        job.cancel()
        runCurrent()

        assertEquals(null, delivered)
        assertEquals(1, resource.releaseCount)
    }

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

private class QueueDispatcher : CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks += block
    }

    fun runNext() {
        tasks.removeFirst().run()
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
