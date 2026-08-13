package cloud.univ.jointsense.navigation

import androidx.navigation.NavHostController

internal interface NavigationDriver {
    fun navigate(target: NavigationTarget, launchSingleTop: Boolean)
    fun popOne(): Boolean
    fun popMeasurement(): Boolean
    fun popToImageSelect(): Boolean
    fun popCalibration(): Boolean
    fun popToHome()
    fun containsMeasurement(): Boolean
}

class NavigationActions internal constructor(
    private val driver: NavigationDriver,
) {
    constructor(navController: NavHostController) : this(
        NavControllerNavigationDriver(navController),
    )

    fun openTopLevel(destination: TopLevelDestination) {
        if (destination == TopLevelDestination.HOME) {
            driver.popToHome()
        } else {
            driver.navigate(destination.route(), launchSingleTop = true)
        }
    }

    fun startMeasurement(origin: TopLevelDestination) {
        driver.navigate(MeasurementGraph(origin), launchSingleTop = true)
    }

    fun openCrop() {
        driver.navigate(CropRoute, launchSingleTop = true)
    }

    fun openFactorSelect() {
        driver.navigate(FactorSelectRoute, launchSingleTop = true)
    }

    fun openResult(resultId: String) {
        driver.navigate(ResultRoute(resultId), launchSingleTop = true)
    }

    fun openHistory() {
        driver.navigate(HistoryRoute, launchSingleTop = true)
    }

    fun openAbout() {
        driver.navigate(AboutRoute, launchSingleTop = true)
    }

    fun startCalibration() {
        driver.navigate(CalibrationGraph, launchSingleTop = true)
    }

    fun openCalibrationCrop() {
        driver.navigate(CalibrationCropRoute, launchSingleTop = true)
    }

    fun openCalibrationAssign() {
        driver.navigate(CalibrationAssignRoute, launchSingleTop = true)
    }

    fun openCalibrationReview() {
        driver.navigate(CalibrationReviewRoute, launchSingleTop = true)
    }

    fun openCalibrationDone() {
        driver.navigate(CalibrationDoneRoute, launchSingleTop = true)
    }

    fun restartCalibration() {
        driver.popCalibration()
        driver.navigate(CalibrationGraph, launchSingleTop = true)
    }

    /** Natural back for ordinary destinations, including Crop and FactorSelect. */
    fun navigateBack(): Boolean = driver.popOne()

    /** Result exits an active measurement graph; historical results pop one entry. */
    fun exitResult(): Boolean = if (driver.containsMeasurement()) {
        driver.popMeasurement()
    } else {
        driver.popOne()
    }

    fun exitMeasurement(): Boolean = driver.popMeasurement()

    fun restartMeasurement(): Boolean = driver.popToImageSelect()

    fun continueMeasurementFromResult(origin: TopLevelDestination) {
        if (driver.containsMeasurement()) {
            driver.popMeasurement()
        } else {
            driver.popOne()
        }
        startMeasurement(origin)
    }

    fun exitCalibration(): Boolean = driver.popCalibration()

    fun goHome() {
        driver.popToHome()
    }

    fun isInMeasurement(): Boolean = driver.containsMeasurement()
}

private class NavControllerNavigationDriver(
    private val navController: NavHostController,
) : NavigationDriver {
    override fun navigate(target: NavigationTarget, launchSingleTop: Boolean) {
        navController.navigate(target) {
            this.launchSingleTop = launchSingleTop
        }
    }

    override fun popOne(): Boolean = navController.popBackStack()

    override fun popMeasurement(): Boolean =
        navController.popBackStack<MeasurementGraph>(inclusive = true)

    override fun popToImageSelect(): Boolean =
        navController.popBackStack<ImageSelectRoute>(inclusive = false)

    override fun popCalibration(): Boolean =
        navController.popBackStack<CalibrationGraph>(inclusive = true)

    override fun popToHome() {
        navController.navigate(HomeRoute) {
            popUpTo(HomeRoute) { inclusive = false }
            launchSingleTop = true
        }
    }

    override fun containsMeasurement(): Boolean = runCatching {
        navController.getBackStackEntry<MeasurementGraph>()
    }.isSuccess
}
