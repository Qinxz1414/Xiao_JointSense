package cloud.univ.jointsense.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import cloud.univ.jointsense.core.designsystem.R as DesignSystemR
import cloud.univ.jointsense.designsystem.component.ClinicalCard
import cloud.univ.jointsense.designsystem.component.JointSenseTopBar
import cloud.univ.jointsense.feature.settings.R
import cloud.univ.jointsense.settings.locale.LanguageOption

@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    selectedLanguage: LanguageOption,
    readCurrentLanguage: () -> LanguageOption,
    onApplyLanguage: (LanguageOption) -> Unit,
    onOpenHistory: () -> Unit,
    onCalibrate: () -> Unit,
    onOpenAbout: () -> Unit,
    onRequestClearAll: () -> Unit,
    onRequestRestoreSamples: () -> Unit,
    onConfirmDataAction: () -> Unit,
    onDismissDataAction: () -> Unit,
    onRetryDataAction: () -> Unit,
    onConsumeDataActionResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var languageDialogSelection by remember { mutableStateOf<LanguageOption?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(SCREEN_PROFILE_TAG),
        topBar = { JointSenseTopBar(title = stringResource(R.string.settings_title)) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .testTag(PROFILE_SCREEN_TAG)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            IdentityCard(state)
            Spacer(Modifier.height(20.dp))
            SettingsSection(
                title = stringResource(R.string.settings_section_application),
                entries = listOf(
                    ProfileEntryModel(
                        icon = Icons.Default.Language,
                        tint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_language),
                        subtitle = languageLabel(selectedLanguage),
                        testTag = SETTINGS_LANGUAGE_TAG,
                        onClick = { languageDialogSelection = readCurrentLanguage() },
                    ),
                    ProfileEntryModel(
                        icon = Icons.Default.Science,
                        tint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(R.string.settings_calibrate),
                        subtitle = calibrationSummary(state),
                        testTag = SETTINGS_CALIBRATION_TAG,
                        onClick = onCalibrate,
                    ),
                ),
            )
            Spacer(Modifier.height(20.dp))
            SettingsSection(
                title = stringResource(R.string.settings_section_data_model),
                entries = listOf(
                    ProfileEntryModel(
                        icon = Icons.Default.History,
                        tint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_history),
                        subtitle = stringResource(R.string.settings_history_summary),
                        testTag = SETTINGS_HISTORY_TAG,
                        onClick = onOpenHistory,
                    ),
                    ProfileEntryModel(
                        icon = Icons.Default.Restore,
                        tint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_restore_samples),
                        subtitle = if (state.countsLoaded) {
                            stringResource(
                                R.string.settings_restore_samples_summary,
                                pluralStringResource(
                                    R.plurals.settings_builtin_sample_count,
                                    state.builtInSampleCount,
                                    state.builtInSampleCount,
                                ),
                            )
                        } else {
                            stringResource(R.string.settings_counts_loading)
                        },
                        testTag = SETTINGS_RESTORE_SAMPLES_TAG,
                        onClick = onRequestRestoreSamples,
                    ),
                    ProfileEntryModel(
                        icon = Icons.Default.Delete,
                        tint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.settings_clear_all),
                        subtitle = stringResource(R.string.settings_clear_summary),
                        testTag = SETTINGS_CLEAR_ALL_TAG,
                        onClick = onRequestClearAll,
                    ),
                ),
            )
            Spacer(Modifier.height(20.dp))
            SettingsSection(
                title = stringResource(R.string.settings_section_support),
                entries = listOf(
                    ProfileEntryModel(
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        tint = MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.settings_about_model),
                        subtitle = stringResource(R.string.settings_about_summary),
                        testTag = SETTINGS_ABOUT_TAG,
                        onClick = onOpenAbout,
                    ),
                ),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    languageDialogSelection?.let { snapshot ->
        LanguageDialog(
            selected = snapshot,
            onSelect = { option ->
                completeLanguageSelection(
                    current = snapshot,
                    selected = option,
                    close = { languageDialogSelection = null },
                    apply = onApplyLanguage,
                )
            },
            onDismiss = { languageDialogSelection = null },
        )
    }

    DataManagementDialogs(
        action = state.dataAction,
        onDismiss = onDismissDataAction,
        onConfirm = onConfirmDataAction,
        onRetry = onRetryDataAction,
        onConsumeResult = onConsumeDataActionResult,
    )
}

