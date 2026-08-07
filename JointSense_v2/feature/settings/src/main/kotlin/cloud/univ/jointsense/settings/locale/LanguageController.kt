package cloud.univ.jointsense.settings.locale

interface LanguageController {
    fun current(): LanguageOption

    fun apply(option: LanguageOption)
}
