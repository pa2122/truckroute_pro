package com.example.truckroutepro

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlacePrediction(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullDescription: String
)

data class PlaceDetailsResult(
    val placeId: String,
    val name: String,
    val formattedAddress: String,
    val location: LatLng
)

object TruckPlacesService {

    suspend fun getPlacePredictions(
        apiKey: String,
        query: String
    ): List<PlacePrediction> = withContext(Dispatchers.IO) {
        if (query.length < 2 || apiKey.isBlank()) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlStr = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$encodedQuery&components=country:us&key=$apiKey"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonText)
                val status = jsonObj.optString("status")

                if (status == "OK") {
                    val predictions = jsonObj.getJSONArray("predictions")
                    val list = mutableListOf<PlacePrediction>()

                    for (i in 0 until predictions.length()) {
                        val p = predictions.getJSONObject(i)
                        val placeId = p.getString("place_id")
                        val description = p.optString("description", "")
                        val formatting = p.optJSONObject("structured_formatting")
                        val primary = formatting?.optString("main_text", description) ?: description
                        val secondary = formatting?.optString("secondary_text", "") ?: ""

                        list.add(
                            PlacePrediction(
                                placeId = placeId,
                                primaryText = primary,
                                secondaryText = secondary,
                                fullDescription = description
                            )
                        )
                    }
                    return@withContext list
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        emptyList()
    }

    suspend fun getPlaceDetails(
        apiKey: String,
        placeId: String
    ): PlaceDetailsResult? = withContext(Dispatchers.IO) {
        if (placeId.isBlank() || apiKey.isBlank()) return@withContext null

        try {
            val urlStr = "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&fields=name,formatted_address,geometry&key=$apiKey"
            val conn = URL(urlStr).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonText)
                val status = jsonObj.optString("status")

                if (status == "OK" && jsonObj.has("result")) {
                    val res = jsonObj.getJSONObject("result")
                    val name = res.optString("name", "")
                    val address = res.optString("formatted_address", name)
                    val locObj = res.getJSONObject("geometry").getJSONObject("location")
                    val latLng = LatLng(locObj.getDouble("lat"), locObj.getDouble("lng"))

                    return@withContext PlaceDetailsResult(
                        placeId = placeId,
                        name = name,
                        formattedAddress = address,
                        location = latLng
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }
}
