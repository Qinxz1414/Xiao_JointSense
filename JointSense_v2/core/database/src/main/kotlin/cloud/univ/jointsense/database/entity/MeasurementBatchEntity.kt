package cloud.univ.jointsense.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "measurement_batch",
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
data class MeasurementBatchEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val draftId: String,
    val measuredAt: Long,
)
