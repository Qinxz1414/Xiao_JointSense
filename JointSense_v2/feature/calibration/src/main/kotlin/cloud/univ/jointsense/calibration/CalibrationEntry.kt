package cloud.univ.jointsense.calibration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cloud.univ.jointsense.domain.repository.CalibrationRepository

@Composable
fun CalibrationRouteScreen(
    repository: CalibrationRepository,
    onExit: () -> Unit,
) {
    val controller = remember(repository) { BaselineCalibrationController(repository) }
    CalibrationFlowScreen(controller = controller, onExit = onExit)
}
