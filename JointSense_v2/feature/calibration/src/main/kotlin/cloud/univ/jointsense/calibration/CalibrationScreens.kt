package cloud.univ.jointsense.calibration

import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cloud.univ.jointsense.analysis.calibration.CalibrationError
import cloud.univ.jointsense.analysis.calibration.CalibrationValidation
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseBarAction
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.feature.calibration.R
import java.io.File

const val CALIBRATION_FACTOR_GROUP_TAG = "calibration:factor-group"
const val CALIBRATION_CROP_VIEW_TAG = "calibration:crop-view"
const val CALIBRATION_DETECT_TAG = "calibration:detect"
const val CALIBRATION_SAVE_TAG = "calibration:save"
const val CALIBRATION_CAPTURE_TAG = "calibration:capture"
const val CALIBRATION_GALLERY_TAG = "calibration:gallery"
const val CALIBRATION_REVIEW_TAG = "calibration:review-curve"
const val CALIBRATION_SELECT_LEGACY_TAG = "calibration:select"
const val CALIBRATION_CROP_LEGACY_TAG = "calibration:crop"
const val CALIBRATION_ASSIGN_LEGACY_TAG = "calibration:assign"
const val CALIBRATION_REVIEW_LEGACY_TAG = "calibration:review"
const val CALIBRATION_DONE_LEGACY_TAG = "calibration:done"
const val SCREEN_CALIBRATION_SELECT_TAG = "screen_calibration_select"
const val SCREEN_CALIBRATION_CROP_TAG = "screen_calibration_crop"
const val SCREEN_CALIBRATION_ASSIGN_TAG = "screen_calibration_assign"
const val SCREEN_CALIBRATION_REVIEW_TAG = "screen_calibration_review"
const val SCREEN_CALIBRATION_DONE_TAG = "screen_calibration_done"

fun calibrationFactorTag(factor: InflammationFactor): String =
    "calibration:factor-${factor.name.lowercase()}"

@Composable
internal fun CalibrationSelectScreen(
    state: CalibrationUiState,
    onImageSelected: (String) -> Unit,
    onRetryLegacyReview: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoUri?.let(onImageSelected)
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "calib_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            photoUri = uri.toString()
            cameraLauncher.launch(uri)
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.toString()?.let(onImageSelected)
    }

    CalibrationScaffold(
        title = stringResource(R.string.calibration_title),
        step = stringResource(R.string.calibration_step_select),
        tag = CALIBRATION_SELECT_LEGACY_TAG,
        onBack = onBack,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Science, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.calibration_capture_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.calibration_capture_instructions),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) },
                enabled = !state.isDecoding,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(CALIBRATION_CAPTURE_TAG),
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.calibration_take_photo))
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = !state.isDecoding,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(CALIBRATION_GALLERY_TAG),
            ) {
                Icon(Icons.Default.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.calibration_from_gallery))
            }
            state.errorMessage?.let { ErrorText(localizedStateMessage(it)) }
            state.legacyWarning?.let { warning ->
                ErrorText(localizedStateMessage(warning.message))
                TextButton(
                    onClick = onRetryLegacyReview,
                    enabled = !state.isRevalidatingLegacy && !state.isPersistenceBusy,
                ) {
                    Text(stringResource(if (state.isRevalidatingLegacy) R.string.calibration_retrying else R.string.calibration_retry_legacy))
                }
            }
        }
    }
}

