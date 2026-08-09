package cloud.univ.jointsense.analysis

import cloud.univ.jointsense.domain.model.RangeStatus

class StandardCurve(knots: List<CurveKnot>) {
    private val knots = knots.sortedBy(CurveKnot::signal).also {
        require(it.size >= 2) { "A standard curve needs at least two knots." }
        require(it.all { knot -> knot.concentration.isFinite() && knot.signal.isFinite() }) {
            "Curve knots must be finite."
        }
        require(it.zipWithNext().all { (a, b) ->
            b.signal >= a.signal && b.concentration > a.concentration
        }) { "Curve concentrations must increase as signals do not decrease." }
    }

    fun quantify(signal: Float): QuantificationResult {
        require(signal.isFinite()) { "Signal must be finite." }

        if (signal < knots.first().signal) {
            return QuantificationResult(0f, RangeStatus.BELOW_RANGE)
        }
        if (signal > knots.last().signal) {
            return QuantificationResult(knots.last().concentration, RangeStatus.ABOVE_RANGE)
        }

        val exactMatch = knots.indexOfFirst { it.signal == signal }
        if (exactMatch >= 0) {
            val highestPlateauIndex = knots.indexOfLast { it.signal == signal }
            return QuantificationResult(
                concentration = knots[highestPlateauIndex].concentration,
                rangeStatus = RangeStatus.IN_RANGE,
            )
        }

        val upper = knots.indexOfFirst { signal < it.signal }
        val lower = upper - 1
        val a = knots[lower]
        val b = knots[upper]
        val concentration = a.concentration + (b.concentration - a.concentration) *
            (signal - a.signal) / (b.signal - a.signal)
        return QuantificationResult(concentration, RangeStatus.IN_RANGE)
    }
}
