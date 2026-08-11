package cloud.univ.jointsense

import androidx.compose.runtime.Composable
import cloud.univ.jointsense.calibration.CalibrationAssignRouteScreen
import cloud.univ.jointsense.calibration.CalibrationCropRouteScreen
import cloud.univ.jointsense.calibration.CalibrationDoneRouteScreen
import cloud.univ.jointsense.calibration.CalibrationReviewRouteScreen
import cloud.univ.jointsense.calibration.CalibrationSelectRouteScreen
import cloud.univ.jointsense.calibration.CalibrationViewModel
import cloud.univ.jointsense.di.AppContainer
import cloud.univ.jointsense.insights.HomeRouteScreen
import cloud.univ.jointsense.insights.InsightsViewModel
import cloud.univ.jointsense.insights.ReportRouteScreen
import cloud.univ.jointsense.insights.TrendsRouteScreen
import cloud.univ.jointsense.measurement.CropRouteScreen
import cloud.univ.jointsense.measurement.FactorSelectRouteScreen
import cloud.univ.jointsense.measurement.HistoryRouteScreen
import cloud.univ.jointsense.measurement.ImageSelectRouteScreen
import cloud.univ.jointsense.measurement.MeasurementViewModel
import cloud.univ.jointsense.measurement.ResultRouteScreen
import cloud.univ.jointsense.settings.SettingsRouteScreen
import cloud.univ.jointsense.settings.SettingsViewModel
import cloud.univ.jointsense.settings.locale.LanguageController
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModuleBoundarySmokeTest {
    @Test
    fun appContainerPublishesEveryRepositoryAndTheMigrationCoordinator() {
        assertNotNull(AppContainer::testSessions)
        assertNotNull(AppContainer::calibrations)
        assertNotNull(AppContainer::dataManagement)
        assertNotNull(AppContainer::migrationCoordinator)
        assertNotNull(AppContainer::legacyCalibrationRevalidator)
    }
}

/**
 * Compile-time boundary smoke: every app destination is forced through a
 * public feature-owned entry. These are real imports and calls, not source
 * text checks; moving an entry back into :app breaks test compilation.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun ImportedFeatureRouteEntries(
    container: AppContainer,
    calibration: CalibrationViewModel,
    insights: InsightsViewModel,
    measurement: MeasurementViewModel,
    settings: SettingsViewModel,
    languageController: LanguageController,
) {
    HomeRouteScreen(insights, onStartMeasurement = {}, onOpenReport = {})
    TrendsRouteScreen(insights)
    ReportRouteScreen(insights)

    ImageSelectRouteScreen(measurement, onImageReady = {}, onBack = {})
    CropRouteScreen(measurement, onConfirm = {}, onBack = {})
    FactorSelectRouteScreen(measurement, onResultReady = {}, onBack = {})
    ResultRouteScreen(
        viewModel = measurement,
        resultId = "result-id",
        onContinueMeasurement = {},
        onReturnToOrigin = {},
    )
    HistoryRouteScreen(measurement, onOpenResult = {}, onBack = {})

    CalibrationSelectRouteScreen(calibration, onImageReady = {}, onBack = {})
    CalibrationCropRouteScreen(calibration, onSignalsReady = {}, onBack = {})
    CalibrationAssignRouteScreen(calibration, onReviewReady = {}, onBack = {})
    CalibrationReviewRouteScreen(calibration, onSaved = {}, onBack = {})
    CalibrationDoneRouteScreen(calibration, onDone = {}, onAnother = {}, onBack = {})
    SettingsRouteScreen(
        viewModel = settings,
        languageController = languageController,
        onOpenHistory = {},
        onCalibrate = {},
    )
}
