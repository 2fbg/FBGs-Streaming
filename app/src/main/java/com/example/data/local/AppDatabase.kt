package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.database.ManualPlaylistDao
import com.example.data.database.PlaylistItemDao
import com.example.data.database.PlaylistItemConverters
import com.example.data.model.ManualPlaylist
import com.example.data.model.PlaylistItem
import com.example.data.model.ServerProfile

/**
 * Banco de dados principal do aplicativo MK21 Player usando Room.
 */
@Database(
    entities = [
        ServerProfile::class,
        PlaylistItem::class,
        ManualPlaylist::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(PlaylistItemConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun serverDao(): ServerDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun manualPlaylistDao(): ManualPlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE playlist_items ADD COLUMN alternateUrls TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mk21_player_db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
