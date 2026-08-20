package cloud.univ.jointsense.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AccessibilitySourceContractTest {
    @Test
    fun profileEntryDoesNotRepeatItsAnnouncementAsAnActionLabel() {
        val source = File(
            "src/main/kotlin/cloud/univ/jointsense/settings/ProfileScreen.kt",
        ).readText()

        assertFalse(source.contains("onClickLabel = announcement"))
        assertFalse(Regex("onClick\\(label\\s*=\\s*announcement").containsMatchIn(source))
    }
}
