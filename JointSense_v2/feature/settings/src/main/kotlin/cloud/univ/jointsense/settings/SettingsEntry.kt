package cloud.univ.jointsense.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cloud.univ.jointsense.settings.locale.LanguageController

@Composable
fun SettingsRouteScreen(
    viewModel: SettingsViewModel,
    languageController: LanguageController,
    onOpenHistory: () -> Unit,
    onCalibrate: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        selectedLanguage = languageController.current(),
        readCurrentLanguage = languageController::current,
        onApplyLanguage = languageController::apply,
        onOpenHistory = onOpenHistory,
        onCalibrate = onCalibrate,
        onOpenAbout = onOpenAbout,
        onRequestClearAll = viewModel::requestClearAllConfirmation,
        onRequestRestoreSamples = viewModel::requestRestoreBuiltInSamplesConfirmation,
        onConfirmDataAction = viewModel::confirmDataAction,
        onDismissDataAction = viewModel::dismissDataAction,
        onRetryDataAction = viewModel::retryDataAction,
        onConsumeDataActionResult = viewModel::consumeDataActionResult,
        modifier = modifier,
    )
}
