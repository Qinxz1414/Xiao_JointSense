package cloud.univ.jointsense.data.legacy

import android.content.Context
import androidx.room.withTransaction
import cloud.univ.jointsense.data.BuiltInSampleProvider
import cloud.univ.jointsense.data.SAMPLES_INITIALIZED_KEY
import cloud.univ.jointsense.data.restoreBuiltInSamples
import cloud.univ.jointsense.database.JointSenseDatabase
import cloud.univ.jointsense.database.entity.AppMetadataEntity
import cloud.univ.jointsense.database.entity.toEntity
import kotlinx.coroutines.CancellationException

class LegacyMigrationCoordinator(
    context: Context,
    private val database: JointSenseDatabase,
    private val parser: LegacyJsonParser = LegacyJsonParser(),
    private val samples: BuiltInSampleProvider = BuiltInSampleProvider(),
) {
    private val dataPreferences = context.applicationContext.getSharedPreferences(
        LEGACY_DATA_PREFERENCES,
        Context.MODE_PRIVATE,
    )
    private val calibrationPreferences = context.applicationContext.getSharedPreferences(
        LEGACY_CALIBRATION_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    suspend fun migrate(): MigrationOutcome {
        when (database.metadataDao().getValue(MIGRATION_STATUS_KEY)) {
            STATUS_COMPLETED -> return MigrationOutcome.AlreadyCompleted
            STATUS_SKIPPED_BY_USER -> return MigrationOutcome.SkippedByUser
        }

        val sessions = try {
            dataPreferences.getString(LEGACY_SESSIONS_KEY, null)
                ?.let(parser::parseSessions)
                .orEmpty()
        } catch (exception: Exception) {
            return exception.toFailure()
        }
        val calibrations = try {
            calibrationPreferences.getString(LEGACY_CALIBRATION_KEY, null)
                ?.let(parser::parseCalibrations)
                .orEmpty()
        } catch (exception: Exception) {
            return exception.toFailure()
        }

        return try {
            database.withTransaction {
                sessions.forEach { session ->
                    database.testSessionDao().insertSession(session.toEntity())
                    session.results.forEach { result -> database.testSessionDao().insertResult(result.toEntity()) }
                }
                calibrations.forEach { calibration ->
                    database.calibrationDao().replaceCalibration(
                        calibration = calibration.toEntity(),
                        knots = calibration.knots.map { it.toEntity(calibration.factor) },
                    )
                }

                val shouldSeed = sessions.isEmpty() &&
                    database.metadataDao().getValue(SAMPLES_INITIALIZED_KEY) != "true"
                if (shouldSeed) database.restoreBuiltInSamples(samples)

                database.metadataDao().put(AppMetadataEntity(MIGRATION_STATUS_KEY, STATUS_COMPLETED))
                database.metadataDao().put(AppMetadataEntity(SAMPLES_INITIALIZED_KEY, "true"))

                MigrationOutcome.Completed(
                    sessions = sessions.size + if (shouldSeed) samples.sessions.size else 0,
                    results = sessions.sumOf { it.results.size } +
                        if (shouldSeed) samples.sessions.sumOf { it.results.size } else 0,
                    calibrations = calibrations.size,
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            exception.toFailure()
        }
    }

    suspend fun skipLegacyAndStartFresh(): MigrationOutcome = try {
        database.withTransaction {
            database.testSessionDao().deleteAllSessions()
            database.calibrationDao().deleteAllCalibrations()
            database.metadataDao().put(AppMetadataEntity(MIGRATION_STATUS_KEY, STATUS_SKIPPED_BY_USER))
            database.metadataDao().put(AppMetadataEntity(SAMPLES_INITIALIZED_KEY, "true"))
        }
        MigrationOutcome.SkippedByUser
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        exception.toFailure()
    }

    private fun Exception.toFailure(): MigrationOutcome.Failed =
        MigrationOutcome.Failed(message ?: this::class.java.simpleName)

    private companion object {
        const val LEGACY_DATA_PREFERENCES = "joint_sense_data"
        const val LEGACY_SESSIONS_KEY = "sessions"
        const val LEGACY_CALIBRATION_PREFERENCES = "joint_sense_calibration"
        const val LEGACY_CALIBRATION_KEY = "calibration"
        const val MIGRATION_STATUS_KEY = "legacyMigrationStatus"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_SKIPPED_BY_USER = "SKIPPED_BY_USER"
    }
}
