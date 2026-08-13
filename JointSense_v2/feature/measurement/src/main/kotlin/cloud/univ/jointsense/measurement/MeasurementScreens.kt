package cloud.univ.jointsense.measurement

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseBarAction
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.designsystem.theme.BioGreen
import cloud.univ.jointsense.designsystem.theme.PrimaryAccent
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.measurement.crop.ImageCropView

const val MEASUREMENT_PROGRESS_TAG = "measurement_progress"
const val MEASUREMENT_ERROR_TAG = "measurement_error"
const val RETRY_BUTTON_TAG = "retry_button"
const val ANALYZE_BUTTON_TAG = "analyze_button"
const val CONTINUE_MEASUREMENT_TAG = "continue_measurement"

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
        topBar = {
            JointSenseTopBar(
                title = sessionName,
                navigationIcon = {
                    JointSenseBarAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = PrimaryAccent.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Select Image",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Take a photo of the chip reaction chamber\nor select an existing image from gallery",
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
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Take Photo", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gallery button
            Button(
                onClick = onPickImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = PrimaryAccent
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Select from Gallery", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
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
        topBar = {
            JointSenseTopBar(
                title = "Select Analysis Area",
                navigationIcon = {
                    JointSenseBarAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
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
                    tint = PrimaryAccent
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Drag to move, drag corners to resize the selection area",
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
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BioGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Confirm Selection",
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
        topBar = {
            JointSenseTopBar(
                title = "Select Detection Factor",
                navigationIcon = {
                    JointSenseBarAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        enabled = backEnabled,
                    )
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = PrimaryAccent.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Choose the inflammation factor\nyou want to detect",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Factor selection cards
            InflammationFactor.entries.forEach { factor ->
                val isSelected = factor == selectedFactor
                ClinicalCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onFactorSelected(factor) }
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    2.dp,
                                    PrimaryAccent,
                                    RoundedCornerShape(16.dp)
                                )
                            } else {
                                Modifier
                            }
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            PrimaryAccent.copy(alpha = 0.08f)
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
                                color = if (isSelected) PrimaryAccent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = factor.displayName,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = PrimaryAccent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Analyze button
            Button(
                onClick = onAnalyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag(ANALYZE_BUTTON_TAG),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                enabled = !isAnalyzing
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .testTag(MEASUREMENT_PROGRESS_TAG),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Analyzing...", fontSize = 18.sp)
                } else {
                    Icon(Icons.Default.Science, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Start Analysis",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
