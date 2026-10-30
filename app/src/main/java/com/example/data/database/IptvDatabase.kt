package com.example.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ManualPlaylist
import com.example.data.model.PlaylistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistItemDao {
    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source ORDER BY name ASC")
    fun getAllItemsByPlaylist(source: String): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND logoUrl IS NOT NULL AND logoUrl != '' ORDER BY RANDOM() LIMIT 20")
    fun getRandomHighlights(source: String): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND contentType = :type ORDER BY name ASC")
    fun getItemsByType(source: String, type: String): Flow<List<PlaylistItem>>

    @Query("SELECT DISTINCT category FROM playlist_items WHERE playlistSource = :source AND contentType = :type ORDER BY category ASC")
    fun getCategoriesByType(source: String, type: String): Flow<List<String>>

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND category = :category AND contentType = :type ORDER BY name ASC")
    fun getItemsByCategoryAndType(source: String, category: String, type: String): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND name LIKE :query ORDER BY name ASC")
    fun searchItems(source: String, query: String): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(source: String): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND name = :name LIMIT 1")
    suspend fun findItemInPlaylistByName(source: String, name: String): PlaylistItem?

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND (name = :name OR name LIKE :nameLike) LIMIT 1")
    suspend fun findItemInPlaylistFuzzy(source: String, name: String, nameLike: String): PlaylistItem?

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistSource = :source")
    suspend fun getItemCountByPlaylist(source: String): Int

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND contentType = :type")
    suspend fun getItemsByPlaylistAndTypeSync(source: String, type: String): List<PlaylistItem>

    @Query("SELECT * FROM playlist_items WHERE isFavorite = 1")
    suspend fun getAllFavoriteItems(): List<PlaylistItem>

    @Query("SELECT * FROM playlist_items WHERE contentType = 'LIVE' AND (name LIKE '%PREMIERE%' OR name LIKE '%SPORTV%' OR name LIKE '%ESPN%' OR name LIKE '%GLOBO%' OR name LIKE '%CAZÉ%' OR name LIKE '%CAZE%' OR name LIKE '%TNT%' OR category LIKE '%ESPORTE%' OR category LIKE '%FUTEBOL%') LIMIT 60")
    suspend fun getLiveSportsChannelsGlobal(): List<PlaylistItem>

    @Query("SELECT * FROM playlist_items WHERE playlistSource = :source AND contentType = :type AND lastWatchedTime > 0 ORDER BY lastWatchedTime DESC LIMIT 15")
    fun getContinueWatching(source: String, type: String): Flow<List<PlaylistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistItem>)

    @Query("DELETE FROM playlist_items WHERE playlistSource = :source")
    suspend fun clearPlaylistItems(source: String)

    @Transaction
    suspend fun clearAndInsertPlaylistItems(source: String, items: List<PlaylistItem>) {
        clearPlaylistItems(source)
        // Chunk inserting in optimal batches (500 items) to prevent SQLite binder transaction limit
        items.chunked(500).forEach { chunk ->
            insertItems(chunk)
        }
    }

    @Transaction
    suspend fun insertChunkInTransaction(items: List<PlaylistItem>) {
        items.chunked(500).forEach { chunk ->
            insertItems(chunk)
        }
    }

    @Query("UPDATE playlist_items SET isFavorite = :favorite WHERE id = :itemId")
    suspend fun setFavorite(itemId: Long, favorite: Boolean)

    @Query("UPDATE playlist_items SET lastWatchedTime = :time WHERE id = :itemId")
    suspend fun updateLastWatched(itemId: Long, time: Long)

    @Query("UPDATE playlist_items SET lastWatchedTime = 0 WHERE playlistSource = :source AND contentType IN ('MOVIE', 'SERIES')")
    suspend fun clearMoviesAndSeriesHistory(source: String)

    @Query("UPDATE playlist_items SET lastWatchedTime = 0 WHERE playlistSource = :source AND contentType = 'MOVIE'")
    suspend fun clearMoviesHistory(source: String)

    @Query("UPDATE playlist_items SET lastWatchedTime = 0 WHERE playlistSource = :source AND contentType = 'SERIES'")
    suspend fun clearSeriesHistory(source: String)

    @Query("UPDATE playlist_items SET lastWatchedTime = 0 WHERE playlistSource = :source AND contentType = 'LIVE'")
    suspend fun clearLiveHistory(source: String)
}

@Dao
interface ManualPlaylistDao {
    @Query("SELECT * FROM manual_playlists ORDER BY name ASC")
    fun getAllManualPlaylists(): Flow<List<ManualPlaylist>>

    @Query("SELECT * FROM manual_playlists ORDER BY name ASC")
    suspend fun getAllManualPlaylistsList(): List<ManualPlaylist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertManualPlaylist(playlist: ManualPlaylist)

    @Query("DELETE FROM manual_playlists WHERE name = :name")
    suspend fun deleteManualPlaylist(name: String)
}

@Database(entities = [PlaylistItem::class, ManualPlaylist::class], version = 2, exportSchema = false)
@TypeConverters(PlaylistItemConverters::class)
abstract class IptvDatabase : RoomDatabase() {
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun manualPlaylistDao(): ManualPlaylistDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE playlist_items ADD COLUMN alternateUrls TEXT NOT NULL DEFAULT '[]'"
                )
            }
        }
    }
}
