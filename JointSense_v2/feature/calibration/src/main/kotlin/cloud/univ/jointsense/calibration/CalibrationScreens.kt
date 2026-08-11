package cloud.univ.jointsense.calibration

import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cloud.univ.jointsense.analysis.calibration.CalibrationError
import cloud.univ.jointsense.analysis.calibration.CalibrationValidation
import cloud.univ.jointsense.designsystem.theme.MedicalBlue
import cloud.univ.jointsense.designsystem.theme.MedicalGreen
import cloud.univ.jointsense.designsystem.theme.TextSecondary
import cloud.univ.jointsense.domain.model.InflammationFactor
import java.io.File

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
        title = "Calibrate Standard Curve",
        step = "Step 1 / 5 · Capture standard plate",
        tag = "calibration:select",
        onBack = onBack,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Science, null, Modifier.size(80.dp), tint = MedicalBlue.copy(alpha = 0.3f))
            Spacer(Modifier.height(16.dp))
            Text("Photo the standard ladder plate", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Use the kit's 3×3 standard plate for one factor.",
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { cameraPermission.launch(android.Manifest.permission.CAMERA) },
                enabled = !state.isDecoding,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Take Photo")
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = !state.isDecoding,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Default.PhotoLibrary, null)
                Spacer(Modifier.width(8.dp))
                Text("From Gallery")
            }
            state.errorMessage?.let { ErrorText(it) }
            state.legacyWarning?.let { warning ->
                ErrorText(warning.message)
                TextButton(
                    onClick = onRetryLegacyReview,
                    enabled = !state.isRevalidatingLegacy && !state.isPersistenceBusy,
                ) {
                    Text(if (state.isRevalidatingLegacy) "Retrying…" else "Retry legacy review")
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
        title = "Calibrate Standard Curve",
        step = "Step 2 / 5 · Crop to plate",
        tag = "calibration:crop",
        onBack = onBack,
    ) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Crop, null, Modifier.size(20.dp), tint = MedicalBlue)
            Spacer(Modifier.width(8.dp))
            Text("Crop to the 3×3 well plate region", color = TextSecondary)
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
            Text("The selected image is unavailable. Go back and choose it again.")
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDetect,
            enabled = bitmap != null && crop != null && !state.isDetecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedicalGreen),
        ) {
            Text(if (state.isDetecting) "Detecting…" else "Detect Wells")
        }
        state.errorMessage?.let { ErrorText(it) }
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
        title = "Calibrate Standard Curve",
        step = "Step 3 / 5 · Assign concentrations",
        tag = "calibration:assign",
        onBack = onBack,
    ) {
        Text("Factor being calibrated", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InflammationFactor.entries.forEach { factor ->
                val selected = factor == state.factor
                Card(
                    modifier = Modifier.weight(1f).clickable { onFactorChanged(factor) }.then(
                        if (selected) Modifier.border(2.dp, MedicalBlue, RoundedCornerShape(12.dp)) else Modifier,
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MedicalBlue.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
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
                label = { Text("Well ${index + 1} concentration") },
                supportingText = {
                    if (index in state.concentrationFieldErrors) Text("Enter a non-negative number")
                    else Text("signal ${state.signals.getOrElse(index) { Float.NaN }}")
                },
                isError = index in state.concentrationFieldErrors,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = onReview, Modifier.fillMaxWidth().height(52.dp)) {
            Text("Review Curve")
        }
        validationErrorText(state.validation)?.let { message -> ErrorText(message) }
    }
}

@Composable
internal fun CalibrationReviewScreen(
    state: CalibrationUiState,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    CalibrationScaffold(
        title = "Calibrate Standard Curve",
        step = "Step 4 / 5 · Review & save",
        tag = "calibration:review",
        onBack = onBack,
        backEnabled = !state.isPersistenceBusy,
    ) {
        Text("${state.factor.shortName} standard curve", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        when (val validation = state.validation) {
            is CalibrationValidation.Valid -> validation.knots.forEach { knot ->
                Text(
                    "${formatConcentration(knot.concentration)} pg/mL · raw %.1f · fitted %.1f".format(
                        knot.rawSignal,
                        knot.fittedSignal,
                    ),
                    modifier = Modifier.padding(vertical = 3.dp),
                )
            }
            is CalibrationValidation.Invalid -> ErrorText(validationErrorText(validation) ?: "Calibration is invalid")
            null -> ErrorText("Return to Assign and review the readings first")
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedicalGreen),
        ) {
            Icon(Icons.Default.Check, null)
            Spacer(Modifier.width(6.dp))
            Text(if (state.isSaving) "Saving…" else "Save Curve")
        }
        state.errorMessage?.let { ErrorText(it) }
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
        title = "Calibrate Standard Curve",
        step = "Step 5 / 5 · Done",
        tag = "calibration:done",
        onBack = onBack,
        backEnabled = !state.isPersistenceBusy,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.CheckCircle, null, Modifier.size(72.dp), tint = MedicalGreen)
            Spacer(Modifier.height(16.dp))
            Text(
                if (state.factoryRestoreCompleted) "Factory curves restored" else "Calibration saved",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    state.factoryRestoreCompleted -> "All user calibration curves were removed."
                    state.savedFactor != null -> "The ${state.savedFactor.shortName} user curve is active."
                    else -> "Your calibration flow is complete."
                },
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAnother,
                enabled = !state.isPersistenceBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Calibrate Another Factor")
            }
            TextButton(
                onClick = { showRestoreDialog = true },
                enabled = !state.isPersistenceBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Restore factory curve")
            }
            Button(
                onClick = onDone,
                enabled = !state.isPersistenceBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Done") }
            state.errorMessage?.let { ErrorText(it) }
        }
    }
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore factory curves?") },
            text = { Text("This removes every user calibration for all factors.") },
            confirmButton = {
                TextButton(
                    enabled = !state.isRestoringFactory,
                    onClick = {
                        showRestoreDialog = false
                        onRestoreConfirmed()
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalibrationScaffold(
    title: String,
    step: String,
    tag: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(tag),
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        enabled = backEnabled,
                        modifier = Modifier.testTag("calibration:top-back"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MedicalBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(step, fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

private fun validationErrorText(validation: CalibrationValidation?): String? {
    val errors = (validation as? CalibrationValidation.Invalid)?.errors ?: return null
    return when {
        CalibrationError.MissingBlank in errors -> "Exactly one blank (0 pg/mL) is required."
        CalibrationError.MultipleBlanks in errors -> "Only one blank (0 pg/mL) is allowed."
        CalibrationError.DuplicateNonBlankConcentration in errors -> "Non-blank concentrations must be unique."
        CalibrationError.WrongReadingCount in errors -> "All nine well readings are required."
        CalibrationError.DynamicRangeTooLow in errors -> "Signal range is too low to save this curve."
        CalibrationError.NonMonotonicBeyondTolerance in errors ->
            "The readings need too much monotonic correction; check the plate and concentrations."
        else -> "Correct the highlighted calibration readings."
    }
}
