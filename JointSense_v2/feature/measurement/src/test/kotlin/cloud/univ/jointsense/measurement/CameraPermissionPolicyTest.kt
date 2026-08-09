package cloud.univ.jointsense.measurement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraPermissionPolicyTest {
    @Test
    fun firstDenialWithNoPriorRequestIsRecoverable() {
        assertFalse(classifyPermanentCameraDenial(wasRequestedBeforeLaunch = false, shouldShowRationale = false))
    }

    @Test
    fun denialWithoutRationaleAfterARecordedRequestIsPermanent() {
        assertTrue(classifyPermanentCameraDenial(wasRequestedBeforeLaunch = true, shouldShowRationale = false))
    }

    @Test
    fun rationaleKeepsDenialRecoverableEvenAfterRecreationHistory() {
        assertFalse(classifyPermanentCameraDenial(wasRequestedBeforeLaunch = true, shouldShowRationale = true))
    }
}
