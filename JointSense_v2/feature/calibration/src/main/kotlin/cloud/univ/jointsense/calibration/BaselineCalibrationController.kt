package cloud.univ.jointsense.calibration

import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository

/** Phase-1 persistence bridge; Phase 2 replaces its permissive scientific validation. */
class BaselineCalibrationController(
    private val repository: CalibrationRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun save(
        factor: InflammationFactor,
        concentrations: List<Float>,
        signals: List<Float>,
    ) {
        val blankIndex = concentrations.indexOfFirst { it == 0f }.coerceAtLeast(0)
        val blankSignal = signals.getOrElse(blankIndex) { 0f }
        repository.save(
            Calibration(
                factor = factor,
                createdAt = clock(),
                version = 1,
                status = CalibrationStatus.ACTIVE,
                kitName = null,
                kitLot = null,
                signalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
                knots = concentrations.mapIndexed { index, concentration ->
                    val rawSignal = signals.getOrElse(index) { 0f }
                    val netSignal = rawSignal - blankSignal
                    CalibrationKnot(
                        position = index,
                        concentration = concentration,
                        rawSignal = rawSignal,
                        netSignal = netSignal,
                        fittedSignal = netSignal,
                        isBlank = concentration == 0f,
                    )
                },
            ),
        )
    }

    suspend fun restoreFactory() {
        repository.clearAll()
    }
}
