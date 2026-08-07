package cloud.univ.jointsense.data

import cloud.univ.jointsense.database.DatabaseTransactions
import cloud.univ.jointsense.database.JointSenseDatabase
import cloud.univ.jointsense.database.entity.TestResultEntity
import cloud.univ.jointsense.database.entity.TestSessionEntity
import cloud.univ.jointsense.database.entity.toDomain
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.NewTestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.repository.TestSessionRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTestSessionRepository(
    database: JointSenseDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : TestSessionRepository {
    private val dao = database.testSessionDao()
    private val transactions = DatabaseTransactions(database)

    override fun observeSessions(): Flow<List<TestSession>> =
        dao.sessions().map { sessions -> sessions.map { it.toDomain() } }

    override fun observeSession(id: String): Flow<TestSession?> =
        dao.session(id).map { it?.toDomain() }

    override suspend fun createSession(name: String, source: DataSource): String {
        val id = idFactory()
        dao.insertSession(TestSessionEntity(id, name, clock(), source))
        return id
    }

    override suspend fun commitResult(
        sessionId: String,
        draftId: String,
        result: NewTestResult,
    ): String = transactions.commitResult(
        TestResultEntity(
            id = idFactory(),
            sessionId = sessionId,
            draftId = draftId,
            factor = result.factor,
            concentration = result.concentration,
            rangeStatus = result.rangeStatus,
            timestamp = result.timestamp,
            rMean = result.features.rMean,
            gMean = result.features.gMean,
            bMean = result.features.bMean,
            rStd = result.features.rStd,
            gStd = result.features.gStd,
            bStd = result.features.bStd,
        ),
    )

    override suspend fun deleteSession(id: String) {
        dao.deleteSession(id)
    }
}
