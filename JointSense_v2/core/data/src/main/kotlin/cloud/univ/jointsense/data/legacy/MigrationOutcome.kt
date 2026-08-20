package cloud.univ.jointsense.data.legacy

sealed interface MigrationOutcome {
    data class Completed(
        val sessions: Int,
        val results: Int,
        val calibrations: Int,
    ) : MigrationOutcome

    data object AlreadyCompleted : MigrationOutcome
    data object SkippedByUser : MigrationOutcome
    data class Failed(val reason: String) : MigrationOutcome
}
