package com.example.data.database

import androidx.room.TypeConverter
import org.json.JSONArray

class PlaylistItemConverters {
    @TypeConverter
    fun alternateUrlsToJson(urls: List<String>?): String {
        if (urls.isNullOrEmpty()) return "[]"
        return JSONArray(urls).toString()
    }

    @TypeConverter
    fun jsonToAlternateUrls(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val json = JSONArray(value)
            List(json.length()) { index -> json.getString(index) }
                .filter(String::isNotBlank)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
