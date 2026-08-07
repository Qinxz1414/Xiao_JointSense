package cloud.univ.jointsense.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import cloud.univ.jointsense.domain.model.InflammationFactor
import cloud.univ.jointsense.domain.model.RangeStatus
import cloud.univ.jointsense.domain.model.RgbFeatures
import cloud.univ.jointsense.domain.model.TestResult

@Entity(
    tableName = "test_result",
    foreignKeys = [
        ForeignKey(
            entity = TestSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["draftId"], unique = true),
    ],
)
data class TestResultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val draftId: String?,
    val factor: InflammationFactor,
    val concentration: Float,
    val rangeStatus: RangeStatus,
    val timestamp: Long,
    val rMean: Float,
    val gMean: Float,
    val bMean: Float,
    val rStd: Float,
    val gStd: Float,
    val bStd: Float,
)

fun TestResultEntity.toDomain(): TestResult = TestResult(
    id = id,
    sessionId = sessionId,
    draftId = draftId,
    factor = factor,
    concentration = concentration,
    rangeStatus = rangeStatus,
    features = RgbFeatures(
        rMean = rMean,
        gMean = gMean,
        bMean = bMean,
        rStd = rStd,
        gStd = gStd,
        bStd = bStd,
    ),
    timestamp = timestamp,
)

fun TestResult.toEntity(): TestResultEntity = TestResultEntity(
    id = id,
    sessionId = sessionId,
    draftId = draftId,
    factor = factor,
    concentration = concentration,
    rangeStatus = rangeStatus,
    timestamp = timestamp,
    rMean = features.rMean,
    gMean = features.gMean,
    bMean = features.bMean,
    rStd = features.rStd,
    gStd = features.gStd,
    bStd = features.bStd,
)
