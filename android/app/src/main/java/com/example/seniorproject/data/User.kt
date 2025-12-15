package com.example.seniorproject.data

import org.json.JSONObject
import org.json.JSONArray

/**
 * Represents a user profile with calibration data for the ASL glove.
 */
data class User(
    val username: String,
    val isCalibrated: Boolean = false,
    val calibrationData: CalibrationData? = null
) {
    /**
     * Convert to JSON for storage.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("username", username)
            put("isCalibrated", isCalibrated)
            if (calibrationData != null) {
                put("calibrationData", calibrationData.toJson())
            }
        }
    }

    companion object {
        /**
         * Create User from JSON.
         */
        fun fromJson(json: JSONObject): User {
            val username = json.getString("username")
            val isCalibrated = json.getBoolean("isCalibrated")
            val calibrationData = if (json.has("calibrationData")) {
                CalibrationData.fromJson(json.getJSONObject("calibrationData"))
            } else {
                null
            }
            return User(username, isCalibrated, calibrationData)
        }
    }
}

/**
 * Stores calibration parameters for each sensor on the glove.
 * This allows personalization based on hand size and sensor placement.
 */
data class CalibrationData(
    // Sensor baseline values (rest position)
    val sensorBaselines: FloatArray,
    // Sensor maximum values (full flex)
    val sensorMaximums: FloatArray,
    // Timestamp when calibration was performed
    val calibrationTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Normalize a raw sensor reading using calibration data.
     * Maps the value from [baseline, maximum] to [0, 1].
     */
    fun normalizeSensorValue(sensorIndex: Int, rawValue: Float): Float {
        if (sensorIndex >= sensorBaselines.size || sensorIndex >= sensorMaximums.size) {
            return rawValue // No calibration available for this sensor
        }
        
        val baseline = sensorBaselines[sensorIndex]
        val maximum = sensorMaximums[sensorIndex]
        val range = maximum - baseline
        
        if (range == 0f) {
            return 0f
        }
        
        // Normalize to [0, 1] and clamp
        val normalized = (rawValue - baseline) / range
        return normalized.coerceIn(0f, 1f)
    }
    
    /**
     * Apply calibration to an array of sensor readings.
     */
    fun normalizeAllSensors(rawValues: FloatArray): FloatArray {
        return FloatArray(rawValues.size) { i ->
            normalizeSensorValue(i, rawValues[i])
        }
    }

    /**
     * Convert to JSON for storage.
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("sensorBaselines", JSONArray(sensorBaselines.toList()))
            put("sensorMaximums", JSONArray(sensorMaximums.toList()))
            put("calibrationTimestamp", calibrationTimestamp)
        }
    }

    companion object {
        /**
         * Create CalibrationData from JSON.
         */
        fun fromJson(json: JSONObject): CalibrationData {
            val baselines = json.getJSONArray("sensorBaselines")
            val maximums = json.getJSONArray("sensorMaximums")
            val timestamp = json.getLong("calibrationTimestamp")
            
            val sensorBaselines = FloatArray(baselines.length()) { i ->
                baselines.getDouble(i).toFloat()
            }
            val sensorMaximums = FloatArray(maximums.length()) { i ->
                maximums.getDouble(i).toFloat()
            }
            
            return CalibrationData(sensorBaselines, sensorMaximums, timestamp)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CalibrationData

        if (!sensorBaselines.contentEquals(other.sensorBaselines)) return false
        if (!sensorMaximums.contentEquals(other.sensorMaximums)) return false
        if (calibrationTimestamp != other.calibrationTimestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sensorBaselines.contentHashCode()
        result = 31 * result + sensorMaximums.contentHashCode()
        result = 31 * result + calibrationTimestamp.hashCode()
        return result
    }
}

