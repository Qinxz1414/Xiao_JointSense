package cloud.univ.jointsense.navigation

import kotlinx.serialization.Serializable

@Serializable
enum class TopLevelDestination {
    HOME,
    TRENDS,
    REPORT,
    PROFILE,
}

internal fun TopLevelDestination.route(): JointSenseRoute = when (this) {
    TopLevelDestination.HOME -> HomeRoute
    TopLevelDestination.TRENDS -> TrendsRoute
    TopLevelDestination.REPORT -> ReportRoute
    TopLevelDestination.PROFILE -> ProfileRoute
}