@Composable
private fun IdentityCard(state: SettingsUiState) {
    ClinicalCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PROFILE_IDENTITY_TAG),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stack = maxWidth < 400.dp && LocalDensity.current.fontScale >= 1.5f
            if (stack) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IdentityLogo()
                    Spacer(Modifier.height(12.dp))
                    IdentityDetails(state)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IdentityLogo()
                    Spacer(Modifier.width(16.dp))
                    IdentityDetails(state)
                }
            }
        }
    }
}

@Composable
private fun IdentityLogo() {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(DesignSystemR.drawable.jointsense_logo),
                    contentDescription = stringResource(R.string.settings_logo_description),
                    modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Fit,
            )
    }
}

@Composable
private fun IdentityDetails(state: SettingsUiState) {
    Column {
                Text(
                    text = stringResource(R.string.settings_app_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.settings_app_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.countsLoaded) {
                        stringResource(
                            R.string.settings_counts,
                            pluralStringResource(R.plurals.settings_session_count, state.sessionCount, state.sessionCount),
                            pluralStringResource(R.plurals.settings_measurement_count, state.measurementCount, state.measurementCount),
                        )
                    } else {
                        stringResource(R.string.settings_counts_loading)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
    }
}

@Composable
private fun SettingsSection(title: String, entries: List<ProfileEntryModel>) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
    ClinicalCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProfileEntry(entry)
            }
        }
    }
}

private data class ProfileEntryModel(
    val icon: ImageVector,
    val tint: Color,
    val title: String,
    val subtitle: String,
    val testTag: String,
    val onClick: () -> Unit,
)

@Composable
private fun ProfileEntry(entry: ProfileEntryModel) {
    val announcement = stringResource(
        R.string.settings_entry_accessibility_name,
        entry.title,
        entry.subtitle,
    )
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClick = entry.onClick,
            )
            .clearAndSetSemantics {
                contentDescription = announcement
                role = Role.Button
                this[SemanticsProperties.TestTag] = entry.testTag
                onClick {
                    entry.onClick()
                    true
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape)
                .background(entry.tint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = entry.tint,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun languageLabel(option: LanguageOption): String = stringResource(
    when (option) {
        LanguageOption.SYSTEM -> R.string.settings_language_system
        LanguageOption.SIMPLIFIED_CHINESE -> R.string.settings_language_zh_cn
        LanguageOption.ENGLISH -> R.string.settings_language_en
    },
)

@Composable
private fun calibrationSummary(state: SettingsUiState): String = when {
    !state.countsLoaded -> stringResource(R.string.settings_counts_loading)
    state.calibrationCount > 0 && state.calibrationReviewCount > 0 -> stringResource(
        R.string.settings_calibration_combined,
        pluralStringResource(R.plurals.settings_calibration_active, state.calibrationCount, state.calibrationCount),
        pluralStringResource(R.plurals.settings_calibration_review, state.calibrationReviewCount, state.calibrationReviewCount),
    )
    state.calibrationReviewCount > 0 -> pluralStringResource(
        R.plurals.settings_calibration_review,
        state.calibrationReviewCount,
        state.calibrationReviewCount,
    )
    state.calibrationCount > 0 -> pluralStringResource(
        R.plurals.settings_calibration_active,
        state.calibrationCount,
        state.calibrationCount,
    )
    else -> stringResource(R.string.settings_calibration_empty)
}

internal sealed interface CalibrationSubtitle {
    data class Review(val count: Int) : CalibrationSubtitle
    data class Active(val count: Int) : CalibrationSubtitle
    data object Empty : CalibrationSubtitle
}

internal fun calibrationSubtitle(state: SettingsUiState): CalibrationSubtitle = when {
    state.hasCalibrationNeedingReview -> CalibrationSubtitle.Review(state.calibrationReviewCount)
    state.hasCalibration -> CalibrationSubtitle.Active(state.calibrationCount)
    else -> CalibrationSubtitle.Empty
}

const val PROFILE_SCREEN_TAG = "profile_screen"
const val SCREEN_PROFILE_TAG = "screen_profile"
const val PROFILE_IDENTITY_TAG = "profile_identity"
const val SETTINGS_LANGUAGE_TAG = "settings_language"
const val SETTINGS_CALIBRATION_TAG = "settings_calibration"
const val SETTINGS_HISTORY_TAG = "settings_history"
const val SETTINGS_RESTORE_SAMPLES_TAG = "settings_restore_samples"
const val SETTINGS_CLEAR_ALL_TAG = "settings_clear_all"
const val SETTINGS_ABOUT_TAG = "settings_about"
