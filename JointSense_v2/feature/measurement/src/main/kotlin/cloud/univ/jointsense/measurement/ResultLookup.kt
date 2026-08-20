package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession
import cloud.univ.jointsense.domain.model.TestMeasurementBatch
import cloud.univ.jointsense.domain.model.measurementBatchCount
import cloud.univ.jointsense.domain.model.measurementBatches

sealed interface ResultResolution {
    data object Loading : ResultResolution

    data class Found(
        val session: TestSession,
        val result: TestResult,
    ) : ResultResolution {
        val measurement: TestMeasurementBatch = session.measurementBatches().firstOrNull { batch ->
            batch.id == result.measurementBatchId || batch.results.any { it.id == result.id }
        } ?: TestMeasurementBatch(
            id = result.id,
            sessionId = session.id,
            timestamp = result.timestamp,
            results = listOf(result),
            isLegacySingleFactor = true,
        )

        val canContinue: Boolean
            get() = session.measurementBatchCount() < MAX_MEASUREMENTS_PER_SESSION
    }

    data object NotFound : ResultResolution

    private companion object {
        const val MAX_MEASUREMENTS_PER_SESSION = 5
    }
}

/** Resolves a result id without treating a repository that has not emitted as empty. */
fun resolveResultById(
    resultId: String,
    currentSession: TestSession?,
    sessions: List<TestSession>,
    hasReceivedSessionsSnapshot: Boolean,
    awaitingRepositoryResultId: String? = null,
): ResultResolution {
    currentSession?.measurementBatches()?.firstOrNull { it.id == resultId }?.let { measurement ->
        return ResultResolution.Found(currentSession, measurement.results.first())
    }
    currentSession?.results?.firstOrNull { it.id == resultId }?.let { result ->
        return ResultResolution.Found(currentSession, result)
    }
    sessions.forEach { session ->
        session.measurementBatches().firstOrNull { it.id == resultId }?.let { measurement ->
            return ResultResolution.Found(session, measurement.results.first())
        }
        session.results.firstOrNull { it.id == resultId }?.let { result ->
            return ResultResolution.Found(session, result)
        }
    }
    if (awaitingRepositoryResultId == resultId) return ResultResolution.Loading
    return if (hasReceivedSessionsSnapshot) {
        ResultResolution.NotFound
    } else {
        ResultResolution.Loading
    }
}
