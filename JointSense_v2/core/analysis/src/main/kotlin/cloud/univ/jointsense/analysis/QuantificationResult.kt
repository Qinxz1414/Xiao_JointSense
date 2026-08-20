package cloud.univ.jointsense.analysis

import cloud.univ.jointsense.domain.model.RangeStatus

data class QuantificationResult(
    val concentration: Float,
    val rangeStatus: RangeStatus,
)
