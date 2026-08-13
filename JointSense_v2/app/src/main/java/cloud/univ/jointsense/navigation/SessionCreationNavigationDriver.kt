package cloud.univ.jointsense.navigation

import cloud.univ.jointsense.measurement.MeasurementViewModel

internal class SessionCreationNavigationDriver(
    private val measurement: MeasurementViewModel,
    private val actions: NavigationActions,
) {
    fun request(origin: TopLevelDestination, sessionNamePrefix: String) {
        measurement.createNewSession(origin.name, sessionNamePrefix)
    }

    fun synchronize(
        currentOrigin: TopLevelDestination?,
        routeReady: Boolean = true,
    ) {
        if (!routeReady) return
        val request = measurement.state.value.sessionCreationRequest ?: return
        val requestOrigin = TopLevelDestination.entries.firstOrNull {
            it.name == request.originIdentity
        }
        if (requestOrigin == null || requestOrigin != currentOrigin) {
            measurement.cancelSessionCreation(request.requestId)
            return
        }
        if (request.completedSessionId == null) return
        if (measurement.acceptSessionCreation(request.requestId) != null) {
            actions.startMeasurement(requestOrigin)
        }
    }
}
