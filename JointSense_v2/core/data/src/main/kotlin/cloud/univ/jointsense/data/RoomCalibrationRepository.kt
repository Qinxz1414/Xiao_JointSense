package cloud.univ.jointsense.data

import cloud.univ.jointsense.database.JointSenseDatabase
import cloud.univ.jointsense.database.entity.toDomain
import cloud.univ.jointsense.database.entity.toEntity
import cloud.univ.jointsense.domain.model.Calibration
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.repository.CalibrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCalibrationRepository(
    database: JointSenseDatabase,
) : CalibrationRepository {
    private val dao = database.calibrationDao()

    override fun observeCalibrations(): Flow<List<Calibration>> =
        dao.calibrations().map { calibrations -> calibrations.map { it.toDomain() } }

    override fun observeCalibration(factor: InflammationFactor): Flow<Calibration?> =
        dao.calibration(factor).map { it?.toDomain() }

    override suspend fun save(calibration: Calibration) {
        dao.replaceCalibration(
            calibration = calibration.toEntity(),
            knots = calibration.knots.map { it.toEntity(calibration.factor) },
        )
    }

    override suspend fun clearAll() {
        dao.deleteAllCalibrations()
    }
}
