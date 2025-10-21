package com.example.seniorproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.seniorproject.data.CalibrationData
import com.example.seniorproject.data.SensorSource
import com.example.seniorproject.data.UserManager
import com.example.seniorproject.data.CalibrationImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for calibrating the glove sensors for a specific user.
 * Guides the user through capturing baseline and maximum sensor values.
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var usernameText: TextView
    private lateinit var stepText: TextView
    private lateinit var instructionText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var sensorReadingsText: TextView
    private lateinit var sensorReadingsTitle: TextView
    
    private lateinit var connectButton: Button
    private lateinit var calibrateRestButton: Button
    private lateinit var calibrateFlexButton: Button
    private lateinit var saveCalibrationButton: Button
    private lateinit var importCalibrationButton: Button
    private lateinit var skipButton: Button
    
    private lateinit var userManager: UserManager
    private lateinit var calibrationImporter: CalibrationImporter
    private var username: String = ""
    private var sensorSource: SensorSource? = null
    private var isConnected = false
    
    // Calibration data
    private var baselineValues: FloatArray? = null
    private var maximumValues: FloatArray? = null
    private val numSensors = 5 // Adjust based on your glove's sensor count
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme
        val prefs = getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        when (prefs.getString("theme", "Light")) {
            "Light" -> setTheme(R.style.Theme_SeniorProject_Light)
            "Dark" -> setTheme(R.style.Theme_SeniorProject_Dark)
            else -> setTheme(R.style.Theme_SeniorProject_Light)
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        // Get username from intent
        username = intent.getStringExtra("username") ?: ""
        if (username.isEmpty()) {
            Toast.makeText(this, "Error: No username provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize UserManager and CalibrationImporter
        userManager = UserManager(this)
        calibrationImporter = CalibrationImporter(this)

        // Initialize views
        usernameText = findViewById(R.id.usernameText)
        stepText = findViewById(R.id.stepText)
        instructionText = findViewById(R.id.instructionText)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        sensorReadingsText = findViewById(R.id.sensorReadingsText)
        sensorReadingsTitle = findViewById(R.id.sensorReadingsTitle)
        
        connectButton = findViewById(R.id.connectButton)
        calibrateRestButton = findViewById(R.id.calibrateRestButton)
        calibrateFlexButton = findViewById(R.id.calibrateFlexButton)
        saveCalibrationButton = findViewById(R.id.saveCalibrationButton)
        importCalibrationButton = findViewById(R.id.importCalibrationButton)
        skipButton = findViewById(R.id.skipButton)

        // Set username
        usernameText.text = "User: $username"

        // Setup button listeners
        connectButton.setOnClickListener {
            connectToGlove()
        }

        calibrateRestButton.setOnClickListener {
            calibrateRestPosition()
        }

        calibrateFlexButton.setOnClickListener {
            calibrateFlexPosition()
        }

        saveCalibrationButton.setOnClickListener {
            saveCalibration()
        }

        importCalibrationButton.setOnClickListener {
            showImportCalibrationDialog()
        }

        skipButton.setOnClickListener {
            skipCalibration()
        }
    }

    /**
     * Connect to the glove via Bluetooth.
     */
    private fun connectToGlove() {
        // Launch BluetoothActivity to connect
        val intent = Intent(this, BluetoothActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_BLUETOOTH)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_BLUETOOTH && resultCode == RESULT_OK) {
            // Connection established
            isConnected = true
            updateConnectionStatus("Connected", true)
            calibrateRestButton.isEnabled = true
            
            // Update instructions for next step
            updateStep(
                step = "Step 2: Calibrate Rest Position",
                instruction = "Relax your hand completely (fingers straight, glove in rest position). Click 'Calibrate Rest Position' when ready."
            )
            
            // TODO: Get actual sensor source from BluetoothActivity
            // For now, this is a placeholder. You'll need to implement sensor source passing.
            Toast.makeText(this, "Connected! Ready to calibrate.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Calibrate the rest position (baseline values).
     */
    private fun calibrateRestPosition() {
        updateStep(
            step = "Calibrating Rest Position...",
            instruction = "Hold your hand still in rest position. Collecting data..."
        )
        
        calibrateRestButton.isEnabled = false
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Collect sensor readings over 3 seconds and average them
                val readings = collectSensorReadings(samplesCount = 30, delayMs = 100)
                
                if (readings != null) {
                    baselineValues = readings
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CalibrationActivity,
                            "Rest position captured!",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Enable next step
                        calibrateFlexButton.isEnabled = true
                        
                        updateStep(
                            step = "Step 3: Calibrate Flex Position",
                            instruction = "Make a fist (all fingers fully flexed). Click 'Calibrate Flex Position' when ready."
                        )
                        
                        // Display captured values
                        displaySensorReadings("Baseline", baselineValues!!)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CalibrationActivity,
                            "Failed to capture rest position. Try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                        calibrateRestButton.isEnabled = true
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CalibrationActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    calibrateRestButton.isEnabled = true
                }
            }
        }
    }

    /**
     * Calibrate the flex position (maximum values).
     */
    private fun calibrateFlexPosition() {
        updateStep(
            step = "Calibrating Flex Position...",
            instruction = "Hold your fist tightly. Collecting data..."
        )
        
        calibrateFlexButton.isEnabled = false
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Collect sensor readings over 3 seconds and average them
                val readings = collectSensorReadings(samplesCount = 30, delayMs = 100)
                
                if (readings != null) {
                    maximumValues = readings
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CalibrationActivity,
                            "Flex position captured!",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Enable save button
                        saveCalibrationButton.isEnabled = true
                        
                        updateStep(
                            step = "Step 4: Save Calibration",
                            instruction = "Calibration complete! Click 'Save Calibration' to save your settings."
                        )
                        
                        // Display captured values
                        displaySensorReadings("Maximum", maximumValues!!)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CalibrationActivity,
                            "Failed to capture flex position. Try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                        calibrateFlexButton.isEnabled = true
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CalibrationActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    calibrateFlexButton.isEnabled = true
                }
            }
        }
    }

    /**
     * Collect sensor readings and return averaged values.
     * This is a placeholder - implement actual sensor reading based on your SensorSource.
     */
    private suspend fun collectSensorReadings(samplesCount: Int, delayMs: Long): FloatArray? {
        // TODO: Replace with actual sensor reading from your SensorSource
        // This is a placeholder that simulates sensor readings
        
        val sums = FloatArray(numSensors) { 0f }
        var successfulSamples = 0
        
        for (i in 0 until samplesCount) {
            try {
                // Placeholder: Generate fake sensor data
                // Replace this with: val features = sensorSource?.nextFeatures()
                val features = FloatArray(numSensors) { sensorIndex ->
                    // Simulated sensor values between 0 and 1023
                    (Math.random() * 1023).toFloat()
                }
                
                // Add to sums
                features.forEachIndexed { index, value ->
                    if (index < numSensors) {
                        sums[index] += value
                    }
                }
                
                successfulSamples++
                delay(delayMs)
                
            } catch (e: Exception) {
                android.util.Log.e("Calibration", "Error reading sensor", e)
            }
        }
        
        if (successfulSamples == 0) {
            return null
        }
        
        // Return averaged values
        return FloatArray(numSensors) { i ->
            sums[i] / successfulSamples
        }
    }

    /**
     * Save the calibration to the user profile.
     */
    private fun saveCalibration() {
        if (baselineValues == null || maximumValues == null) {
            Toast.makeText(this, "Calibration incomplete", Toast.LENGTH_SHORT).show()
            return
        }
        
        val calibrationData = CalibrationData(
            sensorBaselines = baselineValues!!,
            sensorMaximums = maximumValues!!
        )
        
        if (userManager.updateUserCalibration(username, calibrationData)) {
            Toast.makeText(this, "Calibration saved successfully!", Toast.LENGTH_LONG).show()
            
            // Return success
            setResult(RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "Failed to save calibration", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Show dialog to import calibration from Python script.
     */
    private fun showImportCalibrationDialog() {
        val availableCalibrations = calibrationImporter.listAvailableCalibrations()
        
        if (availableCalibrations.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No Calibration Files Found")
                .setMessage("No calibration files found in app storage. Please:\n\n" +
                        "1. Run the Python calibration script\n" +
                        "2. Copy the generated JSON file to the app's internal storage\n" +
                        "3. Try importing again")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        val items = availableCalibrations.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Select Calibration File")
            .setItems(items) { _, which ->
                val selectedFile = items[which]
                importCalibrationFromFile(selectedFile)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Import calibration from selected file.
     */
    private fun importCalibrationFromFile(filename: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = calibrationImporter.importCalibrationFromInternalStorage(filename, username)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(
                            this@CalibrationActivity,
                            "Calibration imported successfully!",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Return success
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        Toast.makeText(
                            this@CalibrationActivity,
                            "Failed to import calibration. Check the file format.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CalibrationActivity,
                        "Error importing calibration: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Skip calibration and return to user selection.
     */
    private fun skipCalibration() {
        AlertDialog.Builder(this)
            .setTitle("Skip Calibration?")
            .setMessage("You can calibrate later from the Settings menu. Continue without calibration?")
            .setPositiveButton("Skip") { _, _ ->
                setResult(RESULT_CANCELED)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Update the step and instruction text.
     */
    private fun updateStep(step: String, instruction: String) {
        stepText.text = step
        instructionText.text = instruction
    }

    /**
     * Update connection status display.
     */
    private fun updateConnectionStatus(status: String, connected: Boolean) {
        connectionStatusText.text = status
        if (connected) {
            connectionStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            connectionStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }

    /**
     * Display sensor readings on screen.
     */
    private fun displaySensorReadings(label: String, values: FloatArray) {
        sensorReadingsTitle.visibility = android.view.View.VISIBLE
        sensorReadingsText.visibility = android.view.View.VISIBLE
        
        val readings = StringBuilder("$label Values:\n")
        values.forEachIndexed { index, value ->
            readings.append("Sensor $index: ${String.format("%.2f", value)}\n")
        }
        
        sensorReadingsText.text = readings.toString()
    }

    override fun onDestroy() {
        // Clean up sensor source if needed
        sensorSource?.let {
            lifecycleScope.launch {
                it.close()
            }
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_BLUETOOTH = 2001
    }
}

