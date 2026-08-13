package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseBarAction
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.feature.measurement.R
import cloud.univ.jointsense.measurement.crop.ImageCropView

const val MEASUREMENT_PROGRESS_TAG = "measurement_progress"
const val MEASUREMENT_ERROR_TAG = "measurement_error"
const val RETRY_BUTTON_TAG = "retry_button"
const val ANALYZE_BUTTON_TAG = "analyze_button"
const val CONTINUE_MEASUREMENT_TAG = "continue_measurement"
const val SCREEN_MEASUREMENT_SELECT_TAG = "screen_measurement_select"
const val SCREEN_MEASUREMENT_CROP_TAG = "screen_measurement_crop"
const val SCREEN_MEASUREMENT_FACTOR_TAG = "screen_measurement_factor"
const val MEASUREMENT_TAKE_PHOTO_TAG = "measurement_take_photo"
const val MEASUREMENT_GALLERY_TAG = "measurement_gallery"
const val MEASUREMENT_CROP_CONFIRM_TAG = "measurement_crop_confirm"
const val MEASUREMENT_CROP_VIEW_TAG = "crop_view"
const val MEASUREMENT_FACTOR_GROUP_TAG = "measurement_factor_group"

fun measurementFactorTag(factor: InflammationFactor): String =
    "measurement_factor_${factor.name.lowercase()}"

/**
 * Image Selection Screen - Step 1 of the test flow.
 * Allows user to take a photo or select from gallery.
 */
@Composable
fun ImageSelectScreen(
    onTakePhoto: () -> Unit,
    onPickImage: () -> Unit,
    onBack: () -> Unit,
    sessionName: String,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.testTag(SCREEN_MEASUREMENT_SELECT_TAG),
        topBar = {
            JointSenseTopBar(
                title = sessionName,
                navigationIcon = {
                    JointSenseBarAction(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.measurement_action_back), onBack)
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.measurement_title_select_image),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.measurement_select_image_instructions),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Camera button
            Button(
                onClick = onTakePhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .testTag(MEASUREMENT_TAKE_PHOTO_TAG),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.measurement_action_take_photo), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gallery button
            Button(
                onClick = onPickImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp)
                    .testTag(MEASUREMENT_GALLERY_TAG),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.measurement_action_gallery), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Image Crop Screen - Step 2 of the test flow.
 * Allows user to select the region of interest (chip reaction chamber).
 */
@Composable
fun ImageCropScreen(
    bitmap: Bitmap,
    cropRect: Rect,
    onCropRectChanged: (Rect) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.testTag(SCREEN_MEASUREMENT_CROP_TAG),
        topBar = {
            JointSenseTopBar(
                title = stringResource(R.string.measurement_title_crop),
                navigationIcon = {
                    JointSenseBarAction(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.measurement_action_back), onBack)
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Instructions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.measurement_crop_instructions),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Image with crop overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                ImageCropView(
                    bitmap = bitmap,
                    cropRect = cropRect,
                    onCropRectChanged = onCropRectChanged,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Confirm button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag(MEASUREMENT_CROP_CONFIRM_TAG),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.measurement_action_confirm_selection),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Factor Selection Screen - Step 3 of the test flow.
 * Allows user to select which inflammation factor to detect.
 */
@Composable
fun FactorSelectScreen(
    selectedFactor: InflammationFactor,
    onFactorSelected: (InflammationFactor) -> Unit,
    onAnalyze: () -> Unit,
    onBack: () -> Unit,
    isAnalyzing: Boolean,
    backEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.testTag(SCREEN_MEASUREMENT_FACTOR_TAG),
        topBar = {
            JointSenseTopBar(
                title = stringResource(R.string.measurement_title_factor),
                navigationIcon = {
                    JointSenseBarAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.measurement_action_back),
                        onClick = onBack,
                        enabled = backEnabled,
                    )
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.measurement_factor_instructions),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Factor selection cards
            Column(Modifier.fillMaxWidth().selectableGroup().testTag(MEASUREMENT_FACTOR_GROUP_TAG)) {
                InflammationFactor.entries.forEach { factor ->
                    val isSelected = factor == selectedFactor
                    val factorName = stringResource(factor.displayNameResource())
                    val factorAccessibilityName = stringResource(
                        R.string.measurement_factor_accessibility_name,
                        factor.shortName,
                        factorName,
                    )
                    ClinicalCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .heightIn(min = 48.dp)
                            .testTag(measurementFactorTag(factor))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onFactorSelected(factor) },
                            )
                            .semantics(mergeDescendants = true) {
                                contentDescription = factorAccessibilityName
                            }
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(16.dp)
                                    )
                                } else {
                                    Modifier
                                }
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isSelected) 4.dp else 1.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = factor.shortName,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = factorName,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Analyze button
            Button(
                onClick = onAnalyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag(ANALYZE_BUTTON_TAG),
                shape = RoundedCornerShape(16.dp),
                enabled = !isAnalyzing
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag(MEASUREMENT_PROGRESS_TAG),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.measurement_analyzing), fontSize = 18.sp)
                } else {
                    Icon(Icons.Default.Science, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.measurement_action_start_analysis),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

internal fun InflammationFactor.displayNameResource(): Int = when (this) {
    InflammationFactor.IL6 -> R.string.factor_il6_name
    InflammationFactor.TNF_ALPHA -> R.string.factor_tnf_alpha_name
    InflammationFactor.IL1_BETA -> R.string.factor_il1_beta_name
}
