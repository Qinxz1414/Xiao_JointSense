package cloud.univ.jointsense.insights

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomeRouteScreen(
    viewModel: InsightsViewModel,
    onStartMeasurement: () -> Unit,
    onRestoreSamples: () -> Unit,
    onOpenReport: () -> Unit,
) {
    HomeScreen(
        state = viewModel.homeState.collectAsStateWithLifecycle().value,
        onTestNow = onStartMeasurement,
        onRestoreSamples = onRestoreSamples,
        onOpenReport = onOpenReport,
    )
}

@Composable
fun TrendsRouteScreen(viewModel: InsightsViewModel) {
    TrendsScreen(state = viewModel.trendsState.collectAsStateWithLifecycle().value)
}

@Composable
fun ReportRouteScreen(viewModel: InsightsViewModel) {
    ReportScreen(state = viewModel.reportState.collectAsStateWithLifecycle().value)
}
