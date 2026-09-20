package com.example.truckroutepro

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONObject

data class SearchHistoryItem(
    val title: String,
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
)

object SearchHistoryManager {
    private const val PREFS_NAME = "truck_search_history_prefs"
    private const val KEY_HISTORY_JSON = "recent_searches_json"
    private const val MAX_HISTORY_ITEMS = 10

    fun getRecentSearches(context: Context): List<SearchHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY_JSON, null) ?: return emptyList()

        return try {
            val list = mutableListOf<SearchHistoryItem>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SearchHistoryItem(
                        title = obj.optString("title", "Saved Location"),
                        formattedAddress = obj.optString("formattedAddress", ""),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSearchItem(context: Context, title: String, address: String, latLng: LatLng) {
        if (address.isBlank()) return
        val currentList = getRecentSearches(context).toMutableList()

        // Remove duplicate entry if address or title matches
        currentList.removeAll { 
            it.formattedAddress.equals(address, ignoreCase = true) || 
            (title.isNotBlank() && it.title.equals(title, ignoreCase = true))
        }

        currentList.add(
            0,
            SearchHistoryItem(
                title = title.ifBlank { address },
                formattedAddress = address,
                latitude = latLng.latitude,
                longitude = latLng.longitude,
                timestamp = System.currentTimeMillis()
            )
        )

        val trimmedList = currentList.take(MAX_HISTORY_ITEMS)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in trimmedList) {
            val obj = JSONObject().apply {
                put("title", item.title)
                put("formattedAddress", item.formattedAddress)
                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY_JSON, array.toString()).apply()
    }

    fun clearSearchHistory(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY_JSON).apply()
    }
}
