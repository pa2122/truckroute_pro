package com.example.truckroutepro

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object TruckProfileManager {
    private const val PREFS_NAME = "truck_profiles_prefs_v2"
    private const val KEY_PROFILES_JSON = "profiles_json"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"

    val defaultProfiles = listOf(
        TruckProfile(
            id = "default_semi",
            profileName = "Standard 53' Semi",
            heightFeet = 13,
            heightInches = 6,
            weightLbs = 80000.0,
            widthInches = 102.0,
            lengthFeet = 53.0,
            trailerType = "53 ft Dry Van / Reefer",
            axleCount = 5,
            maxSpeedMph = 65,
            isHazmat = false
        ),
        TruckProfile(
            id = "default_flatbed",
            profileName = "Flatbed / Stepdeck",
            heightFeet = 13,
            heightInches = 6,
            weightLbs = 80000.0,
            widthInches = 102.0,
            lengthFeet = 53.0,
            trailerType = "53 ft Flatbed / Stepdeck",
            axleCount = 5,
            maxSpeedMph = 65,
            isHazmat = false
        ),
        TruckProfile(
            id = "default_heavy_haul",
            profileName = "Heavy Haul 105k lbs",
            heightFeet = 14,
            heightInches = 0,
            weightLbs = 105000.0,
            widthInches = 108.0,
            lengthFeet = 53.0,
            trailerType = "Tanker / Container / Specialty",
            axleCount = 7,
            maxSpeedMph = 62,
            isHazmat = false
        )
    )

    fun loadProfiles(context: Context): List<TruckProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PROFILES_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            saveProfiles(context, defaultProfiles)
            return defaultProfiles
        }

        return try {
            val list = mutableListOf<TruckProfile>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TruckProfile(
                        id = obj.optString("id", "profile_$i"),
                        profileName = obj.optString("profileName", "Truck Profile #${i + 1}"),
                        heightFeet = obj.optInt("heightFeet", 13),
                        heightInches = obj.optInt("heightInches", 6),
                        weightLbs = obj.optDouble("weightLbs", 80000.0),
                        widthInches = obj.optDouble("widthInches", 102.0),
                        lengthFeet = obj.optDouble("lengthFeet", 53.0),
                        trailerType = obj.optString("trailerType", "53 ft Dry Van / Reefer"),
                        axleCount = obj.optInt("axleCount", 5),
                        maxSpeedMph = obj.optInt("maxSpeedMph", 65),
                        isHazmat = obj.optBoolean("isHazmat", false)
                    )
                )
            }
            if (list.isEmpty()) defaultProfiles else list
        } catch (_: Exception) {
            defaultProfiles
        }
    }

    fun saveProfiles(context: Context, profiles: List<TruckProfile>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("profileName", p.profileName)
                put("heightFeet", p.heightFeet)
                put("heightInches", p.heightInches)
                put("weightLbs", p.weightLbs)
                put("widthInches", p.widthInches)
                put("lengthFeet", p.lengthFeet)
                put("trailerType", p.trailerType)
                put("axleCount", p.axleCount)
                put("maxSpeedMph", p.maxSpeedMph)
                put("isHazmat", p.isHazmat)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_PROFILES_JSON, array.toString()).apply()
    }

    fun getActiveProfileId(context: Context, defaultId: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, defaultId) ?: defaultId
    }

    fun setActiveProfileId(context: Context, profileId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply()
    }
}
