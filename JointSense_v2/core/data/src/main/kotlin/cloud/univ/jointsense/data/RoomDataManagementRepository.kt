package cloud.univ.jointsense.data

import androidx.room.withTransaction
import cloud.univ.jointsense.database.DatabaseTransactions
import cloud.univ.jointsense.database.JointSenseDatabase
import cloud.univ.jointsense.database.entity.AppMetadataEntity
import cloud.univ.jointsense.database.entity.toEntity
import cloud.univ.jointsense.domain.repository.DataManagementRepository

class RoomDataManagementRepository(
    private val database: JointSenseDatabase,
    private val transactions: DatabaseTransactions = DatabaseTransactions(database),
    private val samples: BuiltInSampleProvider = BuiltInSampleProvider(),
) : DataManagementRepository {
    override suspend fun clearAllData() {
        transactions.clearAllData()
    }

    override suspend fun restoreBuiltInSamples() = database.withTransaction {
        database.restoreBuiltInSamples(samples)
    }
}

internal suspend fun JointSenseDatabase.restoreBuiltInSamples(samples: BuiltInSampleProvider) {
    samples.sessions.forEach { session ->
        testSessionDao().upsertSession(session.toEntity())
        session.results.forEach { result -> testSessionDao().upsertResult(result.toEntity()) }
    }
    metadataDao().put(AppMetadataEntity(SAMPLES_INITIALIZED_KEY, "true"))
}

internal const val SAMPLES_INITIALIZED_KEY = "samplesInitialized"
