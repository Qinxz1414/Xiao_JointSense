package cloud.univ.jointsense.accessibility

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.navigation.JointSenseNavHost
import cloud.univ.jointsense.navigation.MAIN_BOTTOM_BAR_TAG
import cloud.univ.jointsense.navigation.NAV_HOME_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun compactRootKeepsContentActionAndBottomNavigationDisplayedTogether() {
        composeRule.setContent {
            JointSenseTheme {
                Box(Modifier.requiredSize(360.dp, 640.dp)) {
                    JointSenseNavHost(
                        navController = rememberNavController(),
                        screenSlot = { _, _ ->
                            Button(
                                onClick = {},
                                modifier = Modifier.fillMaxWidth().testTag(ROOT_ACTION_TAG),
                            ) {
                                Text("Root action")
                            }
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(ROOT_ACTION_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(MAIN_BOTTOM_BAR_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(NAV_HOME_TAG).assertIsDisplayed()
    }

    private companion object {
        const val ROOT_ACTION_TAG = "compact_root_action"
    }
}
