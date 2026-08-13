package cloud.univ.jointsense.measurement

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.feature.measurement.R

@Composable
fun ImageSelectRouteScreen(
    viewModel: MeasurementViewModel,
    onImageReady: () -> Unit,
    onBack: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val activity = context.findActivity()
    var permissionResultRequestToken by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionResultDraftId by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        viewModel.onAction(MeasurementAction.CameraCaptureCompleted(success))
    }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.onAction(MeasurementAction.PickedImageSelected(it.toString())) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val resultToken = permissionResultRequestToken
        val resultDraft = permissionResultDraftId
        if (resultToken != null && resultDraft != null) {
            permissionResultRequestToken = null
            permissionResultDraftId = null
            val shouldShowRationale = !granted && activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } == true
            viewModel.onAction(
                MeasurementAction.CameraPermissionResult(
                    claim = CameraPermissionLaunchClaim(resultToken, resultDraft),
                    granted = granted,
                    shouldShowRationale = shouldShowRationale,
                ),
            )
        }
    }

    LaunchedEffect(viewModel, cameraLauncher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MeasurementEffect.RequestCameraPermission ->
                    viewModel.claimCameraPermissionLaunch(effect)?.let { claim ->
                        launchClaimedCameraPermission(
                            claim = claim,
                            launch = {
                                permissionResultRequestToken = claim.requestToken
                                permissionResultDraftId = claim.draftId
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            onAcknowledged = {
                                viewModel.onAction(
                                    MeasurementAction.CameraPermissionLaunchAcknowledged(claim),
                                )
                            },
                            onFailure = { reason ->
                                if (permissionResultRequestToken == claim.requestToken &&
                                    permissionResultDraftId == claim.draftId
                                ) {
                                    permissionResultRequestToken = null
                                    permissionResultDraftId = null
                                }
                                viewModel.onAction(
                                    MeasurementAction.CameraPermissionLaunchFailed(claim, reason),
                                )
                            },
                        )
                    }
                is MeasurementEffect.LaunchCamera -> viewModel.claimCameraLaunch(effect)?.let { claim ->
                    launchClaimedCamera(
                        claim = claim,
                        launch = { cameraLauncher.launch(Uri.parse(it)) },
                        onAcknowledged = {
                            viewModel.onAction(MeasurementAction.CameraLaunchAcknowledged(claim))
                        },
                        onFailure = { reason ->
                            viewModel.onAction(MeasurementAction.CameraLaunchFailed(claim, reason))
                        },
                    )
                }
                is MeasurementEffect.NavigateToResult -> Unit
            }
        }
    }
    LaunchedEffect(state.stage) {
        if (state.stage == Stage.ReadyToCrop) onImageReady()
    }

    when (state.stage) {
        Stage.AwaitingImage -> ImageSelectScreen(
            onTakePhoto = {
                handleTakePhotoRequest(
                    cameraPermissionGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED,
                    onCaptureRequested = {
                        viewModel.onAction(MeasurementAction.CameraCaptureRequested)
                    },
                    onPermissionRequested = {
                        viewModel.onAction(MeasurementAction.CameraPermissionRequestStarted)
                    },
                )
            },
            onPickImage = {
                viewModel.onAction(MeasurementAction.GallerySelectionStarted)
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onBack = onBack,
            sessionName = state.currentSession?.localizedDisplayName()
                ?: stringResource(R.string.measurement_new_test),
        )
        Stage.Decoding -> MeasurementProgressContent(stringResource(R.string.measurement_progress_preparing_image))
        Stage.RecoverableError -> MeasurementErrorContent(
            error = state.error ?: MeasurementError.ImageUnreadable,
            onRetry = { viewModel.onAction(MeasurementAction.Retry) },
            onOpenSettings = { context.openApplicationSettings() },
        )
        Stage.ReadyToCrop -> MeasurementProgressContent(stringResource(R.string.measurement_progress_opening_crop))
        Stage.ReadyToAnalyze,
        Stage.Analyzing,
        Stage.Persisting,
        Stage.Success,
        -> MeasurementProgressContent(stringResource(R.string.measurement_progress_restoring))
    }
}

