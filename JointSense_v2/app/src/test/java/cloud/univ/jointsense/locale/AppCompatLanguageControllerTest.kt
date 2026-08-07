package cloud.univ.jointsense.locale

import cloud.univ.jointsense.settings.locale.LanguageOption
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCompatLanguageControllerTest {
    @Test
    fun applyWritesTheSelectedApplicationLocaleTags() {
        val platform = FakeApplicationLocales("initial")
        val controller = AppCompatLanguageController(platform)

        controller.apply(LanguageOption.SYSTEM)
        assertEquals("", platform.languageTags)

        controller.apply(LanguageOption.SIMPLIFIED_CHINESE)
        assertEquals("zh-CN", platform.languageTags)

        controller.apply(LanguageOption.ENGLISH)
        assertEquals("en-US", platform.languageTags)
    }

    @Test
    fun currentMapsApplicationLocaleTagsToTheSupportedOption() {
        val platform = FakeApplicationLocales("")
        val controller = AppCompatLanguageController(platform)

        assertEquals(LanguageOption.SYSTEM, controller.current())

        platform.languageTags = "zh-CN"
        assertEquals(LanguageOption.SIMPLIFIED_CHINESE, controller.current())

        platform.languageTags = "en-US"
        assertEquals(LanguageOption.ENGLISH, controller.current())

        platform.languageTags = "fr-FR"
        assertEquals(LanguageOption.SYSTEM, controller.current())
    }

    private class FakeApplicationLocales(
        override var languageTags: String,
    ) : ApplicationLocales
}
