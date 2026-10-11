package com.example.truckroutepro

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class TruckWeatherInfo(
    val location: LatLng,
    val tempFahrenheit: Int = 70,
    val windSpeedMph: Int = 10,
    val windGustsMph: Int = 15,
    val windDirectionDegrees: Int = 0,
    val weatherDescription: String = "Clear",
    val weatherCode: Int = 0,
    val isHighWindWarning: Boolean = false,
    val isSevereWindRolloverRisk: Boolean = false
) {
    val windCategory: String
        get() = when {
            windGustsMph >= 45 -> "SEVERE_HIGH_WIND"
            windGustsMph >= 30 -> "MODERATE_WIND"
            else -> "LIGHT_WIND"
        }

    val formattedWindText: String
        get() = if (windGustsMph > windSpeedMph) {
            "${windSpeedMph} MPH (Gusts ${windGustsMph} MPH)"
        } else {
            "${windSpeedMph} MPH"
        }
}

object TruckWeatherService {

    private fun weatherCodeToDescription(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1, 2, 3 -> "Partly Cloudy"
            45, 48 -> "Foggy / Low Visibility"
            51, 53, 55, 56, 57 -> "Drizzle / Light Rain"
            61, 63, 65, 66, 67 -> "Rain / Wet Road"
            71, 73, 75, 77 -> "Snow / Icy Road"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Thunderstorm / High Wind"
            else -> "Overcast"
        }
    }

    suspend fun fetchWeatherForLocation(location: LatLng): TruckWeatherInfo? = withContext(Dispatchers.IO) {
        try {
            val lat = String.format(Locale.US, "%.4f", location.latitude)
            val lng = String.format(Locale.US, "%.4f", location.longitude)
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m&temperature_unit=fahrenheit&wind_speed_unit=mph"

            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            if (conn.responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonText)
                val current = jsonObj.optJSONObject("current")

                if (current != null) {
                    val tempF = current.optDouble("temperature_2m", 70.0).toInt()
                    val windMph = current.optDouble("wind_speed_10m", 10.0).toInt()
                    val gustsMph = current.optDouble("wind_gusts_10m", windMph.toDouble()).toInt()
                    val windDir = current.optInt("wind_direction_10m", 0)
                    val wCode = current.optInt("weather_code", 0)
                    val desc = weatherCodeToDescription(wCode)

                    return@withContext TruckWeatherInfo(
                        location = location,
                        tempFahrenheit = tempF,
                        windSpeedMph = windMph,
                        windGustsMph = gustsMph,
                        windDirectionDegrees = windDir,
                        weatherDescription = desc,
                        weatherCode = wCode,
                        isHighWindWarning = gustsMph >= 30,
                        isSevereWindRolloverRisk = gustsMph >= 45
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TruckWeatherService", "Error fetching weather: ${e.message}")
        }
        return@withContext null
    }
}
