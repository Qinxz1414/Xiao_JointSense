package cloud.univ.jointsense.database

import androidx.room.withTransaction
import cloud.univ.jointsense.database.entity.AppMetadataEntity
import cloud.univ.jointsense.database.entity.TestResultEntity

class DatabaseTransactions(
    private val database: JointSenseDatabase,
) {
    suspend fun commitResult(result: TestResultEntity): String = database.withTransaction {
        result.draftId
            ?.let { database.testSessionDao().resultIdForDraft(it) }
            ?.let { return@withTransaction it }

        database.testSessionDao().insertResult(result)
        result.id
    }

    suspend fun clearAllData() = database.withTransaction {
        database.testSessionDao().deleteAllSessions()
        database.calibrationDao().deleteAllCalibrations()
        database.metadataDao().put(AppMetadataEntity("samplesInitialized", "true"))
    }
}
