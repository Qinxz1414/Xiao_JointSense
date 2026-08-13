package cloud.univ.jointsense.navigation

import androidx.activity.ComponentActivity
import androidx.room.Room
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.database.JointSenseDatabase
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.di.AppContainer
import cloud.univ.jointsense.insights.RESTORE_SAMPLES_TAG
import cloud.univ.jointsense.settings.PROFILE_SCREEN_TAG
import cloud.univ.jointsense.settings.RESTORE_SAMPLES_CONFIRMATION_TAG
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRestoreNavigationIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: AppContainer
    private var database: JointSenseDatabase? = null

    @Before
    fun createIsolatedContainer() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val isolatedDatabase = Room.inMemoryDatabaseBuilder(
            application,
            JointSenseDatabase::class.java,
        ).build()
        database = isolatedDatabase
        container = AppContainer(application, isolatedDatabase)
    }

    @After
    fun closeIsolatedDatabase() {
        database?.close()
    }

    @Test
    fun productionHomeRestoreOpensProfileWithConfirmationVisible() {
        composeRule.setContent {
            JointSenseTheme {
                JointSenseNavHost(
                    navController = rememberNavController(),
                    appContainer = container,
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(RESTORE_SAMPLES_TAG)
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag(RESTORE_SAMPLES_TAG).performClick()

        composeRule.onNodeWithTag(PROFILE_SCREEN_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(RESTORE_SAMPLES_CONFIRMATION_TAG).assertIsDisplayed()
    }
}
