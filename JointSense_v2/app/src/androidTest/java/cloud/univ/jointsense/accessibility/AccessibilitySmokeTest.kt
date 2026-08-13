package cloud.univ.jointsense.accessibility

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.MainActivity
import cloud.univ.jointsense.navigation.MAIN_NEW_TEST_TAG
import cloud.univ.jointsense.navigation.NAV_HOME_TAG
import cloud.univ.jointsense.navigation.NAV_PROFILE_TAG
import cloud.univ.jointsense.navigation.NAV_REPORT_TAG
import cloud.univ.jointsense.navigation.NAV_TRENDS_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun primaryNavigationHasNamedLargeTabAndButtonTargets() {
        listOf(NAV_HOME_TAG, NAV_TRENDS_TAG, NAV_REPORT_TAG, NAV_PROFILE_TAG).forEach { tag ->
            composeRule.onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        composeRule.onNodeWithTag(NAV_HOME_TAG).assertIsSelected()
        composeRule.onNodeWithTag(MAIN_NEW_TEST_TAG)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
