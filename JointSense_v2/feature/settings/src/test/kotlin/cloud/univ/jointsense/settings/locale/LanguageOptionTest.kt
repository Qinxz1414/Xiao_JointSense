package cloud.univ.jointsense.settings.locale

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageOptionTest {
    @Test
    fun applicationLocaleTagsMapToSupportedOptions() {
        assertEquals(LanguageOption.SYSTEM, LanguageOption.fromApplicationLocaleTags(""))
        assertEquals(
            LanguageOption.SIMPLIFIED_CHINESE,
            LanguageOption.fromApplicationLocaleTags("zh-CN"),
        )
        assertEquals(LanguageOption.ENGLISH, LanguageOption.fromApplicationLocaleTags("en-US"))
    }

    @Test
    fun unsupportedApplicationLocaleTagsFallBackToSystem() {
        assertEquals(LanguageOption.SYSTEM, LanguageOption.fromApplicationLocaleTags("fr-FR"))
    }
}
