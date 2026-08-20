package cloud.univ.jointsense.settings.locale

enum class LanguageOption(val languageTag: String) {
    SYSTEM(""),
    SIMPLIFIED_CHINESE("zh-CN"),
    ENGLISH("en-US"),
    ;

    companion object {
        fun fromApplicationLocaleTags(languageTags: String): LanguageOption =
            entries.firstOrNull { it.languageTag == languageTags } ?: SYSTEM
    }
}