@Composable
internal fun CalibrationCropScreen(
    state: CalibrationUiState,
    onCropChanged: (CalibrationIntBounds) -> Unit,
    onDetect: () -> Unit,
    onBack: () -> Unit,
) {
    CalibrationScaffold(
        title = stringResource(R.string.calibration_title),
        step = stringResource(R.string.calibration_step_crop),
        tag = CALIBRATION_CROP_LEGACY_TAG,
        onBack = onBack,
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Crop, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.calibration_crop_instructions), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        val bitmap = state.bitmap
        val crop = state.cropBounds
        if (bitmap != null && crop != null) {
            Box(Modifier.fillMaxWidth().height(360.dp).background(Color.Black)) {
                CalibrationCropView(
                    bitmap = bitmap,
                    cropRect = Rect(crop.left, crop.top, crop.right, crop.bottom),
                    onCropRectChanged = { rect ->
                        onCropChanged(CalibrationIntBounds(rect.left, rect.top, rect.right, rect.bottom))
                    },
                    enabled = !state.isDetecting,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Text(stringResource(R.string.calibration_image_unavailable))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDetect,
            enabled = bitmap != null && crop != null && !state.isDetecting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(CALIBRATION_DETECT_TAG),
        ) {
            Text(stringResource(if (state.isDetecting) R.string.calibration_detecting else R.string.calibration_detect_wells))
        }
        state.errorMessage?.let { ErrorText(localizedStateMessage(it)) }
    }
}

@Composable
internal fun CalibrationAssignScreen(
    state: CalibrationUiState,
    onFactorChanged: (InflammationFactor) -> Unit,
    onConcentrationChanged: (Int, String) -> Unit,
    onReview: () -> Unit,
    onBack: () -> Unit,
) {
    CalibrationScaffold(
        title = stringResource(R.string.calibration_title),
        step = stringResource(R.string.calibration_step_assign),
        tag = CALIBRATION_ASSIGN_LEGACY_TAG,
        onBack = onBack,
    ) {
        Text(stringResource(R.string.calibration_factor_label), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup().testTag(CALIBRATION_FACTOR_GROUP_TAG),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InflammationFactor.entries.forEach { factor ->
                val selected = factor == state.factor
                val factorLabel = stringResource(R.string.calibration_factor_option, factor.shortName)
                ClinicalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(calibrationFactorTag(factor))
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onFactorChanged(factor) },
                        )
                        .semantics(mergeDescendants = true) { contentDescription = factorLabel }
                        .then(
                            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier,
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Text(
                        factor.shortName,
                        Modifier.fillMaxWidth().padding(12.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        state.concentrationTexts.forEachIndexed { index, text ->
            OutlinedTextField(
                value = text,
                onValueChange = { onConcentrationChanged(index, it) },
                label = { Text(stringResource(R.string.calibration_well_concentration, index + 1)) },
                supportingText = {
                    if (index in state.concentrationFieldErrors) Text(stringResource(R.string.calibration_concentration_error))
                    else Text(stringResource(R.string.calibration_signal, state.signals.getOrElse(index) { Float.NaN }))
                },
                isError = index in state.concentrationFieldErrors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = onReview,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag(CALIBRATION_REVIEW_TAG),
        ) {
            Text(stringResource(R.string.calibration_review_curve))
        }
        validationErrorResource(state.validation)?.let { message -> ErrorText(stringResource(message)) }
    }
}

@Composable
internal fun CalibrationReviewScreen(
    state: CalibrationUiState,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    CalibrationScaffold(
        title = stringResource(R.string.calibration_title),
        step = stringResource(R.string.calibration_step_review),
        tag = CALIBRATION_REVIEW_LEGACY_TAG,
        onBack = onBack,
        backEnabled = !state.isPersistenceBusy,
    ) {
        Text(stringResource(R.string.calibration_curve_title, state.factor.shortName), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        when (val validation = state.validation) {
            is CalibrationValidation.Valid -> validation.knots.forEach { knot ->
                Text(
                    stringResource(
                        R.string.calibration_knot_summary,
                        knot.concentration,
                        knot.rawSignal,
                        knot.netSignal,
                        knot.fittedSignal,
                    ),
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            is CalibrationValidation.Invalid -> ErrorText(
                stringResource(validationErrorResource(validation) ?: R.string.calibration_invalid),
            )
            null -> ErrorText(stringResource(R.string.calibration_return_assign))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(CALIBRATION_SAVE_TAG),
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (state.isSaving) R.string.calibration_saving else R.string.calibration_save_curve))
        }
        state.errorMessage?.let { ErrorText(localizedStateMessage(it)) }
    }
}

@Composable
internal fun CalibrationDoneScreen(
    state: CalibrationUiState,
    onDone: () -> Unit,
    onAnother: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onBack: () -> Unit,
) {
    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    CalibrationScaffold(
        title = stringResource(R.string.calibration_title),
        step = stringResource(R.string.calibration_step_done),
        tag = CALIBRATION_DONE_LEGACY_TAG,
        onBack = onBack,
        backEnabled = !state.isPersistenceBusy,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (state.factoryRestoreCompleted) R.string.calibration_factory_restored else R.string.calibration_saved),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    state.factoryRestoreCompleted -> stringResource(R.string.calibration_factory_removed)
                    state.savedFactor != null -> stringResource(R.string.calibration_user_curve_active, state.savedFactor.shortName)
                    else -> stringResource(R.string.calibration_complete)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAnother,
                enabled = !state.isPersistenceBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.calibration_another_factor))
            }
            TextButton(
                onClick = { showRestoreDialog = true },
                enabled = !state.isPersistenceBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.calibration_restore_factory))
            }
            Button(
                onClick = onDone,
                enabled = !state.isPersistenceBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(stringResource(R.string.calibration_done)) }
            state.errorMessage?.let { ErrorText(localizedStateMessage(it)) }
        }
    }
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.calibration_restore_title)) },
            text = { Text(stringResource(R.string.calibration_restore_message)) },
            confirmButton = {
                TextButton(
                    enabled = !state.isRestoringFactory,
                    onClick = {
                        showRestoreDialog = false
                        onRestoreConfirmed()
                    },
                ) { Text(stringResource(R.string.calibration_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text(stringResource(R.string.calibration_cancel)) }
            },
        )
    }
}

