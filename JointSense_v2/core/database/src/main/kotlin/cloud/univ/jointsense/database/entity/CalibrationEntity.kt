package cloud.univ.jointsense.database.entity

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.InflammationFactor

@Entity(tableName = "calibration")
data class CalibrationEntity(
    @PrimaryKey val factor: InflammationFactor,
    val createdAt: Long,
    val version: Int,
    val status: CalibrationStatus,
    val kitName: String?,
    val kitLot: String?,
    @ColumnInfo(defaultValue = "'LEGACY_MEAN_BR'")
    val signalMethod: ColorSignalMethod = ColorSignalMethod.PIXEL_BR_P90_V1,
)

data class CalibrationWithKnots(
    @Embedded val calibration: CalibrationEntity,
    @Relation(
        parentColumn = "factor",
        entityColumn = "factor",
    )
    val knots: List<CalibrationKnotEntity>,
)

fun CalibrationWithKnots.toDomain(): Calibration = Calibration(
    factor = calibration.factor,
    createdAt = calibration.createdAt,
    version = calibration.version,
    status = calibration.status,
    kitName = calibration.kitName,
    kitLot = calibration.kitLot,
    signalMethod = calibration.signalMethod,
    knots = knots.sortedBy(CalibrationKnotEntity::position).map(CalibrationKnotEntity::toDomain),
)

fun Calibration.toEntity(): CalibrationEntity = CalibrationEntity(
    factor = factor,
    createdAt = createdAt,
    version = version,
    status = status,
    kitName = kitName,
    kitLot = kitLot,
    signalMethod = signalMethod,
)
