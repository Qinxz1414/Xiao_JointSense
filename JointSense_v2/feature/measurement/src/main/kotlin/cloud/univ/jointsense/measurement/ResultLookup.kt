package cloud.univ.jointsense.measurement

import cloud.univ.jointsense.domain.model.TestResult
import cloud.univ.jointsense.domain.model.TestSession

data class LocatedResult(
    val session: TestSession,
    val result: TestResult,
)

/** Resolves a result id without allowing an unrelated selected session to shadow it. */
fun locateResultById(
    resultId: String,
    currentSession: TestSession?,
    sessions: List<TestSession>,
): LocatedResult? {
    currentSession?.results?.firstOrNull { it.id == resultId }?.let { result ->
        return LocatedResult(currentSession, result)
    }
    sessions.forEach { session ->
        session.results.firstOrNull { it.id == resultId }?.let { result ->
            return LocatedResult(session, result)
        }
    }
    return null
}
