package app.maw629.homerelay.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(entities = [UploadItem::class], version = 1, exportSchema = true)
@TypeConverters(UploadTypeConverters::class)
abstract class HomeRelayDatabase : RoomDatabase() {
    abstract fun uploadDao(): UploadDao
}

class UploadTypeConverters {
    @TypeConverter
    fun uploadStateToString(value: UploadState): String = value.name

    @TypeConverter
    fun stringToUploadState(value: String): UploadState = UploadState.valueOf(value)

    @TypeConverter
    fun uploadErrorCodeToString(value: UploadErrorCode): String = value.name

    @TypeConverter
    fun stringToUploadErrorCode(value: String): UploadErrorCode = UploadErrorCode.valueOf(value)
}
