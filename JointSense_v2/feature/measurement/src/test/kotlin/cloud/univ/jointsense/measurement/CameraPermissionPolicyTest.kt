package cloud.univ.jointsense.measurement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPermissionPolicyTest {
    @Test
    fun firstActualRequestWithNoRationaleIsPermanentOnceFormallyRecorded() {
        assertTrue(
            classifyPermanentCameraDenial(
                wasRequestFormallyRecorded = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun denialWithoutRationaleAfterARecordedRequestIsPermanent() {
        assertTrue(
            classifyPermanentCameraDenial(
                wasRequestFormallyRecorded = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun rationaleKeepsDenialRecoverableEvenAfterRecreationHistory() {
        assertFalse(
            classifyPermanentCameraDenial(
                wasRequestFormallyRecorded = true,
                shouldShowRationale = true,
            ),
        )
    }
}
