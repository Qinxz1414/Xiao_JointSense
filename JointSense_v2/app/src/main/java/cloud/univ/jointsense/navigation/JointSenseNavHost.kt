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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import cloud.univ.jointsense.calibration.CalibrationRouteScreen
import cloud.univ.jointsense.designsystem.theme.BgLight
import cloud.univ.jointsense.designsystem.theme.BgWhite
import cloud.univ.jointsense.designsystem.theme.PrimaryAccent
import cloud.univ.jointsense.designsystem.theme.TextSecondary
import cloud.univ.jointsense.di.AppContainer
import cloud.univ.jointsense.insights.HomeRouteScreen
import cloud.univ.jointsense.insights.InsightsViewModel
import cloud.univ.jointsense.insights.InsightsViewModelFactory
import cloud.univ.jointsense.insights.ReportRouteScreen
import cloud.univ.jointsense.insights.TrendsRouteScreen
import cloud.univ.jointsense.measurement.CropRouteScreen
import cloud.univ.jointsense.measurement.FactorSelectRouteScreen
import cloud.univ.jointsense.measurement.HistoryRouteScreen
import cloud.univ.jointsense.measurement.ImageSelectRouteScreen
import cloud.univ.jointsense.measurement.MeasurementViewModel
import cloud.univ.jointsense.measurement.MeasurementViewModelFactory
import cloud.univ.jointsense.measurement.ResultRouteScreen
import cloud.univ.jointsense.settings.SettingsRouteScreen
import cloud.univ.jointsense.settings.SettingsViewModel
import cloud.univ.jointsense.settings.SettingsViewModelFactory

typealias JointSenseScreenSlot =
    @Composable (route: JointSenseRoute, actions: NavigationActions) -> Unit

