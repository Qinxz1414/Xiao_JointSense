package cloud.univ.jointsense.settings

import cloud.univ.jointsense.settings.locale.LanguageOption
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSelectionOrderTest {
    @Test
    fun changedSelectionClosesBeforeApply() {
        val events = mutableListOf<String>()

        completeLanguageSelection(
            current = LanguageOption.SYSTEM,
            selected = LanguageOption.ENGLISH,
            close = { events += "close" },
            apply = { events += "apply:$it" },
        )

        assertEquals(listOf("close", "apply:ENGLISH"), events)
    }

    @Test
    fun unchangedSelectionClosesWithoutApply() {
        val events = mutableListOf<String>()

        completeLanguageSelection(
            current = LanguageOption.SIMPLIFIED_CHINESE,
            selected = LanguageOption.SIMPLIFIED_CHINESE,
            close = { events += "close" },
            apply = { events += "apply:$it" },
        )

        assertEquals(listOf("close"), events)
    }
}
