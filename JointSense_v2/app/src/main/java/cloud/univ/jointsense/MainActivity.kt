package cloud.univ.jointsense

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import cloud.univ.jointsense.navigation.JointSenseNavHost
import cloud.univ.jointsense.ui.theme.JointSenseTheme
import cloud.univ.jointsense.viewmodel.JointSenseViewModel

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JointSenseTheme {
                JointSenseApp()
            }
        }
    }
}

@Composable
fun JointSenseApp(
    viewModel: JointSenseViewModel = viewModel(),
) {
    JointSenseNavHost(viewModel = viewModel)
}
