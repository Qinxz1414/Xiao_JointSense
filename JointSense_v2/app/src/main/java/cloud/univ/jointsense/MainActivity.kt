package cloud.univ.jointsense

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import cloud.univ.jointsense.designsystem.theme.JointSenseTheme
import cloud.univ.jointsense.di.AppContainer
import cloud.univ.jointsense.migration.MigrationGate
import cloud.univ.jointsense.navigation.JointSenseNavHost

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as JointSenseApplication).container
        setContent {
            JointSenseTheme {
                JointSenseApp(container)
            }
        }
    }
}

@Composable
fun JointSenseApp(
    appContainer: AppContainer,
) {
    MigrationGate(coordinator = appContainer.migrationCoordinator) {
        JointSenseNavHost(appContainer = appContainer)
    }
}
