package app.maw629.homerelay.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class UploadState {
    QUEUED,
    UPLOADING,
    COMPLETED,
    NEEDS_ATTENTION,
    CANCELLED
}

enum class UploadErrorCode {
    NONE,
    SOURCE_UNREADABLE,
    STAGING_STORAGE_FULL,
    DESTINATION_ACCESS_LOST,
    DESTINATION_QUOTA,
    DESTINATION_POLICY,
    WRITE_OUTCOME_UNKNOWN
}

@Entity(tableName = "upload_items")
data class UploadItem(
    @PrimaryKey val id: String,
    val originalName: String,
    val mimeType: String,
    val outputName: String,
    val stagedPath: String,
    val byteSize: Long,
    val createdAtMillis: Long,
    val retryCount: Int,
    val state: UploadState,
    val errorCode: UploadErrorCode
)