@Composable
fun CropRouteScreen(
    viewModel: MeasurementViewModel,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val performBack = {
        viewModel.onAction(MeasurementAction.BackToImageSelection)
        onBack()
    }
    BackHandler(onBack = performBack)
    LaunchedEffect(state.stage) {
        if (state.stage == Stage.ReadyToAnalyze) onConfirm()
    }
    when (state.stage) {
        Stage.ReadyToCrop -> {
            val bitmap = (state.image as? BitmapMeasurementImage)?.bitmap
            if (bitmap == null) {
                MeasurementErrorContent(
                    error = MeasurementError.ImageUnreadable,
                    onRetry = {
                        viewModel.onAction(MeasurementAction.BackToImageSelection)
                        onBack()
                    },
                    onOpenSettings = {},
                )
            } else {
                ImageCropScreen(
                    bitmap = bitmap,
                    cropRect = state.cropBounds.toRect(),
                    onCropRectChanged = {
                        viewModel.onAction(MeasurementAction.CropChanged(it.toBounds()))
                    },
                    onConfirm = { viewModel.onAction(MeasurementAction.CropConfirmed) },
                    onBack = performBack,
                )
            }
        }
        Stage.RecoverableError -> MeasurementErrorContent(
            error = state.error ?: MeasurementError.InvalidCrop,
            onRetry = { viewModel.onAction(MeasurementAction.Retry) },
            onOpenSettings = {},
        )
        Stage.Decoding -> MeasurementProgressContent(stringResource(R.string.measurement_progress_restoring_image))
        Stage.ReadyToAnalyze -> MeasurementProgressContent(stringResource(R.string.measurement_progress_opening_factor))
        Stage.Analyzing,
        Stage.Persisting,
        Stage.Success,
        -> MeasurementProgressContent(stringResource(R.string.measurement_progress_in_progress))
        Stage.AwaitingImage -> MeasurementProgressContent(stringResource(R.string.measurement_progress_returning_image))
    }
}

@Composable
fun FactorSelectRouteScreen(
    viewModel: MeasurementViewModel,
    onResultReady: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val isBusy = state.stage == Stage.Analyzing || state.stage == Stage.Persisting
    val canReturnToCrop = state.stage == Stage.ReadyToAnalyze ||
        state.stage == Stage.RecoverableError
    val performBack = {
        if (canReturnToCrop) {
            viewModel.onAction(MeasurementAction.BackToCrop)
            if (viewModel.state.value.stage == Stage.ReadyToCrop) onBack()
        }
    }
    BackHandler(onBack = performBack)
    LaunchedEffect(viewModel, onResultReady) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MeasurementEffect.NavigateToResult -> onResultReady(effect.resultId)
                is MeasurementEffect.LaunchCamera -> Unit
                is MeasurementEffect.RequestCameraPermission -> Unit
            }
        }
    }
    when (state.stage) {
        Stage.ReadyToAnalyze,
        Stage.Analyzing,
        Stage.Persisting,
        -> FactorSelectScreen(
            selectedFactor = state.factor,
            onFactorSelected = { viewModel.onAction(MeasurementAction.FactorSelected(it)) },
            onAnalyze = { viewModel.onAction(MeasurementAction.Analyze) },
            onBack = performBack,
            isAnalyzing = isBusy,
            backEnabled = canReturnToCrop,
        )
        Stage.RecoverableError -> MeasurementErrorContent(
            error = state.error ?: MeasurementError.AnalysisFailed,
            onRetry = { viewModel.onAction(MeasurementAction.Retry) },
            onOpenSettings = {},
        )
        Stage.Success -> MeasurementProgressContent(stringResource(R.string.measurement_progress_opening_result))
        Stage.AwaitingImage,
        Stage.Decoding,
        Stage.ReadyToCrop,
        -> MeasurementProgressContent(stringResource(R.string.measurement_progress_restoring))
    }
}

@Composable
fun ResultRouteScreen(
    viewModel: MeasurementViewModel,
    resultId: String,
    onContinueMeasurement: () -> Unit,
    onReturnToOrigin: () -> Unit,
    onGoHome: () -> Unit,
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val resolution = resolveResultById(
        resultId = resultId,
        currentSession = state.currentSession,
        sessions = state.sessions,
        hasReceivedSessionsSnapshot = state.hasReceivedSessionsSnapshot,
    )
    ResultScreen(
        resolution = resolution,
        cleanupWarning = state.captureCleanupWarning,
        onContinueMeasurement = onContinueMeasurement,
        onReturnToOrigin = onReturnToOrigin,
        onGoHome = onGoHome,
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

private fun Context.openApplicationSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun CropBounds.toRect(): Rect = Rect(left, top, right, bottom)
private fun Rect.toBounds(): CropBounds = CropBounds(left, top, right, bottom)
