package cloud.univ.jointsense.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import cloud.univ.jointsense.domain.model.DataSource
import cloud.univ.jointsense.domain.model.TestSession

@Entity(tableName = "test_session")
data class TestSessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val source: DataSource,
)

data class TestSessionWithResults(
    @Embedded val session: TestSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val results: List<TestResultEntity>,
)

fun TestSessionWithResults.toDomain(): TestSession = TestSession(
    id = session.id,
    name = session.name,
    createdAt = session.createdAt,
    source = session.source,
    results = results.map(TestResultEntity::toDomain),
)

fun TestSession.toEntity(): TestSessionEntity = TestSessionEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    source = source,
)
