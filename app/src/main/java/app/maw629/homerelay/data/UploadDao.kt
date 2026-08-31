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

    @Query("""
        UPDATE upload_items
        SET originalName = :originalName, outputName = :outputName, byteSize = :byteSize,
            state = 'QUEUED', errorCode = 'NONE'
        WHERE id = :id AND state = 'STAGING'
    """)
    suspend fun completeStaging(
        id: String,
        originalName: String,
        outputName: String,
        byteSize: Long
    ): Int

    @Query("""
        UPDATE upload_items SET state = 'NEEDS_ATTENTION', errorCode = :errorCode
        WHERE id = :id AND state = 'STAGING'
    """)
    suspend fun failStaging(id: String, errorCode: UploadErrorCode): Int

    @Query("SELECT * FROM upload_items WHERE state = 'STAGING' ORDER BY createdAtMillis ASC")
    suspend fun stagingItems(): List<UploadItem>

    @Update
    suspend fun update(item: UploadItem)

    @Query("""
        UPDATE upload_items SET state = 'UPLOADING', errorCode = 'NONE'
        WHERE id = :id AND state = 'QUEUED'
    """)
    suspend fun beginUpload(id: String): Int

    @Query("UPDATE upload_items SET state = 'QUEUED', errorCode = 'NONE' WHERE state = 'UPLOADING'")
    suspend fun requeueInterruptedUploads(): Int

    @Query("UPDATE upload_items SET state = 'QUEUED', errorCode = 'NONE' WHERE id = :id AND state = 'UPLOADING'")
    suspend fun requeueInterruptedUpload(id: String): Int

    @Query("SELECT id FROM upload_items WHERE state = 'QUEUED' ORDER BY createdAtMillis ASC")
    suspend fun queuedIds(): List<String>

    @Query("""
        UPDATE upload_items
        SET state = :state, errorCode = :errorCode, retryCount = :retryCount
        WHERE id = :id AND state = 'UPLOADING'
    """)
    suspend fun finishUpload(
        id: String,
        state: UploadState,
        errorCode: UploadErrorCode,
        retryCount: Int
    ): Int

    @Query("""
        UPDATE upload_items
        SET state = 'QUEUED', errorCode = 'NONE', retryCount = :retryCount, outputName = :outputName
        WHERE id = :id AND state = 'NEEDS_ATTENTION'
    """)
    suspend fun retry(id: String, outputName: String, retryCount: Int): Int

    @Query("""
        UPDATE upload_items SET state = 'CANCELLED'
        WHERE id = :id AND state IN ('QUEUED', 'UPLOADING', 'NEEDS_ATTENTION')
    """)
    suspend fun cancel(id: String): Int

    @Query("DELETE FROM upload_items WHERE id = :id")
    suspend fun delete(id: String)
}
