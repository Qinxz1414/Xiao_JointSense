package cloud.univ.jointsense.calibration

import cloud.univ.jointsense.analysis.calibration.CalibrationInput
import cloud.univ.jointsense.analysis.calibration.CalibrationValidation
import cloud.univ.jointsense.analysis.calibration.CalibrationValidator
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class LegacyRevalidationStage { VALIDATE, SAVE }

data class LegacyRevalidationFailure(
    val factor: InflammationFactor,
    val stage: LegacyRevalidationStage,
    val message: String,
)

data class LegacyRevalidationSummary(
    val attempted: Int = 0,
    val promoted: Int = 0,
    val retained: Int = 0,
    val failures: List<LegacyRevalidationFailure> = emptyList(),
)

class LegacyCalibrationRevalidator(
    private val repository: CalibrationRepository,
    private val validate: (List<CalibrationInput>) -> CalibrationValidation =
        CalibrationValidator()::validate,
) {
    private val mutex = Mutex()
    private val completedRecords = mutableSetOf<Calibration>()

    suspend fun revalidateNeedsReview(): LegacyRevalidationSummary = mutex.withLock {
        val failures = mutableListOf<LegacyRevalidationFailure>()
        var attempted = 0
        var promoted = 0
        var retained = 0
        repository.observeCalibrations().first()
            .filter { it.status == CalibrationStatus.NEEDS_REVIEW }
            .forEach { calibration ->
                if (calibration in completedRecords) return@forEach
                attempted += 1
                val validation = try {
                    validate(calibration.toValidationInputs())
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += error.toFailure(calibration.factor, LegacyRevalidationStage.VALIDATE)
                    return@forEach
                }
                if (validation !is CalibrationValidation.Valid) {
                    completedRecords += calibration
                    retained += 1
                    return@forEach
                }
                try {
                    repository.save(calibration.promotedWith(validation))
                    completedRecords += calibration
                    promoted += 1
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failures += error.toFailure(calibration.factor, LegacyRevalidationStage.SAVE)
                }
            }
        LegacyRevalidationSummary(attempted, promoted, retained, failures)
    }

}

private fun Calibration.toValidationInputs(): List<CalibrationInput> =
    knots.sortedBy(CalibrationKnot::position).map { knot ->
        CalibrationInput(
            wellIndex = knot.position,
            concentration = knot.concentration,
            rawSignal = knot.rawSignal,
        )
    }

private fun Calibration.promotedWith(validation: CalibrationValidation.Valid): Calibration = copy(
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
)

private fun Exception.toFailure(
    factor: InflammationFactor,
    stage: LegacyRevalidationStage,
) = LegacyRevalidationFailure(
    factor = factor,
    stage = stage,
    message = message ?: javaClass.simpleName,
)
