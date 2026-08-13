package cloud.univ.jointsense.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
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
