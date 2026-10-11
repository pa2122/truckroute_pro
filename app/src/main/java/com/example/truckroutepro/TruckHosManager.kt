package com.example.truckroutepro

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.util.Locale

enum class HosDutyStatus(val label: String, val shortCode: String) {
    DRIVING("Driving (On-Duty)", "DRV"),
    ON_DUTY("On-Duty (Not Driving)", "ON"),
    OFF_DUTY("Off-Duty", "OFF"),
    SLEEPER_BERTH("Sleeper Berth", "SB")
}

data class HosDriverState(
    val currentStatus: HosDutyStatus = HosDutyStatus.OFF_DUTY,
    val driveSecondsRemaining: Long = 11 * 3600L,
    val shiftSecondsRemaining: Long = 14 * 3600L,
    val breakSecondsRemaining: Long = 8 * 3600L,
    val cycleSecondsRemaining: Long = 70 * 3600L,
    val lastStatusChangeMs: Long = System.currentTimeMillis()
) {
    val formattedDriveRemaining: String
        get() = formatSecondsToHoursMins(driveSecondsRemaining)

    val formattedShiftRemaining: String
        get() = formatSecondsToHoursMins(shiftSecondsRemaining)

    val formattedBreakRemaining: String
        get() = formatSecondsToHoursMins(breakSecondsRemaining)

    val formattedCycleRemaining: String
        get() = formatSecondsToHoursMins(cycleSecondsRemaining)

    private fun formatSecondsToHoursMins(secs: Long): String {
        val positiveSecs = secs.coerceAtLeast(0L)
        val hours = positiveSecs / 3600
        val mins = (positiveSecs % 3600) / 60
        return "${hours}h ${mins}m"
    }
}

data class HosRouteRequirement(
    val requires30MinBreak: Boolean = false,
    val requires10HourReset: Boolean = false,
    val estimatedBreakMile: Double = 0.0,
    val estimatedResetMile: Double = 0.0,
    val breakRecommendedLocation: String = "",
    val resetRecommendedLocation: String = ""
)

object TruckHosManager {

    private const val PREFS_NAME = "truck_hos_clocks"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDriverState(context: Context): HosDriverState {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString("hos_state", null)

        if (jsonStr != null) {
            try {
                val obj = JSONObject(jsonStr)
                val statusStr = obj.optString("status", "OFF_DUTY")
                val driveSecs = obj.optLong("driveSecs", 11 * 3600L)
                val shiftSecs = obj.optLong("shiftSecs", 14 * 3600L)
                val breakSecs = obj.optLong("breakSecs", 8 * 3600L)
                val cycleSecs = obj.optLong("cycleSecs", 70 * 3600L)
                val lastMs = obj.optLong("lastMs", System.currentTimeMillis())

                return HosDriverState(
                    currentStatus = HosDutyStatus.valueOf(statusStr),
                    driveSecondsRemaining = driveSecs,
                    shiftSecondsRemaining = shiftSecs,
                    breakSecondsRemaining = breakSecs,
                    cycleSecondsRemaining = cycleSecs,
                    lastStatusChangeMs = lastMs
                )
            } catch (_: Exception) {}
        }

        return HosDriverState()
    }

    fun updateDutyStatus(context: Context, newStatus: HosDutyStatus): HosDriverState {
        val currentState = getDriverState(context)
        var updated = currentState.copy(
            currentStatus = newStatus,
            lastStatusChangeMs = System.currentTimeMillis()
        )

        if (newStatus == HosDutyStatus.OFF_DUTY || newStatus == HosDutyStatus.SLEEPER_BERTH) {
            val elapsedOffSecs = (System.currentTimeMillis() - currentState.lastStatusChangeMs) / 1000
            if (elapsedOffSecs >= 36000) {
                updated = updated.copy(
                    driveSecondsRemaining = 11 * 3600L,
                    shiftSecondsRemaining = 14 * 3600L,
                    breakSecondsRemaining = 8 * 3600L
                )
            } else if (elapsedOffSecs >= 1800) {
                updated = updated.copy(breakSecondsRemaining = 8 * 3600L)
            }
        }

        saveDriverState(context, updated)
        return updated
    }

    fun resetClocksToFull(context: Context): HosDriverState {
        val full = HosDriverState(
            currentStatus = HosDutyStatus.OFF_DUTY,
            driveSecondsRemaining = 11 * 3600L,
            shiftSecondsRemaining = 14 * 3600L,
            breakSecondsRemaining = 8 * 3600L,
            cycleSecondsRemaining = 70 * 3600L,
            lastStatusChangeMs = System.currentTimeMillis()
        )
        saveDriverState(context, full)
        return full
    }

    fun updateCustomClocks(
        context: Context,
        driveHours: Float,
        shiftHours: Float,
        breakHours: Float
    ): HosDriverState {
        val currentState = getDriverState(context)
        val updated = currentState.copy(
            driveSecondsRemaining = (driveHours * 3600).toLong().coerceIn(0L, 11 * 3600L),
            shiftSecondsRemaining = (shiftHours * 3600).toLong().coerceIn(0L, 14 * 3600L),
            breakSecondsRemaining = (breakHours * 3600).toLong().coerceIn(0L, 8 * 3600L),
            lastStatusChangeMs = System.currentTimeMillis()
        )
        saveDriverState(context, updated)
        return updated
    }

    private fun saveDriverState(context: Context, state: HosDriverState) {
        val prefs = getPrefs(context)
        val obj = JSONObject().apply {
            put("status", state.currentStatus.name)
            put("driveSecs", state.driveSecondsRemaining)
            put("shiftSecs", state.shiftSecondsRemaining)
            put("breakSecs", state.breakSecondsRemaining)
            put("cycleSecs", state.cycleSecondsRemaining)
            put("lastMs", state.lastStatusChangeMs)
        }
        prefs.edit().putString("hos_state", obj.toString()).apply()
    }

    fun evaluateRouteHosRequirements(
        routeMins: Int,
        totalMiles: Double,
        driverState: HosDriverState
    ): HosRouteRequirement {
        val routeSecs = routeMins * 60L
        val remainingDriveSecs = driverState.driveSecondsRemaining
        val remainingBreakSecs = driverState.breakSecondsRemaining

        val needs30mBreak = routeSecs > remainingBreakSecs
        val needs10hReset = routeSecs > remainingDriveSecs

        val avgSpeedMph = if (routeMins > 0) (totalMiles / (routeMins / 60.0)).coerceIn(30.0, 70.0) else 55.0

        val breakMile = if (needs30mBreak) {
            ((remainingBreakSecs / 3600.0) * avgSpeedMph).coerceAtMost(totalMiles * 0.9)
        } else 0.0

        val resetMile = if (needs10hReset) {
            ((remainingDriveSecs / 3600.0) * avgSpeedMph).coerceAtMost(totalMiles * 0.9)
        } else 0.0

        return HosRouteRequirement(
            requires30MinBreak = needs30mBreak,
            requires10HourReset = needs10hReset,
            estimatedBreakMile = breakMile,
            estimatedResetMile = resetMile,
            breakRecommendedLocation = "Love's Travel Stop (Mile ${String.format(Locale.US, "%.1f", breakMile)})",
            resetRecommendedLocation = "TA Travel Center (Mile ${String.format(Locale.US, "%.1f", resetMile)})"
        )
    }
}
