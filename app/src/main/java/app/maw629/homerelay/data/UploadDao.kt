package app.maw629.homerelay.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: UploadItem)

    @Query("SELECT * FROM upload_items ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<UploadItem>>

    @Query("SELECT * FROM upload_items WHERE id = :id")
    suspend fun get(id: String): UploadItem?

    @Update
    suspend fun update(item: UploadItem)

    @Query("DELETE FROM upload_items WHERE id = :id")
    suspend fun delete(id: String)
}
