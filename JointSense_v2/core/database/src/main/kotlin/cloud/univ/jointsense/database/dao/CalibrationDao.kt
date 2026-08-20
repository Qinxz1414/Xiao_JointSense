package cloud.univ.jointsense.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cloud.univ.jointsense.database.entity.CalibrationEntity
import cloud.univ.jointsense.database.entity.CalibrationKnotEntity
import cloud.univ.jointsense.database.entity.CalibrationWithKnots
import cloud.univ.jointsense.domain.model.InflammationFactor
import kotlinx.coroutines.flow.Flow

@Dao
interface CalibrationDao {
    @Transaction
    @Query("SELECT * FROM calibration ORDER BY factor ASC")
    fun calibrations(): Flow<List<CalibrationWithKnots>>

    @Transaction
    @Query("SELECT * FROM calibration WHERE factor = :factor")
    fun calibration(factor: InflammationFactor): Flow<CalibrationWithKnots?>

    @Query("SELECT * FROM calibration_knot WHERE factor = :factor ORDER BY position ASC")
    fun knotsForFactor(factor: InflammationFactor): Flow<List<CalibrationKnotEntity>>

    @Upsert
    suspend fun insertCalibration(calibration: CalibrationEntity)

    @Insert
    suspend fun insertKnots(knots: List<CalibrationKnotEntity>)

    @Query("DELETE FROM calibration_knot WHERE factor = :factor")
    suspend fun deleteKnots(factor: InflammationFactor)

    @Transaction
    suspend fun replaceCalibration(
        calibration: CalibrationEntity,
        knots: List<CalibrationKnotEntity>,
    ) {
        insertCalibration(calibration)
        deleteKnots(calibration.factor)
        insertKnots(knots)
    }

    @Query("DELETE FROM calibration WHERE factor = :factor")
    suspend fun deleteCalibration(factor: InflammationFactor)

    @Query("DELETE FROM calibration")
    suspend fun deleteAllCalibrations()
}
