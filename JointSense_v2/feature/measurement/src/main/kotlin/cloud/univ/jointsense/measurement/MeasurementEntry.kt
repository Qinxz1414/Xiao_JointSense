package cloud.univ.jointsense.measurement

import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession

@Composable
fun ImageSelectRouteScreen(
    viewModel: MeasurementViewModel,
    onImageReady: () -> Unit,
    onBack: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    ImageSelectScreen(
        onImageSelected = { bitmap ->
            viewModel.setImage(BitmapMeasurementImage(bitmap))
            onImageReady()
        },
        onBack = onBack,
        sessionName = state.currentSession?.name ?: "New Test",
    )
}

@Composable
fun CropRouteScreen(
    viewModel: MeasurementViewModel,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val bitmap = (state.image as? BitmapMeasurementImage)?.bitmap ?: return
    ImageCropScreen(
        bitmap = bitmap,
        cropRect = state.cropBounds.toRect(),
        onCropRectChanged = { viewModel.updateCropBounds(it.toBounds()) },
        onConfirm = onConfirm,
        onBack = onBack,
    )
}

@Composable
fun FactorSelectRouteScreen(
    viewModel: MeasurementViewModel,
    onResultReady: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    LaunchedEffect(viewModel, onResultReady) {
        viewModel.analysisCompletions.collect(onResultReady)
    }
    FactorSelectScreen(
        selectedFactor = state.selectedFactor,
        onFactorSelected = viewModel::selectFactor,
        onAnalyze = viewModel::analyze,
        onBack = onBack,
        isAnalyzing = state.isAnalyzing,
    )
}

@Composable
fun ResultRouteScreen(
    viewModel: MeasurementViewModel,
    resultId: String,
    onRetest: () -> Unit,
    onFinish: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val session = state.currentSession ?: state.sessions.firstOrNull { candidate ->
        candidate.results.any { it.id == resultId }
    }
    ResultScreen(
        session = session,
        lastResult = session?.results?.firstOrNull { it.id == resultId },
        canAddMore = session?.results?.size?.let { it < 5 } == true,
        onNewTest = onRetest,
        onGoHome = onFinish,
    )
}

@Composable
fun HistoryRouteScreen(
    viewModel: MeasurementViewModel,
    onOpenResult: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    HistoryScreen(
        sessions = state.sessions,
        onSessionClick = { session ->
            latestHistoryResultId(session)?.let { resultId ->
                viewModel.selectSession(session.id)
                onOpenResult(resultId)
            }
        },
        onDeleteSession = { viewModel.deleteSession(it.id) },
        onBack = onBack,
    )
}

internal fun latestHistoryResultId(session: TestSession): String? = session.results
    .maxWithOrNull(compareBy<TestResult> { it.timestamp }.thenBy { it.id })
    ?.id

private fun CropBounds.toRect(): Rect = Rect(left, top, right, bottom)
private fun Rect.toBounds(): CropBounds = CropBounds(left, top, right, bottom)
