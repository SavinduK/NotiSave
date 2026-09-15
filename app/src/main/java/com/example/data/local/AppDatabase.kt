package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.local.dao.ClipboardDao
import com.example.data.local.dao.NotificationDao
import com.example.data.local.entity.ClipboardEntity
import com.example.data.local.entity.ClipboardType
import com.example.data.local.entity.NotificationEntity

class Converters {
    @TypeConverter
    fun fromClipboardType(value: ClipboardType): String = value.name

    @TypeConverter
    fun toClipboardType(value: String): ClipboardType = try {
        ClipboardType.valueOf(value)
    } catch (e: Exception) {
        ClipboardType.TEXT
    }
}

@Database(
    entities = [NotificationEntity::class, ClipboardEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun clipboardDao(): ClipboardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notify_clip_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
