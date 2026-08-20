package cloud.univ.jointsense.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cloud.univ.jointsense.database.entity.AppMetadataEntity
import cloud.univ.jointsense.database.entity.CalibrationEntity
import cloud.univ.jointsense.database.entity.CalibrationKnotEntity
import cloud.univ.jointsense.database.entity.MeasurementBatchEntity
import cloud.univ.jointsense.database.entity.TestResultEntity
import cloud.univ.jointsense.database.entity.TestSessionEntity
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun commitMeasurementAtomicallyStoresAllThreeFactorResults() = runTest {
        val dao = db.testSessionDao()
        dao.insertSession(testSessionFixture())
        val transactions = DatabaseTransactions(db)
        val batch = measurementBatchFixture()
        val results = measurementResultsFixture(batch.id)

        val committedId = transactions.commitMeasurement(batch, results)

        assertEquals(batch.id, committedId)
        assertEquals(batch, dao.measurementBatchForDraft(batch.draftId))
        assertEquals(
            inflammationFactorPresentationOrder,
            dao.resultsForMeasurementBatch(batch.id).map(TestResultEntity::factor),
        )
        assertEquals(3, dao.resultCountForMeasurementBatch(batch.id))
        assertEquals(3, dao.resultsForSession(batch.sessionId).first().size)
    }

    @Test
    fun commitMeasurementReturnsExistingBatchForRepeatedDraftWithoutDuplicatingChildren() = runTest {
        val dao = db.testSessionDao()
        dao.insertSession(testSessionFixture())
        val transactions = DatabaseTransactions(db)
        val firstBatch = measurementBatchFixture(id = "batch-first")
        val firstResults = measurementResultsFixture(
            batchId = firstBatch.id,
            idPrefix = "first",
        )
        val retryBatch = measurementBatchFixture(id = "batch-retry")
        val retryResults = measurementResultsFixture(
            batchId = retryBatch.id,
            idPrefix = "retry",
            concentrationOffset = 1_000f,
        )

        val firstId = transactions.commitMeasurement(firstBatch, firstResults)
        val retryId = transactions.commitMeasurement(retryBatch, retryResults)

        assertEquals(firstBatch.id, firstId)
        assertEquals(firstBatch.id, retryId)
        assertEquals(firstBatch, dao.measurementBatchForDraft(firstBatch.draftId))
        assertEquals(firstResults, dao.resultsForMeasurementBatch(firstBatch.id))
        assertTrue(dao.resultsForMeasurementBatch(retryBatch.id).isEmpty())
    }

    @Test
    fun measurementBatchRejectsDuplicateFactorChildren() = runTest {
        val dao = db.testSessionDao()
        dao.insertSession(testSessionFixture())
        val batch = measurementBatchFixture()
        dao.insertMeasurementBatch(batch)
        dao.insertResult(
            testResultFixture(
                id = "tnf-first",
                draftId = null,
                factor = InflammationFactor.TNF_ALPHA,
                measurementBatchId = batch.id,
            ),
        )

        assertConstraintViolation {
            dao.insertResult(
                testResultFixture(
                    id = "tnf-duplicate",
                    draftId = null,
                    factor = InflammationFactor.TNF_ALPHA,
                    measurementBatchId = batch.id,
                ),
            )
        }
        assertEquals(1, dao.resultCountForMeasurementBatch(batch.id))
    }

    @Test
    fun commitMeasurementRollsBackBatchAndEarlierChildrenWhenLaterInsertFails() = runTest {
        val dao = db.testSessionDao()
        dao.insertSession(testSessionFixture())
        val existingBatch = measurementBatchFixture(
            id = "batch-existing",
            draftId = "draft-existing",
        )
        dao.insertMeasurementBatch(existingBatch)
        dao.insertResult(
            testResultFixture(
                id = "duplicate-result-id",
                draftId = null,
                factor = InflammationFactor.TNF_ALPHA,
                measurementBatchId = existingBatch.id,
            ),
        )
        val failingBatch = measurementBatchFixture(
            id = "batch-failing",
            draftId = "draft-failing",
        )
        val failingResults = measurementResultsFixture(
            batchId = failingBatch.id,
            idPrefix = "failing",
        ).toMutableList().also { results ->
            results[1] = results[1].copy(id = "duplicate-result-id")
        }

        assertConstraintViolation {
            DatabaseTransactions(db).commitMeasurement(failingBatch, failingResults)
        }

        assertNull(dao.measurementBatchForDraft(failingBatch.draftId))
        assertTrue(dao.resultsForMeasurementBatch(failingBatch.id).isEmpty())
        assertEquals(1, dao.resultCountForMeasurementBatch(existingBatch.id))
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
        assertTrue(db.testSessionDao().resultsForSession("s1").first().isEmpty())
        assertTrue(db.calibrationDao().calibrations().first().isEmpty())
        assertTrue(db.calibrationDao().knotsForFactor(InflammationFactor.IL6).first().isEmpty())
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
    factor: InflammationFactor = InflammationFactor.IL6,
    concentration: Float = 10f,
    measurementBatchId: String? = null,
) = TestResultEntity(
    id = id,
    sessionId = sessionId,
    draftId = draftId,
    factor = factor,
    concentration = concentration,
    rangeStatus = RangeStatus.IN_RANGE,
    timestamp = 2L,
    rMean = 90f,
    gMean = 100f,
    bMean = 110f,
    rStd = 1f,
    gStd = 1f,
    bStd = 1f,
    measurementBatchId = measurementBatchId,
)

private fun measurementBatchFixture(
    id: String = "batch-1",
    sessionId: String = "s1",
    draftId: String = "draft-1",
) = MeasurementBatchEntity(
    id = id,
    sessionId = sessionId,
    draftId = draftId,
    measuredAt = 2L,
)

private fun measurementResultsFixture(
    batchId: String,
    sessionId: String = "s1",
    idPrefix: String = "batch-result",
    concentrationOffset: Float = 0f,
): List<TestResultEntity> = inflammationFactorPresentationOrder.mapIndexed { index, factor ->
    testResultFixture(
        id = "$idPrefix-$index",
        sessionId = sessionId,
        draftId = null,
        factor = factor,
        concentration = concentrationOffset + index + 1f,
        measurementBatchId = batchId,
    )
}

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
