package cloud.univ.jointsense.calibration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CalibrationSelectRouteScreen(
    viewModel: CalibrationViewModel,
    onImageReady: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.imageReadyToOpenCrop) {
        if (state.imageReadyToOpenCrop) {
            viewModel.consumeImageReady()
            onImageReady()
        }
    }
    CalibrationSelectScreen(state, viewModel::onImageSelected, onBack)
}

@Composable
fun CalibrationCropRouteScreen(
    viewModel: CalibrationViewModel,
    onSignalsReady: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.signalsReadyToOpenAssign) {
        if (state.signalsReadyToOpenAssign) {
            viewModel.consumeSignalsReady()
            onSignalsReady()
        }
    }
    CalibrationCropScreen(
        state = state,
        onCropChanged = viewModel::updateCrop,
        onDetect = viewModel::detectSignals,
        onBack = onBack,
    )
}

@Composable
fun CalibrationAssignRouteScreen(
    viewModel: CalibrationViewModel,
    onReviewReady: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalibrationAssignScreen(
        state = state,
        onFactorChanged = viewModel::selectFactor,
        onConcentrationChanged = viewModel::updateConcentration,
        onReview = { if (viewModel.review()) onReviewReady() },
        onBack = onBack,
    )
}

@Composable
fun CalibrationReviewRouteScreen(
    viewModel: CalibrationViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saveCompleted) {
        if (state.saveCompleted) {
            viewModel.consumeSaveCompleted()
            onSaved()
        }
    }
    CalibrationReviewScreen(state, viewModel::save, onBack)
}

@Composable
fun CalibrationDoneRouteScreen(
    viewModel: CalibrationViewModel,
    onDone: () -> Unit,
    onAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.factoryRestoreCompleted) {
        if (state.factoryRestoreCompleted) {
            viewModel.consumeFactoryRestoreCompleted()
            onAnother()
        }
    }
    CalibrationDoneScreen(
        state = state,
        onDone = onDone,
        onAnother = {
            viewModel.resetForAnotherFactor()
            onAnother()
        },
        onRestoreConfirmed = viewModel::confirmRestoreFactory,
        onBack = onBack,
    )
}
