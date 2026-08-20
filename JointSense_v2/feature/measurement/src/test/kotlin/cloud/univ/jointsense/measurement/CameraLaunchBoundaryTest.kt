package cloud.univ.jointsense.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraLaunchBoundaryTest {
    @Test
    fun synchronousLauncherFailureReportsRollbackWithoutAcknowledging() {
        val claim = CameraLaunchClaim(
            uri = "content://camera/a",
            requestToken = "request-a",
            draftId = "draft-a",
            captureToken = "capture-a",
        )
        var acknowledged = false
        var failed = false
        var reason: String? = null

        launchClaimedCamera(
            claim = claim,
            launch = { error("launcher not registered") },
            onAcknowledged = { acknowledged = true },
            onFailure = {
                failed = true
                reason = it
            },
        )

        assertFalse(acknowledged)
        assertTrue(failed)
        assertEquals("launcher not registered", reason)
    }

    @Test
    fun successfulLauncherReturnAcknowledgesExactlyOnce() {
        val claim = CameraLaunchClaim(
            uri = "content://camera/a",
            requestToken = "request-a",
            draftId = "draft-a",
            captureToken = "capture-a",
        )
        var launches = 0
        var acknowledgements = 0

        launchClaimedCamera(
            claim = claim,
            launch = {
                launches += 1
                assertEquals("content://camera/a", it)
            },
            onAcknowledged = { acknowledgements += 1 },
            onFailure = { error("unexpected failure: $it") },
        )

        assertEquals(1, launches)
        assertEquals(1, acknowledgements)
    }
}