@Composable
private fun CalibrationScaffold(
    title: String,
    step: String,
    tag: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(calibrationScreenTag(tag)),
    ) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(tag),
        topBar = {
            JointSenseTopBar(
                title = title,
                navigationIcon = {
                    JointSenseBarAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.calibration_back),
                        onClick = onBack,
                        enabled = backEnabled,
                        modifier = Modifier.testTag("calibration:top-back"),
                    )
                },
            )
        },
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(step, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
    }
}

private fun calibrationScreenTag(legacyTag: String): String = when (legacyTag) {
    CALIBRATION_SELECT_LEGACY_TAG -> SCREEN_CALIBRATION_SELECT_TAG
    CALIBRATION_CROP_LEGACY_TAG -> SCREEN_CALIBRATION_CROP_TAG
    CALIBRATION_ASSIGN_LEGACY_TAG -> SCREEN_CALIBRATION_ASSIGN_TAG
    CALIBRATION_REVIEW_LEGACY_TAG -> SCREEN_CALIBRATION_REVIEW_TAG
    CALIBRATION_DONE_LEGACY_TAG -> SCREEN_CALIBRATION_DONE_TAG
    else -> error("Unknown calibration page tag: $legacyTag")
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

private fun validationErrorResource(validation: CalibrationValidation?): Int? {
    val errors = (validation as? CalibrationValidation.Invalid)?.errors ?: return null
    return when {
        CalibrationError.MissingBlank in errors -> R.string.calibration_error_missing_blank
        CalibrationError.MultipleBlanks in errors -> R.string.calibration_error_multiple_blanks
        CalibrationError.DuplicateNonBlankConcentration in errors -> R.string.calibration_error_duplicate_concentration
        CalibrationError.WrongReadingCount in errors -> R.string.calibration_error_reading_count
        CalibrationError.DynamicRangeTooLow in errors -> R.string.calibration_error_dynamic_range
        CalibrationError.NonMonotonicBeyondTolerance in errors ->
            R.string.calibration_error_monotonic
        else -> R.string.calibration_error_highlighted
    }
}

@Composable
private fun localizedStateMessage(message: String): String = stringResource(
    when (message) {
        "Unable to detect calibration wells" -> R.string.calibration_error_detect
        "Calibration save was cancelled" -> R.string.calibration_error_save_cancelled
        "Unable to save calibration" -> R.string.calibration_error_save
        "Factory restore was cancelled" -> R.string.calibration_error_restore_cancelled
        "Unable to restore factory curves" -> R.string.calibration_error_restore
        "Some legacy calibrations could not be reviewed automatically" -> R.string.calibration_error_legacy_partial
        "Legacy calibration review was cancelled" -> R.string.calibration_error_legacy_cancelled
        "Unable to review legacy calibrations automatically" -> R.string.calibration_error_legacy
        "Saved crop no longer fits this image; review calibration again" -> R.string.calibration_error_crop_restored
        "Unable to read calibration image" -> R.string.calibration_error_read_image
        else -> R.string.calibration_error_generic
    },
)
