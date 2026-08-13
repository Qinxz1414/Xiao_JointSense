package cloud.univ.jointsense.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreSamplesNavigationDriverTest {
    @Test
    fun homeRestoreRequestsProfileConfirmationBeforeOpeningProfile() {
        val events = mutableListOf<String>()
        val driver = RestoreSamplesNavigationDriver(
            requestConfirmation = { events += "confirmation-requested" },
            openProfile = { events += "profile-opened" },
        )

        driver.requestFromHome()

        assertEquals(listOf("confirmation-requested", "profile-opened"), events)
    }
}
