package cloud.univ.jointsense.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cloud.univ.jointsense.ui.screens.CalibrationFlowScreen
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
import cloud.univ.jointsense.ui.theme.PrimaryAccent
import cloud.univ.jointsense.ui.theme.TextSecondary
import cloud.univ.jointsense.viewmodel.JointSenseViewModel

typealias JointSenseScreenSlot =
    @Composable (route: JointSenseRoute, actions: NavigationActions) -> Unit

@Composable
fun JointSenseNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: JointSenseViewModel = viewModel(),
    screenSlot: JointSenseScreenSlot? = null,
) {
    val actions = remember(navController) { NavigationActions(navController) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val topLevelDestination = currentEntry?.destination?.topLevelDestination()

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (topLevelDestination != null) {
                    MainBottomBar(
                        activeDestination = topLevelDestination,
                        onDestination = actions::openTopLevel,
                    )
                }
            },
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(BgLight),
            ) {
                composable<HomeRoute> {
                    Destination(screenSlot, HomeRoute, actions) {
                        HomeScreen(
                            viewModel = viewModel,
                            onTestNow = {
                                viewModel.createNewSession()
                                actions.startMeasurement(TopLevelDestination.HOME)
                            },
                            onOpenReport = {
                                actions.openTopLevel(TopLevelDestination.REPORT)
                            },
                        )
                    }
                }
                composable<TrendsRoute> {
                    Destination(screenSlot, TrendsRoute, actions) {
                        TrendsScreen(viewModel = viewModel)
                    }
                }
                composable<ReportRoute> {
                    Destination(screenSlot, ReportRoute, actions) {
                        ReportScreen(viewModel = viewModel)
                    }
                }
                composable<ProfileRoute> {
                    Destination(screenSlot, ProfileRoute, actions) {
                        ProfileScreen(
                            viewModel = viewModel,
                            onOpenHistory = actions::openHistory,
                            onCalibrate = actions::startCalibration,
                        )
                    }
                }
                composable<HistoryRoute> {
                    Destination(screenSlot, HistoryRoute, actions) {
                        HistoryScreen(
                            sessions = viewModel.sessions,
                            onSessionClick = { session ->
                                session.results.lastOrNull()?.let { result ->
                                    viewModel.selectSession(session)
                                    actions.openResult(result.id)
                                }
                            },
                            onDeleteSession = viewModel::deleteSession,
                            onBack = { actions.navigateBack() },
                        )
                    }
                }

                navigation<MeasurementGraph>(startDestination = ImageSelectRoute) {
                    composable<ImageSelectRoute> {
                        BackHandler {
                            viewModel.abandonMeasurement()
                            actions.exitMeasurement()
                        }
                        Destination(screenSlot, ImageSelectRoute, actions) {
                            ImageSelectScreen(
                                onImageSelected = { bitmap ->
                                    viewModel.setImage(bitmap)
                                    actions.openCrop()
                                },
                                onBack = {
                                    viewModel.abandonMeasurement()
                                    actions.exitMeasurement()
                                },
                                sessionName = viewModel.currentSession?.name ?: "New Test",
                            )
                        }
                    }
                    composable<CropRoute> {
                        Destination(screenSlot, CropRoute, actions) {
                            viewModel.selectedBitmap?.let { bitmap ->
                                ImageCropScreen(
                                    bitmap = bitmap,
                                    cropRect = viewModel.cropRect,
                                    onCropRectChanged = viewModel::updateCropRect,
                                    onConfirm = actions::openFactorSelect,
                                    onBack = { actions.navigateBack() },
                                )
                            }
                        }
                    }
                    composable<FactorSelectRoute> {
                        LaunchedEffect(viewModel) {
                            viewModel.analysisCompletions.collect(actions::openResult)
                        }
                        Destination(screenSlot, FactorSelectRoute, actions) {
                            FactorSelectScreen(
                                selectedFactor = viewModel.selectedFactor,
                                onFactorSelected = viewModel::selectFactor,
                                onAnalyze = viewModel::analyze,
                                onBack = { actions.navigateBack() },
                                isAnalyzing = viewModel.isAnalyzing,
                            )
                        }
                    }
                }

                composable<ResultRoute> { entry ->
                    val route = entry.toRoute<ResultRoute>()
                    val inMeasurement = actions.isInMeasurement()
                    BackHandler {
                        if (inMeasurement) viewModel.finishMeasurement()
                        actions.exitResult()
                    }
                    Destination(screenSlot, route, actions) {
                        val session = viewModel.currentSession
                            ?: viewModel.sessions.firstOrNull { candidate ->
                                candidate.results.any { it.id == route.resultId }
                            }
                        val result = session?.results?.firstOrNull { it.id == route.resultId }
                        ResultScreen(
                            session = session,
                            lastResult = result,
                            canAddMore = viewModel.canAddMoreTests(),
                            onNewTest = {
                                viewModel.startNewTestInSession()
                                if (inMeasurement) actions.restartMeasurement()
                            },
                            onGoHome = {
                                viewModel.finishMeasurement()
                                actions.goHome()
                            },
                        )
                    }
                }

                navigation<CalibrationGraph>(startDestination = CalibrationSelectRoute) {
                    composable<CalibrationSelectRoute> {
                        CalibrationDestination(screenSlot, CalibrationSelectRoute, actions)
                    }
                    composable<CalibrationCropRoute> {
                        CalibrationDestination(screenSlot, CalibrationCropRoute, actions)
                    }
                    composable<CalibrationAssignRoute> {
                        CalibrationDestination(screenSlot, CalibrationAssignRoute, actions)
                    }
                    composable<CalibrationReviewRoute> {
                        CalibrationDestination(screenSlot, CalibrationReviewRoute, actions)
                    }
                    composable<CalibrationDoneRoute> {
                        CalibrationDestination(screenSlot, CalibrationDoneRoute, actions)
                    }
                }
            }
        }

        if (topLevelDestination != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 42.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(4.dp, BgWhite, CircleShape)
                        .clip(CircleShape)
                        .background(PrimaryAccent)
                        .clickable {
                            viewModel.createNewSession()
                            actions.startMeasurement(topLevelDestination)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "New test",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Destination(
    screenSlot: JointSenseScreenSlot?,
    route: JointSenseRoute,
    actions: NavigationActions,
    content: @Composable () -> Unit,
) {
    if (screenSlot == null) content() else screenSlot(route, actions)
}

@Composable
private fun CalibrationDestination(
    screenSlot: JointSenseScreenSlot?,
    route: JointSenseRoute,
    actions: NavigationActions,
) {
    Destination(screenSlot, route, actions) {
        // Compatibility entry while the legacy calibration screen owns its
        // internal five-step state. Task 7 can replace each registered route.
        CalibrationFlowScreen(onExit = { actions.exitCalibration() })
    }
}

private fun NavDestination.topLevelDestination(): TopLevelDestination? = when {
    route == HomeRoute::class.qualifiedName -> TopLevelDestination.HOME
    route == TrendsRoute::class.qualifiedName -> TopLevelDestination.TRENDS
    route == ReportRoute::class.qualifiedName -> TopLevelDestination.REPORT
    route == ProfileRoute::class.qualifiedName -> TopLevelDestination.PROFILE
    else -> null
}

@Composable
private fun MainBottomBar(
    activeDestination: TopLevelDestination,
    onDestination: (TopLevelDestination) -> Unit,
) {
    Surface(
        color = BgWhite,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarTab(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = activeDestination == TopLevelDestination.HOME,
                    onClick = { onDestination(TopLevelDestination.HOME) },
                )
                BarTab(
                    icon = Icons.Default.ShowChart,
                    label = "Trends",
                    selected = activeDestination == TopLevelDestination.TRENDS,
                    onClick = { onDestination(TopLevelDestination.TRENDS) },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        text = "Test",
                        fontSize = 10.sp,
                        color = PrimaryAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                BarTab(
                    icon = Icons.Default.Description,
                    label = "Report",
                    selected = activeDestination == TopLevelDestination.REPORT,
                    onClick = { onDestination(TopLevelDestination.REPORT) },
                )
                BarTab(
                    icon = Icons.Default.Person,
                    label = "Profile",
                    selected = activeDestination == TopLevelDestination.PROFILE,
                    onClick = { onDestination(TopLevelDestination.PROFILE) },
                )
            }
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsBottomHeight(WindowInsets.navigationBars),
            )
        }
    }
}

@Composable
private fun RowScope.BarTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) PrimaryAccent else TextSecondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = if (selected) PrimaryAccent else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
