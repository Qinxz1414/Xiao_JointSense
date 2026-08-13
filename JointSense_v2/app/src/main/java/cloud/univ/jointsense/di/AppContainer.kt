package cloud.univ.jointsense.di

import android.app.Application
import androidx.room.Room
import cloud.univ.jointsense.data.RoomCalibrationRepository
import cloud.univ.jointsense.data.RoomDataManagementRepository
import cloud.univ.jointsense.data.RoomTestSessionRepository
import cloud.univ.jointsense.data.legacy.LegacyMigrationCoordinator
import cloud.univ.jointsense.calibration.LegacyCalibrationRevalidator
import cloud.univ.jointsense.database.JointSenseDatabase
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import cloud.univ.jointsense.domain.repository.DataManagementRepository
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import cloud.univ.jointsense.locale.AppCompatLanguageController
import cloud.univ.jointsense.measurement.AndroidBaselinePhotoAnalysisAdapter
import cloud.univ.jointsense.measurement.BaselinePhotoAnalysisAdapter
import cloud.univ.jointsense.settings.locale.LanguageController

/**
 * Process-wide composition root. Every mutable data dependency is created once
 * and handed to feature-owned factories explicitly; no feature reaches back
 * into the application module.
 */
class AppContainer(
    application: Application,
    val database: JointSenseDatabase = createPersistentDatabase(application),
) {

    val testSessions: TestSessionRepository = RoomTestSessionRepository(database)
    val calibrations: CalibrationRepository = RoomCalibrationRepository(database)
    val legacyCalibrationRevalidator: LegacyCalibrationRevalidator =
        LegacyCalibrationRevalidator(calibrations)
    val dataManagement: DataManagementRepository = RoomDataManagementRepository(database)
    val migrationCoordinator: LegacyMigrationCoordinator =
        LegacyMigrationCoordinator(application, database)
    val measurementAnalysis: BaselinePhotoAnalysisAdapter =
        AndroidBaselinePhotoAnalysisAdapter(calibrations)
    val languageController: LanguageController = AppCompatLanguageController()

    private companion object {
        const val DATABASE_NAME = "jointsense.db"

        private fun createPersistentDatabase(application: Application): JointSenseDatabase =
            Room.databaseBuilder(
                application,
                JointSenseDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
