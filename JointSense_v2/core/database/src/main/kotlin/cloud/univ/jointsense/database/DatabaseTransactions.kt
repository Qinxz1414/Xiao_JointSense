package cloud.univ.jointsense.database

import androidx.room.withTransaction
import cloud.univ.jointsense.database.entity.AppMetadataEntity
import cloud.univ.jointsense.database.entity.TestResultEntity
import cloud.univ.jointsense.database.entity.MeasurementBatchEntity
import cloud.univ.jointsense.domain.model.inflammationFactorPresentationOrder

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

    suspend fun commitMeasurement(
        batch: MeasurementBatchEntity,
        results: List<TestResultEntity>,
    ): String = database.withTransaction {
        require(results.size == inflammationFactorPresentationOrder.size)
        require(results.map(TestResultEntity::factor) == inflammationFactorPresentationOrder)
        require(results.all { it.sessionId == batch.sessionId && it.measurementBatchId == batch.id })

        database.testSessionDao().measurementBatchForDraft(batch.draftId)?.let { existing ->
            check(existing.sessionId == batch.sessionId) { "Measurement draft belongs to another session." }
            val existingResults = database.testSessionDao().resultsForMeasurementBatch(existing.id)
            check(
                existingResults.size == inflammationFactorPresentationOrder.size &&
                    existingResults.map(TestResultEntity::factor).toSet() ==
                    inflammationFactorPresentationOrder.toSet(),
            ) {
                "Existing measurement batch is incomplete or has an invalid factor order."
            }
            return@withTransaction existing.id
        }

        database.testSessionDao().insertMeasurementBatch(batch)
        database.testSessionDao().insertResults(results)
        check(database.testSessionDao().resultCountForMeasurementBatch(batch.id) == results.size)
        batch.id
    }

    suspend fun clearAllData() = database.withTransaction {
        database.testSessionDao().deleteAllSessions()
        database.calibrationDao().deleteAllCalibrations()
        database.metadataDao().put(AppMetadataEntity("samplesInitialized", "true"))
    }
}
