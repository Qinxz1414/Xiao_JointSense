package cloud.univ.jointsense.navigation

import kotlinx.serialization.Serializable

interface NavigationTarget

interface JointSenseRoute : NavigationTarget

@Serializable data object HomeRoute : JointSenseRoute
@Serializable data object TrendsRoute : JointSenseRoute
@Serializable data object ReportRoute : JointSenseRoute
@Serializable data object ProfileRoute : JointSenseRoute
@Serializable data object AboutRoute : JointSenseRoute
@Serializable data object HistoryRoute : JointSenseRoute

@Serializable
data class MeasurementGraph(val origin: TopLevelDestination) : NavigationTarget

@Serializable data object ImageSelectRoute : JointSenseRoute
@Serializable data object CropRoute : JointSenseRoute
@Serializable data object FactorSelectRoute : JointSenseRoute
@Serializable data class ResultRoute(val resultId: String) : JointSenseRoute

@Serializable data object CalibrationGraph : NavigationTarget
@Serializable data object CalibrationSelectRoute : JointSenseRoute
@Serializable data object CalibrationCropRoute : JointSenseRoute
@Serializable data object CalibrationAssignRoute : JointSenseRoute
@Serializable data object CalibrationReviewRoute : JointSenseRoute
@Serializable data object CalibrationDoneRoute : JointSenseRoute
