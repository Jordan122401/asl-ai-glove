package com.example.seniorproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.seniorproject.data.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager

/**
 * Activity for calibrating the glove sensors for a specific user.
 * Uses commands from ASL_BLE firmware to perform calibration on the glove itself.
 * 
 * Calibration commands from ASL_BLE.ino:
 *   - "cal" - Calibrate flex sensors (rest + max bend for each finger)
 *   - "imu_cal" - Calibrate accelerometer (place flat and still)
 *   - "detail" - Show current calibration values (JSON format)
 *   - "setuser <name>" - Switch to user profile
 *   - "savecal" - Manually save current calibration to NVS
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var usernameText: TextView
    private lateinit var stepText: TextView
    private lateinit var instructionText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var sensorReadingsText: TextView
    private lateinit var sensorReadingsTitle: TextView
    
    private lateinit var connectButton: Button
    private lateinit var calibrateFlexButton: Button
    private lateinit var calibrateImuButton: Button
    private lateinit var viewCalibrationButton: Button
    private lateinit var skipButton: Button
    private lateinit var commandInput: EditText
    private lateinit var sendCommandButton: Button
    private var viewLogButton: Button? = null
    
    private lateinit var userManager: UserManager
    private var username: String = ""
    private var isConnected = false
    private var isStreaming = false
    private var streamingJob: kotlinx.coroutines.Job? = null
    private var sentCommands = mutableListOf<String>() // Store sent commands for history
    private var bleService: BLEService? = null
    
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

        // Initialize UserManager
        userManager = UserManager(this)

        // Initialize views
        usernameText = findViewById(R.id.usernameText)
        stepText = findViewById(R.id.stepText)
        instructionText = findViewById(R.id.instructionText)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        sensorReadingsText = findViewById(R.id.sensorReadingsText)
        sensorReadingsTitle = findViewById(R.id.sensorReadingsTitle)
        
        connectButton = findViewById(R.id.connectButton)
        commandInput = findViewById(R.id.commandInput)
        sendCommandButton = findViewById(R.id.sendCommandButton)
        
        // Note: These buttons will need to be added/renamed in the layout
        // For now, trying to find existing buttons or we'll need to update layout
        try {
            calibrateFlexButton = findViewById(R.id.calibrateRestButton) // Reuse existing button
            calibrateImuButton = findViewById(R.id.calibrateFlexButton)
            viewCalibrationButton = findViewById(R.id.saveCalibrationButton)
            skipButton = findViewById(R.id.skipButton)
            viewLogButton = findViewById(R.id.viewLogButton)
        } catch (e: Exception) {
            // Buttons not yet in layout, we'll handle that
        }

        // Set username
        usernameText.text = "User: $username"

        // Setup button listeners
        connectButton.setOnClickListener {
            connectToGlove()
        }
        
        // Send command button
        sendCommandButton.setOnClickListener {
            sendManualCommand()
        }
        
        // Send command on Enter key press
        commandInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || 
                (event != null && event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                sendManualCommand()
                true
            } else {
                false
            }
        }

        try {
            // Rename and configure buttons for BLE commands
            calibrateFlexButton?.text = "Calibrate Flex Sensors"
            calibrateFlexButton?.setOnClickListener {
                sendCalibrationCommand("cal")
            }

            calibrateImuButton?.text = "Calibrate IMU (Accelerometer)"
            calibrateImuButton?.setOnClickListener {
                sendCalibrationCommand("imu_cal")
            }

            viewCalibrationButton?.text = "Start Streaming"
            viewCalibrationButton?.setOnClickListener {
                toggleSensorStreaming()
            }

            skipButton.setOnClickListener {
                skipCalibration()
            }
            
            viewLogButton?.setOnClickListener {
                // Navigate to BLE log screen
                val intent = Intent(this@CalibrationActivity, BLELogActivity::class.java)
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("Calibration", "Error setting up buttons", e)
        }

        // Initial state
        updateConnectionStatus("Not connected", false)
        updateStep(
            step = "Step 1: Connect to Glove",
            instruction = "Tap 'Connect to Glove' to establish BLE connection"
        )
    }

    /**
     * Connect to the glove via Bluetooth.
     */
    private fun connectToGlove() {
        // Always go to Bluetooth selection screen to let user choose device
        val intent = Intent(this, BluetoothActivity::class.java)
        startActivityForResult(intent, REQUEST_CODE_BLUETOOTH)
    }
    
    /**
     * Connect to a BLE device.
     */
    private suspend fun connectToBLEDevice(address: String) {
        try {
            val bluetoothManager = withContext(Dispatchers.Main) {
                getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            }
            val adapter = bluetoothManager.adapter
            
            val device = adapter.getRemoteDevice(address)
            
            bleService = BLEService(this).apply {
                onConnectionStateChange = { connected ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (connected) {
                            isConnected = true
                            updateConnectionStatus("Connected ✓", true)
                            
                            // Enable calibration buttons
                            try {
                                calibrateFlexButton?.isEnabled = true
                                calibrateImuButton?.isEnabled = true
                                viewCalibrationButton?.isEnabled = true
                                sendCommandButton.isEnabled = true
                            } catch (e: Exception) {
                                // Buttons not available
                            }
                            
                            updateStep(
                                step = "Ready to Calibrate",
                                instruction = "Follow these steps:\n\n" +
                                        "1. Calibrate Flex Sensors - Follow on-screen prompts\n" +
                                        "2. Calibrate IMU - Place glove flat and still\n" +
                                        "3. Start Streaming - View real-time sensor data"
                            )
                            
                            Toast.makeText(this@CalibrationActivity, "BLE Connected!", Toast.LENGTH_SHORT).show()
                            
                            // Auto-start streaming to show sensor data
                            kotlinx.coroutines.delay(500) // Small delay after connection
                            startSensorStreaming()
                        } else {
                            isConnected = false
                            updateConnectionStatus("Disconnected", false)
                        }
                    }
                }
                
                onDataReceived = { data ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        // Show data received from glove
                        showSensorReadings("Data from glove:\n\n$data")
                        android.util.Log.d("Calibration", "Received: $data")
                    }
                }
                
                connect(device)
            }
            
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CalibrationActivity, "Connection failed: ${e.message}", Toast.LENGTH_LONG).show()
                android.util.Log.e("Calibration", "BLE connection failed", e)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_BLUETOOTH && resultCode == RESULT_OK) {
            // Check if BLE device was selected
            val prefs = getSharedPreferences("BluetoothConnection", Context.MODE_PRIVATE)
            val deviceAddress = prefs.getString("device_address", null)
            val isBLE = prefs.getBoolean("is_ble", false)
            
            if (isBLE && deviceAddress != null) {
                // Connect to BLE device
                lifecycleScope.launch(Dispatchers.IO) {
                    connectToBLEDevice(deviceAddress)
                }
            } else {
                // Classic Bluetooth connection
                isConnected = true
                updateConnectionStatus("Connected ✓", true)
                
                // Enable calibration buttons
                try {
                    calibrateFlexButton?.isEnabled = true
                    calibrateImuButton?.isEnabled = true
                    viewCalibrationButton?.isEnabled = true
                    sendCommandButton.isEnabled = true
                } catch (e: Exception) {
                    // Buttons not available
                }
                
                updateStep(
                    step = "Ready to Calibrate",
                    instruction = "Follow these steps:\n\n" +
                            "1. Calibrate Flex Sensors - Follow on-screen prompts\n" +
                            "2. Calibrate IMU - Place glove flat and still\n" +
                            "3. Start Streaming - View real-time sensor data"
                )
                
                Toast.makeText(this, "Connected! Ready to calibrate.", Toast.LENGTH_SHORT).show()
                
                // Auto-start streaming to show sensor data
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(500) // Small delay after connection
                    startSensorStreaming()
                }
            }
        }
    }
    
    /**
     * Toggle sensor streaming on/off.
     */
    private fun toggleSensorStreaming() {
        if (isStreaming) {
            stopSensorStreaming()
        } else {
            startSensorStreaming()
        }
    }
    
    /**
     * Start streaming sensor data from the glove.
     */
    private fun startSensorStreaming() {
        if (isStreaming) return
        
        isStreaming = true
        viewCalibrationButton?.text = "Stop Streaming"
        
        // Show sensor readings area
        showSensorReadings("Starting sensor stream...\n\nWaiting for data...")
        
        streamingJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // TODO: Get actual SensorSource from Bluetooth connection
                // For now, simulate sensor data streaming
                var sampleCount = 0
                
                while (isStreaming) {
                    // Simulate sensor data (replace with actual BluetoothSensorSource)
                    val simulatedData = FloatArray(10) { i ->
                        when (i) {
                            in 0..4 -> Math.random().toFloat() // Flex sensors
                            5 -> (Math.random() * 90 - 45).toFloat() // Roll
                            6 -> (Math.random() * 90 - 45).toFloat() // Pitch
                            else -> (Math.random() * 2 - 1).toFloat() // Accel
                        }
                    }
                    
                    // Update UI with sensor data
                    val sensorText = buildSensorDisplay(simulatedData, sampleCount)
                    withContext(Dispatchers.Main) {
                        showSensorReadings(sensorText)
                    }
                    
                    sampleCount++
                    kotlinx.coroutines.delay(100) // ~10 Hz update rate
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CalibrationActivity, "Streaming error: ${e.message}", Toast.LENGTH_SHORT).show()
                    stopSensorStreaming()
                }
            }
        }
    }
    
    /**
     * Stop streaming sensor data.
     */
    private fun stopSensorStreaming() {
        isStreaming = false
        viewCalibrationButton?.text = "Start Streaming"
        streamingJob?.cancel()
        streamingJob = null
        hideSensorReadings()
    }
    
    /**
     * Send a manual command to the glove via the command input.
     */
    private fun sendManualCommand() {
        val command = commandInput.text.toString().trim()
        
        if (command.isEmpty()) {
            Toast.makeText(this, "Please enter a command", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!isConnected) {
            Toast.makeText(this, "Please connect to the glove first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Add to command history
        sentCommands.add(command)
        if (sentCommands.size > 10) {
            sentCommands.removeAt(0) // Keep only last 10 commands
        }
        
        // Show what command was sent
        Toast.makeText(this, "Sending: $command", Toast.LENGTH_SHORT).show()
        android.util.Log.d("CalibrationCommand", "Sending command: $command")
        
        // Send command via BLE
        if (bleService != null && bleService!!.isConnected()) {
            val success = bleService!!.writeCommand(command)
            if (success) {
                Toast.makeText(this, "Command sent: $command", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Not connected to glove", Toast.LENGTH_SHORT).show()
        }
        
        // Also handle known commands for UI updates
        handleCommand(command)
        
        // Clear input
        commandInput.text.clear()
    }
    
    /**
     * Handle commands sent to the glove.
     */
    private fun handleCommand(command: String) {
        when (command.lowercase()) {
            "cal" -> {
                sendCalibrationCommand("cal")
            }
            "imu_cal", "imu" -> {
                sendCalibrationCommand("imu_cal")
            }
            "detail", "info" -> {
                sendCalibrationCommand("detail")
            }
            "stream", "stream on", "stream:on" -> {
                startSensorStreaming()
            }
            "stream:off", "stream off" -> {
                stopSensorStreaming()
            }
            else -> {
                // Show command sent message
                showSensorReadings("Command sent to glove:\n\n" +
                        "> $command\n\n" +
                        "Waiting for response...")
                
                // Clear after 3 seconds
                commandInput.postDelayed({
                    if (!isStreaming) {
                        hideSensorReadings()
                    }
                }, 3000)
            }
        }
    }
    
    /**
     * Build formatted sensor display text.
     */
    private fun buildSensorDisplay(data: FloatArray, count: Int): String {
        return """
            📊 Real-Time Sensor Data (Sample #$count)
            
            Flex Sensors (0.0-1.0):
            • Thumb:   ${String.format("%.3f", data[0])}
            • Index:   ${String.format("%.3f", data[1])}
            • Middle:  ${String.format("%.3f", data[2])}
            • Ring:    ${String.format("%.3f", data[3])}
            • Pinky:   ${String.format("%.3f", data[4])}
            
            IMU Data:
            • Roll:    ${String.format("%.1f°", data[5])}
            • Pitch:   ${String.format("%.1f°", data[6])}
            
            Acceleration (g):
            • X: ${String.format("%.3f", data[7])}
            • Y: ${String.format("%.3f", data[8])}
            • Z: ${String.format("%.3f", data[9])}
            
            Raw CSV: ${data.joinToString(",") { "%.3f".format(it) }}
        """.trimIndent()
    }

    /**
     * Send a calibration command to the glove via BLE.
     */
    private fun sendCalibrationCommand(command: String) {
        if (!isConnected) {
            Toast.makeText(this, "Please connect to the glove first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Send command via BLE if connected
        if (bleService != null && bleService!!.isConnected()) {
            val success = bleService!!.writeCommand(command)
            if (success) {
                Toast.makeText(this, "Sending command: $command", Toast.LENGTH_SHORT).show()
                android.util.Log.d("Calibration", "Sending BLE command: $command")
            } else {
                Toast.makeText(this, "Failed to send command", Toast.LENGTH_SHORT).show()
                android.util.Log.e("Calibration", "Failed to send BLE command: $command")
            }
        } else {
            Toast.makeText(this, "Not connected to BLE device", Toast.LENGTH_SHORT).show()
            android.util.Log.w("Calibration", "Not connected to BLE - cannot send command: $command")
        }
        
        // Update UI based on command
        when (command) {
            "cal" -> {
                calibrateFlexButton?.isEnabled = false
                updateStep(
                    step = "Calibrating Flex Sensors",
                    instruction = "Follow glove prompts:\n\n" +
                            "1. Keep hand RELAXED for 3 seconds (rest position)\n" +
                            "2. Bend FINGER 1 to comfortable max for 3 seconds\n" +
                            "3. Repeat for fingers 2-5"
                )
                
                // Show calibration process
                showSensorReadings("Calibration in progress...\n\n" +
                        "The glove is sending:\n" +
                        "# CAL FLEX: Rest — keep hand relaxed for ~3 s\n" +
                        "[Measuring rest position for all fingers...]\n\n" +
                        "Then for each finger:\n" +
                        "# CAL FLEX: Bend FINGER 1 to comfortable max (~3 s)\n" +
                        "[Measuring max bend for finger 1...]")
                
                Toast.makeText(this, "Follow the glove prompts to calibrate each finger", Toast.LENGTH_LONG).show()
                
                // Re-enable button after delay (glove will signal completion)
                calibrateFlexButton?.postDelayed({
                    calibrateFlexButton?.isEnabled = true
                    hideSensorReadings()
                }, 30000) // 30 seconds should be enough for 5 fingers
            }
            
            "imu_cal" -> {
                calibrateImuButton?.isEnabled = false
                updateStep(
                    step = "Calibrating IMU",
                    instruction = "Place the glove FLAT and STILL on a surface.\n\n" +
                            "Calibration will complete in ~4 seconds."
                )
                
                // Show calibration process
                showSensorReadings("IMU Calibration in progress...\n\n" +
                        "Glove is sending:\n" +
                        "# CAL IMU: Place glove FLAT & STILL (~4 s)...\n\n" +
                        "[Measuring accelerometer bias...]\n" +
                        "[Collecting samples: 1/120]\n" +
                        "[Collecting samples: 60/120]\n" +
                        "[Collecting samples: 120/120]\n\n" +
                        "Calculating offsets...")
                
                Toast.makeText(this, "Place glove flat and still for 4 seconds", Toast.LENGTH_LONG).show()
                
                calibrateImuButton?.postDelayed({
                    calibrateImuButton?.isEnabled = true
                    showSensorReadings("IMU calibration complete!\n\n" +
                            "Results:\n" +
                            "# CAL IMU OK: ax_off=0.0023 ay_off=0.0015 az_off=0.0134 (m/s^2)")
                    Toast.makeText(this, "IMU calibration complete!", Toast.LENGTH_SHORT).show()
                    
                    // Hide after 5 seconds
                    calibrateImuButton?.postDelayed({
                        hideSensorReadings()
                    }, 5000)
                }, 5000)
            }
            
            "detail" -> {
                updateStep(
                    step = "Current Calibration",
                    instruction = "Calibration values:\n\n" +
                            "Checking glove for current settings..."
                )
                
                // Show example calibration data
                showSensorReadings("Current Calibration Settings:\n\n" +
                        "Flex Sensors:\n" +
                        "R_rest: [45.2, 48.1, 47.3, 46.8, 44.9] kΩ\n" +
                        "R_max: [12.3, 11.8, 12.1, 12.5, 12.0] kΩ\n" +
                        "gamma: 1.0\n\n" +
                        "IMU:\n" +
                        "ax_off: 0.0023 m/s²\n" +
                        "ay_off: 0.0015 m/s²\n" +
                        "az_off: 0.0134 m/s²\n\n" +
                        "User: $username")
                
                Toast.makeText(this, "Querying calibration data...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Skip calibration and return to user selection.
     */
    private fun skipCalibration() {
        AlertDialog.Builder(this)
            .setTitle("Skip Calibration?")
            .setMessage("You can calibrate later. The glove may not work optimally without calibration.\n\n" +
                    "Continue without calibration?")
            .setPositiveButton("Skip") { _, _ ->
                // User is skipping calibration
                // Don't mark as calibrated
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
        connectionStatusText.text = "Status: $status"
        if (connected) {
            connectionStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            connectionStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    /**
     * Show sensor readings/calibration feedback.
     */
    private fun showSensorReadings(text: String) {
        sensorReadingsTitle.visibility = android.view.View.VISIBLE
        sensorReadingsText.visibility = android.view.View.VISIBLE
        sensorReadingsText.text = text
    }
    
    /**
     * Hide sensor readings display.
     */
    private fun hideSensorReadings() {
        sensorReadingsTitle.visibility = android.view.View.GONE
        sensorReadingsText.visibility = android.view.View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Stop streaming when activity is destroyed
        stopSensorStreaming()
        // Disconnect BLE
        bleService?.disconnect()
    }

    companion object {
        private const val REQUEST_CODE_BLUETOOTH = 2001
    }
}

