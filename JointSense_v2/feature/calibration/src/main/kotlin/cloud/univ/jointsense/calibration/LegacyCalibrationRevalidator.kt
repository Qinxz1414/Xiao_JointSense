package cloud.univ.jointsense.calibration

import cloud.univ.jointsense.analysis.calibration.CalibrationInput
import cloud.univ.jointsense.analysis.calibration.CalibrationValidation
import cloud.univ.jointsense.analysis.calibration.CalibrationValidator
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.flow.first

class LegacyCalibrationRevalidator(
    private val repository: CalibrationRepository,
    private val validate: (List<CalibrationInput>) -> CalibrationValidation =
        CalibrationValidator()::validate,
) {
    suspend fun revalidateNeedsReview() {
        val snapshot = repository.observeCalibrations().first()
        snapshot.filter { it.status == CalibrationStatus.NEEDS_REVIEW }.forEach { calibration ->
            val validation = validate(
                calibration.knots.sortedBy(CalibrationKnot::position).map { knot ->
                    CalibrationInput(
                        wellIndex = knot.position,
                        concentration = knot.concentration,
                        rawSignal = knot.rawSignal,
                    )
                },
            )
            if (validation is CalibrationValidation.Valid) {
                repository.save(
                    calibration.copy(
                        status = CalibrationStatus.ACTIVE,
                        knots = validation.knots.map { knot ->
                            CalibrationKnot(
                                position = knot.wellIndex,
                                concentration = knot.concentration,
                                rawSignal = knot.rawSignal,
                                netSignal = knot.netSignal,
                                fittedSignal = knot.fittedSignal,
                                isBlank = knot.concentration == 0f,
                            )
                        },
                    ),
                )
            }
        }
    }
}
