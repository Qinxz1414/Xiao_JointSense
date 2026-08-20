package cloud.univ.jointsense.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import cloud.univ.jointsense.database.dao.AppMetadataDao
import cloud.univ.jointsense.database.dao.CalibrationDao
import cloud.univ.jointsense.database.dao.TestSessionDao
import cloud.univ.jointsense.database.entity.AppMetadataEntity
import cloud.univ.jointsense.database.entity.CalibrationEntity
import cloud.univ.jointsense.database.entity.CalibrationKnotEntity
import cloud.univ.jointsense.database.entity.TestResultEntity
import cloud.univ.jointsense.database.entity.TestSessionEntity
import cloud.univ.jointsense.database.entity.MeasurementBatchEntity
import cloud.univ.jointsense.domain.model.CalibrationStatus
import cloud.univ.jointsense.domain.model.ColorSignalMethod
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus

@Database(
    entities = [
        TestSessionEntity::class,
        TestResultEntity::class,
        CalibrationEntity::class,
        CalibrationKnotEntity::class,
        AppMetadataEntity::class,
        MeasurementBatchEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class JointSenseDatabase : RoomDatabase() {
    abstract fun testSessionDao(): TestSessionDao
    abstract fun calibrationDao(): CalibrationDao
    abstract fun metadataDao(): AppMetadataDao
}

object DatabaseConverters {
    @TypeConverter
    fun dataSourceToString(value: DataSource): String = value.name

    @TypeConverter
    fun stringToDataSource(value: String): DataSource = DataSource.valueOf(value)

    @TypeConverter
    fun factorToString(value: InflammationFactor): String = value.name

    @TypeConverter
    fun stringToFactor(value: String): InflammationFactor = InflammationFactor.valueOf(value)

    @TypeConverter
    fun rangeStatusToString(value: RangeStatus): String = value.name

    @TypeConverter
    fun stringToRangeStatus(value: String): RangeStatus = RangeStatus.valueOf(value)

    @TypeConverter
    fun calibrationStatusToString(value: CalibrationStatus): String = value.name

    @TypeConverter
    fun stringToCalibrationStatus(value: String): CalibrationStatus = CalibrationStatus.valueOf(value)

    @TypeConverter
    fun colorSignalMethodToString(value: ColorSignalMethod): String = value.name

    @TypeConverter
    fun stringToColorSignalMethod(value: String): ColorSignalMethod = ColorSignalMethod.valueOf(value)
}
