package com.example.data.model

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representa um item da playlist (canal ao vivo, filme ou episódio de série).
 * Inclui índices otimizados para busca rápida e categorização.
 */
@Immutable
@Entity(
    tableName = "playlist_items",
    indices = [
        Index(value = ["playlistSource", "contentType"]),
        Index(value = ["playlistSource", "category", "contentType"]),
        Index(value = ["playlistSource", "isFavorite"])
    ]
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val category: String = "Geral",
    val contentType: String = "LIVE", // LIVE, MOVIE, SERIES
    val isAdult: Boolean = false,
    val playlistSource: String = "Principal",
    val lastWatchedTime: Long = 0,
    val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "'[]'")
    val alternateUrls: List<String> = emptyList()
)
