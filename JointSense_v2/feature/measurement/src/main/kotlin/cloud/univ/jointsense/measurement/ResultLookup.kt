package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession

sealed interface ResultResolution {
    data object Loading : ResultResolution

    data class Found(
        val session: TestSession,
        val result: TestResult,
    ) : ResultResolution {
        val canContinue: Boolean
            get() = session.results.size < MAX_RESULTS_PER_SESSION
    }

    data object NotFound : ResultResolution

    private companion object {
        const val MAX_RESULTS_PER_SESSION = 5
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
    currentSession?.results?.firstOrNull { it.id == resultId }?.let { result ->
        return ResultResolution.Found(currentSession, result)
    }
    sessions.forEach { session ->
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
