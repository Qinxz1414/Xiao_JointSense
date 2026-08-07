package cloud.univ.jointsense.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import cloud.univ.jointsense.domain.model.CalibrationKnot
import cloud.univ.jointsense.domain.model.InflammationFactor

@Entity(
    tableName = "calibration_knot",
    primaryKeys = ["factor", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CalibrationEntity::class,
            parentColumns = ["factor"],
            childColumns = ["factor"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("factor")],
)
data class CalibrationKnotEntity(
    val factor: InflammationFactor,
    val position: Int,
    val concentration: Float,
    val rawSignal: Float,
    val netSignal: Float,
    val fittedSignal: Float,
    val isBlank: Boolean,
)

fun CalibrationKnotEntity.toDomain(): CalibrationKnot = CalibrationKnot(
    position = position,
    concentration = concentration,
    rawSignal = rawSignal,
    netSignal = netSignal,
    fittedSignal = fittedSignal,
    isBlank = isBlank,
)

fun CalibrationKnot.toEntity(factor: InflammationFactor): CalibrationKnotEntity = CalibrationKnotEntity(
    factor = factor,
    position = position,
    concentration = concentration,
    rawSignal = rawSignal,
    netSignal = netSignal,
    fittedSignal = fittedSignal,
    isBlank = isBlank,
)
