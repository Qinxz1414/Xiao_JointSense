package cloud.univ.jointsense.measurement.image

import cloud.univ.jointsense.measurement.MeasurementCapture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurablePickedImageResolverTest {
    @Test
    fun persistableProviderUriRemainsExternalAndIsNeverOwnedForDeletion() {
        val resolver = DurablePickedImageResolver(
            persistReadPermission = { true },
            copyToOwned = { error("copy must not run") },
        )

        val input = resolver.acquire("content://provider/persisted")

        assertEquals("content://provider/persisted", input.uri)
        assertNull(input.ownedCapture)
    }

    @Test
    fun unsupportedPersistablePermissionCopiesToOwnedCaptureForRecreation() {
        val owned = MeasurementCapture("content://app/owned", "owned-token")
        val resolver = DurablePickedImageResolver(
            persistReadPermission = { false },
            copyToOwned = { owned },
        )

        val input = resolver.acquire("content://provider/ephemeral")

        assertEquals("content://app/owned", input.uri)
        assertEquals(owned, input.ownedCapture)
    }
}
