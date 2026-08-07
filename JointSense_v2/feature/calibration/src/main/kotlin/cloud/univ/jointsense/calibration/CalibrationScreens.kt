package cloud.univ.jointsense.calibration

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cloud.univ.jointsense.designsystem.theme.MedicalBlue
import cloud.univ.jointsense.designsystem.theme.MedicalGreen
import cloud.univ.jointsense.designsystem.theme.TextSecondary
import cloud.univ.jointsense.domain.model.InflammationFactor
import java.io.File
import kotlinx.coroutines.launch

private enum class CalibStep { SELECT, CROP, ASSIGN, REVIEW, DONE }

/**
 * Guided calibration flow.
 *
 *  1. Capture/select a photo of the standard ladder plate.
 *  2. Crop to the plate region.
 *  3. Assign each detected well its known concentration (pre-filled with the
 *     factor's factory ladder; the user corrects any that differ).
 *  4. Review the background-corrected (conc, signal) knots and save.
 *  5. Confirmation; optionally calibrate another factor or restore the
 *     factory curve.
 *
 * The saved calibration becomes the repository-backed active calibration and is
 * used by the live analysis path thereafter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalibrationFlowScreen(
    controller: BaselineCalibrationController,
    onExit: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var step by remember { mutableStateOf(CalibStep.SELECT) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropRect by remember { mutableStateOf(Rect(0, 0, 1, 1)) }
    var selectedFactor by remember { mutableStateOf(InflammationFactor.TNF_ALPHA) }
    var wellConcs by remember { mutableStateOf(FACTORY_LADDER[InflammationFactor.TNF_ALPHA]!!.toMutableList()) }
    var wellSignals by remember { mutableStateOf<List<Float>?>(null) }
    var savedFactor by remember { mutableStateOf<InflammationFactor?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibrate Standard Curve", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MedicalBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Step ${step.ordinal + 1} / 5 · " + when (step) {
                    CalibStep.SELECT -> "Capture standard plate"
                    CalibStep.CROP -> "Crop to plate"
                    CalibStep.ASSIGN -> "Assign concentrations"
                    CalibStep.REVIEW -> "Review & save"
                    CalibStep.DONE -> "Done"
                },
                fontSize = 12.sp,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            when (step) {
                CalibStep.SELECT -> CalibSelectStep { b ->
                    bitmap = b
                    cropRect = Rect(b.width / 4, b.height / 4, 3 * b.width / 4, 3 * b.height / 4)
                    wellSignals = null
                    step = CalibStep.CROP
                }

                CalibStep.CROP -> {
                    val bmp = bitmap
                    if (bmp != null) {
                        CalibCropStep(
                            bitmap = bmp,
                            cropRect = cropRect,
                            onCropRectChanged = { cropRect = it },
                            onConfirm = {
                                val signals = BaselineCalibrationImageAnalyzer.detectGridSignals(
                                    bmp,
                                    cropRect,
                                    3,
                                    3,
                                )
                                    .sortedBy { it.index }
                                    .map { it.signal }
                                wellSignals = signals
                                wellConcs = FACTORY_LADDER[selectedFactor]!!.toMutableList()
                                step = CalibStep.ASSIGN
                            },
                            onBack = { step = CalibStep.SELECT }
                        )
                    }
                }

                CalibStep.ASSIGN -> CalibAssignStep(
                    factor = selectedFactor,
                    onFactorChange = { f ->
                        selectedFactor = f
                        wellConcs = FACTORY_LADDER[f]!!.toMutableList()
                    },
                    concs = wellConcs,
                    signals = wellSignals ?: emptyList(),
                    onConcChange = { i, v ->
                        wellConcs = wellConcs.toMutableList().also { it[i] = v }
                    },
                    onBack = { step = CalibStep.CROP },
                    onNext = { step = CalibStep.REVIEW }
                )

                CalibStep.REVIEW -> CalibReviewStep(
                    factor = selectedFactor,
                    concs = wellConcs,
                    signals = wellSignals ?: emptyList(),
                    onBack = { step = CalibStep.ASSIGN },
                    onSave = {
                        coroutineScope.launch {
                            controller.save(
                                factor = selectedFactor,
                                concentrations = wellConcs,
                                signals = wellSignals ?: emptyList(),
                            )
                            savedFactor = selectedFactor
                            step = CalibStep.DONE
                        }
                    }
                )

                CalibStep.DONE -> CalibDoneStep(
                    savedFactor = savedFactor,
                    onDone = onExit,
                    onAnother = {
                        bitmap = null
                        wellSignals = null
                        savedFactor = null
                        step = CalibStep.SELECT
                    },
                    onRestore = {
                        coroutineScope.launch {
                            controller.restoreFactory()
                            bitmap = null
                            wellSignals = null
                            savedFactor = null
                            step = CalibStep.SELECT
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CalibSelectStep(onImage: (Bitmap) -> Unit) {
    val context = LocalContext.current
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            photoUri?.let { uri ->
                val bmp = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bmp != null) onImage(bmp)
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val bmp = runCatching {
                context.contentResolver.openInputStream(it)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            if (bmp != null) onImage(bmp)
        }
    }
    val cameraPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "calib_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            photoUri = uri
            cameraLauncher.launch(uri)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Science,
            null,
            modifier = Modifier.size(80.dp),
            tint = MedicalBlue.copy(alpha = 0.3f)
        )
        Spacer(Modifier.height(16.dp))
        Text("Photo the standard ladder plate", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Use the kit's standard plate (known concentrations) for ONE factor. " +
                "We detect the 3×3 wells automatically and fit the curve from your photo.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { cameraPerm.launch(android.Manifest.permission.CAMERA) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue)
        ) {
            Icon(Icons.Default.CameraAlt, null)
            Spacer(Modifier.width(8.dp))
            Text("Take Photo")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MedicalBlue
            )
        ) {
            Icon(Icons.Default.PhotoLibrary, null)
            Spacer(Modifier.width(8.dp))
            Text("From Gallery")
        }
    }
}

@Composable
private fun CalibCropStep(
    bitmap: Bitmap,
    cropRect: Rect,
    onCropRectChanged: (Rect) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Crop, null, modifier = Modifier.size(20.dp), tint = MedicalBlue)
            Spacer(Modifier.width(8.dp))
            Text("Crop to the 3×3 well plate region", fontSize = 13.sp, color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Color.Black)
        ) {
            CalibrationCropView(
                bitmap = bitmap,
                cropRect = cropRect,
                onCropRectChanged = onCropRectChanged,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedicalGreen)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Detect Wells")
            }
        }
    }
}

@Composable
private fun CalibAssignStep(
    factor: InflammationFactor,
    onFactorChange: (InflammationFactor) -> Unit,
    concs: List<Float>,
    signals: List<Float>,
    onConcChange: (Int, Float) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Column {
        Text("Factor being calibrated", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InflammationFactor.entries.forEach { f ->
                val sel = f == factor
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onFactorChange(f) }
                        .then(
                            if (sel) Modifier.border(2.dp, MedicalBlue, RoundedCornerShape(12.dp))
                            else Modifier
                        ),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (sel) MedicalBlue.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            f.shortName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel) MedicalBlue else MaterialTheme.colorScheme.onSurface
                        )
                        if (sel) {
                            Spacer(Modifier.height(4.dp))
                            Icon(Icons.Default.CheckCircle, null, tint = MedicalBlue, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Enter the known concentration (pg/mL) of each well in row order. " +
                "\"sig\" is the tealness measured from your photo.",
            fontSize = 13.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        for (r in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 3) {
                    val i = r * 3 + c
                    val sig = signals.getOrElse(i) { 0f }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, TextSecondary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Text("Well ${i + 1}", fontSize = 11.sp, color = TextSecondary)
                        OutlinedTextField(
                            value = (concs.getOrNull(i)?.toInt()?.toString()) ?: "",
                            onValueChange = { txt ->
                                val v = txt.toFloatOrNull() ?: 0f
                                onConcChange(i, v)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("sig %.1f".format(sig), fontSize = 11.sp, color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue)
            ) {
                Text("Review", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CalibReviewStep(
    factor: InflammationFactor,
    concs: List<Float>,
    signals: List<Float>,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val blankIdx = concs.indexOfFirst { it == 0f }.coerceAtLeast(0)
    val blankSignal = signals.getOrElse(blankIdx) { 0f }
    Column {
        Text("Review — ${factor.shortName}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Background (blank) signal = %.1f will be subtracted from every well.".format(blankSignal),
            fontSize = 13.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Well", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Conc", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Raw", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Net", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                for (i in concs.indices) {
                    val net = signals.getOrElse(i) { 0f } - blankSignal
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${i + 1}", Modifier.weight(1f), fontSize = 13.sp)
                        Text("%.0f".format(concs[i]), Modifier.weight(1f), fontSize = 13.sp)
                        Text("%.1f".format(signals.getOrElse(i) { 0f }), Modifier.weight(1f), fontSize = 13.sp)
                        Text("%.1f".format(net), Modifier.weight(1f), fontSize = 13.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "These net (conc, signal) points become the new standard curve for " +
                "${factor.shortName}. It replaces the factory curve and is used for all " +
                "future measurements of this factor.",
            fontSize = 13.sp,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedicalGreen)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(6.dp))
                Text("Save Curve", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CalibDoneStep(
    savedFactor: InflammationFactor?,
    onDone: () -> Unit,
    onAnother: () -> Unit,
    onRestore: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(72.dp), tint = MedicalGreen)
        Spacer(Modifier.height(16.dp))
        Text("Calibration saved", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            savedFactor?.let {
                "The ${it.shortName} standard curve is now active and will be used for live measurements."
            } ?: "Your calibration is active.",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAnother,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue)
        ) {
            Icon(Icons.Default.Science, null)
            Spacer(Modifier.width(8.dp))
            Text("Calibrate Another Factor")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
            Text("Restore factory curve", color = TextSecondary)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MedicalBlue
            )
        ) {
            Text("Done", fontWeight = FontWeight.SemiBold)
        }
    }
}
