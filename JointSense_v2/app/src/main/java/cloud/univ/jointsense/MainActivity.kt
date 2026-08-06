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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cloud.univ.jointsense.ui.screens.FactorSelectScreen
import cloud.univ.jointsense.ui.screens.HistoryScreen
import cloud.univ.jointsense.ui.screens.HomeScreen
import cloud.univ.jointsense.ui.screens.ImageCropScreen
import cloud.univ.jointsense.ui.screens.ImageSelectScreen
import cloud.univ.jointsense.ui.screens.ProfileScreen
import cloud.univ.jointsense.ui.screens.ReportScreen
import cloud.univ.jointsense.ui.screens.ResultScreen
import cloud.univ.jointsense.ui.screens.TrendsScreen
import cloud.univ.jointsense.ui.theme.BgLight
import cloud.univ.jointsense.ui.theme.BgWhite
import cloud.univ.jointsense.ui.theme.JointSenseTheme
import cloud.univ.jointsense.ui.theme.PrimaryAccent
import cloud.univ.jointsense.ui.theme.TextSecondary
import cloud.univ.jointsense.viewmodel.FlowScreen
import cloud.univ.jointsense.viewmodel.JointSenseViewModel
import cloud.univ.jointsense.viewmodel.MainTab

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
    val flow = viewModel.flowScreen

    if (flow != null) {
        // Full-screen test flow / history above the tab scaffold
        AnimatedContent(
            targetState = flow,
            transitionSpec = {
                (fadeIn() + slideInHorizontally { it / 4 })
                    .togetherWith(fadeOut() + slideOutHorizontally { -it / 4 })
            },
            label = "flow_transition",
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            when (screen) {
                FlowScreen.IMAGE_SELECT -> {
                    ImageSelectScreen(
                        onImageSelected = { bitmap -> viewModel.setImage(bitmap) },
                        onBack = { viewModel.exitFlow() },
                        sessionName = viewModel.currentSession?.name ?: "New Test"
                    )
                }

                FlowScreen.IMAGE_CROP -> {
                    val bitmap = viewModel.selectedBitmap
                    if (bitmap != null) {
                        ImageCropScreen(
                            bitmap = bitmap,
                            cropRect = viewModel.cropRect,
                            onCropRectChanged = { rect -> viewModel.updateCropRect(rect) },
                            onConfirm = { viewModel.confirmCrop() },
                            onBack = { viewModel.navigateToFlow(FlowScreen.IMAGE_SELECT) }
                        )
                    }
                }

                FlowScreen.FACTOR_SELECT -> {
                    FactorSelectScreen(
                        selectedFactor = viewModel.selectedFactor,
                        onFactorSelected = { factor -> viewModel.selectFactor(factor) },
                        onAnalyze = { viewModel.analyze() },
                        onBack = { viewModel.navigateToFlow(FlowScreen.IMAGE_CROP) },
                        isAnalyzing = viewModel.isAnalyzing
                    )
                }

                FlowScreen.RESULT -> {
                    ResultScreen(
                        session = viewModel.currentSession,
                        lastResult = viewModel.lastResult,
                        canAddMore = viewModel.canAddMoreTests(),
                        onNewTest = { viewModel.startNewTestInSession() },
                        onGoHome = { viewModel.goHome() }
                    )
                }

                FlowScreen.HISTORY -> {
                    HistoryScreen(
                        sessions = viewModel.sessions,
                        onSessionClick = { session -> viewModel.selectSession(session) },
                        onDeleteSession = { session -> viewModel.deleteSession(session) },
                        onBack = { viewModel.exitFlow() }
                    )
                }
            }
        }
    } else {
        // Tab scaffold
        Scaffold(
            bottomBar = {
                MainBottomBar(
                    activeTab = viewModel.activeTab,
                    onTab = { tab -> viewModel.selectTab(tab) },
                    onTest = { viewModel.createNewSession() }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(BgLight)
            ) {
                when (viewModel.activeTab) {
                    MainTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onTestNow = { viewModel.createNewSession() },
                        onOpenReport = { viewModel.selectTab(MainTab.REPORT) }
                    )
                    MainTab.TRENDS -> TrendsScreen(viewModel = viewModel)
                    MainTab.REPORT -> ReportScreen(viewModel = viewModel)
                    MainTab.PROFILE -> ProfileScreen(
                        viewModel = viewModel,
                        onOpenHistory = { viewModel.navigateToFlow(FlowScreen.HISTORY) }
                    )
                }
            }
        }
    }
}

/**
 * Bottom navigation: Home / Trends / (Test) / Report / Profile with a
 * raised center camera action, matching the design mockup.
 */
@Composable
private fun MainBottomBar(
    activeTab: MainTab,
    onTab: (MainTab) -> Unit,
    onTest: () -> Unit
) {
    Surface(
        color = BgWhite,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarTab(
                icon = Icons.Default.Home,
                label = "Home",
                selected = activeTab == MainTab.HOME,
                onClick = { onTab(MainTab.HOME) }
            )
            BarTab(
                icon = Icons.Default.ShowChart,
                label = "Trends",
                selected = activeTab == MainTab.TRENDS,
                onClick = { onTab(MainTab.TRENDS) }
            )

            // Center raised test action
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .offset(y = (-14).dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(PrimaryAccent)
                            .clickable { onTest() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "New test",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "Test",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        modifier = Modifier.offset(y = (-10).dp)
                    )
                }
            }

            BarTab(
                icon = Icons.Default.Description,
                label = "Report",
                selected = activeTab == MainTab.REPORT,
                onClick = { onTab(MainTab.REPORT) }
            )
            BarTab(
                icon = Icons.Default.Person,
                label = "Profile",
                selected = activeTab == MainTab.PROFILE,
                onClick = { onTab(MainTab.PROFILE) }
            )
        }
    }
}

@Composable
private fun RowScope.BarTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) PrimaryAccent else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (selected) PrimaryAccent else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
