package com.example.data.database

import androidx.room.TypeConverter
import org.json.JSONArray

class PlaylistItemConverters {
    @TypeConverter
    fun alternateUrlsToJson(urls: List<String>): String = JSONArray(urls).toString()

    @TypeConverter
    fun jsonToAlternateUrls(value: String): List<String> {
        val json = JSONArray(value)
        return List(json.length()) { index -> json.getString(index) }
            .filter(String::isNotBlank)
    }
}
