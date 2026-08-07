package cloud.univ.jointsense.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationActionsTest {
    @Test
    fun topLevelDestinationsPreserveHistoryForBack() {
        val driver = StackNavigationDriver(mutableListOf(HomeRoute))
        val actions = NavigationActions(driver)

        actions.openTopLevel(TopLevelDestination.TRENDS)
        actions.openTopLevel(TopLevelDestination.REPORT)
        assertEquals(listOf(HomeRoute, TrendsRoute, ReportRoute), driver.stack)

        actions.navigateBack()
        assertEquals(listOf(HomeRoute, TrendsRoute), driver.stack)

        actions.navigateBack()
        assertEquals(listOf(HomeRoute), driver.stack)
    }

    @Test
    fun openingHomeClearsTopLevelHistoryAndLeavesTheUniqueRoot() {
        val driver = StackNavigationDriver(mutableListOf(HomeRoute))
        val actions = NavigationActions(driver)

        actions.openTopLevel(TopLevelDestination.TRENDS)
        actions.openTopLevel(TopLevelDestination.REPORT)
        actions.openTopLevel(TopLevelDestination.HOME)

        assertEquals(listOf(HomeRoute), driver.stack)
        assertEquals(false, actions.navigateBack())
    }

    @Test
    fun cropBackReturnsToImageSelect() {
        val driver = StackNavigationDriver(mutableListOf(HomeRoute))
        val actions = NavigationActions(driver)

        actions.startMeasurement(TopLevelDestination.HOME)
        actions.openCrop()
        assertEquals(
            listOf(HomeRoute, MeasurementGraph(TopLevelDestination.HOME), ImageSelectRoute, CropRoute),
            driver.stack,
        )

        actions.navigateBack()

        assertEquals(
            listOf(HomeRoute, MeasurementGraph(TopLevelDestination.HOME), ImageSelectRoute),
            driver.stack,
        )
    }

    @Test
    fun resultBackPopsMeasurementGraphAndRestoresOrigin() {
        val driver = StackNavigationDriver(mutableListOf(HomeRoute))
        val actions = NavigationActions(driver)

        actions.openTopLevel(TopLevelDestination.TRENDS)
        actions.startMeasurement(TopLevelDestination.TRENDS)
        actions.openCrop()
        actions.openFactorSelect()
        actions.openResult("actual-result-id")
        actions.exitResult()

        assertEquals(listOf(HomeRoute, TrendsRoute), driver.stack)
    }

    @Test
    fun historicalResultBackReturnsToHistory() {
        val driver = StackNavigationDriver(mutableListOf(HomeRoute))
        val actions = NavigationActions(driver)

        actions.openTopLevel(TopLevelDestination.PROFILE)
        actions.openHistory()
        actions.openResult("historical-result-id")
        actions.exitResult()

        assertEquals(listOf(HomeRoute, ProfileRoute, HistoryRoute), driver.stack)
    }

    @Test
    fun continuingHistoricalResultStartsMeasurementAboveHistoryAndReturnsThere() {
        val driver = StackNavigationDriver(mutableListOf(HomeRoute))
        val actions = NavigationActions(driver)

        actions.openTopLevel(TopLevelDestination.PROFILE)
        actions.openHistory()
        actions.openResult("historical-result-id")
        actions.continueMeasurementFromResult(TopLevelDestination.PROFILE)

        assertEquals(
            listOf(
                HomeRoute,
                ProfileRoute,
                HistoryRoute,
                MeasurementGraph(TopLevelDestination.PROFILE),
                ImageSelectRoute,
            ),
            driver.stack,
        )

        actions.openResult("continued-result-id")
        actions.exitResult()

        assertEquals(listOf(HomeRoute, ProfileRoute, HistoryRoute), driver.stack)
    }

    private class StackNavigationDriver(
        val stack: MutableList<NavigationTarget>,
    ) : NavigationDriver {
        override fun navigate(target: NavigationTarget, launchSingleTop: Boolean) {
            if (!launchSingleTop || stack.lastOrNull() != target) stack += target
            if (target is MeasurementGraph) stack += ImageSelectRoute
            if (target == CalibrationGraph) stack += CalibrationSelectRoute
        }

        override fun popOne(): Boolean = if (stack.size > 1) {
            stack.removeLast()
            true
        } else {
            false
        }

        override fun popMeasurement(): Boolean = popGraph { it is MeasurementGraph }

        override fun popToImageSelect(): Boolean = popTo { it == ImageSelectRoute }

        override fun popCalibration(): Boolean = popGraph { it == CalibrationGraph }

        override fun popToHome() {
            popTo { it == HomeRoute }
        }

        override fun containsMeasurement(): Boolean = stack.any { it is MeasurementGraph }

        private fun popGraph(predicate: (NavigationTarget) -> Boolean): Boolean {
            val graphIndex = stack.indexOfLast(predicate)
            if (graphIndex < 0) return false
            while (stack.size > graphIndex) stack.removeLast()
            return true
        }

        private fun popTo(predicate: (NavigationTarget) -> Boolean): Boolean {
            val targetIndex = stack.indexOfLast(predicate)
            if (targetIndex < 0) return false
            while (stack.lastIndex > targetIndex) stack.removeLast()
            return true
        }
    }
}
