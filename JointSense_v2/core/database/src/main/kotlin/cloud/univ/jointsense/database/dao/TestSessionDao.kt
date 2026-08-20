package cloud.univ.jointsense.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cloud.univ.jointsense.database.entity.TestResultEntity
import cloud.univ.jointsense.database.entity.TestSessionEntity
import cloud.univ.jointsense.database.entity.TestSessionWithResults
import cloud.univ.jointsense.database.entity.MeasurementBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TestSessionDao {
    @Transaction
    @Query("SELECT * FROM test_session ORDER BY createdAt DESC")
    fun sessions(): Flow<List<TestSessionWithResults>>

    @Transaction
    @Query("SELECT * FROM test_session WHERE id = :id")
    fun session(id: String): Flow<TestSessionWithResults?>

    @Query("SELECT * FROM test_result WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun resultsForSession(sessionId: String): Flow<List<TestResultEntity>>

    @Query("SELECT id FROM test_result WHERE draftId = :draftId LIMIT 1")
    suspend fun resultIdForDraft(draftId: String): String?

    @Query("SELECT COUNT(*) FROM test_result WHERE draftId = :draftId")
    suspend fun resultCountForDraft(draftId: String): Int

    @Query("SELECT * FROM measurement_batch WHERE draftId = :draftId LIMIT 1")
    suspend fun measurementBatchForDraft(draftId: String): MeasurementBatchEntity?

    @Query("SELECT * FROM test_result WHERE measurementBatchId = :batchId ORDER BY timestamp ASC, id ASC")
    suspend fun resultsForMeasurementBatch(batchId: String): List<TestResultEntity>

    @Query("SELECT COUNT(*) FROM test_result WHERE measurementBatchId = :batchId")
    suspend fun resultCountForMeasurementBatch(batchId: String): Int

    @Insert
    suspend fun insertSession(session: TestSessionEntity)

    @Insert
    suspend fun insertResult(result: TestResultEntity)

    @Insert
    suspend fun insertResults(results: List<TestResultEntity>)

    @Insert
    suspend fun insertMeasurementBatch(batch: MeasurementBatchEntity)

    @Upsert
    suspend fun upsertSession(session: TestSessionEntity)

    @Upsert
    suspend fun upsertResult(result: TestResultEntity)

    @Query("DELETE FROM test_session WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM test_session")
    suspend fun deleteAllSessions()
}
