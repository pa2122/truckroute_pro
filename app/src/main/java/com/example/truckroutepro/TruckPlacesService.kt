package com.example.truckroutepro

import android.util.Log
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
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        // 1. Try Google Places Autocomplete API
        if (apiKey.isNotBlank()) {
            try {
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val urlStr = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$encodedQuery&components=country:us&key=$apiKey"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(jsonText)
                    val status = jsonObj.optString("status")
                    Log.d("TruckPlacesService", "Autocomplete status for '$cleanQuery': $status")

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
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (e: Exception) {
                Log.e("TruckPlacesService", "Error in Autocomplete API", e)
            }
        }

        // 2. Fallback: Try Places API (New) searchText
        if (apiKey.isNotBlank()) {
            try {
                val url = URL("https://places.googleapis.com/v1/places:searchText")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("X-Goog-Api-Key", apiKey)
                conn.setRequestProperty("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location")
                conn.doOutput = true
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                val requestBody = JSONObject().apply {
                    put("textQuery", cleanQuery)
                }

                conn.outputStream.use { os ->
                    val input = requestBody.toString().toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(jsonText)
                    if (jsonObj.has("places")) {
                        val results = jsonObj.getJSONArray("places")
                        val list = mutableListOf<PlacePrediction>()

                        for (i in 0 until results.length()) {
                            val r = results.getJSONObject(i)
                            val id = r.optString("id", "")
                            val nameObj = r.optJSONObject("displayName")
                            val primary = nameObj?.optString("text", cleanQuery) ?: cleanQuery
                            val address = r.optString("formattedAddress", primary)

                            list.add(
                                PlacePrediction(
                                    placeId = id,
                                    primaryText = primary,
                                    secondaryText = address,
                                    fullDescription = address
                                )
                            )
                        }
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (e: Exception) {
                Log.e("TruckPlacesService", "Error in Places searchText API", e)
            }
        }

        // 3. Fallback: Try Google Geocoding API
        if (apiKey.isNotBlank()) {
            try {
                val encodedAddr = URLEncoder.encode(cleanQuery, "UTF-8")
                val urlStr = "https://maps.googleapis.com/maps/api/geocode/json?address=$encodedAddr&key=$apiKey"
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                if (conn.responseCode == 200) {
                    val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonObj = JSONObject(jsonText)
                    val status = jsonObj.optString("status")

                    if (status == "OK") {
                        val results = jsonObj.getJSONArray("results")
                        val list = mutableListOf<PlacePrediction>()

                        for (i in 0 until results.length()) {
                            val r = results.getJSONObject(i)
                            val address = r.optString("formatted_address", cleanQuery)
                            val placeId = r.optString("place_id", "")

                            list.add(
                                PlacePrediction(
                                    placeId = placeId,
                                    primaryText = address,
                                    secondaryText = "Geocoded Location",
                                    fullDescription = address
                                )
                            )
                        }
                        if (list.isNotEmpty()) return@withContext list
                    }
                }
            } catch (e: Exception) {
                Log.e("TruckPlacesService", "Error in Geocoding API fallback", e)
            }
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
