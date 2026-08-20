package cloud.univ.jointsense.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cloud.univ.jointsense.MainActivity
import cloud.univ.jointsense.feature.settings.R as SettingsR
import cloud.univ.jointsense.navigation.NAV_PROFILE_TAG
import cloud.univ.jointsense.settings.ABOUT_SCREEN_TAG
import cloud.univ.jointsense.settings.PROFILE_SCREEN_TAG
import cloud.univ.jointsense.settings.SETTINGS_ABOUT_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocaleRecreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun resetLocaleAfterTest() {
        applyLocales("")
    }

    @Test
    fun profileRouteAndLocalizedResourcesSurviveEnglishChineseEnglishRecreation() {
        var activity = forceEnglishAndWaitForStableActivity()
        composeRule.onNodeWithTag(NAV_PROFILE_TAG).performClick()
        assertProfileLocalized(activity, "en")

        val english = activity
        activity = applyLocales("zh-CN")
        assertNotSame(english, activity)
        assertProfileLocalized(activity, "zh")

        val chinese = activity
        activity = applyLocales("en")
        assertNotSame(chinese, activity)
        assertProfileLocalized(activity, "en")
    }

    @Test
    fun aboutRouteAndLocalizedResourcesSurviveEnglishChineseEnglishRecreation() {
        var activity = forceEnglishAndWaitForStableActivity()
        composeRule.onNodeWithTag(NAV_PROFILE_TAG).performClick()
        composeRule.onNodeWithTag(SETTINGS_ABOUT_TAG).performClick()
        assertAboutLocalized(activity, "en")

        val english = activity
        activity = applyLocales("zh-CN")
        assertNotSame(english, activity)
        assertAboutLocalized(activity, "zh")

        val chinese = activity
        activity = applyLocales("en")
        assertNotSame(chinese, activity)
        assertAboutLocalized(activity, "en")
    }

    private fun assertProfileLocalized(activity: MainActivity, language: String) {
        assertEquals(language, activity.resources.configuration.locales[0].language)
        composeRule.onNodeWithTag(PROFILE_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(SettingsR.string.settings_title)).assertIsDisplayed()
    }

    private fun assertAboutLocalized(activity: MainActivity, language: String) {
        assertEquals(language, activity.resources.configuration.locales[0].language)
        composeRule.onNodeWithTag(ABOUT_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(activity.getString(SettingsR.string.settings_about_title)).assertIsDisplayed()
    }

    private fun forceEnglishAndWaitForStableActivity(): MainActivity {
        val activity = applyLocales("en")
        var consecutive = 0
        var observed = activity
        composeRule.waitUntil(timeoutMillis = 10_000) {
            val current = composeRule.activity
            if (current === observed && current.resources.configuration.locales[0].language == "en") {
                consecutive += 1
            } else {
                observed = current
                consecutive = 0
            }
            consecutive >= 3
        }
        return observed
    }

    private fun applyLocales(tags: String): MainActivity {
        val previous = composeRule.activity
        val changed = AppCompatDelegate.getApplicationLocales().toLanguageTags() != tags
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
        }
        composeRule.waitForIdle()
        if (changed) {
            composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.activity !== previous }
        }
        return composeRule.activity
    }
}
