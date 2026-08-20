package cloud.univ.jointsense.domain.repository

import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.NewMeasurementBatch
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.TestSession
import kotlinx.coroutines.flow.Flow

interface TestSessionRepository {
    fun observeSessions(): Flow<List<TestSession>>
    fun observeSession(id: String): Flow<TestSession?>
    suspend fun createSession(name: String, source: DataSource = DataSource.USER): String
    suspend fun commitResult(sessionId: String, draftId: String, result: NewTestResult): String
    suspend fun commitMeasurement(
        sessionId: String,
        draftId: String,
        measurement: NewMeasurementBatch,
    ): String
    suspend fun deleteSession(id: String)
}
