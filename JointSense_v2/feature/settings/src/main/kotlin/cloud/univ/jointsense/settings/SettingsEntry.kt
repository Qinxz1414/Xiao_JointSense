package cloud.univ.jointsense.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.univ.jointsense.settings.locale.LanguageController

/**
 * Public app-facing settings entry. Language selection remains deliberately
 * outside this Task 7 migration; the controller is accepted at the boundary
 * established by the locale infrastructure task.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SettingsRouteScreen(
    viewModel: SettingsViewModel,
    languageController: LanguageController,
    onOpenHistory: () -> Unit,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onOpenHistory = onOpenHistory,
        onCalibrate = onCalibrate,
        onClearAllData = viewModel::clearAllData,
        onConfirmRestoreSamples = viewModel::confirmRestoreBuiltInSamples,
        onCancelRestoreSamples = viewModel::cancelRestoreBuiltInSamplesConfirmation,
        onDismissRestoreSamplesOutcome = viewModel::dismissRestoreSamplesOutcome,
        modifier = modifier,
    )
}
