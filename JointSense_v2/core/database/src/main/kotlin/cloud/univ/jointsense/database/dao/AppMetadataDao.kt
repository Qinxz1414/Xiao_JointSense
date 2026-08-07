package cloud.univ.jointsense.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import cloud.univ.jointsense.database.entity.AppMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppMetadataDao {
    @Query("SELECT value FROM app_metadata WHERE `key` = :key")
    fun value(key: String): Flow<String?>

    @Upsert
    suspend fun put(metadata: AppMetadataEntity)
}
