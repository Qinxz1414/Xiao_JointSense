package cloud.univ.jointsense.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.database.entity.AppMetadataEntity
import cloud.univ.jointsense.database.entity.CalibrationEntity
import cloud.univ.jointsense.database.entity.CalibrationKnotEntity
import cloud.univ.jointsense.database.entity.TestResultEntity
import cloud.univ.jointsense.database.entity.TestSessionEntity
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JointSenseDatabaseTest {
    private lateinit var db: JointSenseDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, JointSenseDatabase::class.java).build()
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun deletingSessionCascadesResults() = runTest {
        db.testSessionDao().insertSession(testSessionFixture())
        db.testSessionDao().insertResult(testResultFixture())

        db.testSessionDao().deleteSession("s1")

        assertTrue(db.testSessionDao().resultsForSession("s1").first().isEmpty())
    }

    @Test
    fun duplicateNonNullDraftIdIsRejected() = runTest {
        db.testSessionDao().insertSession(testSessionFixture())
        db.testSessionDao().insertResult(testResultFixture())

        assertConstraintViolation {
            db.testSessionDao().insertResult(testResultFixture(id = "r2"))
        }
        assertEquals(1, db.testSessionDao().resultCountForDraft("d1"))
    }

    @Test
    fun commitResultReturnsExistingIdForDuplicateDraft() = runTest {
        db.testSessionDao().insertSession(testSessionFixture())
        val transactions = DatabaseTransactions(db)

        val firstId = transactions.commitResult(testResultFixture())
        val duplicateId = transactions.commitResult(testResultFixture(id = "r2"))

        assertEquals("r1", firstId)
        assertEquals("r1", duplicateId)
        assertEquals(1, db.testSessionDao().resultCountForDraft("d1"))
    }

    @Test
    fun deletingCalibrationCascadesKnots() = runTest {
        db.calibrationDao().insertCalibration(calibrationFixture())
        db.calibrationDao().insertKnots(listOf(calibrationKnotFixture()))

        db.calibrationDao().deleteCalibration(InflammationFactor.IL6)

        assertTrue(db.calibrationDao().knotsForFactor(InflammationFactor.IL6).first().isEmpty())
    }

    @Test
    fun clearAllDataClearsTablesAndMarksSamplesInitialized() = runTest {
        db.testSessionDao().insertSession(testSessionFixture())
        db.testSessionDao().insertResult(testResultFixture())
        db.calibrationDao().insertCalibration(calibrationFixture())
        db.calibrationDao().insertKnots(listOf(calibrationKnotFixture()))
        db.metadataDao().put(AppMetadataEntity("samplesInitialized", "false"))

        DatabaseTransactions(db).clearAllData()

        assertTrue(db.testSessionDao().sessions().first().isEmpty())
        assertTrue(db.calibrationDao().calibrations().first().isEmpty())
        assertEquals("true", db.metadataDao().value("samplesInitialized").first())
    }
}

private fun testSessionFixture() = TestSessionEntity(
    id = "s1",
    name = "Test #1",
    createdAt = 1L,
    source = DataSource.USER,
)

private fun testResultFixture(
    id: String = "r1",
    sessionId: String = "s1",
    draftId: String? = "d1",
) = TestResultEntity(
    id = id,
    sessionId = sessionId,
    draftId = draftId,
    factor = InflammationFactor.IL6,
    concentration = 10f,
    rangeStatus = RangeStatus.IN_RANGE,
    timestamp = 2L,
    rMean = 90f,
    gMean = 100f,
    bMean = 110f,
    rStd = 1f,
    gStd = 1f,
    bStd = 1f,
)

private fun calibrationFixture() = CalibrationEntity(
    factor = InflammationFactor.IL6,
    createdAt = 3L,
    version = 1,
    status = CalibrationStatus.ACTIVE,
    kitName = "kit",
    kitLot = "lot",
)

private fun calibrationKnotFixture() = CalibrationKnotEntity(
    factor = InflammationFactor.IL6,
    position = 0,
    concentration = 5f,
    rawSignal = 20f,
    netSignal = 15f,
    fittedSignal = 14f,
    isBlank = false,
)

private suspend fun assertConstraintViolation(block: suspend () -> Unit) {
    var constraintViolated = false
    try {
        block()
    } catch (_: SQLiteConstraintException) {
        constraintViolated = true
    }
    assertTrue("Expected SQLiteConstraintException", constraintViolated)
}
