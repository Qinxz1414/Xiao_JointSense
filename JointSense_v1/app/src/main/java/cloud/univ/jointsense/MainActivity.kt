package cloud.univ.jointsense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import cloud.univ.jointsense.ui.screens.FactorSelectScreen
import cloud.univ.jointsense.ui.screens.HistoryScreen
import cloud.univ.jointsense.ui.screens.HomeScreen
import cloud.univ.jointsense.ui.screens.ImageCropScreen
import cloud.univ.jointsense.ui.screens.ImageSelectScreen
import cloud.univ.jointsense.ui.screens.ResultScreen
import cloud.univ.jointsense.ui.theme.JointSenseTheme
import cloud.univ.jointsense.viewmodel.JointSenseViewModel
import cloud.univ.jointsense.viewmodel.Screen

class MainActivity : ComponentActivity() {
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
    viewModel: JointSenseViewModel = viewModel()
) {
    AnimatedContent(
        targetState = viewModel.currentScreen,
        transitionSpec = {
            (fadeIn() + slideInHorizontally { it / 4 })
                .togetherWith(fadeOut() + slideOutHorizontally { -it / 4 })
        },
        label = "screen_transition",
        modifier = Modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            Screen.HOME -> {
                HomeScreen(
                    onNewTest = { viewModel.createNewSession() },
                    onHistory = { viewModel.navigateTo(Screen.HISTORY) },
                    testCount = viewModel.sessions.size
                )
            }

            Screen.IMAGE_SELECT -> {
                ImageSelectScreen(
                    onImageSelected = { bitmap -> viewModel.setImage(bitmap) },
                    onBack = { viewModel.goHome() },
                    sessionName = viewModel.currentSession?.name ?: "New Test"
                )
            }

            Screen.IMAGE_CROP -> {
                val bitmap = viewModel.selectedBitmap
                if (bitmap != null) {
                    ImageCropScreen(
                        bitmap = bitmap,
                        cropRect = viewModel.cropRect,
                        onCropRectChanged = { rect -> viewModel.updateCropRect(rect) },
                        onConfirm = { viewModel.confirmCrop() },
                        onBack = { viewModel.navigateTo(Screen.IMAGE_SELECT) }
                    )
                }
            }

            Screen.FACTOR_SELECT -> {
                FactorSelectScreen(
                    selectedFactor = viewModel.selectedFactor,
                    onFactorSelected = { factor -> viewModel.selectFactor(factor) },
                    onAnalyze = { viewModel.analyze() },
                    onBack = { viewModel.navigateTo(Screen.IMAGE_CROP) },
                    isAnalyzing = viewModel.isAnalyzing
                )
            }

            Screen.RESULT -> {
                ResultScreen(
                    session = viewModel.currentSession,
                    lastResult = viewModel.lastResult,
                    canAddMore = viewModel.canAddMoreTests(),
                    onNewTest = { viewModel.startNewTestInSession() },
                    onGoHome = { viewModel.goHome() }
                )
            }

            Screen.HISTORY -> {
                HistoryScreen(
                    sessions = viewModel.sessions,
                    onSessionClick = { session -> viewModel.selectSession(session) },
                    onDeleteSession = { session -> viewModel.deleteSession(session) },
                    onBack = { viewModel.goHome() }
                )
            }
        }
    }
}
