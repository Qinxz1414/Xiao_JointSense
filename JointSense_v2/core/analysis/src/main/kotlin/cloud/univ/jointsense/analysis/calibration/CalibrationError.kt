package cloud.univ.jointsense.analysis.calibration

enum class CalibrationError {
    WrongReadingCount,
    InvalidConcentration,
    MissingBlank,
    MultipleBlanks,
    DuplicateNonBlankConcentration,
    NonFiniteSignal,
    DynamicRangeTooLow,
    NonMonotonicBeyondTolerance,
}

sealed interface CalibrationValidation {
    data class Valid(val knots: List<CalibrationKnot>) : CalibrationValidation
    data class Invalid(val errors: List<CalibrationError>) : CalibrationValidation
}
