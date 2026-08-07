package cloud.univ.jointsense.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import cloud.univ.jointsense.settings.locale.LanguageController
import cloud.univ.jointsense.settings.locale.LanguageOption

class AppCompatLanguageController internal constructor(
    private val applicationLocales: ApplicationLocales,
) : LanguageController {
    constructor() : this(AppCompatApplicationLocales)

    override fun current(): LanguageOption =
        LanguageOption.fromApplicationLocaleTags(applicationLocales.languageTags)

    override fun apply(option: LanguageOption) {
        applicationLocales.languageTags = option.languageTag
    }
}

internal interface ApplicationLocales {
    var languageTags: String
}

private object AppCompatApplicationLocales : ApplicationLocales {
    override var languageTags: String
        get() = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        set(value) {
            val locales = if (value.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(value)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
}