@Composable
fun JointSenseNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    appContainer: AppContainer? = null,
    screenSlot: JointSenseScreenSlot? = null,
) {
    require(appContainer != null || screenSlot != null) {
        "JointSenseNavHost needs an AppContainer unless a test screenSlot is supplied"
    }
    val featureViewModels = if (appContainer == null) {
        null
    } else {
        rememberFeatureViewModels(appContainer)
    }
    val actions = remember(navController) { NavigationActions(navController) }
    val currentEntry by navController.currentBackStackEntryAsState()
    val topLevelDestination = currentEntry?.destination?.topLevelDestination()
    val snackbarHostState = remember { SnackbarHostState() }
    val measurementState = featureViewModels?.measurement?.state
        ?.collectAsStateWithLifecycle()?.value
    val sessionCreationError = measurementState?.sessionCreationError
    val sessionCreationDriver = featureViewModels?.measurement?.let { measurement ->
        remember(measurement, actions) {
            SessionCreationNavigationDriver(measurement, actions)
        }
    }

    LaunchedEffect(
        sessionCreationDriver,
        currentEntry != null,
        topLevelDestination,
        measurementState?.sessionCreationRequest,
    ) {
        sessionCreationDriver?.synchronize(
            currentOrigin = topLevelDestination,
            routeReady = currentEntry != null,
        )
    }

    LaunchedEffect(sessionCreationError) {
        val message = sessionCreationError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        featureViewModels?.measurement?.consumeSessionCreationError()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        val viewModels = requireNotNull(featureViewModels)
                        HomeRouteScreen(
                            viewModel = viewModels.insights,
                            onStartMeasurement = {
                                requireNotNull(sessionCreationDriver)
                                    .request(TopLevelDestination.HOME)
                            },
                            onOpenReport = {
                                actions.openTopLevel(TopLevelDestination.REPORT)
                            },
                        )
                    }
                }
                composable<TrendsRoute> {
                    Destination(screenSlot, TrendsRoute, actions) {
                        TrendsRouteScreen(requireNotNull(featureViewModels).insights)
                    }
                }
                composable<ReportRoute> {
                    Destination(screenSlot, ReportRoute, actions) {
                        ReportRouteScreen(requireNotNull(featureViewModels).insights)
                    }
                }
                composable<ProfileRoute> {
                    Destination(screenSlot, ProfileRoute, actions) {
                        val container = requireNotNull(appContainer)
                        SettingsRouteScreen(
                            viewModel = requireNotNull(featureViewModels).settings,
                            languageController = container.languageController,
                            onOpenHistory = actions::openHistory,
                            onCalibrate = actions::startCalibration,
                        )
                    }
                }
                composable<HistoryRoute> {
                    Destination(screenSlot, HistoryRoute, actions) {
                        HistoryRouteScreen(
                            viewModel = requireNotNull(featureViewModels).measurement,
                            onOpenResult = actions::openResult,
                            onBack = { actions.navigateBack() },
                        )
                    }
                }

                navigation<MeasurementGraph>(startDestination = ImageSelectRoute) {
                    composable<ImageSelectRoute> {
                        if (screenSlot == null) {
                            BackHandler {
                                requireNotNull(featureViewModels).measurement.abandonMeasurement()
                                actions.exitMeasurement()
                            }
                        }
                        Destination(screenSlot, ImageSelectRoute, actions) {
                            val measurement = requireNotNull(featureViewModels).measurement
                            ImageSelectRouteScreen(
                                viewModel = measurement,
                                onImageReady = actions::openCrop,
                                onBack = {
                                    measurement.abandonMeasurement()
                                    actions.exitMeasurement()
                                },
                            )
                        }
                    }
                    composable<CropRoute> {
                        Destination(screenSlot, CropRoute, actions) {
                            CropRouteScreen(
                                viewModel = requireNotNull(featureViewModels).measurement,
                                onConfirm = actions::openFactorSelect,
                                onBack = { actions.navigateBack() },
                            )
                        }
                    }
                    composable<FactorSelectRoute> {
                        Destination(screenSlot, FactorSelectRoute, actions) {
                            FactorSelectRouteScreen(
                                viewModel = requireNotNull(featureViewModels).measurement,
                                onResultReady = actions::openResult,
                                onBack = { actions.navigateBack() },
                            )
                        }
                    }
                }

                composable<ResultRoute> { entry ->
                    val route = entry.toRoute<ResultRoute>()
                    val inMeasurement = actions.isInMeasurement()
                    BackHandler {
                        if (inMeasurement) featureViewModels?.measurement?.finishMeasurement()
                        actions.exitResult()
                    }
                    Destination(screenSlot, route, actions) {
                        val measurement = requireNotNull(featureViewModels).measurement
                        val state = measurement.state.collectAsStateWithLifecycle().value
                        val session = state.currentSession ?: state.sessions.firstOrNull { candidate ->
                            candidate.results.any { it.id == route.resultId }
                        }
                        val canContinue = session?.results?.size?.let { it < 5 } == true
                        ResultRouteScreen(
                            viewModel = measurement,
                            resultId = route.resultId,
                            onRetest = {
                                if (session != null && canContinue) {
                                    measurement.selectSession(session.id)
                                    measurement.startNewTestInSession()
                                    if (inMeasurement) {
                                        actions.restartMeasurement()
                                    } else {
                                        actions.continueMeasurementFromResult(
                                            TopLevelDestination.PROFILE,
                                        )
                                    }
                                }
                            },
                            onFinish = {
                                measurement.finishMeasurement()
                                actions.goHome()
                            },
                        )
                    }
                }

                navigation<CalibrationGraph>(startDestination = CalibrationSelectRoute) {
                    composable<CalibrationSelectRoute> {
                        CalibrationDestination(screenSlot, CalibrationSelectRoute, actions, appContainer)
                    }
                    composable<CalibrationCropRoute> {
                        CalibrationDestination(screenSlot, CalibrationCropRoute, actions, appContainer)
                    }
                    composable<CalibrationAssignRoute> {
                        CalibrationDestination(screenSlot, CalibrationAssignRoute, actions, appContainer)
                    }
                    composable<CalibrationReviewRoute> {
                        CalibrationDestination(screenSlot, CalibrationReviewRoute, actions, appContainer)
                    }
                    composable<CalibrationDoneRoute> {
                        CalibrationDestination(screenSlot, CalibrationDoneRoute, actions, appContainer)
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
                            sessionCreationDriver?.request(topLevelDestination)
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

private data class FeatureViewModels(
    val insights: InsightsViewModel,
    val measurement: MeasurementViewModel,
    val settings: SettingsViewModel,
)

@Composable
private fun rememberFeatureViewModels(container: AppContainer): FeatureViewModels {
    val insightsFactory = remember(container) { InsightsViewModelFactory(container.testSessions) }
    val measurementFactory = remember(container) {
        MeasurementViewModelFactory(container.testSessions, container.measurementAnalysis)
    }
    val settingsFactory = remember(container) {
        SettingsViewModelFactory(
            container.testSessions,
            container.calibrations,
            container.dataManagement,
        )
    }
    return FeatureViewModels(
        insights = viewModel(key = "insights", factory = insightsFactory),
        measurement = viewModel(key = "measurement", factory = measurementFactory),
        settings = viewModel(key = "settings", factory = settingsFactory),
    )
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
    appContainer: AppContainer?,
) {
    Destination(screenSlot, route, actions) {
        CalibrationRouteScreen(
            repository = requireNotNull(appContainer).calibrations,
            onExit = actions::exitCalibration,
        )
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
                    icon = Icons.AutoMirrored.Filled.ShowChart,
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
