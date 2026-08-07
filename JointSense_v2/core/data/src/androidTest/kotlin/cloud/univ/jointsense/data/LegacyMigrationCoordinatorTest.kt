package cloud.univ.jointsense.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cloud.univ.jointsense.data.legacy.LegacyMigrationCoordinator
import cloud.univ.jointsense.data.legacy.MigrationOutcome
import cloud.univ.jointsense.database.JointSenseDatabase
import cloud.univ.jointsense.database.entity.TestSessionEntity
import cloud.univ.jointsense.database.entity.toDomain
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMigrationCoordinatorTest {
    private lateinit var context: Context
    private lateinit var database: JointSenseDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("joint_sense_data", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("joint_sense_calibration", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, JointSenseDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
        context.getSharedPreferences("joint_sense_data", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("joint_sense_calibration", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun validPayloadImportsEveryRowAndMarksLegacyCalibrationForReview() = runTest {
        legacyDataPrefs().edit().putString("sessions", VALID_SESSIONS).commit()
        legacyCalibrationPrefs().edit().putString("calibration", VALID_CALIBRATION).commit()

        val outcome = LegacyMigrationCoordinator(context, database).migrate()

        assertEquals(MigrationOutcome.Completed(sessions = 1, results = 2, calibrations = 2), outcome)
        val session = database.testSessionDao().sessions().first().single().toDomain()
        assertEquals(2, session.results.size)
        assertEquals(RangeStatus.UNKNOWN, session.results.first().rangeStatus)
        assertNull(session.results.first().draftId)
        val calibrations = database.calibrationDao().calibrations().first().map { it.toDomain() }
        assertEquals(2, calibrations.size)
        assertEquals(setOf(CalibrationStatus.NEEDS_REVIEW), calibrations.map { it.status }.toSet())
        assertEquals("COMPLETED", metadata("legacyMigrationStatus"))
        assertEquals("true", metadata("samplesInitialized"))
        assertEquals(VALID_SESSIONS, legacyDataPrefs().getString("sessions", null))
        assertEquals(VALID_CALIBRATION, legacyCalibrationPrefs().getString("calibration", null))
    }

    @Test
    fun malformedPayloadImportsNothingAndDoesNotWriteCompletionMarker() = runTest {
        legacyDataPrefs().edit().putString("sessions", MALFORMED_SECOND_RESULT).commit()
        legacyCalibrationPrefs().edit().putString("calibration", VALID_CALIBRATION).commit()

        val outcome = LegacyMigrationCoordinator(context, database).migrate()

        assertEquals(true, outcome is MigrationOutcome.Failed)
        assertEquals(0, database.testSessionDao().sessions().first().size)
        assertEquals(0, database.calibrationDao().calibrations().first().size)
        assertNull(metadata("legacyMigrationStatus"))
        assertNull(metadata("samplesInitialized"))
    }

    @Test
    fun databaseConstraintFailureRollsBackEveryImportedRowAndMarker() = runTest {
        legacyDataPrefs().edit().putString("sessions", DUPLICATE_RESULT_IDS).commit()

        val outcome = LegacyMigrationCoordinator(context, database).migrate()

        assertEquals(true, outcome is MigrationOutcome.Failed)
        assertEquals(0, database.testSessionDao().sessions().first().size)
        assertNull(metadata("legacyMigrationStatus"))
    }

    @Test
    fun noLegacyDataSeedsTwelveStableSamplesOnlyOnce() = runTest {
        val coordinator = LegacyMigrationCoordinator(context, database)

        assertEquals(MigrationOutcome.Completed(12, 36, 0), coordinator.migrate())
        val first = database.testSessionDao().sessions().first().map { it.toDomain() }
        assertEquals(12, first.size)
        assertEquals(36, first.sumOf { it.results.size })
        assertEquals(12, first.map { it.id }.toSet().size)
        assertEquals(setOf(DataSource.BUILT_IN), first.map { it.source }.toSet())
        assertEquals(0f, first.single { it.id == "builtin-tc1" }.results.single {
            it.factor == InflammationFactor.TNF_ALPHA
        }.concentration)
        assertEquals(50f, first.single { it.id == "builtin-clip-3" }.results.single {
            it.factor == InflammationFactor.TNF_ALPHA
        }.concentration)

        assertEquals(MigrationOutcome.AlreadyCompleted, coordinator.migrate())
        assertEquals(12, database.testSessionDao().sessions().first().size)
    }

    @Test
    fun skipLegacyAndStartFreshClearsPartialRowsAndPersistsNoRetryChoice() = runTest {
        database.testSessionDao().insertSession(TestSessionEntity("partial", "Partial", 1L, DataSource.USER))
        legacyDataPrefs().edit().putString("sessions", VALID_SESSIONS).commit()
        val coordinator = LegacyMigrationCoordinator(context, database)

        assertEquals(MigrationOutcome.SkippedByUser, coordinator.skipLegacyAndStartFresh())
        assertEquals(0, database.testSessionDao().sessions().first().size)
        assertEquals("SKIPPED_BY_USER", metadata("legacyMigrationStatus"))
        assertEquals("true", metadata("samplesInitialized"))
        assertEquals(MigrationOutcome.SkippedByUser, coordinator.migrate())
        assertEquals(0, database.testSessionDao().sessions().first().size)
        assertEquals(VALID_SESSIONS, legacyDataPrefs().getString("sessions", null))
    }

    @Test
    fun roomTestSessionRepositoryCommitsDraftOnlyOnceAndExposesDomainRows() = runTest {
        val ids = ArrayDeque(listOf("session-id", "result-id-1", "result-id-2"))
        val repository = RoomTestSessionRepository(database) { ids.removeFirst() }
        val sessionId = repository.createSession("New test")
        val result = NewTestResult(
            factor = InflammationFactor.IL6,
            concentration = 12f,
            rangeStatus = RangeStatus.IN_RANGE,
            features = RgbFeatures(1f, 2f, 3f, 4f, 5f, 6f),
            timestamp = 99L,
        )

        val firstId = repository.commitResult(sessionId, "draft-1", result)
        val secondId = repository.commitResult(sessionId, "draft-1", result.copy(concentration = 999f))

        assertEquals("result-id-1", firstId)
        assertEquals(firstId, secondId)
        val stored = repository.observeSession(sessionId).first()!!
        assertEquals(1, stored.results.size)
        assertEquals(12f, stored.results.single().concentration)
        repository.deleteSession(sessionId)
        assertNull(repository.observeSession(sessionId).first())
    }

    @Test
    fun roomCalibrationRepositorySavesObservesAndClearsCalibrations() = runTest {
        val repository = RoomCalibrationRepository(database)
        val calibration = Calibration(
            factor = InflammationFactor.IL1_BETA,
            createdAt = 10L,
            version = 2,
            status = CalibrationStatus.ACTIVE,
            kitName = "Kit",
            kitLot = "Lot",
            knots = listOf(CalibrationKnot(0, 0f, -1f, 0f, 0f, true)),
        )

        repository.save(calibration)

        assertEquals(calibration, repository.observeCalibration(InflammationFactor.IL1_BETA).first())
        assertEquals(listOf(calibration), repository.observeCalibrations().first())
        repository.clearAll()
        assertEquals(emptyList<Calibration>(), repository.observeCalibrations().first())
    }

    @Test
    fun clearDoesNotAutoReseedButExplicitRestoreRecreatesStableSamples() = runTest {
        val migration = LegacyMigrationCoordinator(context, database)
        val repository = RoomDataManagementRepository(database)
        assertEquals(MigrationOutcome.Completed(12, 36, 0), migration.migrate())

        repository.clearAllData()

        assertEquals(0, database.testSessionDao().sessions().first().size)
        assertEquals(MigrationOutcome.AlreadyCompleted, LegacyMigrationCoordinator(context, database).migrate())
        assertEquals(0, database.testSessionDao().sessions().first().size)
        repository.restoreBuiltInSamples()
        repository.restoreBuiltInSamples()
        val restored = database.testSessionDao().sessions().first().map { it.toDomain() }
        assertEquals(12, restored.size)
        assertEquals(36, restored.sumOf { it.results.size })
        assertEquals("true", metadata("samplesInitialized"))
    }

    private fun legacyDataPrefs() =
        context.getSharedPreferences("joint_sense_data", Context.MODE_PRIVATE)

    private fun legacyCalibrationPrefs() =
        context.getSharedPreferences("joint_sense_calibration", Context.MODE_PRIVATE)

    private suspend fun metadata(key: String): String? = database.metadataDao().value(key).first()

    private companion object {
        const val RESULT_1 = """{
            "id":"result-1","factor":"TNF_ALPHA","concentration":42.5,"timestamp":124,
            "rMean":100.0,"gMean":110.0,"bMean":130.0,"rStd":1.0,"gStd":2.0,"bStd":3.0
        }"""
        const val RESULT_2 = """{
            "id":"result-2","factor":"IL6","concentration":12.0,"timestamp":125,
            "rMean":101.0,"gMean":111.0,"bMean":131.0,"rStd":1.1,"gStd":2.1,"bStd":3.1
        }"""
        val VALID_SESSIONS = """[{
            "id":"session-1","name":"Legacy test","createdAt":123,
            "results":[$RESULT_1,$RESULT_2]
        }]""".trimIndent()
        val MALFORMED_SECOND_RESULT = VALID_SESSIONS.replace(
            "\"concentration\":12.0",
            "\"concentration\":\"bad\"",
        )
        val DUPLICATE_RESULT_IDS = """[
            {"id":"session-1","name":"One","createdAt":1,"results":[$RESULT_1]},
            {"id":"session-2","name":"Two","createdAt":2,"results":[$RESULT_1]}
        ]""".trimIndent()
        val VALID_CALIBRATION = """{
            "createdAt":456,
            "factors":{
                "IL6":[{"c":0.0,"s":-8.0},{"c":5.0,"s":2.5}],
                "IL1_BETA":[{"c":0.0,"s":-10.0}]
            }
        }""".trimIndent()
    }
}
