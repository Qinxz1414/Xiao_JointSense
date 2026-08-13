package cloud.univ.jointsense.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cloud.univ.jointsense.core.designsystem.R as DesignSystemR
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.feature.settings.R

/** Profile tab — app info, history entry, data management, and about. */
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onOpenHistory: () -> Unit,
    onCalibrate: () -> Unit,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { JointSenseTopBar(title = stringResource(R.string.settings_title)) }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Identity card
            ClinicalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = DesignSystemR.drawable.jointsense_logo),
                            contentDescription = stringResource(R.string.settings_logo_description),
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_app_name),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.settings_app_summary),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                R.string.settings_counts,
                                pluralStringResource(
                                    R.plurals.settings_session_count,
                                    state.sessionCount,
                                    state.sessionCount,
                                ),
                                pluralStringResource(
                                    R.plurals.settings_measurement_count,
                                    state.measurementCount,
                                    state.measurementCount,
                                ),
                            ),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Settings card
            ClinicalCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ProfileEntry(
                        icon = Icons.Default.History,
                        tint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_history),
                        subtitle = stringResource(R.string.settings_history_summary),
                        onClick = onOpenHistory
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    ProfileEntry(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        tint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_about_model),
                        subtitle = stringResource(R.string.settings_about_summary),
                        onClick = { showAboutDialog = true }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    ProfileEntry(
                        icon = Icons.Default.Science,
                        tint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(R.string.settings_calibrate),
                        subtitle = when (val summary = calibrationSubtitle(state)) {
                            is CalibrationSubtitle.Review -> pluralStringResource(
                                R.plurals.settings_calibration_review,
                                summary.count,
                                summary.count,
                            )
                            is CalibrationSubtitle.Active -> pluralStringResource(
                                R.plurals.settings_calibration_active,
                                summary.count,
                                summary.count,
                            )
                            CalibrationSubtitle.Empty -> stringResource(R.string.settings_calibration_empty)
                        },
                        onClick = onCalibrate
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    ProfileEntry(
                        icon = Icons.Default.Delete,
                        tint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.settings_clear_all),
                        subtitle = stringResource(R.string.settings_clear_summary),
                        onClick = { showClearDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = {
                Text(pluralStringResource(
                    R.plurals.settings_clear_message,
                    state.sessionCount,
                    state.sessionCount,
                ))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllData()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.settings_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.settings_about_model)) },
            text = {
                Text(stringResource(R.string.settings_about_model_body))
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.settings_ok))
                }
            }
        )
    }
}

internal sealed interface CalibrationSubtitle {
    data class Review(val count: Int) : CalibrationSubtitle
    data class Active(val count: Int) : CalibrationSubtitle
    data object Empty : CalibrationSubtitle
}

internal fun calibrationSubtitle(state: SettingsUiState): CalibrationSubtitle = when {
    state.hasCalibrationNeedingReview ->
        CalibrationSubtitle.Review(state.calibrationReviewCount)
    state.hasCalibration -> CalibrationSubtitle.Active(state.calibrationCount)
    else -> CalibrationSubtitle.Empty
}

@Composable
private fun ProfileEntry(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
