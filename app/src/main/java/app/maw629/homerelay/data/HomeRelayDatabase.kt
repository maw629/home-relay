package app.maw629.homerelay.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UploadItem::class], version = 2, exportSchema = true)
@TypeConverters(UploadTypeConverters::class)
abstract class HomeRelayDatabase : RoomDatabase() {
    abstract fun uploadDao(): UploadDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }
    }
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
