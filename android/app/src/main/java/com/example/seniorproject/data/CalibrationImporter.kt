package com.example.seniorproject.data

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Imports calibration data from Python-generated JSON files.
 * This allows the Android app to use calibration data collected
 * by the Python calibration script.
 */
class CalibrationImporter(private val context: Context) {
    
    companion object {
        private const val TAG = "CalibrationImporter"
        private const val CALIBRATION_DIR = "calibrations"
    }
    
    /**
     * Import calibration data from a JSON file.
     * 
     * @param jsonFilePath Path to the JSON file (can be absolute or relative to app directory)
     * @param username Username to associate with the calibration
     * @return True if import was successful, false otherwise
     */
    fun importCalibrationFromFile(jsonFilePath: String, username: String): Boolean {
        return try {
            val file = File(jsonFilePath)
            if (!file.exists()) {
                Log.e(TAG, "Calibration file does not exist: $jsonFilePath")
                return false
            }
            
            val jsonString = file.readText()
            importCalibrationFromJson(jsonString, username)
            
        } catch (e: IOException) {
            Log.e(TAG, "Error reading calibration file: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error importing calibration: ${e.message}")
            false
        }
    }
    
    /**
     * Import calibration data from JSON string.
     * 
     * @param jsonString JSON string containing calibration data
     * @param username Username to associate with the calibration
     * @return True if import was successful, false otherwise
     */
    fun importCalibrationFromJson(jsonString: String, username: String): Boolean {
        return try {
            val json = JSONObject(jsonString)
            
            // Validate JSON structure
            if (!json.has("calibrationData")) {
                Log.e(TAG, "Invalid calibration JSON: missing 'calibrationData'")
                return false
            }
            
            val calibrationDataJson = json.getJSONObject("calibrationData")
            
            if (!calibrationDataJson.has("sensorBaselines") || 
                !calibrationDataJson.has("sensorMaximums")) {
                Log.e(TAG, "Invalid calibration JSON: missing baseline or maximum values")
                return false
            }
            
            // Parse sensor values
            val baselinesArray = calibrationDataJson.getJSONArray("sensorBaselines")
            val maximumsArray = calibrationDataJson.getJSONArray("sensorMaximums")
            
            val sensorBaselines = FloatArray(baselinesArray.length()) { i ->
                baselinesArray.getDouble(i).toFloat()
            }
            
            val sensorMaximums = FloatArray(maximumsArray.length()) { i ->
                maximumsArray.getDouble(i).toFloat()
            }
            
            // Create calibration data
            val calibrationData = CalibrationData(
                sensorBaselines = sensorBaselines,
                sensorMaximums = sensorMaximums,
                calibrationTimestamp = calibrationDataJson.optLong("calibrationTimestamp", System.currentTimeMillis())
            )
            
            // Save to user profile
            val userManager = UserManager(context)
            val success = userManager.updateUserCalibration(username, calibrationData)
            
            if (success) {
                Log.i(TAG, "Successfully imported calibration for user: $username")
                Log.i(TAG, "Baseline values: ${sensorBaselines.contentToString()}")
                Log.i(TAG, "Maximum values: ${sensorMaximums.contentToString()}")
            } else {
                Log.e(TAG, "Failed to save calibration to user profile")
            }
            
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing calibration JSON: ${e.message}")
            false
        }
    }
    
    /**
     * Import calibration from app's internal storage.
     * Looks for calibration files in the app's files directory.
     * 
     * @param filename Name of the calibration file (e.g., "calibration_john.json")
     * @param username Username to associate with the calibration
     * @return True if import was successful, false otherwise
     */
    fun importCalibrationFromInternalStorage(filename: String, username: String): Boolean {
        val file = File(context.filesDir, filename)
        return importCalibrationFromFile(file.absolutePath, username)
    }
    
    /**
     * List available calibration files in the app's internal storage.
     * 
     * @return List of calibration filenames
     */
    fun listAvailableCalibrations(): List<String> {
        val calibrationsDir = File(context.filesDir, CALIBRATION_DIR)
        if (!calibrationsDir.exists()) {
            return emptyList()
        }
        
        return calibrationsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.map { it.name }
            ?: emptyList()
    }
    
    /**
     * Copy calibration file to app's internal storage.
     * 
     * @param sourcePath Source file path
     * @param filename Destination filename
     * @return True if copy was successful, false otherwise
     */
    fun copyCalibrationToInternalStorage(sourcePath: String, filename: String): Boolean {
        return try {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                Log.e(TAG, "Source file does not exist: $sourcePath")
                return false
            }
            
            val calibrationsDir = File(context.filesDir, CALIBRATION_DIR)
            if (!calibrationsDir.exists()) {
                calibrationsDir.mkdirs()
            }
            
            val destFile = File(calibrationsDir, filename)
            sourceFile.copyTo(destFile, overwrite = true)
            
            Log.i(TAG, "Copied calibration file to: ${destFile.absolutePath}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error copying calibration file: ${e.message}")
            false
        }
    }
    
    /**
     * Validate calibration data.
     * 
     * @param calibrationData Calibration data to validate
     * @return True if calibration data is valid, false otherwise
     */
    fun validateCalibrationData(calibrationData: CalibrationData): Boolean {
        return try {
            // Check if arrays have the same length
            if (calibrationData.sensorBaselines.size != calibrationData.sensorMaximums.size) {
                Log.e(TAG, "Baseline and maximum arrays have different lengths")
                return false
            }
            
            // Check if all values are finite
            val allBaselinesFinite = calibrationData.sensorBaselines.all { it.isFinite() }
            val allMaximumsFinite = calibrationData.sensorMaximums.all { it.isFinite() }
            
            if (!allBaselinesFinite || !allMaximumsFinite) {
                Log.e(TAG, "Calibration data contains non-finite values")
                return false
            }
            
            // Check if maximums are greater than baselines
            val maximumsGreaterThanBaselines = calibrationData.sensorBaselines.zip(calibrationData.sensorMaximums)
                .all { (baseline, maximum) -> maximum > baseline }
            
            if (!maximumsGreaterThanBaselines) {
                Log.w(TAG, "Warning: Some maximum values are not greater than baseline values")
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating calibration data: ${e.message}")
            false
        }
    }
}
