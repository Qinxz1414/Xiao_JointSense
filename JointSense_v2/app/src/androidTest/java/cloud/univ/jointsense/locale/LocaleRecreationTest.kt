package cloud.univ.jointsense.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cloud.univ.jointsense.MainActivity
import cloud.univ.jointsense.navigation.NAV_PROFILE_TAG
import cloud.univ.jointsense.settings.ABOUT_SCREEN_TAG
import cloud.univ.jointsense.settings.PROFILE_SCREEN_TAG
import cloud.univ.jointsense.settings.SETTINGS_ABOUT_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocaleRecreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetLocaleBeforeTest() = applyLocales("")

    @After
    fun resetLocaleAfterTest() = applyLocales("")

    @Test
    fun chineseAndEnglishChangesRecreateActivityAndKeepAboutRoute() {
        composeRule.onNodeWithTag(NAV_PROFILE_TAG).performClick()
        composeRule.onNodeWithTag(PROFILE_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_ABOUT_TAG).performClick()
        composeRule.onNodeWithTag(ABOUT_SCREEN_TAG).assertIsDisplayed()

        var previous = composeRule.activity
        applyLocales("zh-CN")
        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.activity !== previous }
        composeRule.onNodeWithTag(ABOUT_SCREEN_TAG).assertIsDisplayed()

        previous = composeRule.activity
        applyLocales("en")
        composeRule.waitUntil(timeoutMillis = 10_000) { composeRule.activity !== previous }
        composeRule.onNodeWithTag(ABOUT_SCREEN_TAG).assertIsDisplayed()
    }

    private fun applyLocales(tags: String) {
        val previousActivity = composeRule.activity
        val localeChanged = AppCompatDelegate.getApplicationLocales().toLanguageTags() != tags
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
        }
        composeRule.waitForIdle()
        if (localeChanged) {
            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.activity !== previousActivity
            }
        }
    }
}
